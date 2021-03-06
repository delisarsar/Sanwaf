package com.sanwaf.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;

import com.sanwaf.log.Logger;
import com.sanwaf.log.LoggerSystemOut;

public final class Sanwaf {
  private static final String STANDALONE_XML_FILENAME = "sanwaf.xml";
  private static final String REQ_ATT_TRACK_ID = "~sanwaf-id";
  private static final String REQ_ATT_ERRORS = "~sanwaf-errors";

  private String xmlFilename = null;
  protected Logger logger;

  boolean enabled = false;
  boolean verbose = false;
  boolean onErrorAddTrackId = true;
  boolean onErrorAddParmErrors = true;
  protected static String securedAppVersion = "unknown";
  protected List<Shield> shields = new ArrayList<>();
  Map<String, String> globalErrorMessages = new HashMap<>();

  public enum AllowListType {
    HEADER, COOKIE, PARAMETER
  }

  /**
   * Default Sanwaf constructor.
   * 
   * <pre>
   * Creates a in instance of Sanwaf initializing it with:
   *  -default System.out.println Logger (com.sanwaf.log.LoggerSystemOut)
   *   should not be used in a production environment
   *  -default Sanwaf XML configuration file (sanwaf.xml on classpath)
   * </pre>
   * 
   * @return void
   */
  public Sanwaf() throws IOException {
    this(new LoggerSystemOut(), "/" + STANDALONE_XML_FILENAME);
  }

  /**
   * Sanwaf constructor.
   * 
   * <pre>
   * Creates a new Sanwaf instance initializing it with the logger provided; 
   * Uses the default Sanwaf XML configuration file (sanwaf.xml on classpath)
   * </pre>
   * 
   * @param logger
   *          A logger of your choice that implements the com.sanwaf.log.Logger
   *          interface
   * 
   * @return void
   */
  public Sanwaf(Logger logger) throws IOException {
    this(logger, "/" + STANDALONE_XML_FILENAME);
  }

  /**
   * Sanwaf constructor where you specify the logger and properties file to use
   * 
   * <pre>
   * Creates a new instance of Sanwaf using the logger & Sanwaf XML configuration provided.
   * </pre>
   * 
   * @param logger
   *          A logger of your choice that implements the com.sanwaf.log.Logger
   *          interface
   * @param filename
   *          Fully qualified path to a valid Sanwaf XML file
   * @return void
   */
  public Sanwaf(Logger logger, String filename) throws IOException {
    this.logger = logger;
    this.xmlFilename = filename;
    loadProperties();
  }

  /**
   * Method to determine if a given request contains a threat determined by
   * configured shields
   * 
   * <pre>
   * If an error is detected, attributes will be added to request for processing latter.  
   *  Attributes added are dependent on the properties settings of:
   *        <provideTrackId>true/false</provideTrackId>
   *        <provideErrors>true/false</provideErrors>
   * 
   * Use the following methods in this class to retrieve the values:
   * 	public static String getTrackingId(HttpServletRequest req)
   * 	public static String getErrors(HttpServletRequest req)
   * </pre>
   * 
   * @param req
   *          ServletRequest the ServletRequest object you want to scan for
   *          threats
   * @return boolean true/false if a threat was detected
   */
  public boolean isThreatDetected(ServletRequest req) {
    if (!enabled || !(req instanceof HttpServletRequest)) {
      return false;
    }
    for (Shield shield : shields) {
      if (shield.threatDetected(req)) {
        addErrorAttributes(req, getSortOfRandomNumber(), getErrorList(req));
        return true;
      }
    }
    return false;
  }

  /**
   * Method to determine if a given value contains a threat determined by
   * configured shields
   * 
   * <pre>
   * If an error is detected, attributes will be added to request for processing latter.  
   *  Attributes added are dependent on the properties settings of:
   *        <provideTrackId>true/false</provideTrackId>
   *        <provideErrors>true/false</provideErrors>
   * 
   * Use the following methods in this class to retrieve the values:
   *  public static String getTrackingId(HttpServletRequest req)
   *  public static String getErrors(HttpServletRequest req)
   * </pre>
   * 
   * @param value
   *          a String object you want to scan for threats
   * @return boolean true/false if a threat was detected
   */
  public boolean isThreat(String value) {
    for (Shield sh : shields) {
      if (sh.threat(null, null, "", value)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Method to retrieve an allow-listed header/cookie/parameter value from a
   * request. The header/cookie/parameter value will be returned IFF the its
   * name is set in any Shield's Metadata block
   * 
   * <pre>
   *    <metadata>
   *      <secured>
   *        <headers></headers>
   *        <cookies></cookies>
   *        <parameters></parameters>
   *      </secured>
   *    </metadata>
   * </pre>
   * 
   * @param request
   *          HttpServletRequest Object to pull the header/cookie/parameter
   *          value from
   * @param type
   *          the Sanwaf.AllowListType type (HEADER, COOKIE, PARAMETER)
   * @param name
   *          the name of the header/cookie/parameter you want to get
   * @return String the value of the requested header/cookie/parameter requested
   *         or null.
   */
  public String getAllowListedValue(String name, AllowListType type, HttpServletRequest req) {
    for (Shield sh : shields) {
      String value = sh.getAllowListedValue(name, type, req);
      if (value != null) {
        return value;
      }
    }
    return null;
  }

  /**
   * The reload method to dynamically reload sanwaf.
   *
   * @return void
   */
  public final void reLoad() throws IOException {
    loadProperties();
  }

  /**
   * Get the Sanwaf Tracking ID
   * 
   * <pre>
   * useful for displaying to your users in case they call support. this allows
   * you to pull the exact exception from the log file
   * 
   * <pre>
   * 
   * @param req
   *          HttpServletRequest the request object where
   *          Sanwaf.isThreatDetected() returned true.
   * @return String returns the Sanwaf Tracking ID
   */
  public static String getTrackingId(HttpServletRequest req) {
    Object o = req.getAttribute(REQ_ATT_TRACK_ID);
    if (o != null) {
      return String.valueOf(o);
    }
    return "Sanwaf TrackId is disabled";
  }

  /**
   * Returns all threats found for a give request object in JSON format
   * 
   * <pre>
   * Used to display errors to the user.
   * </pre>
   * 
   * @param req
   *          HttpServletRequest the request object where
   *          Sanwaf.isThreatDetected() returned true.
   * @return String Returns all threats found in JSON format
   */
  public static String getErrors(HttpServletRequest req) {
    Object o = req.getAttribute(REQ_ATT_ERRORS);
    if (o != null) {
      return String.valueOf(o);
    }
    return "Sanwaf Error handling is disabled";
  }

  private List<Error> getErrorList(ServletRequest req) {
    List<Error> errors = new ArrayList<>();
    if (!onErrorAddParmErrors) {
      return errors;
    }
    String k = null;
    String[] values = null;
    Enumeration<?> names = req.getParameterNames();

    while (names.hasMoreElements()) {
      k = (String) names.nextElement();
      values = req.getParameterValues(k);
      for (String v : values) {
        getShieldErrors(req, errors, k, v);
      }
    }
    return errors;
  }

  private void getShieldErrors(ServletRequest req, List<Error> errors, String key, String value) {
    for (Shield sh : shields) {
      if (sh.threatDetected(req)) {
        errors.addAll(sh.getErrors(req, key, value));
      }
    }
  }

  List<Error> getError(ServletRequest req, Shield shield, String key, String value) {
    return shield.getErrors(req, key, value);
  }

  private void addErrorAttributes(ServletRequest req, String id, List<Error> errors) {
    if (onErrorAddTrackId) {
      req.setAttribute(REQ_ATT_TRACK_ID, id);
    }
    if (onErrorAddParmErrors) {
      req.setAttribute(REQ_ATT_ERRORS, Error.toJson(errors));
    }
  }

  static String getSortOfRandomNumber() {
    try {
      java.security.SecureRandom srandom = new java.security.SecureRandom();
      return String.format("%03d", srandom.nextInt(99999)) + "-" + String.format("%03d", srandom.nextInt(9999));
    } catch (IllegalFormatException e) {
      return String.valueOf(UUID.randomUUID());
    }
  }

  // XML LOAD CODE
  private static final String XML_GLOBAL_SETTINGS = "global-settings";
  private static final String XML_ENABLED = "enabled";
  private static final String XML_VERBOSE = "verbose";
  private static final String XML_APP_VER = "app.version";
  private static final String XML_ERR_HANDLING = "errorHandling";
  private static final String XML_ERR_SET_ATT_TRACK_ID = "provideTrackId";
  private static final String XML_SET_ATT_PARM_ERR = "provideErrors";
  private static final String XML_SHIELD = "shield";

  private synchronized void loadProperties() throws IOException {
    long start = System.currentTimeMillis();
    Xml xml;
    try {
      xml = new Xml(Sanwaf.class.getResource(xmlFilename));
    } catch (IOException e) {
      throw new IOException("Sanwaf Failed to load config file " + xmlFilename + ".  \n**Server is NOT protected**\n", e);
    }

    String settingsBlock = xml.get(XML_GLOBAL_SETTINGS);
    Xml settingsBlockXml = new Xml(settingsBlock);
    enabled = Boolean.parseBoolean(settingsBlockXml.get(XML_ENABLED));
    verbose = Boolean.parseBoolean(settingsBlockXml.get(XML_VERBOSE));
    securedAppVersion = settingsBlockXml.get(XML_APP_VER);
    logger.info("Starting Sanwaf: enabled=" + enabled + ", " + XML_VERBOSE + "=" + verbose + ", " + XML_APP_VER + "=" + securedAppVersion);

    String errorBlock = xml.get(XML_ERR_HANDLING);
    Xml errorBlockXml = new Xml(errorBlock);
    onErrorAddTrackId = Boolean.parseBoolean(errorBlockXml.get(XML_ERR_SET_ATT_TRACK_ID));
    onErrorAddParmErrors = Boolean.parseBoolean(errorBlockXml.get(XML_SET_ATT_PARM_ERR));

    Error.setErrorMessages(globalErrorMessages, xml);

    String[] xmls = xml.getAll(XML_SHIELD);
    for (String item : xmls) {
      shields.add(new Shield(this, new Xml(item), logger));
    }
    logger.info("Started in: " + (System.currentTimeMillis() - start) + " ms.");
  }

}
