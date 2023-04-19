/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package UDP;



import HTTP.PopulateCallmaidHamQTH;
import m.db.Database;
import v.Log.Logfile;
import java.util.Vector;
import m.db.Database;
import m.Common;
import v.Log.Logfile;
import m.MessageRecord;
import UDP.UDPWSJTX;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import m.PopulateAlltxt;
import HTTP.QRZCQ;








/**
 *
 * @author chas
 */

/**
 * @author Charles Gray
 */
public class UDPProcessMessageThread extends Thread
{
private final Logfile logger;
public int state, oldstate;
public Database DBlocal;
private Vector<MessageRecord> bufferVector;
protected boolean debugFlag;
public boolean terminateFlag=false; 
public int ferror=0;
public int errorlimit=3;
private int loopcount=0;
public int messageReceiveCnt; // number of messages received from UDPWSJTX
private MessageRecord messRec;
private String myCallsign;
private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
private Date dtNow;
public String dtNowString;
public PopulateAlltxt popAlltxtObj;
public int messagesProcessed;
public PopulateCallmaidHamQTH pophamqthObj;
public QRZCQ qrzcqObj;

 
public UDPProcessMessageThread(Vector bufferVectora, Logfile l)
  {
  this.bufferVector = bufferVectora;
  this.debugFlag=Common.debugFlag;
  this.logger = l;
  this.setName("UDPProcessMessageThread");
  if(Common.qthrealtime==true) 
    {
    this.pophamqthObj= new PopulateCallmaidHamQTH(logger,debugFlag);
    if(this.pophamqthObj.validStatus == false) Common.qthrealtime = false;
    this.pophamqthObj.sleeptime=0; this.pophamqthObj.doShow=false;
    this.qrzcqObj = new QRZCQ(logger,debugFlag);
    }
  logger.println("Begin UDPProcessMessageThread");
  state=0; oldstate=0;
  } // end constructor


public void run()
{
while(state != 100)
  {
  if(debugFlag)logger.println("UDPProcessMessageThread State=" + state);
  if(this.terminateFlag)
    {
    logger.tprintlnwarn("terminateFlag=true at top of machine. Invoking state=100.");
    state=100; 
    continue;
    }
  if(ferror > 0){logger.tprintlnwarn("ferror=" + ferror); System.err.println("ferror=" + ferror);}
  if(ferror > errorlimit){state=100;continue;}
  loopcount++;
  if(loopcount > 99){loopcount=0; if(ferror > 0) ferror--;}
  switch(state)
    {
    case 0: // Initialize
      this.popAlltxtObj = new PopulateAlltxt(logger,Common.prodb,Common.debugFlag);
      state=10;
      break;
      
    case 10: // get MessageRecord from Vector bufferVector
      if(debugFlag)logger.println("bufferVector size=" + bufferVector.size());
      if(this.bufferVector.size()==0)
        {
        if(debugFlag)logger.println("bufferVector is empty.  Waiting 10 seconds");
        this.sleep(10000);
        state=10;
        break;
        }
      this.messRec = this.bufferVector.get(0);  // pop the first element off the Vector
      if(this.messRec==null)
        {
        logger.e("MessageRecord is null in UDPProcessMessageThread.  This should never happen."); 
        state=10; ferror++; break;
        }
      state=20;
      break;
      
    case 20:  // process MessageRecord
      if(messRec.messageType != 11 && messRec.messageType != 10) this.popAlltxtObj.processMessageRecord(messRec);
      this.popAlltxtObj.writeToDatabase(messRec);  // write the ALLTXT record.
      this.messagesProcessed++;
      if(Common.qthrealtime==true && this.popAlltxtObj.callcreateflag==true) state=25;
      else state=30;
      break;
      
    case 25:  // Query HamQTH
      String callsign = messRec.sourceCallsign;
      if(Common.qthrealtime)
        {
        if(!this.pophamqthObj.hamqthToCallmaid("callsign = '" + callsign + "'")) // i.e. if hamqth api query fails
        this.qrzcqObj.QRZCQToCallmaid(callsign);  // then query QRZCQ
        }
      state=30;
      break;
      
    case 30:  // delete bufferVector record.
      this.bufferVector.remove(0);
      state=10;
      break;
      
    case 90:  // error restart
      this.popAlltxtObj=null;
      this.qrzcqObj=null;
      state=0;
      break;
    } // end switch
  }
if(debugFlag)System.err.println("Ending UDPProcessMessageThread");
logger.println("Ending UDPProcessMessageThread");
} // end run()



public void terminate()
  {
  logger.println("Begin UDPProcessMessageThread.terminate(). terminateFlag set to true");
  this.terminateFlag=true;
  this.interrupt();
  }
 

void sleep(int millis)
  {
   try{Thread.currentThread().sleep(millis);}
  catch(Exception e){};
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




    
}  // end class UDPProcessMessageThread
