package c;

/* Class Init
 * loadProperties() from properties.txt
 * openLogfile() in folder ./logs.  Read properties.txt "logfile". If "logfile"="autogen" then create logfile with name this.progName
 * readSystemProperties()
 * logProperties()
 * boolean retstat:
 *   Failure to read properties.txt:1
 *   Failure to open logfile:2
 *   Failure to read System Propeties:3
 *   Failure to open database:4
 *   Failure to Construct Propdbutils: 5
 *   Failure to autodelete: 6
 */

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import v.Log.Logfile;
import v.Log.LogfileFactory;
import java.util.Date;
import c.Time.TimeUtils;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.AlreadyBoundException;
import java.rmi.AccessException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.JOptionPane;
import v.File.PropertiesUtils;
import m.SystemInfo;
import m.Propdbutils;
import m.Common;
import m.net.Utils;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;



public class Init
  {
  boolean abort = false; // tell parent program MessageSwitch to abort.
  Logfile logger;
  boolean restartFlag=false;
  PropertiesUtils puObj;
  public int retstat;
  String progName="propagation";
  String dbType="MariaDB"; 
  private String csString;
  
  
  
  
  public Init(String args[])
  {
  int x = 0;
  int y = 0;
  if(!this.loadProperties())
    {
    System.err.println("Failed to Init.loadProperties()");
    retstat=1; return;
    }
  
  if(openLogfile(Common.logfileName,progName)==null) 
    {
    System.err.println("Failed to open logfile.");
    retstat= 2;return;
    }
  
  if(!this.readSystemProperties()) 
    {
    System.err.println("Failed to Init.readSystemProperties()");
    retstat=4; return;
    } // must proceed loadProperties()
  
  this.logProperties();   
  
  Common.dbAllTxt=Common.openDatabase();
  if(Common.dbAllTxt==null)
    {
    System.out.println("Init failed to create database " + Common.DBName + " connection.");
    JOptionPane.showMessageDialog(null, "Initialization Error", "ERROR in openDatabase().  Failed to open database= " + Common.DBName + ".",JOptionPane.ERROR_MESSAGE);
    retstat=5; return;
    }
  
  this.setLookAndFeel();
  
  Common.prodb = new Propdbutils(); if(Common.prodb.retstat==false) {retstat=6; return;}
  if(!Common.prodb.callprefixToLatLonHashmap()){retstat=6; return;}
  
  if(Common.autodelete < 999 && Common.autodelete > 0) if(!Common.prodb.autodelete()){retstat=6; return;}
  
  this.retstat = 0;
  }  // end constructor
  
  
  
/**
 * Note that prerequisite is Logfile
 */
private boolean readSystemProperties()
  {
  try
    {
    InetAddress inetaddr = InetAddress.getLocalHost();
    Common.cannonicalHostName = inetaddr.getCanonicalHostName();
    Common.myIPAddress = inetaddr.getHostAddress();
    }
  catch(UnknownHostException UHE)
    {
    System.out.println("ERROR in Init: Unable to obtain local hostname or ip address."); 
    logger.e("Error in Init: Unable to obtain local hostame or ip address.");
    return false;
    }
  SystemInfo siObj = new SystemInfo(logger); 
  Common.hostName=siObj.hostName;
  Common.osName = siObj.osName;
  Common.arch = siObj.architecture;
  Common.screenHeight=siObj.screenHeight;
  Common.screenWidth=siObj.screenWidth;
  Common.programName=siObj.programName;
  System.out.println("Program name=" + Common.programName);
  siObj.logInfo();
  m.net.Utils netutils = new Utils(logger);
  String preferredHostAddress = netutils.getPreferredHostAddress();
  if(Common.localIPAddress.equalsIgnoreCase("auto")) Common.localIPAddress = preferredHostAddress;
  String localHostLANAddress = netutils.getLocalHostLANAddressString();
  System.out.println("preferred=" + preferredHostAddress + ", local=" + localHostLANAddress);
  if(Common.debugFlag)netutils.displayAllInterfaces();
  return true;
  }  // end readSystemProperties
 


    
private Logfile openLogfile(String logfileName, String progName)  
  {
  Logfile loggerl;
  File logdir = new File("logs");  if(!logdir.exists()) logdir.mkdir();
  String logfileNameActual = logfileName;
  if(logfileNameActual.equalsIgnoreCase("autoGen"))
    {
    logfileNameActual = "logs/" + progName + "_" + new TimeUtils().TimestampToDateStringFile() + ".txt";  
    }
  loggerl = new LogfileFactory(logfileNameActual,progName,"0",Logfile.LOG_LEVEL_MAX,false,null).getLogfile();
  if(!loggerl.logfileStatus) 
    {
    System.out.println("ERROR: Failed to open logfile " + logfileNameActual);
    JOptionPane.showMessageDialog(null, "Initialization Error", "ERROR in Init.openLogfile.  Failed to open logfile " + logfileNameActual + ".",JOptionPane.ERROR_MESSAGE);
    return null;
    }
  Common.logfileName=logfileName;
  loggerl.tprintln("BOJ messageswitch");
  this.logger=loggerl;
  Common.logger=loggerl;
  return loggerl;
  } // end openLofgile

  


/**
 * Failure in this method is not fatal.
 */
private boolean loadProperties()
{
// create and load default properties
this.puObj = new PropertiesUtils("properties.txt");
if(puObj.getValidStatus()==false)
  {    
  JOptionPane.showMessageDialog(null, "Properties file=properties.txt not found.\n"
      + "Program cannot run without these parameters.", 
    "Fatal ERROR", JOptionPane.ERROR_MESSAGE);
  return false;
  }
String debugFlag;
// Load properties
Common.logfileName=puObj.loadParameterS("logfile");
Common.debugFlag=puObj.loadParameterB("debugFlag");
Common.wsjtxFile=puObj.loadParameterS("wsjtxFile");
Common.DBType=puObj.loadParameterS("DBType");
Common.DBServer=puObj.loadParameterS("DBServer");
Common.DBName=puObj.loadParameterS("DBName");
Common.DBUsername=puObj.loadParameterS("DBUsername");
Common.DBPassword=puObj.loadParameterS("DBPassword");
Common.homeQTH=puObj.loadParameterS("homeQTH");
Common.callsign=puObj.loadParameterS("callsign");
Common.entityFile=puObj.loadParameterS("entityFile");
Common.zipcodeFile=puObj.loadParameterS("zipcodeFile");
Common.queryRowLimit=puObj.loadParameterI("queryRowLimit");
Common.qthrealtime=puObj.loadParameterB("qthrealtime");
Common.adminFlag=false;
Common.adminFlag=puObj.loadParameterB("adminFlag");
if(Common.queryRowLimit < 2000) Common.queryRowLimit=2000;
this.csString=puObj.loadParameterS("columnSelections");
Common.autodelete=puObj.loadParameterI("autodelete");
if(Common.autodelete==-99999) Common.autodelete=999;  // autodelete key does not exist in properties.txt
String[] csArray = csString.split(",");
int[] csIntArray = new int[csArray.length];
for(int x=0; x < csArray.length; x++)csIntArray[x]=Integer.parseInt(csArray[x]);
Common.columnSelections=csIntArray;
Common.UDPPort = puObj.loadParameterI("UDPPort");
Common.localIPAddress = puObj.loadParameterS("localIPAddress");
Common.lookAndFeel=puObj.loadParameterS("lookAndFeel");
return true;
}  // end loadProperties()



private void logProperties()
 {
 logger.println("Callsign=" + Common.callsign);
 logger.println("Database type=" + Common.DBType + ", server=" + Common.DBServer + ", database name=" + Common.DBName + ", database password=" + Common.DBPassword);
 logger.println("wsjtxFile=" + Common.wsjtxFile);
 logger.println("Debug flag=" + Common.debugFlag);
 logger.println("Hostname=" + Common.hostName + ", osName=" + Common.osName + ", arch=" + Common.arch);
 logger.println("Cannonical hostname=" + Common.cannonicalHostName + ", my ip address=" + Common.myIPAddress);
 logger.println("Program name=" + Common.programName);
 logger.println("Database server=" + Common.DBServer + ", database name=" + Common.DBName + ", database password=" + Common.DBPassword);
 logger.println("Selected columns=" + this.csString);
 logger.println("queryRowLimit=" + Common.queryRowLimit);
 logger.println("qthrealtime=" + Common.qthrealtime);
 logger.println("admin flag=" + Common.adminFlag);
 logger.println("localIPAddress=" + Common.localIPAddress);
 logger.println("lookAndFeel=" + Common.lookAndFeel);
 } // end logProerties()



/**
 * You can also use the actual class name of a Look and Feel as the argument to UIManager.setLookAndFeel(). 
 * For example,Set cross-platform Java L&F (also called "Metal")
 * UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
 * or
 * Set Motif L&F on any platform
 * UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel");
 */
private void setLookAndFeel()
  {
  if(Common.lookAndFeel==null || Common.lookAndFeel.equals(""))return;
  String landf = Common.lookAndFeel.toLowerCase();
  logger.println("Setting lookAndFeel to " + landf);
  try
    {
    switch (landf)
      {
      case "metal":  // Set cross-platform Java L&F (also called "Metal")
          UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());   
          break;
      case "system": // System platform look and feel, native to the system upon which it is running.
          //For Linux and Solaris, the System L&Fs are "GTK+" if GTK+ 2.2 or later is installed, "Motif" otherwise. 
          // For Windows, the System L&F is "Windows," which mimics the L&F of the particular Windows OS that is running
          UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
          break;
      case "motif": // The "System" L&F if GTK+ 2.2 or later is not installed
          UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.MotifLookAndFeel"); break;
      case "gtk": // The "System" L&F if GTK+ 2.2 or later is installed
          UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.GTKLookAndFeel"); break;
      case "windows": 
          UIManager.setLookAndFeel("com.sun.java.swing.plaf.motif.WindowsLookAndFeel"); break;    
      default: logger.e("Invalid look and feel class=" + Common.lookAndFeel);
      }  // end switch  
    } // end try  
  catch(ClassNotFoundException cnfe){logger.ee("Failed to locate LookAndFeelClassName for " + Common.lookAndFeel,cnfe); return;}
  catch(InstantiationException ie){logger.ee("Failed to instantiate LookAndFeelClassName for " + Common.lookAndFeel,ie); return;}
  catch(IllegalAccessException iae){logger.ee("Illegal Access Exception LookAndFeelClassName for " + Common.lookAndFeel,iae); return;}
  catch(UnsupportedLookAndFeelException ue){logger.ee("Unsupported Look And Feel Exception LookAndFeelClassName for " + Common.lookAndFeel,ue); return;}
  }
  


    

     

















  }  // end class Init






