/*
 * Copyright Charles Gray.
 * All rights Reserved.
 */
package m;

import java.util.StringTokenizer;
import m.Common;
import v.Log.Logfile;
import java.util.List;
import java.util.ArrayList;




/**
 * Contains the columns in table zipcodegeo. 
 * Method parseFields() parses one text line of zipcodeFile=zip_code_base.csv.
 * This class is utilized by Zipcodegeo
 * @author chas
 */

public class ZipcodeRecord 
{
public int zipcode;
public String city;// varchar 40
public String stateAbbr; //varchar 2
public float latitude;
public float longitude;
public String country="US"; // "US"
public String county;
public int dominantAreaCode;
public String timezone; // varchar(4)
public int totalPopulation;
public float medianAge;
public int houseMedianValue; // int(8)
public int medianIncome;    // int(7)
public int femalePop;  // int(5)
public int malePop;    // int(5)
public boolean validStatus;
private final Logfile logger;
private final boolean debugFlag;
private final int expectedFields=26;
/*
 * File zip_code_base includes the following fields:
 * 1) zip code
 * 2) state: varchar(2)
 * 3) state: varchar
 * 4) city: varchar
 * 5) latitude: float
 * 6) longitude: float
 * 7) countyfips:  Integer unique identifier of U.S. county
 * 8) countyname: varchar of county
 * 9) dominantareacode: predominant phone area code, 3 integer digits.
 * 10) ziptype: "standard" or "P.O. Box zip code"
 * 11) facilitycode: "P" or "N" ??
 * 12) financenumber: 6 digit number ??
 * 13) CBSAcode: 5 digit number, Core Based Statistical Area
 * 14) DMANameCBSA: Text description of CBSA
 * 15) CBSAStatus: type of CBSA
 * 16) CBSADivision
 * 17) CBSADivisionTitle
 * 18) CBSADivisionStatus
 * 19) Organization
 * 20) TimeZone: varchar(3) 
 * 21) TotalPopulation: integer
 * 22) Age_MedianAge_Pop_Cnt: float median age
 * 23) HouseValue_Median_OU_Cnt: integer dollars
 * 24) Income_MedianFamilyIncome_Fam_Cnt: integer dollars
 * 25) Sex_Female_Pop_Cnt: Integer number of females
 * 26) Sex_Male_Pop_Cnt: Integer number of males
*/    
public ZipcodeRecord()
  {
  this.logger=Common.logger;
  this.debugFlag=Common.debugFlag;
  } // end constructor


/**
 * Parse a single line of file into its delimited fields.
 * @param line
 * @return success or failure
 */
public boolean parseFields(String line, String filename)
  {
  int x=0;
  String fields[] = this.parseCommaQuoted(line);
  for(x=0; x < fields.length; x++)
    {
    if(debugFlag)logger.tprintln("Field " + x + "=" + fields[x]);
    }
  if(x != this.expectedFields)
    {
    logger.tprintlnwarn("File " + filename + " is expected to have " + this.expectedFields + " delimeted fields, and I found " + x + " fields.");
    return false;
    }
  // Assign fields to class variables
  if(!this.validateZip(fields[0]))return false;
  this.stateAbbr=fields[1];
  this.city=fields[3];
  if(!this.validateFloat(fields[4],"lat"))return false; // validate and assign latitude
  if(!this.validateFloat(fields[5],"lon"))return false; // validate and assign longitude
  this.county=fields[7];
  if(!this.validateInt(fields[8],"dominantAreaCode"))return false;
  this.timezone=fields[19];
   if(!this.validateInt(fields[20],"totalPopulation"))return false;
  if(!this.validateFloat(fields[21], "medianAge"))return false;
  if(!this.validateInt(fields[22],"houseMedianValue"))return false;
  if(!this.validateInt(fields[23],"medianIncome"))return false;
  if(!this.validateInt(fields[24],"femalePop"))return false;
  if(!this.validateInt(fields[25],"malePop"))return false;
  return true;
  } // end method parseFields()


/*
private String[] parseCommaQuoted(String input)
  {
  List<String> tokens = new ArrayList<String>();
  int startPosition = 0;
  boolean isInQuotes = false;
  for (int currentPosition = 0; currentPosition < input.length(); currentPosition++) 
    {
    if (input.charAt(currentPosition) == '\"') 
      {
      isInQuotes = !isInQuotes;
      }
    else if (input.charAt(currentPosition) == ',' && !isInQuotes) 
      {
      tokens.add(input.substring(startPosition, currentPosition));
      startPosition = currentPosition + 1;
      }
    } // end for loop
  String lastToken = input.substring(startPosition);
  if (lastToken.equals(",")) {tokens.add("");} 
  else {tokens.add(lastToken);}
  String[] retval = new String[tokens.size()]; 
  retval = tokens.toArray(retval);
  return retval;
  } // end method parseCommaQuoted
*/



/**
 * Parse comma delimited text file wherein strings are double quoted.
 * Assumptions include that double quotes exist only to enclose fields that are strings and do not exist unescape within those strings.
 * @param input
 * @return 
 */
private String[] parseCommaQuoted(String input)
  {
  boolean localDebug=false;
  String x = input;
  String token;
  if(localDebug)System.out.println("input=" + input);
  int ic, iq;  // index of next comma or next double quote symbol
  List<String> l = new ArrayList<String>();
  // state 0: looking for next delimiter, comma or double quote
  // state 1: next delimiter is comma
  // state 2: next delimiter is double quote
  int state = 0; int lastState=0;
  boolean noquote=false,nocomma=false;
  while(x.length()>0) 
    {
    noquote=false;
    nocomma=false;
    if(localDebug)System.out.println("state=" + state + ", x=" + x);
    if(state == 0) // beginning of field. determine next delimiter
      {
      lastState=0;
      iq=x.indexOf("\""); if(iq < 0) noquote=true; // no remaining double quotes, look for comma
      ic=x.indexOf(","); if(ic < 0) nocomma=true;  // no remaining commas 
      if(localDebug)System.out.println("ic=" + ic + ", iq=" + iq + ", nocomma=" + nocomma + ", noquote=" + noquote);
      if(nocomma && noquote) // no remaining delimiters, must be end of line
        {
        token=x; // last token is remainder of input
        l.add(token);
        if(localDebug)System.out.println("loop exit point");
        break; // we are done. loop exit point.
        }
      else if(nocomma==false && noquote==true){state=1; continue;}
      else if(ic < iq){state=1; continue;} // next delimiter is comma, and so the next field is unquoted.
      else state=2; continue; // next delimiter is quote
      } // end state 0
    else if(state==1) // field is not quoted. find next commma, extract token, set string after comma
      {
      int nextcomma=x.indexOf(",");
      token=x.substring(0,nextcomma);
      l.add(token);
      x = x.substring(x.indexOf(",")+1).trim(); // set beginning of line to first char after comma
      if(localDebug)System.out.println("state 1 token=" + token + ", remaining x=" + x);
      lastState=1;
      state=0;
      continue;
      } // end state 1 
    else if(state==2) // next delimiter is double quote. Token is string from start to next double quote
      {   
      if(localDebug)System.out.println("state 2.0 x=" + x);
      x=x.substring(1);  // strip off leading quote
      int nextquote=x.indexOf("\"");
      token=x.substring(0,nextquote + 1);  // token is string to before next quote
      if(localDebug)System.out.println("state 2.1. x=" + x + ", nextquote=" + nextquote);
      token=token.substring(0,token.length()-1);  // remove closing quote
      l.add(token);  
      if(localDebug)System.out.println("token=" + token);
      x = x.substring(x.indexOf("\"")+1);  // remove token
      x = x.substring(x.indexOf(",")+1).trim();  // move start to first char after next comma
      if(localDebug)System.out.println("state 2.2. token=" + token + ", x=" + x);
      lastState=2;
      state=0;
      continue;
      }
    } // end while(x.length > 0)
  if(localDebug)System.out.println("nocomma=" + nocomma + ", noquote=" + noquote + ", lastState=" + lastState);
  if(noquote==false && nocomma ==false && lastState==1)
    {
    l.add("");
    if(localDebug)System.out.println("Added token for trailing comma.");
    }
  String[] fields = new String[l.size()]; 
  fields = l.toArray(fields);
  if(localDebug)for(int y=0; y< fields.length; y++)System.out.println("Field " + y + "=" + fields[y]);
  if(localDebug)System.out.println("*************************************************************");
  return fields;
  }// end method parseCommaQuoted()     
        
       




// Ensure that zipcode is <= 5 characters and can be converted to int.
private boolean validateZip(String zipString)
  {
  String zipTrim=zipString.trim();
  if(zipTrim.length() > 5)
    {
    if(debugFlag)logger.tprintwarn("zipcode is greater than 5 characters.  Zip=" + zipString);
    return false;
    }
  try{this.zipcode = Integer.parseInt(zipTrim);}
  catch(NumberFormatException nfe)
    {
    this.logger.tee("Failed to parse zip string to integer for string=" + zipString, nfe);
    return false;
    }  
  return true;
  }

private boolean validateFloat(String ll, String which)
  {
  float fl;
  if(ll.equals("")) ll="0";
  try{fl=Float.parseFloat(ll);}
  catch(NumberFormatException nfe)
    {
    this.logger.ee("Latitude or longitude value of " + ll + " failed to convert to a float.", nfe);
    return false;
    }
  if(which.equals("lat"))this.latitude=fl;
  else if(which.equals("lon"))this.longitude=fl;
  else if(which.equals("medianAge"))this.medianAge=fl;
  else{logger.e("Programming error in validatell().  which=" + which); return false;}
  return true;
  }


private boolean validateInt(String si, String var)
  {
  int itemp;
  if(si.equals("")) si="0";
  try{itemp=Integer.parseInt(si);}
  catch(NumberFormatException nfe)
    {
    logger.ee("Failed to convert " + var + "=" + si + " to integer.", nfe);
    return false;
    }
  switch (var)
    {
      case "totalPopulation": this.totalPopulation=itemp; break;
      case "houseMedianValue": this.houseMedianValue=itemp; break;  
      case "medianIncome": this.medianIncome = itemp; break;
      case "femalePop": this.femalePop=itemp; break;
      case "malePop": this.malePop=itemp; break;
      case "dominantAreaCode":this.dominantAreaCode=itemp; break;
      default:logger.e("Programming error inn validateInt. var=" + var); return false;
    }
  return true;    
  }



} // end class ZipcodeRecord
