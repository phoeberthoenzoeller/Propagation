/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package c;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Locale;
import m.Common;
import m.db.ResultSetTableModel;
import v.Log.Logfile;
import v.UIMain;
import v.File.ExportCallsign;



/**
 *
 * @author chas
 */
public class UIMainC 
{
private final Logfile logger;
private UIMain mainObj;
private ExportCallsign exportCallsign;


public UIMainC(Logfile l)
  {
  this.logger=l;
  this.mainObj=null;
  }


public UIMainC(Logfile l, UIMain main)
  {
  this.logger = l;      
  this.mainObj = main;
  }

public UIMainC(Logfile l, ExportCallsign exportCallsign)
  {
  this.logger = l;      
  this.exportCallsign = exportCallsign;
  }

    
public boolean displayHTMLFile(String filename)
  {
  File file = new File(filename);
  PrintStream out = null;
  if(Desktop.isDesktopSupported())
    {
    logger.i("Display html file " + file.toURI());
    try 
      {
      Desktop.getDesktop().browse(file.toURI());
      }
    catch(UnsupportedOperationException uoe){logger.ee("Desktop is unsupported on this platform.  HTML cannot be rendered.",uoe); return false;    }
    // catch(URISyntaxException use) {logger.ee("URISyntaxException when loading html for Queryutil help.",use); return false;}
    catch(IOException ioe){logger.ee("IOException when loading html for Queryutil help.", ioe); return false;}
    catch(Exception e){logger.ee("Exception thrown in MainC.displayHTMLFile",e); return false;}
    } // end block if Desktop is supported
  return true;
  }// end method displayHTMLFile


public String buildSQLClauseAlltxt(String endDateUI, String startDateUI, String modeUI,
        int messageTypeUI, String callerCallsignUI, String respondentCallsignUI,
        String orAndUI, String sourceMaidenUI, String contestUI, String distanceUI,
        String plusMinusUI, String bearingUI, String bearingPlusMinusUI,
        String messageUI)
  {
  String SQLClause="select * from ALLTXT where ";    
  
  // First is <> ldt.  This anded with other variables.
  System.out.println("end date=" + endDateUI);
  String[] strDate = endDateUI.split("/");
  int month=Integer.parseInt(strDate[0]);
  int day=Integer.parseInt(strDate[1]);
  int year=2000 + Integer.parseInt(strDate[2]);
  String endDateString = String.valueOf(year) + "-" + String.valueOf(month) + "-" + String.valueOf(day) + " 23:59:59";
  System.out.println("endDate length=" + strDate.length + ", year=" + year + ", month=" + month + ", day=" + day + ", endDateString=" + endDateString);
  strDate = startDateUI.split("/");
  month=Integer.parseInt(strDate[0]);
  day=Integer.parseInt(strDate[1]);
  year=2000 + Integer.parseInt(strDate[2]);
  String startDateString = String.valueOf(year) + "-" + String.valueOf(month) + "-" + String.valueOf(day);
  System.out.println("startDate length=" + strDate.length + ", year=" + year + ", month=" + month + ", day=" + day + ", startDateString=" + startDateString);
  if(Common.DBType.equals("Derby_res"))SQLClause+="LDT >= CAST('" + startDateString + " 00:00:00' as TIMESTAMP) and LDT <= CAST('" + endDateString + "' as TIMESTAMP)";
  else SQLClause+="LDT >= '" + startDateString + "' and LDT <= '" + endDateString + "'";
  
  // mode. Optional. If mode is selected then it is anded with other variables.
  String mode=modeUI;
  System.out.println("mode=" + mode);
  if(mode.equals("FT8")) SQLClause += " and mode='FT8'";
  else if(mode.equals("FT4")) SQLClause += " and mode='FT4'";
  
  // message type / AllTxt.messageType. Optional. If message type is selected then it is anded with other variables.
  if(messageTypeUI != 0)SQLClause += " and messagetype=" + (messageTypeUI - 1);
  
  // callercallsign.  . Optional. If not null then callsign is anded with other variables.
  boolean callerExists=false;
  if(!callerCallsignUI.equals("")) callerExists=true;
  if(callerExists)SQLClause += " and (CALLERCALLSIGN like '" + callerCallsignUI.toUpperCase() + "'";
  
  // respondentCallsign.  Optional.  If caller callsign was specified then it anded or ored to caller callsign.  Else it is anded to other variables.
  if(!respondentCallsignUI.equals("")) 
    {
    if(callerExists)SQLClause += " " + orAndUI;
    else SQLClause += " and ";
    SQLClause += " respondentCallsign like '" + respondentCallsignUI.toUpperCase() + "'";
    }
  if(callerExists)SQLClause += ")";
  
  // sourceMaiden.  Optional. If specified then it is anded with other variables.
  if(!sourceMaidenUI.equals("")) SQLClause += " and sourceMaiden like '" + sourceMaidenUI + "%'";
  
  // contest.  Optional. If specified then it is anded with other variables.
  if(!contestUI.equals("")) SQLClause += " and contestidentifier like '" + "%" + contestUI.toUpperCase(Locale.ITALY) + "%'";
  
  // distanceBetween. Distance between transmitting callsign and QTH.  Specified in kilometers. Optional. +- is also in kilometers.
  String tempString = distanceUI;
  if(!tempString.equals(""))
    {
    int distance, plusminus, distanceminus, distanceplus;
    try
      {
      distance=Integer.parseInt(tempString);
      tempString = plusMinusUI;
      if(tempString.equals("")) tempString="0";
      plusminus=Integer.parseInt(tempString);
      }
    catch(NumberFormatException nfe){logger.ee("Integer.parseInt() failed for distance or plussminus.",nfe); return null;}
    distanceminus = distance - plusminus - 1;
    distanceplus = distance + plusminus + 1;
    SQLClause += " and distanceBetween > " + distanceminus + " and distanceBetween < " + distanceplus;
    }
  // bearing to transmitting site
  tempString = bearingUI;
  if(!tempString.equals(""))
    {
    int bearing, plusminus, bearingminus, bearingplus;
    try
      {
      bearing=Integer.parseInt(tempString);
      tempString = bearingPlusMinusUI;
      if(tempString.equals("")) tempString="0";
      plusminus=Integer.parseInt(tempString);
      }
    catch(NumberFormatException nfe){logger.ee("Integer.parseInt() failed for bearing or plussminus.",nfe); return null;}
    bearingminus = bearing - plusminus - 1;
    bearingplus = bearing + plusminus + 1;
    SQLClause += " and INITIALBEARING > " + bearingminus + " and INITIALBEARING < " + bearingplus;
    }
         
  // message content. Optional. Wildcards are appended to beginning and end of entered text.  Anded with other variables.
  if(!messageUI.equals(""))
      SQLClause += " and lower(MESSAGE) like '%" + messageUI.toLowerCase() + "%'";
  
  System.out.println("SQLclause=" + SQLClause);
  logger.println("Log query SQLclause=" + SQLClause);
  return SQLClause;           
  } // end buildSQLClauseAlltxt()





public String buildSQLClauseCallmaid(String callsignUI, String maidenheadUI,
        String cqZoneUI, String ituZoneUI, String moddateUI, int countryIndexUI,
        int stateIndexUI, int moddateIndexUI, boolean workedUI)
  {
  String SQLClause="";
  String callsign = callsignUI.toUpperCase();
  String maidenhead = maidenheadUI;
  String cqstr = cqZoneUI;
  String itustr = ituZoneUI;
  String mdate = moddateUI;
  int cqint, ituint;
  if(!callsign.equals(""))
    {
    logger.println("Selected callsign for CALLMAID query=" + callsign);
    SQLClause = " CALLSIGN like '" + callsign + "'";
    } // end callsign
  else if(!maidenhead.equals(""))
    {
    maidenhead += "%";
    logger.println("selected maidenhead for CALLMAID query=" + maidenhead);
    SQLClause = " MAIDENHEAD like '" + maidenhead + "'";
    } // end maidenhead
  else if(countryIndexUI !=0)
    {
    //System.out.println("Abbreviation is " + this.alabbr.get(this.jComboBoxCountry.getSelectedIndex()-1));
    SQLClause = " COUNTRY = '" + Common.prodb.alcountryabbr.get(countryIndexUI -1) + "'";// -1 because first entry in combobox is "None".
    }
  else if(stateIndexUI !=0)
    {
    //System.out.println("State Abbreviation is " + this.alabbr.get(this.jComboBoxCountry.getSelectedIndex()-1));
    SQLClause = " STATE = '" + Common.prodb.alstateabbr.get(stateIndexUI -1) + "'";
    }
  else if(!cqstr.equals("")) // cq zone specified
    {
    cqint = Integer.valueOf(cqstr);
    SQLClause = " CQ = " + cqint;
    }
   else if(!itustr.equals("")) // itu zone specified
    {
    ituint = Integer.valueOf(itustr);
    SQLClause = " ITU = " + ituint;
    }
   else if(!mdate.equals(""))
     {
     String[] modDate = moddateUI.split("/");
     int month=Integer.parseInt(modDate[0]);
     int day=Integer.parseInt(modDate[1]);
     int year=2000 + Integer.parseInt(modDate[2]);
     String modDateString = "'" + String.valueOf(year) + "-" + String.valueOf(month) + "-" + String.valueOf(day) + "'";
     System.out.println("modDate length=" + modDate.length + ", year=" + year + ", month=" + month + ", day=" + day + ", modDateString=" + modDateString);
     SQLClause = " MDATE ";
     if(moddateIndexUI==1) SQLClause += ">="; else SQLClause += "<=";
     SQLClause += modDateString;
     }
  if(workedUI)
     {
     if(SQLClause.equals("")) SQLClause=" WORKED=TRUE";
     else SQLClause += " and WORKED=TRUE";
     }
  System.out.println("SQLclause=" + SQLClause);
  if(SQLClause.equals("")){logger.e("Please specify a search constraint."); return "";}
  return "select a.CALLSIGN,a.MAIDENHEAD,a.FIRSTNAME,a.MIDDLEINITIAL,a.LASTNAME,a.STREET,a.CITY,a.STATE,a.ZIPCODE,a.MDATE,b.name, a.ITU, a.CQ, a.WORKED, a.CRDATE, a.QSODATE, a.DS from CALLMAID a left outer join COUNTRYABBR b on(a.COUNTRY=b.abbr) where " + SQLClause;
  } // end buildSQLClauseCallmaid()
      
    


// ad hoc











    
}  // end class UIMainC
