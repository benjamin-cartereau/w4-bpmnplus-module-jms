package eu.w4.contrib.bpmnplus.module.jms;

import eu.w4.contrib.bpmnplus.module.jms.configuration.BpmnAction;
import eu.w4.contrib.bpmnplus.module.jms.listener.AbstractW4MessageListener;
import eu.w4.contrib.bpmnplus.module.jms.listener.MessageListenerFactory;
import eu.w4.engine.client.service.EngineService;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Resource;
import javax.jms.Session;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.listener.adapter.AbstractAdaptableMessageListener;
import org.springframework.jms.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.util.ReflectionUtils;

/**
 * Declaration and dynamic configuration of JMS listeners
 */
//@EnableJms
@Configuration
//@PropertySource(value = "classpath:configuration.properties", ignoreResourceNotFound = true)
public class ApplicationConfig implements JmsListenerConfigurer {
  
  private final Log logger = LogFactory.getLog(ApplicationConfig.class);

  // Properties names in JMS messages
  // - Fully qualified class name of the DTO
  private static final String JMS_PROPERTY_DTO_CLASSNAME = "ClassName";
  // - Signal name
  private static final String JMS_PROPERTY_SIGNAL_NAME = "SignalName";

  // Configuration file properties names
  private static final String PROPERTY_JMS_ENDPOINTS = "module.jms.endpoints";
  private static final String PROPERTY_JMS_LOGIN = "module.jms.principal.login";
  private static final String PROPERTY_JMS_PASSWORD = "module.jms.principal.password";
  
  private static final String PROPERTY_JMS_ENDPOINT_PREFIX = "module.jms.endpoint";
  
  private static final String PROPERTY_JMS_ENDPOINT_BPMN_PREFIX = "bpmn";
  
  private static final String PROPERTY_JMS_ENDPOINT_BPMN_ACTION = "bpmn.action";
  private static final String PROPERTY_JMS_ENDPOINT_BPMN_DEFINITION = "bpmn.definition_identifier";
  private static final String PROPERTY_JMS_ENDPOINT_BPMN_COLLABORATION = "bpmn.collaboration_identifier";
  private static final String PROPERTY_JMS_ENDPOINT_BPMN_PROCESS = "bpmn.process_identifier";
  private static final String PROPERTY_JMS_ENDPOINT_BPMN_SIGNAL_ID = "bpmn.signal_identifier";
  private static final String PROPERTY_JMS_ENDPOINT_BPMN_SIGNAL_NAME = "bpmn.signal_name";
  
  private static final String PROPERTY_JMS_ENDPOINT_BPMN_NAME_PREFIX = "bpmn.process_instance_name_prefix";
  
  private static final String PROPERTY_JMS_ENDPOINT_BPMN_DATAENTRY = "bpmn.data_entry_id";
  private static final String PROPERTY_JMS_ENDPOINT_MAPPING = "mapping";
  
  private static final String PROPERTY_JMS_ENDPOINT_PASSWORD = "principal.password";
  private static final String PROPERTY_JMS_ENDPOINT_LOGIN = "principal.login";
  private static final String PROPERTY_JMS_ENDPOINT_SELECTOR = "selector";
  private static final String PROPERTY_JMS_ENDPOINT_DESTINATION = "destination";
  private static final String MAPPING_NONE = "none";
  private static final String MAPPING_JSON = "json";
  
  private static final String PROPERTY_SEPARATOR = ".";
  private static final String SEPARATOR_COMMA = "\\s*,\\s*";
  
  private final DefaultMessageHandlerMethodFactory methodFactory = new DefaultMessageHandlerMethodFactory();
  
  @Autowired
  Environment env;
  
  @Resource(name = "configurationProperties")
  Properties conf;
  
  @Autowired
  MessageListenerFactory listenerFactory;
  
  @Override
  public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
    initializeFactory(methodFactory);

    /*
     As a single container factory setup can be fairly common, the containerFactory attribute can 
     be omitted if a default one has either been set or discovered. 
     By default, we look up for a bean named jmsListenerContainerFactory.
     */
    if (conf == null) {
      logger.warn("Configuration file (configuration.properties) is missing");
      return;
    }

    // Check if any endpoint has been defined
    String endpointsStr = StringUtils.trim(conf.getProperty(PROPERTY_JMS_ENDPOINTS));
    if (StringUtils.isEmpty(endpointsStr)) {
      logger.warn("No endpoint has been defined");
      return;
    }

    // Parse endpoints ids
    String[] endpoints = stringAsArray(endpointsStr);
    
    String defaultLogin = conf.getProperty(PROPERTY_JMS_LOGIN);
    String defaultPassword = conf.getProperty(PROPERTY_JMS_PASSWORD);

    // Configure each endpoint
    for (String endpointId : endpoints) {
      JmsListenerEndpoint endpoint;
      try {
        endpoint = configureEndpoint(endpointId, defaultLogin, defaultPassword);
      } catch (Exception ex) {
        logger.error("Failed to configure endpoint '" + endpointId + "'", ex);
        continue;
      }
      // The default registrar is already populated with the container factory
      //  bean named "jmsListenerContainerFactory" by default
      //registrar.setContainerFactoryBeanName();
      registrar.registerEndpoint(endpoint);
    }
  }

  /**
   * Configure one endpoint
   *
   * @param endpointId
   * @param defaultLogin
   * @param defaultPassword
   * @return Configured endpoint
   */
  private JmsListenerEndpoint configureEndpoint(String endpointId, String defaultLogin, String defaultPassword) throws Exception {
    // Define the endpoint
    SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
    endpoint.setId(endpointId);
    endpoint.setDestination(getRequiredEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_DESTINATION));
    
    String selector = getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_SELECTOR);
    if (!StringUtils.isEmpty(selector)) {
      endpoint.setSelector(selector);
    }

    // Define the receiver
    //DefltMessageListener receiver = new DefaultMessageListener();
    EngineService engineService = env.getProperty(JMSModule.PROPERTY_ENGINE_SERVICE, EngineService.class);
    String login = getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_LOGIN, defaultLogin);
    String password = getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_PASSWORD, defaultPassword);
    String definitionsIdentifier = getRequiredEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_BPMN_DEFINITION);
    
    String actionAsString = getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_BPMN_ACTION);
    BpmnAction action = BpmnAction.INSTANTIATE;
    try {
      action = BpmnAction.parse(actionAsString);
    } catch (IllegalArgumentException wrongArgument) {
      logger.warn("Action unknown for endpoint " + endpointId + " : only 'instantiate' or 'signal' are allowed. Instantiate will be used.");
    }
    
    Map<String, ? extends Object> properties = subsetToCamelCase(conf, getEndpointBpmnPropertiesPrefix(endpointId), true);
    AbstractW4MessageListener listener = listenerFactory.getListener(action, engineService, login, password, definitionsIdentifier, properties);

    // Define the listener
    //MessageListenerAdapter listener = new MessageListenerAdapter(receiver);
    MessagingMessageListenerAdapter listenerAdapter = new MessagingMessageListenerAdapter();
    listenerAdapter.setHandlerMethod(methodFactory.createInvocableHandlerMethod(listener, getHandleMethod()));

    // Set the mapping if needed
    setMapping(endpointId, listenerAdapter);
    
    endpoint.setMessageListener(listenerAdapter);
    
    return endpoint;
  }

  /**
   * Define any mapping
   *
   * @param endpointId if of the endpoint
   * @param listener listener
   */
  private void setMapping(final String endpointId, AbstractAdaptableMessageListener listener) {
    String mapping = getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_MAPPING);
    if (MAPPING_JSON.equalsIgnoreCase(mapping)) {
      MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
      converter.setTypeIdPropertyName(JMS_PROPERTY_DTO_CLASSNAME);
      listener.setMessageConverter(converter);
    } else if (mapping != null && !MAPPING_NONE.equalsIgnoreCase(mapping)) {
      logger.warn("Mapping type unknown for endpoint " + endpointId + " : only 'none' or 'json' are allowed.");
    }
  }

  /**
   * Split a String to a List of strings
   *
   * @param listAsString list as string (comma separated)
   * @return String[] array of Strings
   */
  private String[] stringAsArray(String listAsString) {
    return listAsString.split(SEPARATOR_COMMA);
  }

  /**
   * Get a non mandatory endpoint property
   *
   * @param endpointId id of the endpoint
   * @param suffix property key suffix
   * @return String value of the property, null if not found
   */
  private String getEndpointProperty(String endpointId, String suffix) {
    return getEndpointProperty(endpointId, suffix, false);
  }

  /**
   * Get a non mandatory endpoint property. If not found, default value is
   * returned
   *
   * @param endpointId id of the endpoint
   * @param suffix property key suffix
   * @param defaultValue default value if the property is not found
   * @return String value of the property, null if not found
   */
  private String getEndpointProperty(String endpointId, String suffix, String defaultValue) {
    String value = getEndpointProperty(endpointId, suffix, false);
    if (StringUtils.isEmpty(value)) {
      return defaultValue;
    }
    return value;
  }

  /**
   * Get endpoint properties prefix
   *
   * @param endpointId id of the endpoint
   * @return properties prefix for this endpoint
   */
  private String getEndpointPropertiesPrefix(String endpointId) {
    StringBuilder propertiesPrefix = new StringBuilder();
    propertiesPrefix.append(PROPERTY_JMS_ENDPOINT_PREFIX).append(PROPERTY_SEPARATOR);
    propertiesPrefix.append(endpointId);
    return propertiesPrefix.toString();
  }

    /**
   * Get endpoint properties prefix realated to bpmn configuration
   *
   * @param endpointId id of the endpoint
   * @return bpmn properties prefix for this endpoint
   */
  private String getEndpointBpmnPropertiesPrefix(String endpointId) {
    StringBuilder bpmnPropPrefix = new StringBuilder();
    bpmnPropPrefix.append(getEndpointPropertiesPrefix(endpointId));
    bpmnPropPrefix.append(PROPERTY_SEPARATOR);
    bpmnPropPrefix.append(PROPERTY_JMS_ENDPOINT_BPMN_PREFIX);
    return bpmnPropPrefix.toString();
  }
  
  /**
   * Get an endpoint property
   *
   * @param endpointId id of the endpoint
   * @param suffix property key suffix
   * @param mandatory is the property mandatory
   * @return String value of the property
   */
  private String getEndpointProperty(String endpointId, String suffix, boolean mandatory) {
    StringBuilder propertyKey = new StringBuilder();
    propertyKey.append(getEndpointPropertiesPrefix(endpointId));
    propertyKey.append(PROPERTY_SEPARATOR).append(suffix);
    String propertyValue;
    if (mandatory) {
      propertyValue = getRequiredProperty(conf, propertyKey.toString());
    } else {
      propertyValue = conf.getProperty(propertyKey.toString());
    }
    return StringUtils.trim(propertyValue);
  }

  /**
   * Get a mandatory endpoint property
   *
   * @param endpointId id of the endpoint
   * @param suffix property key suffix
   * @return String value of the property
   */
  private String getRequiredEndpointProperty(String endpointId, String suffix) {
    return getEndpointProperty(endpointId, suffix, true);
  }

  /**
   * Get a required property
   *
   * @param properties Properties
   * @param key Searched key
   * @return Found value or throws an IllegalStateException if not found
   */
  private static String getRequiredProperty(Properties properties, String key) {
    String value = properties.getProperty(key);
    if (value == null) {
      throw new IllegalStateException(String.format("required key '[%s]' not found", key));
    }
    return value;
  }
  
  private void initializeFactory(DefaultMessageHandlerMethodFactory factory) {
    //TODO??? factory.setBeanFactory(new StaticListableBeanFactory());
    factory.afterPropertiesSet();
  }
  
  private Method getHandleMethod() {
    return ReflectionUtils.findMethod(AbstractW4MessageListener.class, "handle", Message.class, Session.class, javax.jms.Message.class);
  }

  /**
   * Creates a map from the original properties, by copying those properties
   * that have specified first part of the key name. Prefix may be optionally
   * stripped during this process. All properties keys that are using snake case
   * will be converted to camel case.
   *
   * @param properties source properties, from which new object will be created
   * @param prefix key names prefix
   * @param stripPrefix should the prefix be stripped
   *
   * @return Map the subset of properties
   */
  public static Map<String, ? extends Object> subsetToCamelCase(Properties properties, String prefix, boolean stripPrefix) {
    Map<String, Object> result = new HashMap<String, Object>();
    
    if (properties == null) {
      return result;
    }
    
    if (StringUtils.isBlank(prefix)) {
      prefix = StringUtils.EMPTY;
    }
    
    if (StringUtils.isNotBlank(prefix) && !prefix.endsWith(PROPERTY_SEPARATOR)) {
      prefix += PROPERTY_SEPARATOR;
    }
    
    int baseLen = prefix.length();
    for (Object o : properties.keySet()) {
      String key = (String) o;
      if (key.startsWith(prefix)) {
        String name = stripPrefix ? key.substring(baseLen) : key;
        result.put(fromSnakeToCamelCase(name), properties.getProperty(key));
      }
    }
    return result;
  }

  /**
   * Convert a text from snake-case to (Upper) CamelCase
   *
   * @param text to transform
   * @return transformed text in CamelCase
   */
  private static String fromSnakeToCamelCase(String text) {
    return StringUtils.uncapitalize(StringUtils.remove(WordUtils.capitalizeFully(text, '_'), "_"));
  }
}
