/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package m;

import java.time.LocalDateTime;
import v.Log.Logfile;
import m.Maidenhead;



/**
 * This class contains a superset of the columns in table ALLTXT and is produced in order to populate a row in that table.
 * @author chas
 */
public class MessageRecord 
{
final Logfile logger;
public final LocalDateTime ldt;  // Alltxt.ldt.  UDP.ldt.
public final float radioFrequency;  // alltxt.radiofrequency.  UDP.radiofrequency (assigned in messagetype=1)
public final String rxtx;    // alltxt.rxtx.  UDP.rxtx (assigned in messagetype=1)
public final String mode;    // alltxt.mode.  UDP.modeString
public final float timeOffset;  // alltxt.timeoffset.    UDP.deltaTime
public final int rssi;         // alltxt.rssi.      UDP.snr
public final int audioFrequency;  //alltxt.audiofrequency.  UDP.deltaFreq
public final String[] message;   // alltxt.message.      UDP.message
public int messageType;  // alltxt.messagetype.  From MessageRecord.decode().
public String callerCallsign;  // alltxt.callercallsign.  From MessageRecord.decode().
public String callerCallsignPrefix;  // From MessageRecord.isolateCallsign().
public String callerMaiden=null;   // alltxt.callermaiden.  From MessageRecord.decode().
public String respondentCallsign;  // alltxt.respondentcallsign.  From MessageRecord.decode().
public String respondentMaiden=null;  // alltxt.respondentmaiden.  From MessageRecord.decode().
public int callerReportedrss;      // alltx.callerreportedrss.  From MessageRecord.decode(). case 5.
public int respondentReportedrss;  // alltxt.respondentreportedrss.   From MessageRecord.decode(). case 6.
public String contestIdentifier;  // alltxt.contestidentifier.   From MessageRecord.decode(). case 1.
public String sourceMaidenhead; // either callerMaiden or respondentMaiden depending on who is transmitting.  alltxt.sourcemaiden
public String sourceCallsign;  // either callerCallsign or respondentCallsign depending on who is transmitting.
public String sourceIs;  // (C)aller or (R)espondent.
public double sourceLat, sourceLon;  // latitude and longitude of sourceMaidenhead.  Provided by calling class.  alltxt.sourcelat, sourcelon
public double homeLat, homeLon;  // latitude and longitude of homeQTH maidenhead locator.  Provided by calling class.   
public double distanceBetween;  // distance between homeQTH and sourceMaidenhead.  Provided by calling class.   alltxt.distanceBetween
public double initialBearing;  // bearing from homeQTH to sourceMaidenhead.  Provided by calling class.          alltxt.initialbearing
//public String[] messTypeString={"Invalid","CQ","QRZ","DE","Response to CQ","Caller report",
//    "Respondent report","Caller RRR","Respondent 73","Unused","No Message","Uncategorized message type"};
String[] messTypeString;
private final boolean debugFlag;
public boolean decodeStatus;  // was decode successful? i.e. did we identify the transmitting callsign?
public boolean lowconfidence;  // Not in ALL.TXT. Defaults to false.  Used exclusively to send message type 4 to initiate a QSO.
public byte[] qtimeBytes;      // Not in ALL.TXT. Default is null.    Used exclusively to send message type 4 to initiate a QSO. flagme you should reverse engineer the qtime parse in UDPWSJTX
public byte[] deltaTimeBytes;      // bytes of this.timeOffset. Not in ALL.TXT. Default is null.    Used exclusively to send message type 4 to initiate a QSO. flagme you should reverse engineer the qtime parse in UDPWSJTX



public MessageRecord(Logfile logger, LocalDateTime ldt, float radioFrequency, 
          String rxtx, String mode, float timeOffset, int rssi, int audioFrequency, 
          String[] message, String[] mst, boolean lowconfidence, byte[] qt, byte[] dtb)
  {
  this.logger = logger;
  this.ldt = ldt;
  this.radioFrequency = radioFrequency;
  this.rxtx = rxtx;
  this.mode = mode;
  this.timeOffset = timeOffset;
  this.rssi = rssi;
  this.audioFrequency = audioFrequency;
  this.message = message; 
  this.messTypeString=mst;
   // 0="Invalid", 1="CQ", 2="QRZ", 3="DE", 4= response to CQ, 5=caller report, 
   // 6=respondent report, 7)caller "RRR", 8)respondent "73"
   // 9=Unused message type
   // 10=no message, 
   // 11=Uncategorized message type of two or fewer arguments
  this.lowconfidence = lowconfidence;
  this.qtimeBytes = qt;
  this.deltaTimeBytes = dtb;
  this.debugFlag=Common.debugFlag;
  } // end constructor


public MessageRecord()
  {
  logger=null;
  ldt=null;
  radioFrequency=0;
  rxtx="";
  mode="";
  timeOffset=0;
  rssi=0;
  audioFrequency=0;
  message=null;
  debugFlag=Common.debugFlag;  
  }


/**
 * MessageTypes:  There are just two messagetypes that wherein maidenhead is transmitted, the initial caller "CQ" (type 1) and the respondent response (type 4).
 *      0) Invalid.  i.e. messagetype was not assigned.
        1) "CQ" message, i.e. first field message is "CQ".   
        1) If "CQ" then recognized formats are as follows:
          a) "CQ" <caller callsign> <4 char callers maidenhead> or
          b) "CQ", {"DX","QRP","POTA","state abbreviate"], <caller callsign>, <caller maidenhead>
        2) "QRZ" message format: "QRZ" <caller callsign> <caller maidenhead>
        3) "DE"
        4) Response to "CQ" is <caller callsign> <respondent callsign> <respondent 4 char maidenhead>
        5) Caller then sends report as <respondent callsign> <caller callsign> <decimal received signal strength>
        6) Respondent then sends report as <caller callsign> <respondent callsign> <decimal received signal strength prefixed by "R">
        7) Caller sends "RRR" as <respondent callsign> <caller callsign> "RRR" or "RR73"
        8) Respondent sends "73" as <caller callsign> <respondent callsign> "73".
        9) Unused message type
        10) No message.  Message column is null.  Abort the decode.
        11) Uncategorized message type. We could not identify the transmitting callsign.
The following is from the wsjt-x manual:
  Standard messages consist of two callsigns (or CQ(calling any station), 
  * QRZ(who is calling me), or DE and one callsign) 
  * followed by the transmitting station’s grid locator, a signal report, R plus 
  * a signal report, or the final acknowledgements RRR or 73. These messages are 
  * compressed and encoded in a highly efficient and reliable way. In uncompressed 
  * form (as displayed on-screen) they may contain as many as 22 characters. Some 
  * operators prefer to send RR73 rather than RRR. This is workable because RR73 
  * is encoded as a valid grid locator, one unlikely ever to be occupied by an 
  * amateur station.
*/
/**
 * 
 * @return true for succssful decoding, false for failure.
 */
public boolean decodeMessage()
  {
  this.decodeStatus=false;
  this.sourceMaidenhead=null; this.sourceCallsign=null;// this is the callsign whose transmission you are hearing.
  String[] m = this.message;
  this.messageType=0; // 0 is not a valid messageType.  No message should end with messagetype.
  int mfCount=m.length;
  if(mfCount==0){messageType=10; return false;} // 10=No message
  String lastMessToken = this.message[mfCount - 1];
  if((lastMessToken.startsWith("a")&&lastMessToken.length()==2) && mfCount==4) mfCount=3;
  if(m[0].equalsIgnoreCase("CQ")) this.messageType=1;   //CQ
  if(m[0].equalsIgnoreCase("QRZ")) this.messageType=2; //QRZZ. Who is calling me?/You are being called by...   
  if(m[0].equalsIgnoreCase("DE")) this.messageType=3; //DE
  if(mfCount < 3 && messageType==0) // two or fewer arguments should have been messagetypes 1,2,3
    {
    messageType=11;  // 11=uncategorized message type of two or fewer arguments
    logger.tprintln("Failed to categorize messageType for message=" + this.concatMessage(m) + ". messageType set to 11.");
    } // Unrecognized message type
  if(mfCount==3 && this.messageType==0)// then 4)response to CQ, or 5)caller report, 6)respondent report, 7)caller "RRR", 8)respondent "73"
    { 
    if(m[2].equalsIgnoreCase("73")) messageType=8;              //8=respondent sends "73" form is: <callerCallsign> <respondentCallsign> "73"
    else if(this.isInteger(m[2])) messageType=5; // caller report.  respondent,caller,rssi
    else if(m[2].equalsIgnoreCase("RRR") || m[2].equalsIgnoreCase("RR73")) messageType=7;  //7=caller sends "RRR" or "RR73"
    else if(m[2].startsWith("R") && this.isInteger(m[2].substring(1))) messageType=6;
    else 
      {
      // if m[2] is a valid maidenhead then it will be assumed that the messagetype=4
      // 4= response to CQ. standard exchange respondent answer to CQ: <callerCallsign>, <respondentCallsign>, <respdondent maidenhead>
      if(!m[0].equalsIgnoreCase("TNX"))
        {
        Maidenhead mh = new Maidenhead(this.logger);
        if(mh.validityCheck(m[2])) this.messageType=4;
        }
      else messageType=11;
      } // end message field count is 3 and messagetype not already assigned.
    }  // end mfCount==3
  
  switch (messageType)
    {
    case 0: logger.tprintln("Failed to categorize messageType for message=" + this.concatMessage(m)); // invalid message type
      messageType=11;  // 11=unrecognized message type
      return false;
    case 1: //1=CQ.  "CQ" [contest identifier] callerCallsign callerMaiden
      if(mfCount==1) // "CQ" without callsign is invalid.
        {
        messageType=11;
        break;
        }
      else if(mfCount==2) // "CQ" <callerCallsign>
        {
        this.callerCallsign=this.isolateCallsign(m[1]);
        this.sourceCallsign=this.callerCallsign;
        this.sourceIs="C";
        this.callerMaiden=null;
        this.sourceMaidenhead=null;
        }
      else if(mfCount==3) // "CQ" <callerCallsign> <callerMaiden> or rarely "CQ" "QSL" <callerCallsign>
        {                 // or "CQ" <country code | state code | contest identifier | "DX"> <callerCallsign>   flagme this is not yet coded
        this.callerCallsign=this.isolateCallsign(m[1]);    
        this.callerMaiden=m[2];
        this.sourceMaidenhead=m[2];
        this.sourceCallsign=this.callerCallsign;
        this.sourceIs="C";
        if(m[1].equalsIgnoreCase("QSL")) // to accommodate format  "CQ" "QSL" <callerCallsign>
          {
          this.contestIdentifier=m[1];
          this.callerCallsign=this.isolateCallsign(m[2]);
          this.sourceMaidenhead=null;
          this.sourceCallsign=this.callerCallsign;
          this.sourceIs="C";
          this.callerMaiden="";
          }
        }
      else if(mfCount==4)// "CQ" <country code | state code | contest identifier | "DX"> <callerCallsign> <callerMaiden>
        {
        this.contestIdentifier=m[1];
        this.callerCallsign=this.isolateCallsign(m[2]);    
        this.callerMaiden=m[3];
        this.sourceMaidenhead=m[3];
        this.sourceCallsign=this.callerCallsign;
        this.sourceIs = "C";
        }
      else if(mfCount > 4)  // We just ignore tokens beyond the fourth.
        {
        logger.tprintln("WARNING: Anomalous CQ message of " + mfCount + " tokens.= " + concatMessage(m));
        this.contestIdentifier=m[1];
        this.callerCallsign=this.isolateCallsign(m[2]);    
        this.callerMaiden=m[3];
        this.sourceMaidenhead=m[3];
        this.sourceCallsign=this.callerCallsign;
        this.sourceIs = "C";
        }
      break;
    case 2:  // 2=QRZ.  "QRZ",callerCallsign,callerMaiden; "QRZ",callerCallsign,null
        if(mfCount != 3){logger.tprintln("WARNING: Unhandled QRZ message = " + concatMessage(m)); messageType=11;return false;} 
        this.callerCallsign=this.isolateCallsign(m[1]);
        this.callerMaiden=m[2];
        this.sourceMaidenhead=m[2];
        this.sourceCallsign=this.callerCallsign;
        this.sourceIs = "C";
        break;
    case 3:   // 3=DE.  "DE",callerCallsign,callerMaiden;  "DE",callerCallsign,null
        if(mfCount != 3){logger.tprintln("WARNING: Unhandled DE message = " + concatMessage(m)); messageType=11; return false;} 
        this.callerCallsign=this.isolateCallsign(m[1]);
        this.callerMaiden=m[2];
        this.sourceMaidenhead=m[2];
        this.sourceCallsign=this.callerCallsign;
        this.sourceIs = "C";
        break;
    case 4:              // 4=response to CQ. callerCallsign, respondentCallsign, respondentMaidenhead;  "CQ",callerCallsign,null
        this.callerCallsign=this.isolateCallsign(m[0]);
        this.respondentCallsign=this.isolateCallsign(m[1]);
        this.respondentMaiden=m[2];
        this.sourceMaidenhead=m[2];
        this.sourceCallsign=respondentCallsign;
        this.sourceIs = "R";
        break;
    case 5:                                        // 5=caller report.  respondentCallsign, callerCallsign, respondent rssi received by caller.
        this.respondentCallsign=this.isolateCallsign(m[0]);
        this.callerCallsign=this.isolateCallsign(m[1]);
        this.sourceCallsign=callerCallsign;
        this.sourceIs = "C";
        this.callerReportedrss=Integer.parseInt(m[2]);
        break;
    case 6: // 6=respondent report.  callerCallsign, respondentCallsign, caller rssi received by respondent preceded by "R"
        this.callerCallsign=this.isolateCallsign(m[0]);
        this.respondentCallsign=this.isolateCallsign(m[1]);
        this.sourceCallsign=respondentCallsign;
        this.sourceIs = "R";
        this.respondentReportedrss=Integer.parseInt(m[2].substring(1)); // m[2] starts with "R"
        break;
    case 7: // 7=caller sends "RRR".  respondentCallsign, callerCallsign, "RRR" or "RR73"
        this.respondentCallsign=this.isolateCallsign(m[0]);
        this.callerCallsign=this.isolateCallsign(m[1]);
        this.sourceCallsign=callerCallsign;
        this.sourceIs = "C";
        break;
    case 8:  // 8=respondent sends "73".  callerCallsign, respondentCallsign, "73"
        this.callerCallsign=this.isolateCallsign(m[0]);
        this.respondentCallsign=this.isolateCallsign(m[1]);
        this.sourceCallsign=respondentCallsign;
        this.sourceIs = "R";
        break;
    case 11:  // Uncategorized message
        break;
    } // end switch
  if(messageType != 11 && messageType != 10) this.decodeStatus=true;
  return true;
  } // end method decodeMessage()
  
  

  
public String concatMessage(String[] m)
  {
  String ret="";
  for(int x=0; x < m.length; x++) {ret += m[x]; ret += " ";}
  return ret;
  }


// flagme refactor the following to make it faster
private boolean isInteger(String receivedSignal)
  {
  try{int x = Integer.parseInt(receivedSignal);}
  catch(NumberFormatException nfe)
    {
    return false;
    } 
  return true;
  }



/**
 * Remove leading "<" and lagging ">"
 * In a callsign with a "/" character within it attempt to determine the callsign.
 * @param callsign
 * @return 
 */
private String isolateCallsign(String callsign)
  {
  String lefthand, righthand;  // i.e. before and after the "/"
  int greaterthan;
  // Return null callsign if token value is one of these.
  if(callsign.equalsIgnoreCase("QRM")||callsign.equalsIgnoreCase("QSO")||callsign.equalsIgnoreCase("QDX"))
      return null;
  // remove <>
  int lessthan = callsign.indexOf('<');
  if(lessthan==0) 
    {
    greaterthan=callsign.indexOf('>');
    if(greaterthan > 2)
      {
      if(debugFlag)System.out.println("lt=" + lessthan + ", gt=" + greaterthan + ", callsign=" + callsign);
      callsign=callsign.substring(lessthan + 1,greaterthan); 
      if(debugFlag)System.out.println("callsign with ltgt then " + callsign);}
    }
  // parse lefthand and righthand.  A callsign prefix can be up to 4 characters.
  int slash = callsign.indexOf('/');
  String retval=callsign;
  if(slash > 0)
    {
    lefthand=callsign.substring(0, slash);
    righthand=callsign.substring(slash + 1);
    retval=lefthand;  // assume that callsign is before "/"
    if(lefthand.length() < 5 && righthand.length() > 4) {retval=righthand; this.callerCallsignPrefix=lefthand;} // callsign is to right of slash
    if(debugFlag)logger.println("callsign=" + callsign + ", and then left=" + lefthand + ", right=" + righthand + ", callsign=" + retval);
    }
  return retval.toUpperCase();
  } // end method isolateCallsign

} // end class MessageRecord
