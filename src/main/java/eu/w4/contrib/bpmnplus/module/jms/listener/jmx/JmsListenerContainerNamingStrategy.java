package eu.w4.contrib.bpmnplus.module.jms.listener.jmx;

import java.util.Hashtable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jmx.export.naming.IdentityNamingStrategy;
import static org.springframework.jmx.export.naming.IdentityNamingStrategy.TYPE_KEY;
import org.springframework.jmx.support.ObjectNameManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

/**
 * JmsListenerContainerNamingStrategy
 * TODO : limit operations -> setConcurrency / maxConcurrency
 */
public class JmsListenerContainerNamingStrategy extends IdentityNamingStrategy {

  private static final String MESSAGE_LISTENER_CONTAINERS_DEFAULT_DOMAIN = "eu.w4.contrib.bpmnplus.module.jms";

  private static final String DOUBLE_QUOTES = "\"";

  @Override
  public ObjectName getObjectName(Object managedBean, String beanKey) throws MalformedObjectNameException {
    ObjectName name = null;

    if (managedBean instanceof DefaultMessageListenerContainer) {
      DefaultMessageListenerContainer container = (DefaultMessageListenerContainer) managedBean;
      String domain = MESSAGE_LISTENER_CONTAINERS_DEFAULT_DOMAIN;
      Hashtable<String, String> keys = new Hashtable<String, String>();
      
      // Type : container type
      keys.put(TYPE_KEY, ClassUtils.getShortName(managedBean.getClass()));
      
      // Add destination...
      keys.put("destination", container.getDestinationName());
      
      // ... and selector to distinguish them
      if (!StringUtils.isEmpty(container.getMessageSelector())) {
        keys.put("selector", escapeSelector(container.getMessageSelector()));
      }
      name = ObjectNameManager.getInstance(domain, keys);
    } else {
      name = super.getObjectName(managedBean, beanKey);
    }

    return name;
  }

  /**
   * Escape JMS selector so that it can be used in an ObjectName
   * @param selector JMS selector
   * @return String the escaped selector
   * See also the method {@link javax.jms.Message}.
   */
  private String escapeSelector(String selector) {
    StringBuilder escapedSelector = new StringBuilder(selector + 2);
    if (selector.startsWith(DOUBLE_QUOTES) && selector.endsWith(DOUBLE_QUOTES)) {
      escapedSelector.append(selector);
    }
    else {
      escapedSelector.append(DOUBLE_QUOTES).append(selector).append(DOUBLE_QUOTES);
    }
    return escapedSelector.toString();
  }
}
