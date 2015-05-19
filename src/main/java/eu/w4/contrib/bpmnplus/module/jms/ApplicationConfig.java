package eu.w4.contrib.bpmnplus.module.jms;

import eu.w4.contrib.bpmnplus.module.jms.listener.DefaultMessageListener;
import eu.w4.engine.client.service.EngineService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.env.Environment;
import org.springframework.jms.annotation.JmsListenerConfigurer;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.jms.config.SimpleJmsListenerEndpoint;
import org.springframework.jms.listener.adapter.MessageListenerAdapter;
import org.springframework.jms.support.converter.MappingJackson2MessageConverter;
import org.springframework.util.StringUtils;

/**
 * Declaration and dynamic configuration of JMS listeners
 */
//@EnableJms
@Configuration
@PropertySource(value = "classpath:configuration.properties", ignoreResourceNotFound = true)
public class ApplicationConfig implements JmsListenerConfigurer {
    private final Log logger = LogFactory.getLog(ApplicationConfig.class);
    
    // Property name in JMS message defining fully qualified class name of the DTO
    private static final String JSON_TYPE_JMS_PROPERTY_NAME = "ClassName";
    
    private static final String PROPERTY_JMS_ENDPOINTS = "module.jms.endpoints";
    private static final String PROPERTY_JMS_LOGIN = "module.jms.principal.login";
    private static final String PROPERTY_JMS_PASSWORD = "module.jms.principal.password";
    private static final String PROPERTY_JMS_ENDPOINT_MAPPING = "mapping";
    private static final String PROPERTY_JMS_ENDPOINT_BPMN_DATAENTRY = "bpmn.data_entry_id";
    private static final String PROPERTY_JMS_ENDPOINT_BPMN_NAME_PREFIX = "bpmn.process_instance_name_prefix";
    private static final String PROPERTY_JMS_ENDPOINT_BPMN_PROCESS = "bpmn.process_identifier";
    private static final String PROPERTY_JMS_ENDPOINT_BPMN_COLLABORATION = "bpmn.collaboration_identifier";
    private static final String PROPERTY_JMS_ENDPOINT_BPMN_DEFINITION = "bpmn.definition_identifier";
    private static final String PROPERTY_JMS_ENDPOINT_PASSWORD = "principal.password";
    private static final String PROPERTY_JMS_ENDPOINT_LOGIN = "principal.login";
    private static final String PROPERTY_JMS_ENDPOINT_SELECTOR = "selector";
    private static final String PROPERTY_JMS_ENDPOINT_DESTINATION = "destination";
    private static final String MAPPING_NONE = "none";
    private static final String MAPPING_JSON = "json";
    
    private static final String SEPARATOR_COMMA = "\\s*,\\s*";
    
    @Autowired
    Environment env;
    
    @Override
    public void configureJmsListeners(JmsListenerEndpointRegistrar registrar) {
        /*
        As a single container factory setup can be fairly common, the containerFactory attribute can 
            be omitted if a default one has either been set or discovered. 
        By default, we look up for a bean named jmsListenerContainerFactory.
        */
        String endpointsStr = StringUtils.trimWhitespace(env.getProperty(PROPERTY_JMS_ENDPOINTS));
        
        // Check if any endpoint has been defined
        if (StringUtils.isEmpty(endpointsStr)) {
            logger.warn("No endpoint has been defined or configuration file (configuration.properties) is missing");
            return;
        }
        
        // Parse endpoints ids
        String[] endpoints = stringAsArray(endpointsStr);
        
        String defaultLogin = env.getProperty(PROPERTY_JMS_LOGIN);
        String defaultPassword = env.getProperty(PROPERTY_JMS_PASSWORD);
        
        // Configure each endpoint
        for (String endpointId : endpoints) {
            JmsListenerEndpoint endpoint = configureEndpoint(endpointId, defaultLogin, defaultPassword);
            // The default registrar is already populated with the container factory
            //  bean named jmsListenerContainerFactory by default
            //registrar.setContainerFactoryBeanName();
            registrar.registerEndpoint(endpoint);
        }
    }
    
    /**
     * Configure one endpoint
     * @param endpointId
     * @param defaultLogin
     * @param defaultPassword
     * @return Configured endpoint
     */
    private JmsListenerEndpoint configureEndpoint(String endpointId, String defaultLogin, String defaultPassword) {
        // Define the endpoint
        SimpleJmsListenerEndpoint endpoint = new SimpleJmsListenerEndpoint();
        endpoint.setId(endpointId);
        endpoint.setDestination(getRequiredEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_DESTINATION));
        
        String selector = getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_SELECTOR);
        if (!StringUtils.isEmpty(selector)) {
            endpoint.setSelector(selector);
        }
        
        // Define the receiver
        DefaultMessageListener receiver = new DefaultMessageListener();
        receiver.setEngineService(env.getProperty(JMSModule.PROPERTY_ENGINE_SERVICE, EngineService.class));
        receiver.setEngineLogin(getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_LOGIN, defaultLogin));
        receiver.setEnginePassword(getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_PASSWORD, defaultPassword));
        
        receiver.setDefinitionsIdentifier(getRequiredEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_BPMN_DEFINITION));
        receiver.setCollaborationIdentifier(getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_BPMN_COLLABORATION));
        receiver.setProcessIdentifier(getRequiredEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_BPMN_PROCESS));
        
        receiver.setProcessInstanceNamePrefix(getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_BPMN_NAME_PREFIX));
        
        String dataEntryId = getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_BPMN_DATAENTRY);
        if (!StringUtils.isEmpty(dataEntryId)) {
            receiver.setDataEntryId(dataEntryId);
        }
        else {
            String dataEntriesStr = getEndpointProperty(endpointId, "bpmn.data_entries");
            String[] dataEntries = stringAsArray(dataEntriesStr);
            for (String dataEntryRef : dataEntries) {
                String dataEntryPrefix = "bpmn.data_entry."+dataEntryRef;
                String jmsId = getRequiredEndpointProperty(endpointId, dataEntryPrefix+".id_jms");
                String w4Id = getRequiredEndpointProperty(endpointId, dataEntryPrefix+".id_w4");
                receiver.addDataEntryMapping(jmsId, w4Id);
            }
        }
            
        // Define the listener
        MessageListenerAdapter listener = new MessageListenerAdapter(receiver);
        
        // Mapping needed?
        String mapping = getEndpointProperty(endpointId, PROPERTY_JMS_ENDPOINT_MAPPING);
        if (MAPPING_JSON.equalsIgnoreCase(mapping)) {
            MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
            converter.setTypeIdPropertyName(JSON_TYPE_JMS_PROPERTY_NAME);    
            listener.setMessageConverter(converter);
        }
        else if (mapping!=null && !MAPPING_NONE.equalsIgnoreCase(mapping)) {
            logger.warn("Mapping type unknown for endpoint "+endpointId+" : only 'none' or 'json' are allowed.");
        }
        
        endpoint.setMessageListener(listener);
        
        return endpoint;
    }
    
    /**
     * Split a String to a List of strings
     * @param listAsString list as string (comma separated)
     * @return String[] array of Strings
     */
    private String[] stringAsArray(String listAsString) {
        return listAsString.split(SEPARATOR_COMMA);
    }
    
    /**
     * Get a non mandatory endpoint property
     * @param endpointId id of the endpoint
     * @param suffix property key suffix
     * @return String value of the property, null if not found
     */
    private String getEndpointProperty(String endpointId, String suffix) {
        return getEndpointProperty(endpointId, suffix, false);
    }
    
    /**
     * Get a non mandatory endpoint property. If not found, default value is returned
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
     * Get an endpoint property
     * @param endpointId id of the endpoint
     * @param suffix property key suffix
     * @param mandatory is the property mandatory
     * @return String value of the property
     */
    private String getEndpointProperty(String endpointId, String suffix, boolean mandatory) {
        StringBuilder propertyKey = new StringBuilder();
        propertyKey.append("module.jms.endpoint.");
        propertyKey.append(endpointId).append(".");
        propertyKey.append(suffix);
        String propertyValue;
        if (mandatory) {
            propertyValue = env.getRequiredProperty(propertyKey.toString());
        }
        else {
            propertyValue = env.getProperty(propertyKey.toString());
        }
        return StringUtils.trimWhitespace(propertyValue);
    }
    
    /**
     * Get a mandatory endpoint property
     * @param endpointId id of the endpoint
     * @param suffix property key suffix
     * @return String value of the property
     */
    private String getRequiredEndpointProperty(String endpointId, String suffix) {
        return getEndpointProperty(endpointId, suffix, true);
    }
    
}