package de.home24.middleware.SIIMessageHandler;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;

public class receiveUpdateCommitmentFromShop extends AbstractBaseSoaTest {

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

		LOGGER.info("+++Create Mocks Generic Mocks");
		genericFaultHandlerMock = new DefaultSoapMockService("");
		salesOrderServiceMock = new DefaultSoapMockService("");
		cancellationPublisherReferenceMock = new DefaultSoapMockService("");

		declareXpathNS("env", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("nn", "NO_NAMESPACE");
		declareXpathNS("ots",
				"http://home24.de/interfaces/bes/ordertransactionservice/ordertransactionservicemessages/v1");
		declareXpathNS("ot", "http://home24.de/data/custom/ordertransaction/v1");

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
	 * Invoke SIIMessageHandler - receiveUpdateCommitmentFromShopRequest
	 */
	@Test
	public void receiveUpdateCommitmentFromShopQueueIDNotFoundTest() {

		LOGGER.info("+++receiveUpdateCommitmentFromShopQueueIDNotFoundTest");

		LOGGER.info("+++Create orderTransactionServiceMock for QueueIDNotFound");
		orderTransactionServiceMock = new DefaultSoapMockService(
				readClasspathFile("updateCommitmentOrderTransactionServiceNotFoundResponse.xml"));

		String requestXml = readClasspathFile("receiveUpdateCommitmentFromShopRequest.xml");
		String responseXml;
		String orderTransactionRequestXml;
		String orderTransactionRequestBodyXml;
		LOGGER.info("+++Request " + requestXml);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "CancellationPublisherReference",
				cancellationPublisherReferenceMock);

		LOGGER.info("+++invoke SIIMessageHandler");
		responseXml = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXml));
		orderTransactionRequestXml = orderTransactionServiceMock.getLastReceivedRequest();

		LOGGER.info("+++request orderTransactionService" + orderTransactionRequestXml);
		orderTransactionRequestBodyXml = orderTransactionRequestXml.substring(
				orderTransactionRequestXml.indexOf("<env:Body>") + 10,
				orderTransactionRequestXml.indexOf("</env:Body>"));
		LOGGER.info("+++request orderTransactionServiceBody" + orderTransactionRequestBodyXml);
		LOGGER.info("+++response SIIMessageHandler" + responseXml);

		assertTrue(orderTransactionServiceMock.hasBeenInvoked());
		assertFalse(genericFaultHandlerMock.hasBeenInvoked());
		assertFalse(cancellationPublisherReferenceMock.hasBeenInvoked());

		assertXmlEquals(readClasspathFile("updateCommitmentOrderTransactionServiceRequest.xml"),
				orderTransactionRequestBodyXml);

		assertXpathEvaluatesTo("//ots:getReferenceValueRequest/ots:referenceSearchCriteria/ot:SearchValue/text()",
				"46874002", orderTransactionRequestXml);

	}

	/**
	 * Invoke SIIMessageHandler - receiveUpdateCommitmentFromShopRequest
	 */
	@Test
	public void receiveUpdateCommitmentFromShopQID46874002Test() {

		LOGGER.info("+++receiveUpdateCommitmentFromShopQID46874002FoundTest");

		LOGGER.info("+++Create orderTransactionServiceMock for QueueIDNotFound");
		orderTransactionServiceMock = new DefaultSoapMockService(
				readClasspathFile("updateCommitmentOrderTransactionServiceQID46874002Response.xml"));

		String requestXml = readClasspathFile("receiveUpdateCommitmentFromShopRequest.xml");
		String responseXml;
		String orderTransactionRequestXml;
		String orderTransactionRequestBodyXml;
		LOGGER.info("+++Request " + requestXml);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "CancellationPublisherReference",
				cancellationPublisherReferenceMock);

		LOGGER.info("+++invoke SIIMessageHandler");
		responseXml = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXml));
		orderTransactionRequestXml = orderTransactionServiceMock.getLastReceivedRequest();
		LOGGER.info("+++request orderTransactionService" + orderTransactionRequestXml);
		orderTransactionRequestBodyXml = orderTransactionRequestXml.substring(
				orderTransactionRequestXml.indexOf("<env:Body>") + 10,
				orderTransactionRequestXml.indexOf("</env:Body>"));
		LOGGER.info("+++request orderTransactionServiceBody" + orderTransactionRequestBodyXml);
		LOGGER.info("+++response SIIMessageHandler" + responseXml);

		assertTrue(orderTransactionServiceMock.hasBeenInvoked());
		assertFalse(genericFaultHandlerMock.hasBeenInvoked());
		assertFalse(cancellationPublisherReferenceMock.hasBeenInvoked());

		assertXmlEquals(readClasspathFile("updateCommitmentOrderTransactionServiceRequest.xml"),
				orderTransactionRequestBodyXml);
		assertXpathEvaluatesTo("//ots:getReferenceValueRequest/ots:referenceSearchCriteria/ot:SearchValue /text()",
				"46874002", orderTransactionRequestXml);

	}


	/**
	 * Invoke SIIMessageHandler - receiveUpdateCommitmentFromShopRequest
	 */
	@Test
	public void receiveUpdateCommitmentFromShopErrorQIDNotFoundTest() {

		LOGGER.info("+++receiveUpdateCommitmentFromShopErrorTest");

		LOGGER.info("+++Create orderTransactionServiceMock for QueueIDNotFound");
		orderTransactionServiceMock = new DefaultSoapMockService(
				readClasspathFile("updateCommitmentOrderTransactionServiceNotFoundResponse.xml"));

		String requestXml = readClasspathFile("receiveUpdateCommitmentFromShopErrorRequest.xml");
		int orderTransactionInvokeCounter;
		LOGGER.info("+++Request " + requestXml);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "CancellationPublisherReference",
				cancellationPublisherReferenceMock);

		LOGGER.info("+++invoke SIIMessageHandler");
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXml));
		orderTransactionInvokeCounter = orderTransactionServiceMock.getNumberOfInvocations();

		assertTrue(orderTransactionServiceMock.hasBeenInvoked());
		assertFalse(genericFaultHandlerMock.hasBeenInvoked());
		assertFalse(cancellationPublisherReferenceMock.hasBeenInvoked());
		assertTrue(orderTransactionInvokeCounter==3);

	}

	/**
	 * Invoke SIIMessageHandler - receiveUpdateCommitmentFromShopRequest
	 */
	@Test
	public void receiveUpdateCommitmentFromShopErrorQID46874002Test() {

		LOGGER.info("+++receiveUpdateCommitmentFromShopErrorTest");

		LOGGER.info("+++Create orderTransactionServiceMock for QueueIDNotFound");
		orderTransactionServiceMock = new DefaultSoapMockService(
				readClasspathFile("updateCommitmentOrderTransactionServiceQID46874002Response.xml"));

		String requestXml = readClasspathFile("receiveUpdateCommitmentFromShopErrorRequest.xml");
		int orderTransactionInvokeCounter;
		LOGGER.info("+++Request " + requestXml);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "CancellationPublisherReference",
				cancellationPublisherReferenceMock);

		LOGGER.info("+++invoke SIIMessageHandler");
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXml));
		orderTransactionInvokeCounter = orderTransactionServiceMock.getNumberOfInvocations();

		assertTrue(orderTransactionServiceMock.hasBeenInvoked());
		assertFalse(genericFaultHandlerMock.hasBeenInvoked());
		assertFalse(cancellationPublisherReferenceMock.hasBeenInvoked());
		assertTrue(orderTransactionInvokeCounter==3);

	}
}
