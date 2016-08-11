package de.home24.middleware.ordertransactiontrigger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.osb.OsbAccessor;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.MessagingDao.JmsModule;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Tests for OrderTransactionTrigger
 * 
 * @author adb
 *
 */
public class OrderTransactionTriggerBamPipelineTest extends AbstractBaseSoaTest {

	private static final Logger LOGGER = Logger
			.getLogger(OrderTransactionTriggerBamPipelineTest.class.getSimpleName());

	private final Long TIMEOUT_READ_QUEUE = 1000L;
	
	private static final String PARAMETER_ACTIVITY_ID = "ACTIVITY_ID";

	private static final String PROPS_PARAM_ACTIVITY_TO_IGNORE = "IGNORE";

	private DefaultSoapMockService activityProcessStatusMonitoringQRef;

	@Before
	public void setUp() {
		activityProcessStatusMonitoringQRef = new DefaultSoapMockService();
		
	    declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	    declareXpathNS("ns1", "http://home24.de/data/common/exceptiontypes/v1");
	    declareXpathNS("ns2", "http://home24.de/interfaces/bts/ordertransactiontrigger/ordertransactiontriggermessages/v1");
	    declareXpathNS("ns3", "http://home24.de/data/navision/salesorder/v1");
	    declareXpathNS("nuso","http://home24.de/data/navision/salesordermessages/v1");
	}

	@After
	public void tearDown() {
	}

	@Test
	public void whenSendingActivityCodeThatShouldBeIgnoredByBAMTheBAMQueueIsNotCalled() {

		mockOsbBusinessService("OrderTransactionTrigger/shared/business-service/BamQueueRef",
				activityProcessStatusMonitoringQRef);

		List<String> listActivityCodesToIgnore = getPropValues(PROPS_PARAM_ACTIVITY_TO_IGNORE);

		for (String activityCode : listActivityCodesToIgnore) {

			LOGGER.info(String.format("Execute test for activity code %s", activityCode));
			checkIfIgnoringBAMWorks(activityCode);
		}

	}

	private void checkIfIgnoringBAMWorks(String activityCode) {
		String requestString = readClasspathFile(
				"../servicebus/OrderTransaction/OrderTransactionTrigger/UpdateTransactionMonitoring_JustBAL.xml");

		getOsbAccessor().flushUriChanges();

		getMessagingDao().writeToQueue(JmsModule.LOGGING, "h24jms.REQ_ActivityProcessstatusLogging_Q",
				new ParameterReplacer(requestString).replace(PARAMETER_ACTIVITY_ID, activityCode).build());

		waitForInvocationOf(activityProcessStatusMonitoringQRef, 2);

		assertThat("ActivityProcessStatusMonitoringQRef has been invoked!",
				activityProcessStatusMonitoringQRef.hasBeenInvoked(), is(false));

		TextMessage message = (TextMessage) getMessagingDao().readFromQueue(JmsModule.LOGGING,
				"h24jms.ERR_ActivityProcessstatusLogging_Q",
				TIMEOUT_READ_QUEUE);

		try {
			assertThat("There has been an error while processing the BAM request for activity code " + activityCode,
					message == null || !message.getText().contains(activityCode));
		} catch (JMSException jmsException) {
			Assert.fail("JMS message could not be retrieved");
		}

		LOGGER.info("Test successful - ignoring activity code " + activityCode);

	}

	public List<String> getPropValues(String valueMatcher) {

		List<String> resultListActivities = new ArrayList<String>();

		Properties prop = getBamPipelineTestProperties();
		Enumeration<Object> keyEnum = prop.keys();

		while (keyEnum.hasMoreElements()) {
			String currentKeyElement = (String) keyEnum.nextElement();
			String currentValueElement = (String) prop.get(currentKeyElement);

			if (currentValueElement.equals(valueMatcher)) {
				resultListActivities.add(currentKeyElement);
			}
		}

		return resultListActivities;

	}

	private Properties getBamPipelineTestProperties() {

		Properties props = new Properties();
		String propFileName = "bampipelinetest.properties";
		
		InputStream is = getClass().getResourceAsStream("../servicebus/OrderTransaction/OrderTransactionTrigger/" + propFileName);

		if (is != null) {

			try {
				props.load(is);
				is.close();
			} catch (IOException ioex) {
				// ignore
			}

		}

		return props;
	}


	@Test
	public void whenWriteToBamRequestWithoutStatusMonitoringElement() {
		
		String requestString = readClasspathFile("../servicebus/OrderTransaction/OrderTransactionTrigger/writeToBamRequestWithoutStatusMonitoringElement.xml");

		mockOsbBusinessService("OrderTransactionTrigger/shared/business-service/BamQueueRef",
				activityProcessStatusMonitoringQRef);
		
		try {
			invokeOsbProxyService("OrderTransactionTrigger/exposed/v1/OrderTransactionTrigger",
						requestString);
			waitForInvocationOf(activityProcessStatusMonitoringQRef);
			
			assertThat("ActivityProcessStatusMonitoringQRef has not been invoked!",
					activityProcessStatusMonitoringQRef.hasBeenInvoked(), is(true));
		}catch (ServiceException e) {
				e.printStackTrace();
				fail("ServiceException");
			}
		}
	
		
}
