package eu.w4.contrib.bpmnplus.module.jms;

import eu.w4.common.exception.CheckedException;
import eu.w4.engine.client.bpmn.w4.collaboration.CollaborationIdentifier;
import eu.w4.engine.client.bpmn.w4.events.SignalIdentifier;
import eu.w4.engine.client.bpmn.w4.infrastructure.DefinitionsIdentifier;
import eu.w4.engine.client.bpmn.w4.process.ProcessIdentifier;
import eu.w4.engine.client.service.EngineService;
import eu.w4.engine.client.service.ObjectFactory;
import java.rmi.RemoteException;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.mockito.Mockito.*;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration("ApplicationConfigTest-context.xml")
//@ContextConfiguration
@ContextConfiguration(classes = {TestConfig.class, ApplicationConfig.class}, initializers = ApplicationConfigTest.ContainerTestContextInitializer.class)
public class ApplicationConfigTest {

  @Autowired
  ApplicationConfig instance;

  @Test
  public void testSubsetToCamelCaseEmpty() {
    Map<String, ? extends Object> result = ApplicationConfig.subsetToCamelCase(null, null, false);
    assertTrue(result.isEmpty());
  }

  @Test
  public void testSubsetToCamelCase() {
    Properties properties = new Properties();
    properties.put("module.jms.endpoint.endpoint1.bpmn.action", "myAction");
    properties.put("module.jms.endpoint.endpoint1.bpmn.definition_identifier", "myDefId");
    properties.put("module.jms.endpoint.endpoint1.bpmn.data_entry_id", "myDataEntryId");
    Map<String, ? extends Object> result = ApplicationConfig.subsetToCamelCase(properties, "module.jms.endpoint.endpoint1.bpmn", true);

    assertEquals(3, result.size());

    assertTrue(result.containsKey("action"));
    assertTrue(result.containsKey("definitionIdentifier"));
    assertTrue(result.containsKey("dataEntryId"));

    assertEquals("myAction", result.get("action"));
  }

  @Test
  public void testConfigureJmsListeners() {
    JmsListenerEndpointRegistrar registrar = mock(JmsListenerEndpointRegistrar.class);
    instance.setIgnoreErroneousEndpoint(false);
    instance.configureJmsListeners(registrar);
  }

  /**
   * Utility class to populate the test environment
   */
  public static class ContainerTestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    //@Rule
    //public MockitoRule mockitoRule = MockitoJUnit.rule();

    //@Mock
    //EngineService engineService;
    @Override
    public void initialize(ConfigurableApplicationContext pApplicationContext) {
      EngineService engineService = mock(EngineService.class);
      ObjectFactory objectFactory = mock(ObjectFactory.class);
      try {
        when(engineService.getObjectFactory()).thenReturn(objectFactory);
        when(objectFactory.newDefinitionsIdentifier()).thenReturn(mock(DefinitionsIdentifier.class));
        when(objectFactory.newSignalIdentifier()).thenReturn(mock(SignalIdentifier.class));
        when(objectFactory.newProcessIdentifier()).thenReturn(mock(ProcessIdentifier.class));
        when(objectFactory.newCollaborationIdentifier()).thenReturn(mock(CollaborationIdentifier.class));
      } catch (CheckedException cex) {
        cex.printStackTrace();
      } catch (RemoteException rex) {
        rex.printStackTrace();
      }

      assert engineService != null : "Engine mock not set";
      
      ConfigurableListableBeanFactory beanFactory = pApplicationContext.getBeanFactory();
      beanFactory.registerSingleton("engineService", engineService);
    }
  }
}
