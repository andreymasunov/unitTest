package de.home24.middleware.salesordertrigger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.HashMap;
import java.util.Map;

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
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Test case implementations for SalesOrderTrigger sendSalesOrderUpdateToShop
 * 
 * @author svb
 *
 */
public class SendSalesOrderUpdateToShop extends AbstractBaseSoaTest {

    private static final String PATH_TO_RESOURCES_SB_SALES_ORDER = "../servicebus/SalesOrder/SalesOrderTrigger";
    private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";

    private String salesOrderId = null;
    private String salesOrderUpateFromNav = null;
    private DefaultSoapMockService writeToSIIMessagesErrorQueueRef = null;

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    @Before
    public void setUp() {

	salesOrderId = String.valueOf(System.currentTimeMillis());

	salesOrderUpateFromNav = new ParameterReplacer(readClasspathFile(
		String.format("%s/SendSalesOrderUpdateToShopRequest.xml", PATH_TO_RESOURCES_SB_SALES_ORDER)))
			.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build();

	writeToSIIMessagesErrorQueueRef = new DefaultSoapMockService();
	mockOsbBusinessService("SalesOrderTrigger/shared/business-service/WriteToSIIMessagesErrorQueue",
		writeToSIIMessagesErrorQueueRef);
    }

    @Test
    public void whenReceiveValidSalesOrderUpdateFromNavThenInvokeSiiMessageHandlerToProcessSalesOrderUpdateToShop() {

	final DefaultSoapMockService siiMessageHandlerProcessRef = new DefaultSoapMockService();

	mockOsbBusinessService("SalesOrderTrigger/shared/business-service/SIIMessageHandlerProcess",
		siiMessageHandlerProcessRef);

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.SII, "h24jms.RSP_SIIMessages_Q", salesOrderUpateFromNav);

	waitForInvocationOf(siiMessageHandlerProcessRef);

	assertThat("SIIMessagehandlerProcessRef has not been invoked!",
		siiMessageHandlerProcessRef.hasBeenInvoked(), is(true));
	assertThat("WriteToSIIMessagesErrorQueueRef has not been invoked!",
		writeToSIIMessagesErrorQueueRef.hasBeenInvoked(), is(false));

	declareXpathNS("ns1",
		"http://home24.de/interfaces/bps/siimessagehandlerprocess/siimessagehandlerprocessmessages/v1");
	assertXmlEquals(
		new ParameterReplacer(readClasspathFile(String
			.format("%s/SiiMessageHandlerProcessRequest.xml", PATH_TO_RESOURCES_SB_SALES_ORDER)))
				.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build(),
		evaluateXpath("//ns1:sendSalesOrderUpdateToShopRequest",
			siiMessageHandlerProcessRef.getLastReceivedRequest()));
    }

    @Test
    public void whenReceiveValidSalesOrderUpdateFromNavButSiiMessageHandlerProcessIsNotAvailableThenSendMsgToErrQ() {

	final DefaultSoapMockService siiMessageHandlerProcessRef = new DefaultSoapMockService(
		Lists.newArrayList(new MockResponsePojo(ResponseType.FAULT,
			"SiiMessageHandlerProcess not reachable", "SiiMessageHandlerProcess not reachable")));

	mockOsbBusinessService("SalesOrderTrigger/shared/business-service/SIIMessageHandlerProcess",
		siiMessageHandlerProcessRef);

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.SII, "h24jms.RSP_SIIMessages_Q", salesOrderUpateFromNav);

	waitForInvocationOf(writeToSIIMessagesErrorQueueRef);

	assertThat("SIIMessagehandlerProcessRef has not been invoked!",
		siiMessageHandlerProcessRef.hasBeenInvoked(), is(true));
	assertThat("WriteToSIIMessagesErrorQueueRef has not been invoked!",
		writeToSIIMessagesErrorQueueRef.hasBeenInvoked(), is(true));

	Map<ExceptionAsserterKey, String> keysToExpectedExceptionValue = new HashMap<>();
	keysToExpectedExceptionValue.put(ExceptionAsserterKey.PROCESS_LIBRARY_ID, "P203");
	keysToExpectedExceptionValue.put(ExceptionAsserterKey.SOURCE_SYSTEM_NAME, "Middleware");
	keysToExpectedExceptionValue.put(ExceptionAsserterKey.TRANSACTION_ID, salesOrderId);
	keysToExpectedExceptionValue.put(ExceptionAsserterKey.PAYLOAD_ELEMENT_NAME, "ProcessRequest");
	keysToExpectedExceptionValue.put(ExceptionAsserterKey.PAYLOAD_ELEMENT, salesOrderUpateFromNav);
	keysToExpectedExceptionValue.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	keysToExpectedExceptionValue.put(ExceptionAsserterKey.FAULT_CODE, "MW-10800");
	keysToExpectedExceptionValue.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"Technical fault while calling the SiiMessageHandlerProcess");

	exceptionAsserter.assertException(writeToSIIMessagesErrorQueueRef.getLastReceivedRequest(),
		keysToExpectedExceptionValue);
    }
}
