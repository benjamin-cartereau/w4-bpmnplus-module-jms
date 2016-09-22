package eu.w4.contrib.bpmnplus.module.jms;

import eu.w4.contrib.bpmnplus.module.jms.configuration.BpmnAction;
import eu.w4.contrib.bpmnplus.module.jms.exception.JMSModuleException;
import eu.w4.contrib.bpmnplus.module.jms.listener.AbstractW4MessageListener;
import eu.w4.contrib.bpmnplus.module.jms.listener.MessageListenerFactory;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.annotation.Resource;
import javax.inject.Inject;
import javax.jms.Session;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.listener.adapter.AbstractAdaptableMessageListener;
import org.springframework.jms.listener.adapter.MessagingMessageListenerAdapter;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.messaging.handler.invocation.InvocableHandlerMethod;
import org.springframework.util.ReflectionUtils;

/**
 * Declaration and dynamic configuration of JMS listeners
 */
@EnableJms
@Configuration
//@PropertySource(value = "classpath:configuration.properties", ignoreResourceNotFound = true)
public class ApplicationConfig implements JmsListenerConfigurer {
  
  private static final Logger logger = LogManager.getLogger();

  // Properties names in JMS messages
  // - Fully qualified class name of the DTO
  private static final String JMS_PROPERTY_DTO_CLASSNAME = "ClassName";

  // Configuration file properties names
  private static final String CONFIGURATION_KEY_JMS_ENDPOINTS = "module.jms.endpoints";
  private static final String CONFIGURATION_KEY_JMS_LOGIN = "module.jms.principal.login";
  private static final String CONFIGURATION_KEY_JMS_PASSWORD = "module.jms.principal.password";
  
  //private static final String PROPERTY_JMS_ENDPOINT_PREFIX = "module.jms.endpoint";
  private static final String CONFIGURATION_KEY_JMS_ENDPOINT_DESTINATION = "module.jms.endpoint.%s.destination";
  private static final String CONFIGURATION_KEY_JMS_ENDPOINT_SELECTOR = "module.jms.endpoint.%s.selector";
  private static final String CONFIGURATION_KEY_JMS_ENDPOINT_LOGIN = "module.jms.endpoint.%s.principal.login";
  private static final String CONFIGURATION_KEY_JMS_ENDPOINT_PASSWORD = "module.jms.endpoint.%s.principal.password";
  private static final String CONFIGURATION_KEY_JMS_ENDPOINT_MAPPING = "module.jms.endpoint.%s.mapping";
  
  private static final String CONFIGURATION_KEY_JMS_ENDPOINT_BPMN_PREFIX = "module.jms.endpoint.%s.bpmn";
  private static final String CONFIGURATION_KEY_JMS_ENDPOINT_BPMN_ACTION = "module.jms.endpoint.%s.bpmn.action";
  private static final String CONFIGURATION_KEY_JMS_ENDPOINT_BPMN_DEFINITION = "module.jms.endpoint.%s.bpmn.definition_identifier";

  private static final String MAPPING_NONE = "none";
  private static final String MAPPING_JSON = "json";
  
  private static final char PROPERTY_KEY_CASE_SEPARATOR = '_';
  private static final String PROPERTY_SEPARATOR = ".";
  private static final String SEPARATOR_COMMA = "\\s*,\\s*";
  
  private static final String MESSAGE_HANDLE_METHOD_NAME = "handle";
  
  private final DefaultMessageHandlerMethodFactory methodFactory = new DefaultMessageHandlerMethodFactory();
  
  private boolean ignoreErroneousEndpoint = true;
  
  @Resource(name = "configurationProperties")
  Properties configuration;
  
  @Inject
  MessageListenerFactory listenerFactory;
  
  @Override
  public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
    initializeFactory(methodFactory);

    /*
     As a single container factory setup can be fairly common, the containerFactory attribute can 
     be omitted if a default one has either been set or discovered. 
     By default, we look up for a bean named jmsListenerContainerFactory.
     */
    if (configuration == null) {
      logger.warn("Configuration file ('configuration.properties') is missing.");
      return;
    }

    // Check if any endpoint has been defined
    String endpointsStr = StringUtils.trim(configuration.getProperty(CONFIGURATION_KEY_JMS_ENDPOINTS));
    if (StringUtils.isEmpty(endpointsStr)) {
      logger.warn("No endpoint has been defined.");
      return;
    }

    // Parse endpoints ids
    String[] endpoints = stringAsArray(endpointsStr);
    
    String defaultLogin = configuration.getProperty(CONFIGURATION_KEY_JMS_LOGIN);
    String defaultPassword = configuration.getProperty(CONFIGURATION_KEY_JMS_PASSWORD);

    // Configure each endpoint
    for (String endpointId : endpoints) {
      JmsListenerEndpoint endpoint;
      try {
        endpoint = configureEndpoint(endpointId, defaultLogin, defaultPassword);
      } catch (Exception ex) {
        StringBuilder errorMessage = new StringBuilder();
        errorMessage.append("Failed to configure endpoint '").append(endpointId).append("'.");
        if (ignoreErroneousEndpoint) {
          errorMessage.append(" Go to the next one.");
          logger.warn(errorMessage.toString(), ex);
          continue;
        }
        else {
          throw new JMSModuleException(errorMessage.toString(), ex);
        }
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
    endpoint.setDestination(getRequiredEndpointProperty(endpointId, CONFIGURATION_KEY_JMS_ENDPOINT_DESTINATION));
    
    String selector = getEndpointProperty(endpointId, CONFIGURATION_KEY_JMS_ENDPOINT_SELECTOR);
    if (!StringUtils.isEmpty(selector)) {
      endpoint.setSelector(selector);
    }

    // Define the receiver
    //DefltMessageListener receiver = new DefaultMessageListener();
    String login = getEndpointProperty(endpointId, CONFIGURATION_KEY_JMS_ENDPOINT_LOGIN, defaultLogin);
    String password = getEndpointProperty(endpointId, CONFIGURATION_KEY_JMS_ENDPOINT_PASSWORD, defaultPassword);
    String definitionsIdentifier = getRequiredEndpointProperty(endpointId, CONFIGURATION_KEY_JMS_ENDPOINT_BPMN_DEFINITION);
    
    String actionAsString = getEndpointProperty(endpointId, CONFIGURATION_KEY_JMS_ENDPOINT_BPMN_ACTION);
    BpmnAction action = BpmnAction.INSTANTIATE;
    try {
      action = BpmnAction.parse(actionAsString);
    } catch (IllegalArgumentException wrongArgument) {
      
      StringBuilder errorMessage = new StringBuilder();
      errorMessage.append("Action ('").append(actionAsString).append("') unknown for endpoint '");
      errorMessage.append(endpointId).append(" : only 'instantiate' or 'signal' are allowed.");
      if (ignoreErroneousEndpoint) {
        errorMessage.append(" Instantiate will be used.");
        logger.warn(errorMessage.toString());
      }
      else {
        throw new IllegalArgumentException(errorMessage.toString());
      }
    }
    
    Map<String, ? extends Object> listenerProperties = subsetToCamelCase(configuration, getEndpointBpmnPropertiesPrefix(endpointId), true);
    AbstractW4MessageListener listener = listenerFactory.getListener(action, login, password, definitionsIdentifier, listenerProperties);

    // Define the listener
    //MessageListenerAdapter listener = new MessageListenerAdapter(receiver);
    MessagingMessageListenerAdapter listenerAdapter = new DisposableMessagingMessageListenerAdapter();
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
    String mapping = getEndpointProperty(endpointId, CONFIGURATION_KEY_JMS_ENDPOINT_MAPPING);
    if (MAPPING_JSON.equalsIgnoreCase(mapping)) {
      MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
      converter.setTypeIdPropertyName(JMS_PROPERTY_DTO_CLASSNAME);
      listener.setMessageConverter(converter);
    } else if (mapping != null && !MAPPING_NONE.equalsIgnoreCase(mapping)) {
      StringBuilder errorMessage = new StringBuilder();
      errorMessage.append("Mapping type ('").append(mapping);
      errorMessage.append("') unknown for endpoint ").append(endpointId).append(" : only 'none' or 'json' are allowed.");
      if (ignoreErroneousEndpoint) {
        errorMessage.append(" None mapping will be used.");
        logger.warn(errorMessage.toString());
      }
      else {
        throw new IllegalArgumentException(errorMessage.toString());
      }
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
  private String getEndpointProperty(String endpointId, String key) {
    return getEndpointProperty(endpointId, key, false);
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
  private String getEndpointProperty(String endpointId, String key, String defaultValue) {
    String value = getEndpointProperty(endpointId, key, false);
    if (StringUtils.isEmpty(value)) {
      return defaultValue;
    }
    return value;
  }
  
  /**
   * Get an endpoint property
   *
   * @param endpointId id of the endpoint
   * @param suffix property key suffix
   * @param mandatory is the property mandatory
   * @return String value of the property
   */
  private String getEndpointProperty(String endpointId, String key, boolean mandatory) {
    String propertyKey = String.format(key, endpointId);
    String propertyValue;
    if (mandatory) {
      propertyValue = getRequiredProperty(configuration, propertyKey);
    } else {
      propertyValue = configuration.getProperty(propertyKey);
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
  private String getRequiredEndpointProperty(String endpointId, String key) {
    return getEndpointProperty(endpointId, key, true);
  }

  /**
   * Get endpoint properties prefix realated to bpmn configuration.
   * Used to populate listener specific properties (eg.: signal_identifier)
   * @param endpointId id of the endpoint
   * @return bpmn properties prefix for this endpoint
   */
  private String getEndpointBpmnPropertiesPrefix(String endpointId) {
    return  String.format(CONFIGURATION_KEY_JMS_ENDPOINT_BPMN_PREFIX, endpointId);
  }
  
  /**
   * Initialize message handler method factory
   * @param factory DefaultMessageHandlerMethodFactory
   */
  private void initializeFactory(DefaultMessageHandlerMethodFactory factory) {
    //factory.setBeanFactory(new StaticListableBeanFactory());
    factory.afterPropertiesSet();
  }
  
  /**
   * Get the handle method that deals with JMS messages
   * @return Method the handle method
   */
  private Method getHandleMethod() {
    return ReflectionUtils.findMethod(AbstractW4MessageListener.class, MESSAGE_HANDLE_METHOD_NAME, Message.class, 
            Session.class, javax.jms.Message.class);
  }

  /**
   * Should erroneous endpoint configuration be ignored?
   * @param ignore true to ignore erroneous endpoint, false otherwise.
   */
  public void setIgnoreErroneousEndpoint(boolean ignore) {
    this.ignoreErroneousEndpoint = ignore;
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
      throw new IllegalStateException(String.format("Required key '%s' not found", key));
    }
    return value;
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
    return StringUtils.uncapitalize(StringUtils.remove(
            WordUtils.capitalizeFully(text, PROPERTY_KEY_CASE_SEPARATOR), PROPERTY_KEY_CASE_SEPARATOR)
    );
  }
  
  public class DisposableMessagingMessageListenerAdapter extends MessagingMessageListenerAdapter implements DisposableBean {
    private InvocableHandlerMethod handlerMethod;
    
	/**
	 * Set the {@link InvocableHandlerMethod} to use to invoke the method
	 * processing an incoming {@link javax.jms.Message}.
     * @param handlerMethod method that handles messages
	 */
    @Override
	public void setHandlerMethod(InvocableHandlerMethod handlerMethod) {
		super.setHandlerMethod(handlerMethod);
        this.handlerMethod = handlerMethod;
	}
    
    @Override
    public void destroy() throws Exception {
      if (handlerMethod.getBean() instanceof DisposableBean) {
        ((DisposableBean)handlerMethod.getBean()).destroy();
      }
    }
    
  }
}
