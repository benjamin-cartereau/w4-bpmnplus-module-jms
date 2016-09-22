package eu.w4.contrib.bpmnplus.module.jms.listener.config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jmx.export.MBeanExporter;

/**
 * Subclass of {@code org.springframework.jms.config.DefaultJmsListenerContainerFactory} that register created containers 
 *  as a managed resource through an existing MBeanExporter.
 */
public class JmxAwareJmsListenerContainerFactory extends org.springframework.jms.config.DefaultJmsListenerContainerFactory implements DisposableBean {
  @Autowired(required = false)
  private MBeanExporter mbeanExporter;
  
  Set<Object> messageListeners = Collections.synchronizedSet(new HashSet<Object>());
  
  @Override
  public DefaultMessageListenerContainer createListenerContainer(JmsListenerEndpoint endpoint) {
    DefaultMessageListenerContainer container = super.createListenerContainer(endpoint);
    messageListeners.add(container.getMessageListener());
    if (mbeanExporter != null) {
      mbeanExporter.registerManagedResource(container);
    }
    return container;
  }

  @Override
  public void destroy() throws Exception {
    // Destroy all listeners properly
    for (Object listener : messageListeners) {
      if (listener instanceof DisposableBean) {
        ((DisposableBean)listener).destroy();
      }
    }
  }
}
