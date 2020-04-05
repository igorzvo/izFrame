package izFrame;
import javax.servlet.*;       //HttpServlet, ServletInputStream
import javax.servlet.http.*;  //HttpServletReques, HttpServletResponse
import java.util.*;           //Enumeration, Vector, HashMap
import java.io.*;             //IOException;File;FileInputStream
import java.security.cert.X509Certificate;
import java.security.Principal;

/** Class Web provides coordination between web framework components (Servlet - WebBean - UseCase - JSP) and does common
services like security, logging, dispatch/display. Related izFrame classes are WebBean and UseCase.
UseCase is Web inner class that defines business logic wih prime framework callbacks being execute(), isValidRoleForUseCase().
WebBean class provides data storage and server-side user interface component framework for web applications (similar to JSF).
In more details, class Web:
<ul> <li> extends HttpServlet where overwrites service() that:
   <ul> <li> logs HTTP request content
   </li><li> calls isValidSequenceID() which allows prevention of pages bookmarking and using browser "Back" button
   </li><li> instantiates UseCase class object and calls its passSecurity(), doBeanFromRequest(), execute() functions.
   </li></ul>
</li><li> implements set of 'show' functions: showWebPage(), showHTMLString(), showLoginPage(), showSystemErrorPage(), etc.
   Before calling dispatch() these functions store in request data for JSP: business object, page name. After
   dispatch() page name gets stored in session as 'refering' page name - page we expect to get request from.
</li><li> provides few useful utility functions like logRequest(), getDataBean(), getLocale(), getResMsg(), etc.
</li><li> declares set of configurable (from property file read in init()) processing parameters: APP_TITLE, logRequest,
    checkSequenceID and special page names: APP_FRAME_PAGE, APP_ERROR_PAGE, APP_WELCOME_PAGE, APP_LOGIN_PAGE, etc.
</li><li> defines inner static UseCase class concrete implementation of which will be loaded at run time
   and asked to perform the following functions:
   <ul> <li> passSecurity() function that verifies if user is allowed to access use case
   </li><li> doBeanFromRequest() that creates FormBean (or its child) instance and populates it by request data
   </li><li> execute() function that defines business logic concrete UseCase should implement. Based on business
      outcome function either calls one of 'show' functions or pass request to other use case with gotoUseCase().
   </li></ul>
</li></ul>
<b>Major class functions</b> you will probably use more often then the others are:
<ul> <li> showWebPage(jspName,..) - Stores FormBean and page name in request and displays the specified JSP
</li><li> gotoUseCase(useCaseName,..) - instantiates requested UseCase class instance and calls its methods
</li><li> get/setLocale() - returns locale stored in the HttpSession as an attribute attr_locale, or stores it there
</li><li> getResMsg(msgKey,Locale,optParam) - returns from resources value corresponding to msgKey and Locale
</li></ul>
@author Igor Zvorygin, AppIntegration Ltd., LGPL as per the Free Software Foundation.
<br/>version $Revision: 1.1 $ */
public class Web extends HttpServlet {
   //---- Attribute name constants ----
   //-- session attribute names
   /** session attribute name that will store name of the page submitted as response, usually consider as 'Expected From' page */
   public static final String attr_referPageNm     = "attr_referPageNm";
   /** session attribute name that will store current (say, for this use case or group of use cases) framing page */
   public static final String attr_framingPage  = "attr_framingPage";
   /** session attribute name that will store current locale */
   public static final String attr_locale          = "attr_locale";
   /** session attribute and request parameter name that stores page's sequence ID (for monitoring user follows prescribed sequence of pages)*/
   public static final String attr_pageSeqID       = "attr_pageSeqID";
   //-- request attribute names
   /** request parameter name that will store name of the page request came from, usually consider as 'Actual From' page */
   public static final String attr_InnerPageNm     = "attr_InnerPageNm";
   /** request parameter name that will store data object used in web page */
   public static final String attr_dataObject      = "attr_dataObject";
   /** request parameter name that will store String[] of errors that should be displayed on page */
   public static final String attr_errList         = "attr_errList";
   /** request parameter name that will store exception thrown by page */
   public static final String attr_exception       = "attr_exception";

   //--- global application config data to set in init(): name, path, important page names, etc. ---
   /** title of web application, will be read in init() from file defined in web.xml as 'izFrameProps' param, default "webApp"*/
   public static String APP_TITLE         = "webApp";
   /** Configurable in properties file internal http path of this web application, default "/"+APP_TITLE+"/"*/
   public static String WEB_PATH          = "/"+APP_TITLE+"/";
   /** main framing web page of this web application (can be overloaded for specific use case), will be read in init(), default null */
   public static String APP_FRAME_PAGE    = null; //"izFrame.jsp";
   /** JSP page to display in case of error, will be read in init(), default null */
   public static String APP_ERROR_PAGE    = null; //"appError.jsp";
   /** initial (welcome) JSP page, will be read in init(), can be same as other pages like Login one, default null */
   public static String APP_WELCOME_PAGE  = null; //"welcome.jsp";
   /** login JSP page name, will be read in init(), can be same as other pages like Welcome one, default null */
   public static String APP_LOGIN_PAGE    = null; //"login.jsp";
   /** page to display when user is not following prescribed sequence of pages, will be read in init() */
   public static String SEQ_ID_ERROR_PAGE = null; //"backBtnError.jsp";
   /** relative path of property file listing pages where we want user to follow certain path, will be read in init() */
   public static String SEQ_PAGES_FILE    = null; //"/lib/seqPagesList.prop";
   /** base of name of file that would contain application messages, default 'resources/MessageResources' */
   public static String APP_MSG_RESOURCE  = "resources/MessageResources";

   //---- global application config data, non user or user role specific
   /** if true, request dispatcher will direct calls to page stored in attr_framingPage that is expected to use include of actual page*/
   public static boolean useFramingPage = false;
   /** if true, server console window (and Log on 'info' level) will show HTTP request Parameters, default true */
   public static boolean logRequest = true;
   /** if true, server console window and log.inform will also show HTTP request headers and Cookies, default false */
   public static boolean logRequestHeaders = false;
   /** Seq ID is a security feature that prevents bookmarking of pages and prevents user from using browsers "Back" button.
   If true, we will check if page request came from is in the list of 'sequenced' pages where user is supposed to proceed
   from page to page in specific sequence hence use of 'Back' button and other way of circumventing page order is prohibited.
   If proper order (sequence) is circumvented, service() will redirect user to a special error page. Default true.*/
   public static boolean checkSequenceID = true;

   //---- storage members ----
   /** properties that store list of pages that are under control for right sequence */
   public static Properties sequencedPages;
   /** mapping of UseCase names to classes, contains only classes that were already loaded */
   public static Hashtable _loadedUseCaseClasses = new Hashtable();
   /** class logger */
   protected static Log log = Log.getLog("izFrame.Web");

   //-------- Functions -------------------
   /** initialises Web application - sets propFileNm for Web, Log, Utils and DBservice classes that effectively sets all
   configurable values in them. From you as a framework user, that requires:
   <ul> <li> put izFrame.config to /WEB-INF dir
   </li><li> in web.xml add servlet context entry reflecting that location wih param-name izFrameProps:<br>
   &lt;context-param&gt; &lt;param-name&gt;izFrameProps&lt;/param-name&gt;
      &lt;param-value&gt;/WEB-INF/izFrame.config&lt;/param-value&gt; &lt;/context-param&gt;
   </li></ul>
   Note that init() function will get actual file path using ServletContext.getRealPath() and sysout this path for
   verification purposes.
   <br> If you prefer properties separation, you can create separate Web properties file and specify it in web.xml under servlet
   param-name izFrameWebProps and it will be used for Web initialisation instead of izFrameProps.
   <br> The following Web class members can be initialized: APP_TITLE WEB_PATH APP_FRAME_PAGE APP_ERROR_PAGE
   APP_WELCOME_PAGE APP_LOGIN_PAGE SEQ_PAGES_FILE APP_MSG_RESOURCE useFramingPage logRequest logRequestHeaders checkSequenceID.
   Entries in property file must have name that is the same as member name prefixed with "Web.". */
   public void init(){
      String izFrameProps = getServletContext().getInitParameter("izFrameProps");
      String izFrameWebProps = getInitParameter("izFrameWebProps")!=null?getInitParameter("izFrameWebProps"):izFrameProps;
      System.out.println("Web.init(): izFrameProps="+izFrameProps+", izFrameWebProps="+izFrameWebProps );
      if(izFrameProps == null) 
         return;
      String izFramePropsPath= getServletContext().getRealPath(izFrameWebProps);
      System.out.println("izFramePropsPath="+izFramePropsPath);
      //set Util, Log and DBservice configuration - static calls
      Utils._propFileNm = izFramePropsPath;
      Log.loadConfig(izFramePropsPath);
      DBservice.loadConfig(izFramePropsPath);
      //set Web class configuration - instance call
      loadConfig(izFramePropsPath);
   }
   /** reads configuration information from specified property file and sets corresponding class variables */
   protected void loadConfig(String propFileNm) {
      if(propFileNm==null)
         propFileNm = "izFrame.config"; //Utils._propFileNm;
      Properties props = new Properties();
      boolean seqLoaded = false;
      try {
         FileInputStream fis = new FileInputStream(new File(propFileNm));
         props.load(fis);
         fis.close();
         APP_TITLE         = props.getProperty("Web.APP_TITLE", APP_TITLE);
         WEB_PATH          = props.getProperty("Web.WEB_PATH", WEB_PATH);
         APP_FRAME_PAGE    = props.getProperty("Web.APP_FRAME_PAGE", APP_FRAME_PAGE);
         APP_ERROR_PAGE    = props.getProperty("Web.APP_ERROR_PAGE", APP_ERROR_PAGE);
         APP_WELCOME_PAGE  = props.getProperty("Web.APP_WELCOME_PAGE", APP_WELCOME_PAGE);
         APP_LOGIN_PAGE    = props.getProperty("Web.APP_LOGIN_PAGE", APP_LOGIN_PAGE);
         SEQ_ID_ERROR_PAGE = props.getProperty("Web.SEQ_ID_ERROR_PAGE", SEQ_ID_ERROR_PAGE);
         SEQ_PAGES_FILE    = props.getProperty("Web.SEQ_PAGES_FILE", SEQ_PAGES_FILE);
         APP_MSG_RESOURCE  = props.getProperty("Web.APP_MSG_RESOURCE", APP_MSG_RESOURCE);
         logRequest       = "true".equals(props.getProperty("Web.logRequest",       ""+logRequest))       ?true:false;
         logRequestHeaders= "true".equals(props.getProperty("Web.logRequestHeaders",""+logRequestHeaders))?true:false;
         checkSequenceID  = "true".equals(props.getProperty("Web.checkSequenceID",  ""+checkSequenceID))  ?true:false;
         useFramingPage   = "true".equals(props.getProperty("Web.useFramingPage",   ""+useFramingPage))   ?true:false;
         if(checkSequenceID){
            SEQ_PAGES_FILE = getServletContext().getRealPath(SEQ_PAGES_FILE);
            if(SEQ_PAGES_FILE!=null && new File(SEQ_PAGES_FILE).exists()) {
               sequencedPages = new Properties();
               fis = new FileInputStream(new File(SEQ_PAGES_FILE));
               sequencedPages.load(fis);   
               seqLoaded = true;
            }
         }
      }
      catch(Exception e){log.error(e, "could not load file "+propFileNm+", will use class defaults");}
      if(checkSequenceID && !seqLoaded) {
         log.error("File '"+SEQ_PAGES_FILE+"' listing sequence monitored pages is missing, disabling checkSequenceID");
         checkSequenceID = false;
      }
   }

   /** function is called each time reguest is issued. Does the following:
   <ul> <li> calls logRequest()
   </li><li> if (checkSequenceID==true), verifies pages are processed in sequence (calls isValidSequenceID())
   </li><li> calls passAdditionalChecks() to allow derived class to do extra checking - returns on false
   </li><li> calls gotoUseCase(String useCaseName. ...) that loads and calls UseCase derived class specified in form's
      "action" attribute. Note form's "action" attribute is processed in the following way to get UseCase class name:
      first letter is capitalized and string ".do" is subtracted; in gotoUseCase() name is prefixed with "usecase.".
      Hence, dont't forget that all your UseCase classes needs to be in package "usecase" or its subpackage.
   </li><li> if exception is thrown, shows APP_ERROR_PAGE or generates equivalent to it on the fly
   </li></ul> */
   protected void service(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException {
      Log.traceStart(this);
      //log request parameters, attributes, cookies - can be very useful in debugging
      if(logRequest)
         logRequest(req, logRequestHeaders);
      try {
         if(!passAdditionalChecks(req, res)){//give child servlet freedom to do more checks
            return;
         }
         //check if this page among sequencedPages and what is the value of seq ID (requires session)
         if(checkSequenceID && !isValidSequenceID(req, res)){
            log.debug("service(): invalid use of 'Back' button");
            if(SEQ_ID_ERROR_PAGE != null)
               showWebPage(SEQ_ID_ERROR_PAGE, getDataBean(req), req, res);
            else
               showHTMLstring("Warning: you should navigate pages in the proper sequence", req, res);
            return;
         }
         //instantiate and call UseCase
         String action = req.getServletPath();//Get the asked servlet path like "/placeOrder.do"
         String useCaseName = action.substring(1,2).toUpperCase()+action.substring(2,action.indexOf(".do"));//PlaceOrder
         gotoUseCase(useCaseName, getDataBean(req), req, res);
      }
      catch(Exception e){
         log.error(e, "Web.service(): exception processing "+req.getServletPath());
         showSystemErrorPage(e, req, res); //tries to show APP_ERROR_PAGE, or, if no such, generates HTML
      }
      Log.traceStop(this);
   }
   /** instantiates requested UseCase class instance and calls its passSecurity(), doBeanFromRequest(), execute() methods.
   If useCaseName parameter does not start with string "usecase.", it will be prefixed with "usecase.".
   Hence, dont't forget that all your UseCase classes needs to be in a package "usecase" or its subpackages */
   public void gotoUseCase(String useCaseName, WebBean bo, HttpServletRequest req, HttpServletResponse res)
   throws Exception {
      if(useCaseName.indexOf("usecase.")!=0)
         useCaseName = "usecase."+useCaseName;
      Class useCaseClass = (Class)_loadedUseCaseClasses.get(useCaseName);
      UseCase useCase = null;
      try {
         if(useCaseClass == null) {
            log.debug("Loading UseCase "+useCaseName);
            useCaseClass = Class.forName(useCaseName);
            _loadedUseCaseClasses.put(useCaseName, useCaseClass);
         }
         //log.debug("instantiating UseCase "+useCaseName);
         useCase = (UseCase)useCaseClass.newInstance(); //throws InstantiationException, IllegalAccessException
      }
      catch (ClassNotFoundException e) {
         log.error(e, "Web.service(): cannot load class '"+useCaseName+"', will try using Web$UseCase");
         ArrayList errors = new ArrayList();
         errors.add("<h2>default UseCase was invoked because required one '"+useCaseName+"' was not found<h2><br/>");
         req.setAttribute(Web.attr_errList, errors);
         useCase = new UseCase(); //"izFrame.Web$UseCase"
      }
      catch (java.lang.NoClassDefFoundError e) {
         log.error("Web.service(): class '"+useCaseName+"' not in classpath, will try using Web$UseCase");
         useCase = new UseCase();
      }
      catch (java.lang.InstantiationException e) {
         log.error(e, "Web.service(): no default/nullary constructor for class '"+useCaseName+"', will try using Web$UseCase");
         useCase = new UseCase();
      }
      catch (Exception e) {
         log.error(e, "Web.service(): cannot construct '"+useCaseName+"', will try using Web$UseCase");
         useCase = new UseCase();
      }
      setDataBean(bo, req);
      if(!useCase.passSecurity(this, req, res))
         return;
      WebBean data = useCase.doBeanFromRequest(req);
      String reqPageNm = getRequestingPage(req);
      if(reqPageNm==null && data!=null) //reqPageNm==null can happen for multipart requests even if param was present
         reqPageNm = (String)data.getRequestParam(attr_InnerPageNm);
      boolean isInit = true;
      String referingUseCase = useCase.getReferingUseCase(req);
      if(useCaseName.equals(referingUseCase))
              isInit = false;
      useCase.execute(isInit, this, reqPageNm, data, req, res);
      useCase.setReferingUseCase(useCaseName, req);
   }

   /** called by passSecurity(), can be overriden by the child servlet, default (this one) just returns true */
   protected boolean passAdditionalChecks(HttpServletRequest req, HttpServletResponse res)
   throws ServletException, IOException {
      return true;
   }
   // ------- optionals --------------------
   public String getServletInfo(){
      return "Web: base servlet for web applications using izFrame framework";
   }

   //-------------- main 'show' methods: JSP, JSP inside APP_FRAME_PAGE, plain HTML string ----------------
   /** Stores FormBean and page name in request and displays the specified JSP. If useFramingPage==true, show it in frame*/
   public void showWebPage(String jspName, WebBean bo, HttpServletRequest req, HttpServletResponse res)
   throws IOException, ServletException {
      req.setAttribute(attr_InnerPageNm, jspName);
      setDataBean(bo,req);
      if(useFramingPage) {
         String frameNm = null;
         HttpSession session = req.getSession(false);
         if(session != null)
            frameNm = (String)session.getAttribute(attr_framingPage);
         if(frameNm == null) frameNm = APP_FRAME_PAGE;
         log.debug("showWebPage() about to show: "+jspName+" in "+frameNm);
         dispatchReq(frameNm, req, res, true, false);//use include, don't strip attribs
      }
      else
         dispatchReq(jspName, req, res, true, false);//use include, don't strip attribs
      setReferingPage(jspName, req);
   }

   /** Displays header containing APP_TITLE, then the specified HTML string, then closes with </body></html> tags */
   public void showHTMLstring(String html, HttpServletRequest req, HttpServletResponse res)
   throws ServletException, IOException {
      if(res.isCommitted()) {
         log.error("response has already been committed (status code and headers written), cannot do html:\n"+html);
         return; //we cannot display anything at this point
      }
      PrintWriter out = new PrintWriter(res.getOutputStream());
      res.setContentType("text/html");// set content-type header before accessing the Writer
      out.println("<html><head><title>"+APP_TITLE+"</title></head>\n<body >\n");
      out.println(html);
      out.println("</body>");
      out.println("</html>");
      out.flush();
      out.close();
   }

   //-------------- show special JSP pages ----------------
   /** Displays the APP_WELCOME_PAGE */
   public void showWelcomePage(HttpServletRequest req, HttpServletResponse res)
   throws IOException, ServletException {
      if(APP_WELCOME_PAGE != null)
         showWebPage(APP_WELCOME_PAGE, getDataBean(req), req, res);//"welcome.jsp"
      else
         showHTMLstring("Warning: APP_WELCOME_PAGE not defined <br/><h1>Welcome page - TBD</h1>", req, res);
   }
   /** Displays the APP_LOGIN_PAGE */
   public void showLoginPage(HttpServletRequest req, HttpServletResponse res)
   throws IOException, ServletException {
    if(APP_LOGIN_PAGE != null)
       showWebPage(APP_LOGIN_PAGE, getDataBean(req), req, res);//"login.jsp"
    else { //displayHTML(genLoginForm(), req, res);
     String html = "<h2>Warning: APP_LOGIN_PAGE not defined, generating one dynamically:</h2><br/><h1>Login page</h1>"+
     "<form name='logon' method='post' action='"+WEB_PATH+"logon.do'>"+
     "<table border='0' width='100%' cellpadding='7'>"+
     " <tr><td align='right'>Login:</td><td align='left'><input type='text' name='userID' size='32' value=''/></td></tr>"+
     " <tr><td align='right'>Password:</td><td align='left'><input type='password' name='password' size='32' value=''/></td></tr>"+
     " <tr><th align='center' colspan='2'><input type='submit' name='submitLogon' value='Submit'/></th></tr>"+
     "</table></form>";
     showHTMLstring(html, req, res);
    }
   }
   /** Displays showWebPage(APP_ERROR_PAGE), if APP_ERROR_PAGE=null, uses showHTMLstring() with content of Exception.getMessage() */
   public void showSystemErrorPage(Exception ex, HttpServletRequest req, HttpServletResponse res) {
      try{
         if(ex != null) ex.fillInStackTrace();
         req.setAttribute(attr_exception, ex);
         if(APP_ERROR_PAGE != null)
            showWebPage(APP_ERROR_PAGE, getDataBean(req), req, res);
         else {
            StringBuffer buf = new StringBuffer();
            ArrayList errors = (ArrayList)req.getAttribute(attr_errList);
            if(errors!=null && errors.size()!=0)
              for(int i=0; i<errors.size(); i++) buf.append(errors.get(i)+"\n");
            StringWriter sout = new StringWriter(256);
            if(ex != null) {//buf.append(error.getMessage());
               ex.printStackTrace(new PrintWriter(sout));
               buf.insert(0, "\nException stack:\n").append(sout.toString());
            }
            String text = buf.toString().replaceAll("\n", "\n<br/>");
            showHTMLstring("<h2>System encountered the following error:</h2>"+text, req, res);
         }
      }
      catch (Exception e){// An error happened while reporting an error.  This is not good!
         //Report the error and return.  Never throw an error that has occured as a result of
         //handling an error as you can end up in an infinite error loop!
         log.error(e, "showSystemErrorPage(): cannot display SystemErrorPage, ERROR: "+e.getMessage());
      }
   }

   //-------- static utility methods for the above ---------------
   /** Forwards the user to the specified servlet, JSP, HTML or other resource using RequestDispatcher. */
   public static void forwardToPage(String servletName, HttpServletRequest req, HttpServletResponse res)
   throws IOException, ServletException {
      dispatchReq("/" + servletName, req, res, false, true);//don't use include (i.e use forward), strip attribs
   }
   /** Sends a redirect response to the client using the specified redirect location URL HttpServletResponse.sendRedirect() */
   public static void redirectToPage(String servletName, HttpServletRequest req, HttpServletResponse res) throws IOException{
      String path = servletName;
      if (servletName.indexOf(WEB_PATH) == -1) {
         path = WEB_PATH + servletName;
      }
      res.sendRedirect(path+req.getQueryString());
   }
   /** Forwards request from servlet to another resource (servlet, JSP file, or HTML file) on the server or includes
   the content of a resource specified using RequestDispatcher. If useInclude is true, resource is included, not
   forwarded to. If removeAttr is true, all attributes except 'menukey' and 'locale' are stripped from request. */
   public static void dispatchReq(String pageNm, HttpServletRequest req, HttpServletResponse res,
    boolean useInclude, boolean removeAttr) throws IOException, ServletException {
      if(res.isCommitted()) {
         log.error("response has already been committed (status code and headers written), cannot do: /" +pageNm);
         return; //we cannot display anything at this point
      }
      RequestDispatcher dispatcher = req.getRequestDispatcher("/" +pageNm);
      if (dispatcher == null) {
         log.error("cannot find dispatcher for page: /" +pageNm);
         //sendError(HttpServletResponse.SC_NOT_FOUND, "Dispatcher Error for resource: " + pageNm)
         throw new ServletException("Dispatcher Error for the resource: " + pageNm);
      }
      if(removeAttr) { //remove from request all attribs exept 'menukey' and 'locale'
         String attribute;
         Enumeration attributes = req.getAttributeNames();
         while (attributes.hasMoreElements()) {
            attribute = (String)attributes.nextElement();
            if (!"menukey".equalsIgnoreCase(attribute) && !"locale".equalsIgnoreCase(attribute)) {
               req.removeAttribute(attribute);
            }
         }
      }
      if(useInclude) {
         dispatcher.include(req, res);
      }
      else {
         dispatcher.forward(req, res);
      }
   }

   //------------ generic static utility functions ---------------
   /** Returns value of HttpSession attribute or null if it is missing or session is not created */
   public static Object getSessionAttrib(String attrNm, HttpServletRequest req){
      HttpSession ses = req.getSession(false);
      if(ses != null)
         return ses.getAttribute(attrNm);
      return null;
   }
   /** Sets value of HttpSession attribute if session exists, otherwise returns false */
   public static boolean setSessionAttrib(String attrNm, Object attrVal, HttpServletRequest req){
      HttpSession ses = req.getSession(false);
      if(ses != null){
         ses.setAttribute(attrNm, attrVal);
         return true;
      }
      return false;
   }
   /** stores FormBean bean in the HttpServletRequest and in HttpSession as an attribute attr_dataObject */
   public static void setDataBean(WebBean bean, HttpServletRequest req){
      req.setAttribute(attr_dataObject, bean);
      setSessionAttrib(attr_dataObject, bean, req);
   }
   /** Returns the FormBean stored in the HttpServletRequest or in HttpSession as an attribute attr_dataObject */
   public static WebBean getDataBean(HttpServletRequest req){
      WebBean bean = (WebBean)req.getAttribute(attr_dataObject);
      if(bean != null)
         return bean;
      return (WebBean)getSessionAttrib(attr_dataObject, req);
   }
   /** Returns name of 'business content' page stored in the HttpServletRequest as an attribute attr_InnerPageNm */
   public static String getRequestingPage(HttpServletRequest req){
      return req.getParameter(attr_InnerPageNm);
   }
   /** Returns name of 'business content' page stored in the HttpSession as an attribute attr_referPageNm */
   public static String getReferingPage(HttpServletRequest req){
      return (String)getSessionAttrib(attr_referPageNm, req);
   }
   /** Sets name of 'business content' page stored in the HttpSession as an attribute attr_referPageNm */
   public static void setReferingPage(String pageNm, HttpServletRequest req){
      setSessionAttrib(attr_referPageNm, pageNm, req);
   }
   /** Sets name of 'framing page' that will overwrite APP_FRAME_PAGE and be used if useFramingPage==true, stored as an attribute attr_framingPage */
   public static void setFramingPage(String pageNm, HttpServletRequest req){
      setSessionAttrib(attr_framingPage, pageNm, req);
   }
   /** stores locale as session attribute attribute attr_locale, usually called when user selects prefered language page*/
   public static void setLocale(Locale locale, HttpServletRequest req){
      setSessionAttrib(attr_locale, locale, req);
   }
   /** Returns current locale stored in the HttpSession as an attribute attr_locale */
   public static Locale getLocale(HttpServletRequest req){
      return (Locale)getSessionAttrib(attr_locale, req);
   }
   /** returns from resources value corresponding to msgKey and Locale, replaces {0} in value with non null param.
   If value is not found, returns msgKey, if Locale==null, uses Locale.getDefault() */
   public static String getResMsg(String msgKey, Locale currLoc, String param) {
      if(msgKey == null)
         return null;
      if(currLoc == null)
         currLoc = Locale.getDefault();
      //hopefully, works per API doc: Implementations of getBundle may cache instantiated resource bundles ...
      ResourceBundle myRes = ResourceBundle.getBundle(APP_MSG_RESOURCE, currLoc);
      if(myRes == null)
         return msgKey;
      Enumeration keys = myRes.getKeys();
      while (keys.hasMoreElements()) {
         String foundKey = (String)keys.nextElement();
         if(msgKey.equals(foundKey)){
            msgKey = myRes.getString(msgKey);
            if(msgKey!=null && param!=null)
               msgKey = msgKey.replaceAll("\\{0\\}", param);
            return msgKey;
         }
      }
      return msgKey;
   }
   /** returns from resources value corresponding to msgKey and Locale, uses getResMsg(msgKey, locale, null)*/
   public static String getResMsg(String msgKey, Locale currLoc) {
      return getResMsg(msgKey, currLoc, null);
   }

   /** logs header, cookies and parameters of request. If parameter name contains or equalsIgnoreCase "password", string
   "******" will be logged as the parameter value - handy feature if you don't want log file to be a security crack */
   public void logRequest(HttpServletRequest req, boolean headersAlso){
      StringBuffer buf = new StringBuffer();
      buf.append("\n ").append(req.getMethod()).append(" request from ");
      buf.append(req.getRemoteAddr()).append(" for use case: ").append(req.getServletPath()).append("\n");
      if(headersAlso){
         java.util.Enumeration headers = req.getHeaderNames();
         String header = null;
         while (headers.hasMoreElements()) {
            header = (String)headers.nextElement();
            buf.append("Header: ").append(header).append("=").append(req.getHeader(header)).append("\n");
         }
         Cookie[] cookies = req.getCookies();
         for (int i = 0; (cookies != null) && (i < cookies.length); i++) {
            buf.append("Cookie: ").append(cookies[i].getName()).append("=").append(cookies[i].getValue()).append("\n");
         }
      }
      Enumeration parameters = req.getParameterNames();
      String parameterName;
      String parameter;
      while (parameters.hasMoreElements()) {
         parameterName = (String)parameters.nextElement();
         parameter = req.getParameter(parameterName);
         if("password".equalsIgnoreCase(parameterName) || parameterName.indexOf("password")>=0)//do not show passwords
            parameter = "******";
         buf.append("Parameter: ").append(parameterName).append("=").append(parameter).append("\n");
      }
      log.inform(buf.toString());
   }

   /** called by service() function, checks if page was submitted in proper sequence (in many applications it is important
   that user goes from page to page in some predermined by business rules sequence). Use as following:
   <ul> <li> create property file named as value in SEQ_PAGES_FILE. Put pages name there in format: <pageName>=<pageName>
   </li><li> put on JSP page tag with type 'pageInfo' or use scriptlet output of generatePgNameFields() call.  </li></ul>
   Application framework will do the following:
   <ul> <li> generate semi-secret sequenceID and place it as a hidden field with name of 'attr_pageSeqID' on JSP page
   </li><li> store sequenceID value in session
   </li><li> when user submits request, function checks if it is coming from page listed in 'sequence file' and if thats true, if
       actual value of sequence ID that came to us as request parameter is the same as stored in session.  </li></ul>
   If no valid session exists or value of attr_pageSeqID attribute is null, or if requesting page name is null (i.e. request
   came from page external to the application), function returns true.
   @return true to indicate that service() can proceed, false otherwise */
   protected static boolean isValidSequenceID(HttpServletRequest req, HttpServletResponse res)
   throws ServletException, IOException {
      HttpSession session = req.getSession(false);
      if(session!=null && session.getAttribute(attr_pageSeqID)!= null && sequencedPages!=null){
         String sessionToken = (String)session.getAttribute(attr_pageSeqID);//sequence ID in session
         String jspToken = req.getParameter(attr_pageSeqID);   //sequence ID from the JSP
         String requestingPage = getRequestingPage(req);       //name of requesting page
         if(requestingPage!=null && sequencedPages.getProperty(requestingPage)!=null){//requestingPage page is in the list
            if(sessionToken==null || jspToken==null || !sessionToken.equals(jspToken)){//but IDs don't match
               return false;
            }
         }
      }
      return true;
   }


   /** UseCase class is a base class for business logic components (classes, overriding UseCase), defines interface they
   should implement - essentially, it is execute() function that provides use case specific handling of request based
   on received parameters: name of calling page, Web.FormBean (smart container of user submitted data), HTTP request and
   response.
   Also class provides default implementation of the following functions that will be called in the Web.service() method:
   <ul> <li> passSecurity() function that verifies if user is allowed to access use case
   </li><li> doBeanFromRequest() that creates FormBean (or its child) instance and populates it by request data
   </li></ul>
   Security features are regulated by security flags that can be overwritten in child classes in a custom way.
   For example, Welcome use case and all bookmarkable pages would not require Referer and Session checks, Login use case
   would not require valid user role and valid session, etc. Note, that Web functions that deal with referer page, page
   sequence id, user locale require valid session to work properly.
   <br/> <b>Major class functions</b> you will likely use more often then the others are:
   <ul> <li> execute() - overwrite it as per your specific business case, default implementation displays request params.
   </li><li> get/setUserID() - get/set from/to session userID where it is stored as an attribute attr_userID
   </li><li> get/setUserRole() - get/set from/to the session userRole stored as an attribute attr_userRole
   </li><li> addError(msgKey) - extracts from resources value corresponding to msgKey and adds it to errors ArrayList
   </li><li> getValidRoleForUseCase() - overwrite it to return CSV list of valid roles for use case, dafault is publicUser
   </li><li> writeFileFromDao(dao,binCol,imgDir) -  writes binary data stored in column binCol of Dao to file in imgDir
   </li></ul> */
   public static class UseCase {
      //---- Attribute names constants ----
      //session attribute names
      public static final String attr_userID          = "attr_userID";
      public static final String attr_userRole        = "attr_userRole";
      //request attribute names
      public static final String attr_referingUseCase = "attr_referingUseCase";

      //config data - object variables, can be overwritten in child classes
      //------ non user or user role specific -----------
      /** security flag - if true, in default implementation of passSecurity(), will cause rejection of requests
      originated from server different then the one that issued responce as normally we don't want to come to some
      intermediate page (like middle of filling up of some form) from outside, only from our server, default true.
      In inherited use cases like Login or Welcome flag is usually set to false */
      public boolean checkReferer = true;
      /** security flag - if true and user do not have valid HTTP session, redirect to 'welcome' page. Default true*/
      public boolean checkSessionExist = true;
      //------ user or user role specific -----------
      /** security flag - if true and not HTTPS (secure) connections attempted, send UNAUTHORIZED responce. Default false*/
      public boolean checkSSL = false;
      /** security flag - when secure connections used, do we have to check User Role using x509Certificate? Default false*/
      public boolean check509Certificate = false;
      /** security flag - if not secure connections used, specifies if we have to check User Role and if it is incorrect,
      redirect to user to login page, default false. If useCustomAuthentication==true causes call to
      isValidRoleForUseCase(userRole), if useBasicAuthentication, causes call req.isUserInRole(getValidRoleForUseCase())*/
      public boolean checkUserRole = false;
      /** security flag - specifies if custom authentication is used, default true. Note, user name and role are usually
      stored in session at login time using setUserID() and setUserRole(). */
      public boolean useCustomAuthentication = true;
      /** security flag - specifies if we authenticate user using Basic Authentication, default false */
      public boolean useBasicAuthentication = false;
      /** max request limit in bytes (checked when user uploads files), default 1048576 (1Mb), no limit -1. If exeeded,
      adds error "uploaded file(s) too big" */
      public int MAX_REQUEST_SIZE = 1048576;
      //other
      public static final String publicUser     = "publicUser";

      //------ UseCase security levels (public, login, application page)- user role specific ---
      /** in derived classes set there security flags depending on security level desired: public, login, secure */
      public UseCase() { }

      /** returns true if role passed in is valid. Default implementation calls getValidRoleForUseCase() and compares
      returned value to role passed in. Comparison handles the case when passed in valus are CSV lists like "user,guest"*/
      protected boolean isValidRoleForUseCase(String role, HttpServletRequest req){
         String valid = getValidRoleForUseCase();
         if((valid==null&&role!=null) || (valid!=null&&role==null)) //one is null, another not
            return false;
         else if(valid==null && role==null)//both null
            return true;
         else if (valid!=null && role!=null){ //both not null
            if(valid.equals(role))
               return true;
            //now, suppose we deal with CSV lists and try to match individual entries
            String[] valArr = valid.split(",");
            String[] roleArr = role.split(",");
            for(int i=0; valArr!=null&&i<valArr.length; i++){
               for(int j=0; roleArr!=null&&j<roleArr.length; j++){
                  if(valArr[i].equals(roleArr[j]))
                     return true;
               }
            }
         }
         return false;
      }
      /** returns valid role (can be CSV list) for UseCase, expected to be overwritten. Default returns publicUser */
      protected String getValidRoleForUseCase(){ return publicUser; }

      /** get from the session or request name of previous Use Case (last one that posted web page) */
      protected String getReferingUseCase(HttpServletRequest req){
         String uc = (String)Web.getSessionAttrib(attr_referingUseCase, req);
         if(uc==null) uc = (String)req.getAttribute(attr_referingUseCase);
         return uc;
      }
      /** store in the the session or request name of the active (last one that posted web page) Use Case */
      protected void   setReferingUseCase(String useCase, HttpServletRequest req){
         if(!setSessionAttrib(attr_referingUseCase, useCase, req))
            req.setAttribute(attr_referingUseCase, useCase);
      }
      /** get from the session userID stored as an attribute attr_userID, if not found, tries to get it from request */
      protected String getUserID(HttpServletRequest req){
         HttpSession session = req.getSession(false);
         if(session != null)
            return (String)session.getAttribute(attr_userID);
         return (String)req.getAttribute(attr_userID);
      }
      /** store in the session and in the request userID as an attribute attr_userID */
      protected void   setUserID(String userID, HttpServletRequest req){
         HttpSession session = req.getSession(false);
         if(session != null)
            session.setAttribute(attr_userID, userID);
         req.setAttribute(attr_userID, userID);
      }
      /** get from the session userRole stored as an attribute attr_userRole, if not found, tries to get it from request */
      protected String getUserRole(HttpServletRequest req){
         HttpSession session = req.getSession(false);
         if(session != null)
            return (String)session.getAttribute(attr_userRole);
         return (String)req.getAttribute(attr_userRole);
      }
      /** store in the session and request userRole as attr_userRole */
      protected void   setUserRole(String userRole, HttpServletRequest req){
         HttpSession session = req.getSession(false);
         if(session != null)
            session.setAttribute(attr_userRole, userRole);
         req.setAttribute(attr_userRole, userRole);
      }

      /** called each time reguest is issued. Note that specific checks would depend on values of various switches like:
      checkSessionExist, useCustom/BasicAuthentication, checkUserRole, checkReferer, checkSSL, check509Certificate.
      If some checks are not passed succesfully, Web class functions showLoginPage() or showWelcomePage() are called
      <br/>It is beneficial while designing web application to decide on security levels UseCases should have and devide
      them in the following groups - public, login, secure(typical application page), custom. Then, in constructor of
      corresponding UseCase, adjust those flags you want to change from their default values. Typically:
      <br/> - For 'welcome' pages - SEC_LEVEL_PUBLIC it is common to set checkReferer, checkSessionExist to false
      <br/> - For SEC_LEVEL_LOGIN set checkReferer, checkSessionExist to false, checkSSL to true and create new session
      <br/> - For SEC_LEVEL_SECURE set checkSSL, checkUserRole, checkSessionExist(default) to true.
      <br/> Default implementation of passSecurity() does the following:
      <br/> if(checkSessionExist && (session == null||!req.isRequestedSessionIdValid())) - redirect to Welcome page
      <br/> if(useCustomAuthentication): if session!=null get user name and role from its attribs and if(checkUserRole &&
            !isValidRoleForUseCase(userRole, req)) - redirect to Login page.
      <br/> Similarly handled is useBasicAuthentication case, but user name is drawn from java.security.Principal object
      <br/> if(checkSSL && (!req.isSecure()||[certificate is invalid])) - sendError SC_UNAUTHORIZED
      <br/> if(checkReferer && ([req.getHeader("Referer") != req.getServerName()] ) - redirect to Welcome page
      <br/> if(MAX_REQUEST_SIZE!=-1 && req.getContentLength()>MAX_REQUEST_SIZE) - possibly denial of service attack,
         add Error("uploaded file(s) too big") and show RequestingPage or ReferingPage or SystemErrorPage. */
      protected boolean passSecurity(Web web, HttpServletRequest req, HttpServletResponse res)
       throws ServletException, IOException {
         //get user name and role
         String userName = publicUser, userRole = publicUser;
         HttpSession session = req.getSession(false);
         if(checkSessionExist){//check if HTTP session already exists
            if(session == null || !req.isRequestedSessionIdValid()) {
               log.inform("session == null, will create a new one and show 'welcome' page");
               req.getSession(true);
               addError("Your session has expired, hence you were redirected to 'Welcome' page", req);
               web.showWelcomePage(req, res);
               return false;
            }
         }
         if(useCustomAuthentication){
            if(session != null && req.isRequestedSessionIdValid()) {
               userName = getUserID(req);
               userRole = getUserRole(req);
            }
            if(checkUserRole && !isValidRoleForUseCase(userRole, req)){
               log.error("passSecurity(): invalid user role: "+userRole+", redirecting to Login page");
               addError("Invalid user role for use case requested, hence you were redirected to 'Login' page", req);
               web.showLoginPage(req, res);
               return false;
            }
         }
         else if(useBasicAuthentication){//use standard mechanizms like Basic Authentication
            java.security.Principal p2 = req.getUserPrincipal();
            if(p2 != null && userName == null){
               userName = p2.getName();
               //req.setAttribute("userName", userName);//store user name
               //...
            }
            if(checkUserRole && !req.isUserInRole(getValidRoleForUseCase())){
               log.error("passSecurity(): invalid user role: "+userRole+", redirecting to Login page");
               addError("Invalid user role for use case requested, hence you were redirected to 'Login' page", req);
               web.showLoginPage(req, res);
               return false;
            }
         }
         if(checkSSL){//check if certificate valid
            if(req.isSecure() && check509Certificate){//check for https. note, works in >=servlet2.2
               X509Certificate[] sslCert = (X509Certificate[])req.getAttribute("java.servlet.request.X509Certificate");
               for(int i=0; i<sslCert.length; i++) {
                  try {
                     sslCert[i].checkValidity();
                     //Principal object contains name of the current authenticated user
                     //Name is the subject (subject distinguished name) value from the certificate.
                     Principal p = sslCert[i].getSubjectDN();
                     if(p != null){
                        userName = p.getName();
                        //...
                     }
                  }
                  catch(java.security.cert.CertificateExpiredException e){
                     log.debug("passSecurity(): CertificateExpiredException");
                     res.sendError(HttpServletResponse.SC_UNAUTHORIZED); //we could have added additional message?
                     return false;
                  }
                  catch(java.security.cert.CertificateNotYetValidException e){
                     log.debug("passSecurity(): CertificateNotYetValidException");
                     res.sendError(HttpServletResponse.SC_UNAUTHORIZED); //we could have added additional message?
                     return false;
                  }
               }
            }
            else {
               res.sendError(HttpServletResponse.SC_UNAUTHORIZED);
               return false;
            }
         }
         //check if 'referer' http request header points to my server
         if(checkReferer){
            String referer = req.getHeader("Referer");
            String serverName = req.getServerName().toLowerCase();//host name of server that received request
            if((referer == null || referer.toLowerCase().indexOf(serverName) < 0)) {
               log.inform("in Web.service(), invalid referer ="+referer+", serverName+"+serverName);
               addError("Referer server is not the one expected, hence you were redirected to 'Welcome' page", req);
               web.showWelcomePage(req, res);
               return false;
            }
         }
         //check if user is uploading too big files - possibly denial of service attack
         if(MAX_REQUEST_SIZE!=-1 && req.getContentLength()>MAX_REQUEST_SIZE){
            addError("uploaded file(s) too big", req);
            String pg = getRequestingPage(req)!=null?getRequestingPage(req):getReferingPage(req);
            if(pg != null)
               web.showWebPage(pg, getDataBean(req), req, res);
            else
               web.showSystemErrorPage(null, req, res);
            return false;
         }
         setUserID(userName, req);
         setUserRole(userRole, req);
         return true;
      }

      /** creates FormBean (or its child) instance and populates it by request data, specifically, populates requestFlds
      fields by values in HttpServletRequest parameters. Values will be of type String except
      <br> - if(parameterName.endsWith("Arr")) or parameter is submitted multiple times, value will be String[]
      <br> - if it is a multipart request: a) param value will contain info on stream type, file name and encoding in
             pipe delimited format like "text/plain|t.txt|ISO-8859-1";
             b) entry named 'paramName'_Bytes will be added and will hold bytes[] of uploaded file */
      public WebBean doBeanFromRequest(HttpServletRequest req)
      throws IOException {
         WebBean data = getDataBean(req);
         if(data == null) {
            log.inform("doBeanFromRequest(): no FormBean in request/session, creating new Web.FormBean(null)");
            data = new WebBean(null);
         }
         String encoding = req.getCharacterEncoding()!=null?req.getCharacterEncoding():"ISO-8859-1";
         String parameterName;
         String[] paramVals;
         if(data.requestFlds == null) //may happen if framework is used by un-experienced user
            data.requestFlds = new HashMap();
         else
            data.requestFlds.clear();
         //Multipart test: getContentType() == application/x-www-form-urlencoded or multipart/form-data; boundary=---...
         String contentType = req.getContentType();
         if(contentType!=null && contentType.indexOf("multipart/form-data")==0) {//multipart, need stream processing
            int formDataLength = req.getContentLength();
            if(formDataLength == -1)
               formDataLength = req.getIntHeader("Content-Length");
            //encoding "ISO-8859-1" aka Latin-1 is the default for HTML2.0 and above
            StringBuffer buf = new StringBuffer(); //for request logging
            if(Web.logRequest){ //for multipart, logRequest() did not display params. Do that here
               buf.append("\n got request of type "+contentType.split(" ")[0]+", size="+formDataLength+"\n");
            }
            String boundary = "--"+contentType.substring(contentType.lastIndexOf("boundary=")+9).replaceAll("\"","");//
            javax.servlet.ServletInputStream in = req.getInputStream();
            byte[] dataBytes = new byte[formDataLength];
            int totalBytesRead = 0;
            while (totalBytesRead < formDataLength) {
               int bytesRead = in.read(dataBytes, totalBytesRead, formDataLength-totalBytesRead);//readLine() is less effective
               if(bytesRead == -1)
                  throw new IOException("Bytes expected from client: "+formDataLength+", received: "+totalBytesRead);
               totalBytesRead += bytesRead;
            }
            in.close();
            //log.debug("multipart message: \n"+new String(dataBytes, encoding));
            int offset = 0;
            byte[] boundaryBytes = boundary.getBytes("ISO-8859-1");
            //--- Process parts one by one until no more boundaryBytes found ---
            outer:
            while (offset < totalBytesRead) {
               int[] resLimits = {0, 0};
               boolean found = Utils.findByteToken(dataBytes, offset, -1, boundaryBytes, resLimits);
               if(!found)
                  break;
               else if(resLimits[0] == offset){//empty part or, more often, first boundary separator
                  offset = resLimits[1];
                  continue;
               }
               int[] partLimits = {offset, resLimits[0]}; //defines part content excluding boundary bytes
               String parmNm = null, parmVal = null, fileNm=null, fileType=null;
               byte[] parmBytes = null;
               //1. find headers: Content-Disposition, Content-Type. Ignore all other lines. Break on empty one
               for(int i=0; i<4&&found; i++){ //loop until empty line but no more then 4 times
                  found = Utils.findByteToken(dataBytes, offset, partLimits[1], null, resLimits);
                  if(i==0) { //Content-Disposition' line: Content-Disposition: form-data; name="tfile"; filename="test.lst"
                     String contDisp = new String(dataBytes, offset, resLimits[0]-offset, encoding);//file name can be non asci
                     String[] toks = contDisp.split(" ");
                     if(!"content-disposition:".equalsIgnoreCase(toks[0]))
                        throw new IOException("missing 'Content-Disposition' line, got '"+contDisp);
                     if(toks.length<3 || !"form-data;".equals(toks[1]))
                        throw new IOException("wrong 'Content-Disposition'="+contDisp);
                     parmNm = toks[2].split("\"")[1];
                     if(toks.length>3 && toks[3].indexOf("filename=")==0)
                        fileNm = toks[3].substring(9).replaceAll("\"",""); //9 is lenght of "filename=", remove quotes
                  }
                  if(i==1 && found && resLimits[0]>offset){//we can find empty line or like: Content-Type: image/gif
                     String contType = new String(dataBytes, offset, resLimits[0]-offset);
                     String[] toksB = contType.split(" ");
                     if(!"content-type:".equalsIgnoreCase(toksB[0]) || toksB.length<2){
                        log.error("skipping param "+parmNm+", wrong 'Content-Type'="+contType);
                        break;
                     }
                     fileType = toksB[1]; //like application/octet-stream or image/gif or text/plain or ...
                  }
                  if(resLimits[0]==offset) {
                     offset = resLimits[1]; //move to next header line
                     break;
                  }
                  offset = resLimits[1]; //move to next header line
               }
               //2. read value
               found = Utils.findByteToken(dataBytes, offset, -1, boundaryBytes, resLimits);
               if(found && fileType == null) //normal param
                  parmVal = new String(dataBytes, offset, resLimits[0]-offset, encoding);
               else if (found){ //file (at least html file input was on the web page)
                  if(resLimits[0]==offset && fileNm.equals("")) { //filename="" - no file submitted
                     offset = resLimits[1];
                     continue; //proceed with next part
                  }
                  parmVal = fileType+"|"+fileNm+"|"+encoding;//text/plain|t.txt|ISO-8859-1
                  parmBytes = new byte[resLimits[0]-offset];
                  System.arraycopy(dataBytes, offset, parmBytes, 0, resLimits[0]-offset);
                  data.requestFlds.put(parmNm+"_Bytes", parmBytes);
               } //else no boundary, leave parmNm and parmVal as null
               if(parmNm.endsWith("Arr") || data.requestFlds.get(parmNm)!=null){
                  Object oParm = data.requestFlds.get(parmNm);
                  if(oParm == null)
                     data.requestFlds.put(parmNm, new String[]{parmVal});
                  else if(oParm instanceof String){
                     String[] parmVal2 = new String[]{((String)oParm), parmVal};
                     data.requestFlds.put(parmNm, parmVal2);
                  }
                  else if (oParm instanceof String[]){
                     ArrayList lst = new ArrayList(Arrays.asList((String[])oParm));//List lst = Arrays.asList("you","me","he");
                     lst.add(parmVal);
                     data.requestFlds.put(parmNm, lst.toArray(new String[lst.size()]));
                  }
               }
               else
                  data.requestFlds.put(parmNm, parmVal);
               if(Web.logRequest){
                  if(parmNm!=null && parmNm.toLowerCase().indexOf("password")>=0) //do not show passwords
                     parmVal = "******";
                  buf.append("Parameter: ").append(parmNm).append("=").append(parmVal).append("\n");
                  if(fileType != null)
                     buf.append("\t added parameter: '"+parmNm+"_Bytes' of size="+parmBytes.length+"\n");
               }
               offset = resLimits[1];
            } //while end - done with the part
            if(Web.logRequest)
               log.inform(buf.toString());
         }
         else { //must be application/x-www-form-urlencoded
            Enumeration parameters = req.getParameterNames();
            while (parameters.hasMoreElements()) {
               parameterName = (String)parameters.nextElement();
               paramVals = req.getParameterValues(parameterName);
               if(paramVals != null) {
                  if(parameterName.endsWith("Arr")) {  //user expects String[] - less often case
                     data.requestFlds.put(parameterName, paramVals);
                  }
                  else{      //normal and expected case
                     if(paramVals.length==1)
                        data.requestFlds.put(parameterName, paramVals[0]);
                     else if(paramVals.length>1) {
                        log.warning("Web.FormBean.saveRequest(): fld "+parameterName+" has >1 params, will use the first one");
                        data.requestFlds.put(parameterName, paramVals[0]); //that's the best we can do under circumstances
                     }
                     else if(paramVals.length==0) {
                        log.warning("Web.FormBean.saveRequest(): fld "+parameterName+" has 0 params, will set value to ''");
                        data.requestFlds.put(parameterName, "");    //that's the best we can do under circumstances
                     }
                  }
               }
            }
         }
         data.requestEncoding = encoding;
         return data;
      }

      /** extracts from resources value corresponding to msgKey and adds it to ArrayList of errors stored in request*/
      public void addError(String msgKey, HttpServletRequest req) {
         ArrayList errors = (ArrayList)req.getAttribute(attr_errList);
         if(errors == null) {
            errors = new ArrayList();
            req.setAttribute(attr_errList, errors);
         }
         Locale currLoc = getLocale(req);
         errors.add(getResMsg(msgKey, currLoc));
      }
      /** extracts from resources value corresponding to msgKey and adds it to ArrayList of errors stored in request*/
      public boolean hasErrors(HttpServletRequest req) {
         ArrayList errors = (ArrayList)req.getAttribute(attr_errList);
         if(errors==null || errors.size()==0)
           return false;
         return true;
      }

      /** writes binary data stored in column binCol of DaoList to files in subdirectory of imgDir that is specified as
      the application relative path (relative dir 'image_tmp' is physically like /igor/izFrameTutor/build/image_tmp).
      Generated files will have the following names:
      <ul> <li> if nameCol is not null, names will be read from DaoList with imgNmSuffix appended
      </li><li> if nameCol is null:
         <ul> <li> subdirectory will be created named as usecase class with all package information stripped (ex: AdminCust)
         </li><li> file names will be "file_rowNumber" with imgNmSuffix appended (ex: file_0.gif) </li></ul>
      </li></ul>
      if such file already exists, it will be overwritten if forceOverwrite==true or fileLength!=binCol bytes.length.
      Returned value is 'generic file name' that should be passed to FormBean.addHtmlColumn() function, if nameColl==null
      it will be imgDir+"/"+useCaseNm+"/file_ZrownumZ"+imgNmSuffix.
      <br> Example: <pre>
      String fNames = writeFilesFromDaoList(web, usrLst, binCol, null, imgDir, imgNmSuffix, false);
      date.addHtmlColumn("photo_thumbImg", null, "<img src='"+fNames+"' height=32 width=48/>");   </pre>*/
      public String writeFilesFromDaoList(Web web, DB.DaoList daoLst, String binCol, String nameCol, String imgDir,
       String imgNmSuffix, boolean forceOverwrite) throws IOException {
         imgDir = imgDir==null?"image_tmp":imgDir;          //set default dir as 'image_tmp'
         imgNmSuffix = imgNmSuffix==null?"":imgNmSuffix;    //set dafault as ""
         String appRealPath = web.getServletContext().getRealPath(".");//abs path for app relative path
         //log.debug("aplication real path="+appRealPath); //C:\_igor\code\java\izFrameTutor\build\.
         java.io.File dir = new java.io.File(appRealPath+"/"+imgDir);
         //log.debug(appRealPath+"/"+imgDir+" canWrite="+dir.canWrite()+", isDirectory="+dir.isDirectory());
         if((!dir.isDirectory()||!dir.canWrite()) && (!dir.mkdirs()||!dir.canWrite()))
            throw new IOException("writeImages() cannot write to directory "+appRealPath+"/"+imgDir);
         String fNames = imgDir+"/ZcolvalZ"+imgNmSuffix;
         if(nameCol == null){
            String useCaseNm = getClass().getName().substring(getClass().getName().lastIndexOf(".")+1);
            dir = new java.io.File(appRealPath+"/"+imgDir+"/"+useCaseNm);
            if((!dir.isDirectory()||!dir.canWrite()) && (!dir.mkdirs()||!dir.canWrite()))
               throw new IOException("writeImages() cannot write to directory "+appRealPath+"/"+imgDir+"/"+useCaseNm);
            imgDir = imgDir+"/"+useCaseNm;
            fNames = imgDir+"/file_ZrownumZ"+imgNmSuffix;
         }
         for(int i=0; i<daoLst.getRowCount(); i++){
            String fileName = appRealPath+"/"+imgDir+"/";
            byte[] tfile_Bytes = daoLst.getRow(i).getField(binCol).getBinValue(); //col like "photo_thumb"
            if(nameCol == null){
               fileName = fileName+"file_"+i+imgNmSuffix;
            }
            else {
               String fileNmInDB = daoLst.getRow(i).getFieldValue(nameCol);//col like "photo_thumb_nm"
               fileName = fileName+fileNmInDB+imgNmSuffix;
            }
            java.io.File imgFile = new java.io.File(fileName);
            long fileLength = imgFile.length();
            if(tfile_Bytes != null) {
               if(forceOverwrite || fileLength!=tfile_Bytes.length) { //create only if forced or differ in size
                  imgFile.createNewFile();
                  if(!imgFile.isFile() || !imgFile.canWrite())
                     throw new IOException("writeImages() cannot write to file "+fileName);
                  log.debug("\t file="+fileName+", can write="+imgFile.canWrite()+" writing ...");
                  java.io.FileOutputStream out = new java.io.FileOutputStream(fileName);//
                  out.write(tfile_Bytes);
                  out.close();
               }
            }
            else if(fileLength!=0 && (tfile_Bytes==null||tfile_Bytes.length==0)) //no data in DB, but file -> delete
               imgFile.delete();
         }
         return fNames;
      }
      /** writes binary data stored in column binCol of Dao to file in subdirectory of imgDir that is specified as
      the application relative path (relative dir 'image_tmp' is physically like /igor/izFrameTutor/build/image_tmp).
      Generated file will have relative name imgDir+"/"+[file_base]+imgNmSuffix, where file base is formed per rules:
      <ul> <li> if nameCol, is null file base is "file_01"
      </li><li> if nameCol is not null and there is such field in dao, file base will be read from field's value
      </li><li> if nameCol is not null but there is no such field in dao, nameCol becomes file base name
      </li></ul>
      Also note that if imgDir==null, image_tmp is assumed. If there is no imgDir, will try to create it.
      If imgNmSuffix is null, "" is assumed which means that you can specify full file name as nameCol parameter.
      <br/>If such file already exists, it will be overwritten if forceOverwrite==true or fileLength!=binCol bytes.length.
      Returned value is generated file relative name that can be passed to FormBean.addField() function.
      <br> Example: <pre>
      String fName = writeFileFromDao(web, userDao, "photo", "igor01.jpg", "image_tmp/user_photos", null, false);
      data.addField("userPhoto", fName);   </pre>*/
      public String writeFileFromDao(Web web, DB.Dao dao, String binCol, String nameCol, String imgDir,
       String imgNmSuffix, boolean forceOverwrite) throws IOException {
         imgDir      = imgDir==null?"image_tmp":imgDir;    //set default dir as 'image_tmp'
         imgNmSuffix = imgNmSuffix==null?"":imgNmSuffix;   //set dafault as ""
         nameCol     = nameCol==null?"file_01":nameCol;    //set dafault as "file01"
         if(dao.hasField(nameCol))
            nameCol = dao.getFieldValue(nameCol);
         String appRealPath = web.getServletContext().getRealPath(".");//abs path for app relative path
         //log.debug("aplication real path="+appRealPath); //C:\_igor\code\java\izFrameTutor\build\.
         java.io.File dir = new java.io.File(appRealPath+"/"+imgDir);
         //log.debug(appRealPath+"/"+imgDir+" canWrite="+dir.canWrite()+", isDirectory="+dir.isDirectory());
         if((!dir.isDirectory()||!dir.canWrite()) && (!dir.mkdirs()||!dir.canWrite()))
            throw new IOException("writeFileFromDao() cannot write to directory "+appRealPath+"/"+imgDir);
         String fNameVirt = imgDir+"/"+nameCol+imgNmSuffix;
         String fNameAbs  = appRealPath+"/"+fNameVirt;
         byte[] tfile_Bytes = dao.getField(binCol).getBinValue(); //col like "photo_thumb"
         //log.debug("\t tfile_Bytes="+tfile_Bytes+" tfile_Bytes.length="+(tfile_Bytes==null?-1:tfile_Bytes.length));
         java.io.File imgFile = new java.io.File(fNameAbs);
         long fileLength = imgFile.length();
         if(tfile_Bytes != null) {
            //log.debug("\t file="+fNameAbs+", fileLength="+fileLength);
            if(forceOverwrite || fileLength!=tfile_Bytes.length) { //create only if forced or differ in size
               imgFile.createNewFile();
               if(!imgFile.isFile() || !imgFile.canWrite())
                  throw new IOException("writeImages() cannot write to file "+fNameAbs);
               log.debug("\t file="+fNameAbs+", can write="+imgFile.canWrite()+" writing ...");
               java.io.FileOutputStream out = new java.io.FileOutputStream(fNameAbs);//
               out.write(tfile_Bytes);
               out.close();
            }
         }
         else if(fileLength!=0 && (tfile_Bytes==null||tfile_Bytes.length==0)) //no data in DB, but on hard drive -> delete
            imgFile.delete();
         return fNameVirt;
      }

      /** execute() function defines interface business logic components should implement. It receives
      name of calling page, values of data from it in the form of Dao bean and HTTP request and response.
      It is expected to call showWebPage() or similar function. Should be overwritten in the child class.
      @param isInit - indicates that this use case was not the last use case that posted web page and likely needs init processing
      @param web - pointer to izFrame derived servlet having bunch of useful functions like showWebPage() or gotoUseCase()
      @param fromPage - refering web page - page from which request came
      @param data - WebBean holding information on request parameters and on field values that were submitted to web page */
      public void execute(boolean isInit, Web web, String fromPage, WebBean data, HttpServletRequest req, HttpServletResponse res)
      throws Exception {
         StringBuffer buf = new StringBuffer();
         ArrayList errors = (ArrayList)req.getAttribute(Web.attr_errList);
         for(int i=0; errors!=null&&i<errors.size(); i++){
            buf.append(errors.get(i)).append("\n");//default UseCase was invoked because required one was not found
         }
         buf.append("<br/>HTTP request contained following parameters: <br>");
         buf.append("\n<table border=1>\n<tr><th>Parameter</th><th>Value</th></tr>");
         Enumeration parameters = req.getParameterNames();
         while (parameters.hasMoreElements()) {
            String parameterName = (String)parameters.nextElement();
            String[] paramVals = req.getParameterValues(parameterName);
            if(paramVals != null) {
               if(paramVals.length==1)
                  buf.append("\n<tr><td>"+parameterName+"</td><td>"+paramVals[0]+"</td></tr>");
               else if(paramVals.length>1) {
                  String val = "";
                  for(int i=0; i<paramVals.length; i++) val = val+((String)paramVals[0])+"<br/>";
                  buf.append("\n<tr><td>"+parameterName+"</td><td>"+val+"</td></tr>");
               }
               else if(paramVals.length==0) {
                  buf.append("\n<tr><td>"+parameterName+"</td><td>[err: paramVals.length==0]</td></tr>");
               }
            }
         }
         buf.append("\n</table>");
         web.showHTMLstring(buf.toString(), req, res);
      }
   }
} // end of Web
