package eu.w4.contrib.bpmnplus.module.jms.listener.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jmx.export.MBeanExporter;

/**
 * Subclass of {@code org.springframework.jms.config.DefaultJmsListenerContainerFactory} that register created containers 
 *  as a managed resource through an existing MBeanExporter.
 */
public class JmxAwareJmsListenerContainerFactory extends org.springframework.jms.config.DefaultJmsListenerContainerFactory {
  @Autowired(required = false)
  private MBeanExporter mbeanExporter;

  @Override
  public DefaultMessageListenerContainer createListenerContainer(JmsListenerEndpoint endpoint) {
    DefaultMessageListenerContainer container = super.createListenerContainer(endpoint);
    if (mbeanExporter != null) {
      mbeanExporter.registerManagedResource(container);
    }
    return container;
  }
}
