/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package m;

import java.sql.SQLException;
import java.text.DecimalFormat;
import m.db.ResultSetTableModel;
import v.Log.Logfile;
import v.ShowMessage;
import c.CallmaidUtils;
import m.Maidenhead;
import c.System.Cleanup;



/**
 *
 * @author chas
 */
public class PopulateCallmaidCQITU 
{
private final Logfile logger;
private final boolean debugFlag;
public int errors;
public int failedToFindZipgeo;
public int failedLatLon;
public int updatedCallmaid;
private int nonUSCallsign;
private final CallmaidUtils cmu;
private final Propdbutils putils;
private final Maidenhead mhObj;
private int recordCounter;
private Cleanup cleanup;


public PopulateCallmaidCQITU()
  {
  this.logger=Common.logger;      
  this.debugFlag=Common.debugFlag;
  putils = Common.prodb;
  this.mhObj = new Maidenhead(logger);
  this.cmu=new CallmaidUtils(logger,putils,debugFlag);
  this.cleanup = new Cleanup(logger);
  }

/**
 * Produce ResultSetTableModel from callmaid query where "(cq = 0 or cq is NULL or itu = 0 or itu is NULL) and COUNTRY = 'US'"
 * For each record call this.popcqitu().
 * @return 
 */
public boolean popCallmaid()
  {
  errors=0;
  // 1) Get all callmaid records with null CQ or ITU
  ResultSetTableModel rstmcm = putils.queryCallmaid("callsign,maidenhead,state,country,zipcode,CQ,ITU","(cq = 0 or cq is NULL or itu = 0 or itu is NULL) and COUNTRY = 'US'");
  if(rstmcm==null){logger.e("Query of callmaid failed for CQ and ITU."); return false;}
  System.out.println("callmaid where cq or itu = \"\" rowcount=" + rstmcm.getRowCount() + " rows.");
  logger.println("callmaid where cq or itu = \"\" and country=US rowcount=" + rstmcm.getRowCount() + " rows.");
  DecimalFormat df = new DecimalFormat("###,###,###");
  ShowMessage sm = new ShowMessage("Processing " + df.format(rstmcm.getRowCount()) + " records from callmaid where CQ or ITU is null and country=US.\n Record count=" + this.recordCounter,250,500);  
  sm.showMessage();
  try{rstmcm.rsl.first();} // step 1
  catch(SQLException se)
    {
    logger.tee("Failed to go to first record in table callmaid", se);
    return false;
    }
  if(!this.popcqitu(rstmcm, putils)) return false;
  try
    {
    while(rstmcm.rsl.next()) // for each callmaid where cq or itu = ''
      { 
      if(recordCounter%10000 == 0)
        {
        sm.showOff(); sm=null;
        sm = new ShowMessage("Processing " + df.format(rstmcm.getRowCount()) + " records from callmaid where CQ or ITU is null and country=US.\n Record count=" + this.recordCounter,250,500);  
        sm.showMessage();
        System.out.println("recordCounter=" + recordCounter);
        Common.dbAllTxt.commit();
        Common.garbageCleanup();
        }
      recordCounter++;
      if(!this.popcqitu(rstmcm, putils)) continue;
      //if(recordCounter > 20000) break;
      }  // end while
    } // end try
  catch(SQLException se)
    {
    logger.tee("Failed to go to next record in table callmaid", se);
    return false;
    }      
  Common.dbAllTxt.commit();
  sm.showOff();
  System.out.println("Total records in=" + rstmcm.getRowCount() + ", total errors=" + this.errors + ", updatedCallmaid=" + this.updatedCallmaid);
  logger.println("Total records in=" + rstmcm.getRowCount() + ", total errors=" + this.errors + ", updatedCallmaid=" + this.updatedCallmaid);
  return true;    
  } // end method popCallmaid()


/**
 * Populate callmaid cq and itu wherein cq and itu are not already populated and country = 'US'
 * via this.USStateToCQITU(state,zippy,callsign,maidenhead);
 * @param rstmcm
 * @param putils
 * @return 
 */
private boolean popcqitu(ResultSetTableModel rstmcm, Propdbutils putils)
  {
  int rowsupdated;
  ResultSetTableModel rstmzg;
  boolean updateSuccess = false;
  int cqint, ituint;
  int[] cqitu = new int[2];
  String state,country, callsign,maidenhead;
  int zippy;
  double latlon[] = new double[2];
  // get existint cq into int cq
  try{cqint=rstmcm.rsl.getInt("cq");}
  catch(SQLException se){logger.ee("Failed to getInt() cq from table callmaid.",se); return false;}
  if(cqint > 0) {if(debugFlag)logger.println("cq is already populated.");return false;} 
  try{ituint=rstmcm.rsl.getInt("itu");}
  catch(SQLException se){logger.ee("Failed to getInt() itu from table callmaid.",se); return false;}
  if(ituint > 0) {if(debugFlag)logger.println("itu is already populated.");}
  // get country, state, maidenhead
  try{country=rstmcm.rsl.getString("country"); state=rstmcm.rsl.getString("state"); maidenhead=rstmcm.rsl.getString("maidenhead");}
  catch(SQLException se){logger.ee("Failed to get country, state, or maidenhead from table callmaid.",se); return false;}
  // get zipcode
  zippy=this.getZip(rstmcm); if(zippy < 0) return false; // step 3. 
  callsign=this.getCallsign(rstmcm, zippy); // get callsign
  if(callsign==null)
    {
    logger.e("No callsign in callmaid record.");
    return false;
    }  
  if(country.equals("US"))
    {
    cqitu=this.USStateToCQITU(state,zippy,callsign,maidenhead);  
    if(cqitu==null){logger.e("getUSCQ() for state=" + state + " failed."); return false;}
    if(cqint <= 0)cqint=cqitu[0];    // if cq was not populated then populate it.
    if(ituint <= 0) ituint=cqitu[1];// if itu was not populated then populate it.
    } // end block country = "US"
  else 
    {
    nonUSCallsign++;
    return true;
    }  // this method processes only the united states callsigns because it relies on states.
  // update callmaid table with with CQ and ITU for matching callsign.
  updateSuccess=Common.dbAllTxt.SQLJDBC("update callmaid set CQ=" + cqint + ", ITU=" + ituint + " where CALLSIGN = '" + callsign + "'");
  if(updateSuccess) this.updatedCallmaid++;
  if(debugFlag)
    {
    logger.println("callsign=" + callsign + ", CQ=" + cqint + ", ITU=" + ituint + ", updateSuccess=" + updateSuccess);
    System.out.println("popcqitu() returns true.");
    }
  return true;
  } // end popcqitu()



private int getZip(ResultSetTableModel rstmcm)
  {
  int zippy;
  try{zippy = rstmcm.rsl.getInt("zipcode");}
  catch(SQLException se)
    {
    logger.tee("Failed to get zipcode from table callmaid", se);
    return -1;
    }
  return zippy;
  }


private String getCallsign(ResultSetTableModel rstmcm, int zippy)
  {
  String callsign;
  try{callsign = rstmcm.rsl.getString("callsign");}
  catch(SQLException se)
    {
    logger.tee("Failed to get callsign from table callmaid", se);
    return null;
    }
  if(debugFlag)System.out.println("callsign=" + callsign + ", zippy=" + zippy);
  return callsign;
  }




/**
 * Based on state abbreviation assign CQ and ITU.
 * Some states are in two ITU zones, differentiated by longitude.
 * We get longitude from zipcode.
 * @param state
 * @return null for error.
 */
private int[] USStateToCQITU(String state, int zipcode, String callsign, String maidenhead)
  {
  int cq=-1;
  int itu=-1;
  int[] cqitu = new int[2];
  double[] latlon = new double[2];
  state = state.toUpperCase();  // on rare occasion a state is entered in lower case.
  switch(state)
    {
    case "":cq=0; itu=0; 
            logger.printwarn("callmaid for callsign=" + callsign + " has null state. CQ and ITU cannot be assigned.");
            break;  // null state
    case "AL":cq=4;itu=8; break;  // alabama
    case "AK":cq=1; itu=1; 
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            if(latlon != null)if(latlon[1] > -141) itu=2;
            break;  // alaska
    case "AS":cq=32; itu=62; break;  // american samoa
    case "AZ":cq=3;  
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=6;
            if(latlon != null)if(latlon[1] > -110) itu=7;
            break;  // arizona ITU=6, or 7 if < -110 longitude
    case "AR":cq=4; 
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=7;
            if(latlon != null)if(latlon[1] > -90) itu=8;
            break;  // arkansas ITU=7, or 8 if < -90 longitude
    case "CA":cq=3; itu=6; break;  // california
    case "CO":cq=4; itu=7; break;  // colorado
    case "CT":cq=5; itu=8; break;  // connecticut
    case "DE":cq=5; itu=8; break;  // delaware
    case "DC":cq=5; itu=8; break;  // district columbia
    case "FL":cq=5; itu=8; break;  // florida
    case "GA":cq=5; itu=8; break;  // georgia
    case "GU":cq=27; itu=64; break;  // guam
    case "HI":cq=31; itu=61; break;  // hawaii
    case "ID":cq=3; itu=6; break;  // idaho
    case "IL":cq=4;
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=8;
            if(latlon != null)if(latlon[1] < -90) itu=7;
            break;  // illinois ITU=7, or 8 if < -90 longitude
    case "IN":cq=4; itu=8; break;  // indiana
    case "IA":cq=4; itu=7; break;  // iowa
    case "KS":cq=4; itu=7; break;  // kansas
    case "KY":cq=4; itu=8; break;  // kentucky
    case "LA":cq=4; 
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=7;
            if(latlon != null)if(latlon[1] > -90) itu=8;
            break;  // louisiana ITU=7, or 8 if < -90 longitude
    case "ME":cq=5; itu=8; break;  // maine
    case "MD":cq=5; itu=8; break;  // maryland
    case "MA":cq=5; itu=8; break;  // massachusetts
    case "MI":cq=4; 
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=8;
            if(latlon != null)if(latlon[1] < -90) itu=7;
            break;  // michigan ITU=7, or 8 if < -90 longitude
    case "MN":cq=4; 
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=7;
            if(latlon != null) if(latlon[1] > -90) itu=8;
            break;  // minnesota ITU=7, or 8 if < -90 longitude
    case "MS":cq=4; 
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=7;
            if(latlon != null)if(latlon[1] > -90) itu=8;
            break;  // mississippi ITU=7, or 8 if < -90 longitude
    case "MO":cq=4; 
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=7;
            if(latlon != null)if(latlon[1] > -90) itu=8;
            break;  // missouri ITU=7, or 8 if < -90 longitude
    case "MT":cq=4;
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=6;
            if(latlon != null)if(latlon[1] > -110) itu=7;
            break;  // montana
    case "NE":cq=4; itu=7; break;  // nebraska
    case "NV":cq=3; itu=6; break;  // nevada
    case "NH":cq=5; itu=8; break;  // new hampshire
    case "NJ":cq=5; itu=8; break;  // new jersey
    case "NM":cq=4; itu=7; break;  // new mexico
    case "NY":cq=5; itu=8; break;  // new york
    case "NC":cq=5; itu=8; break;  // north carolina
    case "ND":cq=4; itu=7; break;  // north dakota
    case "OH":cq=4; itu=8; break;  // ohio
    case "OK":cq=4; itu=7; break;  // oklahoma
    case "OR":cq=3; itu=6; break;  // oregon
    case "PA":cq=5; itu=8; break;  // pennsylvania
    case "PR":cq=8; itu=11; break;  // puerto rico
    case "RI":cq=5; itu=8; break;  // rhode island
    case "SC":cq=5; itu=8; break;  // south carolina
    case "SD":cq=4; itu=7; break;  // south dakota
    case "TN":cq=4;
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign);
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=8;
            if(latlon != null)if(latlon[1] < -90) itu=7;
            break;  // tennessee ITU=7, or 8 if < -90 longitude
    case "TX":cq=4; itu=7; break;  // texas
    case "UT":cq=3; 
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign); 
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=6;
            if(latlon != null)if(latlon[1] > -110) itu=7;
            break;  // utah ITU=6, or 7 if < -110 longitude
    case "VT":cq=5; itu=8; break;  // vermont
    case "VI":cq=8; itu=11; break;  // virgin islands
    case "VA":cq=5; itu=8; break;  // virginia
    case "WA":cq=3; itu=6; break;  // washington
    case "WV":cq=5; itu=8; break;  //west virginia
    case "WI":cq=4;
            latlon = this.cmu.queryZipcodegeo(zipcode, callsign);
            if(latlon==null)latlon = this.mhObj.maidenheadToLatLon(maidenhead);
            itu=8;
            if(latlon != null) if(latlon[1] < -90) itu=7;
            break;  // wisconsin ITU=7, or 8 if < -90 longitude
    case "WY":cq=4; itu=7; break;  // wyoming
    case "AP":cq=0; itu=0; break;  // armed forces pacific
    case "AE":cq=0; itu=0; break;  // armed forces europe
    case "MP":cq=27; itu=64; break; // mariana islands
    case "UM":cq=31; itu=61; break;  // johnston island, wake island
    case "AA":cq=0; itu=0; break;  // armed forces america
    default: logger.e("Invalid state abbreviation=" + state); return null;
    }
  if(cq==-1 || itu==-1){logger.e("CQ or ITU unassigned for state abbr=" + state); return null;}
  cqitu[0]=cq; cqitu[1]=itu;
  return cqitu;
  }  // end USStateToCQITU()
    
} // end class PopulateCallmaidCQITU
