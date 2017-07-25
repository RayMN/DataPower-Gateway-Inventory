# DataPower-Gateway-Inventory
# DEPRICATION NOTICE 
## DEPRICATION NOTICE - PLEASE SEE RayMN/DataPower-Configuation-Inventory
# DEPRICATION NOTICE 

## Synopsis
This is a tool that extracts a list of all of the gateways on a DataPower appliance from an export of the entire appliance.

## Code Example
There are lots of comments in the .java file, I believe in heavy commenting, but the basic steps ***paraphrased*** are:
```
1) Open the parent zip file
2) Gater some basic appliance information including a list of all domains from the export.xml file
3) From that list of domains:
  4) Extract the zip file for each domain
  5) Get a list of Gateways deployed to that domain
  6) Optionally get additional information on each Gateway
7) Create a csv file with the above information. (actually happens throughout)
8) Clean up all the extracted files
```
## Motivation
A friend (and soon to be co-worker) asked me if there was a way to get a listing of all the gateways on a DataPower appliance. The short answer from IBM when I posted the question to Developer Works was "No, we recomment you open an RFE." Having been down that path and understanding the speed with which these things happen (that's not a dig, just a statement ... I realize there is a lot to do and it gets ***complicated*** when you are dealing with that amount of code) I decided that it would probably be best to do this outside of DataPower. I thought about using CLI or SSH and decided to keep it entirely off of DataPower and use the information found in the export.xml files in an export of the entire appliance.

## Installation
This is a simple command line Java program, it does require Java v1.8+
There are sample csv files from an export of one of my old demo virtual appliances

## Usage
```
  Usage: java ConfigInventory zipFile tmpDir csvFile
  Where:
    zipFile = Absolute path to the DataPower export zip file
    tmpDir  = Absolute path to temporary directory to extract zip files
    csvFile = Absolute path and name of the output csv file
    -d      = Optional: When present adds the object details to the output
    -debug  = Optional: When present generates verbose DEBUG messages in the console
    -h      = Optional: This message
Example > java ConfigInventory /data/IDGv720-A.zip /data/dp-export/ /data/SampleOutput.csv -d -debug
```
## Contributors
The code may not be the ***slickest*** code ever written (some of it was done in BFH mode) but I think it works well enough to get started and it's not really meant a high performance application. It's a tool. Like a hammer (mentioned above). I am more than open to constructive criticizm, suggestions for improvement, and/or help in improving the tool.

## License
This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
