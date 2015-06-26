package eu.w4.contrib.bpmnplus.module.jms.listener.jmx;

import java.util.Hashtable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.jmx.export.naming.IdentityNamingStrategy;
import static org.springframework.jmx.export.naming.IdentityNamingStrategy.TYPE_KEY;
import org.springframework.jmx.support.ObjectNameManager;
import org.springframework.util.ClassUtils;

/**
 *
 */
public class JmsListenerContainerNamingStrategy extends IdentityNamingStrategy {

  @Override
  public ObjectName getObjectName(Object managedBean, String beanKey) throws MalformedObjectNameException {
    ObjectName name = null;
    if (managedBean instanceof DefaultMessageListenerContainer) {
      DefaultMessageListenerContainer container = (DefaultMessageListenerContainer) managedBean;
      String domain = "eu.w4.contrib.bpmnplus.module.jms";
      Hashtable<String, String> keys = new Hashtable<String, String>();
      keys.put(TYPE_KEY, ClassUtils.getShortName(managedBean.getClass()));
      //keys.put(HASH_CODE_KEY, ObjectUtils.getIdentityHexString(managedBean));
      keys.put("destination", container.getDestinationName());
      keys.put("selector", container.getMessageSelector());
      return ObjectNameManager.getInstance(domain, keys);
    } else {
      name = super.getObjectName(managedBean, beanKey);
    }
    return name;
  }

}
