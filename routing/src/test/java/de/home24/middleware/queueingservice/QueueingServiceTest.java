package de.home24.middleware.queueingservice;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opitzconsulting.soa.testing.ServiceException;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;

/**
 * Tests for QueueingService SB implementation.
 * 
 * @author adb
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class QueueingServiceTest extends AbstractBaseSoaTest {

	private static final String QUEUEING_SERVICE_PROXY_URI = "QueueingService/exposed/v1/QueueingService";
	
	private final static Logger LOGGER = LoggerFactory.getLogger(QueueingServiceTest.class);
	
	private DefaultSoapMockService enqueueMessageJMSMock = null;
	private String queueingServiceMockUri = null;
	
	@Before
	public void setUp() {

		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("desc", "http://home24.de/data/common/desctypes/v1");
		
	}

	private void setupDefaultMockService() {
		enqueueMessageJMSMock = new DefaultSoapMockService();
		
		queueingServiceMockUri = mockOsbBusinessService(
				"QueueingService/operations/enqueueMessage/business-service/JMSReference",
				enqueueMessageJMSMock);
	}
	
	@After
	public void tearDown()
	{
		enqueueMessageJMSMock = null;
	}

	@Test
	public void invokeEnqueueMessage() {
		
		setupDefaultMockService();
		
		final String enqueueMessageRequest = readClasspathFile("EnqueueMessageRequest.xml");
		
		// in the pipeline we set the endpoint URI which is confusing the testing framework (error we receive is "unknown protocol: jms" then)
		// so we replace the endpoint with the manipulated endpoint we already have
		setOsbServiceReplacement(
				"QueueingService/operations/enqueueMessage/pipeline/EnqueueMessagePipeline",
				"\\$jmsEndpointURI",
				String.format("'%s'", queueingServiceMockUri));
				
		try
		{
			invokeOsbProxyService(QUEUEING_SERVICE_PROXY_URI, enqueueMessageRequest);
		}
		catch (ServiceException ex)
		{
			LOGGER.error(ex.getXml());
			Assert.fail();
		}
		
		LOGGER.info(String.format("Message sent to queue: %s", enqueueMessageJMSMock.getLastReceivedRequest()));
		
		assertThat("JMS queue has been invoked!",
				enqueueMessageJMSMock.hasBeenInvoked(), is(true));
		
		// just the message part from the request should be written to the queue
		assertXmlEquals(readClasspathFile("EnqueueMessageExpectedQueueContent.xml"), enqueueMessageJMSMock.getLastReceivedRequest());
				
	}
	
	@Test
	public void invokeEnqueueMessageWithError() {

		String exceptionResult = null;
		
		final String enqueueMessageRequest = readClasspathFile("EnqueueMessageRequest.xml");

		// we call the proxy without any mocking activity -> the business
		// service call will fail
		try {
			invokeOsbProxyService(QUEUEING_SERVICE_PROXY_URI, enqueueMessageRequest);
						
		} catch (ServiceException ex) {
			
			exceptionResult = ex.getXml();
			
			LOGGER.error("Expected error due to wrong queue credentials");
			LOGGER.error(ex.getXml());
		}
		

		assertXpathEvaluatesTo("//exc:exception/exc:category/text()", "TechnicalFault",
				exceptionResult);
		assertXpathEvaluatesTo("//exc:exception/exc:severity/text()", "ERROR",
				exceptionResult);
		assertXpathEvaluatesTo("//exc:exception/exc:faultInfo/exc:faultMessage/text()", "[JMSPool:169803]JNDI lookup of the JMS connection factory dummyFactory failed: javax.naming.NameNotFoundException: Unable to resolve 'dummyFactory'. Resolved ''; remaining name 'dummyFactory'",
				exceptionResult);
		assertXpathEvaluatesTo("count(//exc:exception/exc:context/exc:payload/desc:description)", "1",
				exceptionResult);		
		assertXpathEvaluatesTo("//exc:exception/exc:context/exc:payload/desc:description/text()", "Write me to the queue",
				exceptionResult);	
		
	}
	
	
}
