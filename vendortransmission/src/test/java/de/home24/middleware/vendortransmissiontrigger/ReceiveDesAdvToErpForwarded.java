////NOTE all test casses should be updated with 1.5 release code for this osb app
package de.home24.middleware.vendortransmissiontrigger;

import static de.home24.middleware.octestframework.vendortransmission.TestDataPovider.RESOURCES_PATH_QUEUES;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.Map;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.components.MessagingDao.JmsModule;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.vendortransmission.TestDataPovider;

/**
 * Test implementations for
 * VendorTransmissionTrigger.receiveDesAdvForwardedToErp
 * 
 * @author svb
 *
 */
public class ReceiveDesAdvToErpForwarded extends AbstractBaseSoaTest {

    private static final Logger LOGGER = Logger.getLogger(ReceiveDesAdvToErpForwarded.class.getSimpleName());

    private DefaultSoapMockService ediInboundVendorTransmissionCallbackRef = null;
    private DefaultSoapMockService responseRetryWrapperRef = null;

    private String correlationId = null;
    private Map<ExceptionAsserterKey, String> keyToExpectedExceptionValues = null;

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    @Autowired
    private TestDataPovider testDataPovider;

    @Before
    public void setUp() {

	correlationId = String.valueOf(System.currentTimeMillis());

	keyToExpectedExceptionValues = testDataPovider.createCommonExpectedValuesForException(correlationId);

	ediInboundVendorTransmissionCallbackRef = new DefaultSoapMockService();
	responseRetryWrapperRef = new DefaultSoapMockService();
	mockOsbBusinessService(
		"VendorTransmissionTrigger/operations/receiveDesAdvToErpForwarded/business-service/EdiInboundCallbackRef",
		ediInboundVendorTransmissionCallbackRef);
	mockOsbBusinessService(
		"VendorTransmissionTrigger/operations/receiveDesAdvToErpForwarded/business-service/EdiInboundCallbackRef",
		ediInboundVendorTransmissionCallbackRef);
    }

    @Test
    public void whenReceiveValidResponseFromRspFwdDesAdvQThenRegularCallbackIsReturned() {

	// TODO: Verify test data, because message format from queue is not
	// final yet and so the mapping does not exist
	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.EDIFACT, "h24jms.RSP_ForwardDesAdv_Q",
		readClasspathFile(String.format("%s/RSP_ForwardDesAdv_Q/ForwardDesAdvToERPResponse.xml",
			RESOURCES_PATH_QUEUES)));

	waitForInvocationOf(ediInboundVendorTransmissionCallbackRef);

	assertThat("RetryResponseWrapper has been invoked!", responseRetryWrapperRef.hasBeenInvoked(),
		is(false));
	// TODO: Assert response to EDIInboundVendorTransmissionProcess
	assertXpathEvaluatesTo("count(/desadv:forwardDesAdvToERPResponse/desadv:purchaseOrder)", "1",
		ediInboundVendorTransmissionCallbackRef.getLastReceivedRequest());
	assertXpathEvaluatesTo(
		"/desadv:forwardDesAdvToERPResponse/desadv:responseHeader/header:Caller/header:SourceSystemName/text()",
		"1", ediInboundVendorTransmissionCallbackRef.getLastReceivedRequest());
	assertXpathEvaluatesTo(
		"/desadv:forwardDesAdvToERPResponse/desadv:responseHeader/header:CorrelationID/text()",
		correlationId, ediInboundVendorTransmissionCallbackRef.getLastReceivedRequest());
	assertXpathEvaluatesTo(
		"/desadv:forwardDesAdvToERPResponse/desadv:responseHeader/header:ActivityID/text()",
		"P1002-DESADV-2ERP-ACK", ediInboundVendorTransmissionCallbackRef.getLastReceivedRequest());
    }

    @Test
    public void whenReceiveInvalidResponseFromRspFwdDesAdvQThenTheResponseRetryWrapperIsInvokedWithADataFault() {

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.EDIFACT, "h24jms.RSP_ForwardDesAdv_Q",
		readClasspathFile(
			String.format("%s/RSP_ForwardDesAdv_Q/ForwardInvalidDesAdvToERPResponse.xml",
				RESOURCES_PATH_QUEUES)));

	waitForInvocationOf(responseRetryWrapperRef);

	assertThat("EdiInboundVendorTransmissionCallbackRef has been invoked!",
		ediInboundVendorTransmissionCallbackRef.hasBeenInvoked(), is(false));

	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "DataFault");
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-40301");
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"Data validation fault for receiveDesAdvToErpForwarded");

	exceptionAsserter.assertException(responseRetryWrapperRef.getLastReceivedRequest(),
		keyToExpectedExceptionValues);
    }

    @Test
    public void whenReceiveValidResponseFromRspFwdDesAdvQButCallbackCannotBeSentThenResponseRetryWrapperIsInvokedWithATechnicalFault() {

	ediInboundVendorTransmissionCallbackRef = new DefaultSoapMockService(
		Lists.newArrayList(new MockResponsePojo(ResponseType.FAULT,
			"EdiInboundVendorTransmissionProcess cannot be invoked!",
			"EdiInboundVendorTransmissionProcess cannot be invoked!")));

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.EDIFACT, "h24jms.RSP_ForwardDesAdv_Q",
		readClasspathFile(String.format("%s/RSP_ForwardDesAdv_Q/ForwardDesAdvToERPResponse.xml",
			RESOURCES_PATH_QUEUES)));

	waitForInvocationOf(responseRetryWrapperRef);

	assertThat("EdiInboundVendorTransmissionCallbackRef has been invoked!",
		ediInboundVendorTransmissionCallbackRef.hasBeenInvoked(), is(true));

	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-10301");
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"Technical fault while calling back EdiVendorTransmissionProcess");

	exceptionAsserter.assertException(responseRetryWrapperRef.getLastReceivedRequest(),
		keyToExpectedExceptionValues);
    }
}
