package de.home24.middleware.purchaseorderinformvendorprocess;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.components.BaseQuery;
import de.home24.middleware.octestframework.components.BaseQuery.SqlOp;
import de.home24.middleware.octestframework.components.QueryPredicate;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.GenericFaultHandlerMock;
import de.home24.middleware.octestframework.mock.GenericFaultHandlerMock.FaultStrategy;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Tests regarding error handling in the PurchaseOrderInformVendorProcess.
 * 
 * Open points: - Payload validations for MockServices - Validation of OTM
 * entries
 * 
 * @author svb
 *
 */
public class InformVendorProcessMockTests extends AbstractBaseSoaTest {

    public static final String COMPOSITE = "PurchaseOrderInformVendorProcess";
    public static final String REVISION = "1.4.0.0";
    public static final String PROCESS = "PurchaseOrderInformVendorDelegator_ep";

    private static final String PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE = "../servicebus/Notification/NotificationService/sendPurchaseOrderMail/SendPurchaseOrderMailResponse.xml";
    private static final String INFORM_VENDOR_PROCESS_ENDPOINT_URL = "http://localhost:7101/soa-infra/services/dropship/PurchaseOrderInformVendorProcess/PurchaseOrderInformVendorDelegator_ep";

    private static final String PARAM_NAME_PURCHASE_ORDER_ID = "PURCHASE_ORDER_ID";
    private static final String PARAM_NAME_SALES_ORDER_ID = "SALES_ORDER_ID";
    private static final String PARAM_NAME_ACTIVITY_ID = "ACTIVITY_ID";
    private static final String PARAM_NAME_ORIGINAL_PAYLOAD = "ORIGINAL_PAYLOAD";
    private static final String PARAM_NAME_FAULT_CODE = "FAULT_CODE";
    private static final String PARAM_NAME_FAULT_MESSAGE = "FAULT_MESSAGE";
    private static final String PARAM_NAME_MESSAGE_ID = "MESSAGE_ID";

    private static final String PURCHASE_ORDER_ID = "123456789756432";
    private static final String ACTIVITY_ID_PO_TO_CSV = "P1003-PO-TO-CSV";
    private static final String ACTIVITY_ID_PO_MAIL = "P1003-PO-MAIL";
    private static final String ACTIVITY_ID_PO_MAIL_CB = "P1003-PO-MAIL-CB";
    private static final String ACTIVITY_ID_PO_MAIL_BCC = "P1003-PO-MAIL-BCC";
    private static final String ACTIVITY_ID_PO_MAIL_BCC_CB = "P1003-PO-MAIL-BCC-CB";
    private static final String PROCESS_LIBRARY_ID = "P1003";
    private static final String FAULT_CATEGORY_TECHNICAL_FAULT = "TechnicalFault";
    private static final String FAULT_CODE_NOTIFICATION_API_ERROR = "MW-11302";
    private static final String FAULT_CODE_CONVERT_TO_CSV = "MW-10101";

    private String salesOrderId;
    private String messageIdSendPoMail;
    private String messageIdSendPoMailBcc;

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    public InformVendorProcessMockTests() {
	super("dropship");
    }

    @Before
    public void setUp() throws Exception {

	salesOrderId = String.valueOf(System.currentTimeMillis());
	messageIdSendPoMail = UUID.randomUUID().toString();
	messageIdSendPoMailBcc = UUID.randomUUID().toString();

	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");

	declareXpathNS("ns1",
		"http://home24.de/interfaces/bas/vendortransmissionservice/vendortransmissionservicemessages/v1");
	declareXpathNS("ns2", "http://home24.de/data/custom/notification/v1");
	declareXpathNS("ns3", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS("ns6",
		"http://home24.de/interfaces/bps/informvendorprocess/informvendorprocessmessages/v1");
	declareXpathNS("ns8", "http://www.openapplications.org/oagis/10");
	declareXpathNS("nsm", "http://home24.de/interfaces/bas/notificationservice/notificationservicemessages/v1");
	declareXpathNS("csm","http://home24.de/data/custom/notification/v1");

	getOtmDao().delete(createDeleteBALQuery());
    }

    @Test
    public void whenFailureWhileCreatingPoCsvThenRetryInCaseOfTechnicalFault() {

	List<MockResponsePojo> vendorTransmissionResponses = new ArrayList<MockResponsePojo>();
	vendorTransmissionResponses
		.add(new MockResponsePojo(MockResponsePojo.ResponseType.BUSINESS_FAULT,
			new ParameterReplacer(replaceParams(
				readClasspathFile(
					"../processes/Dropship/PurchaseOrderInformVendorProcess/FaultMessage.xml"),
				salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_TO_CSV, null))
					.replace(PARAM_NAME_FAULT_CODE, FAULT_CODE_CONVERT_TO_CSV)
					.replace(PARAM_NAME_FAULT_MESSAGE,
						"Technical fault while converting the PO to CSV")
					.build(),
			"fault"));
	vendorTransmissionResponses.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
		replaceParams(
			readClasspathFile(
				"../processes/Dropship/PurchaseOrderInformVendorProcess/VendorTransmissionSuccessResponse.xml"),
			salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_TO_CSV, null)));

	DefaultSoapMockService vendorTransmissionServiceSuccesMock = new DefaultSoapMockService(
		vendorTransmissionResponses);
	GenericFaultHandlerMock genericFautlHandlerServiceMock = new GenericFaultHandlerMock(
		FaultStrategy.RESEND);
	DefaultSoapMockService notificationServiceRef = new DefaultSoapMockService(
		replaceParams(readClasspathFile(PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE), salesOrderId,
			PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL, messageIdSendPoMail));
	DefaultSoapMockService notificationServiceBccRef = new DefaultSoapMockService(
		replaceParams(readClasspathFile(PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE), salesOrderId,
			PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL_BCC, messageIdSendPoMailBcc));

	mockCompositeReference(COMPOSITE, REVISION, "VendorTransmissionService",
		vendorTransmissionServiceSuccesMock);
	mockCompositeReference(COMPOSITE, REVISION, "NotificationService", notificationServiceRef);
	mockCompositeReference(COMPOSITE, REVISION, "NotificationServiceBcc", notificationServiceBccRef);
	mockCompositeReference(COMPOSITE, REVISION, "GenericFautlHandlerService",
		genericFautlHandlerServiceMock);

	invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			replaceParams(
				readClasspathFile(
					"../processes/Dropship/PurchaseOrderInformVendorProcess/InitiateInformVendor.xml"),
				salesOrderId, PURCHASE_ORDER_ID, "", null)));

	waitForInvocationOf(notificationServiceRef, 5);
	invokeSoapEndpoint(INFORM_VENDOR_PROCESS_ENDPOINT_URL,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/SendPurchaseOrderMailSentResponse.xml"))
				.replace(PARAM_NAME_ACTIVITY_ID, ACTIVITY_ID_PO_MAIL_CB)
				.replace(PARAM_NAME_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(PARAM_NAME_MESSAGE_ID, messageIdSendPoMail).build());
	waitForInvocationOf(notificationServiceBccRef, 5);
	invokeSoapEndpoint(INFORM_VENDOR_PROCESS_ENDPOINT_URL,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/SendPurchaseOrderMailSentResponse.xml"))
				.replace(PARAM_NAME_ACTIVITY_ID, ACTIVITY_ID_PO_MAIL_BCC_CB)
				.replace(PARAM_NAME_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(PARAM_NAME_MESSAGE_ID, messageIdSendPoMailBcc).build());

	assertThat("VendorTransmissionService has not been invoked!",
		vendorTransmissionServiceSuccesMock.hasBeenInvoked(), is(Boolean.TRUE));
	assertThat("GenericFaultHandler has not been invoked!",
		genericFautlHandlerServiceMock.hasBeenInvoked(), is(Boolean.TRUE));
	assertThat("NotificationService has not been invoked!", notificationServiceRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("NotificationService has not been invoked!", notificationServiceBccRef.hasBeenInvoked(),
		is(Boolean.TRUE));

	assertGenericFaultHandlerCall(genericFautlHandlerServiceMock, ACTIVITY_ID_PO_TO_CSV);
	assertOtm();
    }

    @Test
    public void whenFailureWhileSendingMailThenRetryInCaseOfTechnicalFault() {

	MockResponsePojo technicalFaultPojo = new MockResponsePojo(
		MockResponsePojo.ResponseType.BUSINESS_FAULT,
		new ParameterReplacer(replaceParams(
			readClasspathFile(
				"../processes/Dropship/PurchaseOrderInformVendorProcess/FaultMessage.xml"),
			salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL, null))
				.replace(PARAM_NAME_FAULT_CODE, FAULT_CODE_NOTIFICATION_API_ERROR)
				.replace(PARAM_NAME_FAULT_MESSAGE,
					"TechnicalFault while sending mail via NotificationAPI")
				.build());

	final DefaultSoapMockService notificationServiceRef = new DefaultSoapMockService(
		Lists.newArrayList(technicalFaultPojo,
			new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
				replaceParams(readClasspathFile(PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE),
					salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL,
					messageIdSendPoMail))));
	final DefaultSoapMockService notificationServiceBccRef = new DefaultSoapMockService(
		Lists.newArrayList(technicalFaultPojo,
			new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
				replaceParams(readClasspathFile(PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE),
					salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL_BCC,
					messageIdSendPoMailBcc))));

	final DefaultSoapMockService vendorTransmissionServiceSuccesMock = new DefaultSoapMockService(
		replaceParams(
			readClasspathFile(
				"../processes/Dropship/PurchaseOrderInformVendorProcess/VendorTransmissionSuccessResponse.xml"),
			salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_TO_CSV, null));
	final GenericFaultHandlerMock genericFautlHandlerServiceRef = new GenericFaultHandlerMock(
		FaultStrategy.RESEND);

	mockCompositeReference(COMPOSITE, REVISION, "VendorTransmissionService",
		vendorTransmissionServiceSuccesMock);
	mockCompositeReference(COMPOSITE, REVISION, "NotificationService", notificationServiceRef);
	mockCompositeReference(COMPOSITE, REVISION, "NotificationServiceBcc", notificationServiceBccRef);
	mockCompositeReference(COMPOSITE, REVISION, "GenericFautlHandlerService",
		genericFautlHandlerServiceRef);

	invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			replaceParams(
				readClasspathFile(
					"../processes/Dropship/PurchaseOrderInformVendorProcess/InitiateInformVendor.xml"),
				salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_TO_CSV, null)));

	waitForInvocationOf(genericFautlHandlerServiceRef);
	waitForInvocationOf(notificationServiceRef, 5);
	invokeSoapEndpoint(INFORM_VENDOR_PROCESS_ENDPOINT_URL,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/SendPurchaseOrderMailSentResponse.xml"))
				.replace(PARAM_NAME_ACTIVITY_ID, ACTIVITY_ID_PO_MAIL_CB)
				.replace(PARAM_NAME_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(PARAM_NAME_MESSAGE_ID, messageIdSendPoMail).build());
	waitForInvocationOf(genericFautlHandlerServiceRef);
	waitForInvocationOf(notificationServiceBccRef, 5);
	invokeSoapEndpoint(INFORM_VENDOR_PROCESS_ENDPOINT_URL,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/SendPurchaseOrderMailSentResponse.xml"))
				.replace(PARAM_NAME_ACTIVITY_ID, ACTIVITY_ID_PO_MAIL_BCC_CB)
				.replace(PARAM_NAME_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(PARAM_NAME_MESSAGE_ID, messageIdSendPoMailBcc).build());

	assertThat("VendorTransmissionService was not invoked!",
		vendorTransmissionServiceSuccesMock.hasBeenInvoked(), is(Boolean.TRUE));
	assertThat("GenericFaultHandler was not invoked!", genericFautlHandlerServiceRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("NotificationService was not invoked!", notificationServiceRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("NotificationServiceBcc was not invoked!", notificationServiceBccRef.hasBeenInvoked(),
		is(Boolean.TRUE));

	assertThat("GenericFaultHandlerService has not been invoked twice!",
		genericFautlHandlerServiceRef.getNumberOfInvocations(), equalTo(2));

	for (String request : genericFautlHandlerServiceRef.getReceivedRequests()) {

	    Map<ExceptionAsserterKey, String> keyToExpectedValue = new HashMap<>();
	    keyToExpectedValue.put(ExceptionAsserterKey.FAULT_CATEGORY, FAULT_CATEGORY_TECHNICAL_FAULT);
	    keyToExpectedValue.put(ExceptionAsserterKey.FAULT_CODE, FAULT_CODE_NOTIFICATION_API_ERROR);
	    keyToExpectedValue.put(ExceptionAsserterKey.PROCESS_LIBRARY_ID, PROCESS_LIBRARY_ID);
	    keyToExpectedValue.put(ExceptionAsserterKey.TRANSACTION_ID, salesOrderId);

	    exceptionAsserter.assertException(request, keyToExpectedValue);
	}

	assertXpathEvaluatesTo("//ns3:exception/ns3:context/ns3:activityId/text()",
		String.format("%s-ERR", ACTIVITY_ID_PO_MAIL),
		genericFautlHandlerServiceRef.getReceivedRequests().get(0));
	assertXpathEvaluatesTo("//ns3:exception/ns3:context/ns3:activityId/text()",
		String.format("%s-ERR", ACTIVITY_ID_PO_MAIL_BCC),
		genericFautlHandlerServiceRef.getReceivedRequests().get(1));

	assertOtm();
    }

    @Test
    public void whenAllInformationAvailableAndProcessingWorksWithoutFailureThenInformVendorSuccessfully()
	    throws Exception {

	executeSuccessfulVendorInformationScenario(
		"../processes/Dropship/PurchaseOrderInformVendorProcess/InitiateInformVendor.xml", true);
    }

    @Test
    /**
     * Test for bug MID-2949
     */
    public void whenNoLablesHaveBeenPrintedThenInformVendorsSuccessfullyWithoutTransmittingLabels()
	    throws Exception {

	executeSuccessfulVendorInformationScenario(
		"../processes/Dropship/PurchaseOrderInformVendorProcess/InitiateInformVendor_NoLabelPath.xml",
		false);
    }

    @Test
    public void whenSendPurchaseOrderMailIsRejectedByNotificationApiThenReceiveCallbackWithBusinessFaultAndExecuteSuccessfullyAfterRetry() {

	final DefaultSoapMockService vendorTransmissionServiceRef = new DefaultSoapMockService(replaceParams(
		readClasspathFile(
			"../processes/Dropship/PurchaseOrderInformVendorProcess/VendorTransmissionSuccessResponse.xml"),
		salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL, null));
	final DefaultSoapMockService notificationServiceRef = new DefaultSoapMockService(
		replaceParams(readClasspathFile(PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE), salesOrderId,
			PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL, messageIdSendPoMail));
	final DefaultSoapMockService notificationServiceBccRef = new DefaultSoapMockService(
		replaceParams(readClasspathFile(PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE), salesOrderId,
			PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL_BCC, messageIdSendPoMailBcc));
	final GenericFaultHandlerMock genericFaultHandlerRef = new GenericFaultHandlerMock(
		FaultStrategy.RESEND);

	mockCompositeReference(COMPOSITE, REVISION, "VendorTransmissionService",
		vendorTransmissionServiceRef);
	mockCompositeReference(COMPOSITE, REVISION, "NotificationService", notificationServiceRef);
	mockCompositeReference(COMPOSITE, REVISION, "NotificationServiceBcc", notificationServiceBccRef);
	mockCompositeReference(COMPOSITE, REVISION, "GenericFautlHandlerService", genericFaultHandlerRef);

	invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			replaceParams(
				readClasspathFile(
					"../processes/Dropship/PurchaseOrderInformVendorProcess/InitiateInformVendor.xml"),
				salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_TO_CSV, null)));

	waitForInvocationOf(notificationServiceRef, 5);
	invokeSoapEndpoint(INFORM_VENDOR_PROCESS_ENDPOINT_URL,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/SendPurchaseOrderMailRejectedResponse.xml"))
				.replace(PARAM_NAME_ACTIVITY_ID, ACTIVITY_ID_PO_MAIL_CB)
				.replace(PARAM_NAME_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(PARAM_NAME_MESSAGE_ID, messageIdSendPoMail).build());

	while (notificationServiceRef.getNumberOfInvocations() < 2) {
	    try {
		Thread.sleep(1000l);
	    } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
	    }
	}

	invokeSoapEndpoint(INFORM_VENDOR_PROCESS_ENDPOINT_URL,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/SendPurchaseOrderMailSentResponse.xml"))
				.replace(PARAM_NAME_ACTIVITY_ID, ACTIVITY_ID_PO_MAIL_CB)
				.replace(PARAM_NAME_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(PARAM_NAME_MESSAGE_ID, messageIdSendPoMail).build());

	waitForInvocationOf(notificationServiceBccRef, 5);
	invokeSoapEndpoint(INFORM_VENDOR_PROCESS_ENDPOINT_URL,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/SendPurchaseOrderMailRejectedResponse.xml"))
				.replace(PARAM_NAME_ACTIVITY_ID, ACTIVITY_ID_PO_MAIL_BCC_CB)
				.replace(PARAM_NAME_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(PARAM_NAME_MESSAGE_ID, messageIdSendPoMailBcc).build());

	while (notificationServiceBccRef.getNumberOfInvocations() < 2) {
	    try {
		Thread.sleep(1000l);
	    } catch (InterruptedException e) {
		Thread.currentThread().interrupt();
	    }
	}

	invokeSoapEndpoint(INFORM_VENDOR_PROCESS_ENDPOINT_URL,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/SendPurchaseOrderMailSentResponse.xml"))
				.replace(PARAM_NAME_ACTIVITY_ID, ACTIVITY_ID_PO_MAIL_BCC_CB)
				.replace(PARAM_NAME_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(PARAM_NAME_MESSAGE_ID, messageIdSendPoMailBcc).build());

	assertThat("GenericFaultHandler has not been invoked!", genericFaultHandlerRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("GenericFaultHandler has not been invoked!",
		genericFaultHandlerRef.getNumberOfInvocations(), equalTo(2));

	assertProcessExecution(true, vendorTransmissionServiceRef, notificationServiceRef,
		notificationServiceBccRef);
    }

    @Test
    public void whenSendPurchaseOrderMailTimesOutThenThrowBusinessFaultAndContinueProcessingWithoutRetry() {

	final DefaultSoapMockService vendorTransmissionServiceRef = new DefaultSoapMockService(replaceParams(
		readClasspathFile(
			"../processes/Dropship/PurchaseOrderInformVendorProcess/VendorTransmissionSuccessResponse.xml"),
		salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL, null));
	final DefaultSoapMockService notificationServiceRef = new DefaultSoapMockService(
		replaceParams(readClasspathFile(PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE), salesOrderId,
			PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL, messageIdSendPoMail));
	final DefaultSoapMockService notificationServiceBccRef = new DefaultSoapMockService(
		replaceParams(readClasspathFile(PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE), salesOrderId,
			PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL_BCC, messageIdSendPoMailBcc));
	final GenericFaultHandlerMock genericFaultHandlerRef = new GenericFaultHandlerMock(
		FaultStrategy.ABORT);

	mockCompositeReference(COMPOSITE, REVISION, "VendorTransmissionService",
		vendorTransmissionServiceRef);
	mockCompositeReference(COMPOSITE, REVISION, "NotificationService", notificationServiceRef);
	mockCompositeReference(COMPOSITE, REVISION, "NotificationServiceBcc", notificationServiceBccRef);
	mockCompositeReference(COMPOSITE, REVISION, "GenericFautlHandlerService", genericFaultHandlerRef);

	saveFileToComposite(COMPOSITE, REVISION, "dvm/ActivityIdToTimeout.dvm", readClasspathFile(
		"../processes/Dropship/PurchaseOrderInformVendorProcess/ActivityIdToTimeout.dvm"));

	invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			replaceParams(
				readClasspathFile(
					"../processes/Dropship/PurchaseOrderInformVendorProcess/InitiateInformVendor.xml"),
				salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_TO_CSV, null)));

	waitForInvocationOf(notificationServiceBccRef, 5);

	assertThat("GenericFaultHandler has not been invoked!", genericFaultHandlerRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("GenericFaultHandler has not been invoked!",
		genericFaultHandlerRef.getNumberOfInvocations(), equalTo(2));
	//Check message id for send
	assertXpathEvaluatesTo("//ns3:faultInfo/ns3:faultUserArea/nsm:purchaseOrderMailResult/csm:messageId/text()", messageIdSendPoMail, genericFaultHandlerRef.getReceivedRequests().get(0));
	//Check message id for bcc
	assertXpathEvaluatesTo("//ns3:faultInfo/ns3:faultUserArea/nsm:purchaseOrderMailResult/csm:messageId/text()", messageIdSendPoMailBcc, genericFaultHandlerRef.getReceivedRequests().get(1));
	
	assertProcessExecution(true, vendorTransmissionServiceRef, notificationServiceRef,
		notificationServiceBccRef);
    }

    private void executeSuccessfulVendorInformationScenario(String pRequestFilepath,
	    boolean pIsLabelsContained) {

	final DefaultSoapMockService vendorTransmissionServiceRef = new DefaultSoapMockService(replaceParams(
		readClasspathFile(
			"../processes/Dropship/PurchaseOrderInformVendorProcess/VendorTransmissionSuccessResponse.xml"),
		salesOrderId, PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL, null));
	final DefaultSoapMockService notificationServiceRef = new DefaultSoapMockService(
		replaceParams(readClasspathFile(PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE), salesOrderId,
			PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL, messageIdSendPoMail));
	final DefaultSoapMockService notificationServiceBccRef = new DefaultSoapMockService(
		replaceParams(readClasspathFile(PATH_TO_NOTIFICATION_SERVICE_MOCK_RESPONSE), salesOrderId,
			PURCHASE_ORDER_ID, ACTIVITY_ID_PO_MAIL, messageIdSendPoMailBcc));
	final GenericFaultHandlerMock genericFautlHandlerServiceMock = new GenericFaultHandlerMock();

	mockCompositeReference(COMPOSITE, REVISION, "VendorTransmissionService",
		vendorTransmissionServiceRef);
	mockCompositeReference(COMPOSITE, REVISION, "NotificationService", notificationServiceRef);
	mockCompositeReference(COMPOSITE, REVISION, "NotificationServiceBcc", notificationServiceBccRef);
	mockCompositeReference(COMPOSITE, REVISION, "GenericFautlHandlerService",
		genericFautlHandlerServiceMock);

	invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			replaceParams(readClasspathFile(pRequestFilepath), salesOrderId, PURCHASE_ORDER_ID,
				ACTIVITY_ID_PO_TO_CSV, null)));

	waitForInvocationOf(notificationServiceRef, 5);
	invokeSoapEndpoint(INFORM_VENDOR_PROCESS_ENDPOINT_URL,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/SendPurchaseOrderMailSentResponse.xml"))
				.replace(PARAM_NAME_ACTIVITY_ID, ACTIVITY_ID_PO_MAIL_CB)
				.replace(PARAM_NAME_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(PARAM_NAME_MESSAGE_ID, messageIdSendPoMail).build());
	waitForInvocationOf(notificationServiceBccRef, 5);
	invokeSoapEndpoint(INFORM_VENDOR_PROCESS_ENDPOINT_URL,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/SendPurchaseOrderMailSentResponse.xml"))
				.replace(PARAM_NAME_ACTIVITY_ID, ACTIVITY_ID_PO_MAIL_BCC_CB)
				.replace(PARAM_NAME_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(PARAM_NAME_MESSAGE_ID, messageIdSendPoMailBcc).build());

	assertThat("GenericFaultHandlerService has not been invoked!",
		genericFautlHandlerServiceMock.hasBeenInvoked(), is(Boolean.FALSE));
	assertProcessExecution(pIsLabelsContained, vendorTransmissionServiceRef, notificationServiceRef,
		notificationServiceBccRef);
    }

    void assertProcessExecution(boolean pIsLabelsContained,
	    final DefaultSoapMockService vendorTransmissionServiceRef,
	    final DefaultSoapMockService notificationServiceRef,
	    final DefaultSoapMockService notificationServiceBccRef) {
	assertThat("VendorTransmissionService has not been invoked!",
		vendorTransmissionServiceRef.hasBeenInvoked(), is(Boolean.TRUE));
	assertThat("NotificationService has not been invoked!", notificationServiceRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("NotificationServiceBcc has not been invoked!", notificationServiceBccRef.hasBeenInvoked(),
		is(Boolean.TRUE));

	assertXpathEvaluatesTo("count(//ns1:requestHeader)", String.valueOf(1),
		vendorTransmissionServiceRef.getLastReceivedRequest());
	assertXpathEvaluatesTo("count(//ns1:purchaseOrder)", String.valueOf(1),
		vendorTransmissionServiceRef.getLastReceivedRequest());
	assertXpathEvaluatesTo("count(//ns1:vendor)", String.valueOf(1),
		vendorTransmissionServiceRef.getLastReceivedRequest());

	String lengthPathToDeliveryNoteForRecipient = evaluateXpath(
		"string-length(//ns2:pathToDeliveryNote/text())",
		notificationServiceRef.getLastReceivedRequest());
	String lengthPathToDeliveryNoteForBccRecipient = evaluateXpath(
		"string-length(//ns2:pathToDeliveryNote/text())",
		notificationServiceBccRef.getLastReceivedRequest());

	assertThat("Path to deliveryNot is not set for recipient mail!",
		Integer.valueOf(lengthPathToDeliveryNoteForRecipient), is(greaterThan(0)));
	assertThat("Path to deliveryNot is not set for BCC recipient mail!",
		Integer.valueOf(lengthPathToDeliveryNoteForBccRecipient), is(greaterThan(0)));

	if (pIsLabelsContained) {
	    String lengthPathToLabels = evaluateXpath("string-length(//ns2:pathToLabels/text())",
		    notificationServiceRef.getLastReceivedRequest());
	    String lengthPathToLabelsForBccRecipient = evaluateXpath(
		    "string-length(//ns2:pathToLabels/text())",
		    notificationServiceBccRef.getLastReceivedRequest());

	    assertThat("Path to labels is not set for recipient mail!", Integer.valueOf(lengthPathToLabels),
		    is(greaterThan(0)));
	    assertThat("Path to labels is not set for BCC recipient mail!",
		    Integer.valueOf(lengthPathToLabelsForBccRecipient), is(greaterThan(0)));
	}

	String lengthPathToPurchaseOrderCsv = evaluateXpath(
		"string-length(//ns2:pathToPurchaseOrderCsv/text())",
		notificationServiceRef.getLastReceivedRequest());
	String lengthPathToPurchaseOrderCsvForBccRecipient = evaluateXpath(
		"string-length(//ns2:pathToPurchaseOrderCsv/text())",
		notificationServiceBccRef.getLastReceivedRequest());

	assertThat("Path to deliveryNote is not set for recipient!",
		Integer.valueOf(lengthPathToPurchaseOrderCsv), is(greaterThan(0)));
	assertThat("Path to deliveryNote is not set for BCC recipient!",
		Integer.valueOf(lengthPathToPurchaseOrderCsvForBccRecipient), is(greaterThan(0)));

	assertXpathEvaluatesTo("count(//ns2:purchaseOrder)", String.valueOf(1),
		notificationServiceRef.getLastReceivedRequest());
	assertOtm();
    }

    /**
     * Send a mid process receive to a running process instance
     */
    void invokeSoapEndpoint(String pEndpointUrl, String pPayload) {
	try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
	    HttpPost request = new HttpPost(pEndpointUrl);
	    request.addHeader("Content-Type", "text/xml; charset=utf-8");
	    request.addHeader("Accept", "text/xml");
	    final String receivePurchaseOrderCreatedRequestSoapRequest = SoapUtil.getInstance()
		    .soapEnvelope(SoapVersion.SOAP11, pPayload);
	    request.setEntity(new StringEntity(receivePurchaseOrderCreatedRequestSoapRequest,
		    ContentType.TEXT_XML.withCharset(StandardCharsets.UTF_8)));

	    httpClient.execute(request);
	} catch (Exception e) {

	    throw new RuntimeException(e);
	}
    }

    /**
     * Asserts expected OTM entries.
     */
    void assertOtm() {
	List<BalActivities> entryActivityPoToCsv = getOtmDao()
		.query(createBALQuery(ACTIVITY_ID_PO_TO_CSV, salesOrderId));
	assertThat(entryActivityPoToCsv, not(empty()));
	assertThat("CorrelationId is not as expected", entryActivityPoToCsv.get(0).getCorrelationId(),
		equalTo(salesOrderId));

	List<BalActivities> entryActivityPoMail = getOtmDao()
		.query(createBALQuery(ACTIVITY_ID_PO_MAIL, salesOrderId));
	assertThat(entryActivityPoMail, not(empty()));
	assertThat("CorrelationId is not as expected", entryActivityPoMail.get(0).getCorrelationId(),
		equalTo(salesOrderId));
	assertThat("MessageId is not stored in BusinessKey2 field",
		entryActivityPoMail.get(0).getBusinessKey2(), equalTo(messageIdSendPoMail));

	List<BalActivities> entryActivityPoMailCb = getOtmDao()
		.query(createBALQuery(ACTIVITY_ID_PO_MAIL_CB, salesOrderId));
	assertThat(entryActivityPoMailCb, not(empty()));
	assertThat("CorrelationId is not as expected", entryActivityPoMailCb.get(0).getCorrelationId(),
		equalTo(salesOrderId));
	assertThat("MessageId is not stored in BusinessKey2 field",
		entryActivityPoMailCb.get(0).getBusinessKey2(), equalTo(messageIdSendPoMail));

	List<BalActivities> entryActivityPoMailBcc = getOtmDao()
		.query(createBALQuery(ACTIVITY_ID_PO_MAIL_BCC, salesOrderId));
	assertThat(entryActivityPoMailBcc, not(empty()));
	assertThat("CorrelationId is not as expected", entryActivityPoMailBcc.get(0).getCorrelationId(),
		equalTo(salesOrderId));
	assertThat("MessageId is not stored in BusinessKey2 field",
		entryActivityPoMailBcc.get(0).getBusinessKey2(), equalTo(messageIdSendPoMailBcc));

	List<BalActivities> entryActivityPoMailBccCb = getOtmDao()
		.query(createBALQuery(ACTIVITY_ID_PO_MAIL_BCC_CB, salesOrderId));
	assertThat(entryActivityPoMailBccCb, not(empty()));
	assertThat("CorrelationId is not as expected", entryActivityPoMailBccCb.get(0).getCorrelationId(),
		equalTo(salesOrderId));
	assertThat("MessageId is not stored in BusinessKey2 field",
		entryActivityPoMailBccCb.get(0).getBusinessKey2(), equalTo(messageIdSendPoMailBcc));
    }

    /**
     * Assert the request sent to the GenericFaultHandler.
     * 
     * @param genericFautlHandlerServiceMock
     *            {@link DefaultSoapMockService} for the GenericFaultHandler
     */
    void assertGenericFaultHandlerCall(GenericFaultHandlerMock genericFautlHandlerServiceMock,
	    String pExpectedActivityId) {
	assertXpathEvaluatesTo("count(//ns3:exception)", String.valueOf(1),
		genericFautlHandlerServiceMock.getLastReceivedRequest());
	assertXpathEvaluatesTo("//ns3:exception/ns3:context/ns3:transactionId/text()", salesOrderId,
		genericFautlHandlerServiceMock.getLastReceivedRequest());
	assertXpathEvaluatesTo("//ns3:exception/ns3:context/ns3:activityId/text()",
		String.format("%s-ERR", pExpectedActivityId),
		genericFautlHandlerServiceMock.getLastReceivedRequest());
    }

    /**
     * Replaces parameters.
     * 
     * @param pPayload
     *            the original payload
     * @param pSalesOrderId
     *            the SalesOrderId to be replaced
     * @param pPurchaseOrderId
     *            the PurchaseOrderId to be replaced
     * @param pActivityId
     *            the ActivityId to be replaced
     * @param pMessageId
     *            the messageId received from NotificationService to be replaced
     * @return
     */
    String replaceParams(final String pPayload, final String pSalesOrderId, final String pPurchaseOrderId,
	    final String pActivityId, String pMessageId) {
	return new ParameterReplacer(pPayload).replace(PARAM_NAME_SALES_ORDER_ID, pSalesOrderId)
		.replace(PARAM_NAME_PURCHASE_ORDER_ID, pPurchaseOrderId)
		.replace(PARAM_NAME_ACTIVITY_ID, pActivityId)
		.replace(PARAM_NAME_MESSAGE_ID, Strings.nullToEmpty(pMessageId)).build();
    }

    /**
     * Replaces the payload placeholder in a given payload.
     * 
     * @param pPayloadWithPlaceholder
     *            the original payload
     * @param pPayload
     *            the payload to use for replacing
     * @return
     */
    String replacePayloadPlaceholder(final String pPayloadWithPlaceholder, final String pPayload) {
	return new ParameterReplacer(pPayloadWithPlaceholder).replace(PARAM_NAME_ORIGINAL_PAYLOAD, pPayload)
		.build();
    }

    private BaseQuery<BalActivities> createBALQuery(String activityCode, String correlationID) {
	return new BaseQuery<>(SqlOp.SELECT,
		new QueryPredicate("correlation_id", correlationID).withEquals("activity_code", activityCode),
		BalActivities.class);
    }

    private BaseQuery<BalActivities> createDeleteBALQuery() {
	return new BaseQuery<>(SqlOp.DELETE, new QueryPredicate("correlation_id", salesOrderId),
		BalActivities.class);
    }
}
