/* * 
 * Regrettably, in order to use this class you will have to read the comments within it.
 * Utilizes v.TableCallmaid.java in libsrc to display the results.
 *Copyright Charles Gray.
 * All rights Reserved.
 */
package v;

import java.sql.SQLException;
import m.Common;
import v.Log.Logfile;
import m.db.ResultSetTableModel;
import c.Time.TimeUtils;
import m.db.Database;
import java.text.DecimalFormat;
import java.sql.Date;
import javax.swing.JOptionPane;



/**
 * Display database table contents for given table name and where clause.
 * This class is limited to a single table.
 * @author chas
 */


/**
 * 	ARRAY 	2003
	BIGINT 	-5
	BINARY 	-2
	BIT 	-7  // MySQL/MariaDB returns this type for a column defined as boolean/tinyint(1)
	BLOB 	2004
	BOOLEAN	16
	CHAR 	1
	CLOB 	2005
	DATALINK 70
	DATE 	91
	DECIMAL 3
	DISTINCT 2001
	DOUBLE 	8
	FLOAT 	6
	INTEGER 4.  smallint(5) unsigned
	JAVA_OBJECT 2000
	LONGNVARCHAR -16
	LONGVARBINARY -4
	LONGVARCHAR -1
	NCHAR 	-15
	NCLOB 	2011
	NULL 	0
	NUMERIC 2
	NVARCHAR -9
	OTHER 	1111
	REAL 	7.  float
	REF 	2006
	REF_CURSOR 2012
	ROWID 	-8
	SMALLINT 5.  Tinyint(3)unsigned, smallint(6) signed
	SQLXML 	2009
	STRUCT 	2002
	TIME 	92
	TIME_WITH_TIMEZONE 	2013
	TIMESTAMP 93.  datetime
	TIMESTAMP_WITH_TIMEZONE	2014
	TINYINT -6.  tinyint(4)unsigned
	VARBINARY -3
	VARCHAR 12
 * 
 */
public class DisplayTables 
{
private final Logfile logger;
private ResultSetTableModel rstm;
private final TimeUtils tu;
private final Database dbLocal;
public DecimalFormat intf= new DecimalFormat("###,###,###"); 
public DecimalFormat df2 = new DecimalFormat("###,###,##0.00");
public DecimalFormat df3 = new DecimalFormat("###,###,##0.000");

public DisplayTables(Logfile logger, Database dbl)
  {
  this.logger = logger;    
  this.tu = new TimeUtils();  
  this.dbLocal = dbl;
  }



/**
 * 
 * @param tableName
 * @param whereClause
 * @param columnHeaders  If a null columnHeaders array is passed then database table column names will be used.
 * @param columnSelections
 * @param columnTypes
 * @return 
 */
public boolean displayTable(String SQLClause, String[] columnHeaders, int[] columnSelections, int[] columnTypes)
  {
  if(SQLClause==null || SQLClause.equals("")){logger.e("Null query passed to displayTable()."); return false;}
  rstm=this.queryTable(SQLClause);
  if(rstm == null) return false;
  if(rstm.getRowCount()==0)
    {
    JOptionPane.showMessageDialog(null,"Your query did not match any rows in database.","NOTICE",JOptionPane.INFORMATION_MESSAGE);    
    logger.println(SQLClause + " returned no rows.");
    return false;
    }
  if(columnHeaders==null)columnHeaders=this.rstm.getColumnNames();
  columnTypes=this.getConversionTypes(columnTypes, this.rstm);
  if(columnTypes==null) return false; // getConversionTypes() failed.
  if(!this.displayResultSet(columnHeaders, columnSelections, columnTypes)) return false;
  return true;
  } // end method displayTable



/**
 * * Current conversion types are as follows:  Others will surely follow.
 * 0) Obtain column type from ResultSet and format automatically.
 * 1) Char. display as as string with length=1
 *    SQL Type: 1
 * 2) No conversion simply display a string. 
 * 2xx) String of which first xx characters are displayed.
 *   SQL Types: -16, -1, -15, -9, 12.
 * 3) long of time/datetime: Value is long int representation of time. 
 *   SQL Types: 93
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
 * -7) MariaDB returns this type for boolean/tinyint(1)
 * @param int[] columnSelections.  A list of columns to be displayed.  A pointer into ResultSetTableModel.get<value>(column#) and pointer into int[] conversionTypes
 * @return success
 */
public boolean displayResultSet(String[] columnHeaders, int[] columnSelections, int[] conversionTypes)
  {
  columnHeaders=getSelectedHeaders(columnHeaders,columnSelections); // Reduce the list of column headers to just those for columns selected.
  Table logTable = new Table(Common.logger,columnHeaders,rstm.getTableName() + " Table");
  int rowcount=this.rstm.getRowCount();
  this.logger.println(rowcount + " rows returned from log query ");
  String[] columnsStringArray;
  if(Common.debugFlag)
    {
    try
      {
      for(int x=1; x <=rstm.getColumnCount(); x++)logger.println("Col " + x + " label=" + rstm.getColumnLabel(x) + ", name=" + rstm.getColumnName(x-1) + ", typeName=" + rstm.getColumnTypeName(x) + ", type=" + rstm.getColumnType(x));
      }
    catch(SQLException se){logger.ee("fubar", se);}
    }
  for(int row=0; row < rowcount; row++)  // for each row in resultset
    {
    columnsStringArray = this.columnsToStrings(rstm, row, columnSelections, conversionTypes);
    if(columnsStringArray==null) return false;
    logTable.addRow(columnsStringArray);
    } // end for loop all records in query result 
  logTable.adjustColumns();
  return true;
  } // end class displayResult


/**
 * For specified row of ResultSetTableMode, read each column, convert to String, and populate the return String[]
 * @param rstmcts
 * @param columnSelections
 * @param conversionTypes
 * @return 
 */
public String[] columnsToStrings(ResultSetTableModel rstmcts, int rowTarget, int[] columnSelections, int[] conversionTypes)
  {
  int tempInt;
  double tempDouble;
  float tempFloat;
  boolean tempBool;
  int colCount = columnSelections.length;
  int columnSelect;
  String strColumn;  // String of current column after conversion.
  String[] columnsStringArray = new String[colCount];
  if(!rstmcts.absolute(rowTarget)){logger.e("Unable to move to absolute row=" + rowTarget + " of table " + rstmcts.getTableName()); return null;}
  for(int csptr=0; csptr < columnSelections.length; csptr++) // for each selected column
     {
     columnSelect=columnSelections[csptr];//System.out.println("columnSelections[" + csptr + "]=" + column);
     try
     {
     switch (conversionTypes[columnSelect]) // convert to String the column contents in ResultSet based on columnType
       {
       case 1:  // char      
         strColumn=rstmcts.rsl.getString(columnSelect + 1);  // getString() of next selected column from columnSelections. getString() is column 1 based.
         break;
       case 2: // no conversion, simple string
          strColumn=rstmcts.rsl.getString(columnSelect + 1);
          break;
        case 3:  // Date
          java.sql.Date date=rstmcts.rsl.getDate(columnSelect + 1);
          strColumn=tu.dateToString(date);
          break;
        case 4: // Integer
          tempInt=rstmcts.rsl.getInt(columnSelect + 1);
          strColumn=this.intf.format(tempInt);
            break;
        case 5: // double
          tempDouble=rstmcts.rsl.getDouble(columnSelect + 1);
          strColumn=this.df2.format(tempDouble);
          break;
        case 6: // float
          tempFloat=rstmcts.rsl.getFloat(columnSelect + 1);
          strColumn=this.df2.format(tempFloat);
          break;
        case 7: // Boolean in Derby/MariaDB
          tempBool = rstmcts.rsl.getBoolean(columnSelect + 1);
          strColumn = String.valueOf(tempBool);
          break;
        default: logger.te("Invalid column type=" + conversionTypes[columnSelect]);
          return null;
        } // end switch on column type
      }
      catch(SQLException se){this.logger.ee("Failed to read column from table " + rstmcts.getTableName(), se); return null;}
      columnsStringArray[csptr]=strColumn;               
      }  // end for each column.
    return columnsStringArray;
    }  // end columnsToStrings()


public ResultSetTableModel queryTable(String SQLClause) 
  {
  int rowcount;
  // Query table
  ResultSetTableModel rstmlocal=null;
  try{rstmlocal = this.dbLocal.selectT(SQLClause);}
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
  //if(debugFlag)System.out.println(queryString + " returned " + rowcount + " rows.");
  if(rowcount==-1) // error
    {
    logger.te("Query failed. Query=" + SQLClause);
    return null;
    } 
  return rstmlocal;
  } // end QueryTable























/**
 * This method is the same as that in Propdbutils.  It is duplicated here to avoid the dependency on Propdbutils.
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
public int[] getConversionTypes(int[] columnTypes, ResultSetTableModel rstml)
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
    else if(ct==16) cTypes[x]=7;  // boolean (Derby)
    else if(ct==-7) cTypes[x]=7;  // boolean MariaDB
    else{logger.e("Please add code to getConversionTypes() to handle SQL type=" + ct); return null;}
    }
  if(Common.debugFlag)
    {
    try{for(int z=0; z < dbColTypes.length; z++)logger.println("Label=" + rstml.getColumnLabel(z+1) + ", database column type=" + dbColTypes[z] + ", conversion type=" + cTypes[z]);}
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
 * @param String[] columnHeaders.  All column headers for this table.
 * @param int[] columnSelections.  The column numbers selected for display from the table.
 * @return String[] columnHeaders corresponding to the column numbers in columnSelections.
 */
public String[]  getSelectedHeaders(String[] columnHeaders, int[] columnSelections) // Reduce the list of column headers to just those for columns selected.
  {
  String[] chs  = new String[columnSelections.length];
  for(int x=0; x < columnSelections.length; x++)
    {
    chs[x]=columnHeaders[columnSelections[x]];
    }
  return chs;
  } // end getSelectedHeaders()



} // end class DisplayTables
