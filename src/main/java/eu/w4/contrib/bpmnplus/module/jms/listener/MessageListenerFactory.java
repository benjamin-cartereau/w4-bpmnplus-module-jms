package eu.w4.contrib.bpmnplus.module.jms.listener;

import eu.w4.contrib.bpmnplus.module.jms.configuration.BpmnAction;
import eu.w4.contrib.bpmnplus.module.jms.identification.ConnectionManager;
import eu.w4.contrib.bpmnplus.module.jms.identification.User;
import eu.w4.engine.client.service.EngineService;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.commons.beanutils.BeanUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Factory that builds the appropriate message listener
 */
@Named
public class MessageListenerFactory {
  private static final Logger logger = LogManager.getLogger();
  
  @Inject
  EngineService engineService;
  
  @Inject
  ConnectionManager connectionManager;
  
  /**
   * Get the appropriate listener
   * @param action targeted BPMN action
   * @param engineLogin login
   * @param enginePassword password
   * @param definitionsIdentifier
   * @param properties specific action properties
   * @return AbstractW4MessageListener
   * @throws Exception if any error occured while building the listener
   */
  public AbstractW4MessageListener getListener(final BpmnAction action, 
          //final EngineService engineService, 
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
    listener.setDefinitionsIdentifier(definitionsIdentifier);
    
    User user = new User(engineLogin);
    user.setPassword(enginePassword);
    listener.setEngineUser(user);
    listener.setConnectionManager(connectionManager);
    
    logger.debug("Listener properties : {}", properties);
    
    BeanUtils.populate(listener, properties);
    
    listener.afterPropertiesSet();
    
    return listener;
  }
  
  /**
   * Set the engine service
   * @param engineService EngineService
   */
  public void setEngineService(EngineService engineService) {
    this.engineService = engineService;
  }
}
