/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package m;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringTokenizer;
import v.File.FileChoose;
import v.File.FileIn;
import v.Log.Logfile;
import v.ShowMessage;
import m.ZipcodeRecord;


/**
 * This class is utilized solely by UIMain.jMenuItemZipcodeActionPerformed()
 * This class reads from free-zip_code_base.csv and populates table zipcodegeo.
 * Table zipcodegeo is used to map zipcodes to maidenheads.
 * All entries in zipcodegeo are for U.S. zipcodes and so zipcoderecord defaults to country="US".
 * Table zipcodegeo is used by CallmaidUtils.queryZipcodegeo() to obtain lat/lon from zipcode.  
 * Method queryZipcodegeo() is used by PopulateCallmaidCQITU to obtain lat/lon from zipcode for estimating maidenhead.
 * It is used the same way in PopulateCallmaidMaidenhead.popcallmaid2() to derive a maidenhead estimate.
 * Following is an explanation of how table zipcodegeo is utilized in PopulateCallmaidMaidenhead.
Populate callmaid.maidenhead from callmaid.zipcode in class PopulateCallmaidMaidenhead.
The main menu item "Populate Callmaid" creates PopulateCallmaidMaidenhead.
The purpose of this class is to populate callmaid.maidenhead given callmaid.zipcode which was provided from FCC database via UIMain.jMenuItemPopCallmaidActionPerformed
records with null maidenhead.  i.e. select * from callmaid where where maidenhead = ''
2) If callmaid.maidenhead is not null then return to 1)
3) Read callmaid.zipcode.
4) Query zipcodegeo wTable zipcodegeo provides link from zipcode to lat/lon.
The original callmaid table contents were loaded from EN.dat by class ReadEntityFile.
1) Get all maidenhead here zipcodegeo.zipcode = callmaid.zipcode. Get latitude and longitude
5) If zipcodegeo not found then return to 1)
6) Convert zipcodegeo.latitude/longitude to maidenhead using Maidenhead.latLonToMaiden()
7) Update callmaid.maidenhead with result of latLonToMaiden()

 * Fields in free-zipcode-database.csv are:
 * 1) Recordnumber: integer
 * 2) zipcode: integer of length 5
 * 3) zipcodetype: "STANDARD" or "PO BOX"
 * 4) City: varchar 40
 * 5) State: varchar 2
 * 6) LocationType: "NOTACCEPTABLE", "PRIMARY"
 * 7) Lat: float
 * 8) Long: float
 * 9) Xaxis: decimal unknown quantity
 * 10) Yaxis: decimal unknown quantity
 * 11) Zaxis: decimal unknown quantity
 * 12) WorldRegion: "NA" presumably because this table is constrained to United States zipcodes
 * 13) Country: "US"
 * 14) LocationText: Usually the same as "city".
 * 15) Location: varchar(2) state abbreviation (or possession e.g. "PR")
 * 16) Decommisioned: varchar unknown quantity.
 * 17) TaxReturnsFiled: varchar "false" or "true"
 * 18) EstimatedPopulation: integer populated sparsely.
 * 19) TotalWages: integer populated sparsely
 * 20) Notes: refer to source of data
 * 
 * 
 * File zip_code_base includes the following fields:
 * 1) zip code
 * 2) state: varchar(2)
 * 3) state: varchar
 * 4) city: varchar
 * 5) latitude: float
 * 6) longitude: float
 * 7) countyfips:  Integer unique identifier of U.S. county
 * 8) countyname: varchar of county
 * 9) dominantareacode: predominant phone area code, 3 integer digits.
 * 10) ziptype: "standard" or "P.O. Box zip code"
 * 11) facilitycode: "P" or "N" ??
 * 12) financenumber: 6 digit number ??
 * 13) CBSAcode: 5 digit number, Core Based Statistical Area
 * 14) DMANameCBSA: Text description of CBSA
 * 15) CBSAStatus: type of CBSA
 * 16) CBSADivision
 * 17) CBSADivisionTitle
 * 18) CBSADivisionStatus
 * 19) Organization
 * 20) TimeZone: varchar(3) 
 * 21) TotalPopulation: integer
 * 22) Age_MedianAge_Pop_Cnt: float median age
 * 23) HouseValue_Median_OU_Cnt: integer dollars
 * 24) Income_MedianFamilyIncome_Fam_Cnt: integer dollars
 * 25) Sex_Female_Pop_Cnt: Integer number of females
 * 26) Sex_Male_Pop_Cnt: Integer number of males

 * @author chas
 */
public class Zipcodegeo 
{
    
private final Logfile logger;
private final String filename;
private FileIn fiObj;
public boolean status;
int totalRecords;
int decodeSuccessesTotal;  // successful decoding in MessageRecord.decodeMessage()
int decodeFailuresTotal;   // failed decoding in MessageRecord.decodeMessage()
int sourceMaidenheadTotal;  // total records with successful decode but no source maidenhead, i.e. no known maidenhead of transmitter.
private boolean debugFlag = Common.debugFlag;
private Maidenhead mhObj;
private Navigation navObj;
private Propdbutils prodb;
private int databaseWrites, databaseWriteFailures;
private int maxcallsign, maxfn, maxln, maxmi, maxst, maxcity, maxstate, maxzip;
private ZipcodeRecord recordEntity;


public Zipcodegeo(String filename)    
  {
  this.logger = Common.logger;
  this.filename = filename;
  this.status=false;
  File f1 = new File(filename);
  if(!f1.exists())
    {
    FileChoose fc = new FileChoose("csv",this.logger);   
    fc.setDialogTitle("Select input file e.g. " + filename);
    filename=fc.open();
    }
  try{this.fiObj = new FileIn(logger,filename);}
  catch(IOException ioe)
    {
    logger.tee("Creation of FileIn object in " + Class.class.getName() + " constructor threw IOException", ioe);
    return;
    }
  this.mhObj = new Maidenhead(this.logger);
  this.navObj = new Navigation(this.logger);
  this.prodb = Common.prodb;
  this.status=true;
  } // end constructor

/**
 * Read entire file into List
 * @return 
 */
public List<String> readFileList()
  {
  Charset cs = StandardCharsets.ISO_8859_1;
  return this.fiObj.readFileInList(cs);
  }

/**
 * Read each line of file, parse into fields by calling parseFields()
 */
public boolean parseFile()
  {
  int x;
  List<String> tlist = this.readFileList();
  String[] fields;
  String[] lineArray = tlist.toArray(new String[0]);
  this.totalRecords=lineArray.length;
  DecimalFormat df = new DecimalFormat("###,###,###");
  ShowMessage sm = new ShowMessage("Processing " + df.format(totalRecords) + " records from file " + filename,50,50);  
  sm.showMessage();
  long starttime = System.currentTimeMillis();
  /*
  for(x=0; x < 1000; x++) // limit to one million records
    {
    currentLine = this.fiObj.readLine();
    if(debugFlag)logger.println("Line " + x + "=" + currentLine);  
    recordEntity = new ZipcodeRecord();
    if(!recordEntity.parseFields(currentLine, this.filename))
       {
       logger.println("ERROR: Failed to parseFields() in file " + filename + " for record number=" + x); 
       this.decodeFailuresTotal++;
       continue;
       }
    else this.decodeSuccessesTotal++;
    //if(this.prodb.submitEntityDatabase(recordEntity)) this.databaseWrites++; // write record to database.
    //  else this.databaseWriteFailures++;
    recordEntity=null;
    } // end loop through each line of entity file
  */
  for(x=0; x < lineArray.length; x++)
    {
    if(debugFlag)logger.println("Line " + x + "=" + lineArray[x]);  
    recordEntity = new ZipcodeRecord();
    if(!recordEntity.parseFields(lineArray[x], this.filename))
       {
       logger.println("ERROR: Failed to parseFields() in file " + filename + " for record number=" + x); 
       this.decodeFailuresTotal++;
       continue;
       }
    else this.decodeSuccessesTotal++;
    if(this.prodb.insertZipcodegeo(recordEntity)) this.databaseWrites++; // write record to database.
    else this.databaseWriteFailures++;
    recordEntity=null;
    } // end loop through each line of entity file
  this.reportSummary(x, filename);
  sm.showOff();
  long endtime = System.currentTimeMillis();
  long elapsedtime = (endtime - starttime)/1000;
  sm=null;
  sm = new ShowMessage("Processed " + df.format(x) + " records in " + elapsedtime + " seconnds.",50,50);  
  sm.showMessage(3);
  sm.showOff();
  return true;
  } // end method parseFile()








public void reportSummarystdout(int x, String filename)
  {
  System.out.println("Processed " + x + " records from file " + filename);
  System.out.println("Total records in file=" + this.filename + "=" + this.totalRecords);
  System.out.println("Total records with successful decoding=" + this.decodeSuccessesTotal);
  System.out.println("Total records with failed decoding=" + this.decodeFailuresTotal);
  //System.out.println("Total records with source maidenhead=" + this.sourceMaidenheadTotal);
  System.out.println("Total database writes=" + this.databaseWrites + ", database write failures=" + this.databaseWriteFailures);
  }


public void reportSummary(int x, String filename)
  {
  logger.println("Processed " + x + " records from file " + filename);
  logger.println("Total records in file=" + this.filename + "=" + this.totalRecords);
  logger.println("Total records with successful decoding=" + this.decodeSuccessesTotal);
  logger.println("Total records with failed decoding=" + this.decodeFailuresTotal);
  //logger.println("Total records with source maidenhead=" + this.sourceMaidenheadTotal);
  logger.println("Total database writes=" + this.databaseWrites + ", database write failures=" + this.databaseWriteFailures);
  this.reportSummarystdout(x, filename);
  }



        
    
} // end class zipcodegeo
