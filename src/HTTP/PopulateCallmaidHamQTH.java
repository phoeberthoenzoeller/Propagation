/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package HTTP;
import m.Common;
import v.Log.Logfile;
import m.Propdbutils;
import m.db.ResultSetTableModel;
import m.CallmaidRecord;
import java.sql.Date;
import java.lang.NumberFormatException;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.GregorianCalendar;
import v.ShowMessage;
import c.Time.TimeUtils;
import c.CallmaidUtils;
import HTTP.PopulateCallmaidHamQTH;




/**
 * For all records specified in whereClause:
 * Query HamQTH API, populate HamQTH.hamqthEntity object, populate CallmaidRecord object with hamqthEntity object.
 * The API documentation states that I may make "unlimited" queries, but in order
 * to mitigate impact on the server and network I limit queries on hamqth to 
 * one per second. 
 * a) Populate table callmaid with HamQTH data.  method hamqthToCallmaid() is the public entry point.
 * b) Create a new CallmaidRecord obect with existing callmaid record contents.
 * c) Call HamQTH.hamQTHXMLAPI(callsign) to create a HamQTH.hamqthEntity.
 *    This method performs a get of XML at HamQTH server, parses the XML, and assigns fields to a hamqthEntity object.
 *    The hamqthEntity object is returned to hamqthToCallmaid() for processing.
 * d) If not HamQTH is found then set callmaid.ds[2]="F", update mdate and continue loop.
 * e) Call this.updateCallmaid() to update callmaid record from step (b) with data from HamQTH.hamqthEntity object.
 *    Update CallmaidRecord object with hamqtyEntity object.
 *    if ds[1] < "3", i.e. if maidenhead was not populated by WSJTX or user, then overwrite maidenhead and set ds[1]="2"
 *      unless existing maidenhead is a more precise version of maidenhead.
 *    Always set ds[0]="2", ds[2]="T".
 * f) Pause for 700 msec.
 * @author chas
 */
public class PopulateCallmaidHamQTH 
{
private final Logfile logger;
private final Propdbutils prodb;
public final HamQTH hqth;
public boolean validStatus=false;
private final TimeUtils tu;
public boolean doShow=false;
private final boolean debugFlag;
private final CallmaidUtils cmutils;
private final String sqlDateString;
public int sleeptime = 800;
public int hamqthqueries, hamqthreads;



public PopulateCallmaidHamQTH(Logfile l, boolean df)
  {
  this.logger = l;
  this.debugFlag=df;
  this.prodb = Common.prodb;
  this.hqth = new HamQTH(logger); // sets HamQTH.validStatus=false if unable to get session id.
  this.tu = new TimeUtils(logger);
  this.sqlDateString = this.tu.dateToSQLString(new Date(System.currentTimeMillis()));
  this.cmutils = new CallmaidUtils(logger, this.prodb, Common.debugFlag);
  this.hamqthqueries=0;
  this.hamqthreads=0;
  if(hqth.validStatus==false)
    {
    logger.e("HamQTH is unavailable at this time.  Callsign records will be created, but not enhanced.");
    logger.tprintlnwarn("PopulateCallmaidHamQTH constructor fails owing to failure of HamQTH to obtain session ID.");
    return;
    }
  validStatus=true;
  } // end constructor


/**
 * Called from UIMain.jMenuItemTestHTTPGetActionPerformed().
 * Called from UDPProcessMessageThread state=25.
 * For each record in callmaid matching whereClause,
 * creates a new CallmaidRecord,
 * create a HamQTH.hamqthEntity object from a Get from HamQTH, parse the XML, assign to hamqthEntitry, return the hamqthEntity object.
 * Update callmaid record with data from HamQTH.hamqthEntity object.
 * @param whereClause
 * @return true if at least one callsign in callmaid records returned by whereclause produced a successful read of hamqth api.
 */
public boolean hamqthToCallmaid(String whereClause)  // step a)
  {
  boolean success=false;
  int queryFailures=0;
  this.hamqthqueries++;
  long starttime = System.currentTimeMillis();
  ResultSetTableModel rstm = this.prodb.queryCallmaid("*",whereClause);
  DecimalFormat df = new DecimalFormat("###,###,###");
  ShowMessage sm1=null,sm2=null;
  if(this.doShow)
    {
    sm1 = new ShowMessage("Processing " + df.format(rstm.getRowCount()) + " records from callmaid where " + whereClause + ".",0,400);  
    sm1.showMessage();
    sm2 = new ShowMessage("Processed " + 0 + " of " + df.format(rstm.getRowCount()) + " records",250,500);  
    }
  int x;
  for(x=0; x < rstm.getRowCount(); x++)
    {
    if(x%100 == 0)
      {
      if(this.doShow)
        {
        sm2.showOff(); sm2=null;
        sm2 = new ShowMessage("Processed " + df.format(x) + " of " + df.format(rstm.getRowCount()) + " records",250,500);  
        sm2.showMessage();
        }
      if(debugFlag)logger.println(tu.CurrentHHMMSS() + ", recordCounter=" + x + ". HamQTH query failures=" + queryFailures);
      Common.dbAllTxt.commit();
      if(this.doShow)Common.garbageCleanup();
      } // end block cleanup after every 100th record.
    CallmaidRecord cm = new CallmaidRecord(rstm, x); // b) Create a new CallmaidRecord obect with existing callmaid record contents.
    if(debugFlag)
      {
      logger.println("**************Begin old callmaid Records***********************");
      cm.logEntity(cm);  // log the CallmaidRecord object.
      logger.println("**************End old callmaid Records***********************");
      }
    HamQTH.hamqthEntity hame = hqth.hamQTHXMLAPI(cm.callsign); // c) Get from HamQTH, parse the XML, assign to hamqthEntitry, return the hamqthEntity object.
    if(hame==null)
      {
      this.updateDSArrayNull(cm.callsign, cm.DS);queryFailures++; 
      Common.sleep(this.sleeptime);
      continue;
      }  // d) no HamQTH record found, or parse failed.
    if(debugFlag)
      {
      logger.println("**************Begin HamQTH response ***********************");
      hqth.reporthamqthEntity(hame); // log the hamqthEntity object.
      logger.println("**************End HamQTH response.  Updating callmaid for callsign=" + cm.callsign + "***********************");
      }
    success=true; // we did read hamqth api.
    this.updateCallmaid(cm, hqth.hent); // e) Update callmaid record from step (b) with data from HamQTH.hamqthEntity object.
    this.hamqthreads++;
    //this.updateMdate(cm.callsign);  
    Common.sleep(this.sleeptime); // f) to ensure that the load on HamQTH server is light.
    } // end for loop each callmaid record returned from query for callsign
  if(this.doShow)
    {
    sm1.showOff();
    sm2.showOff(); sm2=null;
    sm2 = new ShowMessage("Completed processing " + df.format(x) + " records",250,500);  
    sm2.showMessage();
    Common.sleep(1000);
    sm2.showOff();
    }
  if(debugFlag)logger.println("Completed processing of " + x + " records in " + (System.currentTimeMillis() - starttime)/1000 + " seconds. HamQTH query failures=" + queryFailures); 
  sm1=null; sm2=null;
  return success;  
  } // end  hamqthToCallmaid()
    


/**
 * If no HamQTH record is found or it could not be parsed then mark callmaid.DS[2]="N".
 * This will be used to avoid duplicate queries that would otherwise be performed every time
 * that the program is run on the same callsigns wherein no data is available.
 * mdate is updated to current date so that we know how long ago a HamQTH query was made.
 * @param callsign 
 */
private void updateDSArrayNull(String callsign, String DSold)
  {
  callsign=callsign.toUpperCase();
  /*
  GregorianCalendar cnow = new GregorianCalendar();
  int year = cnow.get(GregorianCalendar.YEAR);
  int month = cnow.get(GregorianCalendar.MONTH) + 1;
  year = year - 2000;
  String monthyearStr= String.valueOf(month) + String.valueOf(year);
  */
  String DSnew = this.cmutils.dsUpdate(DSold, 'N', 2);
  Common.dbAllTxt.updateJDBC("callmaid", "mdate = " + this.sqlDateString + ", DS = '" + DSnew + "'", "callsign='" + callsign + "'");
  Common.dbAllTxt.commit();
  }


/**
 * If callmaid was updated with update mode "N" then mdate didn't get updated.
 * We are now updating with mode "S" in which case we "should" update mdate.
 */
/*
private void updateMdate(String callsign)
  {
  callsign=callsign.toUpperCase();
  Date mdate = new java.sql.Date(System.currentTimeMillis());
  String strColumn=tu.dateToSQLString(mdate);
  //System.out.println("Date=" + strColumn);
  Common.dbAllTxt.updateJDBC("callmaid","mdate=" + strColumn, "callsign='" + callsign + "'");
  Common.dbAllTxt.commit();
  }
*/


/**
 * Update CallmaidRecord object with hamqtyEntity object.
 * if ds[1] < "3", i.e. if maidenhead was not populated by WSJTX or user, then overwrite maidenhead and set ds[1]="2"
 * unless existing maidenhead is a more precise, i.e. more extended, version of existing maidenhead.
 * Always set ds[0]="2", ds[2]="T".
 * @param cm
 * @param hent
 * @return 
 */
private boolean updateCallmaid(CallmaidRecord cm, HamQTH.hamqthEntity hent)
  {
  int d1old;
  if(cm.DS != null)
    {
    d1old = this.cmutils.dsCharToInt(this.cmutils.dsQuery(cm.DS, 1));
    }
  else {cm.DS="11F"; d1old=1;}
  String dsnew = cmutils.dsUpdate(cm.DS, '2', 0);
  dsnew = cmutils.dsUpdate(dsnew, 'T', 2);
  if(d1old < 3) dsnew=cmutils.dsUpdate(dsnew, '2', 1); 
  cm.DS=dsnew;
  if(hent.adr_name != null)
    {
    String[] parseName=hent.adr_name.split(" ");
    if(parseName.length==1)cm.firstname=parseName[0];
    else if(parseName.length==2)
      {
      cm.firstname=parseName[0]; 
      cm.lastname=parseName[1];
      }
    else if(parseName.length==3)
      {
      cm.firstname=parseName[0]; 
      if(parseName[1] != null && parseName[1].length() > 0) cm.middleinitial=parseName[1].substring(0,1);
      cm.lastname=parseName[2]; 
      }
    else 
      {
      int namelength=hent.adr_name.length();
      if(namelength > 40) namelength = 40;
      cm.firstname = hent.adr_name.substring(0,namelength);
      }
    }
  if(hent.adr_name == null && hent.nickname != null)  // if adr_name is null then try nickname
    {
    String[] parseName=hent.nickname.split(" ");
    if(parseName.length==1)cm.firstname=parseName[0];
    else if(parseName.length==2)
      {
      cm.firstname=parseName[0];  
      cm.lastname=parseName[1];
      }
    else if(parseName.length==3)
      {
      cm.firstname=parseName[0];
      if(parseName[1] != null && parseName[1].length() > 0) cm.middleinitial=parseName[1].substring(0,1);
      cm.lastname=parseName[2]; 
      }
    else 
      {
      int namelength=hent.nickname.length();
      if(namelength > 40) namelength = 40;
      cm.firstname = hent.nickname.substring(0,namelength); 
      }
    } // end adr_name == null && nickname != null    
  // truncate as necessary firstname and lastname to fit in database columns.
  if(cm.firstname != null && cm.firstname.length() > 40) cm.firstname = cm.firstname.substring(0,40);    
  if(cm.lastname != null && cm.lastname.length() > 20) cm.lastname = cm.lastname.substring(0,20);
  cm.callsign=hent.callsign.toUpperCase();
  if(d1old < 3) cm.maidenhead = this.evaluateMaiden(cm.maidenhead,hent.grid);
  //this.firstname = firstname;
  //this.middleinitial = middleinitial;
  //this.lastname = lastname;
  if(hent.adr_street1 != null && hent.adr_street1.length() > 60)hent.adr_street1=hent.adr_street1.substring(0,60);
  cm.street = hent.adr_street1;
  if(hent.qth != null && hent.qth.length() > 40)hent.qth=hent.qth.substring(0,40);
  cm.city = hent.qth;
  if(hent.us_state != null && hent.us_state.length() > 2) hent.us_state=hent.us_state.substring(0,2);
  cm.state = hent.us_state;
  if(hent.adr_zip!=null && !hent.adr_zip.equals(""))
    {
    try{cm.zipcode = Integer.valueOf(hent.adr_zip);}
    catch(NumberFormatException nfe)
      {
      logger.printlnwarn("Failed to convert zipcode " + hent.adr_zip + " to integer for callsign=" + hent.callsign); 
      cm.zipcode=0;
      }
    }
  cm.countrycode=this.prodb.abbrFromName(hent.adr_country);
  cm.ITU =hent.itu;
  cm.CQ = hent.cq;
  cm.worked = cm.worked;
  cm.mdate = new java.sql.Date(System.currentTimeMillis()); 
  if(cm.crdate == null) cm.crdate = cm.mdate;
  prodb.insertUpdateCallmaidRecord(cm, "S"); // update existing callmaid record for every column wherein cm(from HamQTH) column is not null.
  return true;
  } // end updateCallmaid()



/**
 * If callmaid.maidenhead is not populated then return hent.grid.
 * else if maidenhead <=3 characters and grid >=4 characters then return grid.
 * else if the first four characters of callmaid.maidenhead and hent.grid are not the same then return callmaid.maidenhead.
 * else if the first four characters of each match, but hent.grid has more characters then return hent.grid.
 * else return maidenhead.
 * 
 */
private String evaluateMaiden(String maidenhead, String grid)
  {
  if(maidenhead != null && maidenhead.equals("")) maidenhead=null; 
  if(grid != null && grid.equals("")) grid=null;  // to avoid having to check for both conditions null and empty.
  if(maidenhead==null && grid==null) return null;//If callmaid.maidenhead and hent.grid are both null then return null.
  if(grid!=null && grid.length() > 8) grid = grid.substring(0,8);  // limit hent.grid to 8 characters.
  if(maidenhead==null && grid!=null) return grid;  // if callmaid.maidenhead is null and hent.grid is not null then return grid.
  if(maidenhead != null && grid==null) return maidenhead; // if callmaid.maidenhead is not null and hent.grid is null then return callmaid.maidenhead.
  maidenhead=maidenhead.toUpperCase(); // convert callmaid.maidenhead and hent.grid to upper case for comparison.
  grid=grid.toUpperCase();
  if(maidenhead.length() < 4  && grid.length() > 3)  return grid; //if maidenhead <=3 characters and grid >=4 characters then return grid.
  int existinglength = maidenhead.length();
  int hentlength = grid.length();
  int minlength;
  if(existinglength < hentlength) minlength = existinglength; else minlength = hentlength;
  if(!maidenhead.substring(0,minlength).equals(grid.substring(0,minlength)) && hentlength > existinglength) return grid;
  else return maidenhead;
  }




} // end class PopulateCall maidHamQTH
