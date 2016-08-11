package de.home24.middleware.salesorderservice;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;

public class UpdateSalesOrderLineTest extends AbstractBaseSoaTest {

	private static final Logger LOGGER = Logger.getLogger(UpdateSalesOrderLineTest.class.getSimpleName());

	private AbstractSoapMockService writeToSalesOrderQueueMock = null;
	
	private AbstractSoapMockService performErrorCallbackMock = null;
	
	@Before
	public void setUp() {

		declareXpathNS("ns3", "http://home24.de/data/navision/salesorder/v1");	
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");	
		
	}

	private void setupDefaultMockService() {
		writeToSalesOrderQueueMock = new DefaultSoapMockService();
		performErrorCallbackMock = new DefaultSoapMockService();
		
		mockOsbBusinessService(
				"SalesOrderService/operations/sendSalesOrderLineUpdateToERP/business-service/SendSalesOrderLineUpdateToERP",
				writeToSalesOrderQueueMock);
		
		mockOsbBusinessService(
				"SalesOrderService/shared/business-service/SalesOrderServiceCallback",
				performErrorCallbackMock);
	}
	
	@After
	public void tearDown()
	{
		writeToSalesOrderQueueMock = null;
	}

	@Test
	public void testUpdateSalesOrderLineValidationFails() {

		setupDefaultMockService();
		
		final String updateSOLineRequest = readClasspathFile("Request_UpdateSalesOrderLine_ValidationFails.xml");		
		
		invokeOsbProxyService("SalesOrderService/exposed/v1/SalesOrderService", updateSOLineRequest);

		waitForInvocationOf(performErrorCallbackMock);
		
		LOGGER.info(String.format("Message sent to error callback service: %s", performErrorCallbackMock.getLastReceivedRequest()));

		Assert.assertTrue(performErrorCallbackMock.hasBeenInvoked());
		Assert.assertFalse(writeToSalesOrderQueueMock.hasBeenInvoked());

		// status code and sub status code elements must be empty for this test
		assertXpathEvaluatesTo("count(//exc:category[./text()!='DataFault'])", String.valueOf(0),
				performErrorCallbackMock.getLastReceivedRequest());
	}

	@Test
	public void testUpdateSalesOrderLineWithTrackingNumber() {

		setupDefaultMockService();
		
		final String updateSOLineRequest = readClasspathFile("Request_UpdateSalesOrderLine_TrackingNumber.xml");

		invokeOsbProxyService("SalesOrderService/exposed/v1/SalesOrderService", updateSOLineRequest);

		LOGGER.info(String.format("Message sent to queue: %s", writeToSalesOrderQueueMock.getLastReceivedRequest()));

		Assert.assertTrue(writeToSalesOrderQueueMock.hasBeenInvoked());

		checkForSOFields();

		checkForTrackingNumbers();

		// status code and sub status code elements must be empty for this test
		assertXpathEvaluatesTo("count(//ns3:statusCode[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:statusCodeDescription[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:subStatusCode[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:subStatusCodeDescription[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

	}
	
	@Test
	public void testUpdateSalesOrderLineWithStatusCodes() {

		setupDefaultMockService();
		
		final String updateSOLineRequest = readClasspathFile("Request_UpdateSalesOrderLine_StatusCodes.xml");

		invokeOsbProxyService("SalesOrderService/exposed/v1/SalesOrderService", updateSOLineRequest);

		LOGGER.info(String.format("Message sent to queue: %s", writeToSalesOrderQueueMock.getLastReceivedRequest()));

		Assert.assertTrue(writeToSalesOrderQueueMock.hasBeenInvoked());

		checkForSOFields();

		// status code elements should be available
		assertXpathEvaluatesTo("count(//ns3:statusCode[./text()!=''])", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:statusCodeDescription[./text()!=''])", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		// sub status code elements must be empty for this test
		assertXpathEvaluatesTo("count(//ns3:subStatusCode[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:subStatusCodeDescription[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

	}
	
	@Test
	public void testUpdateSalesOrderLineCallFromCarrierStatusProcess() {

		setupDefaultMockService();
		
		final String updateSOLineRequest = readClasspathFile("Request_UpdateSalesOrderLine_CallFromCarrierStatusProcess.xml");

		invokeOsbProxyService("SalesOrderService/exposed/v1/SalesOrderService", updateSOLineRequest);

		LOGGER.info(String.format("Message sent to queue: %s", writeToSalesOrderQueueMock.getLastReceivedRequest()));

		Assert.assertTrue(writeToSalesOrderQueueMock.hasBeenInvoked());

		checkForSOFields();
		
		checkForTrackingNumbers();
		
		// status code elements should be available
		assertXpathEvaluatesTo("count(//ns3:statusCode[./text()!=''])", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:statusCodeDescription[./text()!=''])", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		// sub status code elements must be empty for this test
		assertXpathEvaluatesTo("count(//ns3:subStatusCode[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:subStatusCodeDescription[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

	}
	
	@Test
	public void testUpdateSalesOrderLineCallFromCloseShipmentProcess() {

		setupDefaultMockService();
		
		final String updateSOLineRequest = readClasspathFile("Request_UpdateSalesOrderLine_CallFromCloseShipmentProcess.xml");

		invokeOsbProxyService("SalesOrderService/exposed/v1/SalesOrderService", updateSOLineRequest);

		LOGGER.info(String.format("Message sent to queue: %s", writeToSalesOrderQueueMock.getLastReceivedRequest()));

		Assert.assertTrue(writeToSalesOrderQueueMock.hasBeenInvoked());

		checkForSOFields();		

		// tracking number should not be set
		assertXpathEvaluatesTo("count(//ns3:trackingNo[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:trackingNo[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		// status code elements should be available
		assertXpathEvaluatesTo("count(//ns3:statusCode[./text()!=''])", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:statusCodeDescription[./text()!=''])", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		// sub status code elements must be empty for this test
		assertXpathEvaluatesTo("count(//ns3:subStatusCode[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:subStatusCodeDescription[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

	}
	
	@Test
	public void testUpdateSalesOrderLineCallFromPurchaseOrderGroupHandlingProcess() {

		setupDefaultMockService();
		
		final String updateSOLineRequest = readClasspathFile("Request_UpdateSalesOrderLine_CallFromPurchaseOrderGroupHandlingProcess.xml");

		invokeOsbProxyService("SalesOrderService/exposed/v1/SalesOrderService", updateSOLineRequest);

		LOGGER.info(String.format("Message sent to queue: %s", writeToSalesOrderQueueMock.getLastReceivedRequest()));

		Assert.assertTrue(writeToSalesOrderQueueMock.hasBeenInvoked());

		checkForSOFields();		
		
		checkForTrackingNumbers();

		// status code elements must not be available
		assertXpathEvaluatesTo("count(//ns3:statusCode[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:statusCodeDescription[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		// sub status code elements must be empty for this test
		assertXpathEvaluatesTo("count(//ns3:subStatusCode[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:subStatusCodeDescription[./text()!=''])", String.valueOf(0),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

	}
	
	@Test
	public void testUpdateSalesOrderLineWithSubStatusCodes() {		

		setupDefaultMockService();
		
		final String updateSOLineRequest = readClasspathFile("Request_UpdateSalesOrderLine_SubStatusCodes.xml");

		invokeOsbProxyService("SalesOrderService/exposed/v1/SalesOrderService", updateSOLineRequest);

		LOGGER.info(String.format("Message sent to queue: %s", writeToSalesOrderQueueMock.getLastReceivedRequest()));

		Assert.assertTrue(writeToSalesOrderQueueMock.hasBeenInvoked());

		checkForSOFields();

		// status code and sub status code elements must be empty for this test
		assertXpathEvaluatesTo("count(//ns3:statusCode[./text()!=''])", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:statusCodeDescription[./text()!=''])", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("count(//ns3:subStatusCode[./text()!=''])", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("count(//ns3:subStatusCodeDescription[./text()!=''])", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

	}
	
	@Test (expected=RuntimeException.class)
	public void testUpdateSalesOrderLineQueueException() {
		
		DefaultSoapMockService writeToSalesOrderQueueThrowsExceptionMock = new DefaultSoapMockService()
		{
			@Override
			public String serviceCallReceived(String serviceName, String requestStr)
					throws ServiceException, Exception {
				throw new ServiceException("Queue was not available", readClasspathFile("Request_UpdateSalesOrderLine_SubStatusCodes.xml"));
			}
		};
		
		mockOsbBusinessService(
				"SalesOrderService/operations/sendSalesOrderLineUpdateToERP/business-service/SendSalesOrderLineUpdateToERP",
				writeToSalesOrderQueueThrowsExceptionMock);

		final String updateSOLineRequest = readClasspathFile("Request_UpdateSalesOrderLine_SubStatusCodes.xml");

		invokeOsbProxyService("SalesOrderService/exposed/v1/SalesOrderService", updateSOLineRequest);

	}

	private void checkForSOFields() {
		assertXpathEvaluatesTo("//ns3:salesOrderNumber/text()", "1231376876",
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		// we expect a number of 2 different lines
		assertXpathEvaluatesTo("count(//ns3:salesOrderLineNumber)", String.valueOf(2),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("//ns3:salesOrderLine[1]/ns3:salesOrderLineNumber/text()", "10000",
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("//ns3:salesOrderLine[2]/ns3:salesOrderLineNumber/text()", "20000",
				writeToSalesOrderQueueMock.getLastReceivedRequest());
	}

	private void checkForTrackingNumbers() {
		// we expect a number of 4 different tracking numbers
		assertXpathEvaluatesTo("count(//ns3:trackingNo)", String.valueOf(4),
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("//ns3:salesOrderLine[1]//ns3:trackingStatus[1]/ns3:trackingNo/text()", "44444",
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("//ns3:salesOrderLine[1]//ns3:trackingStatus[2]/ns3:trackingNo/text()", "55555",
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("//ns3:salesOrderLine[2]//ns3:trackingStatus[1]/ns3:trackingNo/text()", "66666",
				writeToSalesOrderQueueMock.getLastReceivedRequest());

		assertXpathEvaluatesTo("//ns3:salesOrderLine[2]//ns3:trackingStatus[2]/ns3:trackingNo/text()", "77777",
				writeToSalesOrderQueueMock.getLastReceivedRequest());
	}

}
