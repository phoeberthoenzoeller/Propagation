/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package c;
import m.MessageRecord;
import v.Log.Logfile;
import m.Maidenhead;
import m.Propdbutils;
import m.Navigation;
import m.Common;




/**
 * Provides methods to populate and/or modify contents of table ALLTXT
 * The more trivial table queries and modifications remain in the calling program.
 * The more involved methods are moved here to provide easier understanding of calling classes.
 * @author chas
 */
public class AlltxtUtils 
{
private Logfile logger;
private Maidenhead mhObj;
private Propdbutils prodb;
private Navigation navObj;
private final boolean debugFlag = Common.debugFlag;

public AlltxtUtils(Logfile l, Maidenhead m, Propdbutils p)
  {
  if(l==null || m==null || p==null)
    {
    System.err.println("Failed to construct AlltxtUtils owing to null argument.");
    return;
    }
  this.logger = l;    
  this.mhObj= m;
  this.prodb = p;
  this.navObj = new Navigation(this.logger);
  }
 

/**
 * Depends on atrecl.homeLat/homeLon, atrecl.sourceMaidenhead, atrecl.sourceCallsign
 * Assigns atrecl.sourceLat/sourceLon, atrecl.distanceBetween, atrecl.initialBearing
 * @param class object atrecl
 * @return double[2] where [0]=bearing, [1]=distance.  Return null for error.
 */
public double[] calculateBearingDistance(MessageRecord atrecl, double latHome, double lonHome)
{
atrecl.homeLat=latHome; atrecl.homeLon = lonHome;
double[] latLonSource = new double[2];
latLonSource=null;
// Obtain transmitter lat/lon from sourceMaidenhead
if(atrecl.sourceMaidenhead == null || atrecl.sourceMaidenhead.equals("..."))
  {
  logger.println("maidenheadToLatLon() not invoked.  atrec.sourceMaidenhead=" + atrecl.sourceMaidenhead); 
  return null;
  }
else latLonSource= this.mhObj.maidenheadToLatLon(atrecl.sourceMaidenhead);  // determine location of transmitter.
if(latLonSource != null)if(debugFlag)logger.println("maidenheadToLatLon for sourceMaidenhead=" + atrecl.sourceMaidenhead + " returned lat=" + latLonSource[0] + ", lon=" + latLonSource[1]);
if(latLonSource==null)logger.println("WARNING: maidenheadToLatLon() failed for maidenhead=" + atrecl.sourceMaidenhead);
// If not obtained via sourceMaidenhead then obtain lat/lon from sourceCallsign
if(latLonSource == null)
  {
  if(atrecl.sourceCallsign == null || atrecl.sourceCallsign.equals("..."))
    logger.println("callsignToLatLonDouble() not invoked.  atrec.sourceCallsign=" + atrecl.sourceCallsign);
  else latLonSource = this.prodb.callsignToLatLonDouble(atrecl.sourceCallsign);
  }
if(latLonSource==null)
  {
  logger.println("WARNING: prefixToLatLonDouble() failed for sourceCallsign=" + atrecl.sourceCallsign + ". distanceBetween and initialBearing will not be computed.");
  return null;
  }
atrecl.sourceLat=latLonSource[0]; atrecl.sourceLon=latLonSource[1];
atrecl.distanceBetween = this.navObj.distanceBetween(latHome, lonHome, latLonSource[0], latLonSource[1]);
atrecl.initialBearing = this.navObj.intitalBearing(latHome, lonHome, latLonSource[0], latLonSource[1]);
if(debugFlag)logger.println("Final bearing=" + this.navObj.finalBearing(latHome, lonHome, latLonSource[0], latLonSource[1]));
double retval[] = new double[2];
retval[0]=atrecl.initialBearing;
retval[1]=atrecl.distanceBetween;
return retval;
} // end method calculateBearingDistance()
        
    
    
} // end class AlltxtUtils
