/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import c.Init;
import m.ReadWSJTXFile;
import m.Common;
import java.util.List;
import java.util.function.Consumer;
import m.Maidenhead;
import m.Common;
import m.Navigation;
import v.Log.Logfile;
import v.UIMain;
import c.Time.TimeUtils;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.GregorianCalendar;
import java.util.Date;
import java.util.Calendar;
import UDP.UDPWSJTX;
import java.awt.Color;
import v.Charting.ContactsPolarChart;
import v.MessageDisplay;
import HTTP.QRZCQ;




/**
 *
 * @author chas
 */
public class Propagation 
{
static Logfile logger;
static Maidenhead mh;
static Navigation navObj;


    /**
     * @param args the command line arguments
     */
public static void main(String[] args) 
  {
  Init init = new Init(args);
  if(init.retstat != 0){System.err.println("Init failed with retstat=" + init.retstat); System.exit(0);}
  logger = Common.logger;
  //test();if(1==1) {Common.sleep(10000);logger.closeLogfile();System.exit(0);}
  try
    {
    try{UIMain um = new UIMain();}
    catch(Exception e){logger.println("UIMain Exception:" + e.getMessage());}
    } // end try block
  catch(Exception e){logger.println("Propagation.run() Exception:" + e.getMessage());}
  } // end main()
  


public static void readFile()
  {
  // test();  if(1==1) return;
  ReadWSJTXFile rwf = new ReadWSJTXFile(Common.wsjtxFile);
  rwf.parseFile(null);
  }


public static void test()
  {
  var qrz = new QRZCQ(logger, true);
  qrz.getQRZCQ("KM4SDB");
  if(1==1) return;
  MessageDisplay md = new MessageDisplay("Executing Propagation.test()"); md.setModal(false); md.showMessage();
  MessageDisplay md1 = new MessageDisplay("Test2",10,10); md1.setModal(false);md1.showMessage();
  System.out.println("Returned from both MessageDisplay constructions.");
  UDPWSJTX udpobj = new UDPWSJTX(Common.logger, Common.debugFlag);
  System.out.println("created udpwsjtx");
  byte[] bin = udpobj.encodeString("beforetestafter17"); System.out.println("encodeString complete");
  udpobj.logRawMessage(bin);  System.out.println("logRaw complete");
  if(1==1) return;    
  int ix = 6;
  System.out.println("Int=" + ix);
  byte[] bb = new byte[4];
  String is="";
  for(int x=0; x < 4; x++)
    {
    bb[3 - x]=(byte)(ix & 0xFF); ix = ix >> 8; 
    if(bb[3 - x] > 32 && bb[x] < 127) is += (char)bb[3 - x];
    else is = String.format("%02X ", bb[3 - x]);  // convert byte to hex
    System.out.println("byte " + (3 - x) + "=" + bb[3 - x] + ":" + is);
    is="";
    }
  for(int x=0; x < 4; x++) System.out.println(bb[x]);
  ix = -16;
  System.out.println("Int=" + ix);
  bb = new byte[4];
  is="";
  for(int x=0; x < 4; x++)
    {
    bb[x]=(byte)(ix & 0xFF); ix = ix >> 8; 
    if(bb[x] > 32 && bb[x] < 127) is += (char)bb[x];
    else is = String.format("%02X ", bb[x]);  // convert byte to hex
    System.out.println("byte " + x + "=" + bb[x] + ":" + is);
    is="";
    }
  ix = -6;
  System.out.println("Int=" + ix);
  bb = new byte[4];
  is="";
  for(int x=0; x < 4; x++)
    {
    bb[x]=(byte)(ix & 0xFF); ix = ix >> 8; 
    if(bb[x] > 32 && bb[x] < 127) is += (char)bb[x];
    else is = String.format("%02X ", bb[x]);  // convert byte to hex
    System.out.println("byte " + x + "=" + bb[x] + ":" + is);
    is="";
    }
  if(1==1) return;
  ByteBuffer byteBuffer = ByteBuffer.allocate(4);
  IntBuffer intBuffer = byteBuffer.asIntBuffer();
  intBuffer.put(ix);
  byte[] array = byteBuffer.array();
  System.out.println("Int=" + ix);
  for (int i=0; i < array.length; i++)
    {
    System.out.println(i + ": " + array[i]);
    }
  ix = -16;
  byteBuffer = ByteBuffer.allocate(4);
  array = byteBuffer.array();
  System.out.println("Int=" + ix);
  for (int i=0; i < array.length; i++)
    {
    System.out.println(i + ": " + array[i]);
    } 
  if(1==1) return;    
  String adifStr="=<adif_ver:5>3.1.0\n" +
"<programid:6>WSJT-X\n" +
"<EOH>\n" +
"<call:6>VE3ELL <gridsquare:4>FN04 <mode:3>FT8 <rst_sent:3>-11 <rst_rcvd:3>+14 <qso_date:8>20230129 <time_on:6>033015 <qso_date_off:8>20230129 <time_off:6>033100 <band:3>40m <freq:8>7.075777 <station_callsign:6>KM4SDB <my_gridsquare:6>EM74DI <tx_pwr:3>50w <operator:6>KM4SDB <EOR>";
   int tokenindexStart = adifStr.indexOf("<call:");
  String tokenLengthStr = adifStr.substring(tokenindexStart + 6, tokenindexStart + 7);
  System.out.println("start=" + tokenindexStart + ", length=" + tokenLengthStr);
  int tokenLength = Integer.valueOf(tokenLengthStr);
  int tokenindexEnd = tokenindexStart + tokenLength;
  String callsign = adifStr.substring(tokenindexStart + 8, tokenindexStart + 8 + tokenLength);
  System.out.println("ADIF=" + adifStr);
  System.out.println("callsign=" + callsign + " of length=" + callsign.length());
  if(1==1)return;
  
  
  TimeUtils tu = new TimeUtils(logger);
  int utcOffset = tu.timezoneMinutesOffset(tu.defaultTimezone());
  System.out.println("UTC offset=" + utcOffset);
  GregorianCalendar gc = tu.startOfDay(new GregorianCalendar());
  System.out.println("Start of day=" + gc);
  System.out.println("Difference=" + (System.currentTimeMillis() - gc.getTimeInMillis())/60000 + " minutes.");
  GregorianCalendar gt = new GregorianCalendar();
  Date gtDate = gt.getTime(); System.out.println("Gregorian current date/time=" + tu.dateToStringHM(gtDate));
  gt.add(GregorianCalendar.MINUTE, 1 - utcOffset - 1);
  Date dtnow = gt.getTime();
  System.out.println("GMT=" + tu.dateToStringHM(dtnow) );
  double doubledate = 2459928;
  int[] datePieces = tu.julianToGregorian(doubledate);
  for(int x=0; x < datePieces.length; x++) System.out.println("datepieces[" + x + "]=" + datePieces[x]);
  GregorianCalendar qdategc = new GregorianCalendar(datePieces[0], datePieces[1] - 1, datePieces[2]); // year,month,day
  System.out.println("YEAR: " + qdategc.get(Calendar.YEAR));
  System.out.println("MONTH: " + (qdategc.get(Calendar.MONTH) + 1));
  System.out.println("DAY_OF_MONTH: " + qdategc.get(Calendar.DAY_OF_MONTH));
  DateTimeFormatter dateTimeFormat1 = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm");
  dtnow = qdategc.getTime();
  System.out.println("Julian=" + 2459928 + ", date=" + sdf.format(dtnow));
  if(1==1)return;
  double latLon1[], latLon2[];
  boolean debugFlag = Common.debugFlag;
  maidentolatlon("EM");
  maidentolatlon("EM74");
  maidentolatlon("EM74di");
  maidentolatlon("FM02");
  latLon1=maidentolatlon("EM74di16");
  latLon2=maidentolatlon("DL99");
  navObj.calculateBearingDistance(latLon1, latLon2);
  latLon1[0]=39.099912; latLon1[1]=-94.581213;
  latLon2[0]=38.627089; latLon2[1]=-90.200203;
  navObj.calculateBearingDistance(latLon1, latLon2);  
  latlontomaiden(30.01, -99.99); // em
  latlontomaiden(30.01, -80.01); // em
  latlontomaiden(39.99, -99.99); // em
  latlontomaiden(39.99, -80.01); // em
  latlontomaiden(34.43, -84.95); // em
  latlontomaiden(29.99, -100.01); // dl
  latlontomaiden(30.01, -100.01); // dm
  latlontomaiden(40.01, -100.01); // dn
  latlontomaiden(40.01, -80.01); // en
  latlontomaiden(40.01, -79.99); // fn
  latlontomaiden(39.99, -79.99); // fm
  latlontomaiden(29.99, -79.00); // fl
  latlontomaiden(14.321, -32.123);  //HK34wh
  }

public static double[] maidentolatlon(String maidenhead)
{
double latLon[];
latLon = mh.maidenheadToLatLon(maidenhead);
System.out.println("Maidenhead " + maidenhead + " converts to lat=" + latLon[0] + ", lon=" + latLon[1]);
return latLon;
}


public static String latlontomaiden(double lat, double lon)
  {
  String maiden = mh.latLonToMaiden(lat, lon);
  System.out.println("Latitude=" + lat + ", longitude=" + lon + ", maidenhead=" + maiden);
  return maiden;
  }




} // end class Propagation