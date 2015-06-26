package eu.w4.contrib.bpmnplus.module.jms.listener.jmx;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.config.JmsListenerEndpoint;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jmx.export.MBeanExporter;

/**
 * DefaultJmsListenerContainerFactory that register created message 
 *   listener containers as a managed resource through an existing MBeanExporter.
 */
public class DefaultJmsListenerContainerFactory extends org.springframework.jms.config.DefaultJmsListenerContainerFactory {
  @Autowired
  private MBeanExporter mbeanExporter;

  @Override
  public DefaultMessageListenerContainer createListenerContainer(JmsListenerEndpoint endpoint) {
    DefaultMessageListenerContainer container = super.createListenerContainer(endpoint);
    
//    ObjectName name = null;
//    try {
//      name = new ObjectName("eu.w4.contrib.bpmnplus.module.jms:name=jmsListenerContainer,instance=" + Thread.currentThread().getName());
//    } catch (MalformedObjectNameException ex) {
//      throw new RuntimeException(ex.getMessage(), ex);
//    }
//    System.out.println("name="+name.toString());
//    mbeanExporter.registerManagedResource(container, name);
    
    
    
    mbeanExporter.registerManagedResource(container);
    return container;
  }
}
