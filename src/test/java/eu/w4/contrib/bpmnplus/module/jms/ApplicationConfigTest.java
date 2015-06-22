package eu.w4.contrib.bpmnplus.module.jms;

import eu.w4.contrib.bpmnplus.module.jms.ApplicationConfigTest.ContainerTestContextInitializer;
import eu.w4.engine.client.service.EngineService;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.jms.config.JmsListenerEndpointRegistrar;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration("ApplicationConfigTest-context.xml")
//@ContextConfiguration
@ContextConfiguration(classes = {TestConfig.class, ApplicationConfig.class}, initializers = ContainerTestContextInitializer.class)
public class ApplicationConfigTest {

  @Rule
  public MockitoRule mockitoRule = MockitoJUnit.rule();

//  public ApplicationConfigTest() {
//  }
//  
//  @BeforeClass
//  public static void setUpClass() {
//  }
//  
//  @AfterClass
//  public static void tearDownClass() {
//  }
//  
//  @Before
//  public void setUp() {
//  }
//  
//  @After
//  public void tearDown() {
//  }
  @Autowired
  private ConfigurableEnvironment environment;

  @Autowired
  ApplicationConfig instance;

  @Test
  public void testConfigureJmsListeners() {
    System.out.println("configureJmsListeners");
    /*   
     Map<String, Object> w4Properties = new HashMap<String, Object>();
     w4Properties.put(PROPERTY_ENGINE_SERVICE, engineService);
     MapPropertySource appProps = new MapPropertySource("w4Properties", w4Properties);
            
     // Push environment properties to Spring application context 
     applicationContext.getEnvironment().getPropertySources().addFirst(appProps);
    
     environment.getPS
     */
    JmsListenerEndpointRegistrar registrar = null;
    //ApplicationConfig instance = new ApplicationConfig();
    instance.configureJmsListeners(registrar);
    // TODO review the generated test code and remove the default call to fail.
    fail("The test case is a prototype.");
  }

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

  @Mock
  EngineService engineService;

  public class ContainerTestContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    public ContainerTestContextInitializer() {
    
    }
    
    @Override
    public void initialize(ConfigurableApplicationContext pApplicationContext) {
      System.out.println("initialize configuration");
      MutablePropertySources sources = pApplicationContext.getEnvironment().getPropertySources();
      Map<String, Object> w4Properties = new HashMap<String, Object>();
      w4Properties.put("engineService", engineService);
      MapPropertySource mps = new MapPropertySource("w4Properties", w4Properties);
      sources.addFirst(mps);
    }
  }

}
