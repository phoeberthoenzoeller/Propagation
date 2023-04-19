/*
 * Copyright Charles Gray.
 * All rights Reserved.
Populate callmaid with FCC database license data.
Admin/Read Entity File reads from file EN.dat or file specified in properties.txt name=entityFile
The EN.dat file is about 200MB in size.  All of it read into memory as a List which is converted to a String[].
For each line in the String[]:
Parse and write to table callmaid as follows: 
     callsign (field 5) to callsign
	first name (field 9) to firstName
	middle initial (field 10) to middleInitial
	last name (field 11) to lastName
	street address (field 16) to street
	city (field 17) to city
	state (field 18) to state
	zip 	(field 19) to zipcode
If you have debugFlag set to true in properties.txt then the program
will require many hours and over 10GB of log space.

 */
package m;

import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.StringTokenizer;
import m.db.ResultSetTableModel;
import v.File.FileChoose;
import v.File.FileIn;
import v.Log.Logfile;
import v.ShowMessage;
import c.CallmaidUtils;
import java.sql.SQLException;



/**
 *
 * @author chas
 */
public class ReadEntityFile 
{
private final Logfile logger;
private final String filename;
private FileIn fiObj;
public boolean status;
int totalRecords;
int zipcodeSuccessesTotal;  
int zipcodeFailuresTotal;  
int parseFailures;  // wrong number of fields parsed
int sourceMaidenheadTotal;  // total records with successful decode but no source maidenhead, i.e. no known maidenhead of transmitter.
int recordCounter;
private boolean debugFlag = Common.debugFlag;
private Propdbutils prodb;
private int databaseWrites, databaseWriteFailures;
private int maxcallsign, maxfn, maxln, maxmi, maxst, maxcity, maxstate;
private Maidenhead mhObj;
private Navigation navObj;
private CallmaidUtils cmUtilsObj;


public ReadEntityFile(String filename)    
  {
  this.logger = Common.logger;
  this.filename = filename;
  this.status=false;
  File f1 = new File(filename);
  if(!f1.exists())
    {
    FileChoose fc = new FileChoose("dat",this.logger);   
    fc.setDialogTitle("Select input file e.g. EN.dat");
    filename=fc.open();
    }
  try{this.fiObj = new FileIn(logger,filename);}
  catch(IOException ioe)
    {
    logger.tee("Creation of FileIn object in ReadEntityFile constructor threw IOException", ioe);
    return;
    }
  this.mhObj = new Maidenhead(this.logger);
  this.navObj = new Navigation(this.logger);
  this.prodb = Common.prodb;
  this.cmUtilsObj = new CallmaidUtils(logger, this.prodb, Common.debugFlag);
  this.status=true;
  } // end constructor


/**
 * Read entire ALL.TXT into List
 * @return 
 */
public List<String> readFileList()
  {
  return this.fiObj.readFileInList();
  }

/**
 * Read each line of EN.dat, parse into fields by calling parseFields()
 */
public void parseFile()
  {
  int x;
  this.zipcodeSuccessesTotal=0;
  this.zipcodeFailuresTotal=0;
  this.parseFailures=0;
  this.recordCounter=0;
  CallmaidRecord callmaidRecordObj;
  List<String> tlist = this.readFileList();
  String[] fields;
  String[] lineArray = tlist.toArray(new String[0]);
  this.totalRecords=lineArray.length;
  DecimalFormat df = new DecimalFormat("###,###,###");
  ShowMessage sm = new ShowMessage("Processing " + df.format(totalRecords) + " records from file " + filename,250,500);  
  sm.showMessage();
  long starttime = System.currentTimeMillis();
  for(x=0; x < lineArray.length; x++)
    {
    //if(x > 50) break;  // debug flagme
    if(debugFlag)logger.println("Line " + x + "=" + lineArray[x]);  
    callmaidRecordObj = this.parseFields(lineArray[x]);
    if(callmaidRecordObj == null) 
      {
      logger.println("ERROR: Failed to parseFields() in file " + filename + " for record number=" + x); 
      continue;
      }
    if(this.insertUpdateCM(callmaidRecordObj)) this.databaseWrites++; // write record to database.    
    else this.databaseWriteFailures++;
    callmaidRecordObj=null;
    if(recordCounter%10000 == 0)
        {
        sm.showOff(); sm=null;
        sm = new ShowMessage("Processing " + df.format(this.totalRecords) + " records from callmaidRecordObj file.  Record count=" + this.recordCounter,250,500);  
        sm.showMessage();
        Common.dbAllTxt.commit();
        System.out.println("recordCounter=" + recordCounter);
        Common.garbageCleanup();
        }
    this.recordCounter++;
    } // end loop through each line of callmaidRecordObj file
  this.reportSummary(x, filename);
  sm.showOff();
  long endtime = System.currentTimeMillis();
  long elapsedtime = (endtime - starttime)/1000;
  sm=null;
  sm = new ShowMessage("Processed " + df.format(x) + " records in " + elapsedtime + " seconnds.",50,50);  
  sm.showMessage(3);
  sm.showOff();
  if(debugFlag)
    {
    System.out.println("maxcallsign=" + this.maxcallsign + ", maxfn=" + this.maxfn + ", maxmi=" + this.maxmi);
    System.out.println("this.maxln=" + this.maxln + ", maxst=" + this.maxst + ", maxcity=" + this.maxcity + ", maxstate=" + this.maxstate);
    }
  } // end method parseFile()







/**
 * Parse a single line of EN.dat into its pipe delimited fields.
 * There are 30 fields in file EN.dat.
 * Assign:
 * field 5: callsign
 * field 9: first name
 * field 10: middle initial
 * field 11: last name
 * field 16: street address
 * field 17: city
 * field 18: state
 * field 19: zip
 * @param line
 * @return MessageRecord object with parsed fields and results of calculations utilizing those fields.  Return null for error.
 */
private CallmaidRecord parseFields(String line)
  {
  String callsign;
  String firstName;
  String middleInitial;
  String lastName;
  String streetAddress;
  String city;
  String state;
  int zip=0;
  int x=0;
  StringTokenizer st=null;
  String[] fields = line.split("\\|");
  for(x=0; x < fields.length; x++)
    {
    if(debugFlag)logger.tprintln("Field " + x + "=" + fields[x]);
    }
  if(x != 24)
    {
    logger.tprintlnwarn("File " + filename + " is expected to have 24 pipe delimeted fields, and I found " + x + " fields. Fields are as follows:");
    for(x=0; x < fields.length; x++)
      {
      if(debugFlag)logger.tprintln("Field " + x + "=" + fields[x]);
      }
    this.parseFailures++;
    return null;
    }
  callsign = fields[4]; 
  firstName = fields[8];
  middleInitial = fields[9];
  lastName = fields[10];
  streetAddress = fields[15];
  city = fields[16];
  state = fields[17];
  if(debugFlag)
    {
    if(callsign.length() > this.maxcallsign) maxcallsign = callsign.length();
    if(firstName.length() > this.maxfn) maxfn = firstName.length();
    if(middleInitial.length() > this.maxmi) maxmi = middleInitial.length();
    if(lastName.length() > this.maxln) maxln = lastName.length();
    if(streetAddress.length() > this.maxst) maxst = streetAddress.length();
    if(city.length() > this.maxcity) maxcity = city.length();
    if(state.length() > this.maxstate) maxstate = state.length();
    }
  if(fields[18].length() < 5)
    {
    logger.tprintlnwarn("Zipcode=" + fields[18] + " is invalid as indicated by a length of " + fields[18].length() + ". 00000 will be assigned.");
    fields[18]="00000";
    this.zipcodeFailuresTotal++;
    }
  if(fields[18].length() > 5) fields[18]=fields[18].substring(0,5);
  try{zip = Integer.parseInt(fields[18]);}
  catch(NumberFormatException nfe)
    {
    this.logger.tee("Failed to parse zip string to integer for string=" + fields[18], nfe);
    this.zipcodeFailuresTotal++;
    }  
  if(zip != 0)this.zipcodeSuccessesTotal++;
  int ITU=0;
  int CQ=0;
  boolean worked=false;
  java.sql.Date crdate = new java.sql.Date(System.currentTimeMillis());
  java.sql.Date qsodate = null;
  String DS="";  // assigned in this.insertUpdateCM()
  String maidenhead="";
  CallmaidRecord callmaidRecordObj = new CallmaidRecord(callsign,maidenhead, firstName, middleInitial, lastName, streetAddress, city, state, zip,"US",ITU,CQ,worked,crdate,qsodate,DS);
  if(debugFlag)callmaidRecordObj.logEntity(callmaidRecordObj);
  return callmaidRecordObj;
  } // end method parseFields()


/**
 * ds[1] is set to "0" as a flag to PopulateCallmaidMaidenhead that this record should be processed by that program.
 1) If callmaid.callsign not found then create callmaid.  Set ds[0]="1", ds[1]="0", ds[2]="F".
 2) If callmaid.callsign is found then update existing record. Set ds[0]="1", ds[1]="0", and preserve ds[2] unless they are null in which case set them to "1" and "F".
 3) Update mdate.  mdate is automatically set to current date in contructor of CallmaidRecord.
 4) If crdate = null then set crdate.

 * @param cml
 * @return 
 */
private boolean insertUpdateCM(CallmaidRecord cml)
  {
  // Is there an existing callmaid record with this callsign?
  ResultSetTableModel rstml = prodb.queryCallmaid("DS, crdate", "callsign = '" + cml.callsign + "'");  
  if(rstml==null){logger.e("Failed to query callmaid for callsign=" + cml.callsign); return false;}
  int rowcount = rstml.getRowCount();
  if(rowcount==0) // create new callmaid record
    {
    cml.DS="10F";
    if(prodb.insertUpdateCallmaidRecord(cml,"I")) return true;
    else return false;
    } // end create new callmaid record.
  try{rstml.rsl.absolute(1);}
  catch(SQLException se){logger.ee("Failed to absolute(1) in rstml.",se); return false;}
  String oldDS;
  try{oldDS = rstml.rsl.getString("DS");}
  catch(SQLException se){logger.ee("Failed to getString(DS) from callmaid record.",se); return false;}
  String newDS = cmUtilsObj.dsUpdate(oldDS, '1', 0);
  char ds1 = cmUtilsObj.dsQuery(newDS,1);
  newDS=cmUtilsObj.dsUpdate(newDS,'0',1);
  char ds2 = cmUtilsObj.dsQuery(newDS,2);
  if(ds2=='0' || ds2=='1') newDS=cmUtilsObj.dsUpdate(newDS,'F',2);
  if(cml.crdate==null)cml.crdate=cml.mdate;
  cml.DS=newDS;
  if(prodb.insertUpdateCallmaidRecord(cml,"S")) return true;
  else return false;
  } // end insertUpdateCM()



public void reportSummarystdout(int x, String filename)
  {
  System.out.println("Processed " + x + " records from file " + filename);
  System.out.println("Total records in file=" + this.filename + "=" + this.totalRecords);
  System.out.println("Total records with successful decoding=" + this.zipcodeSuccessesTotal);
  System.out.println("Total records with failed decoding=" + this.zipcodeFailuresTotal);
  System.out.println("Total parse failures=" + this.parseFailures);
  System.out.println("Total database writes=" + this.databaseWrites + ", database write failures=" + this.databaseWriteFailures);
  }


public void reportSummary(int x, String filename)
  {
  logger.println("Processed " + x + " records from file " + filename);
  logger.println("Total records in file=" + this.filename + "=" + this.totalRecords);
  logger.println("Total records with successful decoding=" + this.zipcodeSuccessesTotal);
  logger.println("Total records with failed decoding=" + this.zipcodeFailuresTotal);
  logger.println("Total parse failures=" + this.parseFailures);
  logger.println("Total database writes=" + this.databaseWrites + ", database write failures=" + this.databaseWriteFailures);
  this.reportSummarystdout(x, filename);
  }


/*
private void reportMessageRecord(MessageRecord atrl)
  {
  // report ldt, radioFrequency, rxtx, mode, rssi,timeOffset, audioFrequency, message, callerCallsign, 
  // callerMaiden, respondentCallsign, respondentMaiden, sourceMaidenhead, sourceLat, sourceLon,
  // distanceBetween, initialBearing, 
  // respondentReportedrss, callerReportedrss,
  // contestIdentifier
  // datetime needs '2015-11-05 14:29:36'
  DateTimeFormatter format1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");  
  logger.println("+++++++++++++++++++++");
  logger.println("Time/date=" + atrl.ldt.format(format1) + ", radioFrequency=" + atrl.radioFrequency
    + ", rxtx=" + atrl.rxtx + ", mode=" + atrl.mode
    + ", rssi=" + atrl.rssi
    + ", timeOffset=" + atrl.timeOffset
    + ", audioFrequency=" + atrl.audioFrequency);
  logger.println("Message=" + atrl.concatMessage(atrl.message));
  logger.println("Caller callsign=" + atrl.callerCallsign + ", caller maidenhead=" + atrl.callerMaiden + ", callerReportedrss=" + atrl.callerReportedrss);
  logger.println("Respondent callsign=" + atrl.respondentCallsign + ", respondent maidenhead=" + atrl.respondentMaiden);
  logger.println("Source maidenhead=" + atrl.sourceMaidenhead + ", source lat=" + atrl.sourceLat + ", source lon=" + atrl.sourceLon);
  logger.println("Distance between=" + atrl.distanceBetween + ", bearing=" + atrl.initialBearing);
  logger.println("Respondent reported rss=" + atrl.respondentReportedrss + ", caller reported rss=" + atrl.callerReportedrss);
  logger.println("Contest identifier=" + atrl.contestIdentifier);
  logger.println("Message type=" + atrl.messageType);
  logger.println("-----------------------");
 
 
  }
  */  
    
    
    
} // end class ReadEntityFile
