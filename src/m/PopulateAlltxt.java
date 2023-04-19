/*
 * This class processes a message from WSJTX.
 * Instantiated by UDPWSJTX, UDPProcessMessageThread, ReadWSJTXFile.
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package m;
import c.AlltxtUtils;
import v.Log.Logfile;
import m.Propdbutils;
import m.db.ResultSetTableModel;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.time.Instant;
import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.TemporalUnit;
import java.sql.SQLException;
import c.CallmaidUtils;




/**
 * method createMessageRecord creates a MessageRecord object from fields.
 * method processMessageRecord accepts a MessageRecord, processes it and stores it in table Alltxt.  This method is used by UDPWSJTX.
 * NOTE the processMessageRecord also creates a CALLMAID record for the source callsign in the message if it does not already exist therein.
 * method writeToDatabase writes the MessageRecord to table ALLTXT.
 * @author chas
 */
public class PopulateAlltxt 
{
private final Logfile logger;
int sourceMaidenheadTotal;  // total records with successful decode but no source maidenhead, i.e. no known maidenhead of transmitter.
private final Propdbutils prodb;
private final boolean debugFlag;
int updateCreateCallmaiden; // number of calls to Propdbutils.insertUpdateCallmaidRecord()
private double latHome, lonHome;  // for calculation of bearing and distance to contact.
private final Maidenhead mhObj;
private final AlltxtUtils alltxtUtilObj;
private int databaseWrites, databaseWriteFailures;
int decodeSuccessesTotal;  // successful decoding in MessageRecord.decodeMessage()
int decodeFailuresTotal;   // failed decoding in MessageRecord.decodeMessage()
int nonFT84Total;  // messages that are neither FT8 nor FT4
public MessageRecord atrec;
private int dataSource;  // "3" if called from ReadWSJTX(WSJTX message from ALL.TXT).
private final CallmaidUtils cmutils;
public int callsignnew, callsignupdated;
public boolean callcreateflag;
public boolean callupdateflag;



public PopulateAlltxt(Logfile l, Propdbutils pr, boolean df)
  {
  this.logger = l;    
  this.prodb = pr;
  this.debugFlag = df;
  this.mhObj = new Maidenhead(this.logger);
  this.alltxtUtilObj = new AlltxtUtils(this.logger,this.mhObj,this.prodb);
  this.cmutils = new CallmaidUtils(logger,Common.prodb,Common.debugFlag);
  double[] latLon = this.mhObj.maidenheadToLatLon(Common.homeQTH);
  if(latLon==null){logger.te("homeQTH maidenhead locator=" + Common.homeQTH + " could not be mapped to latitude/longitude."); return;}
  this.latHome = latLon[0]; this.lonHome = latLon[1];
  this.logger.println("Home lat=" + this.latHome + ", lon=" + this.lonHome);
  this.updateCreateCallmaiden=0;
  this.decodeSuccessesTotal=0;  // successful decoding in MessageRecord.decodeMessage()
  this.decodeFailuresTotal=0;   // failed decoding in MessageRecord.decodeMessage()
  this.sourceMaidenheadTotal=0;  // total records with successful decode but no source maidenhead, i.e. no known maidenhead of transmitter.int decodeSuccessesTotal;  
  this.callsignnew=0; this.callsignupdated=0;
  }


/**
 * Populate a MessageRecord object and call decodeMessage().  
 * Return null is mode is not FT8 or FT4
 * Called by ReadWSJTXFile.parseFields(), UDPWSJTX.messageType2()
 * @param ldt
 * @param radioFrequency
 * @param rxtx
 * @param mode
 * @param timeOffset
 * @param rssi
 * @param audioFrequency
 * @param message
 * @param messTypeString
 * @return 
 */
public MessageRecord createMessageRecord(LocalDateTime ldt, float radioFrequency, 
         String rxtx, String mode, float timeOffset, int rssi, int audioFrequency, String[] message, String[] messTypeString, boolean lowconfidence, byte[] qtimeBytes, byte[] deltaTimeBytes)
  {
  if((!mode.equalsIgnoreCase("FT8"))&&(!mode.equalsIgnoreCase("FT4")&&(!mode.equalsIgnoreCase("WSPR"))))
    {this.nonFT84Total++; logger.println("parseFields returning null for record with mode=" + mode); return null;}
  atrec = new MessageRecord(this.logger, ldt, radioFrequency, rxtx, mode, timeOffset, rssi, audioFrequency, message, messTypeString, lowconfidence, qtimeBytes, deltaTimeBytes);
  if(atrec.decodeMessage()) 
    {
    if(debugFlag)logger.tprintln("Successful MessageRecord.decodeMessage() time=" + ldt + ", message=" + atrec.concatMessage(message) + ", messagetype=" + atrec.messageType);
    this.decodeSuccessesTotal++;
    } 
  else
    {
    logger.tprintln("WARNING: Failed to MessageRecord.decodeMessage() time=" + ldt + ", message=" + atrec.concatMessage(message));
    this.decodeFailuresTotal++; return atrec;
    }
  if(atrec.sourceCallsign != null)
    {
    int scLen = atrec.sourceCallsign.length();
    if(scLen < 3) 
      {
      logger.tprintlnwarn("sourceCallsign is of length=" + scLen + ". Message=" + atrec.concatMessage(atrec.message));
      }
    }
  else logger.tprintlnwarn("sourceCallsign is null.  Message=" + atrec.concatMessage(atrec.message));
  return atrec;
  }


/**
 * 
 * Increments this.sourceMaidenheadTotal
 * At present it always returns true.
 * Called by: ReadWSJTXFile.parseFile(), UDPProcessMessageThread state=20.
 * Tread carefully here.  This is a complex method.
 * 1) Call this.maidFromCallsign() to obtain the maidenhead from the source's callsign and assign to MessageRecord.
 * Returns boolean prefixMaiden indicating whether the maidenhead was assigned from source callsign prefix.
 * prefixMaiden is true if maidenhead was assigned from source callsign prefix. and is false if obtained if obtained from callmaid.
 * 2) If the callsign or maidenhead in the message is null then return without further processing.
 * 3) Call Propdbutils.callsignToCountrycode() to obtain country from callsign.
 * 4) Call this.createUpdateCallmaid() to:
 *  a) If sourceCallsign or sourceMaidenhead are null then return without action.
 *  b) If there is no callmaid record then create it.  
 *  c) If there is a callmaid then update it if prefixMaiden=false.
 *     prefixMaiden is true if maidenhead was assigned from callsign prefix.
 *     The assumption is that the licensee provided maidenhead preponderates over that provided by the FCC database or approximation from country code.
 * 5) Call AlltxtUtils.calculateBearingDistance() to assign bearing and distance in ALLTXT record.
 */
public boolean processMessageRecord(MessageRecord atrl)
  {
  int prefixMaiden;
  //logger.tprintln("Begin processMessageRecord");
  //long etmr1 = System.currentTimeMillis();
  if(atrl.sourceIs==null){logger.tprintlnwarn("ERROR: atrl.sourceIs==null. Message=" + atrl.concatMessage(atrl.message) + ". Message type=" + atrl.messageType);} 
  prefixMaiden=this.maidFromCallsign(atrl); // step 1. assign sourceCallsign, sourceMaidenhead
  if(prefixMaiden==0) return false;  // maidFromCallsign returned fatal error.
  if(this.callsignIsNull(atrl.sourceCallsign) || this.maidenIsNull(atrl.sourceMaidenhead)) // step 2.
    {
    if(debugFlag)logger.println("sourceCallsign or sourceMaidenhead is null for " + atrl.ldt + ", message=" + atrl.concatMessage(atrl.message));       
    return false;
    } // no reason to create/update callmaid.
  //long etmr2 = System.currentTimeMillis();
  //logger.println("etmr maidFromCallsign=" + (etmr2 - etmr1));
  //etmr1=etmr2; 
  String countryCode = this.prodb.callsignToCountrycode(atrl.sourceCallsign); // step 3. obtain country from callsign.
  this.createUpdateCallmaid(atrl,prefixMaiden, countryCode);  // step 4.
  //etmr2=System.currentTimeMillis();
  //logger.println("etmr.createUpdateCallmaid=" + (etmr2 - etmr1));
  //etmr1=etmr2;
  // 7) If we know sourceMaidenhead then calculate bearing and distance.
  this.sourceMaidenheadTotal++;
  this.alltxtUtilObj.calculateBearingDistance(atrl, this.latHome, this.lonHome);  // step 5.
  //logger.println("etmr calculateBearingDistance=" + (etmr2 - etmr1));
  return true;
  } // end processMessageRecord()




/**
 * The goal here is to obtain the maidenheads of the caller callsign and respondent callsign when it is not obtained from the message.
 * Then assign sourceMaiden from either callerMaiden or respondentMaiden depending on value of sourceIs.
 * All in four easy steps:
 * 1) If callerCallsign is populated and callerMaiden is not
 * then obtain callerMaiden from CALLMAID via callerCallsign.  
 * If CALLMAID does not provide callerMaiden then get lat/lon from callerCallsign and use this to obtain callerMaiden.
 * 2) Similarly for respondentMaiden:
 * If respondentCallsign is populated and respondentMaiden is not
 * then obtain respondentMaiden from CALLMAID via respondentCallsign.  
 * If CALLMAID does not provide respondentMaiden then get lat/lon from respondentCallsign and use this to obtain respondentMaiden.
 * 3) Assign sourceMaidenhead based on sourceIs flag.
 * If sourceIs=="C" then sourceCallsign/maidenhead is callerCallsign/maidenhead.
 * If sourceIs=="R" then sourceCallsign/maidenhead is respondentCallsign/maidenhead. 
 * 4) Return prefixMaiden which is:
 *   (1) if source.maidenhead was assigned from source callsign prefix,
 *   (2) if source.maidenhead was assigned from CALLMAID,
 *   (3) if assigned from WSJTX message.
 *   (0) if fatal error.
 */
private int maidFromCallsign(MessageRecord atrl)
  {
  int prefixMaiden = 3;// (1) if source.maidenhead was assigned from callsign prefix.
                       // (2) if assigned from CALLMAID (preexisting record).
                       // (3) if assigned from MessageRecord.
  int prefixMaidenCaller=0;
  int prefixMaidenRespondent = 0;// 1 if respondent maidenhead was assigned from callsign prefix.
  // First we populate, if necessary, callerMaiden.
  // If callerMaiden was not assigned in MessageRecord.decode() then look it up in table callmaid from callerCallsign.
  // If that fails then get a an approximate maidenhead from the callsign prefix.
  // prefixMaiden is true if maidenhead was assigned from source callsign prefix.
  //1)
  if(this.maidenIsNull(atrl.callerMaiden) && !this.callsignIsNull(atrl.callerCallsign) && atrl.decodeStatus==true)
    { // callerMaiden is null, callerCallsign is not.
    if(debugFlag)logger.println("callerMaiden is null, callerCallsign is not.  Querying callmaid by callsign=" + atrl.callerCallsign + " to obtain callerMaiden");
    atrl.callerMaiden=this.prodb.queryCallmaidCallsign(atrl.callerCallsign); // query callmaid table for callsign
    if(atrl.callerMaiden==null) // if you didn't find callsign in callmaid then obtain gross estimate from callsign prefix.
      {
      double[] latlon=null;
      latlon = this.prodb.callsignToLatLonDouble(atrl.callerCallsign);
      if(latlon != null)
        {
        atrl.callerMaiden=this.mhObj.latLonToMaiden(latlon[0],latlon[1]); 
        if(atrl.sourceIs.equals("C"))prefixMaidenCaller=1;
        }
      }
    else prefixMaidenCaller=2;  // maidenhead was found in existing callmaid record.
    } // end block callerMaiden was null and callerCallsign was not.
  // Next we populate, if necessary, respondentMaiden.
  // If respondentMaiden was not assigned in MessageRecord.decode() then look it up in table callmaid from respondentCallsign.
  // If that fails then get a an approximate maidenhead from the callsign prefix.
  if(this.maidenIsNull(atrl.respondentMaiden) && !this.callsignIsNull(atrl.respondentCallsign) && atrl.decodeStatus==true)
    {
    if(debugFlag)logger.println("respondentMaiden is null, respondentCallsign is not.  Querying callmaid by callsign=" + atrl.respondentCallsign + " to obtain callerMaiden");
    atrl.respondentMaiden=this.prodb.queryCallmaidCallsign(atrl.respondentCallsign); // query callmaid table for callsign
    if(atrl.respondentMaiden==null) // if you didn't find callsign in callmaid then obtain gross estimate from callsign prefix.
      {
      double[] latlon=null;
      latlon = this.prodb.callsignToLatLonDouble(atrl.respondentCallsign);
      if(latlon != null)
        {
        atrl.respondentMaiden=this.mhObj.latLonToMaiden(latlon[0],latlon[1]); 
        if(atrl.sourceIs.equals("R"))prefixMaidenRespondent=1;
        }
      }
    else prefixMaidenRespondent=2;
    } // end block respondentMaiden was null and respondentCallsign was not.
  // ensure that sourceMaidenhead is populated.
  if(atrl.sourceIs == null){logger.e("atrl.sourceIs is null.  See log for message details."); this.reportMessageRecord(atrl); return 0;}
  //3) maidenhead was found in WJTX message record
  if(atrl.sourceMaidenhead==null)
    {
    if(atrl.sourceIs.equals("C"))
      {
      atrl.sourceMaidenhead=atrl.callerMaiden;
      prefixMaiden=prefixMaidenCaller;
      }
    else if(atrl.sourceIs.equals("R"))
      {
      atrl.sourceMaidenhead=atrl.respondentMaiden;
      prefixMaiden=prefixMaidenRespondent;
      }    
    }
  if(debugFlag)logger.println("maidFromCallsign returns prefixMaiden=" + prefixMaiden);
  return prefixMaiden;
  } // end maidFromCallsign()
  
 
 



/**
 * It is the sourceCallsign/sourceMaidenhead whose CALLMAID record will be created or updated.
 * countryCode is derived from callsign via prodb.callsignToCountrycode()
 * 1) If sourceCallsign or sourceMaidenhead are null then return without action.
 * 2) Query callmaid for this callsign to determine whether the record exists and if so whether country is populated.
 *    argument countryCode will update callmaid only if existing callmaid.country is null.
 * 3) If there is no callmaid record then create it with callsign, maidenhead, and country.
 * 4) If there is a callmaid then update it.
 *   int prefixMaiden is:
 *   (1) if source.maidenhead was assigned from source callsign prefix.  Least authoritative.  Never update existing callmaid record.
 *   (2) if source.maidenhead was assigned from CALLMAID then do nothing. there is no reason to overwrite with the same data.
 *   (3) if assigned from WSJTX message. Most authoritative.  Always update existing callmaid record unless maidenhead from WSJTX is a subset of existing callmaid
 * The assumption is that the licensee provided maidenhead preponderates over that provided by the FCC database or approximation from country code.
 * increments this.callsignnew true upon creation of new callmaid record.
 * increments this.callsignupdated true when existing callmaid is updated.
 */  
private void createUpdateCallmaid(MessageRecord atrl, int prefixMaiden, String countryCode)
  {
  int rowCount;
  this.callcreateflag=false;
  this.callupdateflag=false;
  String DSArray = "13F";  // for new callmaid record produced by WSJTX message
  // 1) Return without action if sourceCallsign or sourceMaidenhead are not populated.
  if(this.maidenIsNull(atrl.sourceMaidenhead)) // this should be impossible given same code in calling method.
    {
    logger.tprintln("WARNING: sourceMaidenhead is null for message=" + atrl.concatMessage(atrl.message) + ". messageType=" + atrl.messageType + ", sourceCallsign=" + atrl.sourceCallsign); 
    return;
    }
  else if(this.callsignIsNull(atrl.sourceCallsign)) // this should also be impossible.
    {
    logger.tprintln("WARNING: sourceCallsign is ... for message=" + atrl.concatMessage(atrl.message) + ". messageType=" + atrl.messageType + ", sourceCallsign=" + atrl.sourceCallsign); 
    return;
    }
  // 2) Query callmaid for this callsign to determine whether the record exists and if so whether country is populated.
  //    If country was already populated then use it to overwrite the argument countryCode.
  // flagme To avoid unnecessary database queries the callmaid query in this.maidFromCallsign() should return this data in addition to int prefixMaiden.
  ResultSetTableModel rstm = this.prodb.queryCallmaid("CALLSIGN, MAIDENHEAD, COUNTRY, DS, mdate, crdate", "callsign = '" + atrl.sourceCallsign + "'");
  rowCount=rstm.getRowCount();
  String country=null;
  String cmMaid=null, goodMaid;
  if(rowCount==1)  
    {
    try
      {
      rstm.rsl.absolute(1);
      country=rstm.rsl.getString("COUNTRY");
      cmMaid = rstm.rsl.getString("MAIDENHEAD");
      }
    catch(SQLException se){logger.ee("Failed to read country or maidenhead in PopulateAlltxt.createUpdateCallmaid()", se); return;}       
    } // end rowcount==1
  if(country != null) countryCode = country;  // keep the existing callmaid.countrycode
  // 3) if there is no callmaid then create one and populate with callsign, maidenhead, country, and DS. DSArray is "13F" for new callmaid records.
  if(rowCount==0)
    {  // insertUpdateCallmaidRecord will insert a new callmaid record because no record exists with this callsign.
     CallmaidRecord cr = new CallmaidRecord(atrl.sourceCallsign, atrl.sourceMaidenhead, countryCode, DSArray);
     this.updateCreateCallmaiden++;
     this.callsignnew++;
     this.callcreateflag=true;
     prodb.insertUpdateCallmaidRecord(cr, "I");
    return;
    }
  // All of the following is for updating an existing callmaid record.
  // 4.1) prefixMaiden==1. if source.maidenhead was assigned from source callsign prefix.  Least authoritative.  Never update existing callmaid record.
  if(prefixMaiden==1) return;
  // 4.2) prefixMaiden==2.  maidenhead came from existing callmaid.maidenhead.  No reason to update with identical maidenhead.
  if(prefixMaiden==2) return;
  // 4.3) if assigned from WSJTX message. Most authoritative.  Always update existing callmaid record
  // unless atrl.sourceMaidenhead is a subset of existing maidenhead.
  goodMaid = atrl.sourceMaidenhead;  // assume WSJTX.maidenhead
    String atrlMaid=atrl.sourceMaidenhead.toUpperCase(); // convert wsjtx.maidenhead and callmaid.maidenhead to upper case for comparison.
    cmMaid=cmMaid.toUpperCase();
    if(cmMaid.length() < 4  && atrlMaid.length() > 3) goodMaid = atrlMaid; //if maidenhead <=3 characters and grid >=4 characters then return grid.
    int cmlength = cmMaid.length();
    int atrllength = atrlMaid.length();
    int minlength;
    if(cmlength < atrllength) minlength = cmlength; else minlength = atrllength;
    if(!cmMaid.substring(0,minlength).equals(atrlMaid.substring(0,minlength)))
      {
      if(atrllength > cmlength) goodMaid = atrlMaid;  
      else goodMaid = cmMaid;
      }
  if(debugFlag)logger.println("Updating new maidenhead=" + goodMaid + " into table callmaid for callsign=" + atrl.sourceCallsign);
  this.updateCreateCallmaiden++;
  if(debugFlag)System.err.println("Updating callmaid.callsign=" + atrl.sourceCallsign);
  //rstm = this.prodb.queryCallmaid("MAIDENHEAD, COUNTRY, DS, mdate, crdate", "callsign = '" + atrl.sourceCallsign + "'");
  try
    {
    //rstm.rsl.absolute(1);
    DSArray = this.cmutils.dsUpdate(rstm.rsl.getString("DS"),'3', 1);     // for existing callmaid record set ds[1] to "3".  Retain ds[0] and ds[2].
    rstm.rsl.updateString("DS",DSArray);     // for existing callmaid record set ds[1] to "3".  Retain ds[0] and ds[2].
    char qthstatus = this.cmutils.dsQuery(DSArray, 2);  // get qth status
    int elapseddays = this.getDaysBetween(rstm.rsl.getDate("mdate"));
    if(qthstatus == 'N' && elapseddays > 360) this.callcreateflag=true; // pretend that we created a new record so that UDPProcessMessageThread will update from HamQTH
    if(qthstatus == 'F' || qthstatus == '0') this.callcreateflag=true;
    rstm.rsl.updateString("maidenhead", goodMaid);
    rstm.rsl.updateDate("mdate",new java.sql.Date(System.currentTimeMillis()));
    if(rstm.rsl.getDate("crdate")==null)rstm.rsl.updateDate("crdate",new java.sql.Date(System.currentTimeMillis()));
    rstm.rsl.updateRow();
    rstm.commit();
    }
  catch(SQLException se){logger.ee("Failed to write update of maidenhead to callmaid.callsign=" + atrl.sourceCallsign,se); return;}
  this.callsignupdated++;
  this.callupdateflag=true;
  //if(!prodb.insertUpdateCallmaiden(atrl.sourceCallsign, atrl.sourceMaidenhead, countryCode, DSArray))this.databaseWriteFailures++;
  } // end method createUpdateCallmaid()
 
  


public void writeToDatabase(MessageRecord atrl)
  {
  boolean  writeToDatabase=false;
  if(debugFlag)logger.tprintln("Begin writeToDatabase");
  // The folliwing messagetypes should have been filtered out by the calling method.
  if(atrl.messageType==0 || atrl.messageType==10 || atrl.messageType==11) return; // 0 should be impossible, 10 means there was no message.
  if(this.prodb.submitAlltxtDatabase(atrl)) this.databaseWrites++; // write record to database.
  else this.databaseWriteFailures++;   
  return;
  }  // end method writeToDatabase()    


/**
 * Days between now and CALLMAID creation date.
 * If crdate is null then return 366 days.
 * @param crdate
 * @return 
 */
private int getDaysBetween(java.sql.Date crdate)
  {
  long crdatemillis;
  long millisinday = 86400000;
  if(crdate==null) return 366;
  else crdatemillis = crdate.getTime();
  long nowmillis = System.currentTimeMillis();
  long daysl = (nowmillis - crdatemillis)/millisinday;
  return (int)daysl;
  }








public String reportSummarystdout(int recordsdecoded, String filename)
  {
  String retval="";
  System.out.println("Total records with successful decoding=" + this.decodeSuccessesTotal);
  System.out.println("Total records with failed decoding=" + this.decodeFailuresTotal);
  System.out.println("Total records with source maidenhead=" + this.sourceMaidenheadTotal);
  System.out.println("Total database writes=" + this.databaseWrites + ", database write failures=" + this.databaseWriteFailures);
  System.out.println("Total update/creates of callmaid=" + this.updateCreateCallmaiden);
  System.out.println("Duplicate records=" + this.prodb.duplicates);
  System.out.println("Non-FT8/FT4 records=" + this.nonFT84Total);
  retval +="Total records with successful decoding=" + this.decodeSuccessesTotal + "\n";
  retval +="Total records with failed decoding=" + this.decodeFailuresTotal + "\n";
  retval +="Total records with source maidenhead=" + this.sourceMaidenheadTotal + "\n";
  retval +="Total database writes=" + this.databaseWrites + ", database write failures=" + this.databaseWriteFailures + "\n";
  retval +="Duplicate records=" + this.prodb.duplicates + "\n";
  retval +="Non-FT8/FT4 records=" + this.nonFT84Total;
  return retval;
  }
public void reportSummary(int recordsdecoded, String filename)
  {
  logger.println("Total records with successful decoding=" + this.decodeSuccessesTotal);
  logger.println("Total records with failed decoding=" + this.decodeFailuresTotal);
  logger.println("Total records with source maidenhead=" + this.sourceMaidenheadTotal);
  logger.println("Total database writes=" + this.databaseWrites + ", database write failures=" + this.databaseWriteFailures);
  logger.println("Total update/creates of callmaid=" + this.updateCreateCallmaiden);
  logger.println("Duplicate records=" + this.prodb.duplicates);
  logger.println("Non-FT8/FT4 records=" + this.nonFT84Total);
  }



private void reportMessageRecord(MessageRecord atrl)
  {
  // report ldt, radioFrequency, rxtx, mode, rssi,timeOffset, audioFrequency, message, callerCallsign, 
  // callerMaiden, respondentCallsign, respondentMaiden, sourceMaidenhead, sourceLat, sourceLon,
  // distanceBetween, initialBearing, 
  // respondentReportedrss, callerReportedrss,
  // contestIdentifier
  // datetime needs '2015-11-05 14:29:36'
  DateTimeFormatter format1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");  
  logger.println("+++++++++++++++++++++");
  logger.println("Time/date=" + atrl.ldt.format(format1) + ", radioFrequency=" + atrl.radioFrequency
    + ", rxtx=" + atrl.rxtx + ", mode=" + atrl.mode
    + ", rssi=" + atrl.rssi
    + ", timeOffset=" + atrl.timeOffset
    + ", audioFrequency=" + atrl.audioFrequency);
  logger.println("Message=" + atrl.concatMessage(atrl.message));
  logger.println("Caller callsign=" + atrl.callerCallsign + ", caller maidenhead=" + atrl.callerMaiden + ", callerReportedrss=" + atrl.callerReportedrss);
  logger.println("Respondent callsign=" + atrl.respondentCallsign + ", respondent maidenhead=" + atrl.respondentMaiden);
  logger.println("Source maidenhead=" + atrl.sourceMaidenhead + ", source lat=" + atrl.sourceLat + ", source lon=" + atrl.sourceLon);
  logger.println("Distance between=" + atrl.distanceBetween + ", bearing=" + atrl.initialBearing);
  logger.println("Respondent reported rss=" + atrl.respondentReportedrss + ", caller reported rss=" + atrl.callerReportedrss);
  logger.println("Contest identifier=" + atrl.contestIdentifier);
  logger.println("Message type=" + atrl.messageType);
  logger.println("-----------------------");
  }




/**
 * Is callsign null or invalid?
 * @param cs
 * @return 
 */
private boolean callsignIsNull(String cs)
  {
  if(cs==null || cs.equals("") || cs.equals("...")) return true;
  else return false;
  }

private boolean maidenIsNull(String mh)
  {
  if(mh==null || mh.equals("") || mh.equals("..."))return true;
  else return false;
  }







}  // end class PopulateAlltxt
