
/*
 * Copyright 2021.
 * Charles Gray.
 * All rights reserved.
 */
package m;
import javax.swing.JOptionPane;
import m.db.Database;
import v.Log.Logfile;
import c.Time.TimeUtils;



/**
 *
 * @author Charles Gray
 */
public class Common
{
public static Database dbAllTxt; // created in Init as dbl.
public static String programName;
public static String DBType; // MariaDB or Derby_res
public static String DBServer;
public static String DBName;
public static String DBUsername;
public static String DBPassword;
public static int mgmtport;    
public static String logfileName;
public static boolean debugFlag;
public static String hostName;
public static String cannonicalHostName;
public static String myIPAddress;
public static Logfile logger;
public static int autodelete=999;
// System properties
public static String osName, arch;
public static int screenHeight, screenWidth;
public static String wsjtxFile;
public static String homeQTH;
public static String callsign;
public static String entityFile;
public static String zipcodeFile;
public static Propdbutils prodb;
public static int[] columnSelections;
public static String[] messTypeString={"Invalid","CQ","QRZ","DE","Response to CQ","Caller report",
    "Respondent report","Caller RRR","Respondent 73","Unused","No Message","Uncategorized message type"};
public static int queryRowLimit;
public static int UDPPort;
public static String localIPAddress;
public static boolean qthrealtime;
public static boolean adminFlag;
public static String lookAndFeel;


// Table export parameters set in ConfigureTextOutput.java
private static String[] textOutParams={"tableDump",".txt",",","\'","\r\n"};
public static void setTextOutParams(String[] params){textOutParams = params;}
public static String[] getTextOutParams(){return textOutParams;}

private final static TimeUtils TU = new TimeUtils(logger);


/**
 * Create database connection.
 * @param dbType
 * @param hostname
 * @param int port
 * @param String DBType
 * @param String dbname
 * @param String userid
 * @param String password
 * @param int rowlimit
 * @param Logfile logger
 * @param int DBID  set to 0 if not using separate database IDs
 * @param String DBMode.  : mode of database, e.g. h2 compatibility mode.  Usually null.
 * @return 
 */
public static Database openDatabase()
  {
  boolean retVal=false;  
  Database dbl=null;
  int rowCount = 0;  // Number of rows returned from database query.
  logger.i("Connecting to database " + DBName);
  dbl=(new Database(DBType,DBServer,0,DBName,DBUsername,DBPassword,queryRowLimit,logger,0,""));
  if(dbl.validStatus==false)
    {
    logger.println("Failed to create database object for database=" + DBName + ".");
    logger.printlnerr("Database object validStatus = false for database " + DBName);
    if(DBType.equals("Derby_res") && dbl.myDBConn.vendorError==40000)logger.e("Derby database is in use by another program.");
    JOptionPane.showMessageDialog(null, DBName + " database Error", "ERROR: Failed to connect to database.",JOptionPane.ERROR_MESSAGE);
    dbl=null;
    return null;
    }
  dbl.debugflag=Common.debugFlag;
  logger.println("Database object validStatus = true for database " + DBName);
  //try{System.out.println("In Init.openDatabase: Connection.isClosed()=" + dbl.getConnection().isClosed());}
  //catch(Exception e){};
  // Create a log entry indicating begin of Switch initialization.
  if(dbl.logAvailable)
    {
    dbl.log('I', "Common","Begin initialization of program " + programName + ".");
    dbl.log('I',"Common","openDatabase() returns true.");
    }
  logger.println("openDatabase() returns true;");
  return dbl;
  } // end openDatabase
  
public static void sleep(int millis)
  {
  try{Thread.sleep(millis);}
  catch(InterruptedException ie){};
  }


public static void garbageCleanup()
  {
  logger.println("garbageCleanup");
  long freeMem;
  String currDateTime = TU.CurrentToDateStringHM24Cur();
  freeMem=Runtime.getRuntime().freeMemory();
  logger.println("The Baby is sleeping on " + currDateTime + ".");
  logger.println("Used memory=" + String.valueOf(Runtime.getRuntime().totalMemory() - freeMem)
          + ", free memory=" + String.valueOf(freeMem)
          + ", total memory=" + String.valueOf(Runtime.getRuntime().totalMemory())
          + ", max memory=" + String.valueOf(Runtime.getRuntime().maxMemory()));
  // Runs the finalization methods of any objects pending finalization.
  System.runFinalization();
  // Garbage cleanup
  System.gc();
  // Report free memory after cleanup.
  freeMem=Runtime.getRuntime().freeMemory();
  logger.println("PostCleanup: Used memory=" + String.valueOf(Runtime.getRuntime().totalMemory() - freeMem)
           + ", Free memory=" + String.valueOf(freeMem)
           + ", total memory=" + String.valueOf(Runtime.getRuntime().totalMemory())
           + ", max memory=" + String.valueOf(Runtime.getRuntime().maxMemory())
           + ", stack trace elements=" + Thread.currentThread().getStackTrace().length
   );
  if(freeMem < 90000000)
    {
    logger.println("WARNING,  Free memory is below 90MB. Runtime.getRuntime().freeMemory()=" + freeMem);
    }
  } // end method garbageCleanup



} // end class Common
