package eu.w4.contrib.bpmnplus.module.jms.listener;

import eu.w4.common.exception.CheckedException;
import eu.w4.engine.client.bpmn.w4.events.SignalIdentifier;
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
public class SignalTriggeringListener extends AbstractW4MessageListener {

  private final Log logger = LogFactory.getLog(SignalTriggeringListener.class);

  // Signal identification
  private String signalId;
  private String signalName;
  private SignalIdentifier signalIdentifier;

  @Override
  protected String doProcessW4Action(Principal principal, Set<Map.Entry<String, Object>> properties, Map<String, Object> dataEntries) {
    if (dataEntries != null && dataEntries.size() > 1) {
      // FIXME we could do better!?
      throw new IllegalArgumentException("There is more than 1 data entry. Don't know what to signal...");
    }
    
    // Info about process to instantiate and passed data entries
    if (logger.isInfoEnabled()) {
      logger.info("Trigger signal (" + signalIdentifier.getId() + ") with payload:" + dataEntries.toString());
    }
    
    Object payload = null;
    if (dataEntries != null && dataEntries.size() > 0) {
      payload = dataEntries.values().toArray()[0];
    }
    
    try {
      long timeBefore = System.currentTimeMillis();
      
      engineService.getEventService().triggerSignal(principal, signalIdentifier, signalName, payload);
      
      if (logger.isDebugEnabled()) {
        logger.debug("Signal triggered in " + (System.currentTimeMillis() - timeBefore) + "ms");
      }
    } catch (CheckedException cex) {
      logger.error(cex.getMessage(), cex);
    } catch (RemoteException rex) {
      logger.error(rex.getMessage(), rex);
    }

    return null;
  }

  @Override
  public void afterPropertiesSet() throws Exception {
    super.afterPropertiesSet();

    ObjectFactory factory = engineService.getObjectFactory();
    if (StringUtils.isEmpty(signalId)) {
      this.signalIdentifier = null;
    } else {
      this.signalIdentifier = factory.newSignalIdentifier();
      this.signalIdentifier.setDefinitionsIdentifier(definitionsIdentifier);
      this.signalIdentifier.setId(signalId);
    }
  }
  
  public void setSignalIdentifier(String signalId) {
    this.signalId = signalId;
  }

  public void setSignalName(String signalName) {
    this.signalName = signalName;
  }
}
