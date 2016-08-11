package de.home24.middleware.purchaseorderservice;

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
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class UpdatePurchaseOrderStatusTest extends AbstractBaseSoaTest {

	private final static String PATH_SERVICE = "PurchaseOrderService/exposed/v1/PurchaseOrderService";
	private final static String PATH_REQ_UPDATEPOSTATUS_Q = "PurchaseOrderService/operations/updatePurchaseOrderStatus/business-service/ReqUpdatePOStatusQueue";
	private final static String PATH_PO_ERROR_CALLBACK = "PurchaseOrderService/operations/updatePurchaseOrderStatus/business-service/PurchaseOrderErrorCallbackService";

	private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
	private static final String REPLACE_PARAM_PURCHASE_ORDER_ID = "PURCHASE_ORDER_ID";
	private static final String REPLACE_PARAM_COMBOX_ID = "COMBOX_ID";

	private String randomCorrelationId, randomPoId, randomCoboxId, randomMessageId = "";

	private static final Logger LOGGER = Logger.getLogger(UpdatePurchaseOrderStatusTest.class.getSimpleName());

	private DefaultSoapMockService updatePurchaseOrderStatusSuccesMock;
	private DefaultSoapMockService updatePurchaseOrderStatusFaultMock;
	private List<MockResponsePojo> updatePurchaseOrderStatusFaultMockPojoList = new ArrayList<MockResponsePojo>();

	private DefaultSoapMockService purchaseOrderErrorCallbackServiceSuccesMock;

	@BeforeClass
	public static void setUpBeforeClass() {
		testInitialization();
	}

	@Before
	public void setUp() {
//		System.setProperty("java.io.tmpdir", "C:\\Temp\\");
		
		Random randomNumber = new Random();
		randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));
		randomPoId = "DS" + String.valueOf(randomNumber.nextInt(1000000));
		randomCoboxId = String.valueOf(randomNumber.nextInt(1000000));
		randomMessageId = String.valueOf(randomNumber.nextInt(1000000));

		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("navpo", "http://home24.de/data/navision/purchaseorder/v1");
		declareXpathNS("navpom", "http://home24.de/data/navision/purchaseordermessages/v1");
		declareXpathNS("posm", "http://home24.de/interfaces/bas/purchaseorderservice/purchaseorderservicemessages/v1");
		declareXpathNS("oagis", "http://www.openapplications.org/oagis/10");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("wsa05", "http://www.w3.org/2005/08/addressing");

		LOGGER.info("+++Create Mocks+++");
		updatePurchaseOrderStatusSuccesMock = new DefaultSoapMockService("");
		purchaseOrderErrorCallbackServiceSuccesMock = new DefaultSoapMockService("");
		updatePurchaseOrderStatusFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
		updatePurchaseOrderStatusFaultMock = new DefaultSoapMockService(updatePurchaseOrderStatusFaultMockPojoList);

	}

	@After
	public void tearDown() {
		LOGGER.info("+++Delete Mocks+++");
		updatePurchaseOrderStatusSuccesMock = null;
		updatePurchaseOrderStatusFaultMock = null;
		purchaseOrderErrorCallbackServiceSuccesMock = null;
	}

	@Test
	public void updatePurchaseOrderStatusSuccesTest() {
		try {
			mockOsbBusinessService(PATH_REQ_UPDATEPOSTATUS_Q, updatePurchaseOrderStatusSuccesMock);
			mockOsbBusinessService(PATH_PO_ERROR_CALLBACK, updatePurchaseOrderStatusFaultMock);

			String requestXML = new ParameterReplacer(readClasspathFile("updatePurchaseOrderStatusRequest.xml"))
					.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
					.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, randomPoId)
					.replace(REPLACE_PARAM_COMBOX_ID, randomCoboxId).build();

			final String invokerRefMockUri = mockOsbBusinessService(PATH_PO_ERROR_CALLBACK,
					updatePurchaseOrderStatusFaultMock);

			requestXML = getFullSoapRequest(randomMessageId, invokerRefMockUri, requestXML);

			invokeOsbProxyService(PATH_SERVICE, requestXML);

			assertThat("One message is sent to the queue.",
					updatePurchaseOrderStatusSuccesMock.getNumberOfInvocations() == 1);
			assertThat("No error occours.", updatePurchaseOrderStatusFaultMock.hasBeenInvoked() == false);
			
			String poStatusUpdateQueueMsg = updatePurchaseOrderStatusSuccesMock.getLastReceivedRequest();

			assertXpathEvaluatesTo("/navpom:updatePurchaseOrderStatus/navpom:header/mht:CorrelationID/text()", 
					randomCorrelationId,
					poStatusUpdateQueueMsg);
			assertXpathEvaluatesTo("/navpom:updatePurchaseOrderStatus/navpom:body/navpom:updatePurchaseOrderStatusRequest/navpo:purchaseOrder/navpo:purchaseOrderNumber/text()", 
					randomPoId,
					poStatusUpdateQueueMsg);
			assertXpathEvaluatesTo("/navpom:updatePurchaseOrderStatus/navpom:body/navpom:updatePurchaseOrderStatusRequest/navpo:purchaseOrder/navpo:comboxID/text()", 
					randomCoboxId,
					poStatusUpdateQueueMsg);
			assertXpathEvaluatesTo("/navpom:updatePurchaseOrderStatus/navpom:header/mht:KeyValueList/mht:KeyValuePair[./mht:Key='MessageID']/mht:Value/text()", 
					randomMessageId,
					poStatusUpdateQueueMsg);
			assertXpathEvaluatesTo("/navpom:updatePurchaseOrderStatus/navpom:header/mht:KeyValueList/mht:KeyValuePair[./mht:Key='ReplyTo']/mht:Value/text()", 
					invokerRefMockUri,
					poStatusUpdateQueueMsg);
			
		} catch (ServiceException e) {
			fail();
		}

	}

	@Test
	public void updatePurchaseOrderStatusErrorTest() {
		String invokerRefMockUri="";
		try {

			String requestXML = new ParameterReplacer(readClasspathFile("updatePurchaseOrderStatusRequest.xml"))
					.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
					.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, randomPoId)
					.replace(REPLACE_PARAM_COMBOX_ID, randomCoboxId).build();

			mockOsbBusinessService(PATH_REQ_UPDATEPOSTATUS_Q, updatePurchaseOrderStatusFaultMock);

			invokerRefMockUri = mockOsbBusinessService(PATH_PO_ERROR_CALLBACK,
					updatePurchaseOrderStatusSuccesMock);

			requestXML = getFullSoapRequest(randomMessageId, invokerRefMockUri, requestXML);

			invokeOsbProxyService(PATH_SERVICE, requestXML);

			fail();
		} catch (ServiceException e) {
			String exceptionesponse = updatePurchaseOrderStatusSuccesMock.getLastReceivedRequest();
			
			assertThat("Service tries to send a message to the queue.",
					updatePurchaseOrderStatusSuccesMock.getNumberOfInvocations() == 1);
			assertThat("No error occours.", updatePurchaseOrderStatusFaultMock.getNumberOfInvocations() == 1);

			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/exc:exception/exc:category/text()", 
					"TechnicalFault",
					exceptionesponse);
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/exc:exception/exc:context/exc:transactionId/text()", 
					randomCorrelationId,
					exceptionesponse);

		    assertXpathEvaluatesTo("//wsa05:RelatesTo/text()", randomMessageId,
		    		exceptionesponse);
		}

	}

	private String getFullSoapRequest(final String pMessageId, final String pCallbackUrl, final String pRequestString) {
		return SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, pRequestString),
				SoapUtil.getInstance().messageIdHeader(pMessageId), SoapUtil.getInstance().relatesToHeader(pMessageId),
				SoapUtil.getInstance().replyToHeader(pCallbackUrl));
	}
}
