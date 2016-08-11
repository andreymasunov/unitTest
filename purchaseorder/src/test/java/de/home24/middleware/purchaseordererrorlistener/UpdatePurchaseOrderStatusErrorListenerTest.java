package de.home24.middleware.purchaseordererrorlistener;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.*;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import javax.validation.constraints.AssertTrue;

import org.bouncycastle.crypto.signers.RandomDSAKCalculator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmPo;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.BaseQuery;
import de.home24.middleware.octestframework.components.QueryPredicate;
import de.home24.middleware.octestframework.components.BaseQuery.SqlOp;
import de.home24.middleware.octestframework.components.OtmDao.Query;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class UpdatePurchaseOrderStatusErrorListenerTest extends AbstractBaseSoaTest {

	private static final Logger LOGGER = Logger
			.getLogger(UpdatePurchaseOrderStatusErrorListenerTest.class.getSimpleName());

	private final static String PATH_SERVICE = 						"PurchaseOrderErrorListener/operations/receiveUpdatePOStatusErrorMessage/ReceiveUpdatePOStatusErrorMessage";
	private final static String PATH_UPDATE_STATUS_CALLBACK = 		"PurchaseOrderErrorListener/operations/receiveUpdatePOStatusErrorMessage/business-service/UpdatePOStatusServiceCallback";
	private final static String PATH_RESPONSE_RETRY_WRAPPER = 		"PurchaseOrderErrorListener/shared/business-service/ResponseRetryWrapper";
	private final static String PATH_ORDER_TRANSACTION_MONITORING = "PurchaseOrderErrorListener/shared/business-service/OrderTransactionService";
	
	private String randomCorrelationId, randomPoNumber, randomCoboxId, randomMessageId="";
	private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
	private static final String REPLACE_PARAM_PURCHASE_ORDER_NUMBER = "PURCHASE_ORDER_NUMBER";
	private static final String REPLACE_PARAM_COMBOX_ID = "COMBOX_ID";
	private static final String REPLY_TO="REPLY_TO";
	private static final String MESSAGE_ID="MESSAGE_ID";

	private DefaultSoapMockService updatePoStatusCallbackPortMock = null;
	// private DefaultSoapMockService writeToOrderTransactionTriggerMock = null;
	/**
	 * Used to invoke generic fault handler.
	 */
	private DefaultSoapMockService responseRetryWrapperMock = null;
	private DefaultSoapMockService orderTransactionServiceFaultMock = null;
	private List<MockResponsePojo> orderTransactionServiceFaultMockPojoList = new ArrayList<MockResponsePojo>();

	@Before
	public void setUp() {
//		System.setProperty("java.io.tmpdir", "C:\\Temp\\");

		Random randomNumber = new Random();
		randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));
		randomPoNumber = "DS" + String.valueOf(randomNumber.nextInt(1000000));
		randomCoboxId = String.valueOf(randomNumber.nextInt(1000000));
		randomMessageId=String.valueOf(randomNumber.nextInt(1000000));
		
		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("navpo", "http://home24.de/data/navision/purchaseorder/v1");
		declareXpathNS("navpom", "http://home24.de/data/navision/purchaseordermessages/v1");
		declareXpathNS("posm", "http://home24.de/interfaces/bas/purchaseorderservice/purchaseorderservicemessages/v1");
		declareXpathNS("oagis", "http://www.openapplications.org/oagis/10");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("wsa05", "http://www.w3.org/2005/08/addressing");
		declareXpathNS("rrmess", "http://home24.de/interfaces/bas/responseretrywrapper/responseretrywrappermessages/v1");
		
		
		LOGGER.info("+++Create Mocks+++");

		updatePoStatusCallbackPortMock = new DefaultSoapMockService("");
		responseRetryWrapperMock = new DefaultSoapMockService("");
		orderTransactionServiceFaultMock = new DefaultSoapMockService(orderTransactionServiceFaultMockPojoList);
		orderTransactionServiceFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));

	}

	@After
	public void tearDown() {
		updatePoStatusCallbackPortMock = null;
		responseRetryWrapperMock = null;
		orderTransactionServiceFaultMock = null;
	
	}

	/**
	 * Create BAL and OSM PO logs.
	 * Invoke callback. 
	 * Exception branch should not be invoked.
	 * 
	 */
	@Test
	public void testUpdatePoStatusErrorListenerSuccesTest() {
		try{
			//Is used in soap request preparation because value is used in reply soap header.
			 final String callbackRefMockUri = mockOsbBusinessService(
			 //"SalesOrderErrorListener/operations/receiveSalesOrderLineErrorMessage/business-service/SalesOrderServiceCallback",
			 PATH_UPDATE_STATUS_CALLBACK,
			 updatePoStatusCallbackPortMock);	
			 
			 mockOsbBusinessService(
			 PATH_RESPONSE_RETRY_WRAPPER,
			 responseRetryWrapperMock);
	
			final String requestString = new ParameterReplacer(readClasspathFile("Request_UpdatePOStatusErrorListener.xml"))
					.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
					.replace(REPLACE_PARAM_PURCHASE_ORDER_NUMBER, randomPoNumber)
					.replace(REPLACE_PARAM_COMBOX_ID, randomCoboxId)
					.replace(REPLY_TO, callbackRefMockUri)
					.replace(MESSAGE_ID, randomMessageId).build();
					
	
			LOGGER.info("+++ request " + requestString);
	
			invokeOsbProxyService(PATH_SERVICE, requestString);
	
			// P1002-ORDER-SENT-ERR must be written once to BAL
			assertThat("BAL not written for P1002-ORDER-SENT-ERR",
							getOtmDao().query(createBALQuery("P1002-ORDER-SENT-ERR",randomCorrelationId)).size() == 1);
			// P1002-ORDER-SENT-ERR must be written once to OTM PO
			assertThat("OTM PO not written for P1002-ORDER-SENT-ERR",
					getOtmDao().query(createOSMPOQuery("P1002-ORDER-SENT-ERR",randomCorrelationId)).size() == 1);
	
	
			
			assertThat("updatePoStatusCallbackPortMock is not invocked!",updatePoStatusCallbackPortMock.hasBeenInvoked());
			//This should not be invoked
			assertThat("responseRetryWrapperMock should not be invocked!",!responseRetryWrapperMock.hasBeenInvoked());
	
			
			//Get message from process as it is
			String updatePoStatusCallbackPortMockMsg = updatePoStatusCallbackPortMock.getLastReceivedRequest();
			
			//Get example message and change dynamic used values.
			String updatePoStatusCallbackPortMockMsgExpected = new ParameterReplacer(readClasspathFile("Callback_UpdatePOStatusErrorListener.xml")).replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
					.replace(REPLACE_PARAM_PURCHASE_ORDER_NUMBER, randomPoNumber)
					.replace(REPLACE_PARAM_COMBOX_ID, randomCoboxId)
					.replace(REPLY_TO, callbackRefMockUri)
					.replace(MESSAGE_ID, randomMessageId).build();
					
			
			assertXmlEquals(updatePoStatusCallbackPortMockMsgExpected,	updatePoStatusCallbackPortMockMsg);
		}catch(ServiceException e) {
			fail();
		}

	}
	
	/**
	 * Send invalid message.
	 * Test error handling branch. 
	 * 
	 */
	@Test
	public void testUpdatePoStatusErrorListenerErrorHandlerTest() {
		
		
//		//Prepare OTM to fail
//		List<MockResponsePojo> responseRetryOTMserviceMockPojoList = new ArrayList<MockResponsePojo>();
//		responseRetryOTMserviceMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
//		orderTransactionServiceMock = new DefaultSoapMockService(responseRetryOTMserviceMockPojoList);
		
		
		
		//Is used in soap request preparation because value is used in reply soap header.
		 final String callbackRefMockUri = mockOsbBusinessService(
		 //"SalesOrderErrorListener/operations/receiveSalesOrderLineErrorMessage/business-service/SalesOrderServiceCallback",
		 PATH_UPDATE_STATUS_CALLBACK,
		 updatePoStatusCallbackPortMock);	
		 
		 mockOsbBusinessService(
		 PATH_RESPONSE_RETRY_WRAPPER,
		 responseRetryWrapperMock);
		 
		 
//		 mockOsbBusinessService(
//				 PATH_ORDER_TRANSACTION_MONITORING,
//				 orderTransactionServiceMock);

		final String requestString = new ParameterReplacer(readClasspathFile("ErrorRequest_UpdatePOStatusErrorListener.xml"))
				.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
				.replace(REPLACE_PARAM_PURCHASE_ORDER_NUMBER, randomPoNumber)
				.replace(REPLACE_PARAM_COMBOX_ID, randomCoboxId)
				.replace(REPLY_TO, callbackRefMockUri)
				.replace(MESSAGE_ID, randomMessageId).build();
				
		LOGGER.info("+++ request " + requestString);
		
		try{

			invokeOsbProxyService(PATH_SERVICE, requestString);
			// P1002-ORDER-SENT-ERR should not be written once to BAL
			assertThat("BAL should not written for P1002-ORDER-SENT-ERR",
							getOtmDao().query(createBALQuery("P1002-ORDER-SENT-ERR",randomCorrelationId)).size() == 0);
			// P1002-ORDER-SENT-ERR  should not be written once to OTM PO
			assertThat("OTM PO  should not be written for P1002-ORDER-SENT-ERR",
					getOtmDao().query(createOSMPOQuery("P1002-ORDER-SENT-ERR",randomCorrelationId)).size() == 0);

			//This should not be invoked
			assertThat("updatePoStatusCallbackPortMock should not be invocked!",!updatePoStatusCallbackPortMock.hasBeenInvoked());
			
			assertThat("responseRetryWrapperMock should be invocked!",responseRetryWrapperMock.hasBeenInvoked());

			
			//Get message from process as it is
			String responseRetryWrapperMockMsg = responseRetryWrapperMock.getLastReceivedRequest();
					
			System.out.println("MESG :"+responseRetryWrapperMockMsg);
			
			//HEADER check		
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:Message/mht:Type/text()",
					"Type51",
					responseRetryWrapperMockMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:Message/mht:ID/text()",
					"ID52",
					responseRetryWrapperMockMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:Message/mht:Version/text()",
					"Version53",
					responseRetryWrapperMockMsg);
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:Message/mht:ReferenceID/text()",
					"ReferenceID54",
					responseRetryWrapperMockMsg);
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:Caller/mht:SourceSystemName/text()",
					"Middleware",
					responseRetryWrapperMockMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:Caller/mht:Environment/text()",
					"Environment56",
					responseRetryWrapperMockMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:Operation/mht:OperationName/text()",
					"OperationName57",
					responseRetryWrapperMockMsg);
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:KeyValueList/mht:KeyValuePair[mht:Key/text() = 'ReplyTo']/mht:Value/text()",																										  
					callbackRefMockUri,
					responseRetryWrapperMockMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:KeyValueList/mht:KeyValuePair[mht:Key/text() = 'MessageID']/mht:Value/text()",
					randomMessageId,
					responseRetryWrapperMockMsg);
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId,
					responseRetryWrapperMockMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:ActivityID/text()",
					"P1002-ORDER-SENT-ERR",
					responseRetryWrapperMockMsg);
			
			//Exception check
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:context/exc:sourceSystemName/text()",
					"Middleware",
					responseRetryWrapperMockMsg);
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:context/exc:transactionId/text()",
					randomCorrelationId,
					responseRetryWrapperMockMsg);
				
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:context/exc:activityId/text()",
					"P1002-ORDER-SENT",
					responseRetryWrapperMockMsg);
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:context/exc:processLibraryId/text()",
					"P1002",
					responseRetryWrapperMockMsg);
			
			
			//check excetion payload exception payload
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:context/exc:payload/"
					+ "exc:exception/exc:context/exc:sourceSystemNameMAKEITWRONG/text()",
					"sourceSystemName",
					responseRetryWrapperMockMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:context/exc:payload/"
					+ "exc:exception/exc:context/exc:activityId/text()",
					"P1002-ORDER-SENT",
					responseRetryWrapperMockMsg);
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:context/exc:payload/"
					+ "exc:exception/exc:context/exc:payload/navpom:updatePurchaseOrderStatus/navpom:header/mht:CorrelationID/text()",
					randomCorrelationId,
					responseRetryWrapperMockMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:context/exc:payload/"
					+ "exc:exception/exc:context/exc:payload/navpom:updatePurchaseOrderStatus/navpom:header/mht:CorrelationID/text()",
					randomCorrelationId,
					responseRetryWrapperMockMsg);
		
		} catch(ServiceException e) {
			e.printStackTrace();
			fail();
		}

	}
	
	/**
	 * OTM will return a fault
	 * Exception branch should be invoked.
	 * Invoke callback. 
	 */
	@Test
	public void testUpdatePoStatusErrorListenerFaultTest() {
		try{
			 //Is used in soap request preparation because value is used in reply soap header.
			 final String callbackRefMockUri = mockOsbBusinessService(
					 PATH_UPDATE_STATUS_CALLBACK,
					 updatePoStatusCallbackPortMock);	
			 
			 mockOsbBusinessService(
					 PATH_ORDER_TRANSACTION_MONITORING,
					 orderTransactionServiceFaultMock);
			 
			 mockOsbBusinessService(
					 PATH_RESPONSE_RETRY_WRAPPER,
					 responseRetryWrapperMock);
	
			final String requestString = new ParameterReplacer(readClasspathFile("Request_UpdatePOStatusErrorListener.xml"))
					.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
					.replace(REPLACE_PARAM_PURCHASE_ORDER_NUMBER, randomPoNumber)
					.replace(REPLACE_PARAM_COMBOX_ID, randomCoboxId)
					.replace(REPLY_TO, callbackRefMockUri)
					.replace(MESSAGE_ID, randomMessageId).build();
					
			LOGGER.info("+++ request " + requestString);
	
			invokeOsbProxyService(PATH_SERVICE, requestString);

			assertThat("updatePoStatusCallbackPortMock is not invocked!",!updatePoStatusCallbackPortMock.hasBeenInvoked());
			//This should be invoked
			assertThat("responseRetryWrapperMock is invocked!",responseRetryWrapperMock.hasBeenInvoked());
			
			//Get message from process as it is
			String responseRetryWrapperMockMsg = responseRetryWrapperMock.getLastReceivedRequest();
			LOGGER.info("+++ responseRetryWrapperMockMsg " + responseRetryWrapperMockMsg);
			

			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId,
					responseRetryWrapperMockMsg);

			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:context/exc:transactionId/text()",
					randomCorrelationId,
					responseRetryWrapperMockMsg);
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:category/text()",
					"TechnicalFault",
					responseRetryWrapperMockMsg);
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:exception/exc:context/exc:payload/"
					+ "exc:exception/exc:context/exc:payload/navpom:updatePurchaseOrderStatus/navpom:header/mht:CorrelationID/text()",
					randomCorrelationId,
					responseRetryWrapperMockMsg);

			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:jmsDestination/text()",
					"h24jms.ERR_UpdatePOStatus_Q",
					responseRetryWrapperMockMsg);
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/rrmess:onErrorInResponseQueueRequest/rrmess:jmsFactory/text()",
					"h24jms.PurchaseOrder",
					responseRetryWrapperMockMsg);
			
		} catch(ServiceException e) {
			fail();			
		}

	}

	private BaseQuery<BalActivities> createBALQuery(String activityCode, String correlationID) {
		return new BaseQuery<>(SqlOp.SELECT,
				new QueryPredicate("correlation_id", correlationID).withEquals("activity_code", activityCode),
				BalActivities.class);
	}

	private BaseQuery<OsmPo> createOSMPOQuery(String activityCode, String correlationID) {
		return new BaseQuery<>(SqlOp.SELECT,
				new QueryPredicate("correlation_id", correlationID).withEquals("activity_code", activityCode),
				OsmPo.class);
	}

}
