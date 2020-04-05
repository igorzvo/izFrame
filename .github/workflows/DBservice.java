package izFrame;
import java.io.*;    //File;FileInputStream;
import java.sql.*;   //Connection;DriverManager;ResultSet;SQLException;Statement;Types
import java.util.*;  //Enumeration;HashMap;Hashtable;Iterator;Properties;StringTokenizer;
import javax.sql.DataSource;

/** DBservice class provides services of:
<ul> <li> allocating, configuring and closing JDBC or JNDI Database connections, loading database drivers;
</li><li> wrapping query and update calls with a set of convenient functions like query(), queryInt(), queryString(),
   execRowChange(), execRowInsert(), execUpdate(), etc..
</li><li> handy utility function of formatting text for DB operations - doubleBackslashAndApostroph(textField).
</li></ul>
Class can function in two modes:
<ul> <li> Explicit connect - connection is opened explicitely, used for few operation and also explicitely closed by the user.
   This is the default mode, recommended if connection pooling is not provided either by DB driver or Application server.
</li><li> Implicit connect - connection is opened and closed automatically for each query/insert/update/delete operation. Handy if
   connection pooling is provided externally. Mode is set with configuration entry: "DBservice.autoConnect = true", or connection
   type is 'jndi' or connection is opened using default constructor like "ResultSet rs = new DBservice().query("select * from dbo.orders");"
</li></ul>
<b>Configuring DBservice class </b> <br/>
For JNDI connection to happen you only need to supply the JNDI name of corresponding resource and application server will
take care of the rest.
<br/>For JDBC connection there is more work to be done:
<ul> <li> we have to load proper Java driver
</li><li> driver (actually DriverManager) needs to be called providing driver specific connection information </li></ul>
You provide DBservice class with information required to load coresponding Java driver by executing
putDriverConfigData(dbType, DriverClassName, URLprefix) call. Provide information for DriverManager executing call:
putConnectionData(DBrefID, dbName, dbUser, dbPassword, autoConnect). Alternatively and actually prefered way is to create
property file (by default named izFrame.config) having entries that start with 'DBservice.'. Note, in this file dbType and DBrefID
values are supplied indirectly as part of key name after '@' sign. At DBservice class loading it will try to find and read
izFrame.config file in application root directory, but if you prefer to have different file name or path, call loadConfig() yourself.
<br/><b>Example</b><pre>izFrame.config entries:
# DB driver information (DriverClassName, URLprefix(DSN prefix)) for dbType we refer to as "ORA" below
DBservice.DriverClassName@ORA = oracle.jdbc.OracleDriver
DBservice.URLprefix@ORA = jdbc:oracle:thin:@
# connection information for 'default' database that class will connect to using no parameters constructor. Note, DBservice can read dbUser,
# dbPassword from Java environment variables like: java -D DBservice.dbUser=scott -D DBservice.dbPassword=tiger, so we skip these entries
DBservice.dbType = ODBC
DBservice.dbName = northwind
# connection information for database we will refer to as 'myDB' that would use ORA driver defined above
DBservice.dbType@myDB = ORA
DBservice.dbName@myDB = pubs
DBservice.dbUser@myDB = scott
DBservice.dbPassword@myDB = tiger
DBservice.autoConnect@myDB = true
# JNDI connections are simpler to setup - provide only one entry, specifying type as 'jndi':
DBservice.dbType@otherDB = jndi
<code>
package igor.lana.alex.andrii;
import izFrame.DBservice;
class MySimpleApp {
   public static void main(String[] a){
      DBservice.loadConfig("src/izFrame/izFrame.config");
      ResultSet rs = new DBservice().query("select * from testApp.app_users"); //use default connection, open and close it implicitely
      while(rs.next()) {
         //use ResultSet
      }
  }
}
class MyMultiDatabasesAccessingApp {
   public static void main(String[] a){
      String text = DBservice.doubleBackslashAndApostroph(a[0]); //if text has \ or ' function doubles them, also wraps text in ' '
      DBservice db = new DBservice("myDB"); //or DBservice() if only one DB is defined in property file or with putConnectionData()
      ResultSet rs = db.query("select * from webApp.web_users where comment="+text);
      while(rs.next()) {
         //use ResultSet
      }
      db.close();
      db = new DBservice("otherDB"); //open jndi connection configured in App server as 'otherDB' resource
      // ... use this new connection
      db.close();                    //App servers may or may not require closing connection, bet better to do that
  }
}</code></pre>
As a courtesy to class users, few common driver configurations are preset and user is not required to supply them:
<table><tr><th>dbType</th><th>Driver Class Name</th><th>connection string URL prefix</th><th>Comment</th></tr>
<tr><td>ODBC</td><td>sun.jdbc.odbc.JdbcOdbcDriver</td><td>jdbc:odbc:</td><td>JDBC:ODBC bridge</td></tr>
<tr><td>ORA</td><td>oracle.jdbc.OracleDriver</td><td>jdbc:oracle:thin:@</td><td>Oracle thin, v 9 and above</td></tr>
<tr><td>ORAo</td><td>oracle.jdbc.driver.OracleDriver</td><td>jdbc:oracle:oci9:@</td><td>Oracle fat OCI drv</td></tr>
<tr><td>ORA8</td><td>oracle.jdbc.driver.OracleDriver</td><td>jdbc:oracle:oci8:@</td><td>Oracle ver before 9 use diff driver package hier</td></tr>
<tr><td>MSSQL</td><td>com.microsoft.sqlserver.jdbc.SQLServerDriver</td><td>jdbc:sqlserver:</td><td>use sqljdbc.jar for JRE 5.0, sqljdbc4.jar for 6.0</td></tr>
<tr><td>DB2</td><td>COM.ibm.db2.jdbc.app.DB2Driver</td><td>jdbc:db2:</td><td>DB2 simple</td></tr>
<tr><td>DB2p</td><td>COM.ibm.db2.jdbc.DB2ConnectionPoolDataSource</td><td>jdbc:db2:</td><td>DB2 pool</td></tr>
<tr><td>mySQL</td><td>org.gjt.mm.mysql.Driver</td><td>jdbc:mysql:</td><td>use localhost/authority syntax</td></tr>
</table>
<b>Implementation notes</b> <br/>
Configuration info is stored in two protected static maps: driverConfigMap and connectionConfigMap of key-String : value-String[] type.
For driverConfigMap key is dbType entry (like "ORA" in the sample above); value string array would contain values of DriverClassName,
URLprefix, "true"/"false" to indicate if driver was loaded.
For connectionConfigMap key is DBrefID entry (like "myDB" in the sample above), value string array would contain values of dbType,
dbName, dbUser, dbPassword, "true"/"false" for autoConnect. These value arrays will be populated by parsing property file or by
'putConfig' calls and will be returned by corresponding 'getConfig' calls: getDriverConfigData(dbType), getConnectionData(DBrefID).
Note, putDriverConfigData() call sets 'loaded' part of string array specific to this DriverClassName to "false".<br/>
Class instance members are DBrefID, autoConnect, inTransaction, _conn (java.sql.Connection), _stmt (java.sql.Statement).
All instance class members are protected and have getters for user access.
If DBservice cannot load requested Driver class RuntimeException is thrown. As a courtesy to user, before doing this
DBservice will try to load all drivers listed in the config and log message will list those that were succesfully loaded.
<br/><b>Environment setup (optional information)</b> <br/>
Of course, for database connection to happen, appropriate drivers need to be installed on the system and classpath
variable should be set accordingly. This can be tricky sometimes, for example, Oracle driver jar name depends on product
version: for Oracle 10 it is ojdbc14.jar, for Oracle versions before that there were two files: classes12.zip and
nls_charset12.zip located in directory $ORACLE_HOME/jdbc/lib. On some versions of JVM you had to rename these zip files to
jar extension to enable loading.
<br/>You may also find useful info below from Sun Microsystems on <b>different types of JDBC drivers</b>:
</li><li> Type 1: Uses a bridging technology to access the database (e.g., Sun's JDBC-ODBC Bridge driver)
</li><li> Type 2: Uses native API drivers; requires software on the client machine
</li><li> Type 3: Translates JDBC calls into a database-independent network protocol, which a server then translates into a database protocol
</li><li> Type 4: Uses the network protocols built into the database engine
</li></ul>
Type 1 and 2 drivers require native code to be installed on the client's machine, so they are suitable for a corporate network or
an application server in a three-tier architecture. They are not suitable for machines connecting to the database via the Internet.
Type 3 drivers do not require native drivers on the client's machine, but they require additional security to work over the Internet.
In general, Type 4 drivers are preferable because they use the network protocol of the database without needing any special native
code on the client machine. Conventional wisdom says that you should not use the Type 1 JDBC-ODBC Bridge for any commercial application.
Why? Because not only you have to set up an ODBC source on the client machine, the bridge is slower than a Type 4 driver would be.
It is important to run your own performance tests in a real world test environment. Depending on what you are doing, the JDBC/ODBC
bridge may actually be better  than a Type 4 driver. For example, if you measure how fast a driver inserts large amount of data
into a database via the setBytes() method (e.g., 1MB blocks into an Image column), you'll discover that some of the Type 4 drivers
are almost 10 times slower than the JDBC/ODBC bridge.
@author Igor Zvorygin
@created November 2003. Modified October 2005, April 2010.*/
public class DBservice {
   //------ static config data --------
   /** stores DB driver configurations - dbType(key), DriverClassName, URLprefix, "loaded" */
   protected static Map<String,String[]> _driverConfigMap = new Hashtable<String,String[]>();
   /** stores connection specific configurations - DBrefID(key), dbType, dbName, dbUser, dbPassword */
   protected static Map<String,String[]> _connectionConfigMap = new Hashtable<String,String[]>();
   /** class logger */
   protected static Log log = Log.getLog("izFrame.DBservice");
   //------ Class instance members --------
   /** identifier of database connection linked with properties like DB type, connect_string, user, password, etc. */
   protected String DBrefID;
   /** flag indicating if transaction was initiated that has not completed yet */
   protected boolean inTransaction;
   /** flag indicating if connection's open/close() is to be performed at each 'business function' invocation */
   protected boolean autoConnect;
   //------- Database objects -----------------
   /** Connection object */
   protected java.sql.Connection _conn;
   /** Statement object */
   protected java.sql.Statement _stmt;

   static { //set maps with a few default driver types and connect strings author is aware of.
      putDriverConfigData("ODBC", "sun.jdbc.odbc.JdbcOdbcDriver",     "jdbc:odbc:");          //ODBC
      putDriverConfigData("ORA",  "oracle.jdbc.OracleDriver",         "jdbc:oracle:thin:@");  //Oracle thin, v 9 and above
      putDriverConfigData("ORAo", "oracle.jdbc.driver.OracleDriver",  "jdbc:oracle:oci9:@");  //Oracle fat OCI drv - server:port:instance
      putDriverConfigData("ORA8", "oracle.jdbc.driver.OracleDriver",  "jdbc:oracle:oci8:@");  //Oracle ver before 9 use diff driver package hier
      putDriverConfigData("MSSQL","com.microsoft.sqlserver.jdbc.SQLServerDriver", "jdbc:sqlserver:"); //MS - use sqljdbc.jar for JRE 5.0, sqljdbc4.jar for 6.0
      putDriverConfigData("DB2",  "COM.ibm.db2.jdbc.app.DB2Driver",   "jdbc:db2:");           //DB2 simple
      putDriverConfigData("DB2p", "COM.ibm.db2.jdbc.DB2ConnectionPoolDataSource", "jdbc:db2:");//DB2 pool
      putDriverConfigData("mySQL","org.gjt.mm.mysql.Driver",         "jdbc:mysql:");         //mySQL - //localhost/authority
   }
   /** links specified database type with driver class name and connection string. For example:
   DB2->{COM.ibm.db2.jdbc.app.DB2Driver, jdbc:db2:}. Owerwrites old entries of the same name. 
   @param dbType - identifier for database driver, free form field. Note, some common dbType (ODBC, ORA, MSSAL, DB2, mySQL) are preloaded.
   @param driverClassNm - JDBC driver class name, like sun.jdbc.odbc.JdbcOdbcDriver, oracle.jdbc.OracleDriver.
   @param connURLprefix - connect string URL prefixes in JDBC DSN like jdbc:odbc:, jdbc:mysql:, jdbc:oracle:oci9:@ */
   public static void putDriverConfigData(String dbType, String driverClassNm, String connURLprefix){
      String[] drvData = {driverClassNm, connURLprefix, "false"};
      _driverConfigMap.put(dbType, drvData);
   };
   /** returns driverClassNm, connURLprefix, loaded linked to specified database type (dbType). */
   public static String[] getDriverConfigData(String dbType){
       return _driverConfigMap.get(dbType);
   };
   /** links specified DBrefID (database ID) with info about its type, name and login data. Sets autoConnect to false.
   @param DBrefID - identifier you would like to use refering to this combination of connection parameters, can be "" that makes it default.
   @param dbType - identifier for database driver used to establish this connection
   @param dbName - user ID to use while connecting
   @param dbPassword - password to use */
   public static void putConnectionConfigData(String DBrefID, String dbType, String dbName, String dbUser, String dbPassword){
      String[] connData = {dbType, dbName, dbUser, dbPassword, "false"};
      _connectionConfigMap.put(DBrefID, connData);
   };
   /**sets 'autoConnect' for specified DBrefID - if true, connection is opened and closed automatically for each query/insert/update/delete operation */
   public static void setConnectionAutoConnect(String DBrefID, boolean autoConnect){
      String[] connData = _connectionConfigMap.get(DBrefID); //{dbType, dbName, dbUser, dbPassword, autoConnect};
      connData[4] = autoConnect?"true":"false";
      _connectionConfigMap.put(DBrefID, connData);
   };
   /** returns array with dbType, dbName, dbUser, dbPassword, autoConnect linked to specified DBrefID. */
   public static String[] getConnectionConfigData(String DBrefID){
       return _connectionConfigMap.get(DBrefID);
   };

   //------------ INIT and Config -----------------------
   static { loadConfig(null); } //there are reasons to do it or don't do (leave for app to init). I try default load - cautiously

   /** reads configuration information from specified property file and stores it in static maps. If corresponding values are
   defined, reads dbUser, dbPassword values from Java environment variables. */
   protected static void loadConfig(String propFileNm) {
      if(propFileNm==null)
         propFileNm = "izFrame.config"; //Utils._propFileNm;
      Properties props = new Properties();
      String propFilePath = null;
      try {//read_appProperties
         propFilePath = new File(propFileNm).getAbsolutePath();
         FileInputStream fis = new FileInputStream(new File(propFilePath));
         props.load(fis);
         fis.close();
         //--B. read config params
         Set<String> propList = props.stringPropertyNames();
         String errMsg = "";
         //1. store all drivers data: driverClassNm, connURLprefix
         for(String entry : propList){
            if(entry.startsWith("DBservice.DriverClassName@")){
               String dbType = entry.substring("DBservice.DriverClassName@".length());
               String DriverClassName = props.getProperty("DBservice.DriverClassName@"+dbType);
               String URLprefix = props.getProperty("DBservice.URLprefix@"+dbType, "");
               putDriverConfigData(dbType, DriverClassName, URLprefix);
               if(DriverClassName == null)
                  errMsg = errMsg+"\n Driver config is missing DriverClassName for dbType="+dbType;
            }
         }
         //2. now store all connection configs
         for(String entry : propList){
            if(entry.startsWith("DBservice.dbType")){
               String atSymbolValue = (entry.startsWith("DBservice.dbType@"))?"@":"";
               String DBrefID = entry.substring(("DBservice.dbType"+atSymbolValue).length()); //note, can be ""
               String dbType = props.getProperty("DBservice.dbType"+atSymbolValue+("".equals(atSymbolValue)?"":DBrefID));
               if("jndi".equalsIgnoreCase(dbType)){
                  putConnectionConfigData(DBrefID, "jndi", null, null, null); //DBrefID, dbType, dbName, dbUser, dbPassword
                  setConnectionAutoConnect(DBrefID, true);
               }
               else {//for JDBC conn we need to get: dbName, dbUser, dbPassword and verify driver info exists
                  String dbName = props.getProperty("DBservice.dbName"+atSymbolValue+DBrefID);
                  String dbUser = System.getProperty("DBservice.dbUser"+atSymbolValue+DBrefID, null);
                  if(dbUser==null) props.getProperty("DBservice.dbUser"+atSymbolValue+DBrefID);
                  String dbPassword = System.getProperty("DBservice.dbPassword"+atSymbolValue+DBrefID, null);
                  if(dbPassword==null) props.getProperty("DBservice.dbPassword"+atSymbolValue+DBrefID);
                  putConnectionConfigData(DBrefID, dbType, dbName, dbUser, dbPassword);
                  String autoConn = props.getProperty("DBservice.autoConnect"+atSymbolValue+DBrefID, "false");
                  setConnectionAutoConnect(DBrefID, "true".equals(autoConn)?true:false);
                  //verify driver info exists
                  String[] drvData = getDriverConfigData(dbType);
                  if(drvData == null)
                     errMsg = errMsg+"\n Driver config is missing for DBrefID="+DBrefID+" with dbType="+dbType;
               }
            }
         }
         System.out.println("DBservice.loadConfig() succesfully loaded configuration from file "+propFilePath);
     } catch (Exception e) {
         if(!"izFrame.config".equals(propFileNm)) e.printStackTrace(); //don't print err stack for default config file
         System.out.println("DBservice.loadConfig() could not load file "+(propFilePath==null?propFileNm:propFilePath));
      }
   }

   /** default constructor can be called if unnamed or only one 'named' (having database DBrefID) connection configuration is defined. */
   public DBservice() throws SQLException, RuntimeException {
      this(null);
   }

   //---------- Constructors ----------------
   /** allocates JNDI or JDBC conection and statement. For connection setAutoCommit(false). In case of JDBC connection , if needed,
    * loads driver. In case of Naming context errors, throws RuntimeException */
   public DBservice(String DBrefID_in) throws SQLException, RuntimeException {
      DBrefID = DBrefID_in;
      //handle 'empty/default' connection calls
      if(DBrefID == null){
         if(_connectionConfigMap.get("") != null) //simple case: explicit 'empty/default' connection was declared
            DBrefID = "";
         else { //a bit more complex case of one named connection
            int numDBs = _connectionConfigMap.size();
            if(numDBs != 1)
               throw new RuntimeException("DBrefID can be omitted if only one database ID defined in configuration, fact size="+numDBs);
            Set<String> keys = _connectionConfigMap.keySet();
            Iterator<String> itr = keys.iterator();
            DBrefID = itr.next();
         }
      }
      //get connection data
      String[] connData = _connectionConfigMap.get(DBrefID);
      if(connData == null)
         throw new RuntimeException("There is no configuration for database ID " + DBrefID);
      String dbType = connData[0];
      if("jndi".equalsIgnoreCase(dbType)){   //connect using JNDI
         autoConnect = true;
         open();
         return;
      }
      //must be JDBC connection. Find driver data and load it if it was not loaded, then connect
      String[] drvData = _driverConfigMap.get(dbType);
      if(drvData == null)
         throw new RuntimeException("There is no configuration for database type "+connData[1]+" for DBID " + DBrefID);
      String drvName = drvData[0];
      String connPrefix = drvData[1];
      if("false".equals(drvData[2])){  //load driver
         String errMessage = null;
         try {Class.forName(drvName);} //like "sun.jdbc.odbc.JdbcOdbcDriver"
         catch (ClassNotFoundException e) {
            errMessage = "DBservice(): cannot load DB driver "+drvName;
         }
         catch (java.lang.NoClassDefFoundError e) {
            errMessage = "DBservice.connectDrv(): driver "+drvName+" not in classpath: "+e.getMessage();
         }
         if(errMessage != null){
            //user courtesy - load whatever can be found in config and list what was loaded OK
            Set<String> keys = _driverConfigMap.keySet();
            Iterator<String> itr = keys.iterator();
            while(itr.hasNext()){
               String idbType = itr.next();
               String[] idrvData = _driverConfigMap.get(idbType);
               try {Class.forName(idrvData[0]);}
               catch (Throwable tr) {if("true".equals(idrvData[2])) if(log.level >= Log.ERROR) log.error("idrvData[0] has loading problems "+tr.getMessage());}
            }
            Enumeration drvList = DriverManager.getDrivers();
            if(drvList.hasMoreElements()) if(log.level >= Log.INFO) log.inform("database drivers types available (loaded): ");
            while(drvList.hasMoreElements()) {
               String drName = drvList.nextElement().getClass().getName();
               if(log.level >= Log.INFO) log.inform("\t\t"+drName);
            }
            //log errMessage and exit (throw Ex)
            if(log.level >= Log.INFO) log.inform(errMessage);
            throw new RuntimeException(errMessage);
         }
         _driverConfigMap.put(connData[1], new String[]{drvName, connPrefix, "true"});
      }
      String implConn = connData[4];
      autoConnect = ("true".equals(implConn))?true:false;
      if(!autoConnect)
         open();
   }

   /** connects to the database using connection information specific to this instance of DBservice, setAutoCommit(false), creates Statement. */
   public void open() throws SQLException {
      String[] connData = _connectionConfigMap.get(DBrefID);
      String dbType = connData[0];
      if("jndi".equalsIgnoreCase(dbType)){   //connect using JNDI
         try{
            javax.naming.Context ctx = new javax.naming.InitialContext(); //NamingException
            if(ctx == null )
              throw new RuntimeException("No naming Context");
            javax.sql.DataSource ds = (DataSource)ctx.lookup("java:comp/env/jdbc/"+DBrefID);//NamingException
            if(ds == null)
              throw new RuntimeException("DBservice could not find datasource "+DBrefID+" in JNDI");
            _conn = ds.getConnection(); //SQLException
            if(_conn == null)
              throw new RuntimeException("DBservice could not acquire JNDI connection to Database");
            _conn.setAutoCommit(false);
            _stmt = _conn.createStatement(); //SQLException
            if(_stmt == null)
              throw new RuntimeException("DBservice could not create statement");
         }
         catch(javax.naming.NamingException e){
            if(log.level >= Log.ERROR) log.error(e, "DBservice could not connect to Database '"+DBrefID+"' using JNDI.");
            throw new RuntimeException("connectJNDI("+DBrefID+") error: "+e.getMessage(), e);
         }
         return;
      }
      //must be JDBC connection. Find driver data and load it if it was not loaded, then connect
      //find connection data using {dbType, dbName, dbUser, dbPassword, autoConnect}
      String[] drvData = _driverConfigMap.get(dbType);
      String connPrefix = drvData[1];
      String dsn = connPrefix+connData[1];
      String dbUser = connData[2];
      String dbPwd = connData[3];
      try {
         _conn = DriverManager.getConnection(dsn, dbUser, dbPwd);
         //if(log.level >= Log.DEBUG) log.debug("DBservice.connectDrv(): getConnection() OK:  "+dsn+" "+dbUser+" "+dbPwd);
         _conn.setAutoCommit(false);
         _stmt = _conn.createStatement(); //SQLException
      }
      catch (SQLException e) {
         if(log.level >= Log.WARNING) log.warning("DBservice(): getConnection() failed: "+dsn+" "+dbUser+" "+dbPwd+" "+e.getMessage());
      }
   }

   /** calls close() */
   @Override public void finalize() {
      close();
   }
   /** disconnect from the database. Also sets statement and connection to null to avoid doubling close() in finalize() */
   public void close() {
      try {
         if(_stmt==null && _conn==null) return;
         if(_stmt != null) {
            _stmt.close();
            _stmt = null;
         }
         if(_conn != null && !_conn.isClosed()){
            if(_conn.getAutoCommit()==false){//do to avoid: CLI0116E Invalid transaction state - transaction in progress.
               if(!inTransaction)
                  _conn.commit(); //equivalent to transactionCommit() call, but without 'commit' message in the log
               else
                  transactionRollback(); //=~ _conn.rollback(), but adds 'rollback' message to log
            }
            _conn.close();
            _conn = null;
            //if(log.level >= Log.INFO) log.inform("DBservice.close(): closing connection dbName="+_dbInstanceNm);
         }
      }
      catch(SQLException e){
         if(log.level >= Log.ERROR) log.error(e, "DBservice.close(): error closing connection");
      }
   }

   //------------ Public business methods -----------------------
   //use next exception/logging policy: log in the 'innermost' method, throw e to show error to caller

   //--------- Query -----------
   /** executes database query */
   public ResultSet query(String sql) throws SQLException {
      ResultSet rs = null;
      try { //read from DB, like "select OPS$Q4VTCH3.Orders_ID_seq.nextval from dual"
         rs = _stmt.executeQuery(sql); //never returns null
         return rs;
      }
      catch(SQLException e){//thrown if err or statement produces anything other than a single ResultSet
         if(log.level >= Log.ERROR) log.error(e, "query(): error doing sql=\n"+sql);
         throw e;
      }
      //finally{if(autoConnect) close();}
   }
   /** executes database query that returns single numeric (long, int) value. DB Null returns as 0.
   Logs error and throws SQLException if no data found. */
   public long queryInt (String sql) throws SQLException {
      try {
         if(autoConnect) open();
         ResultSet rs = query(sql);
         if(!rs.next()) {
            if(log.level >= Log.ERROR) log.error("queryInt(): no data found by sql=\n"+sql);
         }
         return rs.getLong(1); //produces SQLException if no data found
      }
      finally{if(autoConnect) close();}
   }
   /** executes database query that returns single string. Convenient to get sysdate, systimestamp, etc.
   Logs error and throws SQLException if no data found. */
   public String queryString (String sql) throws SQLException {
      try {
         if(autoConnect) open();
         ResultSet rs = query(sql);
         if(!rs.next()) {
            if(log.level >= Log.ERROR) log.error("queryInt(): no data found by sql=\n"+sql);
         }
         return rs.getString(1); //produces SQLException if no data found
      }
      finally{if(autoConnect) close();}
   }

   //--------- Insert, update and delete to single row -----------
   /** executes update/insert/delete and commits if rowcount==1. If rowcount!=1, throws runtime (catch or ignore on your
   discretion) ConcurrentModificationException to indicate that underlying single data row is not found anymore. */
   public void execRowChange(String sql) throws SQLException, java.util.ConcurrentModificationException {
      if(log.level >= Log.DEBUG) log.debug("execRowChange() sql=\n"+sql);
      int rowCount = -1;
      try { //exec in DB like "update export_sequence set .. where series_id=1"
         if(autoConnect) open();
         rowCount = _stmt.executeUpdate(sql);
         if(!inTransaction && rowCount==1)
            _conn.commit();
         else if(rowCount != 1) { //notify caller of the problem with update
            transactionRollback();
            if(log.level >= Log.ERROR) log.error("execRowChange(): error doing change, row count="+rowCount+" i.e. !=1, changes rolled back.");
            throw new ConcurrentModificationException("row count!=1, changes rolled back. sql=\n"+sql);
         }
      }
      catch(SQLException e){
         if(log.level >= Log.ERROR) log.error(e, "execRowChange(): error doing sql=\n"+sql);
         transactionRollback();
         throw e;
      }
      finally{if(autoConnect) close();}
   }
   /** executes insert and returns value generated for IDENTITY or SEQUENCE column named in idColName. If idColName==null or
   stmt.getGeneratedKeys() does not return any rows, returns -1. Input SQL can be of the form like below: <br/>
   "insert into my_table (id, name) values (id_seq.nextval, 'a_name')" if Oracle sequence is called directly or <br/>
   "insert into my_table (name) values ('a_name')" if ID column is IDENTITY or if Oracle sequence is called by a trigger.
   @throws runtime ConcurrentModificationException if rowcount!=1, to indicate that row cannot be inserted.
   @throws SQLFeatureNotSupportedException if idColName!=null and used JDBC driver does not support getGeneratedKeys() method.*/
   public long execRowInsert(String sql, String idColName)
    throws SQLException, SQLFeatureNotSupportedException, java.util.ConcurrentModificationException {
      if(log.level >= Log.DEBUG) log.debug("execRowInsert() idColName="+idColName+" sql=\n"+sql);
      long rowCount = -1, generatedID=-1;
      try { //exec in DB like "update export_sequence set .. where series_id=1"
         if(autoConnect) open();
         String[] colIDs = new String[]{idColName};
         if(idColName != null)
            rowCount = _stmt.executeUpdate(sql, colIDs);
         else
            rowCount = _stmt.executeUpdate(sql);
         if(!inTransaction && rowCount==1)
            _conn.commit();
         else if(rowCount != 1) { //notify caller of the problem with update
            transactionRollback();
            if(log.level >= Log.ERROR) log.error("execRowInsert(): error doing change, row count="+rowCount+" i.e. !=1, changes rolled back.");
            throw new ConcurrentModificationException("row count!=1, changes rolled back. sql=\n"+sql);
         }
         if(idColName != null) {
            ResultSet rs = _stmt.getGeneratedKeys();
            if (rs.next())
               generatedID = rs.getLong(1);
         }
         return generatedID;
      }
      catch(SQLException e){
         if(log.level >= Log.ERROR) log.error(e, "execRowInsert(): error doing sql=\n"+sql);
         transactionRollback();
         throw e;
      }
      finally{if(autoConnect) close();}
   }

   /** executes update/insert/delete using PreparedStatement and params extracted from paramLst, calls execRowChangeSpec() */
   public long execRowChangePrep(String sql, HashMap paramLst, String idColName) throws SQLException, ConcurrentModificationException {
      return execRowChangeSpec(sql, paramLst, false, idColName);
   }
   /** executes update/insert/delete using CallableStatement and params extracted from paramLst, calls execRowChangeSpec() */
   public long execRowChangeCall(String sql, HashMap paramLst, String idColName) throws SQLException, ConcurrentModificationException {
      return execRowChangeSpec(sql, paramLst, true, idColName);
   }
   /** executes update/insert/delete using Prepared or Callable Statement and params extracted from paramLst.
   Hastable keys are expected to be in the form of "{VARCHAR|INTEGER|FLOAT|DATE|BLOB|CLOB},<param pos>", ex: "BLOB,2".
   Corresponding hashtable value expected to be of type String, Long, Double, java.sql.Date, String for CLOB and
   byte[] for BLOB. Commits if rowcount==1, rollbacks and throws runtime ConcurrentModificationException otherwise */
   protected long execRowChangeSpec(String sql, HashMap paramLst, boolean call, String idColName) throws SQLException {
      if(log.level>=Log.DEBUG)log.debug("execRowChangeSpec() idColName="+idColName+" paramLst.size="+paramLst.size()+" sql=\n"+sql);
      long rowCount = -1, generatedID = -1;
      java.sql.PreparedStatement preparedStatement = null;
      try { //exec in DB like "update web_user set photo=? where web_user_id=1"
         if(autoConnect) open();
         if(call)
            preparedStatement =  _conn.prepareCall(sql);
         else
            preparedStatement =  _conn.prepareStatement(sql);
         //java.sql.ParameterMetaData pmd = preparedStatement.getParameterMetaData(); //not supported
         java.util.Set eset = paramLst.entrySet();
         java.util.Iterator iter = eset.iterator();
         while(iter.hasNext()){
            java.util.Map.Entry entry = (java.util.Map.Entry)iter.next();
            String key = (String)entry.getKey();
            String[] names = key.split(",");
            int type = Integer.parseInt(names[0]);
            int pos  = Integer.parseInt(names[1]);
            //if(log.level >= Log.DEBUG) log.debug("i="+pos+",ptype="+pmd.getParameterType(pos)+",pclass="+pmd.getParameterClassName(pos));//!supported
            if(type == Types.VARCHAR)
               preparedStatement.setString(pos, (String)entry.getValue());
            else if(type == Types.INTEGER)
               preparedStatement.setLong(pos, ((Long)entry.getValue()).longValue());
            else if(type == Types.FLOAT)
               preparedStatement.setDouble(pos, ((Double)entry.getValue()).doubleValue());
            else if(type == Types.DATE)
               preparedStatement.setDate(pos, (java.sql.Date)entry.getValue());
            else if(type == Types.CLOB)
               preparedStatement.setString(pos, (String)entry.getValue());
            else if(type == Types.BLOB){
               byte[] bytes = (byte[])entry.getValue(); //tfile_Bytes.length=44371
               preparedStatement.setBytes(pos, bytes);   //first run - 0.92, second - 0.14 secs - seems faster then below
               //java.io.InputStream inputStream = new java.io.ByteArrayInputStream(bytes);
               //preparedStatement.setBinaryStream(pos, inputStream, bytes.length); //run1-1.33, 2-0.14 - assume is slower
               //if(log.level >= Log.DEBUG) log.debug("\tbound BLOB, pos="+pos+", bytes.length="+(bytes==null?-1:bytes.length));
            }
         }
         rowCount = preparedStatement.executeUpdate(); //preparedStatement.getWarnings().getMessage() - null
         if(idColName != null){
            try{
               ResultSet rs = _stmt.getGeneratedKeys();
               if(rs.next()) {
                  generatedID = rs.getLong(1);
               }
            }
            catch(SQLFeatureNotSupportedException e){}
         }
         if(!inTransaction && rowCount==1)
            _conn.commit();
         else if(rowCount != 1) { //notify caller of the problem with update
            transactionRollback();
            if(log.level >= Log.ERROR) log.error("execRowChangeSpec(): error doing change, row count="+rowCount+" i.e. !=1, changes rolled back.");
            throw new ConcurrentModificationException("row count!=1, changes rolled back. sql=\n"+sql);
         }
      }
      catch(SQLException e){
         if(log.level >= Log.ERROR) log.error(e, "execRowChangeSpec(): error doing sql=\n"+sql);
         transactionRollback();
         throw e;
      }
      finally {
         if(preparedStatement != null)
            preparedStatement.close();
         if(autoConnect) close();
      }
      if(rowCount != 1) { //notify caller of the problem with update
         if(log.level >= Log.ERROR) log.error("execRowChangeSpec(): error doing change, row count="+rowCount+" i.e. !=1, changes rolled back.");
         throw new SQLException("execRowChangePrep(): row count!=1, changes rolled back. sql=\n"+sql);
      }
      return generatedID;
   }

   //--------- free form Insert, update, delete and DDL -----------
   /** executes SQL statement by calling Statement.executeUpdate(). Commits if(!inTransaction). If exception, logs error and rethrow.
   @returns either the row count for INSERT/UPDATE/DELETE, or 0 for statements that return nothing. */
   public int execUpdate(String sql) throws SQLException {
      if(log.level >= Log.DEBUG) log.debug("execUpdate(sql), sql=\n"+sql);
      try { //DDL like "create table ..."
         if(autoConnect) open();
         int res = _stmt.executeUpdate(sql);
         if(!inTransaction)
            _conn.commit();
         return res;
      }
      catch(SQLException e){
         if(log.level >= Log.ERROR) log.error(e, "execUpdate(): error doing sql=\n"+sql);
         throw e;
      }
      finally{if(autoConnect) close();}
   }
   /** executes SQL statement (often DDL) by calling Statement.execute(). Commits if(!inTransaction). If exception, logs error and rethrow.
   Returns true if the first result is a ResultSet object; false if it is an update count or there are no results.
   You can obtain Statement by calling getStatement() for further processing */
   public boolean execStatement(String sql) throws SQLException {
      if(log.level >= Log.DEBUG) log.debug("execStatement(sql), sql=\n"+sql);
      try { //DDL like "create table ..."
         if(autoConnect) open();
         boolean res = _stmt.execute(sql);
         if(!inTransaction)
            _conn.commit();
         return res;
      }
      catch(SQLException e){
         if(log.level >= Log.ERROR) log.error(e, "execStatement(): error doing sql=\n"+sql);
         throw e;
      }
      finally{if(autoConnect) close();}
   }

   //--------- Transaction -----------
   /** sets inTransaction=true that will direct execRowChange() and execStatement() skip doing commit */
   public void transactionBegin() throws SQLException {
      if(autoConnect){
         if(log.level >= Log.WARNING) log.warning("you are trying to begin transaction on connection with autoConnect=true, switching to autoConnect=false");
         open();
         autoConnect = false;
      }
      if(log.level >= Log.DEBUG) log.debug("transactionBegin");
      inTransaction = true;
   }
   /** commits transaction and sets inTransaction=false that will direct exec{RowChange,Update,Statement}() to do commit */
   public void transactionCommit() throws SQLException {
      if(log.level >= Log.DEBUG) log.debug("transactionCommit");
      _conn.commit();
      inTransaction = false;
   }
   /** rollbacks transaction and sets inTransaction=false that will direct exec{RowChange,Update,Statement}() to do commit */
   public void transactionRollback() throws SQLException {
      if(log.level >= Log.DEBUG) log.debug("transactionRollback");
      _conn.rollback();
      inTransaction = false;
   }

   //--------- Getters -----------------
   /** returns DBrefID - identifier of database connection.*/
   public String getDBrefID() {
      return DBrefID;
   }
   /** returns inTransaction - flag indicating if transaction was initiated that has not completed yet*/
   public boolean isInTransaction() {
      return inTransaction;
   }
   /** returns autoConnect - flag indicating if connection's open/close() is to be performed at each 'business function' invocation */
   public boolean isAutoConnect() {
      return autoConnect;
   }
   /** returns java.sql.Connection that you can use if you need finer control or functionality not provided by DBservice */
   public Connection getConnection() {
      return _conn;
   }
   /** returns java.sql.Statement that you can use if you need special processing of result set */
   public Statement getStatement() {
      return _stmt;
   }

   // -------- Utils ----------------------------
   /** doubles every backslash ( \  -> \\ ) and apostrophe ( ' -> '' ), encloses text in ', if value==null returns "null" */
   public static String doubleBackslashAndApostroph(String value){
      if(value==null) return null;
      if(value!=null && value.trim().equalsIgnoreCase("null")) return "null";
      StringBuffer retval = new StringBuffer("'");
      for(int i=0; i<value.length(); i++) {
         if(value.charAt(i) == '\\') retval.append("\\");
         else if(value.charAt(i) == '\'') retval.append("\'\'");//double apostrophe
         else retval.append(value.charAt(i));
      }
      return retval.append("'").toString();
   }
   // -------- TEST ----------------------------
   /** test driver - attempts to load drivers configured, connects to few tast databases, executes simple statements */
   public static void main(String[] args) {
      Log.loadConfig("vCurrent/src/izFrame/izFrame.config");
      if(log.level >= Log.INFO) log.inform("DBservice main() starts");
      DBservice.loadConfig("vCurrent/src/izFrame/izFrame.config");
      //user courtesy - load whatever can be found in config and list what was loaded OK
      Set<String> keys = _driverConfigMap.keySet();
      Iterator<String> itr = keys.iterator();
      while(itr.hasNext()){
         String idbType = itr.next();
         String[] idrvData = _driverConfigMap.get(idbType);
         try {Class.forName(idrvData[0]);}
         catch (Throwable tr) {if(log.level >= Log.INFO) log.inform("DBservice: cannot load DB driver "+idrvData[0]); }
      }
      Enumeration drvList = DriverManager.getDrivers();
      if(drvList.hasMoreElements()) if(log.level >= Log.INFO) log.inform("database drivers types available (loaded): ");
      while(drvList.hasMoreElements()) {
         String drName = drvList.nextElement().getClass().getName();
         if(log.level >= Log.INFO) log.inform("\t\t"+drName);
      }

      //try MS SQL Server
      //putConnectionConfigData(DBrefID, dbType, dbName, dbUser, dbPassword, autoConnect)
      DBservice.putConnectionConfigData("MSDB", "MSSQL", "//localhost:1433", "igor", "zx123");//
      DBservice srv = null;
      try{
         srv = new DBservice("MSDB");
         if(log.level >= Log.INFO) log.inform("MSDB-MSSQL: connected="+(srv._conn!=null));
         srv.execStatement("create table tbl1(id int IDENTITY, text varchar(256))");
         long id = srv.execRowInsert("insert into tbl1(text) values('testing value')", "id"); //null as col name also works
         if(log.level >= Log.INFO) log.inform("query res value: " + srv.queryString("select text from tbl1 where id="+id));
         srv.close();
      } catch(Throwable t){}
      //try integrated Authentication - requires sqljdbc_auth.dll on system PATH or -Djava.library.path=C:\apps\MSjdbc3\sqljdbc_3.0\enu\auth\x86
      DBservice.putConnectionConfigData("MSDBsec", "MSSQL", "//localhost:1433;integratedSecurity=true", null, null);
      setConnectionAutoConnect("MSDBsec", true);
      try{
         srv = new DBservice("MSDBsec");
         if(log.level >= Log.INFO) log.inform("MSDBsec-MSSQL-integratedSecurity: connected="+(srv._conn!=null));
         srv.execStatement("use _test drop table igor.tbl1"); //note MSSQL quirk: table created by user 'igor' gets userID in name
      } catch(Throwable t){}
      //try Oracle
      try{
         DBservice.putConnectionConfigData("OraDB", "ORA", "127.0.0.1:1521:Instance", "scott", "tiger");//
         srv = new DBservice("OraDB");
         if(log.level >= Log.INFO) log.inform("OraDB-ORA: connected="+(srv._conn!=null));
         srv.close();
      } catch(Throwable t){}
      //try Sybase through the ODBC bridge
      DBservice.putConnectionConfigData("sybDB", "ODBC", "myJdbcDSN", "user", "password");//id, type, dsn, u, p
      setConnectionAutoConnect("sybDB", true);
      try{
         srv = new DBservice("sybDB");
         if(log.level >= Log.INFO) log.inform("sybDB-myJdbcDSN: connected="+(srv._conn!=null));
         srv.close();
      } catch(Throwable t){}
   }
}//DBservice end
/*
07-11-08 11:39:59.82 I DBservice main() starts
10-05-07 18:30:38.51 I DBservice: cannot load DB driver org.gjt.mm.mysql.Driver
10-05-07 18:30:38.51 I DBservice: cannot load DB driver oracle.jdbc.OracleDriver
10-05-07 18:30:38.51 I DBservice: cannot load DB driver COM.ibm.db2.jdbc.DB2ConnectionPoolDataSource
10-05-07 18:30:38.13 I DBservice: cannot load DB driver COM.ibm.db2.jdbc.app.DB2Driver
07-11-08 11:39:59.85 I database drivers types available (loaded):
10-05-07 18:30:38.13 I                 com.microsoft.sqlserver.jdbc.SQLServerDriver
07-11-08 11:39:59.85 I                 oracle.jdbc.driver.OracleDriver
07-11-08 11:39:59.85 I                 sun.jdbc.odbc.JdbcOdbcDriver
10-05-07 18:30:38.19 I MSDB-MSSQL: DBservice._connected=true
10-05-07 18:30:38.19 D execStatement(sql), sql=
create table tbl1(id int IDENTITY, text varchar(256))
10-05-07 18:30:38.19 D execRowInsert(sql, idColName), idColName=id, sql=
insert into tbl1(text) values('testing value')
10-05-07 18:30:38.23 I query res value: testing value
10-05-07 18:30:38.25 I MSDBsec-MSSQL-integratedSecurity: DBservice._connected=true
10-05-07 18:30:38.25 D execStatement(sql), sql=
use _test drop table igor.tbl1
*/
