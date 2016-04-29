package eu.w4.contrib.bpmnplus.module.jms.listener;

import eu.w4.common.exception.CheckedException;
import eu.w4.contrib.bpmnplus.module.jms.exception.JMSModuleException;
import eu.w4.contrib.bpmnplus.module.jms.identification.ConnectionManager;
import eu.w4.contrib.bpmnplus.module.jms.identification.User;
import eu.w4.engine.client.bpmn.w4.infrastructure.DefinitionsIdentifier;
import eu.w4.engine.client.service.EngineService;
import eu.w4.engine.client.service.ObjectFactory;
import eu.w4.engine.client.service.ProcessService;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import javax.jms.Session;
import javax.jms.StreamMessage;
import ma.glasnost.orika.BoundMapperFacade;
import ma.glasnost.orika.MapperFactory;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import ma.glasnost.orika.metadata.Type;
import ma.glasnost.orika.metadata.TypeBuilder;
import ma.glasnost.orika.metadata.TypeFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.messaging.Message;

/**
 * Abstract JMS messages listener and processor for W4. Should be thread safe.
 */
public abstract class AbstractW4MessageListener implements InitializingBean, DisposableBean {

  private static final Logger logger = LogManager.getLogger();

  // Service to communicate with W4 BPMN+ engine
  protected EngineService engineService;

  // Principal and credentials to authenticate on W4 BPMN+ engine
  private User engineUser;

  // Process definition name
  private String definitionsIdentifierName;
  protected DefinitionsIdentifier definitionsIdentifier;

  // Only one data entry
  private String dataEntryId;

  // Several data entries
  private Map<String, String> dataEntriesMapping;

  // W4 connection manager
  private ConnectionManager connectionManager;
  
  /**
   * Handle received message. Please note that ByteMessage (without object
   * mapping) and StreamMessage are not managed by the listener.
   *
   * @param message Spring generic message representation of the JMS message.
   * @param session The JMS Session if needed.
   * @param jmsMessage the original JMS message if needed.
   * @return String Message to send back or null if none
   */
  public String handle(Message<?> message, Session session, javax.jms.Message jmsMessage) {
    if (message.getPayload() instanceof byte[] || message.getPayload() instanceof StreamMessage) {
      throw new IllegalArgumentException("Message payload type cannot be processed by this listener (" + message.getPayload().getClass().getName() + ")");
    }

    logger.debug("Received message: {}", message.getPayload().toString());

    // Extract data
    Map<String, Object> dataEntries = mapPayloadToData(message.getPayload());

    // Process W4 action
    String returnedMessage = processW4Action(message.getHeaders(), dataEntries);

    return returnedMessage;
  }

  /**
   * Map payload to a Map of data
   *
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
      } else {
        // Convert the object to a map (XSD type in W4)
        dataEntries.put(dataEntryId, convertObjectToMap(payload));
      }
    } else if (dataEntriesMapping != null) {
      if (!(payload instanceof Map)) {
        throw new IllegalArgumentException("With dataEntries mappings defined, message payload should be of Map type.");
      }
      dataEntries = new HashMap<String, Object>();
      for (String mappingKey : dataEntriesMapping.keySet()) {
        dataEntries.put(dataEntriesMapping.get(mappingKey), ((Map) payload).get(mappingKey));
      }
    }
    return dataEntries;
  }

  /**
   * Convert an object to a Map
   *
   * @param object object to convert
   * @return Map&lt;String, Object&gt; converted object
   */
  protected Map<String, Object> convertObjectToMap(Object object) {
    Type objectType = TypeFactory.valueOf(object.getClass());
    Type mapType = new TypeBuilder<Map<String, Object>>() {
    }.build();

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
   *
   * @param properties Set of properties if needed to process the action
   * @param dataEntries data that should (can) be used by the action
   * @return any message to send back
   */
  private String processW4Action(Map<String, Object> properties, Map<String, Object> dataEntries) {
    String returnedMessage = null;

    // LOGIN
    Principal principal = connectionManager.login(engineUser);

    long timeBefore = System.currentTimeMillis();

    // PROCESS
    returnedMessage = doProcessW4Action(principal, properties, dataEntries);

    logger.debug("Message processed in {}ms", System.currentTimeMillis() - timeBefore);

    // Could return something (eg. process instance id) if needed. If return
    //  value is not null, reply-to channel will be used
    return returnedMessage;
  }

  /**
   * Implements the W4 action to process as the authenticated Principal
   *
   * @param principal Authenticated Principal
   * @param properties Set of properties if needed to process the action
   * @param dataEntries data that should (can) be used by the action
   * @return anything to send back to the emitter (null if nothing to return)
   */
  protected abstract String doProcessW4Action(Principal principal, Map<String, Object> properties, Map<String, Object> dataEntries);

  /**
   * Get W4 process engine service
   *
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
   * Initialize listener
   * <ul>
   * <li>define the definitionsIdentifier</li>
   * </ul>
   * Can be overridden if others initializations must be done
   *
   * @throws Exception an error occured
   */
  @Override
  //@PostConstruct
  public void afterPropertiesSet() throws Exception {
    //Assert.notNull(engineService, "EngineService must be set");
    assert engineService != null : "EngineService must be set";
    assert engineUser != null : "User must be set";
    assert definitionsIdentifierName != null : "Definitions identifier must be set";

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
   *
   * @param engineService W4 engine service
   */
  public void setEngineService(EngineService engineService) {
    this.engineService = engineService;
  }

  /**
   * Setter for user
   * @param user the engine user to use
   */
  public void setEngineUser(User user) {
    this.engineUser = user;
    // TODO : need to logout if user change?
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
  
  /**
   * Set the W4 connection manager
   * @param connectionManager ConnectionManager
   */
  public void setConnectionManager(ConnectionManager connectionManager) {
    this.connectionManager = connectionManager;
  }
  
  @Override
  public void destroy() throws Exception {
    logger.debug("Destroy listener ({})", this.getClass().getName());
    connectionManager.logout(engineUser);
  }
}
