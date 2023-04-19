/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package m;
import v.Log.Logfile;
import m.Common;
import m.db.ResultSetTableModel;
import m.Propdbutils;
import java.sql.SQLException;
import java.text.DecimalFormat;
import v.ShowMessage;
import c.CallmaidUtils;
import c.Time.TimeUtils;




/**
 * The purpose of this class is to populate callmaid.maidenhead given callmaid.zipcode which was provided from FCC database via UIMain.jMenuItemPopCallmaidActionPerformed.
 * All maidenhead records are populated. While this may produce errors from erroneous zipcodes, we update because man US licensees move addresses offten.
 * Table zipcodegeo provides link from zipcode to lat/lon.
 * The original callmaid table contents were loaded from EN.dat by class ReadEntityFile.
 * 1) Get all callmaid records with null maidenhead.  i.e. select * from callmaid where where maidenhead = ''
 * 2) If callmaid.maidenhead is not null then return to 1)
 * 3) Read callmaid.zipcode.
 * 4) Query zipcodegeo where zipcodegeo.zipcode = callmaid.zipcode. Get latitude and longitude
 * 5) If zipcodegeo not found then return to 1)
 * 6) Convert zipcodegeo.latitude/longitude to maidenhead using Maidenhead.latLonToMaiden()
 * 7) Update callmaid.maidenhead with result of latLonToMaiden()
 * 8) Set DS[1] to "1", update mdate.
 * Invoked from UIMain.jMenuItemPopCallmaidActionPerformed().
 * @author chas
 */
public class PopulateCallmaidMaidenhead 
{
private final Logfile logger;
private final boolean debugFlag;
public int errors;
public int failedToFindZipgeo;
public int failedLatLon;
public int updatedMaidenhead;
private final CallmaidUtils cmu;
private final Propdbutils putils;
private final TimeUtils tutils;


public PopulateCallmaidMaidenhead()
  {
  this.logger=Common.logger;      
  this.debugFlag=Common.debugFlag;
  putils = Common.prodb;
  this.cmu=new CallmaidUtils(logger,putils,debugFlag);
  this.tutils = new TimeUtils(logger);
  }

public boolean popCallmaid()
  {
  errors=0;
  int counter=0;
  // 1) Get all maidenhead records with null maidenhead.  i.e. select * from callmaid where where maidenhead = ''
  ResultSetTableModel rstmcm=  putils.queryCallmaid("callsign,maidenhead,zipcode,DS", "country='US' and ds like '_0_'"); 
  System.out.println("callmaid where country = \"US\" and ds like _0_" + rstmcm.getRowCount() + " rows.");
  DecimalFormat df = new DecimalFormat("###,###,###");
  ShowMessage sm = new ShowMessage("Processing " + df.format(rstmcm.getRowCount()) + " records from callmaid where ds like '_0_'dsdssdsdsddss and country=US.",250,500);  
  sm.showMessage();
  try{rstmcm.rsl.first();} // step 1
  catch(SQLException se)
    {
    logger.tee("Failed to go to first record in table callmaid", se);
    return false;
    }
  if(!this.popcallmaid2(rstmcm, putils)) return false;
  try
    {
    while(rstmcm.rsl.next()) // for each callmaid where maidenhead = ''
      {
      if(!this.popcallmaid2(rstmcm, putils)) return false;
      counter++;
      //if(counter > 199) return true;   // flagme debug
      }  // end while
    } // end try
  catch(SQLException se)
    {
    logger.tee("Failed to go to next record in table callmaid", se);
    return false;
    }      
  Common.dbAllTxt.commit();
  sm.showOff();
  System.out.println("Total records in=" + rstmcm.getRowCount() + ", total errors=" + this.errors + ", failedToFindZipgeo=" + this.failedToFindZipgeo + ", failedLatLon=" + this.failedLatLon + ", updatedMaidenhead=" + this.updatedMaidenhead);
  return true;    
  } // end method popCallmaid()


private boolean popcallmaid2(ResultSetTableModel rstmcm, Propdbutils putils)
  {
  int rowsupdated;
  ResultSetTableModel rstmzg;
  Maidenhead mh = new Maidenhead(logger);
  String newMaid, callsign, DS;
  String oldMaid;  // preexisting maidenhead
  String goodMaid=null;  // the maidenhead that we will write.
  int zippy;
  double latlon[] = new double[2];
  /*
  try{maidenhead=rstmcm.rsl.getString("maidenhead");}
  catch(SQLException se){logger.ee("Failed to getString() maidenhead from table callmaid.",se); return false;}
  */
  try
    {
    DS=rstmcm.rsl.getString("DS");
    oldMaid = rstmcm.rsl.getString("MAIDENHEAD");
    }
  catch(SQLException se){logger.ee("Failed to getString() DS from table callmaid.", se); return false;}
  // if(maidenhead.length() > 0) {if(debugFlag)logger.println("maidenhead is already populated.");return false;} // step 2
  zippy=this.getZip(rstmcm); if(zippy < 0) return false; // step 3
  callsign=getCallsign(rstmcm, zippy); if(callsign==null)return false;
  latlon=this.cmu.queryZipcodegeo(zippy,callsign); if(latlon==null){this.failedToFindZipgeo++;errors++; return true;} // step 4,5
  newMaid = mh.latLonToMaiden(latlon[0], latlon[1]);  // step 6
  if(debugFlag)System.out.println("maidenhead from latlon=" + newMaid);
  if(newMaid == null)
    {
    logger.e("Failed to convert latitude=" + latlon[0] + ", longitude=" + latlon[1] + " to maidenhead.");
    this.failedLatLon++;
    return true;
    }
  // If existing callmaid is not null then preserve it.
  if(oldMaid != null) goodMaid = oldMaid;
  // If existing maidenhead is a longer(more precise) version of latlon maidenhead then preserve the existing.
    oldMaid=oldMaid.toUpperCase(); // convert callmaid.maidenhead and hent.grid to upper case for comparison.
    newMaid=newMaid.toUpperCase();
    if(oldMaid.length() < 4  && newMaid.length() > 3) goodMaid = newMaid; //if maidenhead <=3 characters and grid >=4 characters then return grid.
    int oldlength = oldMaid.length();
    int newlength = newMaid.length();
    int minlength;
    if(oldlength < newlength) minlength = oldlength; else minlength = newlength;
    if(!oldMaid.substring(0,minlength).equals(newMaid.substring(0,minlength)))
      {
      if(newlength > oldlength) goodMaid = newMaid;  
      else goodMaid = oldMaid;
      }
    if(goodMaid == null) goodMaid = newMaid; 
  // update callmaid table with maidenhead with matching callsign. Update DS[1], mdate  step 7, 8.
  DS=this.cmu.dsUpdate(DS,'1', 1);
  java.sql.Date mdate = new java.sql.Date(System.currentTimeMillis());
  String strColumn=this.tutils.dateToSQLString(mdate);
  rowsupdated=Common.dbAllTxt.updateJDBC("callmaid", "maidenhead = '" + goodMaid + "', mdate = " + strColumn + ", DS = '" + DS + "'", "callsign = '" + callsign + "'");
  if(rowsupdated > 0) this.updatedMaidenhead++;
  if(debugFlag)System.out.println("popcallmaid2() returns true.");
  return true;
  } // end popcallmaid2()



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




} // end class PopulateCallmaidMaidenhead
