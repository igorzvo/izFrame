package izFrame;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/** Class WebBean holds and displays web form and back-end data passed between JSP and UseCase.
<ul> <li> Class stores few sets of data: database persistent part (DB.DaoList), map of page fields and map of request
parameters. Functions are provided to update one storage from another.
</li><li> To faciltate display of page fields class implements tag handler - generator of corresponding html elements
for data stored in the bean.
</li></ul>
Lets look at <b>storage function</b> first. Lets see what we need to service (display) web form. Web form typically
has two types of components:
<ul> <li> components that are grouped in the html table. What is a table? Table is a collection (list) of rows. To
service them we need object that can store rows of information - DB.DaoList aggregated in WebBean does that.
</li><li> components that are not grouped in the html table - various input fields, textarea, dropdowns, check and
radio buttons, etc. Usually user is required to fill them in and based on user input application would do some
queries and service user with the result. Somehow these controls need to be managed - DB.Dao aggregated in WebBean
does that.
</li></ul>
Above mentioned two WebBean components - DB.DaoList and DB.Dao, as you may have guessed from their names, are related
to persistent data - data read from and stored to a database. But not everything we have on a html form is directly
persisted in a database - there are other elements like submit buttons or html links (you can call them link buttons)
that are capable of generating user input (which we need to capture) and which would be great to somehow manage.
To manage these presentation controls that use data not from database or use data from a database in the form of
complex html controls WebBean uses HashMap responseFlds.
<br/> Now we need storage for user input, a.k.a. request parameters. To capture and store user input WebBean is using
HashMap requestFlds.
<br/> To reiterate - there are 4 data storage elements in the WebBean. Three of them - DaoList, Dao and responseFlds
map are used to present data to a user, one element - requestFlds map stores user input. Among presentation elements
DaoList and Dao keep database persisted data, responseFields - data not directly stored in a database.
<br/> Internally Storage functionality is implemented using just three aggregated components DaoList class and 2 hash maps
(requestFlds, responseFlds). This is because DaoList class has two parts - Dao bean (so called descriptor Dao)
and the collection of rows. So, you still have four logical components, but three physical members of WebBean because
one member is a composite one. You can imagine all this as something like Russian matr'oshka: WebBean is what you see
on the surface, inside it has DaoList and two maps, DaoList has inside Dao bean.
<br/> Dao classes play major role in representing data read and saved to database, hash maps play supplementary role
allowing JSP to display additional columns or fields, most of which are based on Dao columns or fields with some twist
like formatting or data derived html (but can be anything else, like list of options for select box). This
storage separation allows to define 'extra' fields that may be present on web page but not in Dao/database and vice
versa.
<p/> As a <b>tag handler</b> for izFrame web library WebBean can generate handy html form elements, both in scriptlet form
or as SimpleTagSupport (STS) class output (see doTag() method for details). When used as a tag support class, WebBean
class instance created by JSP framework will get actual data values from instance of class that is stored in request
as the attr_dataObject attribute.
<br> For security purposes, values displayed by WebBean through STS tags or 'toHtml..()' scriptlets are converted to
'html safe' form, i.e. all angle brackets, 'and' and 'at' symbols are replaced with corresponding entities. Picture is
a bit more complex for so called 'html' columns created with addHtmlColumn() methods (list of such columns is stored in
ArrayList hlmlColsLst) - their values won't be escaped, but underlying values that were used to build html column will
be, hence, safety won't be compromised.
<p/> New instance of WebBean is typically created in next cases:
<br/> - in UseCase class execute() function by developer to initialize bean with data to display on a web page
<br/> - by JSP framework when bean is instantiated for Tag Support (STS)
<br/> - in UseCase class doBeanFromRequest() function if one cannot be found in corresponding request attribute.
<br/>Note that access to fields is case sensitive. Taking into account that fields in Dao are always lowercase and making
  responseFlds entries uppercase or mixed case you can clearly distinguish two sets.
<br/> <b>Major class functions</b> you will likely use more often then the others are:
<ul> <li> DB.DaoList getDaoList() - DaoList getter.
</li><li> DB.Dao getDaoBean(i) - getter for bean used to present database single row data or search parameters
</li><li> addField(name,val) - adds to responseFlds map field with the given name and value.
</li><li> void addHtmlColumn(newColName,existingColName,htmlTemplate) - adds to responseFlds new collection field that builds
  complex html control using existingColName and htmlTemplate; collection will be the same size as DaoList rows collection
</li><li> Object getRequestParam() -  returns request parameters and unlike request.getParameterValue() call does that
even for multipart (file submit) requests
</li><li> DB.Dao request2dao() - populates daoLst fields by values from same named requestFlds fields
</li><li> hasFieldChanged() - this is of interest if you want to handle user actions in JSF or ASP.NET style
</li></ul>
<b>Major class generated tags</b> you will likely use more often then the others on JSP pages: <pre>
&lt;%@ taglib prefix="f" uri="/WEB-INF/lib/izFrame.jar" %&gt; - this will indicate to JSP to use izFrame tag library
&lt;f:message key="Hdr_Addr"/&gt; - this will display string stored in message resources file with key Hdr_Addr
&lt;f:bean type="field" name="userAndRole"/&gt; - will display value stored in WebBean field userAndRole
&lt;f:bean type="pageInfo"/&gt; - will create couple (page name and sequence) of hidden fields on the page.
&lt;f:message type="headrow" key="loginID,passwd,..."/&gt; - will create header row for HTML table from resources
&lt;f:bean type="tablebody" rowsPerPage="20" name="editUserL,user_password,..." /&gt; style="0:align=center,..."
- will create body of HTML table filled in with WebBean data (found either in DaoList or responseFields)
&lt;f:bean type="rangeLinks" action="izFrameTutor/adminUser.jsp"/&gt; - will add page from page navigation bar
</pre>Note: you need to have JSP-API libraries (for jsp.tagext.SimpleTagSupport) in classpath to compile this class
*/
public class WebBean extends javax.servlet.jsp.tagext.SimpleTagSupport implements Serializable, javax.servlet.jsp.tagext.DynamicAttributes {

   /** storage of HTML/JSP form input fields we receive in request, populated by saveRequest() */
   protected HashMap requestFlds;
   /** storage of HTML/JSP entry form fields that together with Dao we use to populate JSP */
   protected HashMap responseFlds;
   /** storage of database data, composite of Dao descrDao and rows collection (each row is Dao similar to dscrDao) */
   protected DB.DaoList daoLst;
   /** list of 'html' columns (added with addHtmlColumn() method) - we won't use escapeChars() on these columns */
   protected ArrayList hlmlColsLst;
   /** encoding used in the submitted request - rarely used, but is need to know for languages not in Latin-1 charset */
   public String requestEncoding;
   /** class logger */
   protected static Log log = Log.getLog("izFrame.WebBean");

   /** default constructor is regired for using class as Tag in SimpleTagSupport */
   public WebBean() {
   }
   /** main constructor populates it with data (initilises WebBean with typically prepopulated DaoList) */
   public WebBean(DB.DaoList daoLst) {
      this.daoLst = daoLst;
      responseFlds = new HashMap();
   }

   //---getters, setters, has/is functions ----
   /** DaoList getter */
   public DB.DaoList getDaoList() {
      return daoLst;
   }
   /** getter for DaoList descriptor Dao - bean that we use to store search parameters and be sample row for the list */
   public DB.Dao getDaoBean() {
      return daoLst != null ? daoLst.dscrDao : null;
   }
   /** setter for DaoList descriptor Dao, will initilise (and if necessary create) aggregated DaoList with supplied Dao */
   public void setDaoBean(DB.Dao dao) {
      if (daoLst == null) {
         daoLst = new DB.DaoList();
      }
      for (int i = 0; dao != null && i < dao.getFieldsCount(); i++) {
         String name = dao.getFieldName(i); //.toLowerCase();
         if (responseFlds.containsKey(name)) {
            log.warning("setDaoBean(): Field/column " + name + " was present in responseFlds, removing it");
            responseFlds.remove(name);
         }
      }
      daoLst.dscrDao = dao;
   }
   /** adds DaoList to bean. ResponseFlds entities whose keys have same names as one of Dao fields will be removed */
   public void setDaoList(DB.DaoList daoList) {
      this.daoLst = daoList;
      setDaoBean(daoList != null ? daoList.dscrDao : null);
   }
   
   //----- working with fields -----
   /** function would return total number of fields in the Bean - total of responseFlds.size() and daoLst.getFieldsCount() */
   public int getFieldsCount() {
      return responseFlds == null ? 0 : responseFlds.size() + (daoLst == null ? 0 : daoLst.dscrDao.getFieldsCount());
   }
   /** returns true if field is found in map or dao. Basically is responseFlds.containsKey(nm)||daoLst.hasField(nm)*/
   public boolean hasField(String name) {
      return(responseFlds!=null&&responseFlds.containsKey(name)) || ((daoLst==null||name==null||!name.toLowerCase().equals(name))?false:daoLst.dscrDao.hasField(name));
   }
   /** adds field to responseFlds HashMap. If field with such name exists in daoList, throws IndexOutOfBoundsException - we don't want duplicates*/
   public void addField(String name, Object value) {
      if (daoLst != null && daoLst.dscrDao != null && daoLst.dscrDao.hasField(name)) {
         log.warning("addField(): Field/column " + name + "cannot be added as it is already present in Dao");
         throw new IndexOutOfBoundsException("Field/column " + name + " cannot be added as it is already present in Dao");
      }
      if (responseFlds == null) {
         responseFlds = new HashMap();
      }
      responseFlds.put(name, value);
   }
   /** adds to responseFlds new collection field that builds complex html control using existingColName and htmlTemplate.
   New field will be named newColName collection will be of ArrayList type of the same size as DaoList rows collection.
   htmlTemplate may include special placeholders that will be replaced with dynamic values:
   <br/> - ZcolvalZ will be replaced with the escapeChars() of corresponding value in existing column
   <br/> - ZrownumZ will be replaced with the row number
   <br/>If existingColName is null or empty, only ZrownumZ will be replaced with row number which will be drawn from Dao
   <br/>Example: addHtmlColumn("roleLn", "role", "&lt;a href='viewR.do?submSeeR=ZcolvalZ&selItem=ZrownumZ'&gt;See role&lt;/a&gt;")
   will build and add to responseFlds key "roleLn" with value of ArrayList of strings that will dispaly as html links */
   public void addHtmlColumn(String newColName, String existingColName, String htmlTemplate) {
      ArrayList existVals = null;
      ArrayList newVals = new ArrayList();
      if (existingColName == null || existingColName == "") {
         existVals = new ArrayList();
         for (int i = 0; daoLst != null && i < daoLst.getRowCount(); i++) {
            newVals.add(htmlTemplate.replaceAll("ZrownumZ", "" + i));
         }
      } else {
         existVals = getFieldValueArr(existingColName);
         for (int i = 0; i < existVals.size(); i++) {
            newVals.add(htmlTemplate.replaceAll("ZcolvalZ", escapeChars((String) existVals.get(i))).replaceAll("ZrownumZ", "" + i));
         }
      }
      addField(newColName, newVals);
      if (hlmlColsLst == null) {
         hlmlColsLst = new ArrayList();
      }
      hlmlColsLst.add(newColName);
   }
   /** returns true if field found in map or dao (Dao+responseFlds, aka 'inner storage') has different value then what
   came in request (requestFlds). If there is no such field in inner storage, throws IndexOutOfBoundsException */
   public boolean hasFieldChanged(String name) throws IndexOutOfBoundsException {
      if (name == null || !hasField(name)) {
         throw new IndexOutOfBoundsException("There is no field \'" + name + "\' in class " + getClass().getName());
      }
      if (!requestFlds.containsKey(name)) {
         return false;
      }
      String oldV = getFieldValue(name);
      String newV = (String) requestFlds.get(name);
      return (oldV == null && newV == null) ? false : ((oldV == null && newV != null) ? true : oldV.equals(newV));
   }
   /** populates responseFlds and/or daoLst field value. If there is no field with such name (case sensitive),
   throws IndexOutOfBoundsException. Note, field names in Dao are always lowercase. */
   public void setFieldValue(String name, String val) throws IndexOutOfBoundsException {
      if (name == null || !hasField(name)) {
         throw new IndexOutOfBoundsException("There is no field \'" + name + "\' in class " + getClass().getName());
      }
      if (responseFlds != null && responseFlds.containsKey(name)) {
         responseFlds.put(name, val);
      }
      if (daoLst != null && name.toLowerCase().equals(name) && daoLst.dscrDao.hasField(name)) {
         daoLst.dscrDao.setFieldValue(name, val);
      }
   }
   /** returns value given the key for responseFlds HashMap (first priority) or Dao field name, case sensitive. Note, field
   names in Dao are always lowercase. If there is no field with such name, throws IndexOutOfBoundsException */
   public String getFieldValue(String name) throws IndexOutOfBoundsException {
      if (responseFlds != null && responseFlds.containsKey(name)) {
         return (String) responseFlds.get(name);
      } else if (daoLst != null && name != null && name.toLowerCase().equals(name) && daoLst.dscrDao.hasField(name)) {
         return daoLst.dscrDao.getFieldValue(name);
      } else {
         throw new IndexOutOfBoundsException("There is no field \'" + name + "\' in class " + getClass().getName());
      }
   }
   /** if instanceof ArrayList found in map entry with this name, returns it. Otherwise tries to build it from Dao values.
   If there is no field with such name in Dao or ArrayList value in HashMap, throws IndexOutOfBoundsException */
   public ArrayList getFieldValueArr(String name) throws IndexOutOfBoundsException {
      if (responseFlds != null && responseFlds.containsKey(name) && responseFlds.get(name) instanceof ArrayList) {
         return (ArrayList) responseFlds.get(name); //.get(rownum)
      }
      if (responseFlds != null && responseFlds.containsKey(name) && responseFlds.get(name) instanceof String[]) {
         ArrayList list = new ArrayList();
         String[] strArr = (String[]) responseFlds.get(name);
         for (int i = 0; i < strArr.length; i++) {
            list.add(strArr[i]);
         }
         return list;
      } else if (daoLst != null && name != null && name.toLowerCase().equals(name) && daoLst.dscrDao.hasField(name)) {
         ArrayList list = new ArrayList();
         for (int i = 0; i < daoLst.getRowCount(); i++) {
            list.add(daoLst.getRow(i).getFieldValue(name));
         }
         return list;
      } else {
         throw new IndexOutOfBoundsException("There is no ArrayList field \'" + name + "\' in daoLst or class " + getClass().getName());
      }
   }

   //----- working with request parameters storage ------
   /** returns request parameters and unlike request.getParameterValue() call does that even for multipart requests */
   public Object getRequestParam(String name) {
      return requestFlds.get(name);
   }
   /** populates Dao fields by values of same named requestFlds fields, useful for subsequent Dao queries and saves.
   For Dao fields of BINARY and BLOB types binValue is populated and is drawn from request field named daoFld.name+"_Bytes",
   and Field.value, Field.otherValue are populated with info on mime type, file name, encoding - pipe delimited, like
   "text/plain|t.txt|ISO-8859-1". For other Dao field types, types that use 'value' field for storing actual value, this
   information go only to Field's otherValue and for anything other then CLOB it is "text/plain||"+requestEncoding.
   CLOB fields for which bean field named daoFld.name+"_Bytes" exists, are getting value from there.
   When user leaves input field empty function assigns null to Dao field value, when submits empty file, assigns null to
   binValue for binary field types or 'value' in case of CLOB.
   Dao fields that do not have corresponding request parameters (bean fields) are not affected.
   @return same DB.Dao object as passed in with values properly populated */
   public DB.Dao request2dao(DB.Dao dao) {
      for (int i = 0; dao != null && i < dao.getFieldsCount(); i++) {
         DB.Field fld = dao.getField(i);
         if (requestFlds.containsKey(fld.name)) {
            if ((fld.type == DB.BINARY || fld.type == DB.BLOB || fld.type == DB.CLOB) && requestFlds.get(fld.name + "_Bytes") != null) {
               byte[] val = (byte[]) requestFlds.get(fld.name+"_Bytes");
               String suplInfo = (String) requestFlds.get(fld.name);
               if (fld.type == DB.CLOB) {
                  String valC = null;
                  String encoding = requestEncoding;
                  String[] toks = (suplInfo != null ? suplInfo : "").split("|");
                  if (toks.length > 2 && toks[2].length() > 0) {
                     encoding = toks[2];
                  }
                  try {
                     valC = new String(val, encoding);
                  } catch (java.io.UnsupportedEncodingException e) {
                     valC = new String(val);
                  }
                  fld.setValue(valC);
                  fld.otherValue = suplInfo;
               } else {
                  //binaries
                  fld.setBinValue(val.length == 0 ? null : val); //when user submits empty file, assume it is null
                  fld.setValue((String) requestFlds.get(fld.name)); //text/plain|t.txt|ISO-8859-1
                  fld.otherValue = suplInfo;
               }
            } else {
               String val = (String) requestFlds.get(fld.name);
               fld.setValue(val.length() == 0 ? null : val); //when user leaves input field empty, assume it is null
               fld.otherValue = "text/plain||" + requestEncoding;
            }
         }
      }
      return dao;
   }
   //----------- WebBean html generation functionality ---------------------------
   //--- SimpleTagSupport implementation ----
   /**STS: tType can be one of HTML input tag types like 'text', 'radio', .., or plain text fields like 'message', 'error',
   or special tType specific to izFrame framework like 'pageInfo' (generates hidden fields of page tName and sequence) */
   protected String tType;
   /**STS: WebBean field tName user wants to display */
   protected String tName;
   /**STS: tValue, if it is other then named field holds. Useful for initialization and for checkbox, radio types where it
   adds 'checked' input attribute if tValue is same as stored in bean field */
   protected String tValue;
   /**STS:  storage of dynamic Tag attributes: class, style, extras, rownum, key, etc. */
   protected HashMap tagAttrMap;
   /**STS: sets bgcolor for even rows displayed by tags of 'tablebody' tType, default light blue 'e4ffff' */
   protected String tEvenRowColor = "e4ffff";

   //---getters and setters for members our SimpleTagSupport implementation will use ---
   /**STS: static attrib setter, called by JSP framework */
   public void setType(String a) {
      tType = a;
   }
   /**STS: static attrib setter, called by JSP framework */
   public void setName(String a) {
      tName = a;
   }
   /**STS: static attrib setter, called by JSP framework */
   public void setValue(String a) {
      tValue = a;
   }
   /**STS: dynamic attrib setter, called by JSP framework. Note, 'key' attrib goes to 'tName' member for compactness */
   public void setDynamicAttribute(String uri, String localName, Object value) {
      if (tagAttrMap == null && !("key".equals(localName) || "evenRowColor".equals(localName))) {
         tagAttrMap = new HashMap();
      }
      if ("key".equals(localName)) {
         tName = (String) value;
      } else if ("evenRowColor".equals(localName)) {
         tEvenRowColor = "null".equals((String) value) ? null : (String) value;
      } else {
         tagAttrMap.put(localName, value); //class, style, extras, rownum, key
      } //class, style, extras, rownum, key
      //class, style, extras, rownum, key
   }

   //--- doTag: our core method ---
   /**STS: Displays html appropriate to attributes set in the tag on the jsp page - input, message, field/row/table,
   error, select, pageInfo, etc.
   <ul> <li> HTML 'input' element is generated if type is the same as supported by input in HTML 4.01/XHTML 1.0:
   button,checkbox,file,hidden,image,password,radio,reset,submit,text.
   <br> Input supports following attributes: type, name, value, class, style, extras. Note, that normally 'value'
   is optional as it will be drawn from the bean, so, supply it only if you want to overwrite (like first page init)
   it, or for inputs like radio and checkbox to set 'checked' attribute properly. See toHtmlInput() for more details.
   Type 'hidden' also supports 'write' attribute and if its value is 'true' will write actual value besides hidden field.
   </li><li> if type is set to one of 'field, row, tablebody' displays value corresponding to 'name' and stored either
   in dao (first priority) of responseFlds map. 'field' type is using 'name' and 'value' attributes, 'row' and
   'tablebody' accept all attributes (class and extras will be applied to tr or table elements accordingly. When type is
   one of 'field, row' value attribute supposed to contain Dao row number or number of element in ArrayList stored
   in responseFlds map. Actually for these types 'value' attribute is optional - if omitted, 0 Dao row or String stored
   in responseFlds map is assumed. For 'tablebody' type 'value' attribute should contain coma delimited list of headers
   defined in message resource file. For 'row, tablebody' types 'name' attribute accepts coma delimited list of all
   names to display and 'style' attribute accepts coma delimited list of all col numbers and their table cell attribs in
   format: "col_num1:tdattr1,col_num2:tdattr2,...", like "1:align=right,2:align=left". Names containing string ZrownumZ
   are special names that will be outputed back as is except that ZrownumZ will be replaced with current row number -
   handy to generate columns of links, checkboxes, etc.
   <br> Also, tag 'tablebody' accepts optional attribute 'rowsPerPage' to define set of rows to display and stores
   this info in page attribute attr_minRow for further row sets. It also reads request attribute with that name set by
   'rangeLinks' tag, so when you use it in coordination with 'rangeLinks' tag you can bypass servlet in navigation
   from one results page to another one.
   </li><li> tag 'rangeLinks' creates set of links to access/display set of rows that is less then full set Dao contains,
   i.e. allows page by page navigation between partial result sets. Tag uses attributes 'action' and 'extraParam' and
   implicitly page attribute attr_minRow set by 'tablebody'. Setting these attributes in a special way would allow
   pages navigation bypassing servlet. For such scenario, set tag's 'action' attribute to page itself, like
   action="izFrameTutor/adminUser.jsp". As in all other cases when you access JSP bypassing servlet, add attribute
   innerPageNm={pg name, like adminUser.jsp} to pageInfo tag. By default link value is in the format of
   {minRow}-{maxRow}, if you want format of {pageNum}, add attribute byPages="1".
   </li><li>For Message group of tags, type can be 'message' or 'errors', if omitted, 'message' is assumed. For messages
   to be displayed with proper Locale, it must be stored in session attrubute 'attr_locale', see Web.setLocale().
   <br> Type 'message' is using 'key' attribute - as a key to find string from APP_MSG_RESOURCE and, optionally,
   'param' or 'beanParam' attribute. Use one of last two if your message accepts parameter (has {0}). Value of
   'param' will replace {0} in the message, while value of 'beanParam' is name WebBean field which value replaces {0}.
   <br> Type 'errors' is not using any attributes as it expects to find ArrayList of error messages in request
   attribute attr_errList. For changing format or 'error' list from the default one, define in application resource
   (APP_MSG_RESOURCE) the following entries: errors.header, errors.prefix, errors.suffix, errors.footer.
   Each error message is displayed between errors.prefix and errors.suffix.
   </li><li> tag of type='select' creates html 'select' based on array of values in named field, appends 'Sel' to outName.
   </li><li> tag of type='pageInfo' adds to html form couple of hidden fields: inner page name and page sequence ID.
   Has one optional attribute - innerPageNm, to use when you plan show JSP bypassing servlet.
   </li><li> any tag if it has 'rendered' attribute set to 'false' would not produce any output (doTag() would exit right away).
   </li></ul>
   Note:<br>1. attributes 'class' and 'style' will be single quoted in the output, 'extras' - prefixed with space
   <br>2. in izFrame.tld tag names message and bean refer to this class, hense these tag names can be used interchangibly
   <br>3. Remember, DB.Dao class works in double capacity - as data holder and as a list of other Dao classes. Rownum
   attribute is what indicates to framework that you want to use the second form.
   <br> Samples: <pre>
   &lt;%@ taglib prefix="f" uri="/WEB-INF/lib/izFrame.jar" %&gt; - this will indicate to JSP to use izFrame web tag library
   &lt;f:message key="Hdr_Addr"/&gt; - this will display string stored in message resources file with key Hdr_Addr
   &lt;f:bean type="field" name="userAndRole"/&gt; - will display value stored in WebBean field userAndRole
   &lt;f:bean type="pageInfo"/&gt; - will create couple (page name and sequence) of hidden fields on the page. */
   public void doTag() throws javax.servlet.jsp.JspException, IOException {
      //log.debug("WebBean.doTag() called: tType="+tType+", tName="+tName+", tValue="+tValue+", extras="+extras);
      if(tagAttrMap!=null && "false".equals(tagAttrMap.get("rendered")))
         return;
      try {
         javax.servlet.jsp.JspContext pageContext = getJspContext();
         WebBean beenScoped = (WebBean) pageContext.findAttribute(Web.attr_dataObject); //ret null if not found
         if (beenScoped == null) {
            beenScoped = this;
         }
         javax.servlet.jsp.JspWriter out = pageContext.getOut();
         if (tType == null) {
            tType = "message"; //set default
            //log.debug("WebBean.doTag(): pageContext="+pageContext+" beenScoped="+beenScoped);
         }
         String classID = tagAttrMap == null ? null : (String) tagAttrMap.get("class");
         String style = tagAttrMap == null ? null : (String) tagAttrMap.get("style");
         String extras = tagAttrMap == null ? null : (String) tagAttrMap.get("extras");
         String rownumS = tagAttrMap == null ? null : (String) tagAttrMap.get("rownum");
         int rownum = rownumS == null ? -1 : Integer.parseInt(rownumS); //expected to be number of row to display
         StringBuffer buf = new StringBuffer();
         //button,checkbox,file,hidden,image,password,radio,reset,submit,text
         if ("button,checkbox,file,hidden,image,password,radio,reset,submit,text".indexOf(tType) >= 0) {
            if (tValue != null && "reset,submit".indexOf(tType) >= 0) {
               //get tValue from resource
               Locale currLoc = (Locale) pageContext.findAttribute(Web.attr_locale);
               tValue = Web.getResMsg(tValue, currLoc);
            }
            buf.append(beenScoped.toHtmlInput(tName, tType, tValue, classID, style, extras));
            if ("hidden".equals(tType) && tagAttrMap != null && "true".equals((String) tagAttrMap.get("write"))) {
               buf.append(beenScoped.toHtml(tName, rownum)); //write actual value besides hidden field
            }
         } else if ("teaxtarea".equals(tType)) {
            buf.append("<textarea name=\'" + tName + "\'");
            if (classID != null) {
               buf.append(" class=\'").append(classID).append("\'");
            }
            if (style != null) {
               buf.append(" style=\'").append(style).append("\'");
            }
            if (extras != null) {
               buf.append(" ").append(extras);
            }
            buf.append(">" + beenScoped.toHtml(tName, rownum) + "</textarea>");
         } else if ("link".equals(tType)) {
            buf.append("<a href=\'" + (tagAttrMap == null ? "" : (String) tagAttrMap.get("url")) + "\'");
            if (classID != null) {
               buf.append(" class=\'").append(classID).append("\'");
            }
            if (style != null) {
               buf.append(" style=\'").append(style).append("\'");
            }
            if (extras != null) {
               buf.append(" ").append(extras);
            }
            buf.append(">" + beenScoped.toHtml(tName, rownum) + "</a>");
         } else if ("message".equals(tType)) {
            Locale currLoc = (Locale) pageContext.findAttribute(Web.attr_locale);
            String param = tagAttrMap == null ? null : (String) tagAttrMap.get("param");
            String beanP = tagAttrMap == null ? null : (String) tagAttrMap.get("beanParam");
            if (param != null) {
               buf.append(Web.getResMsg(tName, currLoc, param)); //tName=(String)tagAttrMap.get("key")
            } else if (beanP != null) {
               buf.append(Web.getResMsg(tName, currLoc, toHtml(beanP, -1)));
            } else {
               buf.append(Web.getResMsg(tName, currLoc));
            }
         } else if ("errors".equals(tType)) {
            ArrayList errors = (ArrayList) pageContext.findAttribute(Web.attr_errList);
            if (errors != null && errors.size() > 0) {
               Locale currLoc = (Locale) pageContext.findAttribute(Web.attr_locale);
               buf.append(Web.getResMsg("errors.header", currLoc)).append("\n"); //.append("\n") is just for nicety
               for (int i = 0; i < errors.size(); i++) {
                  buf.append(Web.getResMsg("errors.prefix", currLoc) + errors.get(i) + Web.getResMsg("errors.suffix", currLoc));
               }
               buf.append(Web.getResMsg("errors.footer", currLoc)).append("\n");
            }
         } else if ("select".equals(tType)) {
            String selected = tagAttrMap != null ? (String) tagAttrMap.get("selected") : null;
            if (selected != null) {
               selected = Web.getResMsg(selected, (Locale) pageContext.findAttribute(Web.attr_locale));
            }
            buf.append("<select name=\'").append(tName).append("Sel\'" + (extras != null ? (" " + extras) : "")).append(">\n");
            ArrayList options = beenScoped.getFieldValueArr(tName);
            for (int i = 0; options != null && i < options.size(); i++) {
               String selFlag = (selected != null && selected.equals((String) options.get(i))) ? " selected=\'selected\'" : "";
               buf.append("<option value=" + i + selFlag + ">" + (String) options.get(i) + "</option>\n");
            }
            buf.append("</select>\n");
         } else if ("field".equals(tType)) {
            buf.append(beenScoped.toHtml(tName, rownum));
         } else if ("row,headrow".indexOf(tType) >= 0) {
            String[] cols = tName == null ? null : tName.split(","); //expect tName to contain list of columns to show, coma delim
            String[] tdstyles = style == null ? null : style.split(","); //style to contain list of col nums with their tdstyles
            String[] classes = classID == null ? null : classID.split(","); //class to contain list of col nums with their tdclasses
            String[] extrasS = extras == null ? null : extras.split(","); //extras to contain list of col nums with their tdextras
            for (int i = 0; cols != null && i < cols.length; i++) {
               String tdstyle = extractAttrVals("style", i, tdstyles) + extractAttrVals("class", i, classes) + extractAttrVals(null, i, extrasS);
               if ("row".equals(tType)) {
                  buf.append("<td" + tdstyle + ">" + beenScoped.toHtml(cols[i], rownum) + "</td>");
               } else {
                  Locale currLoc = (Locale) pageContext.findAttribute(Web.attr_locale);
                  buf.append("<th").append(tdstyle).append(">").append(Web.getResMsg(cols[i], currLoc)).append("</th>");
               }
            }
         } else if ("tablebody".equals(tType)) {
            String minRowS = (String) pageContext.findAttribute("attr_minRow");
            if (minRowS == null) {
               minRowS = (String) ((javax.servlet.jsp.PageContext)pageContext).getRequest().getParameter("attr_minRow");
            }
            String incrS = tagAttrMap == null ? null : (String) tagAttrMap.get("rowsPerPage");
            int minRow = minRowS == null ? 0 : Integer.parseInt(minRowS);
            int maxDao = beenScoped.daoLst != null ? beenScoped.daoLst.getRowCount() : 0;
            int maxRow = incrS == null ? maxDao : minRow + Integer.parseInt(incrS);
            if (incrS != null) {
               pageContext.setAttribute("attr_minRow", "" + minRow); //to be used by 'rangeLinks'
               pageContext.setAttribute("attr_rowsPerPage", incrS); //to be used by 'rangeLinks'
            }
            String[] cols = tName == null ? null : tName.split(","); //expect tName to contain list of columns to show, coma delim
            String[] tdstyles = style == null ? null : style.split(","); //style to contain list of col nums with their tdstyles
            for (int rowNum = minRow; rowNum < maxRow && rowNum < maxDao; rowNum++) {
               String bgcol = (rowNum % 2 == 1 && tEvenRowColor != null) ? " bgcolor=\'" + tEvenRowColor + "\'" : "";
               buf.append("<tr").append(bgcol);
               if (classID != null) {
                  buf.append(" class=\'").append(classID).append("\'");
               }
               if (extras != null) {
                  buf.append(" ").append(extras);
               }
               buf.append(">");
               for (int i = 0; cols != null && i < cols.length; i++) {
                  String tdstyle = extractAttrVals(null, i, tdstyles);
                  buf.append("<td" + tdstyle + ">" + beenScoped.toHtml(cols[i], rowNum) + "</td>");
               }
               buf.append("</tr>\n");
            }
         } else if ("rangeLinks".equals(tType)) {
            String minRowS = (String) pageContext.findAttribute("attr_minRow");
            String incrS = (String) pageContext.findAttribute("attr_rowsPerPage");
            boolean byPages = tagAttrMap == null ? false : (tagAttrMap.get("byPages") != null); //if attrib byPages is set
            int minRow = minRowS == null ? 0 : Integer.parseInt(minRowS);
            int maxDao = beenScoped.daoLst != null ? beenScoped.daoLst.getRowCount() : 0;
            int maxRow = incrS == null ? maxDao : minRow + Integer.parseInt(incrS);
            if (minRow > 0 || maxRow < maxDao) {
               //would it not, no sense for range links
               String action = tagAttrMap == null ? null : (String) tagAttrMap.get("action");
               String extraParam = (tagAttrMap == null || tagAttrMap.get("extraParam") == null) ? "" : ("&" + (String) tagAttrMap.get("extraParam"));
               int incr = maxRow - minRow;
               Locale currLoc = (Locale) pageContext.findAttribute(Web.attr_locale);
               if (minRow > 0) {
                  //put link to "Previous"
                  String prevMin = "?attr_minRow=" + (minRow - incr > 0 ? minRow - incr : 0);
                  buf.append("<a href=\'/" + action + prevMin + extraParam + "\'>" + Web.getResMsg("Previous", currLoc) + "</a>&nbsp;");
               }
               for (int i = 0, j = 1; i < maxDao; i = i + incr, j++) {
                  //put links to all page ranges and <b>currRange</b>
                  String curRange = byPages ? "" + j : ("" + (i + 1) + "-" + ((i + incr) < maxDao ? (i + incr) : maxDao)); //pages count from 1
                  String curMin = "?attr_minRow=" + i;
                  if (i == minRow) {
                     buf.append("&nbsp;<b> " + curRange + " </b>&nbsp;");
                  } else {
                     buf.append("&nbsp;<a href=\'/" + action + curMin + extraParam + "\'>" + curRange + "</a>&nbsp;");
                  }
               }
               if (maxRow < maxDao) {
                  //put link to "Next"
                  String nextMin = "?attr_minRow=" + (maxRow);
                  buf.append("&nbsp;<a href=\'/" + action + nextMin + extraParam + "\'>" + Web.getResMsg("Next", currLoc) + "</a>");
               }
            }
         } else if ("pageInfo".equals(tType)) {
            //page name
            String pgAttr = (String) (tagAttrMap!=null ? tagAttrMap.get("innerPageNm") : null);
            String pg = pgAttr != null ? pgAttr : (String) pageContext.findAttribute(Web.attr_InnerPageNm);
            if (pg != null) {
               buf.append("<input type=hidden name=\'" + Web.attr_InnerPageNm + "\' value=\'" + pg + "\'>\n");
            }
            if (Web.checkSequenceID) {
               // add page sequence ID
               HttpSession session = ((javax.servlet.jsp.PageContext) pageContext).getSession(); //!danger
               if (session != null) {
                  long dt = System.currentTimeMillis();
                  String seqID = session.getId() + dt;
                  buf.append("<input type=hidden name=\'" + Web.attr_pageSeqID + "\' value=\'" + seqID + "\'>");
                  session.setAttribute(Web.attr_pageSeqID, seqID); //store in session
               }
            }
         }
         out.print(buf.toString());
      } catch (Exception e) {
         log.error(e, "WebBean.doTag() has problem, about to throw javax.servlet.jsp.JspTagException");
         throw new javax.servlet.jsp.JspTagException("WebBean: " + e.getMessage());
      }
   }

   //---- Scriptlets, HTML form controls presentation ----
   /**generates String with value of field of given name and row number either from Dao of responseFlds */
   public String toHtml(String name, int rowNum) {
      //log.debug("toHtml(): name="+name+", rowNum="+rowNum+", hasField()="+hasField(name));
      if (name != null && name.indexOf("ZrownumZ") >= 0) {
         return name.replaceAll("ZrownumZ", Integer.toString(rowNum));
      }
      if (daoLst != null && daoLst.dscrDao.hasField(name) && rowNum < 0) {
         return escapeChars(daoLst.dscrDao.getFieldValue(name));
      }
      if (daoLst != null && daoLst.dscrDao.hasField(name) && daoLst.getRowCount() > rowNum) {
         return escapeChars(daoLst.getRow(rowNum).getFieldValue(name));
      } else {
         //cols not in daoLst can be arrayLists in responseFlds
         if(responseFlds == null){
            log.warning("responseFlds array list is null, i.e. no been in session, possible navigation logic mistake");
            return "";
         }
         Object valObj = responseFlds.get(name);
         if (valObj != null && valObj instanceof String) {
            return escapeChars((String) responseFlds.get(name));
         } else if (valObj != null && valObj instanceof ArrayList && rowNum >= 0) {
            ArrayList valList = (ArrayList) valObj;
            String val = valList.size() >= rowNum ? (String) valList.get(rowNum) : "";
            if (hlmlColsLst != null && hlmlColsLst.contains(name)) {
               return val;
            }
            return escapeChars(val);
         }
      }
      return "";
   }

   /**generates String "&lt;input type='text' name='"+name+"' value='"+getFieldValue(name)+"' /&gt;" */
   public String toHtmlText(String name) {
      return "<input type=\'text\' name=\'" + name + "\' value=\'" + escapeChars(getFieldValue(name)) + "\' />";
   }

   /**generates String "&lt;input type='[type]' name='[name]' value='[getFieldValue(name)]' class='[classID] [extras] /&gt;".
   If type is null, 'text' is assumed. Non null hardValue indicates that it should be used instead of getFieldValue(name).
   It is useful for initialization and for checkbox, radio types where it adds 'checked' input attribute if value is the
   same as stored in bean field. Note, if hardValue==null and !hasField(name), empty string is returned.
   <br>All params except 'name' can be null, and even name can be null if type is reset,submit, but in that case type
   cannot be null. Throws Exception if type used is other then in list:
   'button,checkbox,file,hidden,image,password,radio,reset,submit,text'. */
   public String toHtmlInput(String name, String type, String hardValue, String classID, String style, String extras) throws Exception {
      if (type == null) {
         type = "text"; //set default
      }
      String value = (hardValue != null) ? hardValue : ((responseFlds == null || !hasField(name)) ? "" : getFieldValue(name));
      StringBuffer buf = new StringBuffer();
      //button,checkbox,file,hidden,image,password,radio,reset,submit,text
      if ("button,checkbox,file,hidden,image,password,radio,reset,submit,text".indexOf(type) >= 0) {
         buf.append("<input type=\'").append(type).append("\'");
         if (name == null && "reset,submit".indexOf(type) < 0) {
            throw new Exception("\'name\' attribute is mandatory for <input> HTML element");
         } else if (name != null) {
            buf.append(" name=\'" + name + "\' value=\'" + escapeChars(value) + "\'"); //escapeChar() also converts null to ""
         } //escapeChar() also converts null to ""
         if (classID != null) {
            buf.append(" class=\'").append(classID).append("\'");
         }
         if (style != null) {
            buf.append(" style=\'").append(style).append("\'");
         }
         if (extras != null) {
            buf.append(" ").append(extras);
         }
         if ("checkbox,radio".indexOf(type) >= 0) {
            if (value == null) {
               throw new Exception("\'value\' attribute is required with types \'checkbox\' and radio\'");
            }
            if (extras.indexOf("checked=") < 0 && hardValue != null && hardValue.equals(value)) {
               buf.append(" checked=\'checked\'");
            }
         }
         if ("password".equals(type) && (extras == null || extras.indexOf("size") < 0)) {
            buf.append(" size=\'20\'");
         }
         buf.append(" />");
      } else {
         throw new Exception("only \'button,checkbox,file,hidden,image,password,radio,reset,submit,text\' types are valid");
      }
      return buf.toString();
   }

   /** Scriptlet support - generates on the JSP page hidden inputs with JSP page name and SequenceID. */
   public String generatePgNameFields(HttpServletRequest req) {
      String res = "";
      String pg = (String) req.getAttribute(Web.attr_InnerPageNm);
      if (pg != null) {
         res = "<input type=hidden name=\'" + Web.attr_InnerPageNm + "\' value=\'" + pg + "\'>\n";
      }
      if (!Web.checkSequenceID) {
         return res;
      }
      HttpSession session = req.getSession(false);
      if (session != null) {
         //add sequence ID
         long dt = System.currentTimeMillis();
         String seqID = session.getId() + dt;
         res += "<input type=hidden name=\'" + Web.attr_pageSeqID + "\' value=\'" + seqID + "\'>";
         session.setAttribute(Web.attr_pageSeqID, seqID); //store in session
      }
      return res;
   }

   //---static utility methods ---
   /** extracts from colVals array string with value of attribute specific to column name. If attribName==null, returns
   found value, if !=null - attribName=value, otherwise returns empty string. Expects colsVals format as colNum:value */
   public static String extractAttrVals(String attribName, int colNumber, String[] colVals) {
      String colNmExt = Integer.toString(colNumber) + ":";
      for (int j = 0; colVals != null && j < colVals.length; j++) {
         if (colVals[j] != null && colVals[j].startsWith(colNmExt) && colVals[j].length() > colNmExt.length()) {
            return attribName == null ? " " + colVals[j].split(":")[1] : " " + attribName + "=\'" + colVals[j].split(":")[1] + "\'";
         }
      }
      return "";
   }

   /**<xmp> replaces special HTML/XML chars with entities: < &lt; > &gt; & &amp; ' &#039; " &#034; null ""</xmp>*/
   public static String escapeChars(String inText) {
      return inText == null ? "" : inText.replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll("&", "&amp;").replaceAll("\'", "&#039;").replaceAll("\"", "&#034;");
   }

   /** converts string representing some Date from one format to another  */
   public static String formatDate(String inDate, String inFmt, String outFmt) {
      if (inDate == null) {
         return null;
      }
      java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat(inFmt != null ? inFmt : DB._dateFormatJava);
      Date dt = fmt.parse(inDate, new java.text.ParsePosition(0));
      fmt.applyPattern(outFmt);
      return fmt.format(dt);
   }
}
