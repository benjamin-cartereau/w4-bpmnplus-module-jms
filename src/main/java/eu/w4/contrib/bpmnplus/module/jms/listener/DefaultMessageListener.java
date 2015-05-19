package eu.w4.contrib.bpmnplus.module.jms.listener;

import eu.w4.common.exception.CheckedException;
import eu.w4.contrib.bpmnplus.module.jms.exception.JMSModuleException;
import eu.w4.engine.client.bpmn.w4.collaboration.CollaborationIdentifier;
import eu.w4.engine.client.bpmn.w4.infrastructure.DefinitionsIdentifier;
import eu.w4.engine.client.bpmn.w4.process.ProcessIdentifier;
import eu.w4.engine.client.bpmn.w4.runtime.ProcessInstanceIdentifier;
import eu.w4.engine.client.service.EngineService;
import eu.w4.engine.client.service.ObjectFactory;
import eu.w4.engine.client.service.ProcessService;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.HashMap;
import java.util.Map;
import ma.glasnost.orika.BoundMapperFacade;
import ma.glasnost.orika.MapperFactory;
import ma.glasnost.orika.impl.DefaultMapperFactory;
import ma.glasnost.orika.metadata.Type;
import ma.glasnost.orika.metadata.TypeBuilder;
import ma.glasnost.orika.metadata.TypeFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.StringUtils;

/**
 * Default Message Listener for JMS Module. Should be thread safe.
 */
public class DefaultMessageListener {

    private final Log logger = LogFactory.getLog(DefaultMessageListener.class);

    // Service to communicate with W4 BPMN+ engine
    private EngineService engineService;

    // Credentials to authenticate on W4 BPMN+ engine
    private String enginePassword;
    private String engineLogin;

    // Processes instances name prefix
    private String processInstanceNamePrefix;

    // Process definition names
    private String definitionsIdentifierName;
    private String collaborationIdentifierName;
    private String processIdentifierName;

    // Process identifiers
    private CollaborationIdentifier collaborationIdentifier;
    private ProcessIdentifier processIdentifier;

    // Only one data entry
    private String dataEntryId;

    // TODO : several data entries
    private Map<String, String> dataEntriesMapping;

    /**
     * Handle JMS string message
     *
     * @param message the String message
     * @return String message to send back or null
     */
    final public String handleMessage(String message) {
        if (logger.isDebugEnabled()) {
            StringBuilder messageText = new StringBuilder();
            messageText.append("Received simple string message: ").append(message);
            logger.debug(messageText.toString());
        }

        // Build data entries map
        Map<String, Object> dataEntries = new HashMap<String, Object>();
        dataEntries.put(dataEntryId, message);

        return processMessage(dataEntries);
    }

    /**
     * Handle JMS object message
     *
     * @param dto Object received (or mapped) through JMS message
     * @return String message to send back or null
     */
    final public String handleMessage(Object dto) {
        if (logger.isDebugEnabled()) {
            StringBuilder messageText = new StringBuilder();
            messageText.append("Received object message: ").append(dto.toString());
            logger.debug(messageText.toString());
        }

        // Build data entries map
        Map<String, Object> dataEntries = new HashMap<String, Object>();
        // ...apply XSD mapping
        dataEntries.put(dataEntryId, mapObjectToMap(dto));

        return processMessage(dataEntries);
    }
    
    /**
     * Convert an object to a Map
     * @param dto DTO object to convert
     * @return Map&lt;String, Object&gt; converted object
     */
    protected Map<String, Object> mapObjectToMap(Object dto) {
        Type dtoType = TypeFactory.valueOf(dto.getClass());
        Type mapType = new TypeBuilder<Map<String,Object>>(){}.build();
        
        MapperFactory mapperFactory = new DefaultMapperFactory.Builder().build();
        mapperFactory.classMap(dtoType, mapType).byDefault().register();
        BoundMapperFacade boundMapper = mapperFactory.getMapperFacade(dtoType, mapType);
        Map<String,Object> convertedObject = (Map<String,Object>) boundMapper.map(dto);
        
        return convertedObject;
    }
    
    /**
     * Common method to process a message by instantiating a process
     * @param dataEntries to pass to the process instance
     * @return ProcessInstanceIdentifier if needed
     */
    private String processMessage(Map<String, Object> dataEntries) {
        // Processing
        long timeBefore = System.currentTimeMillis();
        ProcessInstanceIdentifier processInstanceId = doProcess(dataEntries);
        if (logger.isDebugEnabled()) {
            logger.debug("Message processed in " + (System.currentTimeMillis() - timeBefore) + "ms");
        }

        // Could return something (eg. process instance id) if needed. If return
        //  value is not null, reply-to channel will be used
        // return processInstanceId;
        return null;
    }

    /**
     * Process message by instantiating a process passing dataEntries
     * @param dataEntries to pass to the process instance
     * @return ProcessInstanceIdentifier of the instantiated process
     */
    private ProcessInstanceIdentifier doProcess(Map<String, Object> dataEntries) {
        // LOGIN
        Principal principal = login();
        
        // Get process service
        ProcessService processService = getProcessService();

        // Init process identifiers if needed
        if (processIdentifier == null) {
            initProcessIdentifiers(engineService);
        }

        // Instantiate Process
        ProcessInstanceIdentifier processInstanceId = instantiateProcess(processService, principal, dataEntries);

        // LOGOUT
        logout(principal);

        return processInstanceId;
    }

    /**
     * Instantiate a W4 process
     *
     * @param processService w4 process engine
     * @param principal user principal
     * @param dataEntryId id of the data entry
     */
    private ProcessInstanceIdentifier instantiateProcess(ProcessService processService, Principal principal, Map<String, Object> dataEntries) {
        // Info about process to instantiate and passed data entries
        if (logger.isInfoEnabled()) {
            StringBuilder processInfo = new StringBuilder();
            processInfo.append("Instantiate process (").append(processIdentifier.getId());
            processInfo.append(") for data entries:").append(dataEntries.toString());
            logger.info(processInfo.toString());
        }

        ProcessInstanceIdentifier processInstanceId = null;

        try {
            long timeBefore = System.currentTimeMillis();
            processInstanceId = processService.instantiateProcess(principal, collaborationIdentifier, processIdentifier, processInstanceNamePrefix, Boolean.TRUE, dataEntries, Boolean.TRUE);
            if (logger.isDebugEnabled()) {
                logger.debug("Process (" + processInstanceId.getId() + ") instantiated in " + (System.currentTimeMillis() - timeBefore) + "ms");
            }
        } catch (CheckedException che) {
            logger.error(che.getMessage(), che);
            throw new JMSModuleException(che.getMessage(), che);
        } catch (RemoteException ree) {
            logger.error(ree.getMessage(), ree);
            throw new JMSModuleException(ree.getMessage(), ree);
        }

        return processInstanceId;
    }

    /**
     * Get W4 process engine service
     *
     * @return ProcessService W4 process service
     */
    private ProcessService getProcessService() {
        ProcessService processService = null;
        try {
            processService = engineService.getProcessService();
        } catch (CheckedException ex) {
            logger.error(ex.getMessage(), ex);
        } catch (RemoteException ex) {
            logger.error(ex.getMessage(), ex);
        }
        return processService;
    }

    /**
     * Log on to W4 BPMN+ Engine
     *
     * @return Principal authenticated user principal
     */
    private Principal login() {
        Principal principal = null;
        try {
            principal = engineService.getAuthenticationService().login(engineLogin, enginePassword);
        } catch (CheckedException ex) {
            logger.error("Error on login", ex);
        } catch (RemoteException ex) {
            logger.error("Error on login", ex);
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
        } catch (CheckedException ex) {
            logger.error("Error on logout", ex);
        } catch (RemoteException ex) {
            logger.error("Error on logout", ex);
        }
    }

    /**
     * Initialize processes identifiers
     *
     * @param engineService W4 engine service
     */
    private synchronized void initProcessIdentifiers(EngineService engineService) {
        if (this.processIdentifier == null) {
            try {
                ObjectFactory factory = engineService.getObjectFactory();

                DefinitionsIdentifier definitionsIdentifier = factory.newDefinitionsIdentifier();
                definitionsIdentifier.setId(definitionsIdentifierName);

                if (StringUtils.isEmpty(collaborationIdentifierName)) {
                    this.collaborationIdentifier = null;
                } else {
                    this.collaborationIdentifier = factory.newCollaborationIdentifier();
                    this.collaborationIdentifier.setId(collaborationIdentifierName);
                    this.collaborationIdentifier.setDefinitionsIdentifier(definitionsIdentifier);
                }

                this.processIdentifier = factory.newProcessIdentifier();
                this.processIdentifier.setId(processIdentifierName);
                this.processIdentifier.setDefinitionsIdentifier(definitionsIdentifier);
            } catch (CheckedException cex) {
                logger.error(cex.getMessage(), cex);
            } catch (RemoteException ree) {
                logger.error(ree.getMessage(), ree);
            }
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
     * Setter for process instance name prefix
     *
     * @param processInstanceNamePrefix the processInstanceNamePrefix to set
     */
    public void setProcessInstanceNamePrefix(String processInstanceNamePrefix) {
        this.processInstanceNamePrefix = processInstanceNamePrefix;
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
     * Setter for collaboration identifier name
     *
     * @param collaborationIdentifier the collaborationIdentifier to set
     */
    public void setCollaborationIdentifier(String collaborationIdentifier) {
        this.collaborationIdentifierName = collaborationIdentifier;
    }

    /**
     * Setter for process identifier name
     *
     * @param processIdentifier the processIdentifier to set
     */
    public void setProcessIdentifier(String processIdentifier) {
        this.processIdentifierName = processIdentifier;
    }

    /**
     * Add a data entry mapping
     *
     * @param jms JMS message map entry id
     * @param w4 W4 data entry id
     */
    public void addDataEntryMapping(String jms, String w4) {
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
