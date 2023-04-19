package v.File;

import m.Common;
import java.awt.*;
import v.File.ExportToTextFileResultSet;
import javax.swing.JLabel;
import java.awt.BorderLayout;
import v.Log.Logfile;
import javax.swing.JPanel;
import javax.swing.*;
import java.awt.Rectangle;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import v.Log.Logfile;
import javax.swing.JOptionPane;

/**
 * <p>Title: </p>
 *
 * <p>Description: 
 * Accepts initial values via String array params[] passed via constructor.
 * Sets attributes within the public variable String arrary textOutParams.
 * Parameters are: 
 * Basename
 * File suffix
 * Field separator
 * Field delimiter
 * Record separator
 * </p>
 * 
 *
 * <p>Copyright: Copyright (c) 2014</p>
 *
 * <p>Company: </p>
 *
 * @author Charles Gray
 * @version 1.0
 */
public class ConfigureTextOutput extends JFrame
{
public String[] textOutParams;
private final Logfile logger;
public String basename;
public String filesuffix;
public String fielddelimiter;
public String fieldseparator;
public String recordseparator;
public boolean savedFlag;  // did the operator press button "save"

public ConfigureTextOutput()
  {
  this(new String[5]);
  }
  
public ConfigureTextOutput(String[] params)
  {
  this.textOutParams = params;
  this.basename=params[0];
  this.filesuffix=params[1];
  this.fielddelimiter=params[2];
  this.fieldseparator=params[3];
  this.recordseparator=params[4];
  this.logger=Common.logger;
  this.savedFlag=false;
  try { jbInit(); }
  catch (Exception ex) {  logger.ee("Exception thrown in ConfigureTextOutput constructor.", ex); }
  this.setLocation(200,200);
  this.setVisible(true);
  } // end constructor

  JPanel jPanel1 = new JPanel();
  JLabel jLabel1 = new JLabel();
  JTextField jTextField1 = new JTextField();
  JLabel jLabel2 = new JLabel();
  JTextField jTextField2 = new JTextField();
  JLabel jLabel3 = new JLabel();
  JTextField jTextField3 = new JTextField();
  JButton jButton1 = new JButton();
  JLabel jLabel4 = new JLabel();
  JTextField jTextField4 = new JTextField();
  JLabel jLabel5 = new JLabel();
  JTextField jTextField5 = new JTextField();
  private void jbInit() throws Exception {
    setSize(new Dimension(330,232));
    this.setTitle("Data Export Parameters");
    jPanel1.setLayout(null);
    jLabel1.setText("Basename");
    jLabel1.setBounds(new Rectangle(10, 10, 95, 16));
    jTextField1.setBounds(new Rectangle(110, 10, 190, 22));
    jLabel2.setText("File Suffix");
    jLabel2.setBounds(new Rectangle(10, 40, 90, 14));
    jTextField2.setBounds(new Rectangle(110, 40, 40, 19));
    jLabel3.setText("Field Separator");
    jLabel3.setBounds(new Rectangle(10, 70, 90, 14));
    jTextField3.setToolTipText("Field Separator");
    jTextField3.setBounds(new Rectangle(110, 70, 40, 19));
    jButton1.setBounds(new Rectangle(20, 160, 73, 23));
    jButton1.setText("Save");
    jButton1.addActionListener(new Submit_actionAdapter(this));
    jLabel4.setText("Field Delimiter");
    jLabel4.setBounds(new Rectangle(10, 106, 100, 14));
    jTextField4.setBounds(new Rectangle(110, 100, 40, 19));
    jLabel5.setText("Record Separator");
    jLabel5.setBounds(new Rectangle(10, 135, 130, 14));
    jTextField5.setBounds(new Rectangle(110, 130, 40, 19));
    
    this.add(jPanel1, java.awt.BorderLayout.CENTER);
    jPanel1.add(jTextField1);
    jPanel1.add(jTextField2);
    jPanel1.add(jButton1);
    jPanel1.add(jLabel4);
    jPanel1.add(jLabel1);
    jPanel1.add(jLabel2);
    jPanel1.add(jLabel3);
    jPanel1.add(jLabel5);
    jPanel1.add(jTextField3);
    jPanel1.add(jTextField4);
    jPanel1.add(jTextField5);
    jTextField1.setText(this.basename);
    jTextField2.setText(filesuffix);
    jTextField3.setText(fieldseparator);
    jTextField4.setText(fielddelimiter);
    jTextField5.setText(recordseparator);
  } // end jbInit

  public void jButton1_actionPerformed(ActionEvent e) {
  logger.println("Submit button pressed for following parameters:");
  logger.println("Basename=" + jTextField1.getText());
  logger.println("File Suffix=" + jTextField2.getText());
  logger.println("Field Separator=" + jTextField3.getText());
  logger.println("Field Deliminator=" + jTextField4.getText());
  this.basename=(jTextField1.getText());
  this.filesuffix=(jTextField2.getText());
  this.fieldseparator=(jTextField3.getText());
  this.fielddelimiter=(jTextField4.getText());
  this.textOutParams[0]=this.basename;
  this.textOutParams[1]=this.filesuffix;
  this.textOutParams[2]=this.fieldseparator;
  this.textOutParams[3]=this.fielddelimiter;
  this.textOutParams[4]=recordseparator;
  this.savedFlag=true;
  Common.setTextOutParams(textOutParams);
  }
}  // end class ConfigureTextOutputclass ConfigureTextOutput_jButton1_actionAdapter implements ActionListener {



class Submit_actionAdapter implements ActionListener
{
private ConfigureTextOutput adaptee;
Submit_actionAdapter(ConfigureTextOutput adaptee) {
  this.adaptee = adaptee;
}
public void actionPerformed(ActionEvent e) {
  adaptee.jButton1_actionPerformed(e);
}
}
