/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package c;

import java.sql.SQLException;
import javax.swing.JOptionPane;
import m.Common;
import m.MessageRecord;
import m.Propdbutils;
import m.db.ResultSetTableModel;
import v.Log.Logfile;
import v.DisplayTables;
import v.TableCallsign;
import m.Maidenhead;
import m.Navigation;






/**
 * This class provides methods for populating and modifying contents of table CALLMAID
 * It is utilized by ReadWSJTXFile, 
 * @author chas
 */
public class CallmaidUtils 
{
private final Logfile logger;
private final Propdbutils putils;
private final boolean debugFlag;

public CallmaidUtils(Logfile l, Propdbutils p, boolean db)
  {
  this.logger = l;    
  this.putils = p;
  this.debugFlag = db;
  }


/**
 * Query table zipcodegeo for zipfile
 * @param putils
 * @param zippy
 * @return double[] lat/lon.  Return null for error.
 */
public double[] queryZipcodegeo(int zippy, String callsign)
  {
  double[] latlon = new double[2];
  ResultSetTableModel rstmzg = putils.queryZipcodegeo("zipcode = " + zippy);
  if(rstmzg.getRowCount()==0)
    {
    logger.printlnwarn("Failed to find zipcodegeo.zipcode for zipcode=" + zippy + " in callmaid.callsign = " + callsign);
    if(debugFlag)System.out.println("Failed to find zipcodegeo.zipcode for callmaid.callsign = " + callsign);
    return null;
    }
  if(rstmzg.getRowCount()>1)
    {
    logger.printlnwarn("Duplicate zipcodegeo.zipcode records for callmaid.callsign = " + callsign);
    if(debugFlag)System.out.println("Duplicate zipcodegeo.zipcode records for callmaid.callsign = " + callsign);
    return null;
    }
  try{rstmzg.rsl.first();latlon[0] = rstmzg.rsl.getFloat("latitude"); latlon[1]=rstmzg.rsl.getFloat("longitude");}
  catch(SQLException se){logger.ee("Failed to getFloat() lat or lon from table zipcodegeo.",se); return null;}
  if(debugFlag)System.out.println("latitude=" + latlon[0] + ", longitude=" + latlon[1]);
  return latlon;
  } // end method queryZipcodegeo
    

/**
 * Update specified position in callmaid.de column with new character.
 * @param oldDE
 * @param lc
 * @param position
 * @return 
 */
public String dsUpdate(String oldDS, char lc, int position)
  {
  char[] oldCharArray;
  if(oldDS==null || oldDS.equals("")) {oldCharArray = new char[3];  oldCharArray[0]='0'; oldCharArray[1]='0'; oldCharArray[2]='0';}
  else oldCharArray = oldDS.toCharArray();
  oldCharArray[position]=lc;
  return new String(oldCharArray);
  }

public char dsQuery(String DS, int position)
  {
  if(DS==null){logger.e("Null string passed to CallmaidUtils.dsQuery()"); return '0';}
  char[] charArray = DS.toCharArray();
  return charArray[position];
  }

public int dsCharToInt(char cl)
  {
  int a = Integer.parseInt(String.valueOf(cl));
  return a;
  }


public void displayCallmaid(String sqlClause)
  {
  int x;
  Maidenhead mhl = new Maidenhead(logger);
  Navigation navObj = new Navigation(logger);
  String[] columnHeadersDB = {"Callsign","Maidenhead","First Name","MI","Last Name","Street","City","State","Zipcode","MDate","Country","ITU","CQ","Worked","Creation Date","QSO Date","DS"};
  int[] columnSelectionDB = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16};
  int[] columnTypesDB = null; // setting this to null forces automatic column types which seem to be working fine for table callmaid.
  String[] columnsStringArray;   // array of strings from array of columns in one row.
  DisplayTables dispTab = new DisplayTables(logger, Common.dbAllTxt);
  //dispTab.displayTable(sqlClause, columnHeaders, columnSelection, columnTypes);  
  ResultSetTableModel rstm = dispTab.queryTable(sqlClause);
  if(rstm.getRowCount()==0)
    {
    JOptionPane.showMessageDialog(null,"Your query did not match any rows in database.","NOTICE",JOptionPane.INFORMATION_MESSAGE);    
    logger.println(sqlClause + " returned no rows.");
    return;
    }
  if(columnHeadersDB==null)columnHeadersDB=rstm.getColumnNames();  // not really appplicable here since I have provided the column headers above.
  columnTypesDB=dispTab.getConversionTypes(columnTypesDB, rstm);
  if(columnTypesDB==null) return; // getConversionTypes() failed.
  //if(!dispTab.displayResultSet(columnHeaders, columnSelection, columnTypes)) return;
  columnHeadersDB=dispTab.getSelectedHeaders(columnHeadersDB,columnSelectionDB); // Reduce the list of column headers to just those for columns selected.
  // Add columns to database columns here... Currently we are adding to columns, distance and bearing.
  String[] columnHeaders = new String[columnHeadersDB.length + 2];
  for(x=0; x< columnHeadersDB.length; x++)columnHeaders[x]=columnHeadersDB[x];
  columnHeaders[x]="Distance"; columnHeaders[x+1]="Bearing";
  //
  TableCallsign callmaidTable = new TableCallsign(Common.logger,columnHeaders,rstm.getTableName() + " Table");
  int rowcount=rstm.getRowCount();
  this.logger.println(rowcount + " rows returned from log query=" + sqlClause);
  if(Common.debugFlag)
    {
    try
      {
      for(x=1; x <=rstm.getColumnCount(); x++)
          logger.println("Col " + x + " label=" + rstm.getColumnLabel(x) + ", name=" + rstm.getColumnName(x-1) + ", typeName=" + rstm.getColumnTypeName(x) + ", type=" + rstm.getColumnType(x));
      }
    catch(SQLException se){logger.ee("fubar", se);}
    }
  for(int row=0; row < rowcount; row++)  // for each row in resultset
    {
    columnsStringArray = dispTab.columnsToStrings(rstm, row, columnSelectionDB, columnTypesDB);
    if(columnsStringArray==null) return;
    String[] tableStringArray = new String[columnsStringArray.length + 2];
    for(x=0; x < columnsStringArray.length; x++)tableStringArray[x]=columnsStringArray[x];
    double[] myLatLon = mhl.maidenheadToLatLon(Common.homeQTH);
    double[] hisLatLon = mhl.maidenheadToLatLon(columnsStringArray[1]);
    if(hisLatLon != null)
      {
      double[] distanceBearing = navObj.calculateBearingDistance(myLatLon, hisLatLon);
      tableStringArray[x]=dispTab.df2.format(distanceBearing[0]); tableStringArray[x + 1]=dispTab.df2.format(distanceBearing[1]); 
      }
    callmaidTable.addRow(tableStringArray);
    } // end for loop all records in query result 
  callmaidTable.adjustColumns();
  mhl=null;
  navObj=null;
  }  // end displayCallmaid



} // end class CallmaidUtils
