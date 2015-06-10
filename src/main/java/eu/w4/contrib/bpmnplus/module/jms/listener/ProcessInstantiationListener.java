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
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.util.StringUtils;

/**
 *
 */
public class ProcessInstantiationListener extends AbstractW4MessageListener {
  private final Log logger = LogFactory.getLog(ProcessInstantiationListener.class);

  // Processes instances name prefix
  public void setProcessIdentifier(ProcessIdentifier processIdentifier) {
    this.processIdentifier = processIdentifier;
  }
  private String processInstanceNamePrefix;

  // Identifiers names
  private String collaborationIdentifierName;
  private String processIdentifierName;

  // Identifiers
  private CollaborationIdentifier collaborationIdentifier;
  private ProcessIdentifier processIdentifier;
  
  @Override
  protected String doProcessW4Action(Principal principal, Set<Map.Entry<String, Object>> properties, Map<String, Object> dataEntries) {
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
      
      // INSTANTIATE the PROCESS
      processInstanceId = getProcessService().instantiateProcess(principal, collaborationIdentifier, processIdentifier, processInstanceNamePrefix, Boolean.TRUE, dataEntries, Boolean.TRUE);
      
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

    //return processInstanceId.getId().toString();
    return null;
  }
  
  @Override
  public void afterPropertiesSet() throws Exception {
    super.afterPropertiesSet();
    
    ObjectFactory factory = engineService.getObjectFactory();
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
