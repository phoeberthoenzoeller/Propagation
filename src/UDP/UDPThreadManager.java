/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package UDP;



import javax.swing.SwingWorker;
import v.Log.Logfile;
import v.WSJTXStatus;
import m.MessageRecord;
import java.util.Date;
import java.sql.Time;
import java.text.DecimalFormat;
import UDP.UDPProcessMessageThread;
import java.util.Vector;
import java.util.Calendar;
import v.MessageDisplay;
import javax.swing.SwingUtilities;
import m.Common;


public class UDPThreadManager extends Thread
{
//private JTextArea progressBarTaskOutput; // for done()
//private JProgressBar progressBar;
public final String title;
private final Logfile logger;
public WSJTXStatus wsjtxStatus;
public Vector<MessageRecord> bufferVector;
int progressWorker=0,progMin=0,progMax=20;
public UDPServerWorker serverWorker;
private UDPProcessMessageThread processMessageThread;
private int loopcount;
private int state, oldstate;
private int ferror;
private final int ferrorlimit=10;
private long lasttime;
private final boolean debugFlag;
private int usedMemory;
private final int usedMemoryLimitAlarm=40;
private String errmsg;
private boolean terminateFlag=false;
private final DecimalFormat df = new DecimalFormat("###,###,###");
private final String myCallsign;
private boolean restartFlag=false; // are we in state=90 restart?
    
    
    
    
    
public UDPThreadManager(String title,Logfile loggerp, boolean debugFlag, String myCallsign)
  {
  this.myCallsign = myCallsign;
  this.debugFlag=debugFlag;
  this.title = title;
  this.progMin=0;
  this.logger = loggerp; 
  this.bufferVector = new Vector<MessageRecord>(1000,100);  
  if(SwingUtilities.isEventDispatchThread()) logger.println("UDPThreadManager.constructor() is on the EventDispatchThread");
  else logger.tprintlnwarn("UDPThreadManager.constructor() is NOT on the EventDispatchThread");
  }  // end constructor  
    
@Override   
public void run() 
  {
  if(SwingUtilities.isEventDispatchThread()) logger.println("UDPThreadManager.run() is on the EventDispatchThread");
  else logger.tprintlnwarn("UDPThreadManager.run() is NOT on the EventDispatchThread");
  while(state != 100)
    {
    loopcount++;
    if(state > 0) 
      {
      if(ferror < 0) ferror = 0;  // You can't ever have less than zero errors.
      if(debugFlag)logger.println("Old state=" + oldstate + ", new state=" + state);
      if(ferror > 0)logger.println("UDPThreadManager errors=" + ferror);
     oldstate=state;
     if((loopcount > 1000 && (loopcount % 1000)==0)) 
       {
       if(ferror > 0)
         {
         logger.println("Loopcount=" + loopcount + ". Decrementing ferror=" + ferror);
         ferror--;
         }
       }
     if(this.terminateFlag==true && this.restartFlag==false) 
       {
       state=100; 
       logger.tprintln("In UDPThreadManager state machine terminateFlag is true.");
       break;
       }
     } // end state > 0


  switch(state)  // primary program state
    {
      case 0: // State 0 is program initialize.
       this.restartFlag=false; // if we were restarting then we are no longer restarting.
       if(!this.startThreads()) state=95; // abort
       else state=10;
       break;

      case 10: // Read status and update JFrame WSJTXStatus.
        this.wsjtxStatus.jTextFieldMessReceived.setText(df.format(this.serverWorker.messageReceiveCnt));
        // this.processMessageThread is an instance of UDPProcessMessageThread
        this.wsjtxStatus.jTextFieldMessProcessed.setText(df.format(this.processMessageThread.messagesProcessed));
        if(this.processMessageThread.popAlltxtObj != null)
          {
          this.wsjtxStatus.jTextFieldNewCallsignRecords.setText(df.format(this.processMessageThread.popAlltxtObj.callsignnew));
          this.wsjtxStatus.jTextFieldUpdatedCallsignRecs.setText(df.format(this.processMessageThread.popAlltxtObj.callsignupdated));
          }
        if(this.processMessageThread.pophamqthObj != null)
          {
          this.wsjtxStatus.jTextFieldOnlineQueries.setText(df.format(this.processMessageThread.pophamqthObj.hamqthqueries + this.processMessageThread.qrzcqObj.queriestotal));
          this.wsjtxStatus.jTextFieldOnlineResponses.setText(df.format(this.processMessageThread.pophamqthObj.hamqthreads + this.processMessageThread.qrzcqObj.queryresponses));
          }
        this.wsjtxStatus.jTextFieldQueueSize.setText(df.format(this.bufferVector.size()));
        this.wsjtxStatus.jTextFieldHeartbeat.setText(this.serverWorker.dtNowString);
        state=20;
        break; // end state=10
        
      case 20:  // perform housekeeping.
        Time time = new Time(System.currentTimeMillis());
        Date currdate = new Date(System.currentTimeMillis());
        this.sleep(9000);
        // Begin block to clean log records if one hour has elapsed since I last performed the clean.
        if((System.currentTimeMillis() - lasttime) < 3600000)
          {
          state=30;
          break;
          }
        if(debugFlag)logger.println("The Baby is sleeping on " + currdate + " at " + time.toString() + ".");
        this.usedMemory=(int)(((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())+500000)/1000000);
        if(debugFlag)logger.println("Used memory=" + usedMemory + "MB"
          + ", free memory=" + String.valueOf(Runtime.getRuntime().freeMemory())
          + ", total memory=" + String.valueOf(Runtime.getRuntime().totalMemory())  // This is -Xms value or greater up to maxMemory
          + ", max memory=" + String.valueOf(Runtime.getRuntime().maxMemory())); // This is the -Xmx value
        logger.println("Prior to garbage cleanup; used memory=" + String.valueOf(usedMemory) + "MB"
          + ", free memory=" + String.valueOf(Runtime.getRuntime().freeMemory())
          + ", total memory=" + String.valueOf(Runtime.getRuntime().totalMemory())  
          + ", max memory=" + String.valueOf(Runtime.getRuntime().maxMemory()));
        // Runs the finalization methods of any objects pending finalization.
        System.runFinalization();
        this.sleep(1000);
        // Garbage cleanup
        System.gc();
        // Report free memory
        this.usedMemory=(int)(((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())+500000)/1000000);
        logger.println("Used memory=" + String.valueOf(this.usedMemory)
          + ", free memory=" + String.valueOf(Runtime.getRuntime().freeMemory())
          + ", total memory=" + String.valueOf(Runtime.getRuntime().totalMemory())
          + ", max memory=" + String.valueOf(Runtime.getRuntime().maxMemory()));
        if(this.usedMemory> usedMemoryLimitAlarm)
          {
          this.errmsg="WARNING, in state=20, Used memory=" + usedMemory + "MB exceeds usedMemoryLimitAlarm=" + usedMemoryLimitAlarm + ".";
          System.out.println(errmsg + " at " + currdate.toString());
          logger.println(errmsg);
           }
         if(Runtime.getRuntime().freeMemory() < 2000000)
           {
           this.errmsg="WARNING, in state=60, Free memory =" + ((int)(Runtime.getRuntime().freeMemory() + 500000)/1000000) + " MB.";
           System.out.println(errmsg);
           logger.println(errmsg);
           }
         lasttime = System.currentTimeMillis();        
        state=30; 
        break;

    case 30: // Thread Health       
      if(!this.getWorkerStatus())
        {
        logger.tprintlnwarn("Calling state 90/restart in response to getWorkerStatus()=false");
        state=90; 
        break;
        }
      if(!this.getThreadStatus(this.processMessageThread))
        {
        logger.tprintlnwarn("Calling state 90/restart in response to getThreadStatus(processMessageThread)=false");
        state=90; 
        break;
        }
      state=10;
      break; 
     
      
    case 90:  // restart
      if(this.terminateFlag==true)
        {
        logger.tprintlnwarn("UDPThreadManager terminateFlag is true.  Moving from state=90 to state=95");
        state=95; 
        break;
        }
      ferror++;
      this.restartFlag=true;
      logger.tprintlnwarn("State=90. Restarting UDPThreadManager. ferror=" + ferror);
      System.err.println("WARNING: Restarting UDPThreadManager. ferror=" + ferror);
      new MessageDisplay("Restarting UDPThreadManager in response to error.").showMessage(5);
      System.err.println("In UDPThreadManager state=90, ferror count=" + ferror + ", error limit=" + this.ferrorlimit);
      this.endThreads();
      if(this.ferror < this.ferrorlimit)
        {
        logger.println("this.ferror=" + this.ferror + " is less than this.ferrorlimit=" + this.ferrorlimit + ", restarting.");
        state=0;
        break;
        }
      else state=95; // abort
      logger.println("state=95. Aborting UDPThreadManagerr.");
      break; // end state=90
      


    case 95:  // Abort program
      logger.println("State=95, aborting program. ferror=" + ferror); //
      System.err.println("State=95. Aborting UDPThreadManager. ferror=" + ferror);
      this.endThreads(); // this will be performed again when state=100. We want to be sure that Threads are terminated properly.
      state=100;
      break;
      
    case 100:
      if(debugFlag)logger.println("WARNING: State=100. Exiting state machine.");
      break;  
      
    default:
      logger.e("Invalid State in state machine. State=" + state + ", aborting.");
      state=100;
      break;
    }  // end state machine switch statement.
  }  // end while != state 100
  if(debugFlag)System.err.println("Ending UDPThreadManager statemachine(). Termination State=100. Ending UDPServerWorker and UDPProcessMessageThread.");
  logger.tprintln("End UDPThreadManager statemachine(). Termination State=100");
  this.endThreads();
               
  } // end method run
     
/**
 * This is the public method by which the parent or child requests termination.
 * Currently it is invoked solely by UDPServerWorker in state=30 in response to messageType == 6 WSJT-X has closed
 * and by UIMain jMenuItemWSJTXUDPServerStopActionPerformed.
 */  
public void terminate()
  {
  logger.println("Begin UDPThreadManager.terminate(). Setting terminateFlag=true");
  this.terminateFlag=true;
  this.state=95;
  }
  
private boolean startThreads() 
  {
  logger.println("UDPThreadManager. Creating WSJTXStatus with title= " + title);
  this.wsjtxStatus = new WSJTXStatus(this, this.logger, debugFlag);  
  this.serverWorker = new UDPServerWorker(this.bufferVector, this.logger, this.myCallsign, this);  
  this.serverWorker.execute();
  this.processMessageThread = new UDPProcessMessageThread(this.bufferVector, this.logger);
  this.processMessageThread.setName("UDPProcessMessageThread");
  this.processMessageThread.start();
  logger.println("UDPProcessMessageThread started as Thread name=" + this.processMessageThread.getName());
  logger.println("UDPServerWorker started.");
  return true;
  }



private boolean getThreadStatus(Thread threadname)
  {
  if(threadname==null)
    {
    System.err.println("ERROR, Thread " + threadname.getName() + " is null.");
    logger.println("Thread for " + threadname.getName() + " is null.");
    return false;
    }
  logger.tprintln("Thread " + threadname.getName() + " status=" + this.getThreadState(threadname)); 
  if(!threadname.isAlive()) 
    {
    logger.e("Thread for " + threadname.getName() + " is not alive.  State=" + threadname.getState());
    return false;
    }
  if(threadname.getState()==Thread.State.TERMINATED)
    {
    System.err.println("ERROR in UDPThreadManager. Thread for " + threadname.getName() + " is terminated.");
    logger.e("Thread for " + threadname.getName() + " is terminated.");
    return false;
    }
  if(threadname.getState() != Thread.State.TIMED_WAITING && threadname.getState() != Thread.State.RUNNABLE)
    {
    if(threadname.getState() == Thread.State.BLOCKED) 
      {
      logger.tprintlnwarn("Thread for " + threadname.getName() + " is BLOCKED.");
      this.sleep(1000);// Thread will often block for a few milliseconds.
      }
    if(threadname.getState() == Thread.State.TIMED_WAITING)
      {
      logger.tprintln("Thread for " + threadname.getName() + " is TIMEDWAITING.");
      this.sleep(1000);// Thread will often block for a few milliseconds.
      }
    for(int x=1; x < 10; x++)
      {  // wait for Thread that is BLOCKED or TIMED_WAITING
      if(threadname.getState() != Thread.State.BLOCKED && threadname.getState() != Thread.State.TIMED_WAITING) break;
      logger.tprintlnwarn(threadname.getName() + " is BLOCKED or TIMED_WAITINGG for " + x + " seconds.");
      this.sleep(10000);
      }
    if(threadname.getState() != Thread.State.RUNNABLE && threadname.getState() != Thread.State.TIMED_WAITING && threadname.getState() != Thread.State.BLOCKED)
      {
      logger.e("ERROR: Thread for " + threadname.getName() + " is not RUNNABLE or BLOCKED or TIMED_WAITING. State=" + threadname.getState() + " at " + this.getStrDateTime(System.currentTimeMillis()));
      return false;
      }
    }
  return true;
  }

private boolean getWorkerStatus()
  {
  if(this.serverWorker==null)
    {
    System.err.println("ERROR, SwingWorker UDPServerWorker is null.");
    logger.println("SwingWorker UDPServerWorker is null.");
    return false;
    }
  SwingWorker.StateValue sv = this.serverWorker.getState();
  logger.tprintln("SwingWorker UDPServerworker status=" + sv); 
  if(sv == SwingWorker.StateValue.DONE) 
    {
    logger.tprintlnwarn("SwingWorker UDPServerWorker state is DONE.");
    new MessageDisplay("SwingWorker UDPServerWorker state is DONE.").showMessage(5);
    if(this.serverWorker.terminateFlag==true && this.restartFlag==false)
      {
      logger.tprintlnwarn("UDPThreadManager terminateFlag set to true in response to UDPServerWorker terminateFlag true");
      this.terminateFlag=true;
      } 
    return false;
    }
  return true;
  }  // end getWorkerStatus()


private boolean endThreads()
  {
  logger.println("Begin endThreads()");
  // Terminate UDPServerWorker
  boolean serverDeadFlag=false;
  boolean processDeadFlag=false;
  if(this.serverWorker==null){logger.println("UDPServerWorker is already null."); serverDeadFlag=true;}
  if(serverDeadFlag==false && this.serverWorker.getState()==SwingWorker.StateValue.DONE) 
    {
    logger.println("UDPServerWorker state is already DONE");
    if(this.serverWorker.terminateFlag && this.restartFlag==false)
      {
      this.terminateFlag=true; 
      logger.println("UDPServerWorker terminateFlag=true. UDPServerWorker state=DONE.");
      }
    serverDeadFlag=true;
    }
  if(serverDeadFlag==false)this.serverWorker.terminate();
  serverDeadFlag=false;
  // Terminate UDPProcessMessageThread
  if(this.processMessageThread==null){logger.println("processMessageThread is alrady null."); processDeadFlag=true;}
  if(processDeadFlag==false && this.processMessageThread.getState()==Thread.State.TERMINATED) 
    {
    logger.println("processMessageThread state is already TERMINATE");
    processDeadFlag=true;
    }
  if(processDeadFlag==false)this.processMessageThread.terminate();
  // Wait for thread termination
  int x=0;
  for(x=0; x < 100; x++)
    {
    logger.println("Waiting for UDPServerWorker and UDPProcessMessageThread to terminate. Timer=" + x);
    this.sleep(1000);
    if(serverWorker==null || serverWorker.getState()==SwingWorker.StateValue.DONE) serverDeadFlag=true;
    if(processMessageThread==null || processMessageThread.getState()==Thread.State.TERMINATED) processDeadFlag=true;
    if(serverDeadFlag && processDeadFlag) 
      {
      logger.tprintln("serverDeadFlag and processDeadFlag both true. Exiting wait for Threads to terminate.");
      break;
      }
    }
  logger.tprintln("End wait for UDPServerWorker and UDPProcessMessageThread termination at " + x + " seconds.");
  if(this.wsjtxStatus != null){this.wsjtxStatus.dispose(); this.wsjtxStatus=null;}
  if(x < 100) return true;
  if(serverWorker != null && serverWorker.getState() != SwingWorker.StateValue.DONE)logger.e("UDPServerWorker failed to terminate. State=" + serverWorker.getState());
  if(processMessageThread != null && processMessageThread.getState() != Thread.State.TERMINATED)logger.e("UDPProcessMessageThread failed to terminate.");
  return false;
  } // end endThreads()




public void setProgMax(int maxp){progMax=maxp;}

public int getProgressWorker(){return progressWorker;}  
    
public int incProgressWorker()
  {
  progressWorker++;
  return progressWorker;
  } 
  
  

void sleep(int millis)
  {
   try{Thread.currentThread().sleep(millis);}
  catch(Exception e){};
  }


String getThreadState(Thread lthread)
  {
    Thread.State m = lthread.getState();
    if(m == Thread.State.BLOCKED) return "BLOCKED";
    if(m == Thread.State.NEW) return "NEW";
    if(m == Thread.State.RUNNABLE) return "RUNNABLE";
    if(m == Thread.State.TERMINATED) return "TERMINATED";
    if(m == Thread.State.TIMED_WAITING) return "TIMED_WAITING";
    if(m == Thread.State.WAITING) return "WAITING";
    else return ("INVALID STATE");
  }
  

public String getStrDateTime(long millis)
  {
  //java.util.Date sd =new Date(millis);
  java.util.Calendar gc = new java.util.Calendar.Builder().build();
  gc.setTimeInMillis(millis);
  int month = gc.get(Calendar.MONTH) + 1;
  int day = gc.get(Calendar.DAY_OF_MONTH);
  int year = gc.get(Calendar.YEAR);
  int hour = gc.get(Calendar.HOUR_OF_DAY);
  int minute = gc.get(Calendar.MINUTE);
  int second = gc.get(Calendar.SECOND);
  return month + "/" + String.format("%02d",day) + "/" + String.format("%04d",year)
      + " " + String.format("%02d",hour)
      + ":" + String.format("%02d",minute) + ":" + String.format("%02d",second);
  }


/**
 * This method responds to a CQ by sending a UDP message that is equivalent to double clicking a CQ message in WSJTX.
 * This method is called exclusively by WSJTXStatus.dr.getTableCellRendererComponent().
 * 
 */
public void transmitCQResponse(int row)
  {
  logger.println("In UDPThreadManager.transmitCQResponse: Clicked on row=" + row);
  if(this.serverWorker.transmitFlag==true)
    {
    if(debugFlag)logger.println("transmitCQResponse invoked when already in transmit mode.");
    return;
    }
  MessageRecord mrl = (MessageRecord)this.wsjtxStatus.jTableEligibleQSOs.getModel().getValueAt(row, 9);
  this.serverWorker.cqCallsign=mrl.callerCallsign;
  this.serverWorker.transmitFlag=true;
  this.serverWorker.smessl = new MessageDisplay("Responding to CQ from " + mrl.callerCallsign,250,500);  // 250,500 are x/y location on screen
  this.serverWorker.smessl.showMessage();
  this.serverWorker.udpwsjtx.messageType4Transmit(mrl);
  }


public void testDispatchThread()
{
  if(SwingUtilities.isEventDispatchThread()) System.err.println("UDPThreadManager.testDispatchThread() is on the EventDispatchThread");
  else System.err.println("UDPThreadManager.testDispatchThread() is NOT on the EventDispatchThread");
}


} // end class UDPThreadManager
