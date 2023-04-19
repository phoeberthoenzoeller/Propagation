/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package m;
import m.Common;
import v.Log.Logfile;
import java.util.Date;
import m.db.ResultSetTableModel;
import java.sql.SQLException;



/**
 * This class exists to simply carry the values of columns that constitute a CALLMAID record.
 * @author chas
 */
public class CallmaidRecord 
{
private final Logfile logger;
public String callsign;
public String maidenhead;
public String firstname;
public String middleinitial;
public String lastname;
public String street;
public String city;
public String state;
public int zipcode;
public String countrycode;
public int ITU;
public int CQ;
public boolean worked;
public java.sql.Date mdate;
public java.sql.Date crdate;
public java.sql.Date qsodate;
public String DS;





public CallmaidRecord(String callsign, String maidenhead, String firstname, String middleinitial, String lastname, String street, String city, String state, int zipcode, String countrycode, int ITU, int CQ, boolean worked, java.sql.Date crdate, java.sql.Date qsodate, String ds)
  {
  this.logger = Common.logger;
  this.callsign = callsign;
  this.maidenhead = maidenhead.toUpperCase();
  this.firstname = firstname;
  this.middleinitial = middleinitial;
  this.lastname = lastname;
  this.street = street;
  this.city = city;
  this.state = state;
  this.zipcode = zipcode;
  this.countrycode = countrycode;
  this.ITU = ITU;
  this.CQ = CQ;
  this.worked = worked;
  this.mdate = new java.sql.Date(System.currentTimeMillis());  
  this.crdate = crdate;
  this.qsodate= qsodate;
  this.DS = ds;
  } // end constructor


public CallmaidRecord(ResultSetTableModel rstm, int rowNumber)
  {
  this.logger=Common.logger;
  try
  {
  rstm.absolute(rowNumber);
  callsign=rstm.rsl.getString("CALLSIGN");
  maidenhead=    rstm.rsl.getString("MAIDENHEAD");
  firstname=    rstm.rsl.getString("FIRSTNAME");
  middleinitial=rstm.rsl.getString("MIDDLEINITIAL");
  lastname=rstm.rsl.getString("LASTNAME");
  street=    rstm.rsl.getString("STREET");
  city=    rstm.rsl.getString("CITY");
  state=    rstm.rsl.getString("STATE");
  zipcode=    rstm.rsl.getInt("ZIPCODE");
  countrycode=    rstm.rsl.getString("COUNTRY");
  mdate=    rstm.rsl.getDate("MDATE");
  ITU=    rstm.rsl.getInt("ITU");
  CQ=    rstm.rsl.getInt("CQ");
  worked=    rstm.rsl.getBoolean("WORKED");
  crdate = rstm.rsl.getDate("CRDATE");
  qsodate = rstm.rsl.getDate("QSODATE");
  DS = rstm.rsl.getString("DS");
  }
  catch(SQLException se){logger.ee("SQLException thrown in CallmaidRecord constructor=",se); return;}
  }
/**
 * This constructor is for populating the initial callmaid record from ReadEntityFile for read of EN.dat file from FCC.
 * @param cs
 * @param fn
 * @param mi
 * @param ln
 * @param sa
 * @param city
 * @param state
 * @param zip 
 */
public CallmaidRecord(String cs, String fn, String mi, String ln, String sa, String city, String state, int zip, String countrycode)
  {
  this(cs,"",fn,mi,ln,sa,city,state,zip,countrycode,0,0,false,null,null,"");  
  }

public CallmaidRecord(String cs, String maidenhead)
  {
  this(cs,maidenhead,"","","","","","",0,"",0,0,false,null,null,"");  
  }

public CallmaidRecord(String cs, String maidenhead, String countryCode, String DSArray)
  {
  this(cs,maidenhead,"","","","","","",0,countryCode,0,0,false,null,null, DSArray);  
  }

public void setMDate(java.sql.Date md)
  {
  this.mdate = md;
  }


public void logEntity(CallmaidRecord entity)
  {
  logger.println("CallmaidRecord variables follow*********************************************************:");
  logger.println("callsign = " + entity.callsign);    
  logger.println("maidenhead = " + entity.maidenhead);
  logger.println("firstName = " + entity.firstname);
  logger.println("middleInitial = " + entity.middleinitial);
  logger.println("lastName = " + entity.lastname);
  logger.println("streetAddress = " + entity.street);
  logger.println("city = " + entity.city);
  logger.println("state = " + entity.state);
  logger.println("zip = " + entity.zipcode);
  logger.println("Country code = " + entity.countrycode);
  logger.println("ITU = " + entity.ITU);
  logger.println("CQ = " + entity.CQ);
  logger.println("Worked = " + entity.worked);
  logger.println("MDate = " + this.mdate);
  logger.println("CRdate = " + this.crdate);
  logger.println("QSOdate = " + this.qsodate);
  logger.println("DS = " + this.DS);
  }





} // end CallmaidRecord
