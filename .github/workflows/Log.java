package izFrame;
import java.io.*;       //PrintWriter,File,FileInputStream,FileReader,FileWriter,IOException
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;     //Date; HashMap; Properties; Set;

/** Log is logging and performance measuring utility class. It provides the following functionality:
<ul> <li> simple static logging with functions like <i>err(), warn(), info(), trace(), dbg()</i>
</li><li> highly customisable 'logger type' logging wih functions like <i>error(), warning(), inform(), debug()</i>
</li><li> performance measuring static logging with functions <i>traceStart(), trace(), traceStop()</i> activated when <i>traceTiming</i> flag is set to true
</li></ul>
Class behaviour is highly configurable which is done using configuration property file that is loaded at startup. Two options:
'level' and 'logFileNm' will likely satisfy everyone except the most demanding log customization addicts.
But don't worry if you don't have configuration file - default values should be fine for most applications.
<p>
<b>Simple Static and Instance (per class type) Logging</b> <br/>
Standard logging functions: <i>dbg()/debug(), info()/inform(), warn()/warning(), trace(), err()/error()</i> are not much different
from what you've grown accustomed to see in other logging frameworks. Worth mentioning are the next features:
<ul> <li> Class implements logging using either static or instance functions. Static ones are intended for quick logging solutions
   while the latter are used when high degree of sofistication is required, like large number of messages per class with ability to
   enable/disable logging on per package/class type level. For the latter, one instance of the Log object will be created and
   used for multiple instances of attached class type.
</li><li> static function name is shorter (<i>err(), warn(), info()</i> etc.) than its 'class type' counterpart (<i>error(), warning()</i>, etc.)
   which is reflection of the fact that it is inteneded for quick logging solutions. Statics are also slightly faster.
</li><li> warn()/warning() and err()/error() have overloads that accept Exception as a first parameter. These
   overloads will, beside message, print out exception stack. Stack will be printed out in full, but, if logging to file,
   deepness of stack from the bottom can be regulated with the <i>stackDeepness</i> attribute, default is 7.
</li><li> Class supports rotation of the log file that is based on time only (as it is a dominant selection for enterprise applications
   anyway). Rotation period can vary greatly - yearly, monthly, every 10 days, every day or every hour. Default is daily.
</li><li> log file will be kept open permanently if global (static) Log.level >= INFO (i.e. INFO, TRACE, DEBUG). When Log.level is ERROR or
   WARNING, log file is opened and closed for each logging function invocation.
</li><li> Standard message format is: [timestamp as yy-MM-dd HH:mm:ss.SS] [level: E,W,I,T,D] [message source (instance calls only):] [message].
   <br/>Sample:<br/> <i>03-11-15 15:11:50.43 D com.igor.Mod: constructor called.</i>
</li></ul>
<b>Example</b><pre> <code>
package igor.lana.alex.andrii;
import izFrame.Log;
class MyApp {
   Log log = Log.getLog(this);          //this is a standard way to attach Log instance to some class, in this case to MyApp
   Log log2 = Log.getLog("MY_STUFF");   //alternative way, allows creation of distinct/fancy messages
   MyApp(){
      Log.info("in constructor"); //static call, no need to create Log instance, outputs: <i>05-11-25 23:41:45.26 I in constructor</i>
      log.inform("constructor called");  //outputs: <i>05-11-25 23:41:45.26 I igor.lana.alex.andrii.MyApp: constructor called</i>
      log2.debug("constructor called");  //outputs: <i>05-11-25 23:41:45.26 D MY_STUFF: constructor called</i>
   }
   public static void main(String[] a){
     Log.loadConfig("myApp.props"); //call can be omitted if no need for fancy features or default config file is used
     MyApp mod = new MyApp();
   }
}</code></pre>
<b>Log levels guide and class functions you will likely use more often then the others</b>
<ul> <li> At Log.level==ERROR only err/error(message) and err/error(Exception,message) functions log messages (and exception
   stack if Exception param is present). As name implies, functions are used for logging conditions that <b>must</b> be addressed.
</li><li> Log.level==WARNING functions are for logging conditions that must be kept in mind and may need to be addressed if number
   of them exceeds some threshold.
</li><li> Log.level==INFO functions (info/inform(message)) are used to log important information that will affect program
   execution as a whole, for example, HTTP request parameters.
</li><li> At Log.level==TRACE trace/traceStart/traceStop() message functions become active. They are used mostly for performance
   measuring. Note, trace/traceX() functions have only static version so you cannot configure corresponding options
   (traceTiming, moduleNum, resBlended) on a 'per class' level. Note, when traceTiming==false traceStart/traceStop() invoke
   trace() that produces output similar to other statics - err(), warn(), info(), etc. Put traceX() function calls at the
   start/end of long running or performace critical program sections.
</li><li> dbg/debug(message) - activated when Log level == DEBUG. Obviously, at such log level all functions above log messages too.
</li><li> Log.level==OFF stops any logging.
</li></ul>
<b>Configuring log Level and other features </b> <br/>
Class is configured by adding entries that start with 'Log.' to the configuration file. Configuration option name for static function
immediately follows 'Log.' (for example: Log.level=debug). 'per class type / instance' config options should include class or
package name (for example Log.level@com.Middleware.Accounts=debug).
<br/> - Global (static) configuration options are: level, logFileNm, frequency, logToFile, logToConsole, stackDeepness, classNamesNum
<br/> - 'per class type / instance' configuration options are: level, classNamesNum, loggedClassName, appender
<br/> - two appenders - file and console are always awailable, other can be set in 'per class type / instance' configuration like:
   <br/> Log.appender@com.ibm.lana.alex.mod1=com.utils.emailAppender;com.utils.eventAppender
   <br/> Appenders should implement "boolean logMessage(char level, String loggedClassName, String msg, Exception e)", are called in sequence specified in config.
<br/>As was mentioned above, don't worry much about setting configuration - in vast majority of cases defaults will be fine. If you need to
know more about any specific option, just check javadoc for the correspondingly named member of the Log class. Keep in mind, for static
options coresponding Log class member name will be one prefixed with the underscore, like static <i>_level</i> vs. instance <i>level</i>.
<br/><b>Example</b><br/>
Lets assume izFrame.config (or other you specified in LoadConfig() call) file includes the following Log related options:
<table border=1><tr><th>config option</th><th>Comment</th</tr>
<tr><td>Log.level=trace</td><td>log level will be TRACE for all static function calls and TRACE will be default for instance function calls</td></tr>
<tr><td>Log.level@com.ibm.lana.alex.mod1=debug</td><td>log level is DEBUG for instance function calls on the classes in tree 'com.ibm.lana.alex.mod1'</td></tr>
<tr><td>Log.classNamesNum=3</td><td>instance functions when specifying message source will provide 3 names - root name and two from the bottom </td></tr>
<tr><td>Log.loggedClassName@com.ibm.igor=com_igor</td><td>instance functions when specifying message source will replace 'com.ibm.igor' with 'com_igor'</td></tr>
</table>
<br/>Lets assume number of classes of the following types is created (package name embedded in class name for shortness):
<table border=1>
<tr><th>Java code extract</th><th>Log output</th</tr>
<tr><td>class foo.myApp{main(){Log.info("main starts");Log.trace("main ends")}</td><td>03-11-15 15:11:50.42 I main starts<br/>03-11-15 15:11:50.43 T main ends</td></tr>
<tr><td>class com.ibm.igor{Log l = Log.getLog(this); igor(){l.inform("constructor called")}</td><td>03-11-15 15:11:50.44 I com_igor constructor called</td></tr>
<tr><td>class com.ibm.igor.mod1{...{l.error("constructor called")}  </td><td>03-11-15 15:11:50.45 E com_igor.mod1 constructor called</td></tr>
<tr><td>class com.ibm.mod1     {...{l.debug("constructor called")}  </td><td>03-11-15 15:11:50.46 D com.ibm.mod1 constructor called</td></tr>
<tr><td>class com.ibm.lana.mod1{...{l.inform("constructor called")} </td><td>03-11-15 15:11:50.47 I com..lana.mod1 constructor called</td></tr>
<tr><td>class com.ibm.foo.alex.mod1{...{l.warning("in constructor")}</td><td>03-11-15 15:11:50.48 W com...alex.mod1 in constructor</td></tr>
</table>
At class load time it tries to read configuration from file 'izFrame.config' if one can be found in the application root dir.
If you would like to keep configuration in file named or located differently, your application has to issue call "Log.loadConfig(confFilePath)"
sometime close to startup time, before Log is called for the first time. While providing file path is trivial for standalone applications,
it is a bit more convoluted for applications hosted on application server, say, web applications. In that case call to getRealPath() is required.
For example, you can do the following:
<ul> <li> create izFrame.config file (actual name is your preference) in the application's /WEB-INF dir.
</li><li> in Application or Servlet's init() function call: <i>Log.loadConfig(getServletContext().getRealPath("/WEB-INF/izFrame.config"));</i>
</li><li> if name or path of file can change, use context-param or servlet init-param entry in 'web.xml' file to pass it in:<br/><i>
   &lt;init-param&gt; <br/> &lt;param-name&gt;izFrameProps&lt;/param-name&gt; <br/>
   &lt;param-value&gt;/WEB-INF/izFrame.config&lt;/param-value&gt; <br/> &lt;/init-param&gt;  </i>
</li><li> in Application or Servlet's init() function read that entry, get it's real path then call Log.loadConfig(realPath):<pre><code>
   public class MyServlet extends HttpServlet {
      public void init(){
         String izFrameProps = getInitParameter("izFrameProps"); //reading servlet init-param value "/WEB-INF/izFrame.config"
         Log.loadConfig(getServletContext().getRealPath(izFrameProps));   </code></pre>
</li></ul>
</p><p>
<i><b>You can skip section below if you are not interested in the performance measuring capability of the class</b></i><br/>
Besides bread and butter of logging (debug(), ... error() functions), class implements less typical feature of supporting
performance timing functionality which kicks in when traceTiming flag is set to true and log level >= TRACE. That
transforms trace() function to behave differently, as class starts to track and summarize time between trace() calls and
enables/transforms few more functions:
<ul> <li> traceStart(moduleID) - starts timing specific module. When _traceTiming==false it calls trace() producing
   output similar to other static functions like dbg(), err() differing only by letter T, not D or E in the message.
</li><li> trace(moduleID) - stores intermediate timing result for specific module.
</li><li> traceStop(moduleID) stops timing (when _traceTiming==false, just calls trace()).
</li><li> performance messages have next columns: <br/>
   timestamp, step duration (in secs), cumulative duration from traceStart() call, source/moduleID, step, optional message. Sample:<br/>
   <pre><i>03-11-15 15:11:50.43 | 3.00 | 5.90 | MyBean: MyBean done at 3:06:51 o'clock PM EDT</i></pre>
</li><li> static trace() function has overload that accepts Object o as a first parameter, typically 'this' of the class
   being monitored - handy for those who want to save on typing, it produces output of type o.getClass().getName(): [msg],
   or, if msg==null, o.getClass().getName(): o.toString()
</li></ul>
You can use these functions in 3 different flavors, depending on parameters passed in:
<ul> <li> w/o moduleID parameter - would use default "TraceTime" identifier for output messages
</li><li> when it receives as a parameter Object class pointer, would start independent thread of timing/monitoring this
   object and will use class name for timing thread identification
</li><li> if parameter is String identifying user defined moduleID, traceStart() would start independent thread of
   monitoring which can span across whole application (as long as moduleID stays the same).
</li><li> variant of two parameters are passed in - moduleID and moduleName is typically used when module ID is Class name,
   and module name is function name - used for monitoring function performance that include intermediate steps. Place traceStart()
   at function entry and traceStop() at function exit and use function name as module name.
</li></ul>
Note, we try to limit memory and processing power consumption so total number of traced modules should be less then 256 as
performance information is stored in memory until module's traceStop() call is made.
</p><p>
<b>To reiterate</b>
<ul> <li> Log.traceStart(this) - begins saving performance information, uses class name as monitoring thread ID
</li><li> Log.trace(this) - puts intermediate performance information to corresponding monitoring thread
</li><li> Log.traceStop(this) - dumps performance information to the log file
</li></ul>
<p><b>Example</b>: Company developed Web application which has Servlets, Managers, Beans classes and wants to gather
performance statistics on them. There are 3 developers - Igor, Lana and Alex. Igor is assigned to check overall performance,
Lana is interested to know how her Manager class is doing, Alex needs to know how often and how many times some Bean function is
used. Alex also don't want to learn Log class working (he is attending primary school, but likes to program).
<br>In this scenario, Igor would spread traceX() invocations throughout the whole application passing moduleID='iz',
Lana would invoke trace() in Manager passing 'this' as module ID, Alex would invoke Log.trace() in Bean w/o specifying any module ID.
Code may look like this: <pre> <code>
class WebApplication {
   public static void main(String[] a){
      svt s = new svt();
      s.s3();
      Log.traceClose(); //logMessage tracing info to a file
   }
}
class svt {
   mgr m;
   svt(){ Log.traceStart("iz", "full_flow"); m= new mgr(); Log.traceStart(m);}
   void s1(){Log.trace("iz", "s1: about to call m1()"); m.m1();}
   void s2(){Log.trace("iz", "s2: about to call m2()"); m.m2();}
   void s3(){Log.trace("iz", "s3: about to call s1(), s2()"); s1(); s2(); }
}
class mgr {
   mybean b;
   mgr(){ b = new mybean();} // "bean_flow"
   void m1(){Log.trace(this, "m1: about to call b1()"); b.b1();Log.trace("iz", "m1 done");}
   void m2(){Log.trace(this, "m2: about to call b1()"); b.b2();}
}
class mybean {
   mybean(){ Log.traceStart(); for(int i=0; i<30000000; i++);}
   void b1(){Log.trace();for(int i=0; i<30000000; i++);Log.trace("iz", "exited b2()");}
   void b2(){Log.trace("b2");for(int i=0; i<20000000; i++);}
} </code>
Performance statistics will be like the following (first for Lana, then Alex, then Igor):
<font size=-1>
mgr.1828227: 0.0sec     FromStart: 0.0sec,      : mgr started at 189
    mgr.1828227: 0.0sec  FromStart: 0.0sec,      m1: about to call b1()
    mgr.1828227: 3.0sec  FromStart: 3.0sec,      m2: about to call b1()
mgr.1828227: 1.9sec     FromStart: 4.9sec,      : mgr done at 192

TraceTime: 0.0sec         FromStart: 0.0sec,      : TraceTime started at 186
    TraceTime: 2.9sec      FromStart: 2.9sec,      step 0
    TraceTime: 3.0sec      FromStart: 5.9sec,      b2
TraceTime: 1.9sec         FromStart: 7.9sec,      : TraceTime done at 192

iz: 0.0sec      FromStart: 0.0sec,      : full_flow started at 186
    iz: 2.9sec   FromStart: 2.9sec,      s3: about to call s1(), s2()
    iz: 0.0sec   FromStart: 2.9sec,      s1: about to call m1()
    iz: 3.0sec   FromStart: 5.9sec,      exited b2()
    iz: 0.0sec   FromStart: 5.9sec,      m1 done
    iz: 0.0sec   FromStart: 5.9sec,      s2: about to call m2()
iz: 1.9sec      FromStart: 7.9sec,      : full_flow done at 192
</font>
If Igor would want to, he can set Log._resBlended = true and output would change to:
<font size=-1>
iz: 0.0sec      FromStart: 0.0sec,      : full_flow started at 307
    TraceTime: 0.0sec      FromStart: 0.0sec,      : TraceTime started at 307
       mgr.1828227: 0.0sec       FromStart: 0.0sec,      : mgr started at 310
    iz: 2.9sec   FromStart: 2.9sec,      s3: about to call s1(), s2()
    iz: 0.0sec   FromStart: 2.9sec,      s1: about to call m1()
          mgr.1828227: 0.0sec    FromStart: 0.0sec,      m1: about to call b1()
       TraceTime: 2.9sec   FromStart: 2.9sec,      step 0
    iz: 3.0sec   FromStart: 5.9sec,      exited b2()
    iz: 0.0sec   FromStart: 5.9sec,      m1 done
    iz: 0.0sec   FromStart: 5.9sec,      s2: about to call m2()
          mgr.1828227: 3.0sec    FromStart: 3.0sec,      m2: about to call b1()
       TraceTime: 3.0sec   FromStart: 5.9sec,      b2
       mgr.1828227: 1.9sec       FromStart: 4.9sec,      : mgr done at 313
    TraceTime: 1.9sec      FromStart: 7.9sec,      : TraceTime done at 313
iz: 1.9sec      FromStart: 7.9sec,      : full_flow done at 313
</font>
</p>
For other examples of usage look in test driver at the class end </pre>
@author Igor Zvorygin, AppIntegration Ltd., LGPL as per the Free Software Foundation
@version $Revision: 1.1 $ */ //javadoc -notree -noindex Log.java
public class Log {
   //----- configuration defaults,  can be read from config props -------------
   //--- CONSTANTS ---
   /** log _level constants: OFF=0, ERROR=1, WARNING=2, INFO=3, TRACE=4, DEBUG=5 */
   public static final int OFF=0, ERROR=1, WARNING=2, INFO=3, TRACE=4, DEBUG=5;
   /** log file _frequency rotation constants: HOURLY=11, DAILY=8, DAY10=7, MONTHLY=5, YEARLY=2;
   Numbers are based on index of changing character in _dateFormat */
   public static final int HOURLY=11, DAILY=8, DAY10=7, MONTHLY=5, YEARLY=2;
   /** new line constant */
   private static final String cr = System.getProperty("line.separator");
   /** String date format used for usual logging functionality, default "yy-MM-dd HH:mm:ss.SS" */
   private static String _dateFormat = "yy-MM-dd HH:mm:ss.SS";

   //--- CONFIGURATION OPTIONS ---
   //--- CLASS (static) config values ---
   /** if true, log to file, false - see warnings, errors, info on System.out. Default true */
   public static boolean _logToFile = true;
   /** setting _logToConsole to true would direct log output to screen. Default true */
   public static boolean _logToConsole = true;
   /** actual class log _level, default _level = DEBUG */
   protected static int _level = DEBUG;
   /** actual log file _frequency rotation, default _frequency = DAILY */
   protected static int _frequency = DAILY;
   /** log file name, default is "../logs/izApp.log". If directory does not exist, it will be created. */
   private static String _logFileNm = "../logs/izApp.log";
   /** limits deepness of exception stack logged, used only if _preJDK1_4 = false. Default is 7 */
   public static int _stackDeepness = 7;
   /** global value for max number of dot separated entries in the class name displayed in the log message, default=-1 that is show all.
   Configured in properties as: Log.classNamesNum=... */
   protected static int _classNamesNum = -1;
   //--- TIMING related config values------
   /** true, activates performance timing for trace() type calls. False makes then do normal logging */
   public static boolean _traceTiming = true;
   /** if true, timing results of different modules would be intermixed rather then shown separately */
   public static boolean _resBlended = false;

   //--- INSTANCE config values ---
   /** instance log level, default level = DEBUG */
   protected int level = DEBUG;
   /** attached class type name provided in the Log.getLog("myClass1") or Log.getLog(this) call (this => getClass().getName())) */
   private String loggedClassName = "";
   /** max number of dot separated entries in the class name displayed in the log message starting from the deepest one */
   private int classNamesNum = -1;
   /** some people want to be fancy and use something other than console and file for logging, they can save name(s) of this as
   'appender'. If Log detects one, function processAppenders() would get called before calling logMessage() */
   protected String appenderName;

   //-------- usability and internal -------------------
   /** if true, won't use e.getStackTrace() that appeared in jdk1.4, will use e.printStackTrace(file) instead. Default false */
   protected static boolean _preJDK1_4 = false;
   /** stores rotation important part of file opening date, relies on _df as "yy-MM-dd HH.."*/
   protected static String _startedAt = "";
   /** date format used for usual logging functionality - SimpleDateFormat(_dateFormat) */
   private static DateFormat _df = new SimpleDateFormat(_dateFormat);
   /** additinal date format used for timing (traceStart, trace) functionality */
   private static DateFormat _tf = DateFormat.getTimeInstance(DateFormat.FULL);
   /** handle to output file */
   private static PrintWriter _logFile; //FileWriter
   /** map of instance (attached to specific class type) loggers */
   private static HashMap<String,Log> logMap;
   /** map of instance (attached to specific class type) loggers */
   private static HashMap<String,ArrayList<String>> logConfigMap;
   /** mapping of appender names to classes, contains only appender classes that were already loaded */
   private static Hashtable _loadedAppenderClasses = new Hashtable();
   //--- timing related ---
   /** number of independent threads of monitoring (thread not in a system sense, but rather in common life one) */
   private static int _moduleNum = 256;
   private static long[] _startArr = new long[_moduleNum];
   private static long[] _currArr = new long[_moduleNum];
   private static String[] _moduleIDArr = new String[_moduleNum];
   private static String[] _moduleNmArr = new String[_moduleNum];
   private static StringBuffer[] _msgArr = new StringBuffer[_moduleNum];
   private static int _currIndex = 0;
   private static int _currStep = 0;
   private static DecimalFormat nf = new DecimalFormat(" | ##0.00");

   //--- constructor and 'multitone' pattern implmentattion ---
   /** private constructor - Log class implements so called 'multitone' pattern in which, similarly to 'singleton', you cannot call new() */
   private Log(){}
   /** that's main way to create specialized Log class instance, when param is this returned instance list this class name as a source */
   public static Log getLog(Object cls){
      return getLog(cls.getClass().getName());
   }
   /** that's an alternative and internal way to create specialized Log class instance, param can be getClass().getName() or some fancy like "MY_STUFF" */
   public static synchronized Log getLog(String cls){
      if(logMap == null)
         logMap = new HashMap<String,Log>();
      Log log = logMap.get(cls);
      if(log == null){
         log = new Log();
         config(log, cls); //now configure it: className, loggedClassName, level, classNamesNum
         logMap.put(cls, log);
      }
      return log;
   }

   //#########------ CONFIG FUNCTIONS --------#########
   static { loadConfig(null); } // there are reasons to do it or don't do (leave for app to init). Try default load - cautiously
   /** reads specified configuration property file and stores data in logConfigMap, dynamically reconfigures whatever log instances we
   already have in logMap. If null is passed in, uses config file izFrame.config */
   public static synchronized void loadConfig(String propFileNm) {
      if(propFileNm==null)
         propFileNm = "izFrame.config"; //Utils._propFileNm;
      Properties props = new Properties();
      String propFilePath = null;
      try {//read_appProperties
         propFilePath = new File(propFileNm).getAbsolutePath();
         FileInputStream fis = new FileInputStream(new File(propFilePath));
         props.load(fis);
         fis.close();
         //--A. read global/static config params: level, logFileNm, frequency, ...
         _logFileNm = props.getProperty("Log.logFileNm", _logFileNm);
         _logToFile   = "true".equals(props.getProperty("Log.logToFile",   ""+_logToFile))   ?true:false;
         _logToConsole= "true".equals(props.getProperty("Log.logToConsole",""+_logToConsole))?true:false;
         _traceTiming = "true".equals(props.getProperty("Log.traceTiming", ""+_traceTiming)) ?true:false;
         _resBlended  = "true".equals(props.getProperty("Log.resBlended",  ""+_resBlended))  ?true:false;
         try {_stackDeepness = Integer.parseInt(props.getProperty("Log.stackDeepness", ""+_stackDeepness));} catch(Exception e){}
         try {_classNamesNum = Integer.parseInt(props.getProperty("Log.classNamesNum", ""+_classNamesNum));} catch(Exception e){}
         String tmp = props.getProperty("Log.level", ""+_level);
         if     ("debug".equalsIgnoreCase(tmp) || "5".equalsIgnoreCase(tmp)) _level = DEBUG;
         else if("trace".equalsIgnoreCase(tmp) || "4".equalsIgnoreCase(tmp)) _level = TRACE;
         else if("info".equalsIgnoreCase(tmp)  || "3".equalsIgnoreCase(tmp)) _level = INFO;
         else if("warning".equalsIgnoreCase(tmp)||"2".equalsIgnoreCase(tmp)) _level = WARNING;
         else if("error".equalsIgnoreCase(tmp) || "1".equalsIgnoreCase(tmp)) _level = ERROR;
         else if("off".equalsIgnoreCase(tmp)   || "0".equalsIgnoreCase(tmp)) _level = OFF;
         //_dateFormat = props.getProperty("Log.dateFormat", _dateFormat);
         tmp = props.getProperty("Log.frequency", ""+_frequency);
         if     ("HOURLY".equalsIgnoreCase(tmp) ||"11".equalsIgnoreCase(tmp)) _frequency = HOURLY;
         else if("DAILY".equalsIgnoreCase(tmp)  || "8".equalsIgnoreCase(tmp)) _frequency = DAILY;
         else if("DAY10".equalsIgnoreCase(tmp)  || "7".equalsIgnoreCase(tmp)) _frequency = DAY10;
         else if("MONTHLY".equalsIgnoreCase(tmp)|| "5".equalsIgnoreCase(tmp)) _frequency = MONTHLY;
         else if("YEARLY".equalsIgnoreCase(tmp) || "2".equalsIgnoreCase(tmp)) _frequency = YEARLY;
         //--B. read instance config params: level, loggedClassName, classNamesNum
         Set<String> propList = props.stringPropertyNames();
         ArrayList<String> levArr = new ArrayList<String>(), snArr = new ArrayList<String>(), cnnArr = new ArrayList<String>(), appArr = new ArrayList<String>();
         for(String entry : propList){
            if(entry.startsWith("Log.level@"))
               levArr.add(entry.substring("Log.level@".length()));
            if(entry.startsWith("Log.classShortName@"))
               snArr.add(entry.substring("Log.classShortName@".length()));
            if(entry.startsWith("Log.classNamesNum@"))
               cnnArr.add(entry.substring("Log.classNamesNum@".length()));
            if(entry.startsWith("Log.appender@"))
               appArr.add(entry.substring("Log.appender@".length()));
         }
         Collections.sort(levArr);
         Collections.sort(snArr);
         Collections.sort(cnnArr);
         Collections.sort(appArr);
         logConfigMap = new HashMap<String,ArrayList<String>>();
         logConfigMap.put("level", levArr);
         logConfigMap.put("classShortName", snArr);
         logConfigMap.put("classNamesNum", cnnArr);
         logConfigMap.put("appender", appArr);
         //store values in corresponding 'value' arrays
         ArrayList<String> levArV = new ArrayList<String>(), snArV = new ArrayList<String>(), cnnArV = new ArrayList<String>(), appArV = new ArrayList<String>();
         for(String val : levArr)
            levArV.add(props.getProperty("Log.level@"+val));
         for(String val : snArr)
            snArV.add(props.getProperty("Log.classShortName@"+val));
         for(String val : cnnArr)
            cnnArV.add(props.getProperty("Log.classNamesNum@"+val));
         for(String val : appArr)
            appArV.add(props.getProperty("Log.appender@"+val));
         logConfigMap.put("levelV", levArV);
         logConfigMap.put("classShortNameV", snArV);
         logConfigMap.put("classNamesNumV", cnnArV);
         logConfigMap.put("appenderV", appArV);
         if(logMap != null){//correct whatever log configs we had in the map before, so static loggers (static Log log = Log.getLog(..)) would work OK
            Set<String> names = logMap.keySet();
            for(String logName : names){
               Log log = logMap.get(logName);
               config(log, logName); //actually this functionality is quite useful to reconfigure all loggers on the fly
            }
         }
         System.out.println("Log.loadConfig() succesfully loaded configuration from file "+propFilePath);
     } catch (Exception e) {
         if(!"izFrame.config".equals(propFileNm)) e.printStackTrace(); //don't print err stack for deafult config file
         System.out.println("Log.loadConfig() could not load file "+(propFilePath==null?propFileNm:propFilePath)+", will use class defaults");
      }
   }

   /** configures (level, classNamesNum, loggedClassName) specialized Log class instance, uses findBestMatch() which in turn uses logConfigMap */
   private static void config(Log log, String cls){
      String tmp = findBestMatch("level", cls, ""+_level);
      if     ("debug".equalsIgnoreCase(tmp) || "5".equalsIgnoreCase(tmp)) log.level = DEBUG;
      else if("trace".equalsIgnoreCase(tmp) || "4".equalsIgnoreCase(tmp)) log.level = TRACE;
      else if("info".equalsIgnoreCase(tmp)  || "3".equalsIgnoreCase(tmp)) log.level = INFO;
      else if("warning".equalsIgnoreCase(tmp)||"2".equalsIgnoreCase(tmp)) log.level = WARNING;
      else if("error".equalsIgnoreCase(tmp) || "1".equalsIgnoreCase(tmp)) log.level = ERROR;
      else if("off".equalsIgnoreCase(tmp)   || "0".equalsIgnoreCase(tmp)) log.level = OFF;
      log.loggedClassName = findBestMatch("classShortName", cls, cls);
      tmp = findBestMatch("classNamesNum", cls, ""+_classNamesNum);
      try {log.classNamesNum = Integer.parseInt(tmp);} catch(Exception e){}
      if(log.classNamesNum > -1) {//more adjustments to loggedClassName: com.ibm.foo.alex ->3-> com..foo.alex
         String[] lst = log.loggedClassName.split("\\."); //com ibm foo alex
         if(lst.length >= log.classNamesNum){
            StringBuffer buf = new StringBuffer();
            for(int i=0; i<lst.length; i++){
               if(i==0) {
                  if(log.classNamesNum > 0) buf.append(lst[0]); //put first entry: com
                  int dotLen = (lst.length-log.classNamesNum > 16) ? 21: lst.length - log.classNamesNum; //lets be reasonable
                  buf.append("................more..".substring(0, dotLen)); //4-3=1->com.
                  i = lst.length - log.classNamesNum; // i=0 ->i=1, after ++ becomes 2, i.e 'foo'
               } else
                  buf.append(".").append(lst[i]); //put next entry: -> com..foo
            }
            log.loggedClassName = buf.toString();
         }
      }
      log.appenderName = findBestMatch("appender", cls, null);
   }

   /** finds best match config value of entryNm in properties. Best match is the longest that is found in 'start' of classNm. */
   protected static String findBestMatch(String entryNm, String classNm, String defaultVal) {
      if(logConfigMap == null) //means no config file load
         return defaultVal;
      String ret = defaultVal;
      ArrayList<String> confKeyArr = logConfigMap.get(entryNm); //level, loggedClassName, classNamesNum
      for(int i=confKeyArr.size()-1; i>=0; i--){
         if(classNm.startsWith(confKeyArr.get(i))){ //that should be the largest matching entry found in config
            ret = logConfigMap.get(entryNm+"V").get(i);
            if("classShortName".equals(entryNm)) { //special: cls can be descendant, need loggedClassName adjustments
               ret = classNm.replaceFirst(confKeyArr.get(i), ret);
            }
            break;
         }
      }
      return ret;
   }

   //#########------ LOGGING FUNCTIONS --------#########
   //-------- Standard logging -------------------
   //--error--
   /** logs error, i.e. something like serious business logic, data or software related problem. Active when global log level is ERROR */
   public static void err(String msg){
      if(_level >= ERROR) logMessage('E', msg, null);
   }
   /** logs error if Exception was thrown, mostly for system errors or severe business errors. */
   public static void err(Exception e, String msg){//
      if(_level >= ERROR) logMessage('E', msg, e);
   }
   /** instance method for error logging, active when 'class type specific' log level is ERROR or above */
   public void error(String msg){
      if(level >= ERROR) processAppenders('E', msg, null);
   }
   /** instance method used for error logging when Exception was thrown, active when 'class type specific' log level is ERROR or above */
   public void error(Exception e, String msg){
      if(level >= ERROR) processAppenders('E', msg, e);
   }

   //--warning--
   /** logs warning, i.e. something like business logic related problem */
   public static void warn(String msg){
      if(_level >= WARNING) logMessage('W', msg, null);
   }
   /** logs warning when Exception was thrown (used mostly for recoverable system warnings). */
   public static void warn(Exception e, String msg){//
      if(_level >= WARNING) logMessage('W', msg, e);
   }
   /** instance method for warnings logging, active when 'class type specific' log level is WARNING or above */
   public void warning(String msg){
      if(level >= WARNING) processAppenders('W', msg, null);
   }
   /** instance method for warnings logging when Exception was thrown */
   public void warning(Exception e, String msg){
      if(level >= WARNING) processAppenders('W', msg, e);
   }

   //--info, debug--
   /**logs other information of possible interest, active when global log level is INFO or above */
   public static void info(String msg){
      if(_level >= INFO) logMessage('I', msg, null);
   }
   /** instance method for other information logging, active when 'class type specific' log level is INFO or above*/
   public void inform(String msg){
      if(level >= INFO) processAppenders('I', msg, null);
   }
   /** logs message about details of program execution, active when global log level is DEBUG */
   public static void dbg(String msg){
      if(_level >= DEBUG) logMessage('D', msg, null);
   }
   /** instance method for logging message about details of program execution, active when 'class type specific' log level is DEBUG */
   public void debug(String msg){
      if(level >= DEBUG) processAppenders('D', msg, null);
   }

   /** calls appender's logMessage() method. If it returns false, next appender's logMessage() or Log.logMessage() is called.
   Appender is supposed to implement "boolean logMessage(char level, String loggedClassName, String msg, Exception e)". Appenders
   are supposed to be initialed in a way specific to them, Log does not care which one. For example, write() method may check for
   'configDone' flag and if is false, read config file. */
   public void processAppenders(char level, String msg, Exception e){
      if(appenderName != null) {
         String[] appendersArr = appenderName.split(";"); //support multiple appenders, semicolon separated
         for(String apprNm: appendersArr){
            Object appenderObj = _loadedAppenderClasses.get(apprNm);
            try {
               if(appenderObj == null) { //it may be first call to this appender - load it and create instance
                     Class appenderClass = Class.forName(apprNm); //load appender class - ClassNotFoundException, NoClassDefFoundError
                     appenderObj = appenderClass.newInstance(); //throws InstantiationException, IllegalAccessException
                     _loadedAppenderClasses.put(apprNm, appenderObj);
               }
               if(appenderObj != null) {
                  java.lang.reflect.Method m = appenderObj.getClass().getMethod("logMessage", char.class, String.class, String.class, Exception.class);
                  Object res = m.invoke(appenderObj, level, loggedClassName, msg, e );
                  if(res instanceof Boolean && (Boolean)res == Boolean.TRUE) //if appender.write() returns false stop processing
                     return;
               }
            }
            catch (Throwable t) {  t.printStackTrace();      }
         }
      }
      logMessage(level, loggedClassName+": "+msg, e);
   }

   /** displays msgCore or writes it to the log file, also provides file rotation if necessary. */
   protected static synchronized void logMessage(char level, String msgCore, Exception e) {
      String tmp = _df.format(new java.util.Date());
      if(tmp.length() > 20) tmp = tmp.substring(0,20);
      String msg = level==' '? msgCore : tmp+" "+level+" "+msgCore;
      if(_logToConsole) {
         System.out.println(msg);
         if(e != null){
            //System.out.println("\t"+e.getClass().getName()+", msg: "+e.getMessage()); - unneeded as printStackTrace() does something similar
            Throwable cause = e;
            while(cause != null){
               cause.printStackTrace();
               cause = cause.getCause();
               if(cause != null)
                  System.out.println("\t which in turn is caused by "+cause.getClass().getName()+": "+cause.getMessage());
            }
         }
      }
      if(_logToFile) { //use file for output
         try {
            //1. Open existing or create new log file.
            //expected msg starts with "yy-MM-dd HH:mm:ss.S", use for currentAt variable
            String currentAt = msg.substring(0,_frequency);
            if(_logFile==null){//initialize and read _startedAt
               //System.out.println("1.currentAt="+currentAt+", _startedAt="+_startedAt);
               _startedAt = "";
               File fileTmp = new File(_logFileNm);
               File logDir = new File(fileTmp.getParent());
               if(!logDir.exists())
                 logDir.mkdirs();
               if(!fileTmp.exists()){
                  _startedAt = currentAt;
               }
               //2. Read logging start date and time
               else { //read _startedAt chars
                  FileReader rdr = new FileReader(fileTmp);
                  char[] cbuf = new char[_frequency];
                  if(rdr.read(cbuf, 0, _frequency) != -1)
                     _startedAt = new String(cbuf);
                  else //i.e. end of file reached before we get needed chars
                     _startedAt = currentAt;
                  rdr.close();
               }
               _logFile = new PrintWriter(new FileWriter(_logFileNm, true), true);//append, flush
               //System.out.println("2.currentAt="+currentAt+", _startedAt="+_startedAt);
            }
            //3. do log file rotation (if necessary)
            if(msg!=null && msg.length()>_frequency && !msg.substring(0,_frequency).equals(_startedAt)) {
               //System.out.println("3a.currentAt="+currentAt+", _startedAt="+_startedAt);
               _logFile.close(); //rotating. needs to close and rename
               _startedAt = _startedAt.replace('-', '_');//"yy-MM-dd HH"
               _startedAt = _startedAt.replace(' ', '_');//"yy_MM_dd_HH"
               File fileTmp = new File(_logFileNm);
               fileTmp.renameTo(new File(_logFileNm+"."+_startedAt+".old"));
               //System.out.println("3b.currentAt="+currentAt+", _startedAt="+_startedAt);
               _startedAt = currentAt;
               _logFile = new PrintWriter(new FileWriter(_logFileNm, true), true);//append, flush
            }
            //4. Finally - do the actual file writing
            _logFile.println(msg);
            if(e != null) {
               if(_preJDK1_4)
                  e.printStackTrace(_logFile);
               else {
                  try {
                     StringBuffer b = new StringBuffer("\t").append(e.getClass().getName()).append(": ").append(e.getMessage());
                     Throwable cause = e;
                     while(cause != null){ //build exception stack: first entry, count of skipped, remainder as per stackDeepness
                        StackTraceElement[] list = e.getStackTrace(); //since JDK 1.4
                        for(int i=0; i<list.length; i++){                        //let be: list=4 (a.b.c.d), stackDeepness=3, expect a|cd
                           if(i==0) {
                              if(_stackDeepness > 0) b.append(cr).append("\t\t").append(list[i].toString()); //put first entry
                              if(list.length>_stackDeepness && _stackDeepness>1){
                                 b.append(cr).append("\t\t\t..."+(list.length-_stackDeepness)+" more ..." ); //put 'extra count' line: '1'
                                 i = list.length-_stackDeepness; // i=0 ->i=1, after ++ becomes 2, i.e. points to 'c'
                              }
                           } else
                              b.append(cr).append("\t\t").append(list[i].toString()); //put next entry: 'c' then 'd'
                        }
                        cause = cause.getCause();
                        if(cause != null)
                           b.append(cr).append("\t which in turn is caused by "+cause.getClass().getName()+": "+cause.getMessage());
                     }
                     _logFile.println(b.toString());
                  }
                  catch(Exception writeEx){//can be because of pre 1.4 Java
                     e.printStackTrace(_logFile);
                     _preJDK1_4 = true;
                     System.out.println("mode was changed to '_preJDK1_4' because of the exception:");
                     writeEx.printStackTrace();
                     _logFile.println("mode was changed to '_preJDK1_4' because of the exception:");
                     writeEx.printStackTrace(_logFile);
                  }
               }
            }
            _logFile.flush();
            if(_level < INFO) //closeFileOnExit
               _logFile.close();
          }
          catch(Exception se){
            if(se instanceof SecurityException)
               System.out.println("\tCannot create directory or file "+_logFileNm+" "+se.getLocalizedMessage());
            else if(se instanceof IOException)
               System.out.println("\tCannot write to file "+_logFileNm+" "+se.getLocalizedMessage());
            if(_logToFile && !_logToConsole) { //in this case msg was not displayed, do it now
               System.out.println(msg);
               if(e != null) e.printStackTrace();
            }
            _logToFile=false;
          }
      }
   }

   //-------- Tracing/timing -------------------
   /** start timing the default "TraceTime" module */
   public static void traceStart() {
      if(_level >= TRACE) traceStart((Object)null);
   }
   /** start timing for the Object (moduleID will be based on object class name) */
   public static void traceStart(Object obj) {
      if(_level < TRACE) return;
      String[] nm = new String[2];//objName, shortObjNm
      genName(obj, nm);
      //System.out.println("traceStart(): nm="+nm[0]+", shNm="+nm[1]);
      traceStart(nm[1], nm[0]);
   }
   /** start timing the module with specific ID and name. ID can be anything, for example "MyModule"
   Another useful approach is to use class name as moduleID and function name as moduleName
   and then to invoke traceStart() at function entry and traceStop() at function exit */
   public static synchronized void traceStart(String moduleID, String moduleName) {
      if(_level < TRACE) return;
      if(!_traceTiming) {
         logMessage('T', "module "+moduleID+" started", null);
         return;
      }
      if(moduleName==null) moduleName="";
      if(findModuleIndex(moduleID)!=-1) return; //already started
      if(_currIndex < _moduleNum){
         Date currDt = new java.util.Date();
         long curr = currDt.getTime(); //number of milliseconds since 1970
         _startArr[_currIndex] = curr;
         _currArr[_currIndex] = curr;
         _moduleIDArr[_currIndex] = moduleID;
         _moduleNmArr[_currIndex] = moduleName;
         _msgArr[_currIndex] = new StringBuffer(512);
         int idx = _currIndex;
         if(_resBlended) idx = 0; //when blended, all results go to the single stream
         //_msgArr[idx].append("\n"+moduleID+": "+moduleName+ " started at "+(curr/1000)%1000+"\n");
         _currIndex++;
         trace(moduleID, moduleName+" started at "+_tf.format(currDt), false);
      }
      else {
         warn(moduleName+" tracing cannot be started, too many modules already");
      }
   }

   /** intermediate timing for the default module and default message like step1, step2, etc. */
   public static void trace() {
      if(_level >= TRACE) trace("step "+_currStep++);
   }
   /** intermediate timing for the default module but with some specific message */
   public static void trace(String msg) {
      if(_level >= TRACE) trace((Object)null, msg);
   }
   /** intermediate timing for the declared object and default message like step1, step2, etc. */
   public static void trace(Object obj) {
      if(_level >= TRACE) trace(obj, "step "+_currStep++);
   }
   /** intermediate timing for the declared object and with the passed in message */
   public static void trace(Object obj, String msg) {
      if(_level < TRACE) return;
      String[] nm = new String[2];//objName, shortObjNm
      genName(obj, nm);
      //System.out.println("in trace(Object obj, String msg), obj="+obj+" msg="+msg+" nm1="+nm[1]);
      trace(nm[1], msg);
   }
   /** intermediate timing for the specified module and with the passed in message
   does trace shifting message one 'tab' to the right */
   public static void trace(String moduleID, String msg) {
      if(_level >= TRACE) trace(moduleID, msg, true);
   }
   /** main 'trace() - intermediate timing for the specified module and with the passed in
   message does trace shifting message one 'tab' to the right if 'tab' parameter is 'true' */
   public static void trace(String moduleID, String msg, boolean tab) {
      //System.out.println("entered trace(String moduleID, String msg)");
      if(_level < TRACE) return;
      if(!_traceTiming) {
         logMessage('T', "module "+moduleID+" "+msg, null);
         return;
      }
      int moduleIdx = findModuleIndex(moduleID);
      //System.out.println("in trace(mdl, msg), mdl="+moduleID+" msg="+msg+" idx="+moduleIdx);
      if(moduleIdx >= 0 && moduleIdx < _currIndex){
         Date currDt = new java.util.Date();
         long curr = currDt.getTime();
         long start = _startArr[moduleIdx];
         long prev = _currArr[moduleIdx];
         double frPr = ((double)(curr-prev ))/1000;  //from previous
         double frSt = ((double)(curr-start))/1000; //from start
         int idx = moduleIdx;
         String offset = tab ? "  " : "";
         if(_resBlended){
            idx = 0; //when blended, all messages go to a single stream with index 0
            for(int i = 0; i < moduleIdx; i++) offset += "  ";//2 spaces shift for each new module
         }
         String dt = _df.format(currDt);
         if(dt.length() > 20) dt = dt.substring(0,20);
         _msgArr[idx].append(dt+nf.format(frPr)+nf.format(frSt)+" | "+offset+moduleID+" : "+msg+cr);
         _currArr[moduleIdx] = curr;
      }
   }

   /** stop timing  for the default "TraceTime" module */
   public static void traceStop() {
      if(_level >= TRACE) traceStop("TraceTime");
   }
   /** stop timing for the declared Object (uses Object class name) */
   public static void traceStop(Object obj) {
      if(_level < TRACE)  return;
      String[] nm = new String[2];//objName, shortObjNm
      genName(obj, nm);
      traceStop(nm[1]);
   }
   /** stop timing for the specified module (uses moduleID) */
   public static synchronized void traceStop(String moduleID) {
      if(_level < TRACE) return;
      if(!_traceTiming) {
         logMessage('T', "module "+moduleID+" stopped", null);
         return;
      }
      int free = findModuleIndex(moduleID);
      //System.out.println("traceStop(): free="+free+", _currIndex="+_currIndex);
      if(free>=0 && free < _currIndex) {
         trace(moduleID, _moduleNmArr[free]+" done at "+_tf.format(new Date()), false);
         if(!_resBlended) {//when blended, we will continue appending results to stream 0
            logMessage(' ', _msgArr[free].toString(), null); //trace() displays all results of the module
         }
      }
      while(free!=-1 && free < _currIndex){ //shift free indexes
         _startArr[free]    = _startArr     [free + 1];
         _currArr[free]     = _currArr      [free + 1];
         _moduleIDArr[free] = _moduleIDArr  [free + 1];
         _moduleNmArr[free] = _moduleNmArr  [free + 1];
         if(!_resBlended) {//when blended, we will continue appending results to stream 0
            _msgArr[free]      = _msgArr       [free + 1];
         }
         free++;
      }
      if(free>=0) _currIndex--;
   }
   /** stop timing for all modules, logMessage collected data to a file */
   public static synchronized void traceClose() {
      if(_level < TRACE) return;
      for(int i = _currIndex-1; i >=0; i--){
         //System.out.println("traceClose(): closing i="+i);
         traceStop(_moduleIDArr[i]);
      }
      if(_resBlended) {//when blended, all results were in the single stream [0]
         logMessage(' ', _msgArr[0].toString(), null);
      }
   }

   //---- private tracing utility functions -----------------
   /** generates moduleID based on the Object class passed in */
   private static void genName(Object obj, String[] nm) {
      //System.out.println("entered genName(): nm[0]="+nm[0]+", nm[1]="+nm[1]);
      String objName = null;
      String shortObjNm = null;
      if(obj==null){
         objName = shortObjNm = "TraceTime";
      }
      else {
         objName = obj.getClass().getName();
         shortObjNm = objName.substring(objName.lastIndexOf(".")+1)+obj.hashCode();//class name + obj hash
      }
      nm[0] = objName;
      nm[1] = shortObjNm;
      //System.out.println("exit genName(): nm[0]="+nm[0]+", nm[1]="+nm[1]);
   }
   /** find if this module already started and return the index of it or -1 */
   private static int findModuleIndex(String moduleID) {
      int index = -1;
      for(int i = 0; i < _currIndex; i++){
         if(_moduleIDArr[i].equalsIgnoreCase(moduleID)){
            //System.out.println("i="+i+", moduleID="+moduleID);
            index = i;
            break;
         }
      }
      //System.out.println("index="+index);
      return index;
   }

   //--------- TEST DRIVER -------------
   /** main() is a test driver */
   public static void main(String[] a){
      Log.loadConfig("/_igor/code/izFrame/vCurrent/src/izFrame/izFrame.config"); //loadConfig(null)->/_igor/code/izFrame/izFrame.config
      info("main starts");
      Log log1 = Log.getLog("com.ibm.igor.mod.foo");
      log1.debug("test1");
      Log log2 = Log.getLog("com.ibm.lana.mod.foo");
      log2.debug("test2");
      Log log3 = Log.getLog("alex.com.ibm.lana.mod.foo");
      log3.debug("test3");
      warn(new Exception("ex1"), "testing Exception log");
      Integer o = new Integer(1);   //we will use this as object to wait on and as object to trace on
      traceStart();                    //start on default
      traceStart(o);                      //start on object
      traceStart("class Log", "main()");     //start classic
      synchronized(o) {try{o.wait(500);}catch(InterruptedException e){}}//for(int i=0; i<1000000000; i++);
      trace();                         //default: msg1
      trace(o);                           //object: use default message - step1
      trace("class Log", "main() msg1");     //classic: msg1
      info("main middle");
      synchronized(o) {try{o.wait(1500);}catch(InterruptedException e){err(e, "testing InterruptedException");}}
      trace("msg2");                   //default: override default message, use msg2
      trace(o);                           //object: use default message - step2
      trace("class Log", "main() msg2");     //classic: use custom message msg2
      synchronized(o) {try{o.wait(500);}catch(InterruptedException e){}}
      System.gc();
      traceStop();
      traceStop(o);
      traceStop("class Log");
      //svt s = new svt();  s.s3();
      traceClose();
      info("main ends");
   }
}
