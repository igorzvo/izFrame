package izFrame;
import java.io.Serializable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*; //ArrayList;Hashtable;HashMap;GregorianCalendar;NoSuchElementException;
//import static java.sql.Types.*;

/** Class DB and its inner classes Field, Table allow representation of database model by Java object
model - classes declaratively define database tables and their fields. Two other DB inner classes - Dao and DaoList
provide database read, insert, update, delete operations on single or multiple rows appropriately.
<ul> <li> class DB presents database structure as an array of DB.Table descriptors. DB also provides basic services
   for field and table searching plus support for 'audit' fields. 'Audit' fields are fields like created_by, modified_by,
   created_date, modified_date that we may want to handle consistently in many tables or even add to many tables by just
   passing flag or field names array to table descriptor constructor.
</li><li> class DB.Field describes column (field) of database table. Field type defaults to varchar, but other data types -
   date, int, float, binary, blob, clob, identity, sequence, etc.(last two are variants of int) are supported. Attribute
   'preset' indicates that field is populated automatically (say, by trigger). Attribute 'format' is typically used to
   store Java Date or Float format for SQL to Java conversion and attribute 'formatSQL' to store format for Java to SQL
   conversion. 
</li><li> class DB.Table describes table as a set of DB.Field structures. You need not (although usually will) list
   all table fields, you can list only the ones you want to change or read. If field is backed by underlying sequence or
   identity set field type to INT_SEQUENCE or INT_IDENTITY and generation of this ID for insert operation will be automatically
   handled by the framework. Table also supports automatic appending of 'audit' fields.
</li></ul>
<p/> DB.Dao and DB.DaoList classes are used to define real business data objects:
<ul> <li> class DB.Dao describes data access object (a.k.a data object or business object). Typically it is based on
   some database table row (main table) and can include few related fields from other tables (extra tables). Here we use
   word 'related' in a 'relational database' sense, i.e. some kind of logical or physical relation should exist between
   'extra table' fields and 'main table' fields. Class DB.Dao aggregates DB.Table to define 'main' table and
   uses array eTblArr DB.Table objects to add fields from other ('extra') tables. In the last case each of these
   objects must have joinCond member defined that establishes join conditions between tables.
   Method daoSave() persists Dao as row of 'main' database table and can generate required insert, update and delete SQL.
</li><li> DB.DaoList is used to represent set of database rows and to read (daoRead()) and store (daoSave()) these rowset data.
   Generated select SQL will populate both 'main' and 'extra' table fields.
   DB.DaoList aggregates DB.Dao that is used in two ways: a) as a storage of search parameters, b) as a template for
   building new rows using variant of clone() method. Note, CLOB and BLOB field values are populated only if they were
   preliminary marked with markLOBfield4read() method.
</li></ul>
Unlike other data access frameworks, izFrame.DB does not require external XML files to define database or
data object structure on the premise that too many external files make code unreadeable and difficult to debug.
In fact, the idea is to reverse this particular trend of creating 'XML hell' similar to 'DLL hell' we loathed so much.
Still, to accommodate developers who cannot live without XML, DB inner classes provide toXML() and fromXML() methods.
<br> Also note that actual database reading and writing operations require SQL Connection that is provided by
class DBservice.
<p/> Implementation limitations:
<ul> <li> straight extension of DB class yields one database schema per application. To define additional
   schemas, add another static array of table descriptors to your DB derived class or write few DB derived classes - one for
   each schema or even module (set of related tables).
</li><li> Class DB.Dao requires tables to have unique names even across schemas - no straight workaround here
</li></ul>
Here is an example of a DB derived class defining database of three tables (in two schemas), two Dao classes and two
DaoList classes to access the tables.
<pre> <code>
import izFrame.DB;
import izFrame.DBservice;
public class myAppDB extends izFrame.DB{
   //A. class member variables and functions
   protected static final myAppDB instance;
   protected static final DB.Field[] histFlds;
   static { //init global variable
      instance = new myAppDB();
      _dateFormatSQL = "120"; //set to MS SQL Server style equivalent to Oracle 'yyyy-mm-dd hh24:mi:ss'
      instance.auditFlds = new DB.Field[] {
         new Field("modified_by", VARCHAR, true),  //true means preset, i.e. populated by DEFAULT or trigger
         new Field("modified_date",DATE, true),
      };
      histFlds = new DB.Field[] {
         new Field("hist_id", INT_IDENTITY),
         new Field("action",VARCHAR),
      };
      //B. populate DBtablesArr to define database tables we want to work with
      instance.DBtablesArr = new DB.Table[] {
         new DB.Table("Item",                //auditable table ITEM
            new DB.Field[]{ new DB.Field("itemID", INT_IDENTITY),
               new DB.Field("Description"),
               new DB.Field("Price",DB.FLOAT),
            }, instance.auditFlds
         )
         , new DB.Table("dbo.OrderLine",         //auditable table ORDERLINE
            new DB.Field[]{ new DB.Field("OrderLineID", INT_IDENTITY),
               new DB.Field("OrderID", INTEGER),
               new DB.Field("ItemID", INTEGER),
            }, instance.auditFlds
         ),             //end of myApp schema tables definition
         new DB.Table("dbo.Orders o",              //auditable table ORDERS (ORDER is database reserverd word)
            new DB.Field[]{ new DB.Field("OrderID", INT_IDENTITY),
               new DB.Field("CustName"),
               new DB.Field("OrderData"),
            }, instance.auditFlds
         ),
      };                //end of initial tables definition
      instance.addExtendedTable("Item_History", "Item", histFlds); //declare Item_History table derived from Item one
   }
   //C.-- define dao classes ----
   public static class Order extends DB.Dao{
      public Order()      { super(myAppDB.instance.getTable("dbo.Orders")); }
   }
   public static class ItemHistory extends DB.Dao{
      public ItemHistory(){ super(myAppDB.instance.getTable("Item_History")); }
   }
   public static class OrderLine extends DB.Dao{
      public OrderLine() { super(myAppDB.instance.getTable("OrderLine"),
            new DB.Table[] { //from table Item get field Description, join by ItemId
               new DB.Table("Orders o", "join Orders o on o.OrderID=a.OrderID",
                  new DB.Field[]{myAppDB.instance.getTableField("Orders", "OrderData")}
               ),
               new DB.Table("Item it", "it.itemID=a.ItemID",
                  new DB.Field[]{myAppDB.instance.getTableField("Item", "Description")}
               ),
            }
         );
      }
   }
   //optional convenience (preinitialized with the specific Dao) DaoList classes
   public static class OrderLst extends DB.DaoList{ public OrderLst(){ super(new Order()); } }
   public static class OrderLineLst extends DB.DaoList{ public OrderLineLst(){ super(new OrderLine()); } }
}
</code></pre>
So, what is happening here? Simple as A-B-C.
<ul> <li>'A' - class myAppDB declares static final myAppDB instance, as we desire to have only one instance of object
   describing our database.
</li><li>'B' - we populate DBtablesArr to define database tables we want to work with.
   <br/> Every table descriptor is inialized with array of field descriptors which are created inline. Beside array
   of field descriptors, table descriptor constructor optionally takes array of extra (audit, history) fields that too will be
   added at fields array end. Also note that table names can include schema and owner qualifier plus desired alias.
</li><li>'C' - Dao classes are defined. If there are not many of them and you are not overwriting many methods in them, you
   can declare these Dao as static inner classes of your DB extension class as is done in the example above, or, you can define
   them as separate classes - whatever you like more.
   <br/> In our example we define three Dao classes. First one is using table Orders, second Item_History and thisrd is more
   complex - baside main table OrderLine it references and reads fields from Item and Order tables. Note use of table joins and aliases.
   <br/>Lastly two DaoList classes preinitialized with the specific Dao are defined.
</li></ul>
Now, how do you use this DB implementation to read and save data in a real database?
Static main() demonstrates how Dao class can be used to save and read cross-referenced data in the the database of some
internet store. Also you can also observe Table class capability (quite basic one) to create tables in real database on the fly.
<pre> <code>
   public static void main(String[] args) throws java.sql.SQLException {
      //A. create JDBC connection to Oracle or MS SQL database and create tables
      DBservice.putConnectionConfigData("myDB", "MSSQL", "//localhost:1433", "igor", "zx123");//id, type, srv:port:instanceNm, u, p
      DBservice srv = new DBservice("myDB");
      //create ITEM, Order and OrderLine tables (usually won't be required)
      srv.execStatement(myAppDB.instance.getTable("ITEM").sqlCreateTable(true)); //true means sql2000type => datetime
      srv.execStatement(myAppDB.instance.getTable("Orders").sqlCreateTable(true));
      srv.execStatement(myAppDB.instance.getTable("OrderLine").sqlCreateTable(true));
      srv.execStatement(myAppDB.instance.getTable("Item_History").sqlCreateTable(true));
      //B. create and populate Dao objects; save their data in the database
      DB.Dao item = new DB.Dao(myAppDB.instance.getTable("Item")); //create Dao for ITEM table 'on the fly'
      ItemHistory itemHist = new ItemHistory();
      Order order = new Order();                                   //use Dao class created above for ORDERS table
      OrderLine ol = new OrderLine();                              //OrderLine table related to both ITEM and ORDERS tables
      item.setFieldValue("Description", "PC Athlon"); item.setFieldValue("Price", "5.00"); //set values for a few ITEM Dao fields
      srv.transactionBegin();
      item.daoSave(srv);                      //save Dao as row in ITEM table
      itemHist.populateValuesFromDao(item);   //transfer same named field values from one Dao to another
      itemHist.setFieldValue("action", "I");
      itemHist.daoSave(srv);                  //save Dao as row in ITEM_HISTORY table
//insert into ITEM (description, price, created_by, created_date) values ('PC Athlon', 5.00, DEFAULT, DEFAULT)
      item.setFieldValue("Description", "PC Intel");                                       //set values for a few ITEM Dao fields
      item.daoSave(srv);                                                                   //update row in ITEM table
//update ITEM set description='PC Intel', modified_by=DEFAULT, modified_date=DEFAULT where itemid=1 and description='PC Athlon' and price=5.00
      order.setFieldValue("CustName", "Igor"); order.setFieldValue("OrderData", "cash");   //set values for a few ORDERS Dao fields
      order.daoSave(srv);                                                                  //save Dao as row in ORDERS table
      ol.setFieldValue("OrderID", order.getFieldValue("orderID")); //set OrderID field of OrderLine Dao to orderID in ORDERS
      ol.setFieldValue("itemID", item.getFieldValue("itemID")); //set itemID field of OrderLine Dao to corresponding value in ITEM
      ol.daoSave(srv);                                                                     //save Dao as row in ORDERLINE table
      srv.transactionCommit();
      //C. read data from database and display on screen
      OrderLineLst olLst = new OrderLineLst();                    //create OrderLineLst DaoLst for reading data from database
      olLst.setSearchParam("OrderData", "cash");                  //specify that we are looking only for 'cash'
      olLst.daoRead(srv); //olLst.sqlSelect()
//select a.*, o.orderdata, it.description from DBO.ORDERLINE a join Orders o on o.OrderID=a.OrderID, ITEM it where it.itemID=a.ItemID and o.orderdata='cash'
      for(int i=0; i < olLst.getRowCount(); i++) {System.out.println(olLst.getRow(i).toString());}
//myAppDB$OrderLine: orderlineid=1 orderid=1 itemid=1 modified_by='igor' modified_date=convert(datetime,'2010-06-09 15:05:51',120) orderdata='cash' description='PC Intel'
      olLst.getRow(0).markForDelete();                           //delete first row (actually, just mark for deletion)
      olLst.daoSave(srv);                                        //save changes - delete row
//delete from DBO.ORDERLINE where orderlineid=1 and orderid=1 and itemid=1 and modified_by='igor' and '2010-06-09 15:05:51'=convert(varchar(32),modified_date,120)
      srv.execStatement("drop table Item"); srv.execStatement("drop table Orders"); srv.execStatement("drop table OrderLine");
      //srv.execStatement("drop sequence itemID_sq"); srv.execStatement("drop sequence orderID_sq"); srv.execStatement("drop sequence OrderLineID_sq");
      srv.close();
   }
</code></pre>
So, what is happening here? Simple as A-B-C.
<br>'A' - we created database connection - see DBservice class for more details.
<br>part 'B', shows how to create data objects and persist them in the database (insert and update)
objects can be created in two ways - either 'on the fly' as the one above to access table Item, or simply by using
'new' operator if appropriate class was already defined. Population is done by simple sefFieldData() calls and
persistence to database with daoSave().
<br>And lastly, reading by calling DaoList.daoRead(). You can specify parameters for 'where' clause by setting Dao field
values with setSearchParam().
Note, if you prefer singletons, you can implement one for class instance instead of final static variable.
@author Igor Zvorygin, AppIntegration Ltd., LGPL as per the Free Software Foundation  */
public abstract class DB {
   //---- class DB members -------
   /** schema name. Will be prepended to table name to form nameFull member of DB.Table objects */
   //protected static String schemaNm;
   /** set of constants identifying field type: VARCHAR=1, INTEGER=2, DATE=3, FLOAT=4, BINARY=5, BLOB=6, CLOB=7, OTHER=10 */
   public static final int VARCHAR=1, INTEGER=2, DATE=3, FLOAT=4, BINARY=5, BLOB=6, CLOB=7, OTHER=10;
   /** constants identifying INTEGER field type that is unique identifier populated by the database. Use INT_IDENTITY type if
   column is IDENTITY or if Oracle sequence is called by a trigger, use INT_SEQUENCE if it is an ordinary Oracle sequence and
   JDBC driver does not support getGeneratedKeys(). INT_IDENTITY=21, INT_SEQUENCE=22 */
   public static final int INT_IDENTITY=21, INT_SEQUENCE=22;
   /** format used for conversion of SQL DATE type fields to internal String representation, default yyyy-MM-dd HH:mm:ss*/
   protected static String _dateFormatJava = "yyyy-MM-dd HH:mm:ss";
   /** format used for conversion of DATE fields from internal String representation to SQL date - so it has to match _dateFormatJava.
   For Oracle DB set this value to ANSI datetime literal "yyyy-mm-dd hh24:mi:ss", for MS SQL server set it to ODBC canonical style "120".
   If set to null you may need to adjust database settings (NLS_DATE_FORMAT, NLS_LANG on Oracle) as internal String will be passed
   to database 'as is'. Default is "yyyy-mm-dd hh24:mi:ss" */
   protected static String _dateFormatSQL = "yyyy-mm-dd hh24:mi:ss";
   /** default array of 'audit' fields - fileds like created/modified by/date we may want to handle consistently */
   protected Field[] auditFlds={
      new Field("created_by", VARCHAR), 
      new Field("created_date", DATE),    
      new Field("modified_by", VARCHAR, true), //true means preset, i.e. populated by default or trigger
      new Field("modified_date", DATE, true),
   };
   /** Default array of table descriptors, populated when derived class instance is initialized */
   protected Table[] DBtablesArr;
   /** class logger */
   protected static Log log = Log.getLog("izFrame.DB");

   //----------------- DB class functions -----------------
   //---------- table getters, setetrs ---------
   /** returns table descriptor from DBtablesArr given table name (case insensitive), sets parentDB for table */
   public Table getTable(String tblNm) throws java.util.NoSuchElementException {
      tblNm = tblNm.toUpperCase();
      for(int i=0; i<DBtablesArr.length; i++){//check for exact name match
         if(DBtablesArr[i].name.equals(tblNm)) {
            DBtablesArr[i].parentDB = this; //set parentDB for table
            return DBtablesArr[i];
         }
      }
      for(int i=0; i<DBtablesArr.length; i++){//check if table names without schema name would match
         String arrTnm = DBtablesArr[i].name;
         String nm1 = (tblNm.lastIndexOf('.')>0 ? tblNm.substring(tblNm.lastIndexOf('.')+1) : tblNm);
         String nm2 = (arrTnm.lastIndexOf('.')>0 ? arrTnm.substring(arrTnm.lastIndexOf('.')+1) : arrTnm);
         String schemaNm1 = (tblNm.lastIndexOf('.')>0 ? tblNm.substring(0,tblNm.lastIndexOf('.')) : "");
         String schemaNm2 = (arrTnm.lastIndexOf('.')>0 ? arrTnm.substring(0,arrTnm.lastIndexOf('.')) : "");
         if(("".equals(schemaNm1)||"".equals(schemaNm2)) && !schemaNm1.equals(schemaNm2) && nm1.equals(nm2)){
            log.warning("getTable(): Name matching is imperfect by schema name: "+tblNm+" vs "+arrTnm);
            DBtablesArr[i].parentDB = this; //set parentDB for table
            return DBtablesArr[i];
         }
      }
      throw new NoSuchElementException("DB.getTable(): cannot find table '"+tblNm+"' in DBtablesArr");
   }
   /** adds table to DBtablesArr */
   public void addTable(Table tbl) {
      Table[] tArr = new Table[DBtablesArr.length+1];
      for(int i=0; i<DBtablesArr.length; i++)
         tArr[i] = DBtablesArr[i];
      tArr[DBtablesArr.length] = tbl;
      DBtablesArr = tArr;
   }
   /** adds table to DBtablesArr that extends existing table with few extraField (convenient for creation of Audit and History tables). */
   public void addExtendedTable(String tableNm, String existingTableNm, Field[] extraFlds) throws java.util.NoSuchElementException {
      Table baseTbl = getTable(existingTableNm);
      boolean hasIdentity = false;
      Field[] baseFlds = baseTbl.fields;
      for(int i=0; extraFlds!=null&i<extraFlds.length; i++)
         if(extraFlds[i].type==INT_IDENTITY || extraFlds[i].type==INT_SEQUENCE) hasIdentity=true;
      if(hasIdentity){
         baseFlds = new Field[baseTbl.fields.length];
         for(int j=0; j<baseTbl.fields.length; j++){
            baseFlds[j] = baseTbl.fields[j];
            if(baseFlds[j].type==INT_IDENTITY || baseFlds[j].type==INT_SEQUENCE) //change to INTEGER
               baseFlds[j]= new Field(baseFlds[j].name, INTEGER, baseFlds[j].preset, baseFlds[j].format, baseFlds[j].formatSQL);
         }
      }
      Table extTbl = new Table(tableNm, baseFlds, extraFlds);
      addTable(extTbl);
   }

   //---------- field getters ---------
   /** returns field descriptor from DBtablesArr given table and field names (case insensitive) */
   public Field getTableField(String tblNm, String fldNm) throws java.util.NoSuchElementException {
      Table tDsc = getTable(tblNm);
      for(int j=0; j<tDsc.fields.length; j++){
         if(tDsc.fields[j].name.equalsIgnoreCase(fldNm)) return tDsc.fields[j];
      }
      throw new NoSuchElementException("DB.getTableField(): cannot find field "+fldNm+", table "+tblNm+"' in DBtablesArr");
   }
   /** returns sequence name based on schema name (extracted from table name) and field name: [schemaNm.]fldNm+"_sq".
   Don't hesitate to override it if in your case different algorithm is required */
   public String getSequenceName(String tblNm, String fldNm) {
      String schemaNm = (tblNm.lastIndexOf('.')>0 ? tblNm.substring(0,tblNm.lastIndexOf('.'))+"." : "");
      return schemaNm+fldNm+"_sq";
   }
   /** function to handle OTHER type fields - should be overwritten by derived class, default does nothing */
   public void readOtherType(ResultSet rs, Field field, Dao dao) {}//field.setValue(rs.getString(field.name));

   //-------------- INNER DESCRIPTOR CLASSES --------------
   /**Class describes in Java terms database table field attributes: name, type, value, underlying triggers, format (if
   applicable). Has members:
   <ul> <li> name of field
   </li><li> type of it, default is varchar
   </li><li> field format is dual: format and formatSQL, can be useful for Date and decimal values, affects DB read and
      save operations. Note that in many cases format is different between the two - for example, see _dateFormatJava
      and _dateFormatSQL. When no formatSQL is defined, 'format' is used for both operations.
   </li><li> preset attribute - set it to true if value in database is populated by trigger or by other automatic means
   </li><li> isDBfunction, isChanged and isDBnull attributes - indicate what name implies
   </li><li> value members (value, binValue, otherValue) are for storing String, byte array and generic Object type of value
   </li></ul>
   Note, first two class members listed above are final, all attributes and otherValue are public. */
   public static class Field implements Serializable, Cloneable {
      /** name of field, always internally converted to lower case before it is assigned to this public final member */
      public final String name;
      /** type of field, final member, default is varchar */
      public final int type;
      /** can be useful for Date and decimal values, used for DB->Java conversion. */
      public String format;
      /** can be useful for Date and decimal values, used for Java->SQL_DB conversion. */
      public String formatSQL;
      /** preset attribute - set it to true if value in database is populated by DEAFULT, trigger or by other automatic means */
      public final boolean preset;
      /** stores field value as String. Used as storage for all character (including CLOB), date and numeric types */
      protected String value;
      /** alternative storage of field value as byte[]. Used for BINARY and BLOB Field types */
      protected byte[] binValue;
      /** alternative storage of field value in a generic Object. Can be used for OTHER type, no automatic handling */
      public Object otherValue;
      /** indicates that in database value of this field is null, set by daoRead() of DaoList class */
      public boolean isDBnull;
      /** stores field value as it was read from database in cases when it was updated, used by daoSave() of Dao class */
      public String oldValue;
      /** indicates that setValue() was called on this field since last database read; at database read is set to false */
      public boolean isChanged;
      /** stores internal attribute adjusting field handling, for example set by setWhereIsNull(), setFieldFunction(), markLOBfield4read()
      that would set it to "FORCED_NULL", "isDBfunction", "LOB-READ"  values. daoRead() and daoSave() reset it to null. */
      public String actionHint;
      /** pointer to parent Table object */
      public Table parentTable;
      /** most common form of constructor, instantiates field of VARCHAR type and defaults for all other members */
      public Field(String name){this(name, VARCHAR, false, null, null);}
      /** created Field object of specified name and type and uses defaults for all other Field members */
      public Field(String name, int type){this(name, type, false, null, null);}
      /** created Field object of specified name, type and preset, leaves other members at null */
      public Field(String nm, int tp, boolean preset){this(nm, tp, preset, null, null);}
      /** created Field object of specified name, type and formal, leaves other members at null or false */
      public Field(String nm, int tp, String fmt){this(nm, tp, false, fmt, null);}
      /** full Field constructor, creates object of specified name, type, format, formatSQL, preset, leaves other booleans at false */
      public Field(String nm, int tp, boolean pre, String fmt, String fmtSQL){
         if(nm==null) log.error("DB.Field(): name is null");
         name = nm.toLowerCase(); type = tp; format = fmt; preset = pre; formatSQL=fmtSQL;
      }

      /** getter for field string value used as a storage for all character (including CLOB), date and numeric types */
      public String getValue() {return value;}
      /** setter for field string value used as a storage for all character (including CLOB), date and numeric types */
      public void setValue(String s) {
         if(!isChanged)
            oldValue = value;
         value = s;
         isChanged = true;
      }
      /** getter for field binary value used as a storage for BINARY and BLOB Field types */
      public byte[] getBinValue() {return binValue;}
      /** setter for field binary value used as a storage for BINARY and BLOB Field types */
      public void setBinValue(byte[] s) {binValue = s; isChanged = true;}
      /** Normally, for building 'where' part of select/update/delete SQL, fields with NULL values are ignored, so,
      call this function to force corresponding column to be included in 'where' like "where myCol is null" */
      public void setWhereIsNull() {actionHint = "FORCED_NULL";}
      /** returns value in form suitable for SQL statements, i.e. for type==VARCHAR or Date returned field
      value is single quoted and not quoted if type=INTEGER|FLOAT|etc. If (field.isDBfunction) returns field.value.
      <br>For BLOB, CLOB and BINARY if flag isChanged==true, "?" is returned, except if it is null.
      <br>"null" is returned for BLOB and BINARY types if binValue==null, for other types it is returned if value==null. */
      public String sqlValue(){
         if("isDBfunction".equals(actionHint))   return value!=null?value:""; //=="isDBfunction" would be fine, but why get warnings
         if(type==DB.BLOB||type==DB.CLOB||type==DB.BINARY||type==DB.OTHER) {
            /*if(type==DB.BLOB||type==DB.BINARY){
               if(binValue==null) return "null";
               else if (isChanged) return "?";
               else return "";
            }*/
            if((type==DB.BLOB||type==DB.BINARY) && binValue==null)  return "null";
            if(type==DB.CLOB && value==null)     return "null";
            if((type==DB.BLOB||type==DB.CLOB||type==DB.BINARY) && isChanged)  return "?";
            return "";
         }
         if(value==null)                         return "null";
         if(type==DB.VARCHAR)                    return "'"+value+"'";
         if(type==DB.INTEGER||type==FLOAT||type==INT_IDENTITY||type==INT_SEQUENCE) return value;
         if(type==DB.DATE) {//find proper SQL format, evaluate it and use it in ORA or MS notation
            String fmt = formatSQL!=null?formatSQL:_dateFormatSQL;
            if(fmt!=null){
               if(fmt.matches("\\d+")) //contains digits only => Microsoft style, use convert(datetime,value,fmt)
                  return "convert(datetime,'"+value+"',"+fmt+")";
               return "to_date('"+value+"','"+fmt+"')"; //else, assume Oracle style
            }
            return "'"+value+"'";
         }
         return value;
      }
      /** extracts encoding from otherValue where it is possibly stored in format like "text/plain|t.txt|ISO-8859-1".
      If not found, returns null. Caller may continue with java.nio.charset.Charset.defaultCharset().name() call */
      public String getEncoding() {
         if(type!=OTHER && otherValue!=null && otherValue instanceof String) {
            String[] toks = ((String)otherValue).split("|");
            if(toks.length>2 && toks[2].length()>0)
               return toks[2];
         }
         return null;
      }
      /** populates value related members (value, binValue, oldValue, otherValue, actionHint, isDBnull, isChanged) with otherField members*/
      public void populateValues(Field otherField){
         value = otherField.value;
         binValue = otherField.binValue;
         oldValue = otherField.oldValue;
         otherValue = otherField.otherValue;
         actionHint = otherField.actionHint;
         isDBnull = otherField.isDBnull;
         isChanged = otherField.isChanged;

      }
      /** calls super.clone(), sets isDBnull = isChanged = false, sets all 'value' variants to null; used in daoRead()*/
      public Field cloneEmpty() {
         Field retObj = null;
         try {
            retObj = (Field)super.clone();
            retObj.value = null;
            retObj.binValue = null;
            retObj.oldValue = null;
            retObj.otherValue = null;
            retObj.actionHint = null;
            isDBnull = isChanged = false; //keep preset and formats
         }
         catch(Exception e) {
            log.error(e, "Problem while cloning empty DB.Field");
         }
         return retObj;
      }
      /** calls super.clone() and binValue.clone(), does deep copy of Field object */
      public Object clone() {
         Field retObj = null;
         try {
            retObj = (Field)super.clone();
            if(binValue!=null) retObj.binValue = (byte[])binValue.clone();
         }
         catch(Exception e) {
            log.error(e, "Problem while cloning DB.Field");
         }
         return retObj;
      }
      /** equals makes comparison of type, name and value only. Do other checking yourself for stronger equality */
      public boolean equals(Field f){
         if(f==null || !(f instanceof DB.Field)) return false;
         if(name.equals(f.name) && type==f.type && value.equals(f.value))
           return true;
         return false;
      }
      /** generates XML for all Field members except binValue and otherValue. Value is wrapped in CDATA section */
      public String toXML() {
         String val = value==null?"":"<![CDATA["+value.replaceAll("]]>", "]]]]><![CDATA[>")+"]]>";//split CDATA on ]]>-
         return "<Field><name>"+name+"</name><type>"+type+"</type><format>"+format+"</format>"+
           "<formatSQL>"+formatSQL+"</formatSQL><preset>"+preset+"</preset><isDBnull>"+isDBnull+"</isDBnull>"+
           "<actionHint>"+actionHint+"</actionHint><isChanged>"+isChanged+"</isChanged>"+
           "<value>"+val+"</value></Field>";
      }
      /** provides name, type, format and value members of Field in the form like "Field: name="+name+ ... */
      public String toString() {return "Field: name="+name+" type="+type+" format="+format+" value="+value;}
   }

   
   /**Class holds information about database table - name, fields list, join condition. Has members:
   <ul> <li> name of table, always in upper case. nameFull is qualified with schema name (and or owner) if later is defined in DB.
   </li><li> field[] - array of field descriptors
   </li><li> joinCond - join condition string, applicable only to Dao 'extra tables' (tables having join to main table)
   </li></ul> Note, all class members listed above are final. */
   public static class Table implements Serializable, Cloneable {
      /** full (may include schema) name of table as passed in class constructor, always in UPPER case */
      public final String name;
      /** table alias extracted from 'name' param in constructor (say from "myDB.Item it" string), if not supplied is set to "a" */
      public final String alias;
      /** array of field descriptors - sum core fields and audit fields passed to constructor */
      public final Field[] fields;
      /** join condition - applicable only to 'extra tables' - tables having join to main table data from which we
      want to include in Dao. Name of main table in joinCond string should be aliased as 'a.', name of extra table supplied
      as full table name, for example: "a.cust_id=order.customer_id" */
      public String joinCond;
      /** pointer to parent DB object */
      public DB parentDB;

      /** constructor taking in table name and array of field descriptors, used in DBtablesArr definition. */
      public Table(String tblName, Field[] coreFlds){this(tblName, coreFlds, null);}
      /** constructor used in Dao definition and applicable to 'extra tables' only, takes in join condition as a parameter.
      Tip: list in coreFlds array only fields that you want to be added and populated in Dao, not all 'extra table' fields */
      public Table(String tblName, String joinCond, Field[] coreFlds){this(tblName, coreFlds, null); this.joinCond=joinCond;}
      /** constructor taking in table name, array of field descriptors and array of audit fields, used in DBtablesArr definition.
      Tip: if table name has space inside, value before space is considered to be actual table name and after space - alias,
      otherwise alias is set to "a". If nm==null || coreFlds==null throws NullPointerException (type of RuntimeException, unchecked)*/
      public Table(String tblName, Field[] coreFlds, Field[] extraFlds){
         if(tblName==null || coreFlds==null)
            throw new NullPointerException("DB.TableDscr(): name="+tblName+" coreFlds="+coreFlds+", neither can be null");
         String[] nms = tblName.split(" ");
         if(nms.length==2 && !"".equals(nms[1].trim())){
            name = nms[0].toUpperCase();
            alias = nms[1];
         }
         else { //if(nms.length == 1){
            name = tblName.toUpperCase();
            alias = "a";
         }
         if(extraFlds!=null && extraFlds.length>0) {
           fields = new Field[coreFlds.length+extraFlds.length];
           for(int i=0; i<coreFlds.length; i++) fields[i] = coreFlds[i];
           for(int j=0; j<extraFlds.length; j++) fields[coreFlds.length+j] = (Field)extraFlds[j].clone();
         }
         else fields = coreFlds;
         for(int i = 0; i<fields.length; i++)
            fields[i].parentTable = this;
      }

      /** utility: returns SQL that can be used as a good base for 'create table' statement, sql2000type forces use of datetime for DATE fields */
      public String sqlCreateTable(boolean sql2000type) {
         StringBuffer b = new StringBuffer("create table ").append(name).append(" (");
         StringBuffer seq = new StringBuffer();
         for(int i=0; i<fields.length; i++) {
            b.append(fields[i].name).append(" ");
            if(fields[i].type==VARCHAR){
               if(fields[i].format!=null)
                  b.append("char(").append(fields[i].format.length()).append(")");
               else b.append("varchar(256)");
            }
            else if(fields[i].type==INTEGER)
               b.append("int");
            else if(fields[i].type==INT_IDENTITY)
                b.append("int IDENTITY not null");
            else if(fields[i].type==INT_SEQUENCE && parentDB!=null){ //sequences are mostly Oracle => use Ora syntax
               b.append("int");
               if(seq.length() == 0) seq.append(";");
               seq.append("\n create sequence ").append(parentDB.getSequenceName(name, fields[i].name)).append(";");
            }
            else if(fields[i].type==FLOAT)
               b.append("float");
            else if(fields[i].type==DATE)
               b.append((sql2000type?"datetime":"date"));         //Ora-date, MS-datetime
            else if(fields[i].type==BINARY)
               b.append("blob");
            else if(fields[i].type==BLOB)
               b.append("blob");
            else if(fields[i].type==CLOB)
               b.append("clob");
            else b.append("varchar(512)"); //size probably not OK, but cannot leave empty
            if(i < fields.length-1) b.append(", ");
         }
         return b.append(")").append(seq).toString();
      }
      /** returns super.clone() - dont need deep copy of eTblArr as it is final. Note, method is public, not protected */
      public Object clone() {try {return super.clone();} catch(Exception e){log.error(e,"clone() failed"); return null;}}
      /** utility: returns table name and space separated list of fields */
      public String toString() {
         StringBuffer b = new StringBuffer("; fields are");
         for(int i=0; i<fields.length; i++) b.append(" ").append(i).append(fields[i].toString());
         return "Table: name="+name+b.toString();}
      /** returns XML describing table: nameFull, list of field XMLs. */
      public String toXML() {
         StringBuffer b = new StringBuffer("<TableDscr><name>").append(name).append("</name>");
         for(int i=0; i<fields.length; i++) {
            b.append(fields[i].toXML()).append("\n");
         }
         return b.append("\n</TableDscr>").toString();
      }
   }


   //-------------- INNER UTILITY CLASSES --------------
   /** Class DB.Dao holds all fields defined in main and extra tables and defines operations of storing Dao as a
   single row of database table. Generated insert, update and delete SQL deal with 'main' table only and are like: <pre>
   update Item set Name='PC Intel' where ID=123 and Name='PC Athlon'
   delete Item where ID=123 and Name='PC Athlon'
   </pre>
   Generated 'where' part of the clause for update and delete SQL prevents concurrent modifications.
   <br/> <b>Major class functions</b> you will likely use more often then the others are:
   <ul> <li> Dao(DB.Table) or Dao(DB.Tabl, DB.Table[]) - constructors used to define single and multitable Dao like: <br/>
    class Item extends Dao{public Item(){super(myDB.instance.getTable("Item"), new Table[]{new Table("Orders o","o.ID=a.orderID",...
   </li><li> hasField(name) and getField(name) - checker and getter for Field object in member field arrays
   </li><li> getFieldValue(name), setFieldValue(name) - shortcut getter and setter to field's value
   </li><li> setFieldFunction(fldName, funcName) - sets field value and indicates to Dao that value to be treated as
      function call (like Oracle sysdate or MS SQL getdate())
   </li><li> markForDelete() - sets Dao state to DO_DELETE so underlying row will be removed from database on daoSave() call
   </li><li> daoSave(DBservice) or daoSave()- will persist changes made to Dao fields to database
   </li><li> Dao(DB.Table) or Dao(DB.Tabl, DB.Table[]) - constructors used to define single and multitable Dao like: <br/>
    class Item extends Dao{public Item(){super(myDB.instance.getTable("Item"), new Table[]{new Table("Orders o","o.ID=a.orderID",...
   </li></ul> */
   public static class Dao implements Serializable, Cloneable {
      /** constants for the set of states DAO can go through - NEW_EMPTY=1, DO_INSERT=2, DB_READ=3, DO_UPDATE=4, DO_DELETE=5 */
      protected static final int NEW_EMPTY=1, DO_INSERT=2, DB_READ=3, DO_UPDATE=4, DO_DELETE=5;
      /** storage of information on main table - table descriptor, final */
      protected final Table mTbl;
      /** supplementary array of info on extra tables - tables having join condition to main table some fields of which we want to read */
      protected final Table[] eTblArr;
      /** DB.Field[] array holding superset of clones of main table fields (from mTbl) and 'extra table' fields (from eTblArr). */
      protected Field[] fldsArr;
      /** indicator of DAO state - NEW_EMPTY, DB_READ, DO_INSERT, DO_UPDATE, ... */
      protected int state;
      /** if true, for fields with NULL value in DB, Dao will store field's Java value as an emty String "". Default false.
      Note, you can still find out if value read from DB was null by checking field's flag isDBnull. */
      public boolean readNullAsEmpty = false;

      //---- primary functions ---------
      /** simple (singletable) constructor using main table descriptor as a parameter (which should not ne null), sets state to NEW_EMPTY.*/
      public Dao(Table tblDsc) throws NullPointerException {this(tblDsc, null);}
      /** multitable constructor using main table descriptor and array of extra tables as parameters, sets state to NEW_EMPTY.
      Can throw some types of RuntimeException (unchecked, don't need to catch) if parameters are wrong:
      If tblDscr==null throws NullPointerException, if table aliases are not unique throws IllegalArgumentException */
      public Dao(DB.Table tblDsc, DB.Table[] eArr) throws NullPointerException, IllegalArgumentException {
         if(tblDsc==null)
            throw new NullPointerException("tblDsc cannot be null as it is a descriptor of the main Dao table");
         mTbl = tblDsc;
         eTblArr = eArr;
         //check if all aliases are unique
         String[] aliases = new String[1+(eTblArr!=null?eTblArr.length:0)];
         aliases[0] = mTbl.alias;
         for(int i=0; eTblArr!=null&&i<eTblArr.length; i++){
             for(int j=0; j<aliases.length&&aliases[j]!=null; j++){ //break on null member in aliases
               if(eTblArr[i].alias.equalsIgnoreCase(aliases[j]))
                  throw new IllegalArgumentException("Table "+eTblArr[i]+" alias '"+aliases[j]+"' already used in Dao");
             }
             aliases[i+1] = eTblArr[i].alias;
         }
         //calculate number of 'extra table' fields
         int cntExtra = 0;
         for(int i=0; eTblArr!=null&&i<eTblArr.length; i++) cntExtra = cntExtra + eTblArr[i].fields.length;
         fldsArr = new DB.Field[mTbl.fields.length+cntExtra];
         for(int i=0; i<mTbl.fields.length; i++)            //populate main
            fldsArr[i] = (DB.Field)mTbl.fields[i].clone();
         for(int i=0, k=mTbl.fields.length; eTblArr!=null&&i<eTblArr.length; i++){ //populate extra
            for(int j=0; eTblArr[i].fields!=null&&j<eTblArr[i].fields.length; j++){
               fldsArr[k++] = (DB.Field)eTblArr[i].fields[j].clone();
            }
         }
         state = NEW_EMPTY;
      }

      //------------ Persistence: Methods working with DBservice --------------
      /** creates default DBservice, calls daoSave(DBservice) and closes DBservice; convenient for app with single database */
      public void daoSave() throws SQLException {
         DBservice srv = new DBservice();
         daoSave(srv, null);
         srv.close();
      }
      /** main method to persist data to database. Calls daoSave(DBservice, null) so Dao class will generate approprite sql itself*/
      public void daoSave(DBservice srv) throws SQLException {
         daoSave(srv, null);
      }
      /** persist data to database, set state NEW_EMPTY for delete operation and DB_READ for update/insert. Note, only
      'main' table fields are stored. If first field value==null it will be populated by value from the sequence - actually,
      by the result of DBservice.queryString(sqlNextID()). Remember, for BINARY and BLOB fields data are drawn from binValue.
      Method is used if you need utmost control of SQL used.<br/>Hint to caller: if no DB conection, save Dao in TransactionHandler or XML file */
      public void daoSave(DBservice srv, String execSQL) throws SQLException {
         //1. save itself if in savable state
         if(state!=DB_READ && state!=NEW_EMPTY) {
            if(state==DO_DELETE){//Delete
               String sql = execSQL!=null?execSQL:sqlDelete();
               srv.execRowChange(sql);
               for(int i=0; i<getFieldsCount(); i++) {setFieldValue(i, null); getField(i).setBinValue(null);}
               state = NEW_EMPTY;
            }
            else { //insert and update
               //check if LOB or BIN column changed to implement proper ins/upd
               boolean LOBfieldsChanged = false;
               HashMap lobFlds = new HashMap();
               for(int i=0, j=0; i<mTbl.fields.length; i++){ //i - field ordinal, j - LOB ordinal
                  DB.Field fld= getField(i);
                  if((fld.type==BLOB||fld.type==CLOB||fld.type==BINARY) && fld.isChanged){
                     //sqlValue(): if((type==DB.BLOB||type==DB.CLOB||type==DB.BINARY) && isChanged) [and val!=null] return "?";
                     if(fld.type==CLOB && fld.value!=null)
                        {lobFlds.put(""+java.sql.Types.CLOB+","+(++j), fld.value); LOBfieldsChanged = true;}
                     else if((fld.type==BLOB||fld.type==BINARY) && fld.binValue!=null)
                        {lobFlds.put(""+java.sql.Types.BLOB+","+(++j), fld.binValue); LOBfieldsChanged = true;}
                   }
               }
               if(state==DB.Dao.DO_UPDATE){//Update
                  String sql = execSQL!=null?execSQL:sqlUpdate();
                  if(LOBfieldsChanged)
                     srv.execRowChangePrep(sql, lobFlds, null);
                  else
                     srv.execRowChange(sql);
                  state = DB_READ;
               }
               if(state==DB.Dao.DO_INSERT) {//Insert
                  String identFldName = null;
                  for(int i=0; i<mTbl.fields.length; i++){ 
                     DB.Field fld= getField(i);
                     if(fld.type==INT_IDENTITY && fld.value==null) {//Insert with INT_IDENTITY field - only one is expected
                        identFldName = fld.name;
                     }
                     if(fld.type==INT_SEQUENCE && fld.value==null) {//Insert with INT_SEQUENCE field - can be few
                        String nv = srv.queryString("select "+mTbl.parentDB.getSequenceName(mTbl.name,fld.name)+".nextval from dual");
                        setFieldValue(i, nv);
                     }
                  }
                  String sql = execSQL!=null?execSQL:sqlInsert();
                  long identColVal = -1;
                  if(LOBfieldsChanged)
                     identColVal = srv.execRowChangePrep(sql, lobFlds, identFldName);
                  else
                     identColVal = srv.execRowInsert(sql, identFldName);
                  if(identFldName != null && identColVal != -1)
                     getField(identFldName).value = ""+identColVal; //don't use setFieldValue() as it will also make isChanged
                  for(int i=0; i<mTbl.fields.length; i++) getField(i).isChanged = false;
                  state = DB_READ;
               }
            }
         }
      }
      /** populates Dao fields by extracting data from the result set. Each field value is populated in a way corresponding
      to field's type: BINARY and BLOB fields get binValue member populated, OTHER call readOtherType() for handling, all
      other field types have value member populated.
      As 'value' member is Java String, for DATE and FLOAT field types field's 'format' member is used in conversion. */
      public void rs2members(ResultSet rs) throws SQLException {
         int count = getFieldsCount();
         for(int i=0; i<count; i++){
          Field field = getField(i);
          try{
            if(field.type==BINARY) {
               field.setBinValue(rs.getBytes(field.name));
               //Log.debug("\t BINARY field "+field.name+" length "+(field.binValue!=null?field.binValue.length:-1));
            }
            else if(field.type==BLOB) {
               if(field.format!=null && "LOB-READ".equals(field.actionHint)) {
                  java.sql.Blob b = rs.getBlob(field.name);
                  //Log.debug("\t got BLOB="+where+" size "+(where!=null?where.length():-1));
                  if(b != null)
                     field.setBinValue(b.getBytes(1, (int)b.length()));//blob size should be < int max (2,147,483,647)
                  field.format = null;//we don't want for this to be passed from descriptor
               }
            }
            else if(field.type==CLOB) {
               if(field.format!=null && "LOB-READ".equals(field.actionHint)){
                  field.setValue(rs.getString(field.name));//java.sql.Clob where = rs.getClob(field.name); ...
                  field.format = null;//we don't want for this to be passed from descriptor
               }
            }
            else if(field.type==OTHER && mTbl.parentDB!=null) {
               mTbl.parentDB.readOtherType(rs, field, this);
            }
            else if(field.type==DATE){
               GregorianCalendar cal = new GregorianCalendar();
               Date temp = rs.getDate(field.name, cal);
               if(temp != null) {
                  cal.setTime(temp);
                  if(cal.get(Calendar.HOUR_OF_DAY)==0 && cal.get(Calendar.MINUTE)==0 && cal.get(Calendar.SECOND)==0){
                     java.sql.Time timeTemp = rs.getTime(field.name, cal); //some stupid (MS) drivers require separate Time call
                     temp = new Date(temp.getTime()+timeTemp.getTime()+(cal.getTimeZone().getRawOffset()));//subtract Timezone
                  }
                  String fmt = field.format!=null?field.format:_dateFormatJava;
                  if(fmt != null) {
                     field.setValue(new java.text.SimpleDateFormat(fmt).format(temp));
                  }
                  else
                     field.setValue(rs.getString(field.name));//Formats in JDBC date format: yyyy-mm-dd
               }
               //Log.debug("\t set Date Field Value: Date="+(field.value==null?"NULL":field.value));
            }
            else if(field.type==FLOAT){
               if(field.format != null) {
                  double temp = rs.getDouble(i+1); //cannot be null, null returned as 0, take care of it below
                  field.setValue(new java.text.DecimalFormat(field.format).format(temp));
               }
               else
                  field.setValue(rs.getString(field.name));
            }
            else //all other field types
               field.setValue(rs.getString(field.name));//getString():if the value is SQL NULL, the value returned is null
            //handle NULLs and set attributes
            if(rs.wasNull()){
               field.isDBnull = true;
               if(readNullAsEmpty && field.type!=BINARY && field.type!=BLOB && field.type!=CLOB)
                  field.setValue("");
               else {
                  field.setValue(null);
                  field.setBinValue(null);
               }

            }
            field.isChanged = false;
            field.actionHint = null;
            field.oldValue = null; //do we really need to change it? probably, yes
          }catch(SQLException e){log.error(e, "field="+field.toString()); throw e;}
         } //for
         state = DB_READ;
      }

      //------------ Get/Set functions --------------
      /** returns total number of fields in the Dao - sum of fldsArr and fldsExtra */
      public int getFieldsCount() {
         return fldsArr.length;
      }
      /** returns false if no such field found in Dao, case insensitive. Note, field names in Dao are always lowercase. */
      public boolean hasField(String nm) {
         for(int i=0; i<getFieldsCount(); i++){
            if(getFieldName(i).equalsIgnoreCase(nm))
               return true;
         }
         return false;
      }
      /** returns Field object for the field at specific number in table descriptor.
      Throws IndexOutOfBoundsException if no such field found */
      public DB.Field getField(int i) throws IndexOutOfBoundsException {
         if(i>=getFieldsCount()) {
            log.warning("DB.Dao.getField("+i+"): out of range");
            throw new IndexOutOfBoundsException("There is no field #"+i+" in Dao "+getClass().getName());
         }
         return fldsArr[i];
      }
      /** returns Field object for the named field, case insensitive. Note, field names in Dao are always lowercase.
      Throws unchecked (don't have to catch, likely program logic error) NoSuchElementException if no such field found */
      public DB.Field getField(String nm)  throws NoSuchElementException {
         for(int i=0; i<getFieldsCount(); i++){
            if(getFieldName(i).equalsIgnoreCase(nm))
               return getField(i);
         }
         throw new NoSuchElementException("There is no field "+nm+" in Dao "+getClass().getName());
      }
      /** handy shortcut to <code> getField(int i).name </code> call*/
      public String getFieldName(int i) throws IndexOutOfBoundsException {
         return getField(i).name;}
      /** handy shortcut to <code> getField(int i).getValue() </code> call*/
      public String getFieldValue(int i) throws IndexOutOfBoundsException {
         return getField(i).getValue();}
      /** handy shortcut to <code> getField(String nm).getValue() </code> call*/
      public String getFieldValue(String nm) throws java.util.NoSuchElementException {
         return getField(nm).getValue();}
      /** handy shortcut to <code> setFieldValue(int i, String s) </code> call. Throws unchecked (you don't have to catch
      it, usually indicates program logic error) NoSuchElementException if no such field found */
      public void setFieldValue(String nm, String val) throws java.util.NoSuchElementException {
         for(int i=0; i<getFieldsCount(); i++){
            if(getFieldName(i)!=null && getFieldName(i).equalsIgnoreCase(nm)){
               setFieldValue(i, val);
               return;
            }
         }
         throw new NoSuchElementException("There is no field "+nm+" in Dao "+getClass().getName());
      }
      /** indicates that in database value of this field is set by function output (like sysdate, getdate, SYSTEM_USER, etc).
      Affects daoRead() and daoSave(), reset to false by daoRead(). Throws unchecked (you don't have to catch it)
      NoSuchElementException if no such field found */
      public void setFieldFunction(String fldNm, String funcNm) throws NoSuchElementException {
         setFieldValue(fldNm, funcNm);
         getField(fldNm).actionHint = "isDBfunction";
      }
      /** will set field value for the field at specific number in table descriptor and mark Dao dirty.
      Throws IndexOutOfBoundsException if no such field found */
      public void setFieldValue(int i, String value)  throws IndexOutOfBoundsException {
         if(i>=getFieldsCount()) {log.warning("DB.Dao.setFieldValue("+i+"): out of range, exception to follow");}
         getField(i).setValue(value); //Throws NoSuchElementException
         if(state==DB_READ || state==DO_UPDATE)
            state = DO_UPDATE;
         if(state==NEW_EMPTY || state==DO_INSERT)
            state = DO_INSERT;
      }
      /** marks row for deletion by daoSave() - sets its state to DO_DELETE */
      public void markForDelete() {
         state = DO_DELETE;
      }

      //------ functions returning SQL strings --------------
      /** returns Where clause consisting of table joins and 'fieldNm=value' or 'fieldNm is null' sets. Function is used
      in sqlSelect(), sqlDelete(), sqlUpdate() that pass in param string "select", "delete", "update" accordingly.
      In case of 'select' param, return clause also includes FROM part that may specify any join defined.
      All fields of main table except of BINARY, BLOB, CLOB and OTHER types and NULL ones will be present in the clause.
      Fields with NULL value will be present only if row was read from database (state==DB_READ,DO_UPDATE,DO_DELETE).
      In "select" case you can force generation of 'fld is null' pieces for specific fields by calling setWhereIsNull().
      If state==DO_UPDATE ("update" case) and field.isChanged==true, field's 'oldValue' is used instead of the actual value.
      <br/>Sample 1: "FROM Order a left join Item it on a.itemID=it.ID WHERE  a.type='MP3' and a.price is null "
      <br/>Sample 2: "FROM Order a, Item it WHERE a.itemID=it.ID and a.type='MP3' and a.price is null "
      <br/> in sample Order is the main table, Item - extra tables for which join conditions were specified 'join' or 'old' way.
      Note that for 'item' table join condition was specified in 'full form', for 'order' as  'a.OrderID=Order.ID' */
      public String sqlWhere(String param){
         StringBuffer where = new StringBuffer(""), from = new StringBuffer("");
         int fldCount = mTbl.fields.length;
         //1. if it is 'select' statement, build list of joins, like: ' and a.ItemID=Item.ID and a.OrderID=Order.ID'
         if("select".equals(param)) { //also handle full form join statements that start with "join", "left", "right"
            from.append(" from "+mTbl.name+" "+mTbl.alias);
            for(int i=0; eTblArr!=null&&i<eTblArr.length; i++){
               if(eTblArr[i].joinCond != null){
                  String jstr = eTblArr[i].joinCond.toUpperCase();
                  if(jstr.startsWith("JOIN ")||jstr.startsWith("LEFT ")||jstr.startsWith("RIGHT ")||jstr.startsWith("OUTER "))
                     from.append(" ").append(eTblArr[i].joinCond);
                  else{
                     from.append(", "+eTblArr[i].name+" "+eTblArr[i].alias);
                     where.append(where.length()>0?" and ":"").append(eTblArr[i].joinCond);
                  }
               }
            }
            fldCount = fldsArr.length; //use bigger list that includes extra table fields
         }
         //2. build part of Where based on fields with non-null/empty values and not of BINARY and OTHER types
         String tblAlias = "";
         for(int i=0; i<fldCount; i++){
            Field fld = fldsArr[i];
            if(fld.type==BINARY || fld.type==BLOB || fld.type==CLOB || fld.type==OTHER || "isDBfunction".equals(fld.actionHint))
               continue;
            if("select".equals(param))
               tblAlias = fld.parentTable!=null?fld.parentTable.alias+".":"a.";
            //A. Deal with NULL and Empty fields
            //add 'fld.isDBnull' if it is: a) val==null and state=={DB_READ,DO_UPDATE,DO_DELETE} where) select and actionHint==FORCED_NULL
            if((fld.isDBnull&&(state==DB_READ||state==DO_UPDATE||state==DO_DELETE))
              || ("select".equals(param)&&"FORCED_NULL".equals(fld.actionHint))){
               where.append(where.length()>0?" and ":"").append(tblAlias).append(fld.name).append(" is null");
               continue;
            }
            else if(fld.value == null)//for all other cases of value==null - skip
               continue;
            //B. for update, temporary (for generation 'where' clause) replace field.value with field.oldValue
            String val = fld.value;
            if(state==DO_UPDATE && fld.isChanged==true)
               fld.value = fld.oldValue;
            //C. generate name-value pair in SQL format
            String fldNm = tblAlias+fld.name;
            String pair = where.length()>0?" and ":"";
            if(fld.type==DB.DATE) {//find proper SQL format, evaluate it and use it in ORA or MS notation
               String fmt = fld.formatSQL!=null?fld.formatSQL:_dateFormatSQL;
               if(fmt!=null){
                  if(fmt.matches("\\d+")) //contains digits only => Microsoft style, use convert(datetime,value,fmt)
                     pair = pair + "'"+fld.value+"'=convert(varchar(32),"+fldNm+","+fmt+")";
                  else
                     pair = pair + "'"+fld.value+"'=to_char('"+fldNm+"','"+fmt+"')"; //else, assume Oracle style
               } else
                  pair = pair + "fld.name='"+fld.value+"'";
            } else //for all other field types sqlValue() is good enough
               pair = pair + fldNm+"="+fld.sqlValue();
            where.append(pair);
            fld.value = val; //restore value in case it was changed to oldValue
         }
         String res = from.append((where.length()==0)?"":" where "+where).toString();
         return res;
      }
      //------------ Persistence: public select/insert/update/delete ----------
      /** one of main functions. Returns SQL that can be used to delete this Dao object in database */
      public String sqlDelete() {
         return "delete from "+mTbl.name+sqlWhere("delete");
      }
      /** one of main functions. Returns SQL that can be used to insert this Dao object to database */
      public String sqlInsert() {
         StringBuffer ids=new StringBuffer(), vals=new StringBuffer();
         for(int i=0; i<mTbl.fields.length; i++){
            Field fld = getField(i); //note, we need to get Field from fldsArr, not mTbl.fields[i]
            if((fld.value==null && !fld.preset) || fld.type==INT_IDENTITY || fld.type==INT_SEQUENCE)
               continue; //skip all those above
            if(ids.length() > 0) {//i.e. it's not the first entry
               ids.append(", ");
               vals.append(", ");
            }
            ids.append(fld.name);
            vals.append(fld.preset?"DEFAULT":fld.sqlValue());
         }
         return "insert into "+mTbl.name+" ("+ids.toString()+") values ("+vals.toString()+")";
      }
      /** one of main functions. Returns SQL that can be used to update this Dao object in database */
      public String sqlUpdate() {
         StringBuffer b = new StringBuffer();
         for(int i=0; i<mTbl.fields.length; i++){
            if(!fldsArr[i].isChanged && !fldsArr[i].preset)
               continue;
            b.append(", ").append(fldsArr[i].name).append("=").append(fldsArr[i].preset?"DEFAULT":fldsArr[i].sqlValue());
         }
         if(b.length()>1) //that means there was at least one entry, so buffer has front of ", " - delete it
            b.delete(0, 2);
         return "update "+mTbl.name+" set "+b.toString()+sqlWhere("update");
      }

      //------------ Persistence: Methods working with XML, do not use Field.toXML() --------------
      /** persist: returns XML like &lt;izFrame.mydao>&lt;fld1>val1&lt;/fld1>&lt;fld2>val2&lt;/fld2>...&lt;izFrame.mydao>*/
      public String toXML() {
         StringBuffer b = new StringBuffer("<").append(getClass().getName()).append(">");
         for(int i=0; i<fldsArr.length; i++){
            b.append("<"+getFieldName(i)+">"+getFieldValue(i)+"</"+getFieldName(i)+">");
         }
         return b.append("</").append(getClass().getName()).append(">").toString();
      }
      /** persist: populates Dao fields vith value related data (@see Field.populateValues()) of same named fields of other Dao*/
      public void populateValuesFromDao(Dao other) {
         for(int i=0; i<fldsArr.length; i++){
            if(other.hasField(fldsArr[i].name))
               fldsArr[i].populateValues(other.getField(fldsArr[i].name));
         }
      }
      /** persist: populates Dao fields values using XML like &lt;izFrame.mydao>&lt;fldNm1>val1&lt;/fldNm1>..2..&lt;izFrame.mydao>*/
      public void fromXML(String xml) throws java.util.NoSuchElementException {
         for(int i=0; i<fldsArr.length; i++){ //populate fields
            String[] vals = Utils.readXMLelements(getFieldName(i), xml);
            if(vals==null || vals.length!=1)
               throw new NoSuchElementException("duplicate or missing elements in xml: "+xml);
            if("null".equals(vals[0]))
               vals[0] = null;
            setFieldValue(i, vals[0]);
         }
      }
      /** persist: creates arbitrary Dao from XML like &lt;izFrame.mydao>&lt;fldNm1>val1...&lt;izFrame.mydao> */
      public static Dao daoFromXML(String xml) throws Exception {
         String classNm = xml.substring(xml.indexOf(">")+1, xml.indexOf("</")-2);
         try {
            Dao dao = (Dao)Class.forName(classNm).newInstance(); //classNm like "appCommon.MyDao"
            dao.fromXML(xml);
            return dao;
         }
         catch (ClassNotFoundException e) {
            log.error("DB.Dao.daoFromXML(): cannot load class "+classNm+", "+e.getMessage());
            throw e;
         }
         catch (IllegalAccessException e) {
            log.error("DB.Dao.daoFromXML(): cannot construct class "+classNm+"(), is constructor public? "+e.getMessage());
            throw e;
         }
         catch (InstantiationException e) {
            log.error("DB.Dao.daoFromXML(): cannot construct class "+classNm+"(), is constructor defined? "+e.getMessage());
            throw e;
         }
      }
      //------------ Standard generic overrides of Object class --------------
      /** makes shallow copy of Dao mTbl, deep copy of fldsArr and fldsExtra arrays, can throw unchecked exceptions*/
      @Override public Object clone() throws UnsupportedOperationException {
         Dao retObj = null;
         try {
            retObj = (Dao)super.clone();
            if(fldsArr != null){
               retObj.fldsArr = (DB.Field[])fldsArr.clone();
               for(int i=0; i<fldsArr.length; i++) retObj.fldsArr[i] = (DB.Field)fldsArr[i].clone();
            }
         }
         catch(Exception e) {
            log.error(e, "Problem while cloning DB.Dao");
            throw (UnsupportedOperationException)new UnsupportedOperationException("Problem cloning DB.Dao").initCause(e);
         }
         return retObj;
      }
      /** makes deep copy of fldsArr, but using Field.cloneEmpty() method, marks clone with NEW_EMPTY state.
      Used in DaoList.daoRead(). Throws unchecked UnsupportedOperationException if super.clone() or Field.clone() fails. */
      public Dao cloneEmpty() throws UnsupportedOperationException {
         Dao retObj = null;
         try {
            retObj = (Dao)super.clone();
            if(fldsArr != null){
               retObj.fldsArr = (DB.Field[])fldsArr.clone();
               for(int i=0; i<fldsArr.length; i++) retObj.fldsArr[i] = (DB.Field)fldsArr[i].cloneEmpty();
            }
            retObj.state = NEW_EMPTY;
         }
         catch(Exception e) {
            log.error(e, "Problem while cloning empty DB.Dao");
            throw (UnsupportedOperationException)new UnsupportedOperationException("Problem cloning DB.Dao").initCause(e);
         }
         return retObj;
      }
      /** utility: returns Dao name and space separated list of fieldName=sqlValue() */
      @Override public String toString() {
         StringBuffer b = new StringBuffer(getClass().getName()+": ");
         for(int i=0; i<fldsArr.length; i++){
            b.append(getFieldName(i)).append("=").append(getField(i).sqlValue()).append(" ");
         }
         return b.toString();
      }
   }

   /** Class DB.DaoList defines operations of reading and storing rowsets of data from/to database and XML files. Which
   database tables will be read and what are the query parameter is defined by field values of aggregated descDao Dao.
   Generated select SQL will populate both 'main' and 'extra' fields of Dao rows, i.e. class can generate select SQL for
   multiple table queries (using joins) like: <br>
   select a.*, item.Name from Order a join Item it on a.itemID=it.ID where a.ID=123
   <br> Class DB.DaoList aggregates DB.Dao that is used in two ways: a) as a storage of search parameters, b) as a
   template for building new rows using variant of clone() method.
   <br/> <b>Major class functions</b> you will likely use more often then the others are:
   <ul> <li> setSearchParam(name,value) - adds to where clause of generated select SQL smart version of 'name=value' piece
   </li><li> daoRead() - read data from database, populate each row of 'rows' collection with instance of Dao.
   </li><li> daoSave() - save in database all Dao instances of rows collection that needs saving, wrap in transaction
   </li><li> getRow(i) - get specific Dao from the least read from database
   </li><li> markLOBfield4read(name) - will inform daoRead() to populate CLOB and BLOB field values, call it before
      daoRead(). CLOB and BLOB fields values are populated only if they were preliminary marked with the method.
   </li><li> DaoList(Dao) - mostly used in the DB definition class in the form like: <br/>
      public static class UserLst extends DB.DaoList{ public UserLst(){ super(new User()); } }
   </li></ul>
   <br> Note, one DaoList instance can potentially satisfy database reading of numerous tables by providing setDscrDao()
   method to change inner Dao descriptor on the fly.
   <p/> Implementation Limitations:
   <ul> <li> no read or save support for OTHER field types, overwrite readOtherType() method to handle it.
   </li><li> tables should have unique names even across schemas.
   </li></ul> */
   public static class DaoList implements Serializable, Cloneable {
      /** descriptor Dao, defines rowset we want to work with - main table and few related fields from other ones */
      protected Dao dscrDao;
      /** array list of rows (each one is Dao instance representin database row). */
      protected ArrayList rows = null;
      /** user defined search condition to use in SQL select: params, inner queries, order by, grouping, etc; default ""*/
      public String extraSrchCondition = "";

      /** default constructor. Note, that for any useful application DaoList needs dscrDao to be set */
      public DaoList() {}
      /** constructor that uses Dao as a parameter, use when you want to have specialized DaoList for every Dao. Such
      specialized DaoList is declared like: class UserLst extends DB.DaoList{public UserLst(){super(new User());}} */
      public DaoList(Dao dscrDao) {
         this.dscrDao = dscrDao;
      }
      /** returns descriptor Dao (object where search criteria are stored and that is base for cloning of rows read) */
      public Dao getDscrDao() { return dscrDao; }
      /** sets descriptor Dao (object where search criteria are stored and that is a base for cloning of rows read) */
      public void setDscrDao(Dao otherDao) { dscrDao = otherDao; }

      //------ functions establishing search criteria or special fields handling ------
      /**set field value for the field of such name in aggregated Dao, as a result will adds to where clause of
      generated select SQL smart version of 'name=value' piece. Throws unchecked NoSuchElementException if no such field */
      public void setSearchParam(String name, String value) throws java.util.NoSuchElementException {
         for(int i=0; i<dscrDao.getFieldsCount(); i++){
            if(dscrDao.getFieldName(i)!=null && dscrDao.getFieldName(i).equalsIgnoreCase(name)){
               dscrDao.getField(i).setValue(value);
               return;
            }
         }
         throw new NoSuchElementException("There is no field "+name+" in Dao "+dscrDao.getClass().getName());
      }
      /** By default, to speed up database reads, BLOB and CLOB values are not populated in daoRead. markLOBfield4read()
      will set field actionHint = "LOB-READ" which will inform daoRead() to populate CLOB and BLOB field values. Note,
      BINARY fields do not require this as they are always filled in. Throws NoSuchElementException if no such field*/
      public void markLOBfield4read(String name) throws java.util.NoSuchElementException {
         if(dscrDao.hasField(name))
            dscrDao.getField(name).actionHint = "LOB-READ";
         else
            throw new NoSuchElementException("There is no field "+name+" in Dao "+dscrDao.getClass().getName());
      }

      //------ functions returning SQL strings --------------
      /** returns string of SQL select format to populate this DaoList object. Uses sqlWhere() and extraSrchCondition */
      public String sqlSelect() {
         //1. build string in SQL format listing fields to be selected, like: a.*, Item.Description, Order.Date
         StringBuffer selectFlds = new StringBuffer("select ").append(dscrDao.mTbl.alias).append(".*"); //select a.*
         DB.Table[] tblArr = dscrDao.eTblArr;
         for(int i=0; tblArr!=null&&i<tblArr.length; i++){
            for(int j=0; tblArr[i]!=null&&j<tblArr[i].fields.length; j++){
               selectFlds.append(", ").append(tblArr[i].alias).append(".").append(tblArr[i].fields[j].name);
            }
         }
         //2. build FROM_WHERE string in SQL format listing tables and joins for select statement, like: from Order a, Item i where ...
         String from_where = dscrDao.sqlWhere("select");
         //build ending (group by, having, etc) part of select statement using extraSrchCondition param
         String t = extraSrchCondition.trim().toLowerCase();
         if(t.indexOf("and ")==0) //if user put 'and ' at the front remove it, as we will be adding our own
            extraSrchCondition = extraSrchCondition.trim().substring(4);
         boolean specCond = false;
         if(t.indexOf("group")==0 || t.indexOf("order")==0 || t.indexOf("having")==0)
            specCond = true; //true means extraSrchCondition starts with SQL keywords which go w/o word 'where'
         int posWhere = from_where.indexOf(" where ");
         String end = (posWhere==-1||extraSrchCondition.length()==0||specCond)?""+extraSrchCondition:"and "+extraSrchCondition;
         return selectFlds.toString()+from_where+" "+end;
      }

      //------------ Persistence: Methods working with DBservice --------------
      /** creates default DBservice and query string, calls daoRead(DBservice,query) and closes DBservice; convenient for app with single database */
      public void daoRead() throws SQLException {
         DBservice srv = new DBservice();
         daoRead(srv, sqlSelect());
         srv.close();
      }
      /** creates query string (by calling sqlSelect()) and calls daoRead(DBservice, query); most common way to read data from DB */
      public void daoRead(DBservice srv) throws SQLException {
         daoRead(srv, sqlSelect());
      }
      /** read data from database, populate each row of 'rows' collection with instance of Dao having state==DB_READ.
      uses Dao.rs2members() function for proper conversion of database data to inner Dao field values. Use if you need to bypass sqlSelect(). */
      public void daoRead(DBservice srv, String query) throws SQLException {
         if(query==null) {log.error(getClass().getName()+ ".daoRead() called without specifying query sql"); return;}
         if(rows!=null) rows.clear();
         ResultSet rs = srv.query(query);
         while(rs.next()){//ResultSet cursor is initially positioned before the first row
           Dao daoN = dscrDao.cloneEmpty();
           daoN.rs2members(rs);
           addRow(daoN);
         }
      }

      /** creates default DBservice, calls daoSave(DBservice) and closes DBservice; convenient for app with single database */
      public void daoSave() throws SQLException {
         DBservice srv = new DBservice();
         daoSave(srv);
         srv.close();
      }
      /** persist data to database. If collection rows are present and at least some of them changed, save all of them
      that needs saving, wrapping all saves in Transaction, will also remove deleted rows from collection.
      <br> Hint to caller: if no conection, save list in TransactionHandler or to XML file. */
      public void daoSave(DBservice srv) throws SQLException {
         //init. check if transaction is needed and if yes, begin it
         boolean collectionRowsChanged = false;
         for(int i=0; rows!=null&&i<rows.size(); i++){
            Dao iRow = getRow(i);
            if(iRow!=null && iRow.state!=DB.Dao.DB_READ && iRow.state!=DB.Dao.NEW_EMPTY)
               collectionRowsChanged = true;
         }
         //2. save rows collection. must be careful to set state to DB_READ only after transaction commit
         if(collectionRowsChanged) {
            srv.transactionBegin();
            for(int i=0; rows!=null&&i<rows.size(); i++){
               int preState = getRow(i).state;
               if(preState==DB.Dao.NEW_EMPTY)
                  continue;
               getRow(i).daoSave(srv);
               getRow(i).state = preState;
               if(preState==DB.Dao.DO_DELETE){
                  rows.remove(i);
                  i--;//as we removed row, all rows up there change count one down
               }
            }
            //end. commit transaction and do cleanups if needed
            srv.transactionCommit();
            for(int i=0; rows!=null&&i<rows.size(); i++){
               getRow(i).state = DB.Dao.DB_READ;
            }
         }
      }

      //------------ List navigation --------------
      /** returns the inner rows collection as ArrayList */
      public synchronized ArrayList getRows() { return rows; }
      /** returns number of elements in rows collection */
      public synchronized int getRowCount() { return rows==null?0:rows.size();  }
      /** returns the element at the specified position in this list rows collection, throws IndexOutOfBoundsException */
      public synchronized Dao getRow(int i) { return rows==null?null:(Dao)rows.get(i); }
      /** adds row to the inner rows collection */
      public synchronized boolean addRow(DB.Dao row) { if(rows==null) rows=new ArrayList(); return rows.add(row); }

      //------------ Persistence to file --------------
      /** returns String of XML like <izFrame.mydao><daoListItem><fld1>val1</fld1><fld2>val2</fld2>...<izFrame.mydao> */
      public String toXML() {
         StringBuffer b = new StringBuffer("<").append(getClass().getName()).append(">\n");
         for(int i=0; i<rows.size(); i++){
            b.append("<daoListItem>"+getRow(i).toXML()+"</daoListItem>");
         }
         return b.append("\n</").append(getClass().getName()).append(">").toString();
      }
      /** creates DaoList object from string of XML like <izFrame.myList><daoListItem><fld1>val1 ...*/
      public static DaoList fromXML(String xml) throws RuntimeException {
         String classNm = xml.substring(xml.indexOf(">")+1, xml.indexOf("</")-2);
         try {
            DaoList daoList = (DaoList)Class.forName(classNm).newInstance();
            if(daoList.rows!=null) daoList.rows.clear(); else daoList.rows=new ArrayList();
            String[] vals = Utils.readXMLelements("daoListItem", xml);
            for(int i=0; vals!=null&&i<vals.length; i++){ //populate dscrDao items
               Dao item = Dao.daoFromXML(vals[i]);
               daoList.addRow(item);
            }
            return daoList;
         }
         catch (Exception e) {
            log.inform("DB.DaoList.fromXML(): cannot find or load class "+classNm+", "+e.getMessage());
            throw new RuntimeException(e.getMessage(), e);
         }
      }
      /** saves object to XML file named xml/[full class name].xml */
      public boolean daoListSaveAsXMLfile() {
         return Utils.writeFile(toXML(), "./xml/"+getClass().getName()+".lst.xml");
      }
      /** persist: populated object by data from XML file, throws RuntimeException */
      public boolean daoListRestoreFromXMLfile() throws RuntimeException {
         String xml = Utils.readFile("./xml/"+getClass().getName()+".lst.xml");
         if(xml == null) return false;
         String[] vals = Utils.readXMLelements("daoListItem", xml);
         for(int i=0; vals!=null&&i<vals.length; i++){ //populate dscrDao items
            Dao item = (Dao)dscrDao.clone();
            item.fromXML(vals[i]);
            addRow(item);
         }
         return true;
      }
      /** makes deep copy dscrDao and rows collection cloning each corresponding member, throws RuntimeException */
      @Override public Object clone() {
         DaoList retObj = null;
         try {
            retObj = (DaoList)super.clone();
            retObj.dscrDao = (Dao)this.dscrDao.clone();
            //do not share collection pointer between all list members
            retObj.rows = new ArrayList(rows.size());
            for(int i=0; i<rows.size(); i++){
               retObj.rows.add(getRow(i).clone());
            }
         }
         catch(Exception e) {
            log.error(e, "Problem while cloning DB.DaoList");
            throw new RuntimeException(e.getMessage(), e);
         }
         return retObj;
      }
   }
}//end of DB class
