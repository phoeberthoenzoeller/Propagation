/*
 * This class is a wrapper around the DatagramSocket.
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package UDP;
/**
 *
 * @author chas
 */


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import v.Log.Logfile;




public class UDPSocket
{
protected DatagramSocket socket = null;
protected byte[] buffin = new byte[15000];  // flagme set in properties.txt
public boolean valid; // Did we construct a valid socket?    
public int localPort=4445;
public int remotePort;
private Logfile logger;
public InetAddress localAddress;
public InetAddress remoteAddress;
private boolean remoteAddressSet=false;
public DatagramPacket packetReceive;
private DatagramPacket packetSend;
private int socketTimeout;
private boolean debugFlag=false;
private boolean closeInProgress=false;


// Port=4445, Inetaddress.getLocalHost()
public UDPSocket()  
  {
  this(4445,null, null);
  }

// specified port, InetAddress.getLocalHost()
public UDPSocket(int portl, Logfile ll)
  {
  this(portl,null,ll);
  }

// specified port, specified InetAddress
public UDPSocket(int portl, InetAddress addressl, Logfile ll)
  {
  this.valid = false;
  this.logger = ll;
  this.localPort = portl;
  this.localAddress = addressl;
  this.logger = ll;
  if(ll==null)
    {
    this.logger = new Logfile("UDPServerLog.txt","UDPServer","0",true);
    }
  if(addressl==null)
    {
    try{this.localAddress = InetAddress.getLocalHost();} // flagme. This should be changed to address of getByName
    catch(UnknownHostException uhe)
      {
      this.logger.ee("Failed to InetAddress.getLocalHost()", uhe);
      return;
      }
    }
  try
    {
    this.socket = new DatagramSocket(this.localPort, this.localAddress);
    this.socket.setReuseAddress(true);
    }
  catch(IOException ioe)
    {
    this.logger.ee("Failed to create DatagramSocket for local port=" + this.localPort + ", local address=" + this.localAddress, ioe);
    return;
    }
  this.packetReceive = new DatagramPacket(buffin, buffin.length);
  this.packetSend = new DatagramPacket(buffin, buffin.length);
  this.valid = true;
  this.logger.println("Created DatagramSocket for local port=" + this.localPort + ", local address=" + this.localAddress);
  } // end constructor


public void setDebugFlag(boolean df){this.debugFlag=df;}


public boolean setTimeout(int millis)
  {
  try{this.socket.setSoTimeout(millis);}
  catch(SocketException se)
    {
    logger.ee("Failed to set DatagramSocket timeout to " + millis + " milliseconds.",se);
    return false;
    }
  return true;
  }


public void setMaxMess(int sizeInBytes){this.buffin = new byte[sizeInBytes];}

public byte[] receive()
  {
  String receivedString;
  int receivedLength=0;
  try{this.socket.receive(this.packetReceive);}
  catch(SocketTimeoutException ste)
    {
    this.logger.printlnwarn("DatagramSocket.receive() timed out:" + ste.getMessage());
    return null;
    }
  catch(IOException ioe)
    {
    if(!this.closeInProgress)this.logger.ee("DatagramSocket.receive() failed:" , ioe);
    return null;
    }
  receivedLength=this.packetReceive.getLength();
  if(this.debugFlag)logger.println("UDPSocket.receive() received packet of length=" + receivedLength);
  if(debugFlag)System.out.println("UDPSocket.receive() received packet of length=" + receivedLength + ", address=" + packetReceive.getAddress() + ", port=" + packetReceive.getPort());
  this.remoteAddress = packetReceive.getAddress();
  this.remotePort = packetReceive.getPort();
  byte[] bret = new byte[receivedLength];
  for(int x=0; x < receivedLength; x++)
    {
    bret[x]=this.packetReceive.getData()[x];
    }  // getData() returns byte[]
  String convertString=new String(bret); 
  if(this.debugFlag)logger.println("UDPSocket.receive() received from remote address=" + this.remoteAddress + " remote port=" + this.remotePort);
  return bret; // returns byte array
  } // end receive()




/**
 * In the interest of performance this method omits setting of this.remoteAddress, this.remotePort.
 * It also returns the entire buffer[15000] of bytes.  It is up to the calling method to
 * extract the packetReceive.getLength() bytes that are significant.
 * @return 
 */
public byte[] receiveFast()
  {
  String receivedString;
  int receivedLength=0;
  //this.packet = new DatagramPacket(buffin, buffin.length); 
  try{this.socket.receive(this.packetReceive);}
  catch(SocketTimeoutException ste)
    {
    this.logger.ee("DatagramSocket.receive() timed out:" , ste);
    return null;
    }
  catch(IOException ioe)
    {
    this.logger.ee("DatagramSocket.receive() failed:" , ioe);
    return null;
    }
  if(this.debugFlag)logger.println("UDPSocket.receive() received packet of length=" + receivedLength);
  return this.packetReceive.getData();
  } // end receiveFast()





public String receiveString()
  {
  return new String(this.receive());
  }


/**
 * This method can be used only after the DatagramSocket server has received a datagram.
 * The receipt of the datagram sets the client address and port.
 * The source address and source port will be this.serverAddress and this.serverPort
 */
public boolean send(byte[] buffout)
  {
  return this.send(buffout, this.remoteAddress, this.remotePort);
  }



/**
 * This method to be used when initiating a transmission from the client.
 * The source address and source port will be this.serverAddress and this.serverPort
 */
public boolean send(byte[] bufferout, InetAddress remAddress, int remPort)
  { 
  this.packetSend = new DatagramPacket(bufferout, bufferout.length, remAddress, remPort);
  if(this.debugFlag)logger.println("Sending packet of length=" + bufferout.length + " from address=" 
    + this.localAddress + ", from port=" + this.localPort + " to address=" + remAddress + " to port=" + remPort); 
  try{this.socket.send(this.packetSend);}
  catch(IOException ioe)
    {
    this.logger.ee("DatagramSocket.send() failed; cannot create socket to address=" + remAddress, ioe);
    return false;
    }
  return true;
  }


public boolean socketClose()
  {
  this.closeInProgress=true;
  // this.setTimeout(1); cannot set timeout when waiting for datagram
  this.socket.disconnect();
  this.socket.close();
  try{this.socket  = new DatagramSocket(null);  this.socket.setReuseAddress(true);}
  catch(SocketException se){logger.ee("Failed to create a null DatagramSocket", se);}
  return this.socket.isClosed();
  }

} // end class UDPSocket
