/*
 * Instantiated by c.SwingWorkerGeneric, 
 * Copyright 2019.
 * Charles Gray.
 * All rights reserved.
 */
package v;


import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;
import java.awt.*;
import javax.swing.*;

 

/**
 *
 * @author Charles Gray
 */
public class ProgressBarGeneric  extends JPanel
{
public JTextArea taskOutput;
public JProgressBar progressBar;
public String title;
    
public ProgressBarGeneric(String titlel) 
  {
  super(new BorderLayout());
  this.title = titlel;
  //Create the ProgressBar UI.
  progressBar = new JProgressBar(0, 100);
  progressBar.setValue(0);
  progressBar.setStringPainted(true);
  taskOutput = new JTextArea(5, 20);
  taskOutput.setMargin(new Insets(5,5,5,5));
  taskOutput.setEditable(false);
  JPanel panel = new JPanel();
  panel.add(progressBar);
  add(panel, BorderLayout.PAGE_START);
  add(new JScrollPane(taskOutput), BorderLayout.CENTER);
  setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
  javax.swing.SwingUtilities.invokeLater(new Runnable() 
    {
    public void run() 
      {
      createAndShowGUI();
      }
    });
  setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
  } // end constructor
    
    
    
    /**
     * Invoked when task's progress property changes.
     */
   /*
    public void propertyChange(PropertyChangeEvent evt) {
        if ("progress" == evt.getPropertyName()) {
            int progress = (Integer) evt.getNewValue();
            progressBar.setValue(progress);
            taskOutput.append(String.format(
                    "Completed %d%% of task.\n", swTask.getProgress()));
        } 
    }
 */
 
    /**
     * Create the GUI and show it. As with all GUI code, this must run
     * on the event-dispatching thread.
     */
    public void createAndShowGUI() {
        //Create and set up the window.
        JFrame frame = new JFrame(this.title);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        
        //Create and set up the content pane.
        //JComponent newContentPane = new ProgressBarGeneric();
        JComponent newContentPane = this; // this is JPanel
        newContentPane.setOpaque(true); //content panes must be opaque
        frame.setContentPane(newContentPane);
 
        //Display the window.
        frame.pack();
        frame.setSize(500,200);
        frame.setLocation(400,400);
        frame.setVisible(true);
    }
 
} // end class ProgressBarGeneric
