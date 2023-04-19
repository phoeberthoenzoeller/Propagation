/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package UDP;

import java.beans.PropertyChangeListener;
import javax.swing.SwingWorker;
import java.sql.SQLException;
import m.db.Database;
import v.Log.Logfile;
import java.util.Vector;
import m.db.Database;
import m.Common;
import v.Log.Logfile;
import m.MessageRecord;
import UDP.UDPWSJTX;
import java.beans.PropertyChangeEvent;
import java.net.SocketTimeoutException;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.table.DefaultTableModel;
import m.Propdbutils;
import m.db.ResultSetTableModel;
import javax.swing.JTable;
import v.MessageDisplay;
import java.lang.ArrayIndexOutOfBoundsException;
import java.util.List;
import javax.swing.SwingUtilities;






/**
 *
 * @author chas
 */
public class UDPServerWorker extends SwingWorker<Boolean, Object>  implements PropertyChangeListener
{
private final Logfile logger;
public int state, oldstate;
public Database DBlocal;
private Vector<MessageRecord> bufferVector;
protected boolean debugFlag;
private final UDPThreadManager udptm;
private final Propdbutils propdbObj;
public boolean terminateFlag=false; 
public int ferror=0;
public int errorlimit=10;
public UDPWSJTX udpwsjtx;
private byte[] bin;
private int timeout=60;  // 60 second initial timeout, DatagramSocket.setSOTimeout()
private int loopcount=0;
public int messageReceiveCnt; // number of messages received from UDPWSJTX
private MessageRecord messRec;
private String myCallsign;
private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
private Date dtNow;
public String dtNowString;
private ResultSetTableModel rstm;
private int erowcount;  // number of rows in table of eligible CQ messages
private long starttime;  // when this class was intantiated.
private JTable dispTable; 
public boolean transmitFlag;
public String cqCallsign;
private boolean currentlyTransmitting;
public MessageDisplay smessl;
private final MessageRecord nullMessageRecord;
public UDPServerWorker(Vector bufferVectora, Logfile l, String myCallsign, UDPThreadManager sw)
  {
  this.bufferVector = bufferVectora;
  this.debugFlag=Common.debugFlag;
  this.logger = l;
  this.udptm = sw;
  this.propdbObj = Common.prodb;
  this.currentlyTransmitting=false;          
  this.transmitFlag=false;
  this.dispTable = this.udptm.wsjtxStatus.jTableEligibleQSOs;
  this.nullMessageRecord = new MessageRecord();
  this.starttime = System.currentTimeMillis()/1000;
  // This Thread is NOT on the eventdispatchthread
  //if(SwingUtilities.isEventDispatchThread()) logger.println("UDPServerWorker.constructor() is on the EventDispatchThread");
  //else logger.tprintlnwarn("UDPServerWorker.constructor() is NOT on the EventDispatchThread");
  logger.println("Begin UDPServerWorker");
  state=0; oldstate=0;
  } // end constructor




/*
 * Main task. Executed in background thread.
 */
 @Override
public Boolean doInBackground()
{
try
  {
while(state != 100)
  {
  if(debugFlag)logger.println("UDPServerWorker State=" + state);
  if(this.terminateFlag)
    {
    logger.tprintln("In UDPServerWorker state machine terminateflag is set.");
    state=100; 
    continue;
    }
  if(ferror > 0){logger.tprintlnwarn("ferror=" + ferror); System.err.println("UDPServerWorker ferror=" + ferror);}
  if(ferror >= errorlimit){state=100;continue;}
  loopcount++;
  if(loopcount > 99){loopcount=0; if(ferror > 0) ferror--;}
  switch(state)
    {
    case 0: // Initialize
      if(SwingUtilities.isEventDispatchThread()) logger.println("UDPServerWorker.doInBackground() is on the EventDispatchThread");
      else logger.tprintlnwarn("UDPServerWorker.doInBackground() is NOT on the EventDispatchThread");
      this.udpwsjtx = new UDPWSJTX(this.logger, this.timeout, this.debugFlag, Common.UDPPort, Common.localIPAddress);
      if(this.udpwsjtx.status==false)
        {
        if(this.smessl != null) this.smessl.showOff(); this.smessl=null;
        this.smessl = new MessageDisplay("Failed to initialize network communication channel. Terminating.",10,10);    
        this.smessl.setModal(false);
        this.smessl.showMessage(10);
        this.smessl.showOff(); this.smessl=null;
        this.terminateFlag=true;
        this.ferror++; state=100; 
        break;
        }
      this.dtNowString=null;  // signal to state that we have not yet received a messagetype=1, 
                              //and thus have not yet set radiofrequency and rxtx in UDPWSJTX.
      state=10;
      break;
      
    case 10: // get data
      try{bin = this.udpwsjtx.udps.receive();}  // get data from client
      catch(Exception ste){logger.ee("UDP listening socket threw Exception.", ste); state=90; break;}
      if(bin==null) 
        {
        var md3 = new MessageDisplay("UDPSocket timed out waiting for client.  Please check WSJT-X and network. Retrying in 10 seconds.",50,100); 
        md3.showMessage(10);
        state=10; ferror++; break;
        }
      //this.updateElapsedTime(); 
      this.publish(Integer.valueOf(10));
      if(debugFlag)logger.println("In state=10 returned from process(). state=" + state);
      state=20;
      break;
      
    case 20:  // parse UDP message, create MesssageRecord
      if(this.udpwsjtx.parseMessage(bin)){state=30; }
      else state=10;
      break;

      
    case 30:  // process MessageRecord
      if(this.udpwsjtx.messageType == 0)  // heartbeat
        {
        dtNow = new Date();
        dtNowString = this.sdf.format(dtNow);
        }
      if(this.udpwsjtx.messageType == 1)  // status message
        {
        dtNow = new Date();
        dtNowString = this.sdf.format(dtNow);
        // transmitting flag in status message will be false for a few seconds after sending messageType4 before flag becomes true.
        if(this.transmitFlag)
          {
          if(this.udpwsjtx.currentlyTransmitting==true)
            {
            this.currentlyTransmitting = true;  // We are transmitting a response to CQ
            logger.println("Transmitting response to CQ from " + this.cqCallsign);
            if(this.smessl != null) this.smessl.showOff(); this.smessl=null;
            this.smessl = new MessageDisplay("Transmitting response to CQ from " + this.cqCallsign,250,500);    
            this.smessl.showMessage();
            }
          if(this.udpwsjtx.currentlyTransmitting==false && this.currentlyTransmitting) // We failed to responde to CQ.
            {
            logger.println("Transmission responding to CQ from " + this.cqCallsign + " failed.");
            this.smessl.showOff(); this.smessl=null;
            this.smessl = new MessageDisplay("Transmission responding to CQ from " + this.cqCallsign + " failed.",250,500);
            this.smessl.showMessage(3);
            this.transmitFlag=false;
            this.currentlyTransmitting=false;
            }
          } // end this.transmitFlag==true
        }  // end messageType==1
      if(this.udpwsjtx.messageType == 5 && this.transmitFlag == true) // log qso message from client back to server (me)
        {
        logger.println("Resetting transmitFlag in response to message Type=5.");
        this.transmitFlag=false;
        this.smessl.showOff();
        }
      if(this.udpwsjtx.messageType == 6) // WSJT-X has closed
        {
        this.smessl = new MessageDisplay("Received WSJTX shutdown message. Terminating network connection.",250,500);    
        this.smessl.showMessage(10);
        this.udptm.terminate();
        this.sleep(3000);
        this.terminateFlag=true;
        state=100; break;
        }
      if(this.udpwsjtx.messageType != 2 && this.udpwsjtx.messageType != 10) // not a "decode"(ft8/ft4) message or WSPR message
        {
        state=10;
        break;
        }
      if(this.dtNowString==null && this.udpwsjtx.messageType != 10)
        {
        logger.printlnwarn("Ignoring messagetype=" + this.udpwsjtx.messageType + " because we have not yet received a messagtype=1");
        state=10; break;
        } // There is no reason to process a messagetype=2 prior to receipt of messagetype=1
      this.messRec=this.udpwsjtx.getMessageRec();  // Note that this is not UDPWSJTX.messagetype but rather MessageRecord.messageType.
      if(this.messRec==null){logger.printlnwarn("Null message will not be queued."); state=10; break;}
      // type=0 should never happen. type=11 uncategorized message, type=10 no message
      // Note that we do store in ALLTXT uncategorized messages.
      if(this.messRec.messageType == 0 || messRec.messageType == 10 || messRec.messageType == 11)
        {
        logger.printlnwarn("Messagetype=" + messRec.messageType + " will not be queued. ldt=" + messRec.ldt + ", message=" + messRec.concatMessage(messRec.message));
        state=10;
        break;
        }
      if(myCallsign != null) if(mycallsignFilter(this.messRec, myCallsign)) // if myCallsign is specified and it is not in this MessageRecord then skip the record.
        {
        if(debugFlag)logger.println("Filtering on " + myCallsign + ", message will not be queued. ldt=" + messRec.ldt + ", message=" + messRec.concatMessage(messRec.message));
        state=10;
        break;
        }
      // Message is qualified for processing.  Add message to queue.
      this.messageReceiveCnt++;
      this.bufferVector.add(this.messRec);
      state=40;
      break; // end case 30

      
    case 40: // Add or remove message from WSJTXSttaus.jTableEligibleQSOs
       this.publish(messRec); 
       /*
       if(this.messRec.messageType == 1) // CQ
         {
         // Insert row if qualified, else return to state 10 to get next record.
         //******************************  perform the following via publish/process so that the WSJTXStatus table is updated in the EventDispatch Thread
         if(!this.insertCQRow(dispTable, messRec)){state=10; break;}
         // Delete last row (oldest) when table gets over 12 records in size.
         this.erowcount = dispTable.getModel().getRowCount();
         if(erowcount > 12) 
           {
           try{((DefaultTableModel)dispTable.getModel()).removeRow(this.erowcount - 1); erowcount--;}
           catch(ArrayIndexOutOfBoundsException aioob){logger.ee("In case 40.removeRow() ArrayIndexOutOfBounds.",aioob);}
           } 
         //this.udptm.wsjtxStatus.jTableEligibleQSOs.clearSelection();
         //System.out.println("table size=" + dispTable.getModel().getRowCount() + " rows.");
         // Update the elapsed time on each CQ record.
         this.updateElapsedTime();
         //****************************
         }
       if(this.messRec.messageType == 4) // response to CQ
         {
         // If user has not checked the "Inc. Resp." checkbox and transmit flag is not set then delete CQs which have received responses.
         if(this.transmitFlag==false && !this.udptm.wsjtxStatus.jCheckBoxIncResp.isSelected())this.deleteCQsResponded();
         }
       */
       state=10;
       break;  // end case 40
   
      
    case 90:  // error restart
      int x;
      logger.tprintlnwarn("ERROR RESTARTING UDPServerWorker in state=90");
      if(this.disconnectUDP()==false)this.ferror++;
      state=0;
      break;
    } // end switch
  } // end while state != 100
  } // end try block
catch(Exception e){logger.ee("Exception in UDPServerWorker while loop", e); this.terminate();}
this.udpwsjtx.serverClose();
if(debugFlag)System.err.println("Ending UDPServerWorker");
logger.tprintln("Ending UDPServerWorker.doInBackground()");
if(this.terminateFlag) return true; else return false;
} // end doInBackground()






public void terminate()
  {
  logger.println("Begin UDPServerWorker.terminate()");
  this.udpwsjtx.serverClose();
  this.sleep(1000);
  this.terminateFlag=true;
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



private boolean beenWorked(String callsign)
  {
  ResultSetTableModel rstml = this.propdbObj.queryCallmaid("WORKED", "callsign = '" + this.messRec.sourceCallsign + "'");
  boolean worked=false;
  if(rstml.getRowCount() == 1)
    {
    try
      {
      rstml.rsl.absolute(1);  // row 1 is first row
      //System.out.println("row=" + rstml.rsl.getRow() + " columns=" + rstml.getColumnName(0));    
      worked = rstml.rsl.getBoolean("WORKED");
      }
    catch(SQLException se){logger.ee("SQLException reading worked column.",se);}
    //if(worked)System.out.println("Worked"); else System.out.println("Not worked");
    }
  return worked;
  }


/**
 * Iterate through jTableEligibleQSOs.
 * Set timeelapsed (column 6) to current seconds minus timestart (column 7)
 */
private void updateElapsedTime()
  {
  if(debugFlag)
    {
    if(SwingUtilities.isEventDispatchThread()) logger.println("UDPServerWorker.updateElapsedTime() is on the EventDispatchThread");
      else logger.tprintlnwarn("UDPServerWorker.updateElapsedTime() is NOT on the EventDispatchThread");
    }
  long currentseconds = System.currentTimeMillis()/1000 - this.starttime;
  for(int x=0; x < this.udptm.wsjtxStatus.jTableEligibleQSOs.getRowCount(); x++)
    {
    long timestart =(Integer)this.udptm.wsjtxStatus.jTableEligibleQSOs.getValueAt(x, 7);
    int timeelapsed = (int)(currentseconds - timestart);
    //System.out.println("row=" + x + ", start=" + timestart + ", current=" + currentseconds + ", elapsed=" + timeelapsed);
    this.udptm.wsjtxStatus.jTableEligibleQSOs.setValueAt(timeelapsed, x, 6);
    }
  }  // end updateElapsedTime()




    
    
    
/**
 * Insert into new CQ record into this.udptm.wsjtxStatus.jTableEligibleQSOs if qualified.
 * If checkbox NotWorked is checked and this callsign has been worked then do not insert.
 * If min rssi is specified and this message rssi is less than the specified rssi then do not insert.
 * If contest jTextField is populated and this.messRec.contestIdentifier doesn't match then do not insert.  
 *   Note that wildcards are appended to beginning and end of user entered contest.
 * If transmit flag is set by SwingWorkerWSJTX.transmitCQResponse(int row) then do not insert row 
 *   unless message matches callsign in CQ to which we are responding.
 */
private boolean insertCQRow(JTable dispTable, MessageRecord messrec)
  {
  if(debugFlag)
    {
    if(SwingUtilities.isEventDispatchThread()) logger.println("UDPServerWorker.insertCQRow() is on the EventDispatchThread");
      else logger.tprintlnwarn("UDPServerWorker.insertCQRow() is NOT on the EventDispatchThread");
    logger.println("Begin insertCQRow for callsign=" + messrec.callerCallsign);
    }
  int currtime = (int)((System.currentTimeMillis()/1000) - starttime);
  boolean worked = false;
   int intcolor=0; // 0=white, 1=red for worked, 2=blue for rssi
  Object[] rowData = new Object[10];
  rowData[0]=this.messRec.sourceCallsign;
  rowData[1]=this.messRec.sourceMaidenhead;
  rowData[2]=Integer.valueOf(this.messRec.rssi);
  //rowData[3]=Float.valueOf((float)this.messRec.distanceBetween);
  //rowData[4]=Float.valueOf((float)this.messRec.initialBearing);
  rowData[3]=this.messRec.contestIdentifier;
  worked = this.beenWorked(messRec.sourceCallsign);
  rowData[4]=Boolean.valueOf(worked);
  // rowDate[5] watch
  rowData[6] = Integer.valueOf(0); // elapsed time
  rowData[7] = Integer.valueOf(currtime); // start time
  if(worked)intcolor=1;  // worked is red.
  rowData[8] = Integer.valueOf(intcolor);  // 0 is white color
  rowData[9] = this.messRec;
  // Qualify record before displaying.
  // First qualification is "worked".  return if notworked is selected and record is worked.
  if(this.udptm.wsjtxStatus.jCheckBoxNotWorked.isSelected() && worked)
    {
    if(debugFlag)logger.tprintln("Rejecting message because worked. ldt=" + messRec.ldt + ", sourceCallsign=" + messrec.sourceCallsign + ", message=" + messRec.concatMessage(messRec.message));
    return false;
    } 
  // Next is RSSI.   return if rssi threshold is specified and this record is below it.
  String rssiString = this.udptm.wsjtxStatus.jTextFieldRSSIgt.getText();  
  if(rssiString != null & !rssiString.equals(""))
    {
    int rssiInt = Integer.valueOf(rssiString);  // user entered minimum rssi
    int messrecrssi = Integer.valueOf(this.messRec.rssi); // rssi in message
    if( messrecrssi < rssiInt)
      {
      if(debugFlag)logger.tprintln("Rejecting message because rssi=" + messrecrssi + "<" + rssiInt + ", ldt=" + messRec.ldt + ", sourceCallsign=" + messrec.sourceCallsign + ", message=" + messRec.concatMessage(messRec.message));
      return false;
      }
    }
  // Next is contest
  String contest = this.udptm.wsjtxStatus.jTextFieldContest.getText().toUpperCase();
  if(!contest.equals(""))
    {
    if(messRec.contestIdentifier == null || messRec.contestIdentifier.equals("")) return false;
    if(!this.messRec.contestIdentifier.matches(".*" + contest + ".*")) 
      {
      if(debugFlag)logger.tprintln("Rejecting message because contestidentir != " + contest + ", ldt=" + messRec.ldt + ", sourceCallsign=" + messrec.sourceCallsign + ", message=" + messRec.concatMessage(messRec.message));
      return false;
      }
    }
  // Next is transmit mode. If we are responding to a CQ then this.transmitFlag will be true and this.cqCallsign will be populated.
  // In transmit mode display to user only new messages that are between us and CQ caller.
  if(this.transmitFlag)
    {
    if(!messRec.concatMessage(messRec.message).matches(".*" + this.cqCallsign + ".*")) 
      {
      if(debugFlag)logger.tprintln("Rejecting message because transmit flag on and callsign != " + this.cqCallsign + ", ldt=" + messRec.ldt + ", sourceCallsign=" + messrec.sourceCallsign + ", message=" + messRec.concatMessage(messRec.message));
      return false;
      }    
    }
  // Insert the row in the display table.
  this.udptm.wsjtxStatus.insertRow(rowData);
  // ((DefaultTableModel)dispTable.getModel()).insertRow(0, rowData);  
  return true;
  }  // end insertCQRow()


/**
 * Delete from table shown to user those CQ message that have had responses.
 * The idea is that these are not of primary concern to a user who is looking for contacts.
 * The messages will still be logged.
 */
private void deleteCQsResponded()
  {
  if(debugFlag)
    {
    if(SwingUtilities.isEventDispatchThread()) logger.println("UDPServerWorker.deleteCQsResponded() is on the EventDispatchThread");
      else logger.tprintlnwarn("UDPServerWorker.deleteCQsResponded() is NOT on the EventDispatchThread");
    }
  this.erowcount = dispTable.getModel().getRowCount();
  MessageRecord tmr;
  for(int x=0; x < erowcount; x++)
     {
     try{tmr = (MessageRecord)this.dispTable.getModel().getValueAt(x, 9);}
     catch(ArrayIndexOutOfBoundsException aioob){logger.ee("In deleteCQsResponded().getValueAt() ArrayIndexOutOfBounds.",aioob); return;}
     if(this.messRec.callerCallsign==null)continue;
     if(this.messRec.callerCallsign.equals(tmr.callerCallsign))
       {
       if(debugFlag)logger.println("Delete for callsign = " + tmr.callerCallsign + " at " + tmr.ldt);
       /*
       try{((DefaultTableModel)dispTable.getModel()).removeRow(x);}
       catch(ArrayIndexOutOfBoundsException aioob){logger.ee("In deleteCQsResponded().removeRow() ArrayIndexOutOfBounds.",aioob); return;}
       */
       this.udptm.wsjtxStatus.removeRow(x);
       erowcount --;
       }
     }    
  } // end deleteCQsResponded




  /**
   * Override this method in order to obtain a message to main GUI JTextArea
   * in a form other than: title + percent done + "percent complete."
   * @param chunks 
   */
  @Override
  protected void process(List chunks) 
    { 
    // define what the event dispatch thread  
    // will do with the intermediate results received 
    // while the thread is executing 
    boolean MRClass=false;
    int statelocal=0;
    MessageRecord mrlocal= null;
    Integer Intlocal = Integer.valueOf(0);
    if(debugFlag)
      {
      if(SwingUtilities.isEventDispatchThread()) logger.println("UDPServerWorker.process() is on the EventDispatchThread");
      else logger.tprintlnwarn("UDPServerWorker.process is NOT on the EventDispatchThread");
      }
    // Iterate through List
    for(int x=0; x < chunks.size(); x++)
      {
      statelocal=0;
      if(debugFlag)logger.println("process() Object=" + chunks.get(x).getClass() + ",State=" + state);
      if(chunks.get(x).getClass() == this.nullMessageRecord.getClass()) 
        {
        if(debugFlag)logger.println("Class is MessageRecord"); 
        MRClass=true;
        mrlocal = (MessageRecord)chunks.get(x);
        }
      if(chunks.get(x).getClass() == Intlocal.getClass()) 
        {
        if(debugFlag) logger.println("Class is Integer"); 
        MRClass=true;
        statelocal = (Integer) chunks.get(x);
        if(debugFlag)logger.println("Passed state=" + statelocal);
        }
      if(statelocal==10){this.updateElapsedTime(); continue;}
      if(debugFlag)logger.println("process() Statelocal=" + statelocal);
      //**************************  remainder is unique to state=40
      if(mrlocal==null){logger.e("Null MessageRecord in process().  Localstate=" + statelocal + ", state=" + this.state);}
      if(debugFlag)logger.println("process() messageType=" + mrlocal.messageType + ", callsign=" + mrlocal.callerCallsign);
      //if(mrlocal.messageType != 1 && mrlocal.messageType != 4){logger.e("Bad messageType=" + mrlocal.messageType + " in process()"); continue;}
      if(mrlocal.messageType == 1) // CQ
        {
        if(!this.insertCQRow(dispTable, mrlocal)){logger.println("insertCQRow returned false."); continue;}
        // Delete last row (oldest) when table gets over 12 records in size.
        this.erowcount = dispTable.getModel().getRowCount();
        if(erowcount > 12) 
          {
          try{((DefaultTableModel)dispTable.getModel()).removeRow(this.erowcount - 1); erowcount--;}
          catch(ArrayIndexOutOfBoundsException aioob){logger.ee("In case 40.removeRow() ArrayIndexOutOfBounds.",aioob);}
          } 
        //this.udptm.wsjtxStatus.jTableEligibleQSOs.clearSelection();
        //System.out.println("table size=" + dispTable.getModel().getRowCount() + " rows.");
        // Update the elapsed time on each CQ record.
        this.updateElapsedTime();
        continue;
        } // end messageType==1
      if(mrlocal.messageType == 4) // response to CQ
        {
        // If user has not checked the "Inc. Resp." checkbox and transmit flag is not set then delete CQs which have received responses.
        if(this.transmitFlag==false && !this.udptm.wsjtxStatus.jCheckBoxIncResp.isSelected())this.deleteCQsResponded();
        }
      }  // end for each chunk
    //**********************
    } // end process 




       
 /**
   * Invoked when task's int progress property changes via doInBackground.setProgress().
   */
  @Override
  public void propertyChange(PropertyChangeEvent evt) 
    {
    if(SwingUtilities.isEventDispatchThread()) logger.println("UDPServerWorker.propertyChange() is on the EventDispatchThread");
      else logger.tprintlnwarn("UDPServerWorker.propertyChange() is NOT on the EventDispatchThread");
    if ("progress" == evt.getPropertyName()) 
      {
      int progress = (Integer) evt.getNewValue();
      } 
    }
    


private boolean disconnectUDP()
  {
  int x;
  if(this.udpwsjtx.udps.socket != null)
    {  
    logger.println("Attempting to disconnect UDP socket");
    for(x=0; x < 10; x++)
      {
      if(this.udpwsjtx.udps.socketClose()) break;
      sleep(1000);
      }
    if(x < 10)
      {
      logger.e("Failed to disconnect UDP Sockekt");
      return false;
      }
    }
  else logger.tprintlnwarn("Not closing UDP socket because socket is null.");
  return true;  
  }


    
}  // end class UDPServerWorker
