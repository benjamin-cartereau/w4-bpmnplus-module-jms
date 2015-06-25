package eu.w4.contrib.bpmnplus.module.jms.listener;

import eu.w4.contrib.bpmnplus.module.jms.configuration.BpmnAction;
import eu.w4.engine.client.service.EngineService;
import java.util.Map;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Component;

/**
 * Factory that build the appropriate message listener
 */
@Component
public class MessageListenerFactory {
  private static final Logger logger = LogManager.getLogger();
  
  /**
   * Get the appropriate listener
   * @param action targeted BPMN action
   * @param engineService W4 engine service
   * @param engineLogin login
   * @param enginePassword password
   * @param definitionsIdentifier
   * @param properties specific action properties
   * @return AbstractW4MessageListener
   * @throws Exception if any error occured while building the listener
   */
  public AbstractW4MessageListener getListener(final BpmnAction action, 
          final EngineService engineService, 
          final String engineLogin, 
          final String enginePassword, 
          final String definitionsIdentifier,
          final Map<String, ? extends Object> properties) throws Exception {
    AbstractW4MessageListener listener = null;
    switch (action) {
      case INSTANTIATE:
        listener = new ProcessInstantiationListener();
        break;
      case SIGNAL:
        listener = new SignalTriggeringListener();
        break;
    }
    listener.setEngineService(engineService);
    listener.setEngineLogin(engineLogin);
    listener.setEnginePassword(enginePassword);
    listener.setDefinitionsIdentifier(definitionsIdentifier);
    
    logger.debug("Listener properties : {}", properties);
    
    BeanUtils.populate(listener, properties);
    
    listener.afterPropertiesSet();
    
    return listener;
  }
}
