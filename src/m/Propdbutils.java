/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package m;
import m.db.Database;
import v.Log.Logfile;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import m.db.ResultSetTableModel;
import java.sql.Date;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import m.Common;
import v.MessageDisplay;
import java.util.ArrayList;
import javax.swing.JOptionPane;
import java.nio.file.*;
import java.io.IOException;
import java.util.GregorianCalendar;
import c.Time.TimeUtils;
import java.nio.file.attribute.FileAttribute;
import java.io.File;
import v.File.FileChoose;





/**
 *
 * @author chas
 */
public class Propdbutils 
{
private final Database dbl;
private final Logfile logger;
private java.sql.Date currentDate;
private final boolean debugFlag;
public int duplicates; // number of times that submit failed owing to duplicate primary key
public boolean retstat;
//  hashprefixlatlon accessed via this.callsignToLatLonFloat() and this.callsignToLatLonDouble().  
//  this.callsignToLatLonFloat() is not directly called by any other class.
//  this.callsignToLatLonDouble() is called from AlltxtUtils and PopulateAlltxt.
//  hathprefixcountry is used by this.callsignToCountrycode() which is called from PopulateAlltxt.
// These two methods are used during WSJTX import by classes ReadWSJTXFile and AlltxtUtils in order to obtain maidenhead for callsign.
//Created by this.callprefixToLatLonHashmap()
private HashMap<String, float[]> hashprefixlatlon; // used by this.callsignToLatLonFloat. As of 02/07/23 this method is called only from Propdbutils.
// hashprefixcountry mirrors table callsignprefixes which is comprised of columns prefix and countrycode.
private HashMap<String, String> hashprefixcountry; // accessed via prefixToCountry(). // 
// This HashMap is simply persistence of the callsignprefixes table in memory to speed queries.
public ArrayList<String> alcountryabbr;  // countryabbr.abbr
public ArrayList<String> alcountrynames;  // countryabbr.name
public ArrayList<String>alstateabbr;  // states.code
public ArrayList<String>alstatenames; // states.name

public Propdbutils()
  {
  this.retstat = false;
  this.logger = Common.logger;
  this.dbl = Common.dbAllTxt;    
  this.currentDate = new java.sql.Date(System.currentTimeMillis());
  this.debugFlag = Common.debugFlag;
  this.duplicates=0;
  //if(!this.callprefixToLatLonHashmap()) return; // creates HashMap<String, float[]> hashprefixlatlon;
  //float[] ft=this.prefixToLatLon("2T"); System.out.println("2T lat=" + ft[0] + ", lon=" + ft[1]);
  //this.displayHashPrefixCountry();
  //this.displayHashPrefixLatLon();
  this.populateCountries();
  this.populateStates();
  this.retstat = true;
  } // end constructor



/**
 * 
  Time/date=" + sql datetime atrl.ldt + ", radioFrequency=" + atrl.radioFrequency
  rxtx=" + atrl.rxtx + ", mode=" + atrl.mode
  rssi=" + atrl.rssi
  timeOffset=" + atrl.timeOffset
  audioFrequency=" + atrl.audioFrequency);
  Message=" + atrl.concatMessage(atrl.message));
  Caller callsign=" + atrl.callerCallsign + ", caller maidenhead=" + atrl.callerMaiden);
  Respondent callsign=" + atrl.respondentCallsign + ", responndent maidenhead=" + atrl.respondentMaiden);
  Source maidenhead=" + atrl.sourceMaidenhead + ", source lat=" + atrl.sourceLat + ", source lon=" + atrl.sourceLon);
  Distance between=" + atrl.distanceBetween + ", bearing=" + atrl.initialBearing);
  Respondent reported rss=" + atrl.respondentReportedrss + ", caller reported rss=" + atrl.callerReportedrss);
  Contest identifier=" + atrl.contestIdentifier);
  * 
  Database columns=ldt, radioFrequency, rxtx, mode, rssi, timeOffset, audioFrequency,
  message, callerCalsign, callerMaiden, respondentCallsign, respondentMaiden, sourceMaiden,
  sourceLat, sourceLon,  sourceLat, sourceLon, distanceBetween, initialBearing,
  respondentReportedrss, contestIdentifier.
  Example of datetime = '2015-11-05 14:29:36'
  Called by ReadWSJTXile.writeToDatabase()
 * @param atrec
 * @return 
 */
public boolean submitAlltxtDatabase(MessageRecord atrec) 
  {
  DateTimeFormatter format1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");  // to convert java LocalDateTime to string for sql datetime
  String sqlStmt;
  java.sql.Connection con = this.dbl.getConnection();
  sqlStmt=
      "insert into ALLTXT "
      + "set LDT = '" + atrec.ldt.format(format1) + "', RADIOFREQUENCY = '" + atrec.radioFrequency
      + "', RXTX = '" + atrec.rxtx + "', MODE = '" + atrec.mode + "', RSSI = '" + atrec.rssi
      + "', TIMEOFFSET = '" + atrec.timeOffset + "', AUDIOFREQUENCY = '" + atrec.audioFrequency
      + "', MESSAGE = '" + atrec.concatMessage(atrec.message) 
      + "', CALLERCALLSIGN = '" + atrec.callerCallsign + "', CALLERMAIDEN = '" + atrec.callerMaiden
      + "', RESPONDENTCALLSIGN = '" + atrec.respondentCallsign + "', RESPONDENTMAIDEN = '"  + atrec.respondentMaiden
      + "', SOURCEMAIDEN = '" + atrec.sourceMaidenhead + "', SOURCELAT = '" + atrec.sourceLat
      + "', SOURCELON = '" + atrec.sourceLon + "', DISTANCEBETWEEN = '" + atrec.distanceBetween 
      + "', INITIALBEARING = '" + atrec.initialBearing
      + "', RESPONDENTREPORTEDRSS = '" + atrec.respondentReportedrss + "', CONTESTIDENTIFIER = '" + atrec.contestIdentifier 
      + "', CALLERREPORTEDRSS = '" + atrec.callerReportedrss    
      + "', MESSAGETYPE = '" + atrec.messageType
      + "'"
      ;
    if(Common.DBType.equals("Derby_res"))
      {
      sqlStmt="insert into ALLTXT values("
        + "'" + atrec.ldt.format(format1) + "'," 
        + atrec.messageType + "," 
        + atrec.radioFrequency + ",'"
        + atrec.rxtx + "','" 
        + atrec.mode + "'," 
        + atrec.rssi + ","
        + atrec.timeOffset 
        + "," + atrec.audioFrequency + ",'"
        + atrec.concatMessage(atrec.message) + "',";
        if(atrec.callerCallsign==null) sqlStmt += "'',";
        else sqlStmt += "'" + atrec.callerCallsign + "',";
        if(atrec.callerMaiden==null) sqlStmt += "'',";
        else sqlStmt += "'" + atrec.callerMaiden + "',";
        if(atrec.respondentCallsign==null) sqlStmt += "'',";
        else sqlStmt += "'" + atrec.respondentCallsign + "',";
        if(atrec.respondentMaiden==null) sqlStmt += "'',";
        else sqlStmt += "'" + atrec.respondentMaiden + "',";
        sqlStmt += atrec.callerReportedrss + ",";
        if(atrec.sourceMaidenhead==null) sqlStmt += "'',";
        else sqlStmt += "'" + atrec.sourceMaidenhead + "',";
        sqlStmt += + atrec.sourceLat + ","
        + atrec.sourceLon + "," 
        + atrec.distanceBetween + ","
        + atrec.initialBearing + ","
        + atrec.respondentReportedrss + "," ;
        if(atrec.contestIdentifier==null) sqlStmt += "''";
        else sqlStmt += "'" + atrec.contestIdentifier + "'";
        sqlStmt += ")";
      }
    if(!dbl.SQLJDBC(sqlStmt)) 
      {
      if(dbl.lastSQLException != null) if(dbl.lastSQLException.getMessage().contains("Duplicate entry"))
        {
        logger.printwarn("Duplicate entry ignored."); 
        this.duplicates++;
        return true;
        }
      logger.tprintwarn("ERROR: SQLJDBC returned false. Statement=" + sqlStmt);
      return false;
      }
  return true;
  } // end method submitAlltxtDatabase()








/** 
 * Create new propagation.callmaid table entry or if record with matching callmaid.callsign exists then update it with new maidenhead.
 * Only callsign, maidenhead, and mdate are populated.
 * Always create a new record. 
 * This method is not currently utilized.
 * @param 
 * @return 
 */
/*
public boolean insertUpdateCallmaiden(String callsign, String maidenhead)
  {
  CallmaidRecord cr = new CallmaidRecord(callsign, maidenhead);
  return this.insertUpdateCallmaidRecord(cr, "U");
  } // end method insertUpdateCallmaiden
*/


/** 
 * Create new propagation.callmaid table entry or if record with matching callmaid.callsign exists then update it with new maidenhead.
 * Only callsign, maidenhead, and mdate are populated.
 * Create a new record if not extant.  Update existing record only if CallmaidRecord values are not null.
 * This method is called from exclusively by PopulateAlltxt.createUpdateCallmaid() to enter or update callmaid records with maidenhead values received from WSJTX.
 * @param 
 * @return 
 */
public boolean insertUpdateCallmaiden(String callsign, String maidenhead, String countryCode, String DSArray)
  {
  CallmaidRecord cr = new CallmaidRecord(callsign, maidenhead, countryCode, DSArray);
  return this.insertUpdateCallmaidRecord(cr, "S");
  } // end method insertUpdateCallmaiden









/**
 * Insert into table zipcodegeo the contents of class ZipcodeRecord.
 * The table is assumed void.  There is no search for existing matching records.
 * This method is called by Zipcodegeo.parseFile().
public int zipcode;
public String city;// varchar 40
public String stateAbbr; //varchar 2
public float latitude;
public float longitude;
public String country; // "US"
public String county;
public int dominantAreaCode;
public String timezone; // varchar(3)
public int totalPopulation;
public float medianAge;
public int houseMedianValue; // int(8)
public int medianIncome;    // int(7)
public int femalePop;  // int(5)
public int malePop;    // int(5)
 * @param zr
 * @return 
 */
public boolean insertZipcodegeo(ZipcodeRecord zr)
  {
  String sqlStmt;
  java.sql.Connection con = this.dbl.getConnection();
  sqlStmt=
      "insert into zipcodegeo "
      + "set zipcode = '" + zr.zipcode 
      + "', city = '" + zr.city
      + "', state = '" + zr.stateAbbr + "', latitude = '" + zr.latitude + "', longitude = '" + zr.longitude
      + "', country = '" + zr.country + "', county = '" + zr.county
      + "', dominantAreacode = '" + zr.dominantAreaCode 
      + "', timezone = '" + zr.timezone + "', totalpopulation = '" + zr.totalPopulation
      + "', medianAge = '" + zr.medianAge + "', houseMedianValue = '"  + zr.houseMedianValue
      + "', femalePop = '" + zr.femalePop + "', malePop = '" + zr.malePop 
      + "'"
      ;
  if(!dbl.SQLJDBC(sqlStmt)) 
    {
    if(dbl.lastSQLException != null) if(dbl.lastSQLException.getMessage().contains("Duplicate entry")){logger.println("Duplicate entry ignored."); return true;}
    logger.e("ERROR: SQLJDBC returned false. Statement=" + sqlStmt);
    return false;
    }
  return true;    
  }

/**
 * Called by PopulateCallmaidCQITU, PopulateCallmaidMaidenhead, ReadWSJTXFile.
 * Query table callmaid.  Return ResultSetTableModel or null for failure.
 * @param selectClause
 * @param whereClause
 * @return ResultSetTableModel or null for error.
 */
public synchronized ResultSetTableModel queryCallmaid(String selectClause, String whereClause)
  {
  int rowcount;
  // Query callmaid 
  String queryString = "select " + selectClause + " from CALLMAID where " + whereClause + "";
  ResultSetTableModel rstmlocal=null;
  try{rstmlocal = dbl.selectT(queryString);}
  catch(SQLException e)
    {
    logger.tee("Database failed to execute selectT() in Propdbutil.queryCallmaid().", e);
    return null;
    }
  if(rstmlocal==null) 
    {
    logger.te("Database.selectT() failed.");
    return null;
    }
  rowcount=rstmlocal.getRowCount();
  if(debugFlag)logger.println(queryString + " returned " + rowcount + " rows.");
  if(rowcount==-1) // error
    {
    logger.te("Query failed. Query=" + queryString);
    return null;
    } 
  return rstmlocal;
  } // end queryCallmaid()



/**
 * Called by ReadWSJTXFile.maidFromCallsign().
 * Query callmaid for a callsign and return maidenhead
 * @param callsign
 * @return maidenhead or null for error.  Return null for failure to find callsign in callmaid table.
 */
public String queryCallmaidCallsign(String callsign)
  {
  int rowcount;
  String retval=null;
  // Query callmaid 
  String queryString = "select maidenhead from CALLMAID where callsign = '" + callsign + "'";
  ResultSetTableModel rstmlocal=null;
  try{rstmlocal = dbl.selectT(queryString);}
  catch(SQLException e)
    {
    logger.tee("Database failed to execute selectT().", e);
    return null;
    }
  if(rstmlocal==null) 
    {
    logger.te("Database.selectT() failed for select maidenhead from CALLMAID.");
    return null;
    }
  rowcount=rstmlocal.getRowCount();
  if(debugFlag)logger.println(queryString + " returned " + rowcount + " rows.");
  if(rowcount==-1) // error
    {
    logger.te("Query failed. Query=" + queryString);
    return null;
    } 
  if(rowcount==0) 
    {
    if(debugFlag)logger.println("Failed to find maidenhead in callmaid table for callsign=" + callsign);
    return null;
    }
  try{rstmlocal.rsl.first();}catch(SQLException se){logger.tee("Failed to move to first row in callmaid resultset.", se); return null;}
  try{retval = rstmlocal.rsl.getString(1);}
  catch(SQLException se){logger.tee("Failed to get column 1 from callmaid", se); return null;}
  return retval;
  } // end queryCallmaidCallsign()




/**
 * Called by UIMain.updateStatus().
 * @param tableName
 * @return 
 */
public int totalRecordsInTable(String tableName)
  {
  int rslrowcount;
  int tableRows;;
  // Query table 
  String queryString = "select count(*) from " + tableName;
  ResultSetTableModel rstmlocal=null;
  try{rstmlocal = dbl.selectT(queryString);}
  catch(SQLException e)
    {
    logger.tee("Database failed to execute queryString=" + queryString, e);
    return -1;
    }
  if(rstmlocal==null) 
    {
    logger.te("Database.selectT() failed for queryString=" + queryString + ".");
    return -1;
    }
  rslrowcount=rstmlocal.getRowCount();
  if(debugFlag)logger.println(queryString + " returned " + rslrowcount + " rows.");
  if(rslrowcount==-1) // error
    {
    logger.te("Query failed. Query=" + queryString);
    return -1;
    } 
  if(rslrowcount==0) 
    {
    if(debugFlag)logger.println("No rows returned for query=" + queryString);
    return -1;
    }
  try{rstmlocal.rsl.first();}catch(SQLException se){logger.tee("Failed to move to first row in query=" + queryString + ".", se); return -1;}
  try{tableRows = rstmlocal.rsl.getInt(1);}
  catch(SQLException se){logger.tee("Failed to get column 1 from query=" + queryString, se); return -1;}
  return tableRows;
  }













/**
 * Called by CallmaidUtils.queryZipcodegeo().
 * flagme this.queryGeneric() should be used instead.
 * @param whereClause
 * @return 
 */
public ResultSetTableModel queryZipcodegeo(String whereClause)
  {
  int rowcount;
  // Query zipcodegeo
  String queryString = "select * from zipcodegeo where " + whereClause + "";
  ResultSetTableModel rstmlocal=null;
  try{rstmlocal = dbl.selectT(queryString);}
  catch(SQLException e)
    {
    logger.tee("Database failed to execute selectT().", e);
    return null;
    }
  if(rstmlocal==null) 
    {
    logger.te("Database.selectT() failed.");
    return null;
    }
  rowcount=rstmlocal.getRowCount();
  if(debugFlag)logger.println(queryString + " returned " + rowcount + " rows.");
  if(rowcount==-1) // error
    {
    logger.te("Query failed. Query=" + queryString);
    return null;
    } 
  return rstmlocal;
  } // end QuerZipcodegeo()






/**
 * Perform generic select statement
 * Called by TableEditCallmaid, UIMain.
 * @param selectString
 * @return ResultSetTableModel
 */
public ResultSetTableModel queryGeneric(String selectString)
  {
  int rowcount;
  // Query database
  ResultSetTableModel rstmlocal=null;
  try{rstmlocal = dbl.selectT(selectString);}
  catch(SQLException e)
    {
    logger.tee("Database failed to execute generic selectT().", e);
    return null;
    }
  if(rstmlocal==null) 
    {
    logger.te("Database.selectT() failed.");
    return null;
    }
  rowcount=rstmlocal.getRowCount();
  if(debugFlag)logger.println("queryGeneric " + selectString + " returned " + rowcount + " rows.");
  if(rowcount==-1) // error
    {
    logger.te("Select failed. queryGeneric=" + selectString);
    return null;
    } 
  return rstmlocal;
  } // end queryGeneric








/**
 * Query HashMap hashprefixcountry for callsign and return country
 * @param ham license callsign prefix
 * Return String countrycode or null if callsign not found.
 * This method is not currently utilized.
 */
public String callsignToCountrycode(String callsign)
  {
  String countrycode=null;
  if(debugFlag)logger.println("Searching hashprefixcountry for callsign=" + callsign);
  if(this.hashprefixcountry==null){logger.e("Call to method callsignToCountrycode without having initialized hashprefixcountry."); return null;}
  int cl = callsign.length();
  if(cl < 3) {logger.println("WARNING: callsign is of length=" + cl + ". I'm pretty sure that this is not possible."); return null;}
  if(cl < 4) logger.println("WARNING: callsign is of length=" + cl);
  // Try four character prefix
  if((cl > 3) && hashprefixcountry.containsKey(callsign.substring(0,4)))
    {
    countrycode = hashprefixcountry.get(callsign.substring(0,4));
    }
  else if(hashprefixcountry.containsKey(callsign.substring(0,3)))
    {
    countrycode = hashprefixcountry.get(callsign.substring(0,3));
    }
  else if(hashprefixcountry.containsKey(callsign.substring(0,2)))
    {
    countrycode = hashprefixcountry.get(callsign.substring(0,2));
    }
  else {logger.tprintlnwarn("Failed to find countrycode for prefix of callsign=" + callsign + ". callsignToCountrycode returns null.");return null;}
  if(debugFlag)logger.println("Returning countrycode=" + countrycode + " for callsign=" + callsign);
  return countrycode;
  } // end methods callsignToCountrycode()







/**
 * Query HashMap hashprefixlatlon for callsign and return lat/lon.
 * Currently called only by this.callsignToLatLonDouble().
 * @param ham license callsign prefix
 * @return float[] where latlon[0]=latitude, latlon[1]=longitude.  Return null for error.
 */
public float[] callsignToLatLonFloat(String callsign)
  {
  if(debugFlag)logger.println("Searching hashprefixlatlon for callsign=" + callsign);
  if(this.hashprefixlatlon==null){logger.e("Call to method callsignToLatLonFloat without initializing hashprefixlatlon."); return null;}
  float[] latlon;
  int cl = callsign.length();
  if(cl < 3) {logger.println("WARNING: callsign is of length=" + cl + ". I'm pretty sure that this is not possible."); return null;}
  if(cl < 4) logger.println("WARNING: callsign is of length=" + cl);
  // Try four character prefix
  if((cl > 3) && hashprefixlatlon.containsKey(callsign.substring(0,4)))
    {
    latlon = hashprefixlatlon.get(callsign.substring(0,4));
    latlon[0]=latlon[0];
    latlon[1]=latlon[1];
    }
  else if(hashprefixlatlon.containsKey(callsign.substring(0,3)))
    {
    latlon = hashprefixlatlon.get(callsign.substring(0,3));
    latlon[0]=latlon[0];
    latlon[1]=latlon[1];
    }
  else if(hashprefixlatlon.containsKey(callsign.substring(0,2)))
    {
    latlon = hashprefixlatlon.get(callsign.substring(0,2));
    latlon[0]=latlon[0];
    latlon[1]=latlon[1];
    }
  else {logger.tprintlnwarn("Failed to find lat/lon for callsign=" + callsign);return null;}
  if(debugFlag)logger.println("Returning lat/lon=" + latlon[0] + "/" + latlon[1] + " for callsign=" + callsign);
  return latlon;
  }  // end callsignToLatLonFloat()


/**
 * Query HashMap for callsign and return lat/lon.  All prefixes in table callsignprefixes are two, three, or four characters.
 * Called by AlltxtUtils.calculateBearingDistance(), ReadWSJTXFFie.maidFromCallsign().
 * @param ham license callsign prefix
 * @return double[] where latlon[0]=latitude, latlon[1]=longitude.  Return null for error.
 */
public double[] callsignToLatLonDouble(String callsign)
  {
  float[] latlon = this.callsignToLatLonFloat(callsign);
  if(latlon==null) return null;
  double[] lld = new double[2];
  lld[0]=(double)latlon[0];
  lld[1]=(double)latlon[1];
  return lld;
  } // end method callsignToLatLonDouble()




/**
 * Create a hashmap called hashprefixlatlon with key = callsignprefixes.prefix and value = double[] latlon 
 * where latlon[0]=countrygeo.latitude and latlon[1]=countrygeo.longitude.
 * Called by this.constructor().
 * this.hashprefixlatlon is queried via this.callsignToLatLonFloat.
 * The HashMap is used to speed the alternative database query that follows.
 * This method creates HashMap<String, float[]> this.hashprefixlatlon;
 * select b.prefix, a.latitude, a.longitude from countrygeo a inner join callsignprefixes b on (a.abbr=b.countrycode) where b.prefix="<callsignprefix>"
 * @return 
 */
public boolean callprefixToLatLonHashmap()
  {
  ResultSetTableModel rstm;
  String[] prefixlist; // table callsignprefixes is comprised of two columns, prefix and countrycode.
  String[] countrycodelist;// size is same as prefixlist
  logger.tprintln("Begin callprefixToLatLonHashmap.");
  // Read all of table CALLSIGNPREFIXES.prefix into String[] prefixlist and CALLSIGNPREFIXES.countrycode into String[] countrcodelist.
  try
    {
    rstm = this.dbl.selectT("select * from CALLSIGNPREFIXES");  // columns prefix, countrycode(countrycode is country abbreviation)
    prefixlist = new String[rstm.getRowCount()];
    countrycodelist = new String[rstm.getRowCount()];
    if(debugFlag)logger.println("Rowcount in callsignprefixes=" + rstm.getRowCount());
    rstm.rsl.beforeFirst();
    for(int x=0; x < rstm.getRowCount(); x++)  // for each row in CALLSIGNPREFIXES
      {
      rstm.rsl.next();
      //System.out.println("Row=" + x + ", prefix=" + rstm.rsl.getString(1));
      prefixlist[x]=rstm.rsl.getString(1);
      countrycodelist[x]=rstm.rsl.getString(2);
      }
    }
  catch(SQLException se){logger.ee("Failed query of callsignprefixes", se); return false;}
  String[] abbr; // COUNTRYGEO.abbr
  float[] latitude; // COUNTRYGEO.latitude
  float[] longitude;  // COUNTRYGEO.longitude
  // Read all of table COUNTRYGEO. Assign column abbr to abbr[], latitude to latitude[] and longitude to longitude[].
  try
    {
    rstm = this.dbl.selectT("select * from COUNTRYGEO");  // columns prefix, countrycode(countrycode is country abbreviation)
    abbr = new String[rstm.getRowCount()];
    if(debugFlag)logger.println("Rowcount in countrygeo=" + abbr);
    latitude = new float[rstm.getRowCount()];
    longitude = new float[rstm.getRowCount()];
    rstm.rsl.beforeFirst();
    for(int x=0; x < rstm.getRowCount(); x++)
      {
      rstm.rsl.next();
      //System.out.println("Row=" + x + ", abbr=" + rstm.rsl.getString(1));
      abbr[x]=rstm.rsl.getString(1);
      latitude[x]=rstm.rsl.getFloat(2);
      longitude[x]=rstm.rsl.getFloat(3);
      }
    }
  catch(SQLException se){logger.ee("Failed query of countrygeo", se); return false;}
  //System.out.println("callsignprefixes rowcount=" + prefixlist.length + ", countrygeo rowcount=" + abbr.length);
  
   // Create an empty hash map by declaring object of string and double[2] type.  double is for latitude/longitude
  this.hashprefixlatlon = new HashMap<String,float[]>(1100,(float)0.99);
  this.hashprefixcountry = new HashMap<String,String>(1100,(float)0.99);
  int y;
  float[] latlon = new float[2];
  // Populate hashprefixlatlon with prefixlist[], latlon[0], and latlon[1].
  for(int x=0; x < prefixlist.length; x++)  // for each prefix in callsignprefixes and its associated countrycode
    {
    //System.out.println("prefix=" + prefixlist[x] + ", code=" + countrycodelist[x]);
    hashprefixcountry.put(prefixlist[x],countrycodelist[x]);
    // find matching countrygeo.abbr for callsignprefixes.countrycode
    for(y=0; y < abbr.length; y++)  // for each countrygeo.abbr
      {
      if(abbr[y].equals(countrycodelist[x])) break;    // found countrycodegeo.abbr = callsignprefixes.countrycode
      } // end for each countrygeo.abbr
    if(y >= abbr.length) System.err.println("Failed to find countrygeo.abbr for callsignprefixes.countrycode=" + countrycodelist[x]);
    else 
      {
      latlon[0]=latitude[y]; 
      latlon[1]=longitude[y];
      this.hashprefixlatlon.put(prefixlist[x], new float[]{latlon[0],latlon[1]});}
      //if(prefixlist[x].equals("3V"))System.out.println("callsignsprefix.prefix=" + prefixlist[x] + ", callsignsprefixes.countrycode=" + countrycodelist[x] + ", abbr=" + abbr[y] + ", latitude=" + latlon[0] + ", longitude=" + latlon[1]);
      latlon[0]=(float)0;latlon[1]=(float)0;
      }
  float[] llhash = new float[2];
  //if(hashprefixlatlon.containsKey("3V")){llhash = hashprefixlatlon.get("3V");System.out.println("Tunisia3v=" + llhash[0] + ", " + llhash[1]);}
  //if(hashprefixlatlon.containsKey("TS")){  latlon = hashprefixlatlon.get("TS");System.out.println("TunisiaTS=" + latlon[0] + ", " + latlon[1]);}
  //if(hashprefixlatlon.containsKey("BO")){latlon = hashprefixlatlon.get("BO");System.out.println("BoliviaBO=" + latlon[0] + ", " + latlon[1]);}
  //if(hashprefixlatlon.containsKey("SY")){latlon = hashprefixlatlon.get("SY");System.out.println("El Salvador=" + latlon[0] + ", " + latlon[1]);}
  logger.tprintln("End callprefixToLatLonHashmap.");
  return true;
  } // end callprefixToLatLonHashmap()

  
  
/**
 * Used only for debugging
 */  
private void displayHashPrefixLatLon()
  {
  float[] ll = new float[2];
  for (Map.Entry<String, float[]> set : this.hashprefixlatlon.entrySet()) 
    {
    ll=set.getValue();
    // Printing all elements of a Map
    System.out.println("Callsign prefix=" + set.getKey() + ", lat=" + ll[0] + ", lon=" + ll[1]);
    }
  }

  
/**
 * Used only for debugging
 */  
private void displayHashPrefixCountry()
  {
  System.out.println("Following are all elements of hashprefixcountry...");
  for (Map.Entry<String, String> set : this.hashprefixcountry.entrySet()) 
    {
    // Printing all elements of a Map
    System.out.println(set.getKey() + " = " + set.getValue());
    }
  }
  
  
  

/**
 * Return first or last date in allTxtDate.
 * Called by this.allTxtDateAsString(), UIMain.updateStatus().
 * @param firstLast
 * @return 
 */
public java.sql.Date allTxtDate(String firstLast)
  {
  int rowcount;
  java.sql.Date ldtDate;
  // Query alltxt 
  String queryString = "select LDT from ALLTXT order by LDT ";
  if(Common.DBType.equals("Derby_res"))
    {
    if(firstLast.equalsIgnoreCase("first")) queryString += "asc";
    else if(firstLast.equalsIgnoreCase("last")) queryString += "desc";
    else{logger.e("Illicit argument to allTxtDate=" + firstLast); return null;}
    queryString += " fetch first 1 rows ONLY";
    } 
 else
    {
    if(firstLast.equalsIgnoreCase("first")) queryString += "asc";
    else if(firstLast.equalsIgnoreCase("last")) queryString += "desc";
    else{logger.e("Illicit argument to allTxtDate=" + firstLast); return null;}
    queryString +=  " limit 1";
    }
  
  ResultSetTableModel rstmlocal=null;
  try{rstmlocal = dbl.selectT(queryString);}
  catch(SQLException e)
    {
    logger.tee("Database failed to execute selectT().", e);
    return null;
    }
  if(rstmlocal==null) 
    {
    logger.te("Database.selectT() failed.");
    return null;
    }
  rowcount=rstmlocal.getRowCount();
  if(debugFlag)logger.println(queryString + " returned " + rowcount + " rows.");
  if(rowcount==-1) // error
    {
    logger.te("Query failed. Query=" + queryString);
    return null;
    } 
  if(rowcount==0)
    {
    logger.println("Query returned zero rows. Query=" + queryString);
    return null;
    } 
  try{rstmlocal.rsl.first(); ldtDate=rstmlocal.rsl.getDate("ldt");}
  catch(SQLException se){logger.ee("Failed to getDate(ldt)",se); return null;}
  //System.out.println("java.sql.Date=" + ldtDate);
  return ldtDate;
  } // end method allTxtDate()




/**
 * Delete all records in alltxt with ldt greater than Common.autodelete days old.
 * Called by Init.constructor()
 * @return 
 */
public boolean autodelete()
  {
  boolean retstat = false;
  DateTimeFormatter format1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");  // to convert java LocalDateTime to string for sql datetime
  LocalDateTime ldttoday = LocalDateTime.now();
  LocalDateTime ldtpast = ldttoday.minusDays(Common.autodelete);
  int rowcount;
  //System.out.println("ldtpast=" + ldtpast.format(format1));
  String queryString=null;
  MessageDisplay sm = new MessageDisplay("Querying log table for entries before " + ldtpast,350,500);  
  sm.showMessage();
  if(Common.DBType.equalsIgnoreCase("Derby_res"))queryString= "select count(*) from ALLTXT where LDT < CAST('" + ldtpast.format(format1) + "' as timestamp)";
  else if(Common.DBType.equalsIgnoreCase("MariaDB"))queryString= "select count(*) from ALLTXT where LDT < '" + ldtpast.format(format1) + "'";
  ResultSetTableModel rstmlocal=null;
  try
    {
    rstmlocal = dbl.selectT(queryString);
    sm.showOff(); sm=null;
    }
  catch(SQLException e)
    {
    logger.tee(queryString + " failed to execute selectT().", e);
    return false;
    }
  if(rstmlocal==null) 
    {
    logger.te(queryString + "Database.selectT() failed.");
    return false;
    }
  rowcount=rstmlocal.getRowCount();
  if(rowcount==-1) // error
    {
    logger.te("Query failed. Query=" + queryString);
    return false;
    } 
  if(rowcount==0)
    {
    logger.println("Query returned zero rows. Query=" + queryString);
    return false;
    } 
  if(rowcount != 1)
    {
    logger.e(queryString + " returned " + rowcount + " rows when 1 row is required.");
    return false;
    }
  try{rstmlocal.rsl.first(); rowcount = rstmlocal.rsl.getInt(1);}
  catch(SQLException se){logger.ee("Failed to read result from " + queryString,se); return false;}
  if(debugFlag)System.out.println(queryString + " returned " + rowcount + " rows.");
  logger.tprintln("Autodelete. " + queryString + " returned " + rowcount + " rows.");
  if(rowcount < 1000)
    {
    logger.println("Query of expired log entries returned " + rowcount + " rows. This is less then the minimum of 1000 to be acted upon.");
    return true;    
    }
  logger.println(rowcount + " log rows are being deleted in accordance with autodelete=" + Common.autodelete + " days.");
  sm = new MessageDisplay(rowcount + " log rows are being deleted in accordance with autodelete=" + Common.autodelete + " days.",350,500);  
  sm.showMessage();
  String deleteClause=null;
  if(Common.DBType.equalsIgnoreCase("Derby_res"))deleteClause="LDT < CAST('" + ldtpast.format(format1) + "' as timestamp)";
  else if(Common.DBType.equalsIgnoreCase("MariaDB"))deleteClause="LDT < '" + ldtpast.format(format1) + "'";
  if(!dbl.delete("ALLTXT",deleteClause))
    {
    logger.e("Autodelete failed.");
    Common.sleep(5000);
    }
  else retstat = true;
  sm.showOff();
  sm=null;
  return retstat;
  }  // end method autodelete()





/**
 * Return datetime alltxt.ldt as String with format "MM/dd/YY"
 * Called by UIMain.updateStatus().
 * @param firstLast
 * @return 
 */
public String allTxtDateAsString(String firstLast)
  {
  DateFormat df = new SimpleDateFormat("MM/dd/YY");
  java.sql.Date jsd = this.allTxtDate(firstLast);
  if(jsd==null) return null;
  else return df.format(jsd);
  }



/**
 * Column numbers begin at one rather than zero.
 * @param rstml
 * @param columnNum
 * @return 
 */
public ArrayList columnToArrayList(ResultSetTableModel rstml, int columnNum)
  {
  int[] conversiontypearray=this.getConversionTypes(rstml,null);
  int conversiontype=conversiontypearray[columnNum -1];
  int rowcount = rstml.getRowCount();
  ArrayList al;
  if(Common.debugFlag)
    {
    logger.println("Table name=" + rstml.getTableName() + ", column number=" + columnNum);
    logger.println("Conversion type=" + conversiontype);
    }
  switch (conversiontype) // create ArrayList based on column type.
    {
    case 1:  // char      
      al = new ArrayList<String>(rowcount);
      break;
    case 2: // no conversion, simple string
      al = new ArrayList<String>(rowcount);
      break;
    case 3:  // Date
      al = new ArrayList<Date>(rowcount);
      break;
    case 4: // Integer
      al = new ArrayList<Integer>(rowcount);
      break;
    case 5: // double
      al = new ArrayList<Double>(rowcount);
      break;
    case 6: // float
      al = new ArrayList<Float>(rowcount);
      break;
    case 7: // Boolean
      al = new ArrayList<Boolean>(rowcount);
      break;
    default: logger.te("Invalid column type=" + conversiontype);
      return null;
    } // end switch on column type
  try
    {
    rstml.rsl.beforeFirst();
    for(int x=0; x < rstml.getRowCount(); x++)  // for each row in ResultSetTableModel
      {
      rstml.rsl.next();
      if(debugFlag)logger.println("Row=" + x + ", value=" + rstml.rsl.getObject(columnNum));
      switch (conversiontype) // add to ArrayList based on column type.
        {
        case 1:  // char      treat char as String because there is no ResultSet.getChar()
          al.add(rstml.rsl.getString(columnNum));
          break;
        case 2: // no conversion, simple string
          al.add(rstml.rsl.getString(columnNum));
          break;
        case 3:  // Date
           al.add(rstml.rsl.getDate(columnNum));
          break;
        case 4: // Integer
          al.add(rstml.rsl.getInt(columnNum));
          break;
        case 5: // double
          al.add(rstml.rsl.getDouble(columnNum));
          break;
        case 6: // float
          al.add(rstml.rsl.getFloat(columnNum));
          break;
        case 7: // Boolean
          al.add(rstml.rsl.getBoolean(columnNum));
          break;
        default: logger.te("Invalid column type=" + conversiontype);
          return null;
        } // end switch on column type      
      } // end for each row in ResultSetTableModel    
    } // end try block
  catch(SQLException se){logger.te("columnToArrayList failed with SQLException=" + se.getMessage()); return null;}
  //for(int x=0; x < rowcount; x++)System.out.println("Row " + x + "=" + al.get(x));
  return al;
  }




/**
 * Called by this.columnToArrayList().
 * Take as argument a list of conversion types.  If the conversion type is zero then derive one from database column type.
 * This method maps SQL column types to display conversion types used to retrieve a column from the database and display it.
 * If the argument columnTypes is null or any element in that array is "0" then the conversion type will be derived from the database column type.
 * Current conversion types are as follows:  Others will surely follow.
 * 0) Obtain column type from ResultSet and format automatically.
 * 1) Char. display as as string with length=1
 *    SQL Type: 1
 * 2) No conversion simply display a string. 
 * 2xx) String of which first xx characters are displayed.
 *   SQL Types: -16, -1, -15, -9, 12, 93.
 * 3) Date SQL type 21
 * 4) Integer
 * 4xx) Integer formatted to xx digits.
 *   SQL Types: -5, 4, -6, 5
 * 5) Double
 * 5xx
 *   SQL Types: 8
 * 6) Float
 * 6xx)
 *   SQL Types: 6, 7, 3.
 * 7) Bool,Boolean.  In MySQL this is a tinyint(1).  0 means false, non-zero means true.
 *    SQL types: 16
 * DEPENDENCY: This method relies on this.rstm which means that this.queryTable() must precede this method.
 * @param columnTypes
 * @return 
 */
private int[] getConversionTypes(ResultSetTableModel rstml, int[] columnTypes)
  {
  int colCnt=0,x=0,ct;
  int[] cTypes=null;
  // populate dbColTypes from ResultSet
  int[] dbColTypes = new int[rstml.getColumnCount()]; // sql column types from database
  dbColTypes[0]=1;
  colCnt=rstml.getColumnCount();
  try
    {
    for(x=1; x <= colCnt; x++)
      {
      logger.println("Col " + x + " label=" + rstml.getColumnLabel(x) + ", name=" + rstml.getColumnName(x-1) + ", typeName=" + rstml.getColumnTypeName(x) + ", type=" + rstml.getColumnType(x));
      dbColTypes[x-1]=rstml.getColumnType(x);
      }
    }
  catch(SQLException se){logger.tee("Failed to get column type for column=" + x, se); return null;}
  // populate cTypes[] from dbColTypes
  cTypes = new int[colCnt];
  for(x=0; x < colCnt; x++)
    {
    ct=dbColTypes[x]; // give it to shorter variable to cut down on the typing.
    if(ct==1)cTypes[x]=1; // char
    else if(ct==-16 || ct==-1 || ct==-15 || ct==-9 || ct==12)cTypes[x]=2;  //varchar
    else if(ct==93)cTypes[x]=2;  //datetime is read as string/varchar
    else if(ct==91)cTypes[x]=3;  // date
    else if(ct==-5 || ct==4 || ct==-6 || ct==5) cTypes[x]=4;  // integer
    else if(ct==8) cTypes[x]=5;  //double
    else if(ct==6 || ct==7 || ct==3)  cTypes[x]=6;  // float
    else if(ct==16) cTypes[x]=7;  // boolean
    else{logger.e("Please add code to getConversionTypes() to handle SQL type=" + ct); return null;}
    }
  if(Common.debugFlag)
    {
    try{for(int z=0; z < dbColTypes.length; z++)logger.println("Label=" + rstml.getColumnLabel(z+1) + ", type=" + dbColTypes[z] + "-" + cTypes[z]);}
    catch(SQLException se){}
    }
  if(columnTypes != null && (columnTypes.length != rstml.getColumnCount()))
      {
      logger.e("Number of entries in columnTypes passed to getConversionTypes() does not agree with number of columns reported by database.");
      logger.println("columnTypes.length=" + columnTypes.length + ", getColumnCount()=" + rstml.getColumnCount());
      return null;
      } 
    
  columnTypes=cTypes;
  return columnTypes;
  } // end getConversionTypes

/**
 * Export table to export/tablename_datetime
 * Called by UIMain.jMenuItemExportLogActionPerformed(), UIMain.jMenuItemExportPrefixesActionPerformed(), UIMain.jMenuItemCountryAbbreviationsActionPerformed, UIMain.jMenuItemExportCountryGeoActionPerformed.
 * @param tablename
 * @param filename
 * @return 
 */
public boolean exportTableToCSV(String tablename, String filename)
  {
  GregorianCalendar cnow = new GregorianCalendar();
  int year = cnow.get(GregorianCalendar.YEAR);
  int month = cnow.get(GregorianCalendar.MONTH) + 1;
  int day = cnow.get(GregorianCalendar.DAY_OF_MONTH);
  int hour = cnow.get(GregorianCalendar.HOUR_OF_DAY);
  int minute = cnow.get(GregorianCalendar.MINUTE);
  String dtstr = new String("-" + year + "-" + month + "-" + day + "-" + hour + "-" + minute);
  File fex = new File("export");
  if(!fex.exists())if(!fex.mkdir()){logger.e("Failed to create directory export. Unable to export table."); return false;}
  //catch(IOException ioe){logger.ee("Failed to create directory export. Unable to export table.",ioe); return false;}
  String filepathcsv = "export/" + filename + dtstr + ".csv";
  // delete existing .csv dump
  Path pathcsv = FileSystems.getDefault().getPath(filepathcsv);
  try 
    {
    Files.delete(pathcsv);
    } 
  catch (NoSuchFileException x) 
    {
    logger.println("no such file or directory," + pathcsv);
    } 
  catch (IOException x) 
    {
    logger.ee("ERROR: failed to delete file=" + pathcsv,x);
    return false;
    }
  // delete existing table dump
  String filepathtab = "export/" + filename + dtstr + ".csv";
  Path pathtab = FileSystems.getDefault().getPath(filepathtab);
  try 
    {
    Files.delete(pathtab);
    } 
  catch (NoSuchFileException x) 
    {
    logger.println("no such file or directory," + pathtab);
    } 
  catch (IOException x) 
    {
    logger.ee("ERROR: failed to delete file=" + pathtab,x);
    return false;
    }
  
  // EXPORT_TABLE produces the same file as EXPORT_QUERY
  //Common.dbAllTxt.SQLJDBC("CALL SYSCS_UTIL.SYSCS_EXPORT_TABLE('APP'" + ",'" + tablename + "','" + filepathtab + "', null, null, null)");
  MessageDisplay sm = new MessageDisplay("Exporting " + tablename + " data to file=" + filepathtab);
  sm.showMessage();
  Common.dbAllTxt.SQLJDBC("CALL SYSCS_UTIL.SYSCS_EXPORT_QUERY('select * from " + tablename + "','" + filepathcsv + "', null, null, null)");
  sm.showOff(); sm=null;
  JOptionPane.showMessageDialog(null, "Completed export of table " + tablename + " to " + filepathcsv,"Data Export Complete", JOptionPane.INFORMATION_MESSAGE);    
  return true;
  } // end exportTableToCSV()



public boolean importTableFromCSV(String tablename)
  {
  FileChoose fc = new FileChoose("csv",logger);
  fc.setDialogTitle("File must have been produced by this program's export function.");
  String filename = fc.open();
  String sqlStmt = "CALL SYSCS_UTIL.SYSCS_IMPORT_TABLE(" + "'APP','" + tablename + "','" + filename + "',null,null,null,1)";
  MessageDisplay sm = new MessageDisplay("Importing callsign data from file=" + filename);
  sm.showMessage();
  boolean retVal = Common.dbAllTxt.SQLJDBC(sqlStmt);
  if(!retVal)logger.e("Failed to import table " + tablename);
  sm.showOff();sm=null;
  JOptionPane.showMessageDialog(null, "Completed import of file " + filename,"Data Import Complete", JOptionPane.INFORMATION_MESSAGE);    
  return retVal;
  }


public boolean recordIsUnique(String sqlClause)
  {
  ResultSetTableModel rstmlocal=null; 
  try{rstmlocal=this.dbl.selectT(sqlClause);}
  catch(SQLException se){logger.ee("Failed query=" + sqlClause, se); return false;}
  int rowcount = rstmlocal.getRowCount();
  if(rowcount > 0)  { return false; }
  else if(rowcount==-1) // error
    {
    logger.te("Query failed. Query=" + sqlClause);
    return false;
    } // insertModifyString() returned error.
  else if(rowcount==0) return true;
  return false;
  }









/**
 * Called by TableEditCallmaid.jButtonSaveActionPerformed() to create a new callmaid record.
 * Also called by ReadWSJTX.createUpdateCallmaid() via this.insertUpdateCallmaiden().
 * @param rstmlocal
 * @param iu = "I" to insert new record, 
 * "U" to update existing record. Insert new record if "U" specified and record does not exist. i.e update if extant.
 *   This update will overwrite existing data in callmaid with null values if they are null in CallmaidRecord object.
 * "N" to update existing record wherein existing column is null.
 * "S" to update existing record wherein CallmaidRecord value is not null.  i.e. don't overwrite callmaid with null values.
 * This method always creates or updates all columns of record.  To update maidenhead only see PopulateCallmaid.popcallmaid2().
 * @return 
 */
public boolean insertUpdateCallmaidRecord(CallmaidRecord cr,String iu)
  {
  if(debugFlag)logger.println("insertUpdateCallmaidRecord called for callsign=" + cr.callsign + " and flag=" + iu);
  if(debugFlag)cr.logEntity(cr);
  boolean recordExists=false;
  String queryString = "select * from CALLMAID where callsign = '" + cr.callsign + "'";
  ResultSetTableModel rstmlocal=null;
  // Does record exist with matching callsign?
  try{rstmlocal = dbl.selectT(queryString);}
  catch(SQLException e)
    {
    logger.tee("Database.insertUpdateCallmaidRecord() failed to create ResultSetTableModel for table CALLMAID.", e);
    return false;
    }
  if(rstmlocal==null) 
    {
    logger.te("Database.selectT() failed select * from CALLMAID where callsign = " + cr.callsign + ".");
    return false;
    }
  int rowcount=rstmlocal.getRowCount();
  if(rowcount==1)recordExists=true;
  if(debugFlag)logger.println("query for callsign=" + cr.callsign + " yielded " + rowcount + " records.");
  // If (I)nsert has been specified and record exists then log and return.
  if(iu.equalsIgnoreCase("I") && recordExists)
    {
    logger.printwarn("Insert CALLMAID record for callsign=" + cr.callsign + " fails because record already exists.");
    return false;
    }
   try
    {
    if(iu.equalsIgnoreCase("I")) // insert
      {
      rstmlocal.rsl.moveToInsertRow();
      if(debugFlag)logger.println("Inserting into callmaid");
      }
    else if(iu.equalsIgnoreCase("U")) // update
      {
      if(recordExists == false) // then change mode to insert
        {
        logger.println("insertUpdateCallmaidRecord() update flag set, but query for callmaid.callsign=" + cr.callsign + " yielded " + rowcount + " records.");
        logger.println("Changing mode to insert.");
        iu="I"; // change to insert mode
        }
      else // update 
        {
        rstmlocal.rsl.absolute(1);
        }
      }    // update mode requested
    else if(iu.equalsIgnoreCase("N")) // update if existing columns are void.  Do not create if no existing record to update.
      {
      if(recordExists==false)
        {
        logger.printwarn("insertUpdateCallmaidRecord() update null flag set, but no existing record to update. Returning failure.");
        return false;
        }
      rstmlocal.rsl.absolute(1);
      }
    else if(iu.equalsIgnoreCase("S")) // update if CallmaidRecord values are not null. 
      {
      if(recordExists==false)
        {
        logger.printwarn("insertUpdateCallmaidRecord() \"S\" flag set, but no existing record to update. Returning failure.");
        return false;
        }
      rstmlocal.rsl.absolute(1);
      }
    else
      {
      logger.e("insertUpdateCallmaidRecord() called with invalid insert/update flag=" + iu);
      return false;
      }
    if(debugFlag)
      {
      if(iu.equalsIgnoreCase("I"))logger.println("Creating new callmaid. Callsign=" + cr.callsign);
      else logger.println("Updating existing callmaid. Callsign=" + cr.callsign); 
      }
    if(iu.equalsIgnoreCase("I"))rstmlocal.rsl.moveToInsertRow();
    if(iu.equalsIgnoreCase("I") || iu.equalsIgnoreCase("U"))
      {
      rstmlocal.rsl.updateString("CALLSIGN", cr.callsign.toUpperCase());
      rstmlocal.rsl.updateString("MAIDENHEAD", cr.maidenhead);
      rstmlocal.rsl.updateString("FIRSTNAME",cr.firstname);
      rstmlocal.rsl.updateString("MIDDLEINITIAL",cr.middleinitial);
      rstmlocal.rsl.updateString("LASTNAME",cr.lastname);
      rstmlocal.rsl.updateString("STREET",cr.street);
      rstmlocal.rsl.updateString("CITY",cr.city);
      rstmlocal.rsl.updateString("STATE",cr.state);
      rstmlocal.rsl.updateInt("ZIPCODE", cr.zipcode);
      rstmlocal.rsl.updateString("COUNTRY", cr.countrycode);
      rstmlocal.rsl.updateDate("MDATE", cr.mdate);
      rstmlocal.rsl.updateInt("ITU", cr.ITU);
      rstmlocal.rsl.updateInt("CQ",cr.CQ);
      rstmlocal.rsl.updateBoolean("WORKED", cr.worked);
      rstmlocal.rsl.updateDate("CRDATE",cr.crdate);
      if(cr.crdate == null) rstmlocal.rsl.updateDate("CRDATE",cr.mdate);
      rstmlocal.rsl.updateDate("QSODATE",cr.qsodate);
      rstmlocal.rsl.updateString("DS", cr.DS);
      }  // end block for "I" or "U"
        
    else if(iu.equalsIgnoreCase("N"))  // update if existing callmaid column is null
      {
      if(ifNullS(rstmlocal,"CALLSIGN")) rstmlocal.rsl.updateString("CALLSIGN", cr.callsign.toUpperCase());
      if(ifNullS(rstmlocal,"MAIDENHEAD")) rstmlocal.rsl.updateString("MAIDENHEAD", cr.maidenhead);
      if(ifNullS(rstmlocal,"FIRSTNAME")) rstmlocal.rsl.updateString("FIRSTNAME",cr.firstname);
      if(ifNullS(rstmlocal,"MIDDLEINITIAL")) rstmlocal.rsl.updateString("MIDDLEINITIAL",cr.middleinitial);
      if(ifNullS(rstmlocal,"LASTNAME")) rstmlocal.rsl.updateString("LASTNAME",cr.lastname);
      if(ifNullS(rstmlocal,"STREET")) rstmlocal.rsl.updateString("STREET",cr.street);
      if(ifNullS(rstmlocal,"CITY")) rstmlocal.rsl.updateString("CITY",cr.city);
      if(ifNullS(rstmlocal,"STATE")) rstmlocal.rsl.updateString("STATE",cr.state);
      if(ifNullI(rstmlocal,"ZIPCODE")) rstmlocal.rsl.updateInt("ZIPCODE", cr.zipcode);
      if(ifNullS(rstmlocal,"COUNTRY")) rstmlocal.rsl.updateString("COUNTRY", cr.countrycode);
      rstmlocal.rsl.updateDate("MDATE", cr.mdate);
      if(ifNullI(rstmlocal,"ITU")) rstmlocal.rsl.updateInt("ITU", cr.ITU);
      if(ifNullI(rstmlocal,"CQ")) rstmlocal.rsl.updateInt("CQ",cr.CQ);
      if(ifNullB(rstmlocal,"WORKED")) rstmlocal.rsl.updateBoolean("WORKED", cr.worked);
      if(ifNullD(rstmlocal,"CRDATE")) rstmlocal.rsl.updateDate("CRDATE", cr.crdate);
      if(rstmlocal.rsl.getDate("CRDATE")==null) rstmlocal.rsl.updateDate("CRDATE", rstmlocal.rsl.getDate("MDATE"));
      if(ifNullD(rstmlocal,"QSODATE")) rstmlocal.rsl.updateDate("QSODATE", cr.qsodate);
      if(ifNullS(rstmlocal,"DS")) rstmlocal.rsl.updateString("DS", cr.DS);
      }  // end block for "N"
    
    
    else if(iu.equalsIgnoreCase("S"))  // update if the new CallmaidRecord value is not null.
      {
      if(!ifNullS(cr.callsign)) rstmlocal.rsl.updateString("CALLSIGN", cr.callsign.toUpperCase());
      if(!ifNullS(cr.maidenhead)) rstmlocal.rsl.updateString("MAIDENHEAD", cr.maidenhead);
      if(!ifNullS(cr.firstname)) rstmlocal.rsl.updateString("FIRSTNAME",cr.firstname);
      if(!ifNullS(cr.middleinitial)) rstmlocal.rsl.updateString("MIDDLEINITIAL",cr.middleinitial);
      if(!ifNullS(cr.lastname)) rstmlocal.rsl.updateString("LASTNAME",cr.lastname);
      if(!ifNullS(cr.street)) rstmlocal.rsl.updateString("STREET",cr.street);
      if(!ifNullS(cr.city)) rstmlocal.rsl.updateString("CITY",cr.city);
      if(!ifNullS(cr.state)) rstmlocal.rsl.updateString("STATE",cr.state);
      if(!ifNullI(cr.zipcode)) rstmlocal.rsl.updateInt("ZIPCODE", cr.zipcode);
      if(!ifNullS(cr.countrycode)) rstmlocal.rsl.updateString("COUNTRY", cr.countrycode);
      rstmlocal.rsl.updateDate("MDATE", cr.mdate);
      if(!ifNullI(cr.ITU)) rstmlocal.rsl.updateInt("ITU", cr.ITU);
      if(!ifNullI(cr.CQ)) rstmlocal.rsl.updateInt("CQ",cr.CQ);
      if(!ifNullB(cr.worked)) rstmlocal.rsl.updateBoolean("WORKED", cr.worked);
      if(!ifNullD(cr.crdate)) rstmlocal.rsl.updateDate("CRDATE", cr.crdate);
      if(!ifNullD(cr.qsodate)) rstmlocal.rsl.updateDate("QSODATE", cr.qsodate);
      if(!ifNullS(cr.DS)) rstmlocal.rsl.updateString("DS", cr.DS);
      }  // end block for "S"
    

    if(iu.equalsIgnoreCase("I"))rstmlocal.rsl.insertRow();
    else rstmlocal.rsl.updateRow();
    rstmlocal.commit();
    this.dbl.commit();  
    // The following will throw an exception stating "Current position is after the last row" if you don't populate all columns, 
    // or perhaps all columns in the primary index.
    //rstmlocal.rsl.updateRow();  Only for row updates. this should not be needed for an insert and will cause Derby to throw SQLException 24000
    }
  catch(SQLException se)
    {
    logger.tee("ERROR: insertUpdateCallmaidRecord(). failed to perform database row insert/update into table callmaid for callsign=" + cr.callsign + ", maidenhead=" + cr.maidenhead + ".",se);
    return false;
    } 
  rstmlocal=null;
  return true;
  }// end insertUpdateCallmaidRecord


/**
 * Is a boolean column false?
 */
private boolean ifNullB(ResultSetTableModel rstml, String colName)
  {
  try
    {
    if(rstml.rsl.getBoolean(colName)==false)  return true;
    }
  catch(SQLException se){logger.ee("ifNullB threw SQLException", se); return false;}
  return false;
  }

/**
 * Is a boolean callmaid value false?
 */
private boolean ifNullB(boolean boo)
  {
  if(boo==false) return true;
  else return false;
  }

/**
 * Is an Integer column 0?
 */
private boolean ifNullI(ResultSetTableModel rstml, String colName)
  {
  try
    {
    if(rstml.rsl.getInt(colName)==0)  return true;
    }
  catch(SQLException se){logger.ee("ifNullI threw SQLException", se); return false;}
  return false;
  }

/**
 * Is callmaid integer 0?
 * @param var
 * @return 
 */
private boolean ifNullI(int var)
  {
  if(var == 0) return true;
  else return false;
  }


/**
 * Is a varchar column null or empty?
 */
private boolean ifNullS(ResultSetTableModel rstml, String colName)
  {
  try
    {
    if(rstml.rsl.getString(colName)==null)  return true;
    if(rstml.rsl.getString(colName).equals("")) return true;
    }
  catch(SQLException se){logger.ee("ifNullS threw SQLException", se); return false;}
  return false;
  }

/**
 * Is a callmaid String value null or empty?
 */
private boolean ifNullS(String sl)
  {
  if(sl==null) return true;
  else if(sl.equals("")) return true;
  else return false;
  }

/**
 * Is a date column null?
 */
private boolean ifNullD(ResultSetTableModel rstml, String colName)
  {
  try
    {
    if(rstml.rsl.getDate(colName)==null)  return true;
    }
  catch(SQLException se){logger.ee("ifNullD threw SQLException", se); return false;}
  return false;
  }

/**
 * Is callmaid date value null?
 */
private boolean ifNullD(java.sql.Date dl)
  {
  if(dl==null) return true;
  else return false;
  }




/**
 * Get country.abbr from country.name.
 * If two countrabbr.name entries exist that match '%countryname%' then return the 
 * countryabbr.abbr with the shorter name.
 * @param countryname
 * @return 
 */
public String abbrFromName(String countryname)
  {
  ResultSetTableModel rstm;
  String selectClause="select * from countryabbr where upper(name) like upper('%" + countryname + "%')";
  try{rstm = Common.dbAllTxt.selectT(selectClause);}
  catch(SQLException ioe)
    {
    logger.ee("Failed to query table countryabbr for name=" + countryname, ioe); 
    logger.e("Failed query=" + selectClause);
    return null;
    }
  if(rstm.getRowCount()==1)
    {
    try
      {
      rstm.rsl.absolute(1);
      logger.println("Returning country code=" + rstm.rsl.getString("ABBR") + " for country name=" + countryname);
      return rstm.rsl.getString("ABBR");
      }
    catch(SQLException se)
      {
      logger.printlnwarn("Failed to get ABBR column from table COUNTRYABBR for NAME=" + countryname + ". SQLException=" + se.getMessage()); 
      return null;
      }
    }  // end one countrabbr.name matched countryname
  else if(rstm.getRowCount() > 1)
    {
    logger.printlnwarn("Query of COUNTRYABBR for NAME=" + countryname + " returned " + rstm.getRowCount() + " rows.");
    int shortentry=999;  // length of shortest name
    int shortrow=0;   // row with shortest name
    int y=0;
    String retval="";
    for(int x=1; x < (rstm.getRowCount()-1); x++)
      {
      rstm.absolute(x);
      try{y=rstm.rsl.getString("NAME").length();}
      catch(SQLException se)
        {
        logger.printlnwarn("Failed to read name column from table COUNTRYABBR for row=" + x + ". SQLException=" + se.getMessage()); 
        return null;
        }
      if(y < shortentry) {shortentry = y; shortrow=x;}
      } // end for each matching countryabbr
    rstm.absolute(shortrow);
    try{ retval=rstm.rsl.getString("ABBR");}
      catch(SQLException se)
        {
        logger.printlnwarn("Failed to read name column from table COUNTRYABBR for shortentry row. SQLException=" + se.getMessage()); 
        return null;
        }
    logger.println("Returning abbr=" + retval + " for name=" + countryname);
    return retval;
    } // end block > 1 row returned.
  return null;  // rowcount==0
  } // end abbrFromName()


private boolean populateCountries()
  {
  ResultSetTableModel rstm = this.queryGeneric("select * from COUNTRYABBR order by name");
  if(rstm==null){logger.e("Failed query of COUNTRYABBR.  Aborting."); System.exit(0);}
  this.alcountrynames = this.columnToArrayList(rstm,2);
  this.alcountryabbr = this.columnToArrayList(rstm,1); // ArrayList<String>
  return true;
  }

// populate ArrayList this.alstatenames, alstatesabbr
private boolean populateStates()
  {
  ResultSetTableModel rstm = this.queryGeneric("select * from STATES order by name");
  if(rstm==null){logger.e("Failed query of STATES.  Aborting."); System.exit(0);}
  this.alstatenames = this.columnToArrayList(rstm,2); 
  this.alstateabbr = this.columnToArrayList(rstm,1); // ArrayList<String>
  //System.out.println("flagme. state name[0]=" + alstatenames.get(0) + ", combobox[0]=" + jComboBoxState.getItemAt(0) + ", abbr=" + alstateabbr.get(0));
  return true;    
  }


        
} // end class Propdbutils
