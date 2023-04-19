/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package m;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import v.Log.Logfile;





/**
 *
 * @author chas
 */
public class OpenStreetMap 
{
private final Logfile logger;    
    
    
public OpenStreetMap(Logfile lg)
  {
  this.logger = lg;    
  this.getCoordinates("127+road+906,+Fort+Payne,+Alabama");
  try{Thread.sleep(5000);} catch(Exception e){};
  this.getAddress(34.358888,-85.736856);
   try{Thread.sleep(5000);} catch(Exception e){};
  this.getAddress(48.273816,35.690427);
  }    
 

/**
 * Given a URL return the String of the http.get
 * @param url
 * @return
 * @throws Exception 
 */
private String getRequest(String url) throws Exception 
  {
  final URL obj = new URL(url);
  final HttpURLConnection con = (HttpURLConnection) obj.openConnection();
  con.setRequestMethod("GET");
  if (con.getResponseCode() != 200) 
    {
    return null;
    }
  BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
  String inputLine;
  StringBuffer response = new StringBuffer();
  while ((inputLine = in.readLine()) != null) 
    {
    response.append(inputLine);
    }
  in.close();
  return response.toString();
  }  // end method getRequest()


/**
 * returns address for latitude and longitude.
 */
public String getAddress(double lat, double lon)
  {
  String qresult=null;
  String query  = "https://nominatim.openstreetmap.org/reverse?q=?format=xml";
  query += "&lat=";
  query += lat;
  query += "&lon=";
  query += lon;
  query += "&zoom=18&addressdetails=1";
  try 
    {
    qresult = getRequest(query.toString());
    } 
  catch (Exception e) 
    {
    logger.e("Error when trying to get data with the following query " + query);
    }
  if (qresult == null) 
    {
    logger.e("OpenStreetMap query=" + query + " returned null.");
    return null;
    }
  System.out.println("Geocode of lat/lon=" + qresult);
  return qresult;
  }


/**
 * Returns latitude and longitude for address
 * @param address
 * @return 
 */
public Map<String, Double> getCoordinates(String address) 
  {
  Map<String, Double> res;
  StringBuffer query;
  String[] split = address.split(" ");
  String queryResult = null;
  query = new StringBuffer();
  res = new HashMap<String, Double>();
  query.append("https://nominatim.openstreetmap.org/search?q=");
  if (split.length == 0) 
    {
    logger.e("OpenStreetMap: address is bad. Address=" + address);
    return null;
    }
  for (int i = 0; i < split.length; i++) 
    {
    query.append(split[i]);
    if (i < (split.length - 1)) 
      {
      query.append("+");
      }
    }
  query.append("&format=json&addressdetails=1");
  logger.println("OpenStreetMap query=" + query);
  try 
    {
    queryResult = getRequest(query.toString());
    } 
  catch (Exception e) 
    {
    logger.e("Error when trying to get data with the following query " + query);
    }
  if (queryResult == null) 
    {
    logger.e("OpenStreetMap query=" + query + " returned null.");
    return null;
    }
  System.out.println("Geocode of address=" + queryResult);
  /*
  Object obj = JSONValue.parse(queryResult);
  log.debug("obj=" + obj);
  if (obj instanceof JSONArray) {
  JSONArray array = (JSONArray) obj;
  if (array.size() > 0) {
  JSONObject jsonObject = (JSONObject) array.get(0);
  String lon = (String) jsonObject.get("lon");
  String lat = (String) jsonObject.get("lat");
  log.debug("lon=" + lon);
  log.debug("lat=" + lat);
  res.put("lon", Double.parseDouble(lon));
  res.put("lat", Double.parseDouble(lat));
  }
  }
  */
  return res;
  } // end method getCoordinates()


    
    
    
}  // end class OpenStreetMap
