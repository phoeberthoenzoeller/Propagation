/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package HTTP;


import m.Common;
import v.Log.Logfile;
import org.apache.commons.lang3.StringUtils;

/**
 * Utililize the HamQTH API to obtain ham account records.
 * @author chas
 */
public class HamQTH 
{
private final Logfile logger;
private final HTTPGet httpget;
private String sessionID;  // for access to HamQTH
private long sessionIDBornTime;
private long oneHour=(60 * 60 * 1000) - 1000;  // one second is subtracted to compensate for latency.
private final String programName="Propagation";
public hamqthEntity hent;
public boolean validStatus=false;
public String rrr;  // Raw XML response from hamqth API.

public HamQTH(Logfile l)
  {
  this.logger = l;
  this.httpget=new HTTPGet(l);
  this.hent = new hamqthEntity();
  if(this.getSessionIDHamQTH()==null)
    {
    logger.tprintlnwarn("ERROR: Constructor of HamQTH failed to get session id.  No HamQTH queries can be performed.");
    Common.qthrealtime=false;
    return;
    }
  this.validStatus=true;
  }        



public String getSessionIDHamQTH()
  {
  String username = "KM4SDB";
  String password = "P@ss4qth";
  String requestSessionID="https://www.hamqth.com/xml.php?u=KM4SDB&p=P@ss4qth";
  String sid; // session id
  /* Expected response
   * <HamQTH version="2.8">
   * <session>
   * <session_id>981cc474890407d25b4103f85571d814a9698425</session_id>
   * </session>
   * </HamQTH>
   */
  String requestResponse = httpget.getRequest(requestSessionID);
  if(requestResponse==null){logger.tprintlnwarn("ERROR: Failed to get a response from requestsessionID()"); return null;}
  String errorStr = StringUtils.substringBetween(requestResponse, "<error>", "</error");
  if(errorStr != null){logger.tprintlnwarn("ERROR: Failed to get session id from HamQTH. Error=" + errorStr); return null;}
  sid=null;
  sid = StringUtils.substringBetween(requestResponse, "<session_id>", "</session_id");
  if(sid == null){logger.tprintlnwarn("ERROR: Failed to get session id from HamQTH. No session_id tag in response to request."); return null;}
  /*
  for (String s : stringArray) 
    {
    System.out.println("stringArray[]: " + s);    
    }
  */
  logger.tprintln("HamQTH session ID=" + sid);
  this.sessionID=sid;
  this.sessionIDBornTime=System.currentTimeMillis();
  return sid;
  } // end getSessionIDHamQTH()


/**
 * 
 * @param callsign
 * @return null for error.
 */
public HamQTH.hamqthEntity hamQTHXMLAPI(String callsign)
  {
  if((System.currentTimeMillis() - this.sessionIDBornTime) > this.oneHour) 
    {
    if(this.getSessionIDHamQTH()==null)
      {
      Common.qthrealtime=false; 
      logger.e("Failed to get session ID from HamQTH. No further queries of HamQTH will be performed.");
      return null;
      }
    }
  String URL="https://www.hamqth.com/xml.php?id=" + this.sessionID + "&callsign=" + callsign + "&prg=" + this.programName;
  this.rrr = httpget.getRequest(URL);
  if(this.rrr==null){logger.e("Failed to get a response to HTTP request=" + URL); return null;}
  String[] parseArray = StringUtils.substringsBetween(rrr, "<error>", "</error");
  String lookForError=this.getFirstInstance(rrr,"error");
  if(lookForError!=null){logger.println("WARNING: hamQTHXMLAPI returned " + parseArray[0] + " for callsign=" + callsign); return null;}
  this.hent = new HamQTH.hamqthEntity();
  hent.callsign = this.getFirstInstance(rrr, "callsign");
  hent.nickname = this.getFirstInstance(rrr,"nick");
  hent.qth = this.getFirstInstance(rrr, "qth");
  hent.country = this.getFirstInstance(rrr, "country");
  String adifStr = this.getFirstInstance(rrr,"adif");
  try{hent.adif = Integer.parseInt(adifStr);}  // DXCC Entity Code 
  catch(NumberFormatException nfe)
    {
    logger.printlnwarn("Failed to convert adif=" + adifStr + " to Integer.");
    hent.adif=0;
    }
  String ituStr = this.getFirstInstance(rrr,"itu");
  try{hent.itu = Integer.parseInt(ituStr);}
   catch(NumberFormatException nfe)
    {
    logger.printlnwarn("Failed to convert itu=" + ituStr + " to Integer.");
    hent.itu=0;
    } 
  hent.cq = Integer.parseInt(this.getFirstInstance(rrr, "cq"));
  hent.grid = this.getFirstInstance(rrr, "grid");
  hent.adr_name = this.getFirstInstance(rrr,"adr_name");
  hent.adr_street1 = this.getFirstInstance(rrr, "adr_street1");
  hent.adr_city = this.getFirstInstance(rrr, "adr_city");
  hent.adr_zip = this.getFirstInstance(rrr, "adr_zip");
  //if(adr_zip==null) hent.adr_zip=0; else hent.adr_zip = Integer.parseInt(adr_zip);
  hent.adr_country = this.getFirstInstance(rrr, "country");
  String adr_adif = this.getFirstInstance(rrr, "adr_adif");
  if(adr_adif == null) hent.adr_adif=0; else 
    {
    try{hent.adr_adif = Integer.parseInt(adr_adif);}
    catch(NumberFormatException ne){logger.printlnwarn("Failed to parse adr_adif=" + adr_adif); hent.adr_adif=0;}   
    }
  hent.district = this.getFirstInstance(rrr,"district");
  hent.email = this.getFirstInstance(rrr, "email");
  String birth_year = this.getFirstInstance(rrr, "birth_year");
  if(birth_year == null) hent.birth_year=0; else hent.birth_year = Integer.parseInt(birth_year);
  String lic_year = this.getFirstInstance(rrr, "lic_year");
  if(lic_year == null) hent.lic_year=0; else hent.lic_year = Integer.parseInt(lic_year);
  hent.latitude = this.getFirstInstance(rrr, "latitude");
  hent.longitude = this.getFirstInstance(rrr, "longitude");
  hent.us_state = this.getFirstInstance(rrr,"us_state");
  hent.us_county = this.getFirstInstance(rrr, "us_county");
  return hent;
  } // end hamQTYXMLAPI()


private String getFirstInstance(String rr, String tag)
  {
  String lead="<" + tag + ">";
  String lag="</" + tag + ">";
  String[] parseArray = StringUtils.substringsBetween(rr,lead ,lag);
  if(parseArray==null)return null;
  return parseArray[0];
  }





/**
 * This is where I began to parse the HTML from a conventional query.
 * @param getURLResponse
 * @return 
 */
public String[] parseHamQTH(String getURLResponse)
  {
  // Name:</td><td>Charlie</td>    
  // QTH:</td><td>Fort Payne, Alabama</td> 
  // Country:</td><td>United States</td>
  // Grid:</td><td id="grid"><a href="https://aprs.fi/#!addr=EM74">EM74</a></td> 
  // ITU:&nbsp;</td><td>8</td>  
  // CQ:</td><td>4</td>
  // State:</td><td>AL</td>
  // County:</td><td>DeKalb</td>
  //     
  String[] tokens=null;
  return tokens;  
  } // end parseHamQTH()


public void reporthamqthEntity(HamQTH.hamqthEntity hent)
  {
  if(hent == null) return;
  logger.println("**************************************************************************************");
  logger.println(this.rrr);
  logger.println("callsign=" + hent.callsign);    
  logger.println("nick=" + hent.nickname);
  logger.println("qth=" + hent.qth);  
  logger.println("country=" + hent.country);  
  logger.println("adif=" + hent.adif);  // DXCC Entity Code 
  logger.println("itu=" + hent.itu);  
  logger.println("cq=" + hent.cq);  
  logger.println("grid=" + hent.grid);  
  logger.println("adr_name=" + hent.adr_name);  // first name <space> last name, or first <space> mi <space> last.
  logger.println("adr_street1=" + hent.adr_street1);  
  logger.println("adr_city=" + hent.adr_city);  
  logger.println("adr_zip=" + hent.adr_zip);  
  logger.println("adr_country=" + hent.adr_country);  
  logger.println("adr_adif=" + hent.adr_adif);  // DXCC Entity Code 
  logger.println("district=" + hent.district);  
  logger.println("email=" + hent.email);   
  logger.println("birth_year=" + hent.birth_year);   
  logger.println("lic_year=" + hent.lic_year);   
  logger.println("latitude=" + hent.latitude);
  logger.println("longitude=" + hent.longitude);
  logger.println("us_state=" + hent.us_state);
  logger.println("us_county=" + hent.us_county);
  }    
    


public class hamqthEntity
{
public String callsign;  // callmaid.callsign
public String nickname;
public String qth;      // callmaid.city.  May be preceded by district identifier e.g. 36088 Huenfeld where 36088 is the postal code.
                                          // may also be suffixed by provincial code e.g. Sanremo (IM) for city Sanremo in Imperial Province, Italy.
public String country;  // full text of country
public int adif;        // adif dxcc code of country or other specified area.
public int itu;       // callmaid.itu
public int cq;        // callmaid.cq
public String grid;  // callmaid.maidenhead as 4 or 6 characters.  Max 8 characters.
public String adr_name;  // first name, middle initial, last name. as <firstname><space><lastname> or <firstname><space><middleinitial|middlename><space><lastname>
public String adr_street1; // callmaid.street
public String adr_city;   // callmaid.city.  Usually the same as qth field, but sometimes a subcategory of the city.
public String adr_zip;    // callmaid.zipcode
public String adr_country; // callmaid.country (same as country field)
public int adr_adif;       // adif dxcc code of country or other specified area.
public String district;
public String email;
public int birth_year;
public int lic_year;
public String latitude;
public String longitude;
public String us_state;   // callmaid.state. state abbreviation
public String us_county;  
} // end class hamqthEntity
// not populated: callmaid.state, callmaid.mdate, callmaid.worked.


public boolean test()
  {
  hamqthEntity hent = this.hent;
  long starttime=System.currentTimeMillis();
  hent = this.hamQTHXMLAPI("ok2cqr");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("AA0AM");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("K2RHK");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("N0JZJ");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("W1FBV");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("KY4JLS");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("VA3SPN");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("EA1LOK");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("DJ0WJ");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("IZ1ANK");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("XE1YO");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("S56KFG");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("DL4MY");
  this.reporthamqthEntity(hent);
  hent = this.hamQTHXMLAPI("SP9APJ");
  this.reporthamqthEntity(hent);
  System.out.println("14 queries. Elapsed time=" + (System.currentTimeMillis() - starttime)/1000 + " seconds.");  
  return true;
  } // end test()

    
    
} // end clalss HamQTH
