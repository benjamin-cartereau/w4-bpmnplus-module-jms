package eu.w4.contrib.bpmnplus.module.jms.listener;

import eu.w4.contrib.bpmnplus.module.jms.configuration.BpmnAction;
import eu.w4.engine.client.bpmn.w4.events.SignalIdentifier;
import eu.w4.engine.client.bpmn.w4.infrastructure.DefinitionsIdentifier;
import eu.w4.engine.client.service.EngineService;
import eu.w4.engine.client.service.ObjectFactory;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.lang3.RandomStringUtils;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class MessageListenerFactoryTest {

  MessageListenerFactory listenerFactory = new MessageListenerFactory();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Mock
  EngineService engine;

  @Mock
  ObjectFactory objectFactory;

  @Test(expected = NullPointerException.class)
  public void testGetListenerWithoutAction() throws Exception {
    // BpmnAction is required
    listenerFactory.getListener(null, null, null, null, null);
  }

  @Test(expected = AssertionError.class)
  public void testGetListenerWithoutEngine() throws Exception {
    // EngineService is required
    listenerFactory.getListener(BpmnAction.INSTANTIATE, null, null, null, null);
  }

  @Test(expected = AssertionError.class)
  public void testGetListenerWithoutLogin() throws Exception {
    listenerFactory.setEngineService(engine);
    listenerFactory.getListener(BpmnAction.SIGNAL, null, null, null, null);
  }

  @Test(expected = AssertionError.class)
  public void testGetListenerWithoutPassword() throws Exception {
    listenerFactory.setEngineService(engine);
    listenerFactory.getListener(BpmnAction.SIGNAL, "", null, null, null);
  }

  @Test(expected = AssertionError.class)
  public void testGetListenerWithoutDefinitionsIdentifier() throws Exception {
    listenerFactory.setEngineService(engine);
    listenerFactory.getListener(BpmnAction.SIGNAL, "", "", null, null);
  }

  @Test
  public void testGetSignalListenerWithoutSignalId() throws Exception {
    thrown.expect(AssertionError.class);
    thrown.expectMessage("Signal id must be set");

    when(engine.getObjectFactory()).thenReturn(objectFactory);
    when(objectFactory.newDefinitionsIdentifier()).thenReturn(mock(DefinitionsIdentifier.class));
    
    listenerFactory.setEngineService(engine);
    listenerFactory.getListener(BpmnAction.SIGNAL, "", "", "", null);
  }

  @Test
  public void testGetSignalListener() throws Exception {
    when(engine.getObjectFactory()).thenReturn(objectFactory);
    when(objectFactory.newDefinitionsIdentifier()).thenReturn(mock(DefinitionsIdentifier.class));

    when(objectFactory.newSignalIdentifier()).thenReturn(mock(SignalIdentifier.class));

    Map<String, Object> properties = new HashMap<String, Object>();
    properties.put("signalIdentifier", RandomStringUtils.random(1));
    
    listenerFactory.setEngineService(engine);
    AbstractW4MessageListener listener = listenerFactory.getListener(BpmnAction.SIGNAL, "", "", "", properties);

    assertThat(listener, IsInstanceOf.instanceOf(SignalTriggeringListener.class));
  }
}
