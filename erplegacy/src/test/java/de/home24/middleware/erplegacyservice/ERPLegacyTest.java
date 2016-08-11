package de.home24.middleware.erplegacyservice;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmSo;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.OtmDao.Query;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class ERPLegacyTest extends AbstractBaseSoaTest {

	private static final String COMPARATION_KEY_STATUS_TEXT = "StatusText";
	private static final String COMPARATION_KEY_STATUS_CODE = "StatusCode";
	private static final String COMPARATION_KEY_ERP_ITEM_ID = "ErpItemId";

	private static final String COMPARATION_KEY_SALESORDERITEMID = "PurchaseOrderItemId";
	private static final String COMPARATION_KEY_SALESORDERID = "PurchaseOrderId";
	//
	private static final Logger LOGGER = Logger.getLogger(ERPLegacyTest.class.getSimpleName());
	private final static String PATH_SERVICE = "ERPLegacyService/exposed/v1/ERPLegacyProxyService";
	// private final static String PATH_CALL_ORDER_TRANSACTION_SERVICE =
	// "ERPLegacyService/shared/business-service/CallOrderTransactionService";
	private final static String PATH_WRITE_TO_SALES_ORDER_QUEUE = "ERPLegacyService/operations/createDocumentRequest/business-service/WriteToSalesOrderQueue";
	private final static String PATH_WRITE_TO_CUSTOMER_QUEUE = "ERPLegacyService/operations/createAccountRequest/business-service/WriteToCustomerQueue";
	private final static String PATH_WRITE_TO_PAYMENT_QUEUE = "ERPLegacyService/operations/updatePaymentRequest/business-service/WriteToPaymentQueue";

	// private static final String PATH_VENDOR_SERVICE =
	// "VendorTransmissionService/exposed/v1/VendorTransmissionService";

	// private static final String PATH_REQ_ORDERSP_Q =
	// "VendorTransmissionService/operations/FwdOrdRspToERP/business-service/FwdOrdRsp";
	// private static final String PATH_VENDOR_TRANSMISSION_CALLBACK =
	// "VendorTransmissionService/shared/business-service/VendorTransmissionCallbackRef";
	private static final String RPLC_CORRELATION_ID = "CORRELATION_ID";
	private static final String RPLC_PAYMENT_ID = "PAYMENT_ID";
	private static final String RPLC_MESSAGE_ID = "MESSAGE_ID";
	private static final String RPLC_REPLY_TO = "REPLY_TO";
	private static final String RPLC_PAYLOAD = "PAYLOAD";

	private String randomCorrelationId, randomPaymentID = "";

	public static final String RESOURCES_PATH_SB_RESOURCES = "../servicebus/ERPLegacyService/ERPLegacyService";
	// public static final String RESOURCES_PATH_EXAMPLES =
	// "../examples/iwofurn/DESADV";
	// public static final String RESOURCES_PATH_QUEUES =
	// "../queues/h24_Edifact/";

	private DefaultSoapMockService writeSalesOrderQueueMock = null;
	private DefaultSoapMockService writeToCustomerQueueMock = null;
	private DefaultSoapMockService writeToPaymentQueueMock = null;
	private DefaultSoapMockService writeToPaymentErrorQueueMock = null;
	
	

	@Before
	public void setUp() {

		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("wsa05", "http://www.w3.org/2005/08/addressing");
		declareXpathNS("exp", "http://home24.de/data/common/exceptiontypes/v1");

		declareXpathNS("navcws", "urn:microsoft-dynamics-schemas/codeunit/NavConnectorWS");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("webshop", "http://schema.mp-gruppe.de/NAVconnect/Webshop/");

		// SetupRandoms
		randomCorrelationId = System.currentTimeMillis() + "";
		randomPaymentID = System.currentTimeMillis() + "";

		// Clean OTMs
		getOtmDao().delete(createDeleteQueryForTable("bal_activities", randomCorrelationId));
		getOtmDao().delete(createDeleteQueryForTable("osm_so", randomCorrelationId));
		getOtmDao().delete(createDeleteQueryForTable("osm_cust", randomCorrelationId));

		LOGGER.info("+++Setup Mocks+++");

		writeSalesOrderQueueMock = new DefaultSoapMockService("");
		writeToCustomerQueueMock = new DefaultSoapMockService("");
		writeToPaymentQueueMock = new DefaultSoapMockService("");
		
		mockOsbBusinessService(PATH_WRITE_TO_SALES_ORDER_QUEUE, writeSalesOrderQueueMock);
		mockOsbBusinessService(PATH_WRITE_TO_CUSTOMER_QUEUE, writeToCustomerQueueMock);
	}

	@After
	public void tearDown() {
		writeSalesOrderQueueMock = null;
		writeToCustomerQueueMock = null;
		writeToPaymentQueueMock = null;
	}

	@Test
	public void processPaymentUpdaterequestOSMPOshouldBeCreated() {
		
		mockOsbBusinessService(PATH_WRITE_TO_PAYMENT_QUEUE, writeToPaymentQueueMock);

		final String paymentUpdateRequest = new ParameterReplacer(
				readClasspathFile(RESOURCES_PATH_SB_RESOURCES + "/PaymentUpdateRequest.xml"))
						.replace(RPLC_CORRELATION_ID, randomCorrelationId).replace(RPLC_PAYMENT_ID, randomPaymentID)
						.build();

		final String expectedSuccessResponse = new ParameterReplacer(
				readClasspathFile(RESOURCES_PATH_SB_RESOURCES + "/ErpLegacySuccessResponse.xml")).build();

		LOGGER.info("+++paymentUpdateRequest :" + paymentUpdateRequest);

		final String response = invokeOsbProxyService(PATH_SERVICE, paymentUpdateRequest);

		waitForInvocationOf(writeToPaymentQueueMock, 5);

		assertThat("writeToCustomerQueueMock should not been invoked!",				writeToCustomerQueueMock.hasBeenInvoked() == false);
		assertThat("writeSalesOrderQueueMock should not been invoked!",				writeSalesOrderQueueMock.hasBeenInvoked() == false);
		assertThat("OTM SO not written for P101-PAY", getOtmDao()
				.query(new QueryOSMSOByCorrelationIDAndActivityCode(randomCorrelationId, "P101-PAY")).size() == 1);

		System.out.println("+++ " + writeToPaymentQueueMock.getLastReceivedRequest());

		assertThat("Header creation date must exists",
				evaluateXpath("//navcws:ProcessRequest/navcws:header/mht:Message/mht:CreationDate",
						writeToPaymentQueueMock.getLastReceivedRequest()) != "");
		assertThat("Version must exists", evaluateXpath("//navcws:ProcessRequest/navcws:header/mht:Message/mht:Version",
				writeToPaymentQueueMock.getLastReceivedRequest()) != "");

		assertThat("orderNo des not have expected value",
				evaluateXpath(
						"//navcws:ProcessRequest/navcws:requestText/Transfer/Body/PaymentUpdateRequest/Payment/OrderNo/text()",
						writeToPaymentQueueMock.getLastReceivedRequest()).equals(randomCorrelationId));
		assertThat("TransactionNo des not have expected value",
				evaluateXpath(
						"//navcws:ProcessRequest/navcws:requestText/Transfer/Body/PaymentUpdateRequest/Payment/OrderNo/text()",
						writeToPaymentQueueMock.getLastReceivedRequest()).equals(randomPaymentID));
		assertThat("PaymentStatus des not have expected value : pre-authorized ",
				evaluateXpath(
						"//navcws:ProcessRequest/navcws:requestText/Transfer/Body/PaymentUpdateRequest/Payment/PaymentStatus/text()",
						writeToPaymentQueueMock.getLastReceivedRequest()).equals("pre-authorized"));
		assertThat("PaymentStatus must exists ",
				evaluateXpath(
						"//navcws:ProcessRequest/navcws:requestText/Transfer/Body/PaymentUpdateRequest/Payment/UpdateDate/text()",
						writeToPaymentQueueMock.getLastReceivedRequest()) != "");

		//// navcws:ProcessRequest/navcws:requestText/Transfer/Header/@AppDom is
		//// not working correctly
		System.out.println("+++" + evaluateXpath("//navcws:ProcessRequest/navcws:requestText/Transfer/Header[@AppDom]",
				writeToPaymentQueueMock.getLastReceivedRequest()));
		assertThat("AppDom des not have expected value : 1 ",
				evaluateXpath("//navcws:ProcessRequest/navcws:requestText/Transfer/Header[@AppDom]",
						writeToPaymentQueueMock.getLastReceivedRequest()) != (""));
		assertThat("Object des not have expected value : RemoteProcedureCall ",
				evaluateXpath("//navcws:ProcessRequest/navcws:requestText/Transfer/Header[@object]",
						writeToPaymentQueueMock.getLastReceivedRequest()) != (""));
		assertThat("action des not have expected value : Request ",
				evaluateXpath("//navcws:ProcessRequest/navcws:requestText/Transfer/Header[@action]",
						writeToPaymentQueueMock.getLastReceivedRequest()) != (""));

		// Assert expected exception result
		assertXmlEquals(response, expectedSuccessResponse);

	}

	@Test
	public void processCreateCustomerRequestOSMCUSTShouldBeCreated() {
		mockOsbBusinessService(PATH_WRITE_TO_PAYMENT_QUEUE, writeToPaymentQueueMock);
		
		final String createCustomerRequest = new ParameterReplacer(
				readClasspathFile(RESOURCES_PATH_SB_RESOURCES + "/CreateAccountRequest.xml"))
						.replace(RPLC_CORRELATION_ID, randomCorrelationId).build();
		final String expectedSuccessResponse = new ParameterReplacer(
				readClasspathFile(RESOURCES_PATH_SB_RESOURCES + "/ErpLegacySuccessResponse.xml")).build();

		LOGGER.info("+++CreateCustomerRequest :" + createCustomerRequest);

		final String response = invokeOsbProxyService(PATH_SERVICE, createCustomerRequest);

		waitForInvocationOf(writeToCustomerQueueMock, 5);

		assertThat("writeToPaymentQueueMock should not been invoked!",
				writeToPaymentQueueMock.hasBeenInvoked() == false);
		assertThat("writeSalesOrderQueueMock should not been invoked!",
				writeSalesOrderQueueMock.hasBeenInvoked() == false);
		assertThat("OTM SO not written for P101-CUST",
				getOtmDao().query(new QueryOSMCustomerByCorrelationIDAndActivityCode(randomCorrelationId, "P101-CUST"))
						.size() == 1);

		assertThat("Header creation date must exists",
				evaluateXpath("//navcws:ProcessRequest/navcws:header/mht:Message/mht:CreationDate",
						writeToCustomerQueueMock.getLastReceivedRequest()) != "");
		assertThat("Version must exists", evaluateXpath("//navcws:ProcessRequest/navcws:header/mht:Message/mht:Version",
				writeToCustomerQueueMock.getLastReceivedRequest()) != "");

		// status code and sub status code elements must be empty for this test
		assertXpathEvaluatesTo(
				"count(//navcws:ProcessRequest/navcws:requestText/Transfer/Body/CreateAccountRequest/Account)",
				String.valueOf(1), writeToCustomerQueueMock.getLastReceivedRequest());
		// Assert expected exception result
		assertXmlEquals(response, expectedSuccessResponse);
	}

	@Test
	public void processCreateDocumentRequestOSMCUSTShouldBeCreated() {

		mockOsbBusinessService(PATH_WRITE_TO_PAYMENT_QUEUE, writeToPaymentQueueMock);
		
		final String CreateDocumentRequest = new ParameterReplacer(
				readClasspathFile(RESOURCES_PATH_SB_RESOURCES + "/CreateDocumentRequest.xml"))
						.replace(RPLC_CORRELATION_ID, randomCorrelationId).replace(RPLC_PAYMENT_ID, randomPaymentID)
						.build();

		final String expectedSuccessResponse = new ParameterReplacer(
				readClasspathFile(RESOURCES_PATH_SB_RESOURCES + "/ErpLegacySuccessResponse.xml")).build();

		LOGGER.info("+++CreateDocumentRequest :" + CreateDocumentRequest);

		final String response = invokeOsbProxyService(PATH_SERVICE, CreateDocumentRequest);

		waitForInvocationOf(writeSalesOrderQueueMock, 30);
		assertThat("writeSalesOrderQueueMock have to bee invoked ",writeSalesOrderQueueMock.hasBeenInvoked());
				
		assertThat("writeToPaymentQueueMock should not been invoked!",				writeToPaymentQueueMock.hasBeenInvoked() == false);
		assertThat("writeToCustomerQueueMock should not been invoked!",				writeToCustomerQueueMock.hasBeenInvoked() == false);
		assertThat("OTM SO not written for P101-SO-INIT", getOtmDao()
				.query(new QueryOSMSOByCorrelationIDAndActivityCode(randomCorrelationId, "P101-SO-INIT")).size() == 1);

		assertThat("Header creation date must exists",				evaluateXpath("//navcws:ProcessRequest/navcws:header/mht:Message/mht:CreationDate",
						writeSalesOrderQueueMock.getLastReceivedRequest()) != "");
		assertThat("Version must exists",		 evaluateXpath("//navcws:ProcessRequest/navcws:header/mht:Message/mht:Version",
				writeSalesOrderQueueMock.getLastReceivedRequest()) != "");
		writeSalesOrderQueueMock.getLastReceivedRequest();
		// status code and sub status code elements must be empty for this test
		assertXpathEvaluatesTo(
				"count(//navcws:ProcessRequest/navcws:requestText/Transfer/Body/CreateDocumentRequest/Document)",
				String.valueOf(1), writeSalesOrderQueueMock.getLastReceivedRequest());
		// Assert expected exception result
		assertXmlEquals(response, expectedSuccessResponse);
	}

	@Test
	public void processUnsupportedMessageTypeCasePreparedResponseIsReturned() {
		mockOsbBusinessService(PATH_WRITE_TO_PAYMENT_QUEUE, writeToPaymentQueueMock);
		
		final String unsupportedTypeRequest = new ParameterReplacer(
				readClasspathFile(RESOURCES_PATH_SB_RESOURCES + "/UnsupportedMessageTypeRequest.xml")).build();

		final String expectedUnsupportedTypeResponse = new ParameterReplacer(
				readClasspathFile(RESOURCES_PATH_SB_RESOURCES + "/UnsupportedMessageTypeResponse.xml")).build();

		LOGGER.info("+++CreateDocumentRequest :" + unsupportedTypeRequest);

		String response = invokeOsbProxyService(PATH_SERVICE, unsupportedTypeRequest);

		assertThat("writeSalesOrderQueueMock should not been invoked!",				writeSalesOrderQueueMock.hasBeenInvoked() == false);
		assertThat("writeToPaymentQueueMock should not been invoked!",				writeToPaymentQueueMock.hasBeenInvoked() == false);
		assertThat("writeToCustomerQueueMock should not been invoked!",				writeToCustomerQueueMock.hasBeenInvoked() == false);

		// Assert expected exception result
		assertXmlEquals(response, expectedUnsupportedTypeResponse);

	}

	@Test
	public void processPaymentUpdateExceptionOnEnqueueTestExceptionHandling() {
		writeToPaymentErrorQueueMock = new DefaultSoapMockService(Lists.newArrayList(new MockResponsePojo(ResponseType.FAULT , "","")));
		mockOsbBusinessService(PATH_WRITE_TO_PAYMENT_QUEUE, writeToPaymentErrorQueueMock);
		
		final String paymentUpdateRequest = new ParameterReplacer(
				readClasspathFile(RESOURCES_PATH_SB_RESOURCES + "/PaymentUpdateRequest.xml"))
						.replace(RPLC_CORRELATION_ID, randomCorrelationId).replace(RPLC_PAYMENT_ID, randomPaymentID)
						.build();	
		
		// Prepare error pMockedResponses
		
		final String expectedResponseInErrorCase = new ParameterReplacer(
				readClasspathFile(RESOURCES_PATH_SB_RESOURCES + "/ErrorPaymentUpdateResponse.xml"))
						.replace(RPLC_CORRELATION_ID, randomCorrelationId).replace(RPLC_PAYMENT_ID, randomPaymentID)
						.build();

		LOGGER.info("+++paymentUpdateRequest :" + paymentUpdateRequest);
		
		String response = invokeOsbProxyService(PATH_SERVICE, paymentUpdateRequest);
															  

		waitForInvocationOf(writeToPaymentErrorQueueMock, 5);

		assertThat("writeToPaymentQueueMock has not been invoked!",
				writeToPaymentErrorQueueMock.hasBeenInvoked() == true);	// Is invoked
																	// but error
																	// occurs
		assertThat("writeToCustomerQueueMock should not been invoked!",				writeToCustomerQueueMock.hasBeenInvoked() == false);
		assertThat("writeSalesOrderQueueMock should not been invoked!",				writeSalesOrderQueueMock.hasBeenInvoked() == false);

		// OTM should not be created
		assertThat("OTM SO should not been written for P101-PAY", getOtmDao()
				.query(new QueryOSMSOByCorrelationIDAndActivityCode(randomCorrelationId, "P101-PAY")).size() == 0);

		// Assert expected exception result
		assertXmlEquals(response, expectedResponseInErrorCase);

	}



	private class QueryBALByCorrelationIDAndActivityCode implements Query<BalActivities> {
		private String correlationID = null;
		private String activityCode = null;

		QueryBALByCorrelationIDAndActivityCode(String correlationID, String activityCode) {
			this.activityCode = activityCode;
		}

		@Override
		public Class<BalActivities> getExpectedType() {
			return BalActivities.class;
		}

		@Override
		public String getQuery() {
			return String.format("select * from bal_activities where correlation_id = ? and activity_code=?");
		}

		@Override
		public Object[] getQueryParameters() {
			String[] params = { correlationID, activityCode };
			return params;
		}
	}

	private class QueryOSMSOByCorrelationIDAndActivityCode implements Query<OsmSo> {
		private String correlationID = null;
		private String activityCode = null;

		QueryOSMSOByCorrelationIDAndActivityCode(String correlationID, String activityCode) {
			this.activityCode = activityCode;
			this.correlationID = correlationID;
		}

		@Override
		public Class<OsmSo> getExpectedType() {
			return OsmSo.class;
		}

		@Override
		public String getQuery() {
			return String.format("select * from osm_so where correlation_id = ? and status_code=?");
		}

		@Override
		public Object[] getQueryParameters() {
			String[] params = { correlationID, activityCode };
			return params;
		}
	}

	private class QueryOSMCustomerByCorrelationIDAndActivityCode implements Query<OsmSo> {
		private String correlationID = null;
		private String activityCode = null;

		QueryOSMCustomerByCorrelationIDAndActivityCode(String correlationID, String activityCode) {
			this.activityCode = activityCode;
			this.correlationID = correlationID;
		}

		@Override
		public Class<OsmSo> getExpectedType() {
			return OsmSo.class;
		}

		@Override
		public String getQuery() {
			return String.format("select * from osm_cust where correlation_id = ? and status_code=?");
		}

		@Override
		public Object[] getQueryParameters() {
			String[] params = { correlationID, activityCode };
			return params;
		}
	}

	private Query<Void> createDeleteQueryForTable(final String pTablename, final String correlationID) {
		return new Query<Void>() {

			@Override
			public String getQuery() {
				return String.format("delete from %s where correlation_id = ?", pTablename);
			}

			@Override
			public Object[] getQueryParameters() {
				return new Object[] { correlationID };
			}

			@Override
			public Class<Void> getExpectedType() {
				return Void.class;
			}
		};
	}

	private String getSoapRequest(final String pMessageId, final String pCallbackUrl, final String pRequestString) {
		return SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, pRequestString),
				SoapUtil.getInstance().messageIdHeader(pMessageId), SoapUtil.getInstance().relatesToHeader(pMessageId),
				SoapUtil.getInstance().replyToHeader(pCallbackUrl));
	}
}
