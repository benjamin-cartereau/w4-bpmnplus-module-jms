package eu.w4.contrib.bpmnplus.module.jms.listener;

import eu.w4.common.exception.CheckedException;
import eu.w4.contrib.bpmnplus.module.jms.exception.JMSModuleException;
import eu.w4.engine.client.bpmn.SignalNotFoundException;
import eu.w4.engine.client.bpmn.w4.events.SignalIdentifier;
import eu.w4.engine.client.bpmn.w4.infrastructure.DefinitionsFilter;
import eu.w4.engine.client.bpmn.w4.infrastructure.DefinitionsIdentifier;
import eu.w4.engine.client.bpmn.w4.infrastructure.DefinitionsInfo;
import eu.w4.engine.client.bpmn.w4.infrastructure.DefinitionsInfoFilter;
import eu.w4.engine.client.service.ObjectFactory;
import java.rmi.RemoteException;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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
  //private SignalIdentifier lastVersionSignalIdentifier;

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
    logger.info("Trigger signal ({}) with payload: {}", signalName, dataEntries);
    
    // Get 1st dataEntry if any
    Object payload = null;
    if (dataEntries != null && dataEntries.size() > 0) {
      payload = dataEntries.values().toArray()[0];
    }
    
    try {
      long timeBefore = System.currentTimeMillis();
      
      // Retrieve all versions of the signal identifier
      Collection<SignalIdentifier> signalIdentifiers = getSignalIdentifiers(principal);
      
      // Trigger the signal for all versions of the identifier
      //   TODO : could be an option or rely on a version defined in message header
      int versionsSent = 0;
      for (SignalIdentifier signalIdentifier : signalIdentifiers) {
        logger.debug("Trigger signal for identifier {}, version {}", signalIdentifier.getId(), signalIdentifier.getDefinitionsIdentifier().getVersion());
        try {
          engineService.getEventService().triggerSignal(principal, signalIdentifier, signalName, payload);
          versionsSent++;
        } catch (SignalNotFoundException snfe) {
          logger.debug("    signal not found (version {})", signalIdentifier.getDefinitionsIdentifier().getVersion());
        }
      }
      
      logger.debug("Signal triggered (for {} versions) in {}ms", versionsSent, System.currentTimeMillis() - timeBefore);
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
    
//    ObjectFactory factory = engineService.getObjectFactory();
//    this.lastVersionSignalIdentifier = factory.newSignalIdentifier();
//    this.lastVersionSignalIdentifier.setDefinitionsIdentifier(lastVersionDefIdentifier);
//    this.lastVersionSignalIdentifier.setId(signalId);
  }
  
  public void setSignalIdentifier(String signalId) {
    this.signalId = signalId;
  }

  public void setSignalName(String signalName) {
    this.defaultSignalName = signalName;
  }

  /**
   * Retrieve all the versions of a signal identifier
   * @param principal Principal of the connected user
   */
  private Collection<SignalIdentifier> getSignalIdentifiers(Principal principal) throws CheckedException, RemoteException {
    List<SignalIdentifier> signalIdentifiers = new ArrayList<SignalIdentifier>();
    
    List<DefinitionsInfo> definitionsInfos = null;
    try {
      DefinitionsInfoFilter dif = engineService.getObjectFactory().newDefinitionsInfoFilter();
      DefinitionsFilter df = engineService.getObjectFactory().newDefinitionsFilter();
      df.definitionsIdLike(definitionsIdentifierName);
      dif.and(df);
      
      definitionsInfos = engineService.getDefinitionsService().searchDefinitionsInfos(principal, null, dif, null, 0, Integer.MAX_VALUE);
    } catch (CheckedException cex) {
      logger.error(cex.getMessage(), cex);
      throw cex;
    } catch (RemoteException rex) {
      logger.error(rex.getMessage(), rex);
      throw rex;
    }
    
    if (definitionsInfos != null) {
      logger.debug("{} definitions (versions) found", definitionsInfos.size());
      if (logger.isDebugEnabled()) {
        for (DefinitionsInfo definitionInfo : definitionsInfos) {
          logger.debug("\tDefinition id:{} - version:{}", definitionInfo.getDefinitionsIdentifier().getId(), definitionInfo.getDefinitionsIdentifier().getVersion());
        }
      }
    }
    else {
      logger.warn("No definition found for name '{}'", definitionsIdentifierName);
    }
    
    ObjectFactory factory = engineService.getObjectFactory();
    for (DefinitionsInfo definitionInfo : definitionsInfos) {
      DefinitionsIdentifier definitionIdentifier = factory.newDefinitionsIdentifier();
      definitionIdentifier.setId(definitionInfo.getDefinitionsIdentifier().getId());
      definitionIdentifier.setVersion(definitionInfo.getDefinitionsIdentifier().getVersion());
      
      SignalIdentifier signalIdentifier = factory.newSignalIdentifier();
      signalIdentifier.setDefinitionsIdentifier(definitionIdentifier);
      signalIdentifier.setId(signalId);
      
      signalIdentifiers.add(signalIdentifier);
    }
    
    return signalIdentifiers;
  }
}
