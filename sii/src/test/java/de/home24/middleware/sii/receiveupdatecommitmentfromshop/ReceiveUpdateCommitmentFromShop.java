package de.home24.middleware.sii.receiveupdatecommitmentfromshop;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmCustComm;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.BaseQuery;
import de.home24.middleware.octestframework.components.BaseQuery.SqlOp;
import de.home24.middleware.octestframework.components.QueryPredicate;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.mock.RetryWithExceptionSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class ReceiveUpdateCommitmentFromShop extends AbstractBaseSoaTest {

	public static final String COMPOSITE = "SIIMessageHandler";
	public static final String REVISION = "1.2.1.1";
	public static final String PROCESS = "SIIMessageHandlerProcess";

	public static final String PARAM_STATUS = "STATUS_VALUE";
	public static final String PARAM_RESULT_DETAILS = "RESULT_DETAILS_VALUE";
	public static final String PARAM_ORDER_NUMBER = "ORDER_NUMBER";

	public static final String STATUS_VALUE_SUCCESS = "SUCCESS";
	public static final String STATUS_VALUE_ERROR = "ERROR";
	public static final String RESULT_DETAILS_VALUE_ORDER_CAPTURE = "update.order.capture";
	public static final String RESULT_DETAILS_VALUE_EMPTY = "";

	public static final String PATH_ORDERTRANSACTION_SERVICE_EXAMPLEDATA = "../../servicebus/OrderTransaction/OrderTransactionService/";
	public static final String PATH_SALESORDERSERVICE_EXAMPLEDATA = "../../servicebus/SalesOrder/SalesOrderService/";
	public static final String PATH_EXAMPLEDATA = "../../processes/GenericProcesses/SIIMessageHandler/";

	public static final String PROCESS_REQUEST_XML = PATH_EXAMPLEDATA + "ReceiveUpdateCommitmentFromShop_Request.xml";
	public static final String GET_REFERENCE_VALUE_REQUEST_XML = PATH_ORDERTRANSACTION_SERVICE_EXAMPLEDATA + "GetReferenceValue_Request.xml";
	public static final String GET_ACTIVITY_PAYLOAD_REQUEST_XML = PATH_ORDERTRANSACTION_SERVICE_EXAMPLEDATA + "GetActivityPayload_Request.xml";
	public static final String GET_EXECUTION_COUNT_REQUEST_XML = PATH_ORDERTRANSACTION_SERVICE_EXAMPLEDATA + "GetExecutionCount_Request.xml";
	public static final String RESEND_SALESORDERUPDATE_TO_SHOP_REQUEST_XML = PATH_SALESORDERSERVICE_EXAMPLEDATA + "ResendSalesOrderUpdateToShop_Request.xml";

	public static final String GET_REFERENCE_VALUE_RESPONSE_XML = PATH_ORDERTRANSACTION_SERVICE_EXAMPLEDATA + "GetReferenceValue_Response.xml";
	public static final String GET_ACTIVITY_PAYLOAD_RESPONSE_XML = PATH_ORDERTRANSACTION_SERVICE_EXAMPLEDATA + "GetActivityPayload_Response.xml";
	public static final String GET_EXECUTION_COUNT_RESPONSE_XML = PATH_ORDERTRANSACTION_SERVICE_EXAMPLEDATA + "GetExecutionCount_Response.xml";

	public static final String GET_REFERENCE_VALUE_BUSINESSFAULT_XML = PATH_ORDERTRANSACTION_SERVICE_EXAMPLEDATA + "GetReferenceValue_BusinessFault.xml";
	public static final String GET_ACTIVITY_PAYLOAD_BUSINESSFAULT_XML = PATH_ORDERTRANSACTION_SERVICE_EXAMPLEDATA + "GetActivityPayload_BusinessFault.xml";
	public static final String GET_EXECUTION_COUNT_BUSINESSFAULT_XML = PATH_ORDERTRANSACTION_SERVICE_EXAMPLEDATA + "GetExecutionCount_BusinessFault.xml";

	public static final String GENERICFAULTHANDLER_RESPONSE_RESEND_XML = PATH_EXAMPLEDATA + "GenericFaultHandler_Response_Resend.xml";

	private AbstractSoapMockService orderTransactionServiceMock = new DefaultSoapMockService();
	private AbstractSoapMockService genericFaultHandlerServiceMock = new DefaultSoapMockService();
	private AbstractSoapMockService salesOrderServiceMock = new DefaultSoapMockService();
	
	private String orderNumber = null;

	public ReceiveUpdateCommitmentFromShop() {
		super("generic");
	}

	@Before
	public void setUp() throws Exception {		
		orderNumber = Integer.toString(new Random().nextInt((int)999999999));
		
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
	}

	@Test
	public void noErrorStatusInInputAndProcessRunsWithoutExceptions() {

		orderTransactionServiceMock = new DefaultSoapMockService(readClasspathFile(GET_REFERENCE_VALUE_RESPONSE_XML));

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);

		String processRequestPayload = replaceParams(readClasspathFile(PROCESS_REQUEST_XML), STATUS_VALUE_SUCCESS,
				RESULT_DETAILS_VALUE_EMPTY, orderNumber);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processRequestPayload));

		waitForCreationOfBALEntry("P101-SIISHOPCOMMIT");

		assertThat("OrderTransactionService has not been invoked!", orderTransactionServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("GenericFaultHandlerService has been invoked!", genericFaultHandlerServiceMock.hasBeenInvoked(),
				is(Boolean.FALSE));
		assertThat("SalesOrderService has been invoked!", salesOrderServiceMock.hasBeenInvoked(), is(Boolean.FALSE));

		assertThat("P101-SIISHOPCOMMIT has not been written to the BAL table!",
				getOtmDao().query(createBALQuery("P101-SIISHOPCOMMIT")).size() == 1);
		assertThat("P101-SIISHOPCOMMIT has not been written to the OSM CUSTCOMM table!",
				getOtmDao().query(
						createOSMCustCommQuery("Shop: Customer Account Update Message Commitment received from Shop"))
						.size() == 1);

	}

	@Test
	public void getReferenceValueHasTechnicalErrorOnFirstExecutionAndSecondIsFine() {

		orderTransactionServiceMock = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.FAULT, ""),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE,
						readClasspathFile(GET_REFERENCE_VALUE_RESPONSE_XML))) {
			@Override
			public String serviceCallReceived(String serviceName, String requestStr)
					throws ServiceException, Exception {

				if (requestStr.contains("getActivityPayload")) {
					return new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
							readClasspathFile(GET_ACTIVITY_PAYLOAD_RESPONSE_XML)).getResponse();
				}

				if (requestStr.contains("getExecutionCountRequest")) {
					return new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
							readClasspathFile(GET_EXECUTION_COUNT_RESPONSE_XML)).getResponse();
				}

				return super.serviceCallReceived(serviceName, requestStr);
			}
		};

		String genericFaultHandlerResponsePayload = replaceParams(
				readClasspathFile(GENERICFAULTHANDLER_RESPONSE_RESEND_XML), new HashMap<String, String>() {
					{
						put("PAYLOAD_VALUE", readClasspathFile(GET_REFERENCE_VALUE_REQUEST_XML));
					}
				});

		genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerResponsePayload);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);

		String processRequestPayload = replaceParams(readClasspathFile(PROCESS_REQUEST_XML), STATUS_VALUE_SUCCESS,
				RESULT_DETAILS_VALUE_EMPTY, orderNumber);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processRequestPayload));

		waitForCreationOfBALEntry("P101-SIISHOPCOMMIT");
		
		
		assertThat("OrderTransactionService has not been invoked!", orderTransactionServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("GenericFaultHandlerService has not been invoked!", genericFaultHandlerServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has been invoked!", salesOrderServiceMock.hasBeenInvoked(), is(Boolean.FALSE));

		assertThat("P101-SIISHOPCOMMIT has not been written to the BAL table!",
				getOtmDao().query(createBALQuery("P101-SIISHOPCOMMIT")).size() == 1);
		assertThat("P101-SIISHOPCOMMIT has not been written to the OSM CUSTCOMM table!",
				getOtmDao().query(createOSMCustCommQuery("Shop: Customer Account Update Message Commitment received from Shop")).size() == 1);		

		assertXpathEvaluatesTo("//exc:activityId/text()",
				"P101-SIISHOPCOMMIT-ERR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:processLibraryId/text()",
				"P203",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:category/text()",
				"TechnicalFault",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:severity/text()",
				"ERROR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
	}

	@Test
	public void getReferenceValueHasBusinessErrorOnFirstExecutionAndSecondIsFine() {

		orderTransactionServiceMock = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.BUSINESS_FAULT,
						readClasspathFile(GET_REFERENCE_VALUE_BUSINESSFAULT_XML)),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(GET_REFERENCE_VALUE_RESPONSE_XML)));

		String genericFaultHandlerResponsePayload = replaceParams(
				readClasspathFile(GENERICFAULTHANDLER_RESPONSE_RESEND_XML), new HashMap<String, String>() {
					{
						put("PAYLOAD_VALUE", readClasspathFile(GET_REFERENCE_VALUE_REQUEST_XML));
					}
				});

		genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerResponsePayload);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);

		String processRequestPayload = replaceParams(readClasspathFile(PROCESS_REQUEST_XML), STATUS_VALUE_SUCCESS,
				RESULT_DETAILS_VALUE_EMPTY, orderNumber);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processRequestPayload));

		waitForCreationOfBALEntry("P101-SIISHOPCOMMIT");

		assertThat("OrderTransactionService has not been invoked!", orderTransactionServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("GenericFaultHandlerService has not been invoked!", genericFaultHandlerServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has been invoked!", salesOrderServiceMock.hasBeenInvoked(), is(Boolean.FALSE));

		assertThat("P101-SIISHOPCOMMIT has not been written to the BAL table!",
				getOtmDao().query(createBALQuery("P101-SIISHOPCOMMIT")).size() == 1);
		assertThat("P101-SIISHOPCOMMIT has not been written to the OSM CUSTCOMM table!",
				getOtmDao().query(createOSMCustCommQuery("Shop: Customer Account Update Message Commitment received from Shop")).size() == 1);	

		assertXpathEvaluatesTo("//exc:activityId/text()",
				"P101-SIISHOPCOMMIT-ERR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:processLibraryId/text()",
				"P203",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:category/text()",
				"TechnicalFault",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:severity/text()",
				"ERROR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
	}

	@Test
	public void errorStatusWhichIsNotOrderCaptureAndProcessRunsWithoutExceptions() {
		
		orderTransactionServiceMock = new DefaultSoapMockService(readClasspathFile(GET_REFERENCE_VALUE_RESPONSE_XML));

		final String processRequestPayload = replaceParams(readClasspathFile(PROCESS_REQUEST_XML), STATUS_VALUE_ERROR,
				RESULT_DETAILS_VALUE_EMPTY, orderNumber);
		
		String genericFaultHandlerResponsePayload = replaceParams(
				readClasspathFile(GENERICFAULTHANDLER_RESPONSE_RESEND_XML), new HashMap<String, String>() {
					{
						put("PAYLOAD_VALUE", processRequestPayload);
					}
				});

		genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerResponsePayload);
		
		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processRequestPayload));

		waitForInvocationOf(genericFaultHandlerServiceMock);

		assertThat("OrderTransactionService has not been invoked!", orderTransactionServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("GenericFaultHandlerService has not been invoked!", genericFaultHandlerServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has been invoked!", salesOrderServiceMock.hasBeenInvoked(), is(Boolean.FALSE));		

		assertXpathEvaluatesTo("//exc:activityId/text()",
				"P101-SIISHOPCOMMIT-ERR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:processLibraryId/text()",
				"P203",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:category/text()",
				"DataFault",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:severity/text()",
				"ERROR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
	}

	@Test
	public void orderCaptureStatusGetActivityPayloadHasBusinessErrorOnFirstExecutionAndSecondIsFine() {

		List<MockResponsePojo> mockedResponses = new ArrayList<MockResponsePojo>();
		mockedResponses.add(new MockResponsePojo(ResponseType.BUSINESS_FAULT,
						readClasspathFile(GET_ACTIVITY_PAYLOAD_BUSINESSFAULT_XML)));
		mockedResponses.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE,
						readClasspathFile(GET_ACTIVITY_PAYLOAD_RESPONSE_XML)));
		
		orderTransactionServiceMock = new DefaultSoapMockService(mockedResponses) {
			@Override
			public String serviceCallReceived(String serviceName, String requestStr)
					throws ServiceException, Exception {

				if (requestStr.contains("getReferenceValueRequest")) {
					return new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
							readClasspathFile(GET_REFERENCE_VALUE_RESPONSE_XML)).getResponse();
				}

				if (requestStr.contains("getExecutionCountRequest")) {
					return new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
							readClasspathFile(GET_EXECUTION_COUNT_RESPONSE_XML)).getResponse();
				}

				return super.serviceCallReceived(serviceName, requestStr);
			}
		};
		
		String genericFaultHandlerResponsePayload = replaceParams(
				readClasspathFile(GENERICFAULTHANDLER_RESPONSE_RESEND_XML), new HashMap<String, String>() {
					{
						put("PAYLOAD_VALUE", readClasspathFile(GET_ACTIVITY_PAYLOAD_REQUEST_XML));
					}
				});

		genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerResponsePayload);
		
		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);

		String processRequestPayload = replaceParams(readClasspathFile(PROCESS_REQUEST_XML), STATUS_VALUE_ERROR,
				RESULT_DETAILS_VALUE_ORDER_CAPTURE, orderNumber);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processRequestPayload));

		waitForCreationOfHumanTask();

		getHumanTaskDao().update(getHumanTaskDao().findByOrderId(orderNumber), "Resend");

		waitForCreationOfBALEntry("P101-SIISHOPCOMMIT");

		assertThat("OrderTransactionService has not been invoked exactly two times!",
				orderTransactionServiceMock.getNumberOfInvocations() == 2);

		assertThat("GenericFaultHandlerService has not been invoked!", genericFaultHandlerServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has not been invoked!", salesOrderServiceMock.hasBeenInvoked(), is(Boolean.TRUE));

		assertThat("P101-SIISHOPCOMMIT has not been written to the BAL table!",
				getOtmDao().query(createBALQuery("P101-SIISHOPCOMMIT")).size() == 1);
		assertThat("P101-SIISHOPCOMMIT has not been written to the OSM CUSTCOMM table!",
				getOtmDao().query(createOSMCustCommQuery("Order Capture - resending")).size() == 1);	

		assertXpathEvaluatesTo("//exc:activityId/text()",
				"P101-SIISHOPCOMMIT-ERR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:processLibraryId/text()",
				"P203",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:category/text()",
				"TechnicalFault",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:severity/text()",
				"ERROR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
	}

	@Test
	public void orderCaptureStatusGetActivityPayloadHasTechnicalErrorOnFirstExecutionAndSecondIsFine() {

		orderTransactionServiceMock = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.FAULT, ""), 
				new MockResponsePojo(ResponseType.SOAP_RESPONSE,
						readClasspathFile(GET_ACTIVITY_PAYLOAD_RESPONSE_XML))) {
			@Override
			public String serviceCallReceived(String serviceName, String requestStr)
					throws ServiceException, Exception {

				if (requestStr.contains("getReferenceValueRequest")) {
					return new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
							readClasspathFile(GET_REFERENCE_VALUE_RESPONSE_XML)).getResponse();
				}

				if (requestStr.contains("getExecutionCountRequest")) {
					return new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
							readClasspathFile(GET_EXECUTION_COUNT_RESPONSE_XML)).getResponse();
				}

				return super.serviceCallReceived(serviceName, requestStr);
			}
		};
		
		String genericFaultHandlerResponsePayload = replaceParams(
				readClasspathFile(GENERICFAULTHANDLER_RESPONSE_RESEND_XML), new HashMap<String, String>() {
					{
						put("PAYLOAD_VALUE", readClasspathFile(GET_ACTIVITY_PAYLOAD_REQUEST_XML));
					}
				});

		genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerResponsePayload);
		
		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);

		String processRequestPayload = replaceParams(readClasspathFile(PROCESS_REQUEST_XML), STATUS_VALUE_ERROR,
				RESULT_DETAILS_VALUE_ORDER_CAPTURE, orderNumber);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processRequestPayload));

		waitForCreationOfHumanTask();

		getHumanTaskDao().update(getHumanTaskDao().findByOrderId(orderNumber), "Resend");

		waitForCreationOfBALEntry("P101-SIISHOPCOMMIT");

		assertThat("OrderTransactionService has not been invoked exactly two times!",
				orderTransactionServiceMock.getNumberOfInvocations() == 2);

		assertThat("GenericFaultHandlerService has not been invoked!", genericFaultHandlerServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has not been invoked!", salesOrderServiceMock.hasBeenInvoked(), is(Boolean.TRUE));

		assertThat("P101-SIISHOPCOMMIT has not been written to the BAL table!",
				getOtmDao().query(createBALQuery("P101-SIISHOPCOMMIT")).size() == 1);
		assertThat("P101-SIISHOPCOMMIT has not been written to the OSM CUSTCOMM table!",
				getOtmDao().query(createOSMCustCommQuery("Order Capture - resending")).size() == 1);	

		assertXpathEvaluatesTo("//exc:activityId/text()",
				"P101-SIISHOPCOMMIT-ERR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:processLibraryId/text()",
				"P203",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:category/text()",
				"TechnicalFault",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:severity/text()",
				"ERROR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
	}

	@Test
	public void orderCaptureStatusGetExecutionCountHasBusinessErrorOnFirstExecutionAndSecondIsFine() {

		orderTransactionServiceMock = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.BUSINESS_FAULT,
						readClasspathFile(GET_EXECUTION_COUNT_BUSINESSFAULT_XML)),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(GET_EXECUTION_COUNT_RESPONSE_XML))) {
			@Override
			public String serviceCallReceived(String serviceName, String requestStr)
					throws ServiceException, Exception {

				if (requestStr.contains("getReferenceValueRequest")) {
					return new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
							readClasspathFile(GET_REFERENCE_VALUE_RESPONSE_XML)).getResponse();
				}

				if (requestStr.contains("getActivityPayload")) {
					return new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
							readClasspathFile(GET_ACTIVITY_PAYLOAD_RESPONSE_XML)).getResponse();
				}

				return super.serviceCallReceived(serviceName, requestStr);
			}
		};
		
		String genericFaultHandlerResponsePayload = replaceParams(
				readClasspathFile(GENERICFAULTHANDLER_RESPONSE_RESEND_XML), new HashMap<String, String>() {
					{
						put("PAYLOAD_VALUE", readClasspathFile(GET_EXECUTION_COUNT_REQUEST_XML));
					}
				});

		genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerResponsePayload);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);

		String processRequestPayload = replaceParams(readClasspathFile(PROCESS_REQUEST_XML), STATUS_VALUE_ERROR,
				RESULT_DETAILS_VALUE_ORDER_CAPTURE, orderNumber);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processRequestPayload));

		waitForCreationOfHumanTask();

		getHumanTaskDao().update(getHumanTaskDao().findByOrderId(orderNumber), "Resend");

		waitForCreationOfBALEntry("P101-SIISHOPCOMMIT");

		assertThat("OrderTransactionService has not been invoked exactly two times!",
				orderTransactionServiceMock.getNumberOfInvocations() == 2);

		assertThat("GenericFaultHandlerService has not been invoked!", genericFaultHandlerServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has not been invoked!", salesOrderServiceMock.hasBeenInvoked(), is(Boolean.TRUE));

		assertThat("P101-SIISHOPCOMMIT has not been written to the BAL table!",
				getOtmDao().query(createBALQuery("P101-SIISHOPCOMMIT")).size() == 1);
		assertThat("P101-SIISHOPCOMMIT has not been written to the OSM CUSTCOMM table!",
				getOtmDao().query(createOSMCustCommQuery("Order Capture - resending")).size() == 1);	

		assertXpathEvaluatesTo("//exc:activityId/text()",
				"P101-SIISHOPCOMMIT-ERR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:processLibraryId/text()",
				"P203",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:category/text()",
				"TechnicalFault",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:severity/text()",
				"ERROR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
	}

	@Test
	public void orderCaptureStatusGetExecutionCountHasTechnicalErrorOnFirstExecutionAndSecondIsFine() {

		orderTransactionServiceMock = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.FAULT, ""),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(GET_EXECUTION_COUNT_RESPONSE_XML))) {
			@Override
			public String serviceCallReceived(String serviceName, String requestStr)
					throws ServiceException, Exception {

				if (requestStr.contains("getReferenceValueRequest")) {
					return new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
							readClasspathFile(GET_REFERENCE_VALUE_RESPONSE_XML)).getResponse();
				}

				if (requestStr.contains("getActivityPayload")) {
					return new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
							readClasspathFile(GET_ACTIVITY_PAYLOAD_RESPONSE_XML)).getResponse();
				}

				return super.serviceCallReceived(serviceName, requestStr);
			}
		};
		
		String genericFaultHandlerResponsePayload = replaceParams(
				readClasspathFile(GENERICFAULTHANDLER_RESPONSE_RESEND_XML), new HashMap<String, String>() {
					{
						put("PAYLOAD_VALUE", readClasspathFile(GET_EXECUTION_COUNT_REQUEST_XML));
					}
				});

		genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerResponsePayload);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);

		String processRequestPayload = replaceParams(readClasspathFile(PROCESS_REQUEST_XML), STATUS_VALUE_ERROR,
				RESULT_DETAILS_VALUE_ORDER_CAPTURE, orderNumber);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processRequestPayload));

		waitForCreationOfHumanTask();

		getHumanTaskDao().update(getHumanTaskDao().findByOrderId(orderNumber), "Resend");

		waitForCreationOfBALEntry("P101-SIISHOPCOMMIT");

		assertThat("OrderTransactionService has not been invoked exactly two times!",
				orderTransactionServiceMock.getNumberOfInvocations() == 2);

		assertThat("GenericFaultHandlerService has not been invoked!", genericFaultHandlerServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has not been invoked!", salesOrderServiceMock.hasBeenInvoked(), is(Boolean.TRUE));

		assertThat("P101-SIISHOPCOMMIT has not been written to the BAL table!",
				getOtmDao().query(createBALQuery("P101-SIISHOPCOMMIT")).size() == 1);
		assertThat("P101-SIISHOPCOMMIT has not been written to the OSM CUSTCOMM table!",
				getOtmDao().query(createOSMCustCommQuery("Order Capture - resending")).size() == 1);	

		assertXpathEvaluatesTo("//exc:activityId/text()",
				"P101-SIISHOPCOMMIT-ERR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:processLibraryId/text()",
				"P203",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:category/text()",
				"TechnicalFault",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:severity/text()",
				"ERROR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
	}

	@Test
	public void orderCaptureStatusTaskReturnsResendAndResendSalesOrderUpdateToShopOperationHasTechnicalErrorOnFirstExecutionAndSecondIsFine() {

		List<MockResponsePojo> orderTransactionServiceResponses = new ArrayList<>();
		orderTransactionServiceResponses.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
				readClasspathFile(GET_REFERENCE_VALUE_RESPONSE_XML)));
		orderTransactionServiceResponses.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
				readClasspathFile(GET_ACTIVITY_PAYLOAD_RESPONSE_XML)));
		orderTransactionServiceResponses.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
				readClasspathFile(GET_EXECUTION_COUNT_RESPONSE_XML)));

		orderTransactionServiceMock = new DefaultSoapMockService(orderTransactionServiceResponses);

		salesOrderServiceMock = new RetryWithExceptionSoapMockService(1, new MockResponsePojo(ResponseType.FAULT, ""),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		
		String genericFaultHandlerResponsePayload = replaceParams(
				readClasspathFile(GENERICFAULTHANDLER_RESPONSE_RESEND_XML), new HashMap<String, String>() {
					{
						put("PAYLOAD_VALUE", readClasspathFile(RESEND_SALESORDERUPDATE_TO_SHOP_REQUEST_XML));
					}
				});

		genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerResponsePayload);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);

		String processRequestPayload = replaceParams(readClasspathFile(PROCESS_REQUEST_XML), STATUS_VALUE_ERROR,
				RESULT_DETAILS_VALUE_ORDER_CAPTURE, orderNumber);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processRequestPayload));

		waitForCreationOfHumanTask();

		getHumanTaskDao().update(getHumanTaskDao().findByOrderId(orderNumber), "Resend");

		waitForCreationOfBALEntry("P101-SIISHOPCOMMIT");

		assertThat("OrderTransactionService has not been invoked exactly three times!",
				orderTransactionServiceMock.getNumberOfInvocations() == 3);

		assertThat("GenericFaultHandlerService has not been invoked!", genericFaultHandlerServiceMock.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has not been invoked exactly twice!",
				salesOrderServiceMock.getNumberOfInvocations() == 2);

		assertThat("P101-SIISHOPCOMMIT has not been written to the BAL table!",
				getOtmDao().query(createBALQuery("P101-SIISHOPCOMMIT")).size() == 1);
		assertThat("P101-SIISHOPCOMMIT has not been written to the OSM CUSTCOMM table!",
				getOtmDao().query(createOSMCustCommQuery("Order Capture - resending")).size() == 1);	

		assertXpathEvaluatesTo("//exc:activityId/text()",
				"P101-SIISHOPCOMMIT-ERR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:processLibraryId/text()",
				"P203",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:category/text()",
				"TechnicalFault",
				genericFaultHandlerServiceMock.getLastReceivedRequest());
		
		assertXpathEvaluatesTo("//exc:severity/text()",
				"ERROR",
				genericFaultHandlerServiceMock.getLastReceivedRequest());

	}

	@Test
	public void orderCaptureStatusTaskReturnsSettled() {
		List<MockResponsePojo> orderTransactionServiceResponses = new ArrayList<>();
		orderTransactionServiceResponses.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
				readClasspathFile(GET_REFERENCE_VALUE_RESPONSE_XML)));
		orderTransactionServiceResponses.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
				readClasspathFile(GET_ACTIVITY_PAYLOAD_RESPONSE_XML)));
		orderTransactionServiceResponses.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
				readClasspathFile(GET_EXECUTION_COUNT_RESPONSE_XML)));

		orderTransactionServiceMock = new DefaultSoapMockService(orderTransactionServiceResponses);

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);

		String processRequestPayload = replaceParams(readClasspathFile(PROCESS_REQUEST_XML), STATUS_VALUE_ERROR,
				RESULT_DETAILS_VALUE_ORDER_CAPTURE, orderNumber);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processRequestPayload));

		waitForCreationOfHumanTask();

		getHumanTaskDao().update(getHumanTaskDao().findByOrderId(orderNumber), "Settled");

		waitForCreationOfBALEntry("P101-SIISHOPCOMMIT");
		
		assertThat("OrderTransactionService has not been invoked exactly three times!",
				orderTransactionServiceMock.getNumberOfInvocations() == 3);

		assertThat("GenericFaultHandlerService has been invoked!", genericFaultHandlerServiceMock.hasBeenInvoked(),
				is(Boolean.FALSE));
		assertThat("SalesOrderService has been invoked!", salesOrderServiceMock.hasBeenInvoked(), is(Boolean.FALSE));

		assertThat("P101-SIISHOPCOMMIT has not been written to the BAL table!",
				getOtmDao().query(createBALQuery("P101-SIISHOPCOMMIT")).size() == 1);
		assertThat("P101-SIISHOPCOMMIT has not been written to the OSM CUSTCOMM table!",
				getOtmDao().query(createOSMCustCommQuery("Order Capture finished manually")).size() == 1);	
	}

	/**
	 * Replaces parameters.
	 * 
	 * @param pPayload
	 *            the original payload
	 * @param pStatusValue
	 *            the status value to be replaced
	 * @param pResultDetailsValue
	 *            the result details value to be replaced
	 * @param pOrderNumber
	 *            the order number value to be replaced
	 * @return
	 */
	private String replaceParams(final String pPayload, final String pStatusValue, final String pResultDetailsValue, final String pOrderNumber) {

		return new ParameterReplacer(pPayload).replace(PARAM_STATUS, pStatusValue)
				.replace(PARAM_RESULT_DETAILS, pResultDetailsValue).replace(PARAM_ORDER_NUMBER, pOrderNumber).build();
	}

	private String replaceParams(final String pPayload, Map<String, String> mapReplacements) {

		String returnValue = pPayload;

		for (String replaceKey : mapReplacements.keySet()) {

			returnValue = new ParameterReplacer(returnValue).replace(replaceKey, mapReplacements.get(replaceKey))
					.build();
		}

		return returnValue;
	}

	private BaseQuery<BalActivities> createBALQuery(String activityCode) {
		return new BaseQuery<>(SqlOp.SELECT,
				new QueryPredicate("correlation_id", orderNumber).withEquals("activity_code", activityCode),
				BalActivities.class);
	}

	private BaseQuery<OsmCustComm> createOSMCustCommQuery(String statusCode) {
		return new BaseQuery<>(SqlOp.SELECT,
				new QueryPredicate("correlation_id", orderNumber).withEquals("status_code", statusCode),
				OsmCustComm.class);
	}

	private final void waitForCreationOfHumanTask() {
		waitForCreationOfHumanTask(25);
	}

	private final void waitForCreationOfHumanTask(int pMaxWaitTime) {
		try {
			int counter = 0;
			while ((getHumanTaskDao().findByOrderId(orderNumber) == null) && (counter++ < pMaxWaitTime)) {
				Thread.sleep(1000L);
			}
		} catch (Exception e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
	}
	
	private final void waitForCreationOfBALEntry(String activityCode)
	{
		waitForCreationOfBALEntry(25, activityCode);
	}

	private final void waitForCreationOfBALEntry(int pMaxWaitTime, String activityCode) {
		
		try {
			int counter = 0;
			while (getOtmDao().query(new BaseQuery<>(SqlOp.SELECT,
					new QueryPredicate("correlation_id", orderNumber).withEquals("activity_code", activityCode),
					BalActivities.class)).isEmpty() && (counter++ < pMaxWaitTime)) {
				Thread.sleep(1000L);
			}
		} catch (Exception e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}	
	}

}
