/*
 * Copyright Charles Gray.
 * All rights reserved.
 * Contact author for desired changes in licensing.
 */

package v.File;


import java.io.File;
import java.io.RandomAccessFile;
import javax.swing.table.TableModel;
import javax.swing.table.TableModel;
import javax.swing.JOptionPane;
import java.awt.Frame;
import java.awt.FileDialog;
import javax.swing.JFileChooser;
import java.io.IOException;
import java.util.Date;
import java.text.SimpleDateFormat;
import m.Common;
import v.Log.Logfile;

/**
 * Class to Write the data in a TableModel to a text file.
 * This class includes the option to exclude specified columns from the output.
 * The first line to be written are the column names.
 * It is assumed that the ResultSet within the TableModel is at row beforeFirst.
 * This is necessary in order to accommodate databases like SQLite that are TYPE_FORWARD_ONLY.
 * <p>Title: ExportToTextFileTableModel<p>
 * <p>Description: Export TableModel to text disk file.</p>
 * <p>Copyright: Copyright (c) 2015</p>
 * <p>Company: Charles Gray</p>
 * @author Charles Gray
 * @version 4.0
 */
public class ExportToTextFileTableModel
{
RandomAccessFile raf;
TableModel rstmToFile;
String fileName;
boolean flag = false;
String fileName1 = "";
JFileChooser fc = new JFileChooser();
String[] hiddenColumns = null;
Logfile logger;
String fieldDelim = "\"";  // field delimiter
String fieldSep = "\t";  // field separator
String recordSep = "\r\n"; // record separator
String fileSuf = ".txt";  // file suffix
String basename = "";   // output file basename
boolean absolute;  // absolute filename flag  If absolute==true then create File object for pathFileName + baseName.
// If aboslute==false then provide FileDialog for user to specify output filename in pathFileName folder.
String pathFileName;  // if absolute=false then folder for destination file.  if absolute=true then complete file path.
private String[] textOutParams = new String[5];  // basename, file  suffix, field separator, field delimiter, record separator
private String windowTitle;

// getter and setter methods
public String getBasename(){return this.basename;}
public void setBasename(String basename){this.basename=basename;}
public String getFileSuffix(){return this.fileSuf;}
public void setFileSuffix(String suf){this.fileSuf=suf;}
public String getFieldSep(){return this.fieldSep;}
public void setFieldSep(String fs){this.fieldSep=fs;}
public String getFieldDelim(){return this.fieldDelim;}
public void setFieldDelim(String delim){this.fieldDelim=delim;}




/**
  * Constructor wih a QueryDataset as a parameter.It gets the name of the file to which the data has to be saved to from the user.
  * @param 
  */
public ExportToTextFileTableModel(TableModel tm, String[] params)
    {
    this(tm,params,".","Export to Text File");
    }

/**
 * If absolute==true then create File object for pathFileName + baseName.
 * If absolute==false then provide FileDialog for user to specify output filename in pathFileName folder.
 * @param tm
 * @param params
 * @param destAbsPathFileName // if absolute=false then pathFileName is folder for destination file.  if absolute=true then pathFileName is complete file path.
 */
public ExportToTextFileTableModel(TableModel tm, String[] params, String basename, String windowTitle) 
  {
  this(tm,params,".",null,false, basename, windowTitle); 
  }


/**
 * If absolute==true then create File object for pathFileName + baseName.
 * If absolute==false then provide FileDialog for user to specify output filename in pathFileName folder.
 * @param rstm
 * @param params
 * @param destAbsPathFileName
 * @param hiddenColumns 
 */
public ExportToTextFileTableModel(TableModel rstm, String[] params, String destAbsPathFileName, String[] hiddenColumns, boolean abs, String bn, String windowTitle)
  {
  this.rstmToFile = rstm;
  this.textOutParams=params;
  this.logger=Common.logger;
  this.pathFileName=destAbsPathFileName;  // if absolute=false then pathFileName is folder for destination file.  if absolute=true then pathFileName is complete file path.
  this.hiddenColumns=hiddenColumns;
  this.absolute=abs;
  if(bn==null)this.basename=params[0]; else this.basename=bn;
  this.fileSuf=params[1];
  this.fieldSep=params[2];
  this.fieldDelim=params[3];
  this.recordSep=params[4];
  this.windowTitle = windowTitle;
  this.export();
  }




public boolean export()
  {
  if(createOutputFile(this.windowTitle,this.basename, this.absolute)==null) return false;
  return writeToFileRSTM();
  }


private File createOutputFile(String filename)
  {
  return this.createOutputFile("Save Table to Text File",filename,true);
  }

/**
 * If absolute==true then create File object for pathFileName + baseName.
 * If absolute==false then provide FileDialog for user to specify output filename in pathFileName folder.
 */
private File createOutputFile(String windowTitle, String filename, boolean absolute)
  {
  File oFile = null;
  try
    {
    // If absolute flag set then take passed String as exact file name.
    if (absolute)
      {
      if (filename == null || filename.equals(""))
        {
        logger.println("Null file name passed to ExportToTextFile.createOutputFile");
        JOptionPane.showMessageDialog(null, "Null file name passed to ExportToTextFile.createOutputFile", "Oops",JOptionPane.ERROR_MESSAGE);
        return null;
        }
      }  // end absolute
    if(!absolute)
      {
      Date dt = new Date();
      String str = filename + new SimpleDateFormat("dd_MM_yy_HH_mm_ss").format(dt)+ fileSuf;
      fileName = saveFileDialog(new Frame(),windowTitle,this.pathFileName,str);
      }  // end !absolute
    // Create the file
    oFile = new File(fileName);
    if (oFile.exists())
      {
      oFile.delete();
      }
    oFile.createNewFile();
    } // end try block to create file
  catch (IOException IOE)
    {
    JOptionPane.showMessageDialog(null,  "ERROR creating file" + filename, "File Creation Error", JOptionPane.ERROR_MESSAGE);
    logger.println("ERROR creating file" + filename);
    return null;
    }
  return oFile;
  } // end method createOutputFile



/**
 * 
 * @param f Frame in which to display FileDialog
 * @param title  Title of FileDialog
 * @param defDir  Default folder for file
 * @param basename Default file name
 * @return 
 */
private String saveFileDialog(Frame f, String title, String defDir, String basename)
  {
  System.out.println("defDir=" + defDir + ", basename=" + basename);
  FileDialog fd = new FileDialog(f, title, FileDialog.SAVE);
  try
    {
    fd.setFile(basename);
    fd.setDirectory(defDir);
    fd.setLocation(50, 50);
    fd.show();
    if (fd.getFile() == null) return null;
    else
      {
      if (fd.getFile().indexOf(fileSuf) == -1)
        {
        String filename = fd.getFile();
        if (filename.indexOf(".") != -1)
          {
          filename = filename.substring(0, filename.indexOf("."));
          return fd.getDirectory() + "" + filename + fileSuf;
          }
        else return fd.getDirectory() + "" + fd.getFile() + fileSuf;
        }
      }
    } // end try
  catch(Exception ex)
    {
    logger.println("Exception caught in ExportTextToFile.saveFileDialog:" + ex.getMessage());
    }
  return fd.getDirectory() + "" + fd.getFile();
  } // end method saveFileDialog


  /**
   *
   * @param qdsLoc QueryDataSet
   * @param filename String
   * @return boolean
   */
public boolean writeToFileRSTM(TableModel tmLoc, String filename)
  {
  this.rstmToFile=tmLoc;
  this.fileName = filename;
  return this.writeToFileRSTM();
  }


/**
  * Method that actually writes data to a text file.
  * If class String[] hiddenColumns is not null then omit those columns from output
  * Write contents of this.qdsToFile to filename this.filename
  */
private boolean writeToFileRSTM()
  {
  boolean hideFlg;
  String colVal;
  /*
  try{if(this.tmToFile.rsl.isClosed()){logger.e("ResultSet is closed at start of ActionPerformed."); return;}}
  catch(SQLException sqle){logger.ee("Failed to get ResultSet.isClosed() call.", sqle); return;}
  */
  try
    {
    String str = "";
    int rows = this.rstmToFile.getRowCount();  // this.tmToFile is TableModel of query
    int cols = this.rstmToFile.getColumnCount();
    logger.i("Writing to file=" + this.fileName + ", row count=" + this.rstmToFile.getRowCount() + ", column count=" + this.rstmToFile.getColumnCount());
    // Column numbers begin at "1" in TableModel.MetaData.  However, in TableModel.columnNames[] they begin at 0
    for (int i = 0; i < cols; i++) // for each column, write column header, excluding hidden columns.
      {
      logger.println("Column# " + i + "=" + this.rstmToFile.getColumnName(i));
      hideFlg = false;
      if(this.hiddenColumns != null && hiddenColumns.length > 0)
        {
        for(int a=0;a<hiddenColumns.length;a++)
          {
          if(hiddenColumns[a].equals(this.rstmToFile.getColumnName(i)))
            {
            hideFlg = true;
            break;
            }
          }
        }
      if(!hideFlg)  str = str + fieldDelim + this.rstmToFile.getColumnName(i) + fieldDelim; // column header string
      if(i < (cols - 1)) str += fieldSep;
      } // end for each column in header
    logger.i("Completed write of column headers.  Beginning row contents.");
    raf = new RandomAccessFile(this.fileName, "rw");
    raf.writeBytes(str + recordSep);
    for (int i = 0; i < rows; i++)  // for each row in data
      {
      str = "";
      for (int j = 0; j < cols; j++) // for each column of row
        {
        hideFlg = false;
        if(hiddenColumns != null && hiddenColumns.length > 0)
          {
          for(int a=0;a<hiddenColumns.length;a++)
            {
            if(hiddenColumns[a].equals(this.rstmToFile.getColumnName(j)))
              {
              hideFlg = true;
              break;
              }
            }
          }
        if(hideFlg) continue;
        // colVal = String.valueOf(tmToFile.getValueAt(i, j));
        // colVal = rstmToFile.rsl.getString(j + 1); // first column is column 1
        colVal = String.valueOf(rstmToFile.getValueAt(i, j));
        str = str + fieldDelim + colVal + fieldDelim;
        if(j < (cols - 1)) str += fieldSep;
        } // end for each column of reow
      raf.writeBytes(str + recordSep);
      } // end for each row
    raf.close();
    }
  catch(Exception ex)
    {
    logger.println("Exception caught in ExportToTextFile().writeToFile :"+ex);
    return false;
    }
  return true;
  }  // end method writeToFile





/**
  * Method that asks for confirmation to replace a file , if the file name entered by the user already exists.
  * @param ffile File
  */
private void replace(File ffile)
  {
  try
    {
    JOptionPane jpane = new JOptionPane();
    int answer = jpane.showConfirmDialog(null,"Replace the existing file ?","Replace File",jpane.YES_NO_OPTION);
    if (answer == jpane.YES_OPTION)
      {
      ffile.delete();
      ffile.createNewFile();
      if(flag) fileName = fileName1;
      writeToFileRSTM();
      }
    else
      {
      fileName1 = saveFileDialog(new Frame(),"Save",".\\",fileSuf);
      if(fileName1 != null && !fileName1.equals(""))
        {
        File ffile1 = new File(fileName1);
        if (ffile1.exists())
          {
          flag = true;
          replace(ffile1);
          }
        else
          {
          ffile.createNewFile();
          fileName = fileName1;
          writeToFileRSTM();
          }
        } // end if
      } // end else
    }
  catch(Exception e)
    {
    logger.println("Exception caught in replace() in ExportToTextFile.replace :"+e);
    }
  } // end method replace()


/**
  * Method that displays a input dialog and prompts the user to enter a file name, with a default file name "Export".
  * @return String - Returns the name of the file entered by the user.
  */
private String SaveFile()
  {
  String str = JOptionPane.showInputDialog("Enter the name of the file to export to :","Export");
  return str;
  }

}// end class ExporttoTextFile
