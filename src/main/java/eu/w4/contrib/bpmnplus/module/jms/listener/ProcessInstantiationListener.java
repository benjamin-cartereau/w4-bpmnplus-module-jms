package eu.w4.contrib.bpmnplus.module.jms.listener;

import eu.w4.common.exception.CheckedException;
import eu.w4.contrib.bpmnplus.module.jms.exception.JMSModuleException;
import eu.w4.engine.client.bpmn.w4.collaboration.CollaborationIdentifier;
import eu.w4.engine.client.bpmn.w4.process.ProcessIdentifier;
import eu.w4.engine.client.bpmn.w4.runtime.ProcessInstanceIdentifier;
import eu.w4.engine.client.service.ObjectFactory;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Message processor that will instantiate W4 process.
 */
public class ProcessInstantiationListener extends AbstractW4MessageListener {
  private static final Logger logger = LogManager.getLogger();
  
  private String processInstanceNamePrefix;

  // Identifiers names
  private String collaborationIdentifierName;
  private String processIdentifierName;

  // Identifiers
  private CollaborationIdentifier collaborationIdentifier;
  private ProcessIdentifier processIdentifier;
  
  @Override
  protected String doProcessW4Action(Principal principal, Map<String, Object> properties, Map<String, Object> dataEntries) {
    // Info about process to instantiate and passed data entries
    logger.info("Instantiate process ({}) for data entries: {}", processIdentifier.getId(), dataEntries.toString());

    ProcessInstanceIdentifier processInstanceId = null;

    try {
      long timeBefore = System.currentTimeMillis();
      
      // INSTANTIATE the PROCESS
      processInstanceId = getProcessService().instantiateProcess(principal, collaborationIdentifier, processIdentifier, processInstanceNamePrefix, Boolean.TRUE, dataEntries, Boolean.TRUE);
      
      logger.debug("Process ({}) instantiated in {}ms", processInstanceId.getId(), System.currentTimeMillis() - timeBefore);
    } catch (CheckedException che) {
      logger.error(che.getMessage(), che);
      throw new JMSModuleException(che.getMessage(), che);
    } catch (RemoteException ree) {
      logger.error(ree.getMessage(), ree);
      throw new JMSModuleException(ree.getMessage(), ree);
    }

    //return processInstanceId.getId().toString();
    return null;
  }
  
  @Override
  public void afterPropertiesSet() throws Exception {
    super.afterPropertiesSet();
    
    assert StringUtils.isNoneBlank(processIdentifierName) : "Process identifier must be set";
    
    ObjectFactory factory = engineService.getObjectFactory();
    
    this.processIdentifier = factory.newProcessIdentifier();
    this.processIdentifier.setId(processIdentifierName);
    this.processIdentifier.setDefinitionsIdentifier(definitionsIdentifier);
    
    if (StringUtils.isEmpty(collaborationIdentifierName)) {
      this.collaborationIdentifier = null;
    } else {
      this.collaborationIdentifier = factory.newCollaborationIdentifier();
      this.collaborationIdentifier.setId(collaborationIdentifierName);
      this.collaborationIdentifier.setDefinitionsIdentifier(definitionsIdentifier);
    }
  }
  
  public void setProcessInstanceNamePrefix(String processInstanceNamePrefix) {
    this.processInstanceNamePrefix = processInstanceNamePrefix;
  }

  public void setCollaborationIdentifier(String collaborationIdentifierName) {
    this.collaborationIdentifierName = collaborationIdentifierName;
  }

  //@Required
  public void setProcessIdentifier(String processIdentifierName) {
    this.processIdentifierName = processIdentifierName;
  }
}
