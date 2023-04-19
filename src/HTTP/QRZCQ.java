/*
 * Query QRZCQ.com or callsign data.
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package HTTP;
import c.CallmaidUtils;
import m.CallmaidRecord;
import m.Common;
import org.apache.commons.lang3.StringUtils;
import v.Log.Logfile;
import m.Propdbutils;
import m.db.ResultSetTableModel;



/**
 *
 * @author chas
 */
public class QRZCQ 
{
private final Logfile logger;    
public String rrr;  // Raw XML response from QRZCQ.com.
private final HTTPGet httpget;
private int falurecnt;
private int getCnt;
private QRZCQEntity entity;
private final boolean debugFlag;
private int rrrPtr;
private final Propdbutils prodb;
private final CallmaidUtils cmutils;
public int queriestotal;
public int queryresponses;



public QRZCQ(Logfile l)
  {
  this(l,false);  
  }

public QRZCQ(Logfile l, boolean df)
  {
  this.logger = l;    
  this.httpget=new HTTPGet(l);
  this.falurecnt=0;
  this.getCnt=0;
  this.debugFlag = df;
  this.prodb = Common.prodb;
  this.cmutils = new CallmaidUtils(logger, Common.prodb, debugFlag);
  }
    

/**
 * 1) Obtain the callmaid record for given callsign.
 * 2) Get data from qrzcq.com
 * 3) Populate new CallmaidRecord with contents of resultset.
 * 4) Update callmaid record with data from qrzcq.com.
 * @param callsign
 * @return 
 */
public boolean QRZCQToCallmaid(String callsign)
  {
  if(debugFlag)logger.println("QRZCQ for callsign=" + callsign);
  this.queriestotal++;
  ResultSetTableModel rstml = this.prodb.queryCallmaid("*","CALLSIGN='" + callsign + "'");
  if(rstml==null){logger.tprintlnwarn("Query of callmaid for callsign = " + callsign + " returned error"); return false;}
  if(rstml.getRowCount()==0){logger.tprintlnwarn("Query of callmaid for callsign = " + callsign + " returned zero rows."); return false;}
  this.getQRZCQ(callsign);
  if(this.entity==null){logger.tprintlnwarn("getQRZCQ() return null QRZCQEntity."); return false;}
  this.queryresponses++;
  CallmaidRecord cmr = new CallmaidRecord(rstml,0);
  return updateCallmaid(cmr, this.entity);
  } // end populateCallmaidQRZCQ


/**
 * Query qrzcq.com for callsign.  Populate QRZCQEntity with results.
 * @param callsign
 * @return null for error.
 */
public QRZCQ.QRZCQEntity getQRZCQ(String callsign)
  {
  this.entity = new QRZCQEntity();
  callsign = callsign.toUpperCase();
  entity.callsign=callsign;
  String URL="https://www.qrzcq.com/call/" + callsign;
  this.rrr = httpget.getRequest(URL);
  if(this.rrr==null)
    {
    logger.e("Failed to get a response to HTTP request=" + URL);
    falurecnt++;
    if(falurecnt > 10) Common.qthrealtime=false;
    return null;
    }
  this.getCnt++;
  if(this.getCnt > 100){this.getCnt=0; if(this.falurecnt > 0) falurecnt--;}
  if(rrr.contains("ERROR or CALL_NOT_FOUND"))
    {
    logger.println("QRZCQ query for callsign=" + callsign + " returned CAll_NOT_FOUND");
    return null;
    }
  String fullname=getByKey("style=\"text-shadow: 0px 1px 0px #f1f1f1, 0px 1px 3px #999; \"",0); // name field
  this.parseName(fullname); if(debugFlag)logger.println("parseName(" + fullname + ")=" + entity.firstname + " " + entity.middleinitial + " " + entity.lastname);
  String cityAndZip = getByKey("/b><br /><br /",this.rrrPtr);
  this.parseCityZip(cityAndZip);
  String countryState = getByKey("br /",this.rrrPtr);
  this.parseCountryState(countryState);
  this.entity.latitude = getByKey("Latitude:</b></td><td align=\"left\"",this.rrrPtr);
  this.entity.longitude = getByKey("Longitude:</b></td><td align=\"left\"", this.rrrPtr);
  this.entity.maidenhead = getByKey("Locator:</b></td><td align=\"left\"", this.rrrPtr);
  this.entity.dxccZone = Integer.valueOf(getByKey("DXCC Zone:</b></td><td align=\"left\"",this.rrrPtr).trim());
  this.entity.ituZone = Integer.valueOf(getByKey("ITU Zone:</b></td><td align=\"left\"", this.rrrPtr).trim());
  this.entity.cqZone = Integer.valueOf(getByKey("CQ Zone:</b></td><td align=\"left\"", this.rrrPtr).trim());
  if(this.debugFlag==true)this.reportQrzcqEntity(entity);
  return this.entity;
  } // end method getQRZCQ()


/**
 * Find a field by a prefix key wherein the field is then enclosed in > and <.
 * Called exclusively by getQRZCQ().
 * @param key
 * @return 
 */
private String getByKey(String key, int startIndex)
  {
  int keyIndex = this.rrr.indexOf(key,startIndex) + key.length();
  int sfIndex, efIndex;
  if(debugFlag)logger.println("keyIndex=" + keyIndex);
  sfIndex = this.rrr.indexOf(">", keyIndex) + 1;
  efIndex = this.rrr.indexOf("<", sfIndex);
  String field = this.rrr.substring(sfIndex, efIndex);
  this.rrrPtr = efIndex;
  if(debugFlag)logger.println("getByKey(" + key + "," + startIndex + ")=" + field);
  return field;
  }

/**
 * Populates this.entity name fields.
 * Called exclusively by getQRZCQ()
 * @param fullname 
 */
private void parseName(String fullname)
  {
  String[] namefields = fullname.split(" ");
  if(namefields.length==1){entity.firstname=namefields[0]; return;}
  if(namefields.length==2){entity.firstname=namefields[0]; entity.lastname=namefields[1];return;}
  if(namefields.length==3){entity.firstname=namefields[0];entity.middleinitial=namefields[1].substring(0, 1); entity.lastname=namefields[2]; return;}
  }


/**
 * Called exclusively by this.getQRZCQ()
 * @param cityzip 
 */
private void parseCityZip(String cityzip)
  {
  if(cityzip==null || cityzip.length() < 1)
    {
    entity.zipcode=0;
    entity.city="";
    return;
    }
  char lastchar = cityzip.charAt(cityzip.length()-1);
  cityzip = cityzip.trim();
  int lastspace = cityzip.lastIndexOf(" ");
  if(lastchar >= '0' && lastchar <= '9') // last field is numeric and assumed to be zipcode
    {
    this.entity.zipcode = Integer.valueOf(cityzip.substring(lastspace + 1));
    this.entity.city = cityzip.substring(0,lastspace);
    return;
    }
  this.entity.city = cityzip;
  }


/**
 * Called exclusively by this.getQRZCQ()
 * @param cs 
 */
private void parseCountryState(String cs)
  {
  if(cs==null || cs.equals("")) return;
  String[] csArray = cs.split(",");
  if(csArray.length < 2){this.entity.country=cs; return;}
  this.entity.country = this.prodb.abbrFromName(csArray[0].trim());
  this.entity.stateAbbr = csArray[1].trim();
  }






/**
 * Update CallmaidRecord object with hamqtyEntity object.
 * if ds[1] < "3", i.e. if maidenhead was not populated by WSJTX or user, then overwrite maidenhead and set ds[1]="2"
 * unless existing maidenhead is a more precise, i.e. more extended, version of existing maidenhead.
 * Always set ds[0]="2", ds[2]="T".
 * @param cm
 * @param hent
 * @return 
 */
private boolean updateCallmaid(CallmaidRecord cm, QRZCQ.QRZCQEntity hent)
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
  // Populate CallmaidRecord with QRZCQEntity beyond fields populated by query of callmaid.
  // callsign is populated.
  if(hent.maidenhead != null)
    {
    cm.maidenhead = hent.maidenhead.trim();
    }
  if(hent.firstname != null)
    {
    cm.firstname = hent.firstname.trim();
    if(cm.firstname.length() > 40) cm.firstname=cm.firstname.substring(0, 40);
    }
  if(hent.middleinitial != null && hent.middleinitial.length() > 0)
    {
    cm.middleinitial = hent.middleinitial.trim();
    if(cm.middleinitial.length() > 1) cm.middleinitial=cm.middleinitial.substring(0, 1);
    }
  if(hent.lastname != null)
    {
    cm.lastname = hent.lastname.trim();
    if(cm.lastname.length() > 20) cm.lastname=cm.lastname.substring(0, 20);
    }
  if(hent.city != null)
    {
    cm.city = hent.city.trim();
    if(cm.city.length() > 40) cm.city=cm.city.substring(0, 40);
    }
  cm.zipcode = hent.zipcode;
  if(hent.country != null)
    {
    cm.countrycode = hent.country;
    if(cm.countrycode.length() > 2) cm.countrycode=cm.countrycode.substring(0, 2);
    }
  if(hent.stateAbbr != null)
    {
    cm.state = hent.stateAbbr;
    if(cm.state.length() > 2) cm.state=cm.state.substring(0, 2);
    }
  // latitude, longitude, dxccZone do not currently exist in callmaid.
  cm.ITU = hent.ituZone;
  cm.CQ = hent.cqZone;
  if(d1old < 3) cm.maidenhead = hent.maidenhead;
  cm.worked = cm.worked;
  cm.mdate = new java.sql.Date(System.currentTimeMillis()); 
  if(cm.crdate == null) cm.crdate = cm.mdate;
  prodb.insertUpdateCallmaidRecord(cm, "S"); // update existing callmaid record for every column wherein cm(from HamQTH) column is not null.
  return true;
  } // end updateCallmaid()






public void reportQrzcqEntity(QRZCQ.QRZCQEntity hent)
  {
  if(hent == null) return;
  logger.println("**************************************************************************************");
  logger.println("callsign=" + hent.callsign);    
  logger.println("first name=" + hent.firstname);
  logger.println("middle initial=" + hent.middleinitial);
  logger.println("last name=" + hent.lastname);
  logger.println("city=" + hent.city);
  logger.println("zipcode=" + hent.zipcode);
  logger.println("country=" + hent.country);
  logger.println("state=" + hent.stateAbbr);
  logger.println("latitude=" + hent.latitude);
  logger.println("longitude=" + hent.longitude);
  logger.println("maidenhead=" + hent.maidenhead);
  logger.println("DXCC Zone=" + hent.dxccZone);
  logger.println("ITU Zone=" + hent.ituZone);
  logger.println("CQ Zone=" + hent.cqZone);
  } // end reportQrzcEntity    
    
 


    

public class QRZCQEntity
{
public String callsign;
public String maidenhead;
public String firstname;
public String middleinitial;
public String lastname;
public String city;
public int zipcode;
public String country;
public String stateAbbr;
public String latitude;
public String longitude;
public int dxccZone;
public int ituZone;
public int cqZone;
} // end class QRZCQEntity    
    
} // end class qrzcq.com
