package eu.w4.contrib.bpmnplus.module.jms;

import eu.w4.common.exception.CheckedException;
import eu.w4.engine.client.service.EngineService;
import eu.w4.engine.core.module.external.ExternalModule;
import eu.w4.engine.core.module.external.ExternalModuleContext;
import java.net.URL;
import java.net.URLClassLoader;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.MapPropertySource;

/**
 * Entry point for module definition.
 * JMS Module for W4 enabling to listen to JMS destination and interact with processes :<ul>
 * <li>instantiate a process</li>
 * <li>trigger a signal</li></ul>
 */
public class JMSModule implements ExternalModule {
    private static final Logger logger = LogManager.getLogger();
    
    public static String PROPERTY_ENGINE_SERVICE = "engineService";
    
    private static final String SPRING_APPLICATION_CONTEXT = "JMSModule-context.xml";
    private static final long TIME_BETWEEN_SHUTDOWN_AND_STARTUP = 1000;
    
    private Thread module;
    
    /**
     * JMS Module is starting up
     * @param emc ExternalModuleContext context of the module
     * @throws CheckedException an unexpected error occurred
     * @throws RemoteException communication error with the engine (RMI exception). This exception should never happen since external modules are deployed on server side, thrown for convinience.
     */
    @Override
    public void startup(ExternalModuleContext emc) throws CheckedException, RemoteException {
        logger.info("Startup JMS module...");
        
        if (logger.isDebugEnabled()) debugModuleClasspath();
        
        // Wait for the engine to be completely started
        logger.debug("Wait for the engine to be completely started...");
        emc.getEngineService().waitForStartup();
        logger.debug("...engine started.");
        
        // Spring JMS Thread having its own ContextClassLoader mapped on the current ClassLoader
        module = new SpringJMSThread(emc.getEngineService());
        module.setContextClassLoader(this.getClass().getClassLoader());
        module.start();
    }

    /**
     * Debug method that displays libraries in the module classpath
     */
    private void debugModuleClasspath() {
        logger.debug("Module's libraries in the Classpath:");
        ClassLoader cl = this.getClass().getClassLoader();
        URL[] urls = ((URLClassLoader) cl).getURLs();
        for (URL url: urls) {
            logger.debug("   - {}", url.getFile());
        }
    }
    
    /**
     * JMS Module is shutting down
     * @throws CheckedException an unexpected error occurred
     * @throws RemoteException communication error with the engine (RMI exception). This exception should never happen since external modules are deployed on server side, thrown for convinience.
     */
    @Override
    public void shutdown() throws CheckedException, RemoteException {
        logger.info("Shutdown JMS module...");

        module.interrupt();
        try {
            module.join();
        } catch (InterruptedException ex) {
            logger.error("Unexpected module join interruption", ex);
        }

        logger.info("JMS module halted");
    }

    /**
     * Returns the time to wait between a shutdown and a startup operation. 
     * @return long sleep time between shutdown and startup. 
     * @throws CheckedException an unexpected error occurred
     * @throws RemoteException this exception should never happen since external modules are deployed on server side, thrown for convinience.
     */
    @Override
    public long getShutdownStartupSleepTime() throws CheckedException, RemoteException {
        return TIME_BETWEEN_SHUTDOWN_AND_STARTUP;
    }
    
    /**
     * Module core class : introducing a Spring JMS Thread having its own ContextClassLoader
     */
    class SpringJMSThread extends Thread {
        private final EngineService engineService;
        private AbstractApplicationContext appContext;
        
        /**
         * Default constructor with required W4 Engine Service
         * @param engineService EngineService to interact with W4
         */
        public SpringJMSThread(EngineService engineService) {
            this.engineService = engineService;
        }

        /**
         * Run Spring JMS Thread : setup Spring context with JMS consumers
         */
        @Override
        public void run() {
            logger.debug("Setup Spring Context");
            
            // Setup Spring context with JMS consumers
            ClassPathXmlApplicationContext applicationContext = new ClassPathXmlApplicationContext();
            applicationContext.setConfigLocation(SPRING_APPLICATION_CONTEXT);
            applicationContext.setClassLoader(this.getClass().getClassLoader());
            
            // Build a property source embedding W4 engine service
            Map<String, Object> w4Properties = new HashMap<String, Object>();
            w4Properties.put(PROPERTY_ENGINE_SERVICE, engineService);
            MapPropertySource appProps = new MapPropertySource("w4Properties", w4Properties);
            
            // Push environment properties to Spring application context 
            applicationContext.getEnvironment().getPropertySources().addFirst(appProps);
            
            // Do refresh app context
            applicationContext.refresh();
            
            this.appContext = applicationContext;
            
            logger.info("JMS module started");
        }
        
        /**
         * Thread interruption : stop Spring context
         */
        @Override
        public void interrupt() {
            super.interrupt();
            if (this.appContext != null) {
                this.appContext.stop();
                this.appContext.close();
            }
        }
    }
}
