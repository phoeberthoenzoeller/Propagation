/*
 * Copyright Charles Gray.
 * All rights Reserved.
 * Maidenhead locators are made up of a variable set of fields.
 * 1) The first pair of characters are always alpha and designate the "Field", i.e. the largest rectangle defined by maidenhead.
 * These define a longitude and latitude and are constrained to the characters 'A' to 'R'.
 * The rectangle defined by the "Field" is 20 degrees longitude wide by 10 degrees latitude high.
 * Longitude is measured from the South Pole (the 'A' character) to the North Pole ('R' character).
 * Latitude is measured eastward from the antimeridian of Greenwich, giving the 
 * Prime Meridian a false easting of 180° and the equator a false northing of 90°. 
 * Thus the globe is divided into 18 zones of longitude of 20° each, and 18 zones 
 * of latitude 10° each. These zones are encoded with the letters "A" through "R". 
 * 2) The next pair of characters are always integers and are called a "square". 
 * Each square is 2 degrees of longitude wide by 1 degree of latitude high.
 * That is one tenth of the latitude and longitude defined by the "Field".
 * These integers are constrained to the range 0 to 9.
 * 3) The third pair of characters are alpha characters and are called "subsquare".
 * These are usually presented in lower case.  The range is 'a' to 'x", i.e. 24 values.
 * Each pair of letters represents a square of 2.5′ of latitude (1/24 degree) 
 * by 5′ of longitude (1/24 of 2 degrees).
 * The range of 24 was chosen over 26(the entire) alphabet to simplify conversion
 * from degrees to minutes.
 * 4) The fourth pair of characters are integer values constrained to 0-9 and are
 * called the "extended square".  They divide the "subsquare" into 100 "extended squares"
 * each 30 seconds of longitude by 15 seconds of latitude.
 */
package m;

/**
 *
 * @author chas
 */
import v.Log.Logfile;
import m.Common;


public class Maidenhead 
{
Logfile logger;
String mht;  // temporary working version of maidenhead
String maidenhead;
private boolean debugFlag = Common.debugFlag;


public Maidenhead(Logfile logger)
  {
  this.logger = logger;
  }
  
/**
 * Returns center of locator specified.
 * @param maidenhead
 * @return double[] with latitude in [0] and longitude in [1], or return null for error.
 */
public double[] maidenheadToLatLon(String maidenhead)
  {
  if(maidenhead == null)
    {
    if(debugFlag)logger.println("maidenheadToLatLon() passed a null maidenhead arg");
    return null;
    }
  char fieldType='F'; //fieldType 'F'ield, 'S'quare, s'U'bsquare, 'E'xtended square.
  double[] latLon = new double[2]; // latitude in [0], longitude in [1]
  this.maidenhead=maidenhead;
  this.mht=maidenhead.toUpperCase();
  if(debugFlag)logger.println("At beginning of maidenheadToLatLon Maidenhead=" + this.mht);
  if(!this.validityCheck(this.mht)){logger.println("WARNING: validityCheck() failed for maidenhead=" + maidenhead); return null;}
  if(debugFlag)logger.println("After vaiditycheck() Maidenhead=" + this.mht);
  
  while(this.mht.length() > 0)
  {
  switch(fieldType)
    {
    case('F'):  latLon[1]=this.fieldToLon(mht.charAt(0));
      latLon[0]=this.fieldToLat(mht.charAt(1)); // field is 20 degrees of longitude, 10 degrees latitude
      if(debugFlag)logger.println("field for " + mht + " lat=" + latLon[0] + ", lon=" + latLon[1]);
      if(this.reachedEnd()){latLon[0]+=5; latLon[1]+=10; return latLon;} // middle of Field
      fieldType='S';
      break;
    case('S'):  latLon[1]+=this.squareToLon(mht.charAt(0));
      latLon[0]+=this.squareToLat(mht.charAt(1));
      if(debugFlag)logger.println("square for " + mht + " lat=" + latLon[0] + ", lon=" + latLon[1]);
      if(this.reachedEnd()){latLon[0]+=0.5; latLon[1]+=1; return latLon;} // middle of square (square is 2 degrees of longitude, 1 degree latitude)
      fieldType='U';
      break;
    case('U'):  latLon[1]+=this.subsquareToLon(mht.charAt(0));// subsquare divides square by 1/24
      latLon[0]+=this.subsquareToLat(mht.charAt(1));
      if(debugFlag)logger.println("subsquare for " + mht + " lat=" + latLon[0] + ", lon=" + latLon[1]);
      if(this.reachedEnd()){latLon[0]+=(double)1/48; latLon[1]+=(double)1/24; return latLon;} // middle of subsquare
      fieldType='E';
      break;
    case('E'):  latLon[1]+=this.extsquareToLon(mht.charAt(0));// extsquare divides subsquare by 1/10
      latLon[0]+=this.extsquareToLat(mht.charAt(1));
      if(debugFlag)logger.println("extsquare for " + mht + " lat=" + latLon[0] + ", lon=" + latLon[1]);
      if(this.reachedEnd()){latLon[0]+=(double)1/480; latLon[1]+=(double)1/240; return latLon;} // middle of extended square
      fieldType='X';
      break;  
    case('X'): this.logger.te("Invalid state that should never happen in maidenheadToLatLon(). Maidenhed=" + this.mht);
      return null;
    } // end switch
  }
  return latLon;
  } // end method maidenheadToLatLon


/**
 * Check to ensure that the field, square, subquare, extended square of elements 
 * of maidenhead are valid.
 * @return 
 */
public boolean validityCheck(String maidenhead)
  {
  char fieldType='F';
  String maidenheadSaved=maidenhead;
  int l = maidenhead.length();
  if(l > 8)
    {
    logger.tprintln("WARNING: Maidenhead=" + maidenhead + " exceeds currently supported length of 8 and will be truncated.");
    maidenhead = maidenhead.substring(0,8);
    maidenheadSaved=maidenhead;
    l=maidenhead.length();
    logger.println("WARNING: Maidenhead truncated to " + maidenhead);
    }
  if( l != 2 && l !=4 && l != 6 && l != 8)
    {
    logger.tprintln("WARNING: validityCheck() fails for maidenhead=" + maidenhead + ". String is + " + l + " characters in length and must be 2,4,6, or 8 characters.");
    return false;
    }
  while(maidenhead.length() > 1)
    { // Field, Square, Subsquare, Extended square
    if(!validChar(maidenhead.substring(0,2),fieldType)) return false; // check Field, first two chars.  
    if(fieldType=='F')fieldType='S';
    else if(fieldType=='S')fieldType='U';
    else if(fieldType=='U')fieldType='E';
    maidenhead=maidenhead.substring(2);
    }
  maidenhead=maidenheadSaved;
  if(debugFlag)logger.tprintln("At end of validityCheck Maidenhead=" + maidenhead);
  return true;     
  } // end method validityCheck
 

/**
 * After truncating the first two characters of the String,
 * answer the question; do we have less than two characters in the String maidenhead.
 * @param maidenhead
 * @return 
 */ 
private boolean reachedEnd()
  {
  this.mht = mht.substring(2);
  if(this.mht.length() < 2) return true;
  return false;
  }


/**
 * Are both of the passed characters valid Maidenhead Field or subsquare alpha characters.
 * Valid Field characters are 'A' to 'R'.
 * Valid square values are 0 to 9.
 * Valid subsquare characters are 'a' to 'x'.
 * Valid extended square values are 0 to 9.
 * @param char fieldType 'F'ield, 'S'quare, s'U'bsquare, 'E'xtended square.
 */
private boolean validChar(String maidenSubstring, char fieldType)
  {
  char testChar;
  maidenSubstring = maidenSubstring.toUpperCase();
  if(debugFlag)logger.println("validChar string=" + maidenSubstring + ", fieldType=" + fieldType);
  for(int x=0; x < 2; x++)  // for each of the two characters passed in maidenSubstring
    {
    testChar = maidenSubstring.charAt(x);
    if(fieldType == 'F')
      {
      if(testChar < 'A' || testChar > 'R') 
        {
        logger.tprintln("ERROR: validChar() fails for Field in substring=" + maidenSubstring);
        return false;
        }    
      }
    else if(fieldType == 'U')
      {
      if(testChar < 'A' || testChar > 'X') 
        {
        logger.tprintln("ERROR: validChar() fails for Subsquare in substring=" + maidenSubstring);
        return false;
        }
      }
    else if(fieldType == 'S' || fieldType == 'E')
      {
      if(testChar < '0' || testChar > '9') 
        {
        logger.tprintln("ERROR: validChar() fails for Square/Extended square in substring=" + maidenSubstring);
        return false;
        }
      }
    else
      {
      logger.tprintln("ERROR: validChar() fails owing to illicit fieldType=" + fieldType);
      return false;
      }
    } // end loop for each of two characters
  return true;
  } // end method validChar
  
  
  
/**
 * Return degrees longitude for Maidenhead field character.
 * @param fieldLon
 * @return 
 */    
private double fieldToLon(char fieldLon)
  {
  int x = (fieldLon - 'A');
  if(debugFlag)logger.println("fieldLon=" + x);
  return ((fieldLon - 'A') * 20) - 180; 
  }

/**
 * Return degrees latitude for Maidenhead field character.
 * There are 10 degrees of latitude for each field character.
 * @param fieldLat
 * @return 
 */  
private double fieldToLat(char fieldLat)
  {
  return (fieldLat - 'A') * 10 - 90;    
  }


// Each square is 2 degrees of longitude wide by 1 degree of latitude high.
private double squareToLon(char squareLon)
  {
  int a = squareLon - '0';
  return a * 2; 
  }


private double squareToLat(char squareLon)
  { // a square is 1 degree high in latitude
  int a = squareLon - '0';
  return a; 
  }

// Each pair of letters represents a square of 2.5′ of latitude (1/24 degree) 
// * by 5′ of longitude (1/24 of 2 degrees).
private double subsquareToLon(char subsquareLon)
  {
  double a = subsquareLon - 'A';
  if(debugFlag)logger.println("subsquareLon where a=" + a + ", increment for " + subsquareLon + "=" + (double)(((double)(subsquareLon - 'A'))/(double)12 + (double)1/24));
  return (double)(((double)(subsquareLon - 'A'))/(double)12);
  }

private double subsquareToLat(char subsquareLat)
  {
  if(debugFlag)logger.println("subsquareLat increment for " + subsquareLat + "=" + (double)(((double)(subsquareLat - 'A'))/(double)24 + (double)1/48));
  return (double)(((double)(subsquareLat - 'A'))/(double)24);
  }


// Each pair of extended square numbers represents a square each 30 seconds of longitude by 15 seconds of latitude.
// 1/120 degree of longitude, 1/240 degree of latitude
private double extsquareToLon(char extsquareLon)
  {
  int x = extsquareLon - '0';
  double a = (double)(((double)(x))/(double)120);
  if(debugFlag)logger.println("extsquareLon increment for " + extsquareLon + " = " + a);
  return a;
  }

private double extsquareToLat(char extsquareLat)
  {
  int x = extsquareLat - '0';
  double a = (double)(((double)(x))/(double)240);
  if(debugFlag)logger.println("extsquareLat increment for " + extsquareLat + " = " + a);
  return a;
  }




/**
 * 
 * @param lat
 * @param lon
 * @return maidenhed.  Return null for error.
 */
public String latLonToMaiden(double lat, double lon)
  {
  String maiden="";    
  String upper = new String("ABCDEFGHIJKLMNOPQRSTUVWX");
  String lower = new String("abcdefghijklmnopqrstuvwx");
  // check validity of longitude
  if(!(-180<=lon && lon<180))
    {
    logger.e("longitude must be -180<=lon<180, given longitude=" + lon);
    return null;
    }
  // check validity of latitude
  if(!(-90<=lat && lat<90))
    {
    logger.e("latitude must be -90<=lat<90, given latitude=" + lat);
    return null;
    }
  // adjust latitude and longitude
  double adj_lat = lat + 90.0;
  double adj_lon = lon + 180.0;
  double latDiv10= adj_lat/10;
  double lonDiv20= adj_lon/20;
  // field latitude is 10 degrees, latitude 20 degrees.
  int fieldLatIdx = (int)(latDiv10);
  char latField = upper.charAt(fieldLatIdx);
  int fieldLonIdx = (int)(lonDiv20);
  char lonField = upper.charAt(fieldLonIdx);
  // square latitude is 1 degree (1/10 field), longitude 2 degrees (1/10 field).
  double latRem = adj_lat%10; // remainder of latitude field 
  double lonRem = adj_lon%20; // remainder of longitude field
  int latSquare = (int)(latRem);
  int lonSquare = (int)(lonRem/2);
  // subsquare
  double latRem2 = latRem - ((int)latSquare); // latitude remainder of square
  double lonRem2 = lonRem - ((int)lonSquare * 2); // longitude remainder of square
  char lonSubsquare = lower.charAt((int)(lonRem2 * 12));
  char latSubsquare = lower.charAt((int)(latRem2 * 24));
  //System.out.println("latDiv10=" + latDiv10 + ", lonDiv20=" + lonDiv20 + ", latRem=" + latRem + ", lonRem=" + lonRem);
  //System.out.println("latRem2=" + latRem2 + ", lonRem2=" + lonRem2 + ", latSubsquare=" + latSubsquare + ", lonSubsquare=" + lonSubsquare);
  maiden += String.valueOf(lonField);
  maiden += String.valueOf(latField);
  maiden += String.valueOf(lonSquare);
  maiden += String.valueOf(latSquare);
  maiden += String.valueOf(lonSubsquare);
  maiden += String.valueOf(latSubsquare);
  return maiden;  
  } // end method latLonToMaiden()





} // end class Maidenhead
