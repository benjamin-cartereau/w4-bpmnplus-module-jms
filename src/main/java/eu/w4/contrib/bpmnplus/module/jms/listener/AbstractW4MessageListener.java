package eu.w4.contrib.bpmnplus.module.jms.listener;

import eu.w4.common.exception.CheckedException;
import eu.w4.contrib.bpmnplus.module.jms.exception.JMSModuleException;
import eu.w4.engine.client.bpmn.w4.infrastructure.DefinitionsIdentifier;
import eu.w4.engine.client.service.EngineService;
import eu.w4.engine.client.service.ObjectFactory;
import eu.w4.engine.client.service.ProcessService;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import javax.jms.Session;
import javax.jms.StreamMessage;
import ma.glasnost.orika.BoundMapperFacade;
import ma.glasnost.orika.MapperFactory;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import ma.glasnost.orika.metadata.Type;
import ma.glasnost.orika.metadata.TypeBuilder;
import ma.glasnost.orika.metadata.TypeFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.messaging.Message;

/**
 * Abstract JMS Message Listener for W4. 
 * Should be thread safe.
 */
public abstract class AbstractW4MessageListener implements InitializingBean {

  private final Log logger = LogFactory.getLog(AbstractW4MessageListener.class);

  // Service to communicate with W4 BPMN+ engine
  protected EngineService engineService;

  // Credentials to authenticate on W4 BPMN+ engine
  private String engineLogin;
  private String enginePassword;

  // Process definition name
  private String definitionsIdentifierName;
  protected DefinitionsIdentifier definitionsIdentifier;

  // Only one data entry
  private String dataEntryId;

  // Several data entries
  private Map<String, String> dataEntriesMapping;

  /**
   * Handle received message.
   * Please note that ByteMessage (without object mapping) and StreamMessage are not managed by the listener.
   * @param message Spring generic message representation of the JMS message.
   * @param session The JMS Session if needed.
   * @param jmsMessage the original JMS message if needed.
   * @return String Message to send back or null if none
   */
  public String handle(Message<?> message, Session session, javax.jms.Message jmsMessage) {
    if (message.getPayload() instanceof byte[] || message.getPayload() instanceof StreamMessage){
      throw new IllegalArgumentException("Message payload type cannot be processed by this listener (" + message.getPayload().getClass().getName()+")");
    }
    
    if (logger.isDebugEnabled()) {
      StringBuilder messageText = new StringBuilder();
      messageText.append("Received message: ").append(message.getPayload().toString());
      logger.debug(messageText.toString());
    }
    
    // Extract data
    Map<String, Object> dataEntries = mapPayloadToData(message.getPayload());
    
    // Process W4 action
    String returnedMessage = processW4Action(message.getHeaders(), dataEntries);
    
    return returnedMessage;
  }
  
  /**
   * Map payload to a Map of data
   * @param payload Message payload to map
   * @return Map&lt;String, Object&gt; payload representation
   */
  private Map<String, Object> mapPayloadToData(Object payload) {
    // Build data entries map
    Map<String, Object> dataEntries = null;
    
    if (dataEntryId != null) {
      dataEntries = new HashMap<String, Object>();
      if (payload instanceof String || payload instanceof Map) {
        dataEntries.put(dataEntryId, payload);
      }
      else {
        // Convert the object to a map (XSD type in W4)
        dataEntries.put(dataEntryId, convertObjectToMap(payload));
      }
    }
    else if (dataEntriesMapping != null) {
      if (! (payload instanceof Map)) {
        throw new IllegalArgumentException("With dataEntries mappings defined, message payload should be of Map type.");
      }
      dataEntries = new HashMap<String, Object>();
      for (String mappingKey : dataEntriesMapping.keySet()) {
        dataEntries.put(dataEntriesMapping.get(mappingKey), ((Map)payload).get(mappingKey));
      }
    }
    return dataEntries;
  }
  
  /**
   * Convert an object to a Map
   * @param object object to convert
   * @return Map&lt;String, Object&gt; converted object
   */
  protected Map<String, Object> convertObjectToMap(Object object) {
    Type objectType = TypeFactory.valueOf(object.getClass());
    Type mapType = new TypeBuilder<Map<String, Object>>() {}.build();
    
    MapperFactory mapperFactory = new DefaultMapperFactory.Builder().build();
    mapperFactory.classMap(objectType, mapType).byDefault().register();
    BoundMapperFacade boundMapper = mapperFactory.getMapperFacade(objectType, mapType);
    Map<String, Object> convertedObject = (Map<String, Object>) boundMapper.map(object);

    return convertedObject;
  }

  /**
   * Process a W4 action :
   * <ul>
   * <li>Log in to the engine</li>
   * <li>Do process the action as an authenticated user</li>
   * <li>Log out of the enfine</li>
   * </ul>
   * @param properties Set of properties if needed to process the action
   * @param dataEntries data that should (can) be used by the action
   * @return any message to send back
   */
  private String processW4Action(Map<String, Object> properties, Map<String, Object> dataEntries) {
    // LOGIN
    Principal principal = login();
    
    long timeBefore = System.currentTimeMillis();
    
    // PROCESS
    String returnedMessage = doProcessW4Action(principal, properties, dataEntries);
    
    if (logger.isDebugEnabled()) {
      logger.debug("Message processed in " + (System.currentTimeMillis() - timeBefore) + "ms");
    }
    
    // LOGOUT
    logout(principal);
    
    // Could return something (eg. process instance id) if needed. If return
    //  value is not null, reply-to channel will be used
    return returnedMessage;
  }
  
  /**
   * Implements the W4 action to process as the authenticated Principal
   * @param principal Authenticated Principal
   * @param properties Set of properties if needed to process the action
   * @param dataEntries data that should (can) be used by the action
   * @return anything to send back to the emitter (null if nothing to return)
   */
  protected abstract String doProcessW4Action(Principal principal, Map<String, Object> properties, Map<String, Object> dataEntries);
  
  /**
   * Get W4 process engine service
   * @return ProcessService W4 process service
   */
  final protected ProcessService getProcessService() {
    ProcessService processService = null;
    try {
      processService = engineService.getProcessService();
    } catch (CheckedException cex) {
      logger.error(cex.getMessage(), cex);
      throw new JMSModuleException("Cannot retrieve process service", cex);
    } catch (RemoteException rex) {
      logger.error(rex.getMessage(), rex);
      throw new JMSModuleException("Cannot retrieve process service", rex);
    }
    return processService;
  }
  
  /**
   * Log on to W4 BPMN+ Engine
   * @return Principal authenticated user principal
   */
  private Principal login() {
    Principal principal = null;
    try {
      principal = engineService.getAuthenticationService().login(engineLogin, enginePassword);
    } catch (CheckedException cex) {
      logger.error("Error on login", cex);
      throw new JMSModuleException("Cannot login against engine", cex);
    } catch (RemoteException rex) {
      logger.error("Error on login", rex);
      throw new JMSModuleException("Cannot login against engine", rex);
    }
    return principal;
  }

  /**
   * Log out from W4 BPMN+ Engine
   *
   * @param principal authenticated user principal
   */
  private void logout(Principal principal) {
    try {
      engineService.getAuthenticationService().logout(principal);
    } catch (CheckedException cex) {
      logger.error("Error on logout", cex);
      throw new JMSModuleException("Cannot sign out of the engine", cex);
    } catch (RemoteException rex) {
      logger.error("Error on logout", rex);
      throw new JMSModuleException("Cannot sign out of the engine", rex);
    }
  }

  /**
   * Initialize listener
   * <ul>
   * <li>define the definitionsIdentifier</li>
   * </ul>
   * Can be overridden if others initializations must be done
   * @throws Exception an error occured
   */
  @Override
  //@PostConstruct
  public void afterPropertiesSet() throws Exception {
    //Assert.notNull(engineService, "EngineService must be set");
    assert engineService!=null : "EngineService must be set";
    assert definitionsIdentifierName!=null : "Definitions identifier must be set";
    assert engineLogin!=null : "Login must be set";
    assert enginePassword!=null : "Password must be set";
    
    try {
      ObjectFactory factory = engineService.getObjectFactory();
      this.definitionsIdentifier = factory.newDefinitionsIdentifier();
      this.definitionsIdentifier.setId(definitionsIdentifierName);
    } catch (CheckedException cex) {
      logger.error(cex.getMessage(), cex);
      throw new JMSModuleException("Cannot retrieve identifiers", cex);
    } catch (RemoteException rex) {
      logger.error(rex.getMessage(), rex);
      throw new JMSModuleException("Cannot retrieve identifiers", rex);
    }
  }
  
  /**
   * Setter for W4 BPMN+ Engine service
   * @param engineService W4 engine service
   */
  public void setEngineService(EngineService engineService) {
    this.engineService = engineService;
  }

  /**
   * Setter for password
   *
   * @param enginePassword the enginePassword to set
   */
  public void setEnginePassword(String enginePassword) {
    this.enginePassword = enginePassword;
  }

  /**
   * Setter for login
   *
   * @param engineLogin the engineLogin to set
   */
  public void setEngineLogin(String engineLogin) {
    this.engineLogin = engineLogin;
  }

  /**
   * Setter for definitions identifier name
   *
   * @param definitionsIdentifier the definitionsIdentifier to set
   */
  public void setDefinitionsIdentifier(String definitionsIdentifier) {
    this.definitionsIdentifierName = definitionsIdentifier;
  }

  /**
   * Add a data entry mapping
   *
   * @param jms JMS message map entry id
   * @param w4 W4 data entry id
   */
  public synchronized void addDataEntryMapping(String jms, String w4) {
    if (this.dataEntriesMapping == null) {
      this.dataEntriesMapping = new HashMap<String, String>();
    }
    this.dataEntriesMapping.put(jms, w4);
  }

  /**
   * Set data entries mapping from JMS to W4
   *
   * @param dataEntriesMapping the data entries to set
   */
  public void setDataEntriesMapping(final Map<String, String> dataEntriesMapping) {
    this.dataEntriesMapping = dataEntriesMapping;
  }

  /**
   * Set the only one data entry id
   *
   * @param dataEntryId the dataEntryId to set
   */
  public void setDataEntryId(String dataEntryId) {
    this.dataEntryId = dataEntryId;
  }
}
