/* 
 * This class provides methods to
 * a) Create a UDP Server (Constructor).    Used by UDPServerThread.
 * b) Receive data. (UDPSocket.receive()).  Used by UDPServerThread.
 * Instantiated exclusively by UDPServerThread.
 * Copyright 2021.
 * Charles Gray.
 * All rights reserved.
 */
package UDP;

import UDP.UDPSocket;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import UDP.UDPUtils;
import java.util.Arrays;
import v.Log.Logfile;
import v.Log.LogfileFactory;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.nio.ByteBuffer;
import c.Time.TimeUtils;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.StringTokenizer;
import m.MessageRecord;
import m.Common;   // for String[] messTypeString
import m.PopulateAlltxt;
import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import javax.swing.JOptionPane;
import m.net.Utils;





/**
 * This class creates a UDP server which receives from WSJT-X realtime messages.
 * Those messages are parsed.
 * The message types currently supported are as follows:
 * message type 0: Heartbeat.  No data.
 * message type 1: Status. Radio freq., mode, last qso caller callsign, last qso caller maidenhead.
 * message type 2: Message received from radio. Provides directly following ALLTXT columns: LDT, RSSI, TIMEOFFSET, AUDIOFREQUENCY, MODE, MESSAGE.
 *                 Message is missing RADIOFREQUENCY, RXTX, 
 * message type 3: I/O. Not currently supported.
 *      This message is  sent when all prior "Decode"  messages in the
 *      "Band Activity"  window have been discarded  and therefore are
 *      no long available for actioning  with a "Reply" message. It is
 *      sent when the user erases  the "Band activity" window and when
 *      WSJT-X  closes down  normally. The  server should  discard all
 *      decode messages upon receipt of this message.
 *
 *      It may  also be  sent to  a WSJT-X instance  in which  case it
 *      clears one or  both of the "Band Activity"  and "Rx Frequency"
 *      windows.  The Window  argument  can be  one  of the  following
 *      values:
 *
 *         0  - clear the "Band Activity" window (default)
 *         1  - clear the "Rx Frequency" window
 *         2  - clear both "Band Activity" and "Rx Frequency" windows
 * message type 4: Initiate a QSO.
 *           WSJT-X filters this message and only
 *           acts upon it  if the message exactly describes  a prior decode
 *           and that decode  is a CQ or QRZ message.   The action taken is
 *           exactly equivalent to the user  double clicking the message in
 *           the "Band activity" window.
 * message type 5: Log QSO.  The  QSO logged  message is  sent  to the  server(s) 
 *                 when  the WSJT-X user accepts the "Log  QSO" dialog by clicking the "OK" button.
 *             
 * message type 6: WSJT-X close notification. 
 * message type 7: In. Causes WSJT-X to send a "replay" of decoded messages.
 *      WSJT-X sends a "Decode" message for each decode currently in its "Band
 *      activity"  window. Each  "Decode" message  sent will  have the
 *      "New" flag set to false so that they can be distinguished from
 *      new decodes. After  all the old decodes have  been broadcast a
 *      "Status" message  is also broadcast. 
 * message type 8: In. Not yet implemented. Causes WSJT-X to stop transmitting messages.
 * message type 9: In. Sets the current free message text.
 * message type 10: WSPR Message. Directly populated columns: LDT, RDDI, TIMEOFFSET, RADIOFREQUENCY, CALLERCALLSIGN, CALLERMAIDEN.
 *                  Fields not in ALLTXT include power transmitted and drift in hertz.
 *                  RETEST
 * message type 11: In. This  message allows  the server  to set  the current  current
 *         geographical location  of operation. The supplied  location is
 *         not persistent but  is used as a  session lifetime replacement
 *         location that overrides the Maidenhead  grid locator set in the
 *        application  settings. 
 * message type 12: Log of QSO OK.  Includes ADIF in XML.
 * message type 13: In. Not yet implemented. The message
 *      specifies  the background  and foreground  color that  will be
 *      used  to  highlight  the  specified callsign  in  the  decoded
 *      messages  printed  in  the  Band Activity  panel. 
 * message type 14: In. Not implemented. Switch configuration. The message
 *      specifies the name of the  configuration to switch to. The new
 *      configuration must exist.
 * message type 15: In. Not implemented.  The message specifies  various  configuration  options. 
 *      These include: mode, frequency tolerance, submode, fast mode, t/r time, rx df, dx callsign, dx grid.
 * message type 50: In. Not implemented.  Setting TX delta frequency in JTDX.  Received  value  will  be
 *      checked against widegraph frequency range,  it will be ignored
 *      if it does not fit there.
 * message type 51: In. Not implemented.  The  triggerCQ   message  is  dedicated  to  set CQ direction,
     *      TX period  and  optionally  trigger  CQ  message  transmission 
     *      in  JTDX  from  external  software    through    the   network
     *      connection.  Directional  CQ  is  also  being  supported where 
     *      direction is two-char combination in the range AA..ZZ.
     *      TX period is equivalent to TX first in the Status UDP message,
     *      where  'true'  value  shall  correspond  to  'TX 00/30' second 
     *      in FT8 mode and 'TX even' minute in JT65/JT9/T10 modes.
     *      If the "Send" flag is set  then  CQ message  will be generated 
     *      and Enable Tx button will be switched on.
 * 
 * 
 * 
 * @author Charles Gray
 */
public class UDPWSJTX
{
private final Logfile logger; 
private final int payLoadSize=15000;
private int serverPort=2237;
private String localIPAddress;
private int initialTimeout=180; // three minutes wait to hear first datagram from client.
private int testTimeout=2000;  // timeout for subsequent communications with client.
public UDPSocket udps;
public boolean status;
private final static byte[] SYN = {0x16};
private final static byte[] ACK = {0x06};
private final static byte[] BEL = {0x07};
private final UDPUtils utilsObj;
private int cptr;  // pointer into byte[] of message.  Used to pass to submethods.
private enum parseMode {BOOLEAN,SHORT,INTEGER,LONG,STRING,DOUBLE,QTIME,QDATETIME};
private boolean debugFlag = false;
public float radiofrequency;  // in megahertz.  Assigned in messagetype=1.
public String rxtx;  // "rx" or "tx" assigned in messagetype=1 from field "transmitting".
private final TimeUtils tuObj;
private final int utcOffset;  // number of minutes between default local timezone and UTC.
private final DateTimeFormatter dateTimeFormat1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
private final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
private MessageRecord messagerec;
private PopulateAlltxt popAllTxtObj;
public int messageType;
public boolean currentlyTransmitting;  // for communication with UDPServerThread



public UDPWSJTX()
  {
  this(LogfileFactory.getLogfile(),100,false,2237,null);
  }


public UDPWSJTX(Logfile ll, boolean df)
  {
  this(ll,0,df,2237,null);    
  }

public UDPWSJTX(Logfile ll, int tt)
  {
  this(ll,tt,false,2237,null);  
  }

/**
 * Create a UDP server
 * @param ll
 * @param tt
 * @param df 
 */
public UDPWSJTX(Logfile ll, int tt, boolean df, int udpport, String localIPAddress)
  {
  this.logger = ll;
  this.debugFlag = df;
  status = false; // is this a valid object?
  this.initialTimeout = tt;
  this.tuObj = new TimeUtils(logger);
  this.serverPort=udpport;
  this.utcOffset = tuObj.timezoneMinutesOffset(tuObj.defaultTimezone());
  this.utilsObj = new UDPUtils(logger);
  this.currentlyTransmitting=false;
  this.popAllTxtObj = new PopulateAlltxt(this.logger,Common.prodb,Common.debugFlag);
  InetAddress ia = this.getInetAddress(localIPAddress);
  if(ia == null) ia = this.utilsObj.listInterfaces();
  if(!this.serverInit(ia,initialTimeout)) return;
  //System.out.println("Listening for " + testTimeout/1000 + " seconds.");
  //this.serverListen(udps, initialTimeout, testTimeout);
  //udps.socket.close();
  //System.out.println("End serverListen()");
  /*
  if(!this.serverCheckConnection(this.udps,this.initialTimeout,this.testTimeout))
    {
    logger.e("serverCheckConnection failed."); 
    return;
    }
  if(!test(this.udps))
    {
    logger.e("server test failed.");
    return;
    }
  */
  status = true;
  } // end constructor


private boolean serverInit(InetAddress ineta, int initialTimeout)
  {
  this.udps = new UDPSocket(this.serverPort,ineta,logger);
  if(!this.udps.valid)
    {
    logger.println("ERROR: UDPWSJTX.serverInit() failed."); 
    this.checkAddress(ineta);
    return false;
    }
  logger.println("Created socket on local address=" + udps.localAddress + " and port=" + udps.localPort + ", now listening.");
  if(debugFlag)System.out.println("Created socket on local address=" + udps.localAddress + " and port=" + udps.localPort + ", now listening.");
  this.udps.setTimeout(initialTimeout * 1000);   // specified in milliseconds
  logger.println("Socket timeout set to " + initialTimeout + " seconds.");
  return true;
  } // end serverinit()


public boolean serverClose()
  {
  logger.println("Begin UDPWSJTX.serverClose()");
  return this.udps.socketClose();
  }


/**
 * Listen on the UDPSocket.  THIS METHOD IS NOT currently utilized.  It was created for testing.
 * @param udpl
 * @param initialTimeout
 * @param testTimeout
 * @return 
 */
public boolean serverListen(UDPSocket udpl, int initialTimeout, int testTimeout)
  {
  byte[] bin;
  this.udps.setTimeout(initialTimeout);  
  if(debugFlag)System.out.println("Waiting for data from client.  Initial Socket timeout set to " + (float)initialTimeout/60000 + " minutes."); 
  logger.println("Waiting for data from client. Initial Socket timeout=" + (float)initialTimeout/60000 + " minutes, test duration=" + (float)testTimeout/6000);
  long timestart=System.currentTimeMillis();
  long timecurr=System.currentTimeMillis();
  while(timecurr < timestart + testTimeout)
    {
    logger.println("*************************************************");
    try{bin = udpl.receive();}  // get data from client
    catch(Exception ste){logger.ee("UDP listening socket threw Exception.", ste); return false;}
    if(bin==null) {System.err.println("UDPSocket timed out waiting for client."); return false;}
    this.parseMessage(bin);
    timecurr=System.currentTimeMillis();
    }
  logger.println("testTimeout reached.");
  return true;
  } // end serverListen()


/**
 * Parse incoming UDP message.
 * Calls method specific to message type to complete parsing.
 * @param bin
 * @return 
 */
public boolean parseMessage(byte[] bin)
  {
  if(!this.checkMagic(bin)) return false;
  if(!this.checkSchemaVersion(bin)) return false;
  if(!this.getIdField(bin).equals("WSJT-X")) return false;
  this.messageType = (int)this.getMessageType(bin); // bin[11]
  switch(messageType)
    { // In/out is relative to WSJT-X client.
    case 0: this.messageType0(bin); break;  // heartbeat in/out.  Out is implemented.  Updates WSJTXStatus
    case 1: this.messageType1(bin); break;  // status out.  Produces log entry only.
    case 2: this.messageType2(bin); break;  // decode message out.  This is what populates ALL.TXT and CALLMAID.
    // clear in/out. Out when all messages eligible for reply are gone. In to clear the "Band Activity" and "Rx Frequency" windows.
    case 3: this.messageType3(bin); break;  // clear in and out are implented.  In is via button on WSTXStatus screen. Out merely logs.
    // 4: In. Initiates a QSO.
    case 5: this.messageType5(bin); break;  // Out. Message to server when user clicks "OK" to "Log QSO".  Merely logs.
    case 6: this.messageType6(bin); break;  // Close In/Out.  Both implented.  In is sent in response to button onn WSJTXStatus. Out causes UDP Server to shutdown.
    // 7: In. Send decode messages for all messages in "Band Activity" window. Not implemented.
    // 8: In. Causes WSJT-X client to stop sending messages.  Not implemented.
    // 9: In. Sets the free text message.  Not implemented.
    case 10: this.messagetype10(bin); break;  // WSPR decode. Implemented.  Currently only logs.
    // 11: In. Sets the maidenhead grid locator.  Not implemented.
    case 12: this.messageType12(bin); break; // Out. ADIF message in response to user "OK" to "Log QSO".  Merely logs.
    default:logger.e("ERROR: invalid message type=" + messageType + ". Code for message type=" + messageType);
    if(debugFlag)this.logRawMessage(bin);
    return false;
    }
  return true;
  }  // end parseMessage()





/**
 * Magic number is hex ad bc cb da in bytes 0-3
 * @param bin
 * @return 
 */
private boolean checkMagic(byte[] bin)
  {
  if(bin[0]==-83 && bin[1]==-68 && bin[2]==-53 && bin[3]==-38) return true;
  System.err.println("Bad magic");
  logger.e("UDP message Bad magic");
  return false;
  }


/**
 * Schema version is binary 0,0,0,2 in bytes 4-7
 * @param bin
 * @return 
 */
private boolean checkSchemaVersion(byte[] bin)
  {
  if(bin[4]==0 && bin[5]==0 && bin[6]==0 && bin[7]==2) return true;
  System.err.println("Bad schema version");
  logger.e("UDP message bad schema version");
  if(debugFlag)this.logRawMessage(bin);
  return false;
  }


/**
 * Message type is binary 0,0,0,<message type> in bytes 8-11
 * @param bin
 * @return 
 */
private byte getMessageType(byte[] bin)
  {
  if(!(bin[8]==0 && bin[9]==0 && bin[10]==0)) 
     {
     logger.e("Assumption that first three bytes of message type field are zero no longer holds true.");
     this.logRawMessage(bin);
     return (byte)-1;
     }
  return bin[11];
  }

/**
 * Length of id field is in bytes 12-15.  ID field follows.
 * In this case we assume that we need only the last byte, as our ID is always short.
 * ID should always be "WSJT-X" when receiving from that program.
 * @param bin
 * @return 
 */
private String getIdField(byte[] bin)
  {
  if(!(bin[12]==0 && bin[13]==0 && bin[14]==0)) 
    {
    logger.e("Assumption that first three bytes of id length field are zero no longer holds true.");
    this.logRawMessage(bin);
    return null;
    }
  int idLength=(int)bin[15];
  byte[] ba = new byte[idLength];
  String idString="";
  //System.out.print("ID in hex=");
  for(int x=0; x < idLength; x++)
    {
    idString += Byte.toString(bin[x + 16]);
    ba[x]=bin[x + 16];
    //System.out.print(bin[x + 16]);System.out.print(":");
    }
  String retval = new String(ba);
  if(!retval.equals("WSJT-X"))logger.e("UDP message bad ID field=" + retval);
  return retval;
  }




public void logRawMessage(byte[] bin)
  {
  String receivedString="";
  logger.print("Size=" + bin.length + ", Binary=");
  for(int x=0; x < bin.length; x++)
    {
    logger.print(Byte.toString(bin[x]));logger.print(":");
    if(bin[x] > 32 && bin[x] < 127) receivedString += (char)bin[x] + ":";
    else receivedString += String.format("%02X ", bin[x]) + ":";  // convert byte to hex
    }  // getData() returns byte[]
  logger.println("");
  String convertString=new String(bin); 
  logger.println("received byte to hex=" + receivedString + "\n" + "String=" + convertString);    
  }




private boolean serverCheckConnection(UDPSocket udpl, int initialTimeout, int testTimeout)
  {
  DatagramPacket packet;
  this.udps.setTimeout(initialTimeout);  
  if(debugFlag)System.out.println("Waiting for SYN from client.  Socket timeout set to " + (float)initialTimeout/60000 + " minutes."); 
  logger.println("Waiting for Syn from client. Timeout set to " + (float)initialTimeout/60000 + " minutes.");
  byte[] bin = udpl.receive();  // get Syn from client
  if(bin==null) {System.err.println("UDPSocket timed out waiting or client."); return false;}
  logger.println("Received " + bin.length + " bytes.");
  if(bin[0] != SYN[0]) // SYN character 
    {
    logger.e("Expected from client SYN but received " + bin[0]);
    return false;
    }
  if(debugFlag)System.out.println("Received SYN from client.  Sending ACK to address=" + udpl.remoteAddress + ", port=" + udpl.remotePort);
  logger.println("Received SYN from client.  Sending ACK to address=" + udpl.remoteAddress + ", port=" + udpl.remotePort);
  udps.setTimeout(testTimeout);  logger.println("Waiting for BEL from client to start test.  Socket timeout set to 2 seconds."); 
  logger.println("Waiting for BEL from client to start test. Timeout set to 2 seconds.");
  // flagme following line temporary test code.
  udpl.remoteAddress=udpl.packetReceive.getAddress(); udpl.remotePort=udpl.packetReceive.getPort();
  if(!this.utilsObj.sendExpect(udpl, udpl.remoteAddress, udpl.remotePort, ACK,BEL)) return false;
  /*
  //if(!udpl.send(bin,udpl.remoteAddress,udpl.remotePort))
  if(!udpl.send(bin)) // flagme  
    {
    logger.e("Failed to send ACK to client in response to received SYN.");
    return false;
    }
  
  bin = udpl.receive();
  if(bin==null) return false;
  logger.println("Received " + bin.length + " bytes.");
  if(bin[0] != 0x07) // BEL character 
    {
    logger.e("Expected from client BEL but received " + bin[0]);
    return false;
    }
  */
  logger.println("Received BEL from client.  Beginning test with address=" + udpl.remoteAddress + ", port=" + udpl.remotePort);
  logger.println("Received BEL from client.  Beginning test with address=" + udpl.remoteAddress + ", port=" + udpl.remotePort);
  return true;
  }  // end serverCheckConnection

 


/**
 * Header format:
 * Bytes 0-3: 32-bit unsigned integer magic number 0xadbccbda  Shows up in raw message log as hex=AD :BC :CB :DA 
 * Bytes 4-7: 32-bit unsigned integer schema number (currently 2  Shows up in raw message log as 00 :00 :00 :02 
 * Below message descriptions all begin with byte 8.
 */
byte[] messageHeaderBytes = {(byte)0xAD,(byte)0xBC,(byte)0xCB,(byte)0xDA,(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x02}; // bytes 0-7
byte[] messageTypeBytes = {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x00};  // bytes 8-11. replace messageTypeBytes[3] with message type number
byte[] messageIDBytes = {(byte)0x00, (byte)0x00, (byte)0x00, (byte)0x06, (byte)'W', (byte)'S', (byte)'J', (byte)'T', (byte)'-', (byte)'X'};  // bytes 12-21

/**
 * Heartbeat     Out/In    0                      quint32  bytes 8-11
 *                         Id (unique key)        utf8  bytes 12-21 when ID="WSJT-X"
 *                         Maximum schema number  quint32 bytes 22-25
 *                         version                utf8    bytes 26-29
 *                         revision               utf8    bytes 30-end (alpha)
 *
 *    The heartbeat  message shall be  sent on a periodic  basis every
 *    NetworkMessage::pulse   seconds   (see    below),   the   WSJT-X
 *    application  does  that  using the  MessageClient  class.   This
 *    message is intended to be used by servers to detect the presence
 *    of a  client and also  the unexpected disappearance of  a client
 *    and  by clients  to learn  the schema  negotiated by  the server
 *    after it receives  the initial heartbeat message  from a client.
 *    The message_aggregator reference server does just that using the
 *    MessageServer class. Upon  initial startup a client  must send a
 *    heartbeat message as soon as  is practical, this message is used
 *    to negotiate the maximum schema  number common to the client and
 *    server. Note  that the  server may  not be  able to  support the
 *    client's  requested maximum  schema  number, in  which case  the
 *    first  message received  from the  server will  specify a  lower
 *    schema number (never a higher one  as that is not allowed). If a
 *    server replies  with a lower  schema number then no  higher than
 *    that number shall be used for all further outgoing messages from
 *    either clients or the server itself.
 *
 *    Note: the  "Maximum schema number"  field was introduced  at the
 *    same time as schema 3, therefore servers and clients must assume
 *    schema 2 is the highest schema number supported if the Heartbeat
 *    message does not contain the "Maximum schema number" field.
 *
 *   This message type is client out/in, but I am supporting only out, i.e. WJTT-X out to me.
 * @param bin 
 */
private void messageType0(byte[] bin)
  {
  if(debugFlag)System.out.println("Message type=0, heartbeat");
  if(debugFlag)logger.tprintln("Message type=0, heartbeat");
  if(debugFlag)this.logRawMessage(bin);
  byte[] msba = new byte[4];
  msba[0]=bin[22]; msba[1]=bin[23]; msba[2]=bin[24]; msba[3]=bin[25];
  int schemaNumber=this.byteArrayToInteger(msba);
  msba[0]=bin[26]; msba[1]=bin[27]; msba[2]=bin[28]; msba[3]=bin[29];
  int versionNumber=this.byteArrayToInteger(msba);
  int revlength=bin.length-30;
  msba = new byte[revlength];
  for(int z=0; z < revlength; z++)msba[z]=bin[z+30];
  String revisionString = new String(msba);
  if(debugFlag)logger.println("message type=0(Heartbeat), max schema number=" + schemaNumber + ", version=" + versionNumber + ", revision=" + revisionString);
  } // end messageType0


/**
 * 
 * Status        Out       1                      quint32  bytes 8-11
 *                         Id (unique key)        utf8  bytes 16-21 when ID="WSJT-X"
 *                         Dial Frequency (Hz)    quint64 bytes 22-29
 *                         Mode                   utf8  mode length bytes 30-33, mode begins at 34
 *                         DX call                utf8  dx call length 4 bytes
 *                         Report                 utf8
 *                         Tx Mode                utf8
 *                         Tx Enabled             bool
 *                         Transmitting           bool
 *                         Decoding               bool
 *                         Rx DF                  qint32
 *                         Tx DF                  qint32
 *                         DE call                utf8
 *                         DE grid                utf8
 *                         DX grid                utf8
 *                         Tx Watchdog            bool
 *                         Sub-mode               utf8
 *                         Fast mode              bool
 *                         Special operation mode quint8
 *
 *    WSJT-X  sends this  status message  when various  internal state
 *    changes to allow the server to  track the relevant state of each
 *    client without the need for  polling commands. The current state
 *    changes that generate status messages are:
 *
 *      Application start up,
 *      "Enable Tx" button status changes,
 *      Dial frequency changes,
 *      Changes to the "DX Call" field,
 *      Operating mode, sub-mode or fast mode changes,
 *      Transmit mode changed (in dual JT9+JT65 mode),
 *      Changes to the "Rpt" spinner,
 *      After an old decodes replay sequence (see Replay below),
 *      When switching between Tx and Rx mode,
 *      At the start and end of decoding,
 *      When the Rx DF changes,
 *      When the Tx DF changes,
 *      When settings are exited,
 *      When the DX call or grid changes,
 *      When the Tx watchdog is set or reset.
 *
 *    The Special operation mode is  an enumeration that indicates the
 *    setting  selected  in  the  WSJT-X  "Settings->Advanced->Special
 *    operating activity" panel. The values are as follows:
 *
 *       0 -> NONE
 *       1 -> NA VHF
 *       2 -> EU VHF
 *       3 -> FIELD DAY
 *       4 -> RTTY RU
 *       5 -> FOX
 *       6 -> HOUND
 *
 */
private void messageType1(byte[] bin)
  {
  int ptr;
  if(debugFlag)System.out.println("Message type=1, status message.");
  if(debugFlag)logger.tprintln("Message type=1, status message.");
  if(debugFlag)this.logRawMessage(bin);
  // begin test code
  this.cptr=22;
 /**
 *   1=short. length 2. (there are none as off 12/4/22)
 *   2=String. Always variable length. 
 *   3=boolean. Always 1 byte.
 *   4=Double, 8 bytes.
 *   5=qtime. length 4. return int[]{hours,minutes,seconds}
 *   6=integer. length 4 bytes.
 *   7=long.  length 8 bytes.
 *   enum parseMode {BOOLEAN,SHORT,INTEGER,LONG,STRING,DOUBLE,QTIME};
 */
  long radioFreq = (Long)this.parseToken(bin,parseMode.LONG, "radioFreq");  //fixed length 8 bytes
  this.radiofrequency=(float)radioFreq/1000000;  // radiofreq in megahertz
  String modeString = (String)this.parseToken(bin,parseMode.STRING, "mode");  // var length
  String dxCallString= (String)this.parseToken(bin,parseMode.STRING, "dxCall");  // var length
  String reportString= (String)this.parseToken(bin,parseMode.STRING, "report");  //var length 
  String txmodeString= (String)this.parseToken(bin,parseMode.STRING, "txmode");  // var length
  boolean txenabled = (Boolean)this.parseToken(bin,parseMode.BOOLEAN, "txenable"); // fixed length 1 byte
  boolean transmitting = (Boolean)this.parseToken(bin,parseMode.BOOLEAN, "transmitting"); // fixed length 1 byte
  if(transmitting){this.rxtx="tx";  this.currentlyTransmitting=true;}
  else {this.rxtx="rx"; this.currentlyTransmitting=false;}
  boolean decoding = (Boolean)this.parseToken(bin,parseMode.BOOLEAN, "decoding");  // fixed lenth 1 byte
  int rxdf = (Integer)this.parseToken(bin,parseMode.INTEGER, "rxdf");  // fixed length 4 bytes
  int txdf = (Integer)this.parseToken(bin,parseMode.INTEGER, "txdf");  // fixed length 4 bytes
  String decallStr = (String)this.parseToken(bin,parseMode.STRING, "de call");  // var length
  String degrid = (String)this.parseToken(bin,parseMode.STRING, "de grid");  // var length
  String dxgrid = (String)this.parseToken(bin,parseMode.STRING, "dx grid");  // var length
  boolean txwatchdog = (Boolean)this.parseToken(bin,parseMode.BOOLEAN, "txwatchdog"); // fixed length 1 byte
  // The remainder of the message does not correspond to the published format.
  if(debugFlag)logger.println("Message type=1. radioFreq=" + radioFreq + ", mode=" + modeString + ", dxcall=" + dxCallString + ", report=" + reportString + ", txmode=" + txmodeString + ", txenable=" + txenabled + ", transmitting=" + transmitting + ", decoding=" + decoding + ", rxdf=" + rxdf + ", txdf=" + txdf + ", de call=" + decallStr + ", de grid=" + degrid + ", dx grid=" + dxgrid + ", txwatchdog=" + txwatchdog);
  }  // end messageType1()



/**
 * *
 * Decode        Out       2                      quint32
 *                         Id (unique key)        utf8   bytes 16-21 when ID="WSJT-X"
 *                         New                    bool
 *                         Time                   QTime  reference says that it is UINT32.  Time in milliseconds since midnight.  No date.
 *                         snr                    qint32
 *                         Delta time (S)         float (serialized as double)  I'm guessing 8 bytes since as double is UINT64
 *                         Delta frequency (Hz)   quint32  Offset audio frequency.
 *                         Mode                   utf8
 *                         Message                utf8
 *                         Low confidence         bool
 *                         Off air                bool
 *
 *      The decode message is sent when  a new decode is completed, in
 *      this case the 'New' field is true. It is also used in response
 *      to  a "Replay"  message where  each  old decode  in the  "Band
 *      activity" window, that  has not been erased, is  sent in order
 *      as a one of these messages  with the 'New' field set to false.
 *      See  the "Replay"  message below  for details  of usage.   Low
 *      confidence decodes are flagged  in protocols where the decoder
 *      has knows that  a decode has a higher  than normal probability
 *      of  being  false, they  should  not  be reported  on  publicly
 *      accessible services  without some attached warning  or further
 *      validation. Off air decodes are those that result from playing
 *      back a .WAV file.
 *
 */
private void messageType2(byte[] bin)
  {
  if(debugFlag)System.out.println("Message type=2, decode.");
  if(debugFlag)logger.tprintln("Message type=2, decode.");
  if(debugFlag)this.logRawMessage(bin);
  this.cptr=22;
  boolean newFlag= (Boolean)this.parseToken(bin, parseMode.BOOLEAN, "newFlag");  // byte 22
  // qtime is bytes 23-26
  byte[] qtimeBytes = this.getSubArray(bin, 23, 4);  // for response to CQ. bytes 23-26
  String bs = Arrays.toString(qtimeBytes);
  String[] bsa = bs.substring(1, bs.length() - 1).split(",");
  byte[] bytes = new byte[bsa.length];
  for (int i=0, len=bytes.length; i<len; i++) 
    {
    bytes[i] = Byte.parseByte(bsa[i].trim());   
    }
  String qtimeStr = new String(bytes);
  if(debugFlag)logger.println("qtime ************** =" + qtimeStr + " of length=" + qtimeStr.length()); 
  if(debugFlag)this.logRawMessage(qtimeBytes);
  LocalDateTime ldt = (LocalDateTime)this.parseToken(bin, parseMode.QTIME, "Qtime");  // messagerecord.ldt.  bytes 23-26
  String qtStr = ldt.format(this.dateTimeFormat1);  // format LocalDateTime to String as needed for logging. ldt is assigned to messagerecord.ldt
  int snr=(Integer)this.parseToken(bin, parseMode.INTEGER, "SNR");  // messagerecor.RSSI. bytes 27-30
  double deltaTime = (Double)this.parseToken(bin, parseMode.DOUBLE, "deltaTime");   // deltaTime is fixed 8 bytes.  messagerecord.timeOffset
  String deltaTimeStr = String.format("%,.1f", deltaTime); 
  byte[] deltaTimeBytes = this.getSubArray(bin, 31, 8);  // bytes 31-38
  int deltaFreq= (Integer)this.parseToken(bin, parseMode.INTEGER, "deltaFreq");// deltaFreq is fixed 4 bytes.  messagerecord.AUDIOFREQUENCY. bytes 39-42
  String modeString = (String)this.parseToken(bin,parseMode.STRING, "mode");  // var length .  messagerecord.MODE
  int modeInt = modeString.charAt(0); // always a string of length one.
  if(modeInt==126) modeString="FT8";  // ascii char ~
  else if(modeInt==43) modeString="FT4"; // ascii char +
  else logger.e("Mode is not FT8 or FT4");
  String message= (String)this.parseToken(bin, parseMode.STRING, "message");   // messagerecord.MESSAGE
  //String[] messageArray=message.split(" ");
  String[] messageArray = this.tokenize(message);
  boolean lowConfidence= (Boolean)this.parseToken(bin, parseMode.BOOLEAN, "lowConfidence"); // used to initiate response to CQ
  boolean offAir= (Boolean)this.parseToken(bin, parseMode.BOOLEAN, "offAir");
  if(debugFlag)logger.println("MessageType2. newFlag=" + newFlag + ", qtime=" + "GMT " + qtStr + ", snr=" + snr + ", deltaTime=" + deltaTimeStr + ", deltaAudioFreq=" + deltaFreq + ", mode=" + modeString + ", message=" + message + ", lowConfidence=" + lowConfidence + ", offAir=" + offAir);
  DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
  Calendar cal = Calendar.getInstance();
  if(debugFlag)logger.println("Systemmillis=" + System.currentTimeMillis() + "," + dateFormat.format(cal.getTime()));
  //this.messagerec=new MessageRecord(this.logger, ldt, 
  //  this.radiofrequency, this.rxtx, modeString, (float)deltaTime, snr, deltaFreq, 
  //  messageArray, Common.messTypeString);
  this.messagerec = this.popAllTxtObj.createMessageRecord(ldt, 
    this.radiofrequency, this.rxtx, modeString, (float)deltaTime, snr, deltaFreq, 
    messageArray, Common.messTypeString, lowConfidence, qtimeBytes, deltaTimeBytes);  
  } // end messageType2()



/*
 * Clear         Out/In    3                      quint32  // Message type
 *                         Id (unique key)        utf8     // bytes 16-21 when ID="WSJT-X"
 *                         Window                 quint8 (In only)
 *
 *      This message is  sent when all prior "Decode"  messages in the
 *      "Band Activity"  window have been discarded  and therefore are
 *      no long available for actioning  with a "Reply" message. It is
 *      sent when the user erases  the "Band activity" window and when
 *      WSJT-X  closes down  normally. The  server should  discard all
 *      decode messages upon receipt of this message.
 *
 *      It may  also be  sent to  a WSJT-X instance  in which  case it
 *      clears one or  both of the "Band Activity"  and "Rx Frequency"
 *      windows.  The Window  argument  can be  one  of the  following
 *      values:
 *
 *         0  - clear the "Band Activity" window (default)
 *         1  - clear the "Rx Frequency" window
 *         2  - clear both "Band Activity" and "Rx Frequency" windows
*/
private void messageType3(byte[] bin)
  { 
  if(debugFlag)System.out.println("Message type=3, Clear.");
  logger.tprintln("Message type=3, Clear.");
  this.logRawMessage(bin);
  // byte 22 is first byte after ID
  this.cptr=22;
  // There is no body to this message.  Reception of message type=3 is the entirety of the message.
  logger.tprintln("Received message type=3. Clear. Maybe Charlie should provide a notification to the user?");
  return;
  }

public boolean messageType3Transmit()
  {
  if(debugFlag)System.out.println("messageType3Transmit()");
  logger.tprintln("Begin messageType3Transmit()");
  //byte[] mtype = {(byte)0x00,(byte)0x00,(byte)0x00,(byte)0x03};
  ByteArrayOutputStream output = new ByteArrayOutputStream();
  try
    {
    output.write(this.messageHeaderBytes); // bytes 0-7
    byte[] typel = new byte[4]; typel=this.messageTypeBytes; typel[3]=0x03; output.write(typel); // bytes 8-11 message type
    output.write(this.messageIDBytes);  // bytes 12-21
    byte window = 0x02;  // clear both band activity and rx frequency windows.
    output.write(window);
    }
  catch(IOException ioe){logger.ee("Failed to append to message for messageType3Transmit", ioe); return false;}
  byte[] messageOut = output.toByteArray();
  return this.udps.send(messageOut);
  } // end messageType3Transmit()




/* Reply         In        4                      quint32
 *                         Id (target unique key) utf8
 *                         Time                   QTime
 *                         snr                    qint32
 *                         Delta time (S)         float (serialized as double)
 *                         Delta frequency (Hz)   quint32
 *                         Mode                   utf8
 *                         Message                utf8
 *                         Low confidence         bool
 *                         Modifiers              quint8
 *
 *      In order for a server  to provide a useful cooperative service
 *      to WSJT-X it  is possible for it to initiate  a QSO by sending
 *      this message to a client. WSJT-X filters this message and only
 *      acts upon it  if the message exactly describes  a prior decode
 *      and that decode  is a CQ or QRZ message.   The action taken is
 *      exactly equivalent to the user  double clicking the message in
 *      the "Band activity" window. The  intent of this message is for
 *      servers to be able to provide an advanced look up of potential
 *      QSO partners, for example determining if they have been worked
 *      before  or if  working them  may advance  some objective  like
 *      award progress.  The  intention is not to  provide a secondary
 *      user  interface for  WSJT-X,  it is  expected  that after  QSO
 *      initiation the rest  of the QSO is carried  out manually using
 *      the normal WSJT-X user interface.
 *
 *      The  Modifiers   field  allows  the  equivalent   of  keyboard
 *      modifiers to be sent "as if" those modifier keys where pressed
 *      while  double-clicking  the  specified  decoded  message.  The
 *      modifier values (hexadecimal) are as follows:
 *
 *          no modifier     0x00
 *          SHIFT           0x02
 *          CTRL            0x04  CMD on Mac
 *          ALT             0x08
 *          META            0x10  Windows key on MS Windows
 *          KEYPAD          0x20  Keypad or arrows
 *          Group switch    0x40  X11 only
 */
public boolean messageType4Transmit(MessageRecord mrl)
  {
  if(debugFlag)System.out.println("messageType4Transmit(). Response to CQ");
  logger.tprintln("Begin messageType4Transmit() response to CQ.");
  // Create a ByteArrayOutputStream into which we concatenate all the byte arrays.
  ByteArrayOutputStream output = new ByteArrayOutputStream();
  try
    {
    output.write(this.messageHeaderBytes); // bytes 0-7
    byte[] typel = new byte[4]; typel=this.messageTypeBytes; typel[3]=0x04; output.write(typel); // bytes 8-11 message type 4 (quint32)
    output.write(this.messageIDBytes);  // bytes 12-21  (utf8)
    output.write(mrl.qtimeBytes);  // qtime.  bytes 22-25
    int snri = mrl.rssi;  // snr
    byte[] snrb = this.intToByteArray(snri);
    output.write(snrb);
    logger.println("After snr"); this.logRawMessage(output.toByteArray());
    byte[] deltaTimeb = mrl.deltaTimeBytes; output.write(deltaTimeb);
    logger.println("deltaTime " + deltaTimeb.length + " bytes follows:"); this.logRawMessage(deltaTimeb);
    logger.println("After deltaTime"); this.logRawMessage(output.toByteArray());
    byte[] deltaFreqb = this.intToByteArray(mrl.audioFrequency); output.write(deltaFreqb);
    logger.println("After deltaFreq=" + mrl.audioFrequency);  this.logRawMessage(output.toByteArray());
    String modeString = mrl.mode;
    byte[] modeb = new byte[5]; modeb[0]=(byte)0; modeb[1]=(byte)0; modeb[2]=(byte)0; modeb[3]=(byte)1;  // length of following string
    if(modeString.equals("FT8")) modeb[4]=(byte)126;  // ascii ~
    else if(modeString.equals("FT4")) modeb[4]=(byte)43; // ascii +
    else {logger.e("Invalid mode=" + modeString + " in messageType4Transmit()"); return false;}
    output.write(modeb);
    String message = mrl.concatMessage(mrl.message);
    byte[] messageb = this.encodeString(message); output.write(messageb);
    logger.println("after message cq response follows:");
    output.write(this.encodeBoolean(mrl.lowconfidence));
    output.write((byte)0);  // modifier byte
    }
  catch(IOException ioe){logger.ee("Failed to append to message for messageType4Transmit", ioe); return false;}
  byte[] messageOut = output.toByteArray();
  logger.println("Final byte array output from messageType4Transmit:"); this.logRawMessage(messageOut);
  boolean retval = this.udps.send(messageOut);
  if(retval)logger.tprintln("messageType4Transmit socket send successful.");
  else logger.e("messageType4Transmit socket send failed.");
  return retval;
  } // messageType4Transmit()






/**
 *QSO Logged    Out       5                      quint32
 *                         Id (unique key)        utf8  bytes 16-21 when ID="WSJT-X"
 * It appears that there is an undocumented value here of a total of five bytes of 0x00.  I suspect that the first four are the length and the fifth is the value(length 1)
 *                         Date & Time Off        QDateTime  A QDateTime object encodes a calendar date and a clock time (a "datetime").
 *                                                qdate us qint64
 *                         DX call                utf8
 *                         DX grid                utf8
 *                         Tx frequency (Hz)      quint64
 *                         Mode                   utf8
 *                         Report sent            utf8
 *                         Report received        utf8
 *                         Tx power               utf8
 *                         Comments               utf8
 *                         Name                   utf8
 *                         Date & Time On         QDateTime This smells like a Unix date, 64 bit long.
 *                         Operator call          utf8
 *                         My call                utf8
 *                         My grid                utf8
 *                         Exchange sent          utf8
 *                         Exchange received      utf8
 *
 *      The  QSO logged  message is  sent  to the  server(s) when  the
 *      WSJT-X user accepts the "Log  QSO" dialog by clicking the "OK"
 *      button.
 */
private boolean messageType5(byte[] bin)
  { 
  if(debugFlag)System.out.println("Message type=5, log QSO.");
  logger.tprintln("Message type=5, log QSO.");
  this.logRawMessage(bin);
  // byte 22 is first byte after ID
  this.cptr=22;
  //this.cptr +=5;  // to skip over the suspected mystery null value following the ID field.
  /**
 *   1=short. length 2. (there are none as off 12/4/22)
 *   2=String. Always variable length. 
 *   3=boolean. Always 1 byte.
 *   4=Double, 8 bytes
 *   5=qtime. length 4. return int[]{hours,minutes,seconds}
 *   6=integer. length 4 bytes.
 *   7=long.  length 8 bytes.
 */
  LocalDateTime dtoff = (LocalDateTime)this.parseToken(bin, parseMode.QDATETIME,"dtoff");  // 13 bytes
  //Long dtoffDate = (Long)this.parseToken(bin, parseMode.LONG, "dtoffDate");  // flagme debug code
  // dtoffTime = (int[])this.parseToken(bin, parseMode.QTIME, "dtoffTime");
  String dxcall = (String)this.parseToken(bin,parseMode.STRING, "dxcall");  // var length
  String dxgrid= (String)this.parseToken(bin, parseMode.STRING, "dxgrid");  // var length
  long txFreq = (Long)this.parseToken(bin,parseMode.LONG, "txFreq");  //fixed length 8 bytes
  String modeString= (String)this.parseToken(bin,parseMode.STRING, "mode");  // var length
  String reportsentString= (String)this.parseToken(bin,parseMode.STRING, "reportsent");  //var length
  String reportrcvString= (String)this.parseToken(bin,parseMode.STRING, "reportrcv");  // var length
  String txPowerString= (String)this.parseToken(bin, parseMode.STRING, "txPower");  // var length
  String comments= (String)this.parseToken(bin,parseMode.STRING, "comments");  // var length
  String name= (String)this.parseToken(bin, parseMode.STRING, "name");  // var length
  LocalDateTime dton = (LocalDateTime)this.parseToken(bin,parseMode.QDATETIME, "date/time on");  //fixed length 8 bytes
  String operatorCall= (String)this.parseToken(bin,parseMode.STRING, "operatorCall");  // var length
  String mycall= (String)this.parseToken(bin,parseMode.STRING, "mycall");  // var length
  String mygrid= (String)this.parseToken(bin, parseMode.STRING, "mygrid");  // var length
  String exchangeSent= (String)this.parseToken(bin, parseMode.STRING, "exchangeSent");  // var length
  String exchangeRcv= (String)this.parseToken(bin,parseMode.STRING, "exchangeRcv");  // var length
  logger.println("MessageType5. dxcall=" + dxcall + ", dxgrid=" + dxgrid + ", txFreq=" + txFreq 
            + ", mode=" + modeString + ", mode=" + modeString + ", reportsent=" + reportsentString 
            + ", reportrcv=" + reportrcvString + ", txpower=" + txPowerString + ", comments=" + comments
            + ", name=" + name + ", operatorCall= " + operatorCall
            + ", mycall=" + mycall + ", mygrid=" + mygrid + ", exchangeSent=" 
            + exchangeSent + ", exchangeRcv=" + exchangeRcv);
  DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
  Calendar cal = Calendar.getInstance();
  logger.println("date/time=" + dateFormat.format(cal.getTime()));
  return true;
  } // end messageType5()
  



/**Close         Out/In       6                      quint32
 *                         Id (unique key)        utf8
 *
 *      Close is sent by a client immediately prior to it shutting
 *      down gracefully.  When sent by  a server it requests the target
 *      client to close down gracefully.
 */
private boolean messageType6(byte[] bin)
  {
  if(debugFlag)System.out.println("Message type=6, close of WSJT-X");
  logger.tprintln("Message type=6, close of WSJT-X");
  this.logRawMessage(bin);
  return true;
  }

public boolean messageType6Transmit()
  {
  if(debugFlag)System.out.println("messageType6Transmit()");
  logger.tprintln("Begin messageType6Transmit()");
  ByteArrayOutputStream output = new ByteArrayOutputStream();
  try
    {
    output.write(this.messageHeaderBytes); // bytes 0-7
    byte[] typel = new byte[4]; typel=this.messageTypeBytes; typel[3]=0x06; output.write(typel); // bytes 8-11.  message type
    output.write(this.messageIDBytes);  // bytes 12-21
    }
  catch(IOException ioe){logger.ee("Failed to append to message for messageType6Transmit", ioe); return false;}
  byte[] messageOut = output.toByteArray();
  return this.udps.send(messageOut);
  }







/* WSPRDecode    Out       10                     quint32
 *                         Id (unique key)        utf8
 *                         New                    bool
 *                         Time                   QTime  reference says that it is UINT32.  Time in milliseconds since midnight.  No date.
 *                         snr                    qint32
 *                         Delta time (S)         float (serialized as double)
 *                         Frequency (Hz)         quint64
 *                         Drift (Hz)             qint32
 *                         Callsign               utf8
 *                         Grid                   utf8
 *                         Power (dBm)            qint32
 *                         Off air                bool
 *
 *      The decode message is sent when  a new decode is completed, in
 *      this case the 'New' field is true. It is also used in response
 *      to  a "Replay"  message where  each  old decode  in the  "Band
 *      activity" window, that  has not been erased, is  sent in order
 *      as  a one  of  these  messages with  the  'New'  field set  to
 *      false.  See   the  "Replay"  message  below   for  details  of
 *      usage. The off air field indicates that the decode was decoded
 *      from a played back recording.
 */
private boolean messagetype10(byte[] bin)
  {
  if(debugFlag)System.out.println("Message type=10, WSPR.");
  logger.tprintln("Message type=10, WSPR.");
  this.logRawMessage(bin);
  // byte 22 is first byte after ID.  ID field is checked in calling method.
  this.cptr=22;
  /**
 *   1=short. length 2. (there are none as off 12/4/22)
 *   2=String. Always variable length. 
 *   3=boolean. Always 1 byte.
 *   4=Double, 
 *   5=qtime. length 4. return int[]{hours,minutes,seconds}
 *   6=integer. length 4 bytes.
 *   7=long.  length 8 bytes.
 */
  cptr=22;
  boolean newflag = (Boolean)this.parseToken(bin,parseMode.BOOLEAN, "new"); // boolean fixed length 1 byte
  LocalDateTime qtime = (LocalDateTime) this.parseToken(bin,parseMode.QTIME, "qtime");
  String qtStr = qtime.format(this.dateTimeFormat1);
  int snr = (Integer)this.parseToken(bin,parseMode.INTEGER,"SNR");
  double deltatime = (Double)this.parseToken(bin, parseMode.DOUBLE, "delta_time");
  long frequency = (Long)this.parseToken(bin, parseMode.LONG, "Frequency");  // Radio frequency, not audio.
  int drift = (Integer)this.parseToken(bin, parseMode.INTEGER,"drift_hz");
  String callsign = (String)this.parseToken(bin, parseMode.STRING, "callsign");
  String grid = (String)this.parseToken(bin, parseMode.STRING, "grid");
  int power = (Integer)this.parseToken(bin, parseMode.INTEGER, "power");
  boolean offair = (Boolean)this.parseToken(bin, parseMode.BOOLEAN, "offair");
  String[] message = new String[3]; message[0]="CQ"; message[1]=callsign; message[2]=grid;
  if(debugFlag)System.out.println("WSPR date/time=" + qtStr + ", SNR=" + snr + ", deltatime=" 
           + deltatime + ", frequency=" + frequency + ", drift=" + drift 
           + ", callsign=" + callsign + ", grid=" + grid + ", power=" + power 
           + ", offair=" + offair);
  this.messagerec = this.popAllTxtObj.createMessageRecord(qtime, 
    this.radiofrequency, this.rxtx, "WSPR", (float)deltatime, snr, drift, 
    message, Common.messTypeString, false, null, null);  
  return true;  
  }






/** Logged ADIF    Out      12                     quint32
 *                         Id (unique key)        utf8
 *                         ADIF text              utf8
 *
 *      The  logged ADIF  message is  sent to  the server(s)  when the
 *      WSJT-X user accepts the "Log  QSO" dialog by clicking the "OK"
 *      button. The  "ADIF text" field  consists of a valid  ADIF file
 *      such that  the WSJT-X  UDP header information  is encapsulated
 *      into a valid ADIF header. E.g.:
 *
 *          <magic-number><schema-number><type><id><32-bit-count>  # binary encoded fields
 *          # the remainder is the contents of the ADIF text field
 *          <adif_ver:5>3.0.7
 *          <programid:6>WSJT-X
 *          <EOH>
 *          ADIF log data fields ...<EOR>
 *
 *      Note that  receiving applications can treat  the whole message
 *      as a valid ADIF file with one record without special parsing.
 */
private boolean messageType12(byte[] bin)
  {
  if(debugFlag)System.out.println("Message type=12, log of QSO OK.");
  logger.tprintln("Message type=12, log of QSO OK.");
  this.logRawMessage(bin);
  // byte 22 is first byte after ID
  this.cptr=22;
  // types: 1=Integer, 2=String, 3=Boolean, 4=Long
  cptr += 5; // skip over the bytes 00 :00 :01 :D:0A 
  byte[] ba = this.getSubArray(bin, cptr, bin.length - cptr);
  String adifStr = new String(ba);
  logger.println("ADIF=" + adifStr);
  int tokenindexStart = adifStr.indexOf("<call:");
  String tokenLengthStr = adifStr.substring(tokenindexStart + 6, tokenindexStart + 7);
  if(debugFlag)logger.println("start=" + tokenindexStart + ", length=" + tokenLengthStr);
  int tokenLength = Integer.valueOf(tokenLengthStr);
  int tokenindexEnd = tokenindexStart + tokenLength;
  String callsign = adifStr.substring(tokenindexStart + 8, tokenindexStart + 8 + tokenLength);
  logger.println("callsign=" + callsign + " of length=" + callsign.length());
  // Set worked flag in CALLMAID record.
  int retval = Common.dbAllTxt.updateJDBC("CALLMAID","WORKED=true", "CALLSIGN='" + callsign + "'");
  // Write to ADIF.txt
  FileWriter fw;
  try{fw = new FileWriter("ADIF.txt", true);}
  catch(IOException ioe){logger.ee("Unable to open ADIF.txt for append.", ioe); return false;}
  try{fw.append(new String(ba)); fw.close();}
  catch(IOException ioe){logger.ee("Unable to append to ADIF.txt", ioe); return false;}
  String notStr="";
  if(retval != 1)
    {
    notStr = " not";
    JOptionPane.showMessageDialog(null, "Callsign update ERROR", "Failed to mark callsign record as worked.", JOptionPane.ERROR_MESSAGE);
    }
  JOptionPane.showMessageDialog(null, "QSO Logged", "Callsign " + callsign + notStr + " marked worked.", JOptionPane.INFORMATION_MESSAGE);
  return true;
  }


/**
 * Positive integers only! 
 * byte array to integer, big endian
 * @return 
 */
private int byteArrayToInteger(byte[] ba)
  {
  int multiplier = 256;
  int ui; // unsigned integer
  double retval=0;
  for(int x=ba.length -1; x >=0; x--)
    {
    ui=ba[x]&0xFF; // convert signed byte to unsigned int.
    retval+=(double)ui * Math.pow((double)256, (double)(ba.length -1 -x));
    //logger.println("ba[x]=" + ui + ", multiplier=" + Math.pow(256,(ba.length -1 - x)));
    }
  //logger.println("byteArrayToInteger=" + retval);
  return (int)retval;
  }

/**
 * Get subarray of ba[] from byte start for cnt bytes.
 * @param ba
 * @param start
 * @param cnt
 * @return 
 */
private byte[] getSubArray(byte[] ba, int start, int cnt)
  {
  if(cnt > 1000)
    {
    logger.e("getSubArray requested > 1000 bytes of data. start=" + start + ", cnt=" + cnt);
    this.logRawMessage(ba);
    return null;
    }
  if(cnt==0) return null;
  if(ba==null)logger.e("Null byte array passed to getSubArray().");
  byte[] bl = new byte[cnt];
  for(int x=0; x < cnt; x++)bl[x]=ba[start + x];
  return bl;
  }



private long byteArrayToLong(byte[] ba)
  {
  long value = 0l;
  // Iterating through for loop
  for (byte b : ba) 
    {
    // Shifting previous value 8 bits to right and
    // add it with next value
    value = (value << 8) + (b & 255);
    }
  return value;
  }


private double byteArrayToDouble(byte[] ba)
  {
  //convert 8 byte array to double
  int start=0;
  int i = 0;
  int len = 8;
  int cnt = 0;
  byte[] tmp = new byte[len];
  for (i = start; i < (start + len); i++) 
    {
    tmp[cnt] = ba[i];
    //logger.println(java.lang.Byte.toString(arr[i]) + " " + i);
    cnt++;
    }
  long accum = 0;
  i = 0;
  for ( int shiftBy = 0; shiftBy < 64; shiftBy += 8 ) 
    {
    accum |= ( (long)( tmp[i] & 0xff ) ) << shiftBy;
    i++;
    }
  return Double.longBitsToDouble(accum);  
  }


private byte[] reverseByteArray(byte[] ba)
  {
  byte[] bt = new byte[ba.length];
  for(int x=0; x < ba.length; x++)bt[ba.length - 1 - x]=ba[x];
  return bt;
  }


/**
 * Reads byte[] of UDP socket read, start at class variable int ptr, reads len bytes, and decodes according to int type.
 * Utilizes this.cptr to keep track of pointer into byte[] uline.
 * Length of 0 means read the length from the next 4 bytes.
 * Types: 
 *   1=short. length 2. (there are none as off 12/4/22)
 *   2=String. Always variable length. 
 *   3=boolean. Always 1 byte.
 *   4=Double, 
 *   5=qtime. length 4. return int[]{hours,minutes,seconds}
 *   6=integer. length 4 bytes.
 *   7=long.  length 8 bytes.
 *   enum parseMode {BOOLEAN,SHORT,INTEGER,LONG,STRING,DOUBLE,QTIME,QDATETIME};
 * @param uline
 * @param len
 * @param name
 * @return 
 */
private Object parseToken(byte[] uline, parseMode lpm, String name)
  {
  Object retval=null;
  byte[] bx;
  int lint=-1;
  int tokenlen=0;
  ByteBuffer wrapped;
  if(lpm==lpm.STRING)
    {
    bx =this.getSubArray(uline, this.cptr, 4);
    if(bx==null){logger.e("parseToken(),getSubArray() for name=" + name + "returned null."); return null;}
    tokenlen=this.byteArrayToInteger(bx);
    //for(int t=0; t < bx.length; t++)logger.println("parseToken().getSubarray[t]=" + bx[t]);
    // In the unique case wherein a messagetype=1 follows soon after a completed QSO; 
    // the dx grid variable is transmitted with a length of 0xFF,0xFF,0xFF,0xFF followed by a single 0x00.
    if(bx[0]==-1 && bx[1]==-1 && bx[2]==-1 && bx[3]==-1){if(debugFlag)logger.println("Converting FFFFFFFF to 0"); tokenlen=0;}
    this.cptr += 4;
    }

  switch(lpm)
    {
    case SHORT: // short is 16bits/2bytes, integer is 32bits/4bytes, long is 64bits/8bytes
        tokenlen=2;
        bx = this.getSubArray(uline, this.cptr,tokenlen);
        if(bx==null) {logger.e("parseToken(int) failed. byte array="); this.logRawMessage(uline); return null;}
        wrapped = ByteBuffer.wrap(bx); // big-endian by default
        short shortnum = wrapped.getShort(); 
        Short shit=Short.valueOf(shortnum);
        retval = (Object)shit;
        if(debugFlag)logger.println("parseToken name=" + name + ", short value=" + shit);
        break;
        
    case INTEGER:  // integer is 32bits/4 bytes.
        tokenlen=4;
        bx = this.getSubArray(uline, this.cptr,tokenlen);
        if(bx==null) {logger.e("parseToken(int) failed. byte array="); this.logRawMessage(uline); return null;}
        wrapped = ByteBuffer.wrap(bx); // big-endian by default
        int intnum = wrapped.getInt(); 
        Integer shint=Integer.valueOf(intnum);
        retval = (Object)shint;
        if(debugFlag)logger.println("parseToken name=" + name + ", integer value=" + shint);
        break;
    
    case LONG: // long is 64bits/8bytes    
        tokenlen=8;
        bx = this.getSubArray(uline, this.cptr,tokenlen);
        if(bx==null) {logger.e("parseToken(int) failed. byte array="); this.logRawMessage(uline); return null;}
        wrapped = ByteBuffer.wrap(bx); // big-endian by default
        long longnum = wrapped.getLong(); 
        Long shlong=Long.valueOf(longnum);
        retval = (Object)shlong;
        if(debugFlag)logger.println("parseToken name=" + name + ", long value=" + shlong);
        break; 
 
    case STRING: 
      String xString;
      if(tokenlen==0)xString="";
      else 
        {
        bx=this.getSubArray(uline, this.cptr,tokenlen);
        if(bx==null) {logger.e("parseToken(String) failed. byte array="); this.logRawMessage(uline); return null;}
        xString= new String(bx);
        }
      retval = (Object)xString;
      if(debugFlag)logger.println("parseToken name=" + name + ", value=" + xString);
      break;
      
    case BOOLEAN: // boolean is assumed to be one byte
      tokenlen=1;
      boolean boolt = false;
      int boolint=uline[cptr];
      if(boolint==1)boolt=true;
      retval = (Boolean)boolt;
      if(debugFlag)logger.println("parseToken name=" + name + ", value=" + boolt);
      break;
      
    case DOUBLE: // 8 bytes to double
      tokenlen=8;
      double dx;
      bx = this.getSubArray(uline, this.cptr,tokenlen);
      if(bx==null) {logger.e("parseToken(Double) failed. byte array="); this.logRawMessage(uline); return null;}
      bx = this.reverseByteArray(bx);
      dx = this.byteArrayToDouble(bx);
      if(debugFlag)logger.println("parseToken name=" + name + ", value=" + dx);
      Double Dx = Double.valueOf(dx);
      retval=(Object)Dx;
      break;
      
    case QTIME: // qtime is 4 bytes time since midnight.  No date. The time returned from WSJT-X is GMT.
      tokenlen=4;
      bx=this.getSubArray(uline, this.cptr,tokenlen);
      if(bx==null) {logger.e("parseToken(qtime) failed. byte array="); this.logRawMessage(uline); return null;}
      int qtime=this.byteArrayToInteger(this.getSubArray(bx, 0, 4));  
      if(debugFlag)logger.println("Integer value of qtime=" + qtime);
      LocalDateTime ldttime = this.convertQtimeToLocalDateTime(qtime);  // returns a LocalDateTime object.
      if(debugFlag)logger.println("parseToken name=" + name + ", value=" + ldttime.format(dateTimeFormat1));
      retval = ldttime;
      break;
      
    case QDATETIME:
  //         QDate      qint64    Julian day number
  //           QTime      quint32   Milli-seconds since midnight
  //           timespec   quint8    0=local, 1=UTC, 2=Offset from UTC
  //                                                 (seconds)
  //                                3=time zone
      tokenlen=13;
      bx=this.getSubArray(uline, this.cptr,tokenlen);
      if(debugFlag)System.out.println("Hello from QDATETIME. Currently we are just checking to see of token is of length=13.");
      if(bx==null) {logger.e("parseToken(qdatetime) failed. byte array="); this.logRawMessage(uline); return null;}
      byte[] bd = new byte[8]; for(int x=0; x < 8; x++)bd[x]=uline[cptr + x]; // date
      byte[] bt = new byte[4]; for(int x=0; x < 4; x++)bt[x]=uline[cptr + 8 + x]; // time
      if(bt==null) {logger.e("parseToken(QDATETIME) failed. qtime portion byte array"); this.logRawMessage(uline); return null;}
      if(bd==null) {logger.e("parseToken(QDATETIME) failed. qdate portion byte array"); this.logRawMessage(uline); return null;}
      byte bz = uline[cptr + 12];
      wrapped = ByteBuffer.wrap(bx); // big-endian by default
      long longqdate = wrapped.getLong(); 
      if(debugFlag)logger.println("parseToken QDATETIME date julian integer =" + longqdate + ", timezone=" + bz);
      // Convert integer julian of date (double dx) to a GregorianCalendar.
      int[] datePieces = this.tuObj.julianToGregorian((double)longqdate);
      GregorianCalendar qdategc = new GregorianCalendar(datePieces[0], datePieces[1] - 1, datePieces[2]); // year,month,day
      Date qdatedt = qdategc.getTime();
      if(debugFlag)logger.println("QDATETIME qdate=" + this.sdf.format(qdatedt));
      // Convert qtime to integer number of milliseconds since midnight.
      int qtimeint=this.byteArrayToInteger(this.getSubArray(bt, 0, 4));  
      // Add qtime to qdate, i.e. add qtimeint to qdategc.
      qdategc.add(Calendar.MILLISECOND, qtimeint);
      qdatedt = qdategc.getTime();
      if(debugFlag)logger.println("QDATETIME qdate + qtime=" + this.sdf.format(qdatedt));
      if(debugFlag)logger.println("parseToken(QDATETIME) name=" + name + ", value=" + this.sdf.format(qdatedt));
      LocalDateTime retldt = LocalDateTime.of(qdategc.get(Calendar.YEAR), qdategc.get(Calendar.MONTH) + 1,
              qdategc.get(Calendar.DAY_OF_MONTH), qdategc.get(Calendar.HOUR_OF_DAY), qdategc.get(Calendar.MINUTE));
      retval = retldt;
      break;
      
    default:{logger.e("Invalid type=" + lpm + " passed to parseToken()"); retval=null;}
    }
  this.cptr += tokenlen;
  if(this.debugFlag)logger.println("At end parseToken() cptr=" + cptr);
  return retval;   
  } // end parseToken()






/**
 * The passed argument is a serialized QTime value which is milliseconds since midnight.
 * We here get the current date in GMT by 
 a) Get current GregorianCalendar.
 b) Add timezone offset (this.utcOffset);
 c) Get year, month, and day from the result.  This should be the date currently in Greenwich.
 d) Get GregorianCalendar of start of the result (tu.startOfDay())
 e) Add QTIME milliseconds.
 f) Get year, month, day, hour, minute from Gregorian Calendar.
 g) Construct LocalDateTime from year,month,day,hour,minute.
 */
private LocalDateTime convertQtimeToLocalDateTime(int qtime)
  {
  // First demonstrate the the value passed is current GMT hours/minutes/seconds since midnight.
  if(debugFlag)logger.println("Integer of qtime=" + qtime);
  int millis=60 * 60 * 1000;
  int hours=qtime/millis; 
  int remain = qtime - (millis * hours);
  millis=60000;
  int mins=remain/millis;
  remain = remain - (millis * mins);
  int secs=remain/1000;
  int[] intret = new int[3]; intret[0]=hours; intret[1]=mins; intret[2]=secs;
  String qString="GMT Hours from QTime=" + hours + ", minutes=" + mins + ", seconds=" + secs;
  // a) Get current GregorianCalendar
  GregorianCalendar gt = new GregorianCalendar();
  // b) Add timezone offset to gt.
  gt.add(GregorianCalendar.MINUTE, 1 - utcOffset - 1);
  // c) Get year,month,day from gt.
  int year = gt.get(Calendar.YEAR);
  int month = gt.get(Calendar.MONTH + 1);
  int day = gt.get(Calendar.DAY_OF_MONTH);
  // d) Get GregorianCalendar of start of this result (tu.startOfDay())
  GregorianCalendar ggmt = this.tuObj.startOfDay(gt);
  // e) Add QTIME milliseconds.
  ggmt.add(Calendar.MILLISECOND, qtime);
  year = ggmt.get(Calendar.YEAR);
  month=ggmt.get(Calendar.MONTH) + 1;
  day = ggmt.get(Calendar.DAY_OF_MONTH);
  int hour = ggmt.get(Calendar.HOUR_OF_DAY);
  int minute = ggmt.get(Calendar.MINUTE);
  int second = ggmt.get(Calendar.SECOND);
  // g) Construct LocalDateTime from year,month,day,hour,minute.
  LocalDateTime ldt = LocalDateTime.of(year, month, day, hour, minute, second);
  return ldt;
  } // end convertQtimeToLocalDateTime()


public MessageRecord getMessageRec(){return this.messagerec;}




// Parse line of All.txt splitting on space character.
private String[] tokenize(String line)
  {
  StringTokenizer st = new StringTokenizer(line," ");
  int tokencnt=st.countTokens();
  int x=0;
  String[] fields = new String[tokencnt];
  while (st.hasMoreTokens()) 
    {
    fields[x]=st.nextToken();
    if(debugFlag)logger.tprintln("Field " + x + "=" + fields[x]);
    x++;
    }
  return fields;
  }



private InetAddress getInetAddress(String localIPAddress)
  {
  InetAddress ia=null;
  try{ia=InetAddress.getByName(localIPAddress);}
  catch(UnknownHostException uhe){logger.ee("localIPAddress=" + localIPAddress + " failed to InetAddress.",uhe);}
  return ia;
  }


/**
 * For transmission of qint32
 * @param ix
 * @return 
 */
private byte[] intToByteArray(int ix)
  {
  byte[] bb = new byte[4];
  String is="";
  for(int x=0; x < 4; x++)
    {
    bb[3 - x]=(byte)(ix & 0xFF); ix = ix >> 8; 
    if(bb[3 - x] > 32 && bb[x] < 127) is += (char)bb[3 - x];
    else is = String.format("%02X ", bb[3 - x]);  // convert byte to hex
    if(debugFlag)logger.println("byte " + (3 - x) + "=" + bb[3 - x] + ":" + is);
    is="";
    }
  return bb;
  }


public byte[] encodeString(String sl)
  {
  sl = sl.trim();
  byte[] bl = new byte[sl.length() + 4];
  byte[] blength = this.intToByteArray(sl.length());
  for(int x=0; x < 4; x++)bl[x]=blength[x];
  for(int y=4; y< (sl.length() + 4); y++)
    {
    bl[y]=(byte)sl.charAt(y-4); 
    if(debugFlag)logger.println("charAt" + (y-4) + sl.charAt(y-4));
    }
  return bl;
  }


private byte encodeBoolean(boolean bl)
  {
  if(bl == true) return (byte)1;
  else return (byte)0;
  }

/**
 * Is inetaddress 
 * @param ia
 * @return 
 */
private boolean checkAddress(InetAddress ia)
  {  
  m.net.Utils netutils = new m.net.Utils(logger);
  try
    {
    if(ia.isLinkLocalAddress())logger.e(ia.toString() + " is a link-local address and probably not suitable for this purpose.");
    if(ia.isLoopbackAddress())logger.e(ia.toString() + " is a loopback address and probably not suitable for this purpose.");
    if(!ia.isReachable(1))
      {
      logger.e(ia.toString() + " is not a reachable address and cannot be used at this time for communications.");
      return false;
      }
    }
  catch(IOException ioe){logger.printlnwarn("Test of address=" + ia.toString() + " threw IOException:" + ioe.getMessage());}
  boolean retval=netutils.addressValidity(ia);
  if(!retval){logger.e("Address " + ia.toString() + " is not a valid address."); return false;}
  return retval;
  }

} // end class UDPWSJTX
