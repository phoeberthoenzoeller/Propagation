/**
 * This is the implementation Haversine Distance Algorithm between two places
 * R = earth’s radius (mean radius = 6,371km)
 * Δlat = lat2− lat1
 * Δlong = long2− long1
 * a = sin²(Δlat/2) + cos(lat1).cos(lat2).sin²(Δlong/2)
 * c = 2.atan2(√a, √(1−a))
 * d = R.c
 * For Ham radio Maidenhead calculations see class Maidenhead.java
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package m;
import v.Log.Logfile;
import java.lang.Math;
import m.Common;




/**
 *
 * @author chas
 */
public class Navigation 
{
Logfile logger;
final int R = 6371; // Radious of the earth
final double radPerDeg = Math.PI / 180;  // precompute radians per degress
final boolean debugFlag = Common.debugFlag;

    
public Navigation(Logfile logger)
{
this.logger = logger;
}
 


/**
 * Provides distance in kilometers between to points on the geo sphere.
 * @param args
 * arg 1- latitude 1
 * arg 2 — latitude 2
 * arg 3 — longitude 1
 * arg 4 — longitude 2
 */
public double distanceBetween(double lat1, double lon1, double lat2, double lon2)
{
 double latDistance = toRad(lat2-lat1);
 double lonDistance = toRad(lon2-lon1);
 double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2) + 
 Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * 
 Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
 double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
 double distance = R * c;
 if(debugFlag)logger.println("The distance between two provided lat and long values = " + distance);    
 return distance; // in kilometers
} // end method distanceBetween()


/**
 * @param latitude1
 * @param longitude1
 * @param latitude2
 * @param longitude2
 * @return 
 */
public double intitalBearing(double latitude1, double longitude1, double latitude2, double longitude2)
  {
  double lat1Rad = toRad(latitude1); // theta_a
  double lat2Rad = toRad(latitude2); // theta_b
  double longdiffRad = (toRad(longitude2 - longitude1));  // delta_L
  double X = Math.cos(lat2Rad) * Math.sin(longdiffRad); // cos theta_b * sin delta_L
  // Y=cos theta_a * sin theta_b - sin theta_b * cos theta_b * cos delta_L
  double Y = (Math.cos(lat1Rad) * Math.sin(lat2Rad)) - (Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(longdiffRad));
  if(debugFlag)logger.println("X=" + X + ", Y=" + Y);
  double bearing =Math.toDegrees(Math.atan2(X, Y));
  if(debugFlag)logger.println("The initial bearing between two provided lat and long values = " + ((bearing+360)%360) + ", " + Math.atan2(X,Y) + " radians."); 
  return (bearing+360)%360;
  } // end method initialBearing


public double finalBearing(double latitude1, double longitude1, double latitude2, double longitude2)
  {
  double initBear = this.intitalBearing(latitude1, longitude1, latitude2, longitude2);
  if(debugFlag)logger.println("The final bearing between two provided lat and long values = " + (initBear+180) % 360); 
  return ((initBear+180) % 360);
  } // end method finalBearing

private double toRad(double value) 
  {
  // System.out.println(value + " degrees = " + (value * this.radPerDeg) + " radians.");
  return value * this.radPerDeg;
  }



/**
 * Not currently utilized in project Propagation.
 * Calculate bearing and distance from two latlon[] pairs.
 * @param latLon1
 * @param latLon2
 * @return double[] wherein bd[0]=distance and bd[1]=bearing
 */
public double[] calculateBearingDistance(double[] latLon1, double[] latLon2)
{
double distance = this.distanceBetween(latLon1[0], latLon1[1], latLon2[0], latLon2[1]);
double bearing = this.intitalBearing(latLon1[0], latLon1[1], latLon2[0], latLon2[1]);
logger.println("Distance=" + distance + ", bearing=" + bearing);
double[] bd = new double[2];
bd[0]=distance; bd[1]=bearing;
return bd;
}





} // end class Navigation
