/**
 * Copyright Charles Gray.
 * All rights Reserved.
 * Reads the ALL.TXT generated by wsjt-x and populates ALLTXT table and CALLMAID table.
 * Common.callsign = properties.txt value assigned to label "callsign".
 * For each record in ALL.txt wherein if(callsign != null && (atrec.callerCallsign.equalsIgnoreCase(callsign) || atrec.respondentCallsign.equalsIgnoreCase(callsign)))
 * (Common.callsign != null && (atrec.callerCallsign.equalsIgnoreCase(Common.callsign) || atrec.respondentCallsign.equalsIgnoreCase(Common.callsign)))
 * then create a MessageRecord.java object.
 * Use Propdbutils.submitAlltxtDatabase(atrec) to write contents of MessageRecord to database table ALLTXT.
 * All columns of table alltxt are populated by submitAlltxtDatabase().
 * The records are linefeed delimited.
 * The fields are space delimited.  Some fields are separated by multiple spaces.
 * The fields include:
 *    Zulu Date/time as YYMMDD_HHMMSS
 *    Decimal frequency in MHz.
 *    [Rx][Tx] indicating transmit or receive
 *    Mode e.g. "FT8" or "FT4"
 *    Received signal level (0 for transmissions)
 *    Decimal time offset in seconds.
 *    Audio frequency in hertz.
 *    Message:
 *      A) "CQ" message, i.e. first field message is "CQ".   
        1) If "CQ" then format is "CQ" <caller callsign> <4 char callers maidenhead>
        2) Answer to "CQ" is <caller callsign> <respondent callsign> <respondent 4 char maidenhead>
        3) Caller then sends report as <respondent callsign> <caller callsign> <decimal received signal strength>
        4) Respondent then sends report as <caller callsign> <respondent callsign> <decimal received signal strength prefixed by "R">
        5) Caller sends "RRR" as <respondent callsign> <caller callsign> "RRR"
        6) Respondent sends "73" as <caller callsign> <respondent callsign> "73".
  The following is from the wsjt-x manual:
  Standard messages consist of two callsigns (or CQ, QRZ, or DE and one callsign) 
  * followed by the transmitting station’s grid locator, a signal report, R plus 
  * a signal report, or the final acknowledgements RRR or 73. These messages are 
  * compressed and encoded in a highly efficient and reliable way. In uncompressed 
  * form (as displayed on-screen) they may contain as many as 22 characters. Some 
  * operators prefer to send RR73 rather than RRR. This is workable because RR73 
  * is encoded as a valid grid locator, one unlikely ever to be occupied by an 
  * amateur station.

  Signal reports are specified as signal-to-noise ratio (S/N) in dB, using a 
  * standard reference noise bandwidth of 2500 Hz. Thus, in the example message above, 
  * K1ABC is telling G0XYZ that his signal is 19 dB below the noise power in 
  * bandwidth 2500 Hz. In the message at 0004, G0XYZ acknowledges receipt of that 
  * report and responds with a –22 dB signal report. JT65 reports are constrained 
  * to lie in the range –30 to –1 dB, and values are significantly compressed above 
  * about -10 dB. JT9 supports the extended range –50 to +49 dB and assigns more 
  * reliable numbers to relatively strong signals.  
 */
package m;
import v.File.FileIn;
import v.Log.Logfile;
import java.util.List;
import java.io.IOException;
import java.io.File;
import java.util.StringTokenizer;
import java.time.LocalDateTime;
import v.File.FileChoose;
import v.ShowMessage;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import javax.swing.JOptionPane;
import m.db.ResultSetTableModel;
import c.Time.TimeUtils;
import m.PopulateAlltxt;



/**
 *
 * @author chas
 */
public class ReadWSJTXFile 
{
private final Logfile logger;
private final String filename;
private FileIn fiObj;
public boolean status;
private MessageRecord atrec;
int totalRecords;
long elapsedTime;
private boolean debugFlag = Common.debugFlag;
private Propdbutils prodb;
private TimeUtils tuObj;
private final PopulateAlltxt popAllObj;
public int parseFailures;
private boolean doShow=true;
//private long elapsedmillis;



/**
 * Open the ALL.TXT or equivalent file.
 * @param filename 
 */
public ReadWSJTXFile(String filename)    
  {
  this.logger = Common.logger;
  this.tuObj = new TimeUtils(this.logger);
  this.prodb = Common.prodb;
  this.popAllObj = new PopulateAlltxt(this.logger, this.prodb, this.debugFlag);  
  this.filename = filename;
  this.status=false;
  File f1 = new File(filename);
  if(!f1.exists())
    {
    FileChoose fc = new FileChoose("txt",this.logger);   
    fc.setDialogTitle("Select input file e.g. ALL.txt");
    filename=fc.open();
    }
  try{this.fiObj = new FileIn(logger,filename);}
  catch(IOException ioe)
    {
    logger.tee("Creation of FileIn object in ReadWSJTXFile constructor threw IOException", ioe);
    return;
    }
  this.status=true;
  } // end constructor






/**
 * Called by UIMain
 * Read entirety of file into a List, convert to String[] lineArray.
 * Read each line of ALL.TXT from lineArray, parse into fields. Processing is a three step process:
 * 1) Calls this.parseFields() to create a MessageRecord from fields and call decodeMessage()
 * 2) Calls PopulateAlltxt.proecessMessageRecord() to create or update table CALLMAID record.
 * 3) Calls PopulateAlltxt.writeToDatabase() to write the MessageRecord to table ALLTXT
 * callsign is Common.callsign
 * If called with callsign then read only those ALL.TXT records containing that callsign.
 * If called with null callsign then read all ALL.TXT records.
 * Updates this.parseFailures.
 */
public boolean parseFile(String myCallsign)
  {
  int x;
  // successful decoding in MessageRecord.decodeMessage()
  logger.tprintln("Begin method parseFile() with myCallsign=" + myCallsign);
  List<String> tlist = this.fiObj.readFileInList(); // read entire All.txt file into List of Strings
  if(tlist==null)return false;
  String[] fields;
  String[] lineArray = tlist.toArray(new String[0]);  // convert List to array of Strings.
  this.totalRecords=lineArray.length;
  DecimalFormat df = new DecimalFormat("###,###,###");
  ShowMessage sm = new ShowMessage("Processing " + df.format(totalRecords) + " records from file " + filename,250,500);  
  sm.showMessage();
  long starttime = System.currentTimeMillis();
  for(x=0; x < lineArray.length; x++) // for each record in lineArray[], text records of All.txt
    {
    //if(x > 100) break; // flagme debug
    if(debugFlag)logger.println("Line " + x + "=" + lineArray[x]);  
    this.atrec = this.parseFields(lineArray[x]); // MessageRecord atrec.  parseFields performs MessageRec creation and decodeMessage()
    //logger.println("Elapsedmillis parseFields = " + (System.currentTimeMillis() - elapsedmillis));
    if(this.atrec == null) // returned error
      {
      logger.println("ERROR: Failed to parseFields() for record number " + x + " with content=" + lineArray[x]); 
      this.parseFailures++;
      continue;
      }
    if(atrec.messageType == 11 || atrec.messageType == 10 || atrec.messageType == 0)
      {
      logger.printlnwarn("Messagetype=" + atrec.messageType + " will not be processed. ldt=" + atrec.ldt + ", message=" + atrec.concatMessage(atrec.message));
      continue;
      }
    //this.elapsedmillis=System.currentTimeMillis();
    if(mycallsignFilter(atrec, myCallsign)) continue; // if myCallsign is specified and it is not in this MessageRecord then skip the record.
    if(!this.popAllObj.processMessageRecord(atrec)) 
    logger.println("ERROR: Failed processAndWrite() for record number=" + x + " with contents=" + lineArray[x]); 
    //logger.println("Elapsedmillis processMessageRecord = " + (System.currentTimeMillis() - elapsedmillis));
    //this.elapsedmillis=System.currentTimeMillis();   
    this.popAllObj.writeToDatabase(atrec);
    //logger.println("Elapsedmillis writeToDatabase = " + (System.currentTimeMillis() - elapsedmillis));
    if(debugFlag)this.reportMessageRecord(atrec);
    this.popAllObj.atrec = null;
    if(x%100 == 0)
      {
      if(this.doShow)
        {
        sm.showOff(); sm=null;
        sm = new ShowMessage("Processed " + df.format(x) + " of " + df.format(lineArray.length) + " records",250,500);  
        sm.showMessage();
        }
      System.out.println("recordCounter=" + x + ". Parse failures=" + parseFailures);
      Common.dbAllTxt.commit();
      Common.garbageCleanup();
      }
    }  // end for each line in ALL.TXT
  Common.dbAllTxt.commit();  // flagme trying to commit changes before next database connection.
  sm.showOff();
  long endtime = System.currentTimeMillis();
  this.elapsedTime = (endtime - starttime)/1000;
  String reportMessage = this.reportSummarystdout(this.popAllObj.decodeSuccessesTotal, filename);
  String messageToUser="Processed " + df.format(x-1) + " records in " + this.elapsedTime + " seconds." + reportMessage;
  JOptionPane.showMessageDialog(null, messageToUser ,"Import of " + filename + " complete.",JOptionPane.INFORMATION_MESSAGE);
  //sm=null;
  //sm = new ShowMessage(messageToUser ,300,500);  
  //sm.showMessage(30);
  //sm.showOff();
  this.reportSummary(x, filename);
  return true;
  } // end method parseFile()





/**
 * Parse a single line of ALL.TXT into its space delimited fields.
 * Assign:
 *  field 1: LocalDateTime ldt,
 *  field 2: float radioFrequency
 *  field 3: String rxtx [Rx][Tx]
 *  field 4: String mode e.g. "FT8"
 *  field 5: int rssi, received signal strength.  Set to 0 when transmitting.
 *  field 6: float timeOffset
 *  field 7: int audioFrequency
 *  Message fields
 * 
 * 
 * @param line
 * @return MessageRecord object with parsed fields and results of calculations utilizing those fields.  
 Return null for fatal error. Parse errors and non ft8/ft4 mode records are fatal. 
 A failure to MessageRecord.decode() is not considered fatal.
 This method called exclusively by this.parseFile()
 This method utilizes class variable this.atrec.
 */
private MessageRecord parseFields(String line)
  {
  //logger.tprintln("Begin method parseFields. Reset elapsedmillis"); this.elapsedmillis=System.currentTimeMillis();
  LocalDateTime ldt;
  float radioFrequency;
  String rxtx;
  String mode;
  float timeOffset;
  int rssi;
  int audioFrequency;
  String[] fields = this.tokenize(line);
  // 1) Read each field from tokenized line of All.txt.  If value is not valid then return null.
  ldt = this.tuObj.parseLocalDateTime(fields[0]); // Time/date
  if(ldt==null){logger.println("ERROR: failed to parse time/date=" + fields[0]); return null;}
  try{radioFrequency = Float.parseFloat(fields[1]);} //radioFrequency
  catch(NumberFormatException nfe)
    {
    this.logger.tee("Failed to parse frequency string to float for string=" + fields[1], nfe);
    return null;
    }  
  rxtx = fields[2];  // rxtx
  mode = fields[3];  // mode
  try{rssi = Integer.parseInt(fields[4]);}   // rssi
  catch(NumberFormatException nfe)
    {
    this.logger.tee("Failed to parse rssi string to integer for string=" + fields[4], nfe);
    return null;
    } 
  try{timeOffset = Float.parseFloat(fields[5]);} // timeOffset
  catch(NumberFormatException nfe)
    {
    this.logger.tee("Failed to parse timeOffset string to float for string=" + fields[5], nfe);
    return null;
    } 
  try{audioFrequency = Integer.parseInt(fields[6]);} // audioFrequency
  catch(NumberFormatException nfe)
    {
    this.logger.tee("Failed to parse audioFrequency string to integer for string=" + fields[6], nfe);
    return null;
    }  
  int messageFieldCount = fields.length - 7;
  String[] message= new String[messageFieldCount];
  for(int y=0; y < messageFieldCount; y++)
   {
   message[y]=fields[y+7];  // message
   if(debugFlag)logger.println("Message[" + y + "]=" + message[y]);
   }
  
  // 2) Using the parsed fields create the MessageRecord object.
  return popAllObj.createMessageRecord(ldt, radioFrequency, rxtx, mode, timeOffset, rssi, audioFrequency, message, Common.messTypeString, false, null, null);
  } // end method parseFields()

 



// Parse line of All.txt splitting on space character.
private String[] tokenize(String line)
  {
  StringTokenizer st = new StringTokenizer(line," ");
  int tokencnt=st.countTokens();
  int x=0;
  String[] fields = new String[tokencnt];
  while (st.hasMoreTokens()) 
    {
    fields[x]=st.nextToken();
    if(debugFlag)logger.tprintln("Field " + x + "=" + fields[x]);
    x++;
    }
  return fields;
  }


/**
 * Return true if mycallsign is specified and it does match callercallsign or repondentcallsign in this MessageRecord
 * Return true if we should skip this record.
 * @param atrec
 * @param myCallsign
 * @return 
 */
private boolean mycallsignFilter(MessageRecord atrec, String myCallsign)
  {
  if(myCallsign==null)return false;// do not skip this record.  Process it.
  if(myCallsign != null)  // write only those records including the user callsign, i.e. myCallsign
    {
    if((atrec.callerCallsign != null && atrec.callerCallsign.equalsIgnoreCase(myCallsign))
      || (atrec.respondentCallsign != null && atrec.respondentCallsign.equalsIgnoreCase(myCallsign))
      ) return true;
    }    
  return false;  // do not skip this record.  Process it.
  }




public String reportSummarystdout(int recordsdecoded, String filename)
  {
  String retval;
  System.out.println("Decoded " + recordsdecoded + " records from file " + filename);
  System.out.println("Total records in file=" + this.filename + "=" + this.totalRecords);
  System.out.println("Elapsed time= " + this.elapsedTime + " seconds.");
  System.out.println("Duplicate records=" + this.prodb.duplicates);
  retval="\nDecoded " + recordsdecoded + " records from file " + filename + "\n";
  retval +="Total records in file=" + this.filename + "=" + this.totalRecords + "\n";
  retval +="Total parse failures=" + this.parseFailures + "\n";
  retval +="Duplicate records=" + this.prodb.duplicates + "\n";
  return retval;
  }
public void reportSummary(int recordsdecoded, String filename)
  {
  logger.println("Processed " + recordsdecoded + " records from file " + filename);
  logger.println("Total records in file=" + this.filename + "=" + this.totalRecords);
  logger.println("Total parse failures=" + this.parseFailures);
  logger.println("Elapsed time= " + this.elapsedTime + " seconds.");
  logger.println("Duplicate records=" + this.prodb.duplicates);
  }



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






} // end class ReadWSJTXFile