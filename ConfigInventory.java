/**
*   Module: ConfigInventory.java
*
*   Written by Ray Wilson
*
*   Description: Parse DataPower export files files and put the data into a csv file.
*                The output file will be stored written in the base directory of the export.
*				 This file can then be imported into your favorite spreadsheet.
*
*   History:
*	2017-07-06 v0.0.1 Created.
*	2017-07-07 v0.1.0 Basic functionality achieved.
*	2017-07-08 v1.0.0 Added auto extract and removal of extracted folders.
*
*   Author: Ray Wilson
*           DataPower Techincal Specialist
*           wilsonprw@gmail.com
*
*   Date:   2017-07-06
*
*   Copyright (C) 2017  Paul Ray Wilson
*
*   This program is free software: you can redistribute it and/or modify
*   it under the terms of the GNU General Public License as published by
*   the Free Software Foundation, either version 3 of the License, or
*   (at your option) any later version.
*
*   This program is distributed in the hope that it will be useful,
*   but WITHOUT ANY WARRANTY; without even the implied warranty of
*   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
*   GNU General Public License for more details.
*
*   You should have received a copy of the GNU General Public License
*   along with this program.  If not, see <http://www.gnu.org/licenses/>.
*
*/

import java.io.*;
import java.util.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.FileVisitResult;
import java.util.Date;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.text.SimpleDateFormat;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class ConfigInventory {
	public static boolean debug = false;
	public static String zipFile = "";
	public static String tmpDir = "";
	public static String csvFile = "";
	public static String EXPORT_XML = "export.xml";

	// Max size of the array to store objects in, per domain
	public static int MAX_GATEWAYS = 100;

	// Byte buffer size for the zip file extractor
	public static int BUFFER_SIZE = 4096;

	// The list of objects we will search for. You can extend this list to include about an named object.
	// in the export.xml files. Make sure you use the spelling of a declaration tag, not the class tag.
	// Get the spelling from a tag that has a "name=" attribute and not the "class=" attribute.
	public static String[] OBJECT_LIST = {"B2BGateway", "MultiProtocolGateway", "WebAppFW", "WSGateway", "XMLFirewallService"};

	/** main()
	*
	* This is the "main" method that checks for command line arguments and
	* for starting the createInventory method below where all the actual work will happen
	*
	*/
	public static void main(String arg[]) throws Exception {

		boolean argsValid = checkArgs(arg); // Check command line arugments

		if (!argsValid) {
    	  	System.out.println("\n===============================================================================");
    	  	System.out.println(" ");
    	  	System.out.println("Usage: java ConfigInventory zipFile tmpDir csvFile");
    	  	System.out.println("    Where:");
    	  	System.out.println("          zipFile = Absolute path to the DataPower export zip file");
    	  	System.out.println("          tmpDir  = Absolute path to temporary directory to extract zip files");
    	  	System.out.println("          csvFile = Absolute path and name of the output csv file");
    	  	System.out.println(" ");
    	  	System.out.println("Example > java ConfigInventory /data/IDGv720-A.zip /data/dp-export/ /data/IDGv720-A.csv");
    	  	System.out.println(" ");
    	  	System.out.println("===============================================================================\n");
			System.exit(0);
		} else {
			try {
				// Validate the command line arguments.
				zipFile = arg[0];
				tmpDir = arg[1];
				csvFile = arg[2];
				String result = createInventory();
				System.out.println(result);
			} catch (Exception e) {
				System.err.println(e.getMessage());
				System.exit(0);
			}
		}
	}

	/** createInventory()
	*
	* This is the module where the following happens:
	*   1) The zip file from the DataPower Export is extracted
	*   2) Basic appliance information collected from export.xml in the root of the extract
	*	3) A list of domains is read from the export.xml file in the root of the extract
	*   4) For each domain in the list
	*   5)    The zip file for that domain is extracted
	*   6)    The export.xml file in that domain is examined and information on gateways collected.
	*	7) All the upziped files and folders are removed. (still pending)
	*
	* return - String - The results of the doWork opperation.
	*/
	public static String createInventory () {
		if(debug) {System.out.println("DEBUG :: Entering doWork");}
		String outputFilename = csvFile;
		File fout = new File(outputFilename);

		try {
			// Create the output file if it does not exist.
			if(!fout.exists()){
				fout.createNewFile();
				if(debug){System.out.println("DEBUG :: Creating output file - " + outputFilename);}
			}
			// Output file handle for writing
			BufferedWriter out = new BufferedWriter(new FileWriter(outputFilename,true));

			// Add the Inventory Date/Time to the top of the csv file
			String timeStamp = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss").format(new java.util.Date());
			out.write("Inventory Date, "+timeStamp+"\n"); out.flush();

			// Extract the main export file. It will contain multiple zip files with it for each domain
			// on the appliance. We will unzip those later in the loop where we gather the gateway informaiton
			unZipIt(zipFile, tmpDir);

			// ******
			// Get the data we want from the root export file about the appliance itself.
			// ******
			// Open the export file in the root folder to get some some of the data
			String content = new Scanner(new File(tmpDir+EXPORT_XML)).useDelimiter("\\Z").next();
			// commented out because it's too messy to leave even in debug mode, unless you need it.
			//if(debug){ System.out.println("DEBUG :: XML FILE :"+content); 

			// Get the Device Name and write it to the file.
			String deviceName = getTagValue(content, "device-name");
			out.write("Device Name, "+deviceName+"\n"); out.flush();
			if(debug){ System.out.println("DEBUG :: Device Name :"+ deviceName ); }

			// Get the Product ID and write it to the file.
			String productId = getTagValue(content, "product-id");
			out.write("Product ID, "+productId+"\n"); out.flush();
			if(debug){ System.out.println("DEBUG :: Product ID  :"+ productId ); }

			// Get the serial number and write it to the file.
			String serialNum = getTagValue(content, "serial-number");
			out.write("Serial Number, "+serialNum+"\n"); out.flush();
			if(debug){ System.out.println("DEBUG :: Serial No   :"+ serialNum ); }

			// Get the firmware version level and write it to the file.
			String firmwareVer = getTagValue(content, "firmware-version");
			out.write("Firmware Ver, "+firmwareVer+"\n"); out.flush();
			if(debug){ System.out.println("DEBUG :: Firmware    :"+ firmwareVer ); }

			// Get a list of domains on the appliance to process for gateway information.
			String domains = getTagValue(content, "domains");
			// commented out because it's too messy to leave even in debug mode, unless you need it.
			//if(debug){ System.out.println("DEBUG :: Domain List :"+ domains ); } 

			String[] domainList = getDomains(domains);
			// commented out because it's too messy to leave even in debug mode, unless you need it.
			//if(debug){ for(int j=0;j<domainList.length;j++){ System.out.println("DEBUG :: Domain ["+j+"] = "+domainList[j]); } } 

			// Write the header for the list of gateways.
			out.write("\nList of Domains"); out.flush();

			// ******
			// Now get the object information from each domain.
			// ******
			for(int k=0; k<domainList.length; k++) {

				// Extract the zip file for this domain
				String thisZipFile = tmpDir+domainList[k]+".zip";
				String thisTmpDir = tmpDir+domainList[k]+"/";
				unZipIt(thisZipFile, thisTmpDir);

				// Read in the export.xml file for this domain.
				String domainExportFile = thisTmpDir+"/"+EXPORT_XML;
				File def = new File(domainExportFile);

				// Create on big string from the export.xml file for this domain.
				String domainContent = new Scanner(def).useDelimiter("\\Z").next();
				
				// Write out the domain name to the file with a blank line before it.
				out.write("\n,"+domainList[k]+"\n"); out.flush();
				
				// Search the string for gateway names based on the list above, they will appear in that order.
				for(int l=0; l< OBJECT_LIST.length; l++){
					if(debug){ System.out.println("DEBUG :: Searching for "+ OBJECT_LIST[l]); }

					// The list of name attributes for each defined gateway the current search type.
					String[] thisList = getGateways(def, OBJECT_LIST[l]);
					if(debug){ System.out.println("DEBUG :: theList has "+ thisList.length +" elements."); }
					
					// Check to see if there are any gateways in the list.
					if(thisList.length > 0){
						for(int m=0; m<thisList.length; m++){
							if(debug){ System.out.println("DEBUG :: Working element "+ thisList[m]); }
							// Write the type and name of each gateway in the list out to the file.
							out.write(", , "+ OBJECT_LIST[l] +", "+thisList[m]+"\n"); out.flush();
						}						
					}
				}
			}
			// Hose keeping, Flush the buffer for the csvFile and close it.
			out.flush();
			out.close();
			// Remove the extracted files
			removeTmpDir(tmpDir);
		} catch (Exception ex) {
			System.out.println(":: ERROR ::");
			ex.printStackTrace();
		}
		return("Done. The file "+csvFile+" has been created");
	}

	/** checkArgs(String[])
	* This is method validates the command line arguments as best as we can.
	* param - arg[]
	* return - boolean - true of no errors detected else false.
	*/
	public static boolean checkArgs(String[] arg){
		boolean valid = true;
		if(debug) { for(int i=0;i<arg.length;i++) { System.out.println("DEBUG :: arg "+i+" -"+arg[i]+"-"); } }

		// Check for the zip file
		File zFile = new File(arg[0]);
		if (!zFile.exists()) { valid = false; System.out.println("EEROR :: Zip File -"+arg[0]+"- is not valid"); }

		// Check for a temporary directory name. It does not have to exist, so just check that it's there.
		if(!(arg[1].length() > 0)) { valid = false; System.out.println("EEROR :: basename -"+arg[2]+"- is not valid"); }

		// Check for a csvFile name.
		if(!(arg[2].length() > 0)) { valid = false; System.out.println("EEROR :: basename -"+arg[2]+"- is not valid"); }

		return valid;
	}

	/** getTagValue(String, String)
	* This is method that gets the value of a given tag in an xml string.
	* it only gets the first occurance so does not work on repeating elements.
	* param - xml - a string containing xml.
	* param - tagName - a string containg the tag name with out the "<" or ">".
	* return - string - the value inside the tags.
	*/
	public static String getTagValue(String xml, String tagName){
		return xml.split("<"+tagName+">")[1].split("</"+tagName+">")[0];
	}

	/** getDomains(String)
	* This is method that gets the value of a given tag in an xml string.
	* it only gets the first occurance so does not work on repeating elements.
	* param - xml - a string containing xml.
	* return - String[] - the value inside the tags.
	*/
	public static String[] getDomains(String xml){
		// This section is just to find the number of Domains.
		String findStr = "name="; int lastIndex = 0; int domainCount = 0;
		while (lastIndex != -1) {
			lastIndex = xml.indexOf(findStr, lastIndex);
			if (lastIndex != -1) { domainCount++; lastIndex += findStr.length(); }
		}
		if(debug){ System.out.println("DEBUG :: Number of Domains  :"+ domainCount ); }

		String[] list = new String[domainCount];
		String[] tmp = xml.split("/>");
		// commented out because it's too messy to leave even in debug mode.
		// if(debug){ for(int j=0; j<tmp.length; j++){ System.out.println("DEBUG :: Domain ["+j+"] = "+tmp[j]); } } 

		Pattern p = Pattern.compile("\"([^\"]*)\"");
		for(int k=0; k<tmp.length; k++){
			Matcher m = p.matcher(tmp[k]);
			while (m.find()) { list[k] = m.group(1); }
		}
		return list;
	}

	/** getDomains(String, String)
	* This method gets the name attribute of all tags of the given name in an xml string.
	* param - xml - a string containing xml.
	* param - type - a string containing a gateway type.
	* return - String[] - the list names of each gateway of the given type.
	*/
	public static String[] getGateways(File def, String type) {
		// Since there is not really a max number of gateways, and I don't know how many there will be
		// I'm assigning a value at of MAX_GATEWAYS ... if you are close you can edit it above.
		// Empty enties are "trimmed/pruned" below.
		String[] list = new String[MAX_GATEWAYS];
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		try {
			// Creat and parse a document from the xml file
			DocumentBuilder db = dbf.newDocumentBuilder();
			Document doc = db.parse(def);
			doc.getDocumentElement().normalize();
			// Get a list of all the nodes that match our gateway type
			NodeList nodeList = doc.getElementsByTagName(type);
			if(debug){ System.out.println("DEBUG :: Found "+nodeList.getLength()+" "+type+" Gateways"); }
			if (nodeList != null && nodeList.getLength() > 0) {
				for (int j = 0; j < nodeList.getLength(); j++) {
					Element el = (org.w3c.dom.Element) nodeList.item(j);
					String gateway = el.getAttribute("name");
					if(debug){ System.out.println("DEBUG :: Found "+gateway); }
					list[j] = gateway;
				}
			}
		} catch (IOException | ParserConfigurationException | SAXException ex) {
			System.out.println(":: ERROR ::");
			ex.printStackTrace();
		}

		// This is to remove empty members of the array.
		list = Arrays.stream(list).filter(s -> (s != null && s.length() > 0)).toArray(String[]::new);  
		return list;
	}

	/** upzipit(String, String)
	* This is the method that extracts the zip files that make up the DataPower export.
	* It is called on the main file then again for each domain export zip file.
	* param - theZip - a string containing a full path to a zip file.
	* param - theFolder - a string containinga full path to a extract directory ending with a "/" .
	*/
	public static void unZipIt(String theZip, String theFolder) {
		try {
			// Creat the zipFile handle
			ZipFile zipFile = new ZipFile(theZip);
			// Create an enumeration of all the items in the zip file
			Enumeration<?> enu = zipFile.entries();

			if(debug) { System.out.printf("DEBUG :: Extracting: "+theZip); }
			// Extract all of the individual items
			while (enu.hasMoreElements()) {
				// Create a ZipEntry (the format of a zip file entry) from the enumeration
				ZipEntry zipEntry = (ZipEntry) enu.nextElement();
				// Get the name of the item (we will use is a few times)
				String name = zipEntry.getName();
				if(debug) {	System.out.printf("Name: %-30s | Size: %8d | Compressed: %8d\n", name, zipEntry.getSize(), zipEntry.getCompressedSize()); }

				// Create a folder if this time is a folder
				File file = new File(theFolder+name);
				if (name.endsWith("/")) { file.mkdirs(); continue; }

				// Make sure all of the parent directories are in place.
				File parent = file.getParentFile();
				if (parent != null) { parent.mkdirs(); }

				// Do the byte level extract of the compressed file.
				InputStream is = zipFile.getInputStream(zipEntry);
				FileOutputStream fos = new FileOutputStream(file);
				byte[] bytes = new byte[1024];
				int length;
				while ((length = is.read(bytes)) >= 0) { fos.write(bytes, 0, length); }
				// Close the streams used by the byte level extraction.
				is.close();
				fos.close();
			}
			// Close the zip file.
			zipFile.close();
		} catch (IOException ex) {
			System.out.println(":: ERROR ::");
			ex.printStackTrace();
		}
	}

	/** removeTmpDir(String)
	* This is method that cleans up and removes all of the extracted files.
	* param - tmpDir - a string containing the path to the tmpDir.
	*/
	public static void removeTmpDir(String tmp){
		if(debug){ System.out.println("DEBUG :: About to remove "+tmp); }
		//Remove the temporary directory
		try {
			Path directory = Paths.get(tmp);
   			Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
	   			@Override
	   			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException { Files.delete(file); return FileVisitResult.CONTINUE; }
	   			@Override
	   			public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException { Files.delete(dir); return FileVisitResult.CONTINUE; }
   			});
		} catch (IOException ex) {
				System.out.println(":: ERROR ::");
				ex.printStackTrace();
		}
		if(debug) { System.out.println("DEBUG :: "+ tmp +" has been removed."); }
	}
}
