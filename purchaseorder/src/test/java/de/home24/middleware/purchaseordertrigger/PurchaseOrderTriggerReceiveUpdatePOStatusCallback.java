package de.home24.middleware.purchaseordertrigger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.purchaseorderservice.UpdatePurchaseOrderStatusTest;

public class PurchaseOrderTriggerReceiveUpdatePOStatusCallback extends AbstractBaseSoaTest {

	private final static String PATH_SERVICE = "PurchaseOrderTrigger/operations/receiveUpdatePOStatusCallback/ReceiveUpdatePOStatusCallback";
	private final static String PATH_PO_CALLBACK = "PurchaseOrderTrigger/operations/receiveUpdatePOStatusCallback/business-service/UpdatePOStatusCallbackService";
	private final static String PATH_RETRY_WRAPPER = "PurchaseOrderTrigger/shared/business-service/ResponseRetryWrapper";

	private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
	private static final String REPLACE_PARAM_PURCHASE_ORDER_ID = "PURCHASE_ORDER_ID";
	private static final String REPLY_TO="REPLY_TO";
	private static final String MESSAGE_ID="MESSAGE_ID";

	private String randomCorrelationId, randomPoId, randomMessageID = "";

	private static final Logger LOGGER = Logger.getLogger(UpdatePurchaseOrderStatusTest.class.getSimpleName());

	private DefaultSoapMockService retryWrapperSuccesMock;
	private DefaultSoapMockService poCallbackServiceStatusFaultMock;
	private List<MockResponsePojo> updatePurchaseOrderStatusFaultMockPojoList = new ArrayList<MockResponsePojo>();

	private DefaultSoapMockService poCallbackServiceSuccesMock;

	@BeforeClass
	public static void setUpBeforeClass() {
		testInitialization();
	}

	@Before
	public void setUp() {

		Random randomNumber = new Random();
		randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));
		randomPoId = "DS" + String.valueOf(randomNumber.nextInt(1000000));
		randomMessageID = String.valueOf(randomNumber.nextInt(1000000));

		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("navpo", "http://home24.de/data/navision/purchaseorder/v1");
		declareXpathNS("navpom", "http://home24.de/data/navision/purchaseordermessages/v1");
		declareXpathNS("posm", "http://home24.de/interfaces/bas/purchaseorderservice/purchaseorderservicemessages/v1");
		declareXpathNS("oagis", "http://www.openapplications.org/oagis/10");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("wsa05", "http://www.w3.org/2005/08/addressing");
		declareXpathNS("rrw", "http://home24.de/interfaces/bas/responseretrywrapper/responseretrywrappermessages/v1");

		LOGGER.info("+++Create Mocks+++");
		retryWrapperSuccesMock = new DefaultSoapMockService("");
		poCallbackServiceSuccesMock = new DefaultSoapMockService("");
		updatePurchaseOrderStatusFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
		poCallbackServiceStatusFaultMock = new DefaultSoapMockService(updatePurchaseOrderStatusFaultMockPojoList);

	}

	@After
	public void tearDown() {
		LOGGER.info("+++Delete Mocks+++");
		retryWrapperSuccesMock = null;
		poCallbackServiceStatusFaultMock = null;
		poCallbackServiceSuccesMock = null;
	}

	@Test
	public void happyPath() {
		String callbackUri = mockOsbBusinessService(PATH_PO_CALLBACK, poCallbackServiceSuccesMock);
		mockOsbBusinessService(PATH_RETRY_WRAPPER, retryWrapperSuccesMock);

		String requestXML = new ParameterReplacer(readClasspathFile(
				"queues/h24_PurchaseOrder/RSP_UpdatePOStatus_Q/receiveUpdatePurchaseOrderStatusCallback01.xml"))
						.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
						.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, randomPoId)
						.replace(REPLY_TO, callbackUri)
						.replace(MESSAGE_ID, randomMessageID).build();
		try {

			invokeOsbProxyService(PATH_SERVICE, requestXML);

			assertThat("One message is sent to callback.",
					poCallbackServiceSuccesMock.getNumberOfInvocations() == 1);
			assertThat("No error occours.", retryWrapperSuccesMock.hasBeenInvoked() == false);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/posm:updatePurchaseOrderStatusResponse/posm:purchaseOrderNumber/text()", 
					randomPoId,
					poCallbackServiceSuccesMock.getLastReceivedRequest());
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/posm:updatePurchaseOrderStatusResponse/posm:responseHeader/mht:CorrelationID/text()", 
					randomCorrelationId,
					poCallbackServiceSuccesMock.getLastReceivedRequest());
		} catch (ServiceException e) {
			fail();
		}

	}
	

	@Test
	public void errorPath() {
		String callbackUri = mockOsbBusinessService(PATH_PO_CALLBACK, poCallbackServiceStatusFaultMock);
		mockOsbBusinessService(PATH_RETRY_WRAPPER, retryWrapperSuccesMock);

		String requestXML = new ParameterReplacer(readClasspathFile(
				"queues/h24_PurchaseOrder/RSP_UpdatePOStatus_Q/receiveUpdatePurchaseOrderStatusCallback01.xml"))
						.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
						.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, randomPoId)
						.replace(REPLY_TO, callbackUri)
						.replace(MESSAGE_ID, randomMessageID).build();
		try {

			invokeOsbProxyService(PATH_SERVICE, requestXML);

			assertThat("One message is sent to callback.",
					poCallbackServiceStatusFaultMock.getNumberOfInvocations() == 1);
			assertThat("An error occours.", retryWrapperSuccesMock.getNumberOfInvocations() == 1);

			String retryWrapperMsg = retryWrapperSuccesMock.getLastReceivedRequest();
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrw:onErrorInResponseQueueRequest/rrw:requestHeader/mht:CorrelationID/text()", 
					randomCorrelationId,
					retryWrapperMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrw:onErrorInResponseQueueRequest/rrw:exception/exc:category/text()", 
					"TechnicalFault",
					retryWrapperMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrw:onErrorInResponseQueueRequest/rrw:exception/exc:context/exc:transactionId/text()", 
					randomCorrelationId,
					retryWrapperMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrw:onErrorInResponseQueueRequest/rrw:exception/exc:context/exc:payload"
					+ "/navpom:receiveUpdatePurchaseOrderStatusCallback/navpom:header/mht:CorrelationID/text()", 
					randomCorrelationId,
					retryWrapperMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrw:onErrorInResponseQueueRequest/rrw:exception/exc:context/exc:payload"
					+ "/navpom:receiveUpdatePurchaseOrderStatusCallback/navpom:body/navpom:updatePurchaseOrderStatusResponse/navpo:purchaseOrderNumber/text()", 
					randomPoId,
					retryWrapperMsg);
		} catch (ServiceException e) {
			fail();
		}

	}

}
