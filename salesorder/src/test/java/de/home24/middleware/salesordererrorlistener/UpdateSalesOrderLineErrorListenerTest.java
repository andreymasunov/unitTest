package de.home24.middleware.salesordererrorlistener;

import static org.junit.Assert.assertTrue;

import java.util.Properties;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import utils.system;

public class UpdateSalesOrderLineErrorListenerTest extends AbstractBaseSoaTest {

	private static final Logger LOGGER = Logger.getLogger(UpdateSalesOrderLineErrorListenerTest.class.getSimpleName());
	private static final String RESOURCES_PATH_SB_SALESORDERERRORLISTENER = "../servicebus/SalesOrder/SalesOrderErrorListener";
    
	private String callbackMockUri;
	private DefaultSoapMockService writeToSalesOrderCallbackPortMock = null;
	private DefaultSoapMockService writeToGenericFaultHandlerMock = null;
//	private DefaultSoapMockService writeToOrderTransactionTriggerMock = null;

	@BeforeClass
	public static void setUpBeforeClass() {

		testInitialization();
	}

	@Before
	public void setUp() {
		
		writeToSalesOrderCallbackPortMock = new DefaultSoapMockService();
		writeToGenericFaultHandlerMock = new DefaultSoapMockService();
//		writeToOrderTransactionTriggerMock = new DefaultSoapMockService(
//				readClasspathFile(
//						String.format("%s/RSP_ForwardDesAdv_Q/ReceiveDesAdvACKFromERP.xml", RESOURCES_PATH_SB_SALESORDERERRORLISTENER)));
		
		callbackMockUri =  mockOsbBusinessService(
				"SalesOrderErrorListener/operations/receiveSalesOrderLineErrorMessage/business-service/SalesOrderServiceCallback",
				writeToSalesOrderCallbackPortMock);		

		mockOsbBusinessService(
				"SalesOrderErrorListener/shared/business-service/GenericFaultHandlerService",
				writeToGenericFaultHandlerMock);	

//		mockOsbBusinessService(
//				"SalesOrderErrorListener/shared/business-service/OrderTransactionTrigger",
//				writeToOrderTransactionTriggerMock);
		
		declareXpathNS("soservicemessages", "http://home24.de/interfaces/bas/salesorderservice/salesorderservicemessages/v1");
		declareXpathNS("mhtypes", "http://home24.de/data/common/messageheadertypes/v1");			
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");		
		declareXpathNS("somessages", "http://home24.de/data/navision/salesordermessages/v1");
		
		setOsbServiceReplacement(
			    "SalesOrderErrorListener/operations/receiveSalesOrderLineErrorMessage/pipeline/ReceiveSalesOrderLineErrorQueuePipeline",
			    "\\$replyToURI", String.format("'%s'", callbackMockUri));
	}
	
	@After
	public void tearDown()
	{
		writeToSalesOrderCallbackPortMock = null;
		writeToGenericFaultHandlerMock = null;
//		writeToOrderTransactionTriggerMock = null;
	}


	@Test
	public void testSalesOrderLineCallbackValidInput() {

		final String requestString = readClasspathFile(
				String.format("%s/Request_UpdateSalesOrderLineErrorListener.xml", RESOURCES_PATH_SB_SALESORDERERRORLISTENER));


		invokeOsbProxyService(
				"SalesOrderErrorListener/operations/receiveSalesOrderLineErrorMessage/ReceiveSalesOrderLineErrorMessage",
						requestString);		
	
		waitForInvocationOf(writeToSalesOrderCallbackPortMock);		
		assertTrue(writeToSalesOrderCallbackPortMock.hasBeenInvoked());
		assertXpathEvaluatesTo("//exc:exception/exc:context/exc:activityId/text()", 
				"P1001-UPD-SO-LINE-ERR", 
				writeToSalesOrderCallbackPortMock.getLastReceivedRequest());

	}

}
