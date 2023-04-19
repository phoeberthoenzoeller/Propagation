/*
 * Copyright 2021.
 * Charles Gray.
 * All rights reserved.
 */
package UDP;

import m.net.Utils;
import UDP.UDPSocket;
import java.net.InetAddress;
import v.Log.Logfile;

/**
 *
 * @author Charles Gray
 */
public class UDPUtils
{
Logfile logger;

public UDPUtils(Logfile ll)
  {
  this.logger = ll;
  }

public boolean sendExpect(UDPSocket udpl, InetAddress destAddress, int destPort, byte[]sendBytes, byte[]expectBytes)
  {
  if(!udpl.send(sendBytes, destAddress, destPort)) return false;
  logger.println("Sent " + sendBytes.length + " bytes.  Waiting for " + expectBytes.length + " bytes.");
  byte[] brec = udpl.receive();
  if(brec==null) {logger.e("sendExpect returned null.");return false;}
  for(int x=0; x < expectBytes.length; x++)
    {
    if(brec[x] != expectBytes[x])
      {
      logger.e("Response in sendExcpect not expected value.  Received " + brec.length + " bytes, expected " + expectBytes.length);
      if(brec.length==1 && expectBytes.length==1)
      logger.println("Received " + brec[0] + ", expected " + expectBytes[0]);
      return false;
      }
    }
  logger.println("sendExpect returns true.");
  return true;
  }  // end sendExpect()



public boolean sendExpectFast(UDPSocket udpl, InetAddress destAddress, int destPort, byte[]sendBytes, byte[]expectBytes)
  {
  if(!udpl.send(sendBytes, destAddress, destPort)) return false;
  logger.println("Sent " + sendBytes.length + " bytes.  Waiting for " + expectBytes.length + " bytes.");
  byte[] brec = udpl.receiveFast();
  if(brec==null) {logger.e("sendExpect returned null.");return false;}
  for(int x=0; x < expectBytes.length; x++)
    {
    if(brec[x] != expectBytes[x])
      {
      logger.e("Response in sendExcpect not expected value.  Received " + brec.length + " bytes, expected " + expectBytes.length);
      if(brec.length==1 && expectBytes.length==1)
      logger.println("Received " + brec[0] + ", expected " + expectBytes[0]);
      return false;
      }
    }
  logger.println("sendExpect returns true.");
  return true;
  }  // end sendExpectFast()



public boolean expectSend(UDPSocket udpl, InetAddress destAddress, int destPort, byte[]expectBytes, byte[]sendBytes)
  {
  if(!udpl.send(sendBytes, destAddress, destPort)) return false;
  logger.println("Sent " + sendBytes.length + " bytes.  Waiting for " + expectBytes.length + "bytes.");
  byte[] brec = udpl.receive();
  if(brec==null) return false;
  for(int x=0; x < brec.length; x++)
    {
    if(brec[x] != expectBytes[x])
      {
      logger.e("Response in sendExcpect no expected value.");
      return false;
      }
    }
  return true;
  }  // end expectSend()



public InetAddress listInterfaces()
  {
  Logfile loglocal = new Logfile("logs/listinterfaceslog.txt","DataComm",true);
  Utils utes = new Utils(loglocal);
  String localHostName=utes.getHostName();
  System.out.println("Local host name=" + localHostName);
  logger.println("Local host name=" + localHostName);
  String[] addresses = utes.enumerateAddresses();
  InetAddress preferredInetAddress= utes.getPreferredHostInetAddress();
  for(String addStr:addresses)loglocal.println("Viable address=" + addStr);
  loglocal.println("Preferred host address=" + preferredInetAddress.getHostAddress());
  loglocal.closeLogfile();
  return preferredInetAddress;
  }

public void sleep(int millis)
  {
  try{Thread.sleep(millis);}
  catch(InterruptedException ie){};
  }
 


/**
 * UDP transmit test
 * @param udpl
 * @return 
 */
private boolean UDPTransmitTest(UDPSocket udpl, int payLoadSize)
  {
  byte[] testPayLoad;
  testPayLoad = new byte[payLoadSize];  
  for(int x=0; x < testPayLoad.length; x++) testPayLoad[x]=0x41; // ASCII 'A'
  logger.println("Sending from server to client testPayload of " + testPayLoad.length + " bytes.");
  long starttime = System.currentTimeMillis();
  //if(!sendExpect(udpl, udpl.remoteAddress, udps.remotePort, testPayLoad, ACK)) return false;
  for(int x=0; x < 100; x++)
    {
    udpl.send(testPayLoad,udpl.remoteAddress,udpl.remotePort);
    udpl.receiveFast();
    }
  logger.println("Send of payload received ACK.  Elapsed time=" + (System.currentTimeMillis() - starttime) + " milliseconds.");
  logger.println("Send of payload received ACK.  Elapsed time=" + (System.currentTimeMillis() - starttime) + " milliseconds.");
  return true;
  } // end test()







} // end class UDPUtils
