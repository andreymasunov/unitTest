package de.home24.middleware.SIIMessageHandler;

import static org.junit.Assert.*;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;

public class SendSalesOrderToShop extends AbstractBaseSoaTest {

	public static final String COMPOSITE = "SIIMessageHandler";
	public static final String REVISION = "1.2.1.0";
	public static final String PROCESS = "SIIMessageHandlerProcess";

	private static final Logger LOGGER = Logger.getLogger(SendSalesOrderToShop.class.getSimpleName());

	private DefaultSoapMockService orderTransactionServiceMock;
	private DefaultSoapMockService genericFaultHandlerMock;
	private DefaultSoapMockService salesOrderServiceMock;
	private DefaultSoapMockService cancellationPublisherReferenceMock;

	@Before
	public void setUp() {
		
		LOGGER.info("+++Create Mocks");
		genericFaultHandlerMock = new DefaultSoapMockService("");
		salesOrderServiceMock = new DefaultSoapMockService(
				readClasspathFile("sendSalesOrderUpdateToShopResponse.xml"));
		cancellationPublisherReferenceMock = new DefaultSoapMockService("");
		orderTransactionServiceMock = new DefaultSoapMockService("");
		
		declareXpathNS("env", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("nn", "NO_NAMESPACE");
		
	}

	@After
	public void tearDown() {
		LOGGER.info("+++Remove Mocks ");
		orderTransactionServiceMock = null;
		genericFaultHandlerMock = null;
		salesOrderServiceMock = null;
		cancellationPublisherReferenceMock = null;
	}

	/**
	 * Invoke SIIMessageHandler - Save Original Payload from Nav
	 */
	@Test
	public void saveOriginalPayloadFromNavTest() {

		LOGGER.info("+++saveOriginalPayloadFromNavTest");

		String requestXml = readClasspathFile("sendSalesOrderUpdateToShopRequest.xml");
	    String responseXml;
	    String salesOrderRequestXml;
		LOGGER.info("+++Request " + requestXml);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "CancellationPublisherReference",
				cancellationPublisherReferenceMock);

		LOGGER.info("+++invoke SIIMessageHandler");
		responseXml = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXml));
		salesOrderRequestXml = salesOrderServiceMock.getLastReceivedRequest();
		LOGGER.info("+++response SIIMessageHandler" + responseXml);
		LOGGER.info("+++request SalesOrderService" + salesOrderRequestXml);

		assertFalse(orderTransactionServiceMock.hasBeenInvoked());
		assertFalse(genericFaultHandlerMock.hasBeenInvoked());
		
		assertTrue(salesOrderServiceMock.hasBeenInvoked());
		assertXpathEvaluatesTo("count(//nn:update/nn:base_data/nn:order[@order_number=30101002171507])", 
				"1", salesOrderRequestXml);
		assertXpathEvaluatesTo("//nn:update/nn:transaction_data/nn:actions/nn:action[@for=1]/nn:data/nn:reference_number/text()", 
				"VR13221282", salesOrderRequestXml);
		assertXpathEvaluatesTo("//nn:update/nn:transaction_data/nn:actions/nn:action[@for=1]/nn:code/text()", 
				"update.order.capture", salesOrderRequestXml);
		assertXpathEvaluatesTo("count(//nn:update/nn:transaction_data/nn:message[@type=\"order.capture\"])", 
		"1", salesOrderRequestXml);
		
		assertFalse(cancellationPublisherReferenceMock.hasBeenInvoked());

	}


}
