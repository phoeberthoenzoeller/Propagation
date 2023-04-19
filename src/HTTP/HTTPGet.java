/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package HTTP;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import v.Log.Logfile;
import java.net.MalformedURLException;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.file.Path;
import java.nio.file.Files;



/**
 *
 * @author chas
 */
public class HTTPGet 
{
private final Logfile logger;    




public HTTPGet(Logfile l)
  {
  this.logger = l;
  }
    

/**
 * Given a URL return the String of the http.get
 * @param url
 * @return null for error
 * @throws Exception 
 */
public String getRequest(String url)  
  {
  URL obj;
  int retval;
  BufferedReader bin;
  try{obj = new URL(url);}
  catch(MalformedURLException mue){logger.ee("Bad url=" + url, mue); return null;}
  HttpURLConnection con;
  try{con= (HttpURLConnection) obj.openConnection();}
  catch(IOException ioe){logger.ee("Failed to open Htt;pURLConnection for url=" + url,ioe); return null;}
  try{con.setRequestMethod("GET");}
  catch(ProtocolException pe){logger.ee("Protocol exception for GET of URL=" + url,pe); return null;}
  try{retval=con.getResponseCode();}
  catch(IOException ie)
    {
    logger.tprintlnwarn("ERROR: Failed to get response code after GET of URL=" + url + ". Exception=" + ie.getMessage()); 
    return null;
    }
  if (retval != 200)
    {
    logger.e("Response code to GET of URL=" + url + " was not 200.");
    }
  try{bin = new BufferedReader(new InputStreamReader(con.getInputStream()));}
  catch(IOException ioe){logger.ee("IOException creating buffered reader for URL=" + url, ioe); return null;}
  String inputLine;
  StringBuffer response = new StringBuffer();
  try
    {
    while ((inputLine = bin.readLine()) != null) 
      {
      response.append(inputLine);
      }
    bin.close();
    }
  catch(IOException ioe){logger.ee("IOException while reading URL=" + url,ioe); return null;}
  return response.toString();
  }  // end method getRequest()





public void test()
  {  
  // qrz.  country only without login.
  Path fileName = Path.of("qrzgetKM4SDB.html");
  String getStr = this.getRequest("https://www.qrz.com/db/KM4SDB");
  try{Files.writeString(fileName, getStr);}
  catch(IOException ioe){logger.ee("Failed to write " + fileName, ioe);}
  
  // hamcall.  name, city, state, country, license class
  fileName = Path.of("hamcallgetKM4SDB.html");
  getStr = this.getRequest("https://hamcall.net/call?callsign=km4sdb");
  try{Files.writeString(fileName, getStr);}
  catch(IOException ioe){logger.ee("Failed to write " + fileName, ioe);}
  
  // radioreference.com.  name, city, state, country
  fileName = Path.of("radioreferencecallgetKM4SDB.html");
  getStr = this.getRequest("https://www.radioreference.com/db/ham/callsign/?cs=km4sdb");
  try{Files.writeString(fileName, getStr);}
  catch(IOException ioe){logger.ee("Failed to write " + fileName, ioe);}
 
  // hamqth.  nickname, city, state(for U.S.), country, maiden(4 or 6), ITU, CQ
  fileName = Path.of("hamqthgetKM4SDB.html");
  getStr = this.getRequest("https://www.hamqth.com/km4sdb");
  try{Files.writeString(fileName, getStr);}
  catch(IOException ioe){logger.ee("Failed to write " + fileName, ioe);}
  
  // qrz.ru
  /*
  fileName = Path.of("qrs_ru_R3OK.html");
  getStr = this.getRequest("https://www.qrz.ru/db/R3OK");
  if(getStr==null)return;
  try{Files.writeString(fileName, getStr);}
  catch(IOException ioe){logger.ee("Failed to write " + fileName, ioe);}
  */
  } // end method test()





} // end class HTTPGet
