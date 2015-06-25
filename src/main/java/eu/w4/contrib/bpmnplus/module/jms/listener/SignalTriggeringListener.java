package eu.w4.contrib.bpmnplus.module.jms.listener;

import eu.w4.common.exception.CheckedException;
import eu.w4.contrib.bpmnplus.module.jms.exception.JMSModuleException;
import eu.w4.engine.client.bpmn.w4.events.SignalIdentifier;
import eu.w4.engine.client.service.ObjectFactory;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Message processor that will trigger a BPMN signal.
 */
public class SignalTriggeringListener extends AbstractW4MessageListener {

  private static final Logger logger = LogManager.getLogger();

  // Property (message header) used to get dynamic message name
  private static final String PROPERTY_SIGNAL_NAME = "SignalName";
  
  // Signal identification
  private String signalId;
  private String defaultSignalName;
  private SignalIdentifier signalIdentifier;

  @Override
  protected String doProcessW4Action(Principal principal, Map<String, Object> properties, Map<String, Object> dataEntries) {
    if (dataEntries != null && dataEntries.size() > 1) {
      // FIXME we could do better!?
      throw new IllegalArgumentException("There is more than 1 data entry. Don't know what to signal...");
    }
    
    String signalName = defaultSignalName;
    if (properties.containsKey(PROPERTY_SIGNAL_NAME)) {
      signalName = (String) properties.get(PROPERTY_SIGNAL_NAME);
    }
    else {
      // If not default signal name is set, it should be passed through properties
      if (StringUtils.isBlank(signalName)) {
        throw new JMSModuleException("Cannot trigger signal since no signal name has been received.");
      }
    }
    
    // Info about process to instantiate and passed data entries
    logger.info("Trigger signal ({}/{}) with payload: {}", signalIdentifier.getId(), signalName, dataEntries);
    
    // Get 1st dataEntry if any
    Object payload = null;
    if (dataEntries != null && dataEntries.size() > 0) {
      payload = dataEntries.values().toArray()[0];
    }
    
    try {
      long timeBefore = System.currentTimeMillis();
      
      engineService.getEventService().triggerSignal(principal, signalIdentifier, signalName, payload);
      
      logger.debug("Signal triggered in {}ms", System.currentTimeMillis() - timeBefore);
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
    
    assert StringUtils.isNoneBlank(signalId) : "Signal id must be set";
    
    logger.debug("signalId={}-defaultSignalName={}", signalId, defaultSignalName);
    
    ObjectFactory factory = engineService.getObjectFactory();
    this.signalIdentifier = factory.newSignalIdentifier();
    this.signalIdentifier.setDefinitionsIdentifier(definitionsIdentifier);
    this.signalIdentifier.setId(signalId);
  }
  
  public void setSignalIdentifier(String signalId) {
    this.signalId = signalId;
  }

  public void setSignalName(String signalName) {
    this.defaultSignalName = signalName;
  }
}
