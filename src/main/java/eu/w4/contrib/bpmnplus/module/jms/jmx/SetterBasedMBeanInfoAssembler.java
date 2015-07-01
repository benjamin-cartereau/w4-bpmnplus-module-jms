package eu.w4.contrib.bpmnplus.module.jms.jmx;

import java.lang.reflect.Method;

/**
 * Subclass of {@code org.springframework.jmx.export.assembler.SetterBasedMBeanInfoAssembler} that allows
 * to specify method names to be exposed as MBean operations and attributes.
 * JavaBean getters will all be exposed as JMX attributes.
 */
public class SetterBasedMBeanInfoAssembler extends org.springframework.jmx.export.assembler.MethodNameBasedMBeanInfoAssembler {
	@Override
	protected boolean includeReadAttribute(Method method, String beanKey) {
		return true;
	}

	@Override
	protected boolean includeWriteAttribute(Method method, String beanKey) {
		return false;
	}
}
