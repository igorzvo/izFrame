package izFrame;
import java.io.BufferedReader;   //buffered reader of character files
import java.io.File;             //file descriptor
import java.io.FileInputStream;  //reader of raw bytes, Properties loader
import java.io.FileReader;       //reader of character files, in for buffered reader
import java.io.FileWriter;       //writer of character files
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Properties;

/** Class provides centralized access to configuration property file and few general utility methods, can play role of
application context.
Class can provide access to few configuration files in parallel. For example, if DBservice class needs to connect to many
databases it may use one property file for each connection description. Loaded Properties objects will be stored in
memory to limit file access operations.
<br/><b>Major class functions</b> you will probably use more often then the others are:
<ul> <li> Utils._propFileNm = propFilePath - sets default property file for the application, do at the application startup
</li><li> Utils.getProperty(filePath,propEntry,defaultValue) - reads value from specified property file or environment
</li><li> Utils.writeFile(text,fileNm,boolAppend) - writes text to character file, appending if last param is true
</li><li> Utils.setAttribute("myObj", myObj) - stores object in attributes hastable
</li><li> myObj = (MyObj)Utils.getAttribute("myObj", myObj) - retrievs object from attributes hastable
</li></ul>
You may find useful sample property file provided below as a template for your own application.
<pre>
#-----------  DBservice config data      ----------
# if true, JNDI connection will be used, otherwise JDBC
DBservice.connectJNDI = true
DBservice.dbInstanceNm = ccpmsoDB

#--------- Log class configuration defaults -------------
# if true, log to file, false - see warnings, errors, info on System.out. Default true
Log.useFile = false
# actual log _level: ERROR=1, WARNING=2, INFO=3, TRACE=4, DEBUG=5
Log.level = DEBUG
#--- timing related ------
# traceTiming activates performance timing for trace() type calls. False makes trace() do normal logging
Log.traceTiming = true

#-----------  Web config data      ----------
Web.APP_TITLE = izFrameTutor
Web.APP_WELCOME_PAGE = logon.jsp
Web.APP_LOGIN_PAGE = logon.jsp
</pre>
@author Igor Zvorygin, AppIntegration Ltd., LGPL as per the Free Software Foundation.
Created Nov 2003, modified Oct - Dec 2005 */
public class Utils {
   /** name of default property file to read. Re-assign value in static initializer of your application */
   public static String _propFileNm = "izFrame.config";
   /** map of property file names and coppesponding Properties. */
   protected static Hashtable _propertiesMap = new Hashtable();
   /** hashtable of objects stored in Utils as attributes */
   protected static Hashtable _attributesMap = new Hashtable();

   // -------- attrib utils ----------------------------
   /** Application context functionality - stores object in attributes hastable for subsequent retrieval by getAttribute()*/
   public static void setAttribute(String attrName, Object obj) {
      _attributesMap.put(attrName, obj);
   }
   /** Application context functionality - retrievs object from attributes hastable */
   public static Object getAttribute(String attrName) {
      return _attributesMap.get(attrName);
   }

   // -------- Property utils ----------------------------
   //static { loadPropFile(_propFileNm);  } - don't do, use lazy approach

   /* one of main methods, reads value from default config property file or environment */
   public static String getProperty(String nm, String defaultVal){
      return getProperty(null, nm, defaultVal);
   }
   public static String getProperty(String propFileNm, String nm, String defaultVal){
      if(propFileNm==null)
        propFileNm = _propFileNm;
      Properties props = (Properties)_propertiesMap.get(propFileNm);
      if(props == null){
         props = loadPropFile(propFileNm);
      }
      return readProp(props, nm, defaultVal);
   }
   public static String getPropertyEnv(String nm, String defaultVal){
      return readProp(null, nm, defaultVal);
   }

   /** reads properties file and returns created Properties object */
   public static synchronized Properties loadPropFile(String propFileNm) {
      Properties props = new Properties();
      String propFilePath = null; //use purely for logging
      try {//read_appProperties
         File file = new File(propFileNm);
         propFilePath = file.getAbsolutePath();
         if(!file.isFile()) {
            Log.err("Utils.loadPropFile(): there are no such file: "+propFilePath+", will use default prop values");
            return props;
         }
         Log.info("Utils.loadPropFile(): reading propFileNm="+propFilePath);
         FileInputStream fis = new FileInputStream(file);
         props.load(fis);
         fis.close();
      } catch (Exception e) {
         Log.err(e, "Utils.loadPropFile() could not load file "+propFilePath+", will use default prop values");
      }
      _propertiesMap.put(propFileNm, props);
      return props;
   }
   /** finds value of entry denoted by 'nm' in properties or in system environment.
   If entry not found, uses passed-in default */
   protected static String readProp(Properties props, String nm, String defaultVal) {
      String val = null;
      if(props!=null)
         val = (String)props.get(nm);
      else// if(_useJavaEnvParams)
         val = System.getProperty(nm, null);
      //Log.debug("readProp(): name="+nm+", val="+val);
      if(val == null){
         Log.warn("Utils.readProp(): entry '"+nm+"' not found, using default="+defaultVal);
         val = defaultVal;
      }
      return val;
   }

   // -------- XML Utils ----------------------------
   /** util: reads array of XML elements from string like <izFrame.mydao><fld1>val1</fld1><fld2>val2<..*/
   public static String[] readXMLelements(String elemNm, String xml) {
      int start = 0, end = 0;
      String strStart = "<"+elemNm+">", strEnd = "</"+elemNm+">";
      ArrayList larr = new ArrayList();
      while((start=xml.indexOf(strStart))>-1 && (end=xml.indexOf(strEnd))>-1 && end>start){
         start += strStart.length();               //move beyond start tag
         larr.add(xml.substring(start, end));      //add found element to string array
         xml = xml.substring(end+strEnd.length()); //move xml beyond element
      }
      return (String[])larr.toArray(new String[larr.size()]);
   }

   /** returns true and adjusts values in resLimits array if bytes of tokBytes sequence can be found in inBytes sequence
   between offset and maxb. If tokBytes==null, end of line (either of Unix type \r\n Unix or Windows one \n) is assumed,
   which comes handy for parsing byte sequences that use end of line to separate tokens of interest. Value of maxb==-1
   means "read inBytes to the end".
   <br/> Elements of resLimits array are adjusted in the following manner:
   <br/> - resLimits[0] will point to first byte after SI (sequence of interest), which is first byte matching to token
         bytes or \r if sequence ends in \r\n, or on \n is sequence ends in \n. resLimits[0]-offset will equal length of
         SI sequence.
   <br/> - resLimits[1] will point to first byte after matching tokBytes. Would this be \r of "\r\n" sequence or single
         "\n", resLimits[1] will be incremented to point beyond these bytes.
   <br/> Examples:
   <br/> If inBytes sequence is "abcde", offset=0 and tokBytes is "abc", resLimits[0]=0=offset, resLimits[1]=3, SI=""
   <br/> If inBytes sequence is "_abc\r\nd", offset=1 and tokBytes is "bc\r", resLimits[0]=2, resLimits[1]=6, SI="a"
   <br/> If inBytes sequence is "abc\nd", offset=0 and tokBytes is null, resLimits[0]=3, resLimits[1]=4, SI="abc"   */
   public static boolean findByteToken(byte[] inBytes, int offset, int maxb, byte[] tokBytes, int[] resLimits) {
      if(tokBytes == null)
         tokBytes = new byte[]{'\n'};
      if(maxb == -1)
         maxb = inBytes.length;
      //Log.debug("findByteToken: inBts="+inBytes+", offset="+offset+", maxb="+maxb+", tokBts.length="+tokBytes.length);
      //Log.debug("\t offset="+offset+", resLimits[0]="+resLimits[0]+", resLimits[1]="+resLimits[1]);
      if(inBytes==null || offset<0 || maxb<0 || (maxb-offset)<tokBytes.length)
         return false;
      outer:
      for(int i=offset; i<=(maxb-tokBytes.length); i++){
         int j=0;
         for(; j<tokBytes.length; j++){
            if(inBytes[i+j]!=tokBytes[j])
               continue outer;
         }
         resLimits[0] = i;  //strictly speaking should be i-1, but we want to point to next byte after
         resLimits[1] = i+j;//note, j will be already incremented at this point
         //Log.debug("\t found-pre: offset="+offset+", resLimits[0]="+resLimits[0]+", resLimits[1]="+resLimits[1]);
         //Log.debug("\t byte at offset="+inBytes[offset]+", [0]="+inBytes[resLimits[0]]+", [1]="+inBytes[resLimits[1]]);
         //Log.debug("\t about to exclude \\r\\n in resLimits[0] for non empty line");
         if(resLimits[0]>0 && inBytes[resLimits[0]-1]=='\n')  // -1 because we point [1] to next byte after token end
            resLimits[0]--;
         if(resLimits[0]>0 && inBytes[resLimits[0]-1]=='\r')
            resLimits[0]--;
         //Log.debug("\t about to skip \\r\\n,  in resLimits[1], line is "+(i==offset?"":" not")+" empty");
         if((tokBytes.length>1&&tokBytes[tokBytes.length-2]!='\r') && inBytes[resLimits[1]]=='\r')//ctl-M 13 CR CARR RET
            resLimits[1]++;
         if(tokBytes[tokBytes.length-1]!='\n' && inBytes[resLimits[1]] == '\n') //ctl-J 10 A  LF LINE FEED
            resLimits[1]++;
         //Log.debug("\t found-aft: offset="+offset+", resLimits[0]="+resLimits[0]+", resLimits[1]="+resLimits[1]);
         //Log.debug("\t byte at offset="+inBytes[offset]+", [1]="+inBytes[resLimits[0]]+", [2]="+inBytes[resLimits[1]]);
         return true;
      }
      //Log.debug("\t not found: offset="+offset+", resLimits[0]="+resLimits[0]+", resLimits[1]="+resLimits[1]);
      return false;
   }

   /** reads character file and returns created String object. Logs error if any encountered. */
   public static String readFile(String fileNm) {
      StringBuffer buf = new StringBuffer();
      try {//read_appProperties
         BufferedReader in = new BufferedReader(new FileReader(fileNm));
         String line = null;
         while((line=in.readLine()) != null){
            buf.append(line);
         }
         in.close();
      } catch (IOException e) {
         Log.err(e, "Utils.readFile() cannot load file "+fileNm);
      }
      return buf.toString();
   }
   /** writes character file given String object. In case or error, logs it and returns false. */
   public static boolean writeFile(String text, String fileNm) {
      return writeFile(text, fileNm, false);
   }
   /** writes/appends character file given String object. In case or error, logs it and returns false.*/
   public static boolean writeFile(String text, String fileNm, boolean append) {
      try {//read_appProperties
         FileWriter out = new FileWriter(fileNm, append);
         out.write(text);
         out.close();
      } catch (IOException e) {
         Log.err(e, "Utils.writeFile() cannot write to file "+fileNm);
         return false;
      }
      return true;
   }

   /** test driver */
   //public static void main(String[] args) {}
}
