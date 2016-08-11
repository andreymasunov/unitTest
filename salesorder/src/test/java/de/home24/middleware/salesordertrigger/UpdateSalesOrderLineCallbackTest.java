package de.home24.middleware.salesordertrigger;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;

public class UpdateSalesOrderLineCallbackTest extends AbstractBaseSoaTest {

	private static final Logger LOGGER = Logger.getLogger(UpdateSalesOrderLineCallbackTest.class.getSimpleName());

	private DefaultSoapMockService writeToSalesOrderCallbackPortMock = null;
	private DefaultSoapMockService writeToSalesOrderErrorListenerQueueMock = null;
	
	@Before
	public void setUp() {
		
		writeToSalesOrderCallbackPortMock = new DefaultSoapMockService();
		writeToSalesOrderErrorListenerQueueMock = new DefaultSoapMockService();
		
		mockOsbBusinessService(
				"SalesOrderTrigger/operations/receiveSalesOrderLineUpdated/business-service/SalesOrderServiceCallbackPort",
				writeToSalesOrderCallbackPortMock);		

		mockOsbBusinessService(
				"SalesOrderTrigger/operations/receiveSalesOrderLineUpdated/business-service/sendSalesOrderLineUpdateToErrorQueue",
				writeToSalesOrderErrorListenerQueueMock);
		
		declareXpathNS("soservicemessages", "http://home24.de/interfaces/bas/salesorderservice/salesorderservicemessages/v1");
		declareXpathNS("mhtypes", "http://home24.de/data/common/messageheadertypes/v1");			
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");		
		declareXpathNS("somessages", "http://home24.de/data/navision/salesordermessages/v1");
	}
	
	@After
	public void tearDown()
	{
		writeToSalesOrderCallbackPortMock = null;
		writeToSalesOrderErrorListenerQueueMock = null;
	}

	@Test
	public void testSalesOrderLineCallbackInvalidInput() {

		final String requestString = readClasspathFile("Request_UpdateSalesOrderLineCallbackInvalid.xml");

		invokeOsbProxyService(
				"SalesOrderTrigger/operations/receiveSalesOrderLineUpdated/ReceiveSalesOrderLineUpdated",
				SoapUtil.getInstance().soapEnvelope(
						SoapVersion.SOAP11,
						requestString));		

		Assert.assertFalse(writeToSalesOrderCallbackPortMock.hasBeenInvoked());
		Assert.assertTrue(writeToSalesOrderErrorListenerQueueMock.hasBeenInvoked());
		
		String requestReceivedFromBusinessService = writeToSalesOrderErrorListenerQueueMock.getLastReceivedRequest();

		LOGGER.info(String.format("Message sent to queue: %s", writeToSalesOrderErrorListenerQueueMock.getLastReceivedRequest()));
		
		assertXpathEvaluatesTo("count(//exc:exception)", String.valueOf(1),
				requestReceivedFromBusinessService);
		
		// original payload must be within the exception payload
		assertXpathEvaluatesTo("count(//exc:exception/exc:context/exc:payload/somessages:updateSalesOrderLineCallback)", String.valueOf(1),
				requestReceivedFromBusinessService);
		
		assertXpathEvaluatesTo("count(//exc:exception/exc:category[text()='TriggerFault'])", String.valueOf(1),
				requestReceivedFromBusinessService);

		assertXpathEvaluatesTo("count(//exc:exception/exc:faultInfo/exc:faultMessage[text()='Validierung für OSB-Aktion \"Validieren\" nicht erfolgreich'])", String.valueOf(1),
				requestReceivedFromBusinessService);

		assertXpathEvaluatesTo("//exc:exception/exc:context/exc:transactionId/text()", String.valueOf(1231376876),
				requestReceivedFromBusinessService);
	}

	@Test
	public void testSalesOrderLineCallbackValidInput() {

		final String requestString = readClasspathFile("Request_UpdateSalesOrderLineCallback.xml");

		invokeOsbProxyService(
				"SalesOrderTrigger/operations/receiveSalesOrderLineUpdated/ReceiveSalesOrderLineUpdated",
				SoapUtil.getInstance().soapEnvelope(
						SoapVersion.SOAP11,
						requestString));		

		String requestReceivedFromBusinessService = writeToSalesOrderCallbackPortMock.getLastReceivedRequest();

		LOGGER.info(String.format("Message sent to queue: %s", writeToSalesOrderCallbackPortMock.getLastReceivedRequest()));

		Assert.assertTrue(writeToSalesOrderCallbackPortMock.hasBeenInvoked());
		
		assertXpathEvaluatesTo("count(//soservicemessages:updateSalesOrderLineResponse)", String.valueOf(1),
				requestReceivedFromBusinessService);
		
		assertXpathEvaluatesTo("count(//soservicemessages:updateSalesOrderLineResponse/soservicemessages:responseHeader)", String.valueOf(1),
				requestReceivedFromBusinessService);
		
		// we expect three key value elements in the list
		assertXpathEvaluatesTo("count(//soservicemessages:updateSalesOrderLineResponse/soservicemessages:responseHeader/mhtypes:KeyValueList/*)", String.valueOf(3),
				requestReceivedFromBusinessService);
		
		assertXpathEvaluatesTo("count(//soservicemessages:updateSalesOrderLineResponse/soservicemessages:responseHeader/mhtypes:ActivityID[./text()='P1001-UPD-SO-LINE'])", String.valueOf(1),
				requestReceivedFromBusinessService);
		
		assertXpathEvaluatesTo("count(//mhtypes:KeyValuePair[./mhtypes:Key/text()='SalesOrderNumber']/mhtypes:Value[text()='1231376876'])", String.valueOf(1),
				requestReceivedFromBusinessService);
		
		assertXpathEvaluatesTo("count(//mhtypes:KeyValuePair[./mhtypes:Key/text()='ReplyTo']/mhtypes:Value[text()='https://fmw-testing/dummyurl'])", String.valueOf(1),
				requestReceivedFromBusinessService);
		
		assertXpathEvaluatesTo("count(//mhtypes:KeyValuePair[./mhtypes:Key/text()='MessageID']/mhtypes:Value[text()='urn:0913476823476283476283476'])", String.valueOf(1),
				requestReceivedFromBusinessService);


	}

}
