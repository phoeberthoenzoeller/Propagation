/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package v;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Toolkit;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.JDialog;


/**
 *
 * @author chas
 */
public class MessageDisplay extends JDialog
{
    
private String message; 
private JWindow jwin; 
private int fontsize=20;
private int messagelengthinpixels;


public MessageDisplay(String messl)
 {
 this(messl,-1,-1);
 }



public MessageDisplay(String messl, int x, int y) // x,y are location on screen. 
  { 
  super((JDialog)null,messl,false); // default to non-modal
  this.messagelengthinpixels = (int)(messl.length() * 10.3);
  System.out.println("messagelengthinpixels=" + this.messagelengthinpixels);
  if(x==-1 || y==-1)
    {
    // java - get screen size using the Toolkit class
    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    // the screen height
    int height=(int)screenSize.getHeight();
    // the screen width
    int width=(int)screenSize.getWidth();       
    x=width/2 - this.messagelengthinpixels/2; y=height/2;
    System.out.println("x=" + x);
    }
  jwin = new JWindow(); 
  //System.out.println("height=" + height + ", width=" + width);
  // make the background transparent 
  jwin.setBackground(new Color(0, 0, 0, 0));  
  createPanel(messl,x,y);
  }
 

private void createPanel(String messl, int x, int y)
  {
  jwin.setSize(this.messagelengthinpixels, 550); 
  // create a panel 
  JPanel jpan = new JPanel() 
    { 
    public void paintComponent(Graphics g) 
      { 
      // Make the font
      Font font = new Font("myfont", Font.BOLD, fontsize);
      g.setFont(font);
      int wid = g.getFontMetrics().stringWidth(messl); 
      int hei = g.getFontMetrics().getHeight(); 
      // Make the boundary fit the font
      // draw the boundary of the notice and fill it 
      g.setColor(Color.RED); 
      g.fillRect(10, fontsize, wid + 30, hei + 20); 
      g.setColor(Color.RED); 
      g.drawRect(10, fontsize, wid + 30, hei + 20); 
      // set the color of text 
      g.setColor(new Color(255, 255, 255, 240)); 
      g.drawString(messl, 25,(int)(fontsize * 2.5));
      int t = 250; 
      // draw the shadow of the JPanel
      for (int i = 0; i < 4; i++) 
        { 
        t -= 60; 
	g.setColor(new Color(0, 0, 0, t)); 
  	g.drawRect(10 - i, 10 - i + fontsize/2, wid + 30 + i * 2, hei + 10 + i * 2 + fontsize/2); 
	}
      } 
    };  // end JPanel 
  jwin.add(jpan); 
  jwin.setLocation(x, y); 
  }  // end createPanel


public void setModal(boolean modality)
  {
  super.setModal(modality);
  }
  
  
public void showMessage()
  {
  jwin.setAlwaysOnTop(true);
  jwin.setOpacity(1);
  jwin.setVisible(true);
  }


public void showOff()
  {
  this.jwin.setVisible(false);
  //this.jwin.dispose();
  this.dispose();
  }


  // function to pop up the message flashing
public void showMessage(int seconds) 
	{ 
	try 
      { 
          jwin.setAlwaysOnTop(true);
	  jwin.setOpacity(1); 
	  jwin.setVisible(true); 
			// wait for some time 
	  Thread.sleep(2000); 
    	// make the message disappear slowly 
      for(int secs=0; secs < seconds/2; secs++)
        {
	    for (double d = 1; d > 0.2; d -= 0.1) 
          { 
		  Thread.sleep(100);  
		  jwin.setOpacity((float)d); 
		  } 
        for (double d = 0.2; d <1; d += 0.1) 
          { 
		  Thread.sleep(100);  
		  jwin.setOpacity((float)d); 
		  } 
        Thread.sleep(400);
        }
      // set the visibility to false 
	  jwin.setVisible(false); 
	  } 
	catch (Exception e) 
      { 
	  System.out.println(e.getMessage()); 
	  } 
	} // end method showMessage() 


private void waitmillis(int millis)
 {
 try{Thread.sleep(millis);}
 catch(InterruptedException ie){;}
 }

} // end class ShowMessage

 
    
    
    
 
