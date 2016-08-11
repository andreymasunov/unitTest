package de.home24.middleware.notificationservice;

import static com.jcabi.matchers.RegexMatchers.containsPattern;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpStatusCode;
import org.mockserver.model.StringBody;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import de.home24.middleware.octestframework.AbstractRestMockSoaTest;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.octestframework.util.XpathProcessor;

/**
 * Unit test for NotificationService.
 */
public class NotificationServiceTest extends AbstractRestMockSoaTest {

    private static final String PATH_TO_NOTIFICATION_SERVICE_RESOURCES = "../servicebus/Notification/NotificationService/sendPurchaseOrderMail";

    private static final Logger LOGGER = Logger.getLogger(NotificationServiceTest.class.getSimpleName());

    private static final String REPLACE_PARAM_SALES_ORDER_ID = "SALES_ORDER_ID";
    private static final String REPLACE_PARAM_PURCHASE_ORDER_ID = "PURCHASE_ORDER_ID";
    private static final String REPLACE_PARAM_ACTIVITY_ID = "ACTIVITY_ID";
    private static final String REPLACE_PARAM_STATUS = "STATUS";
    private static final String REPLACE_PARAM_DESCRIPTION = "DESCRIPTION";
    private static final String REPLACE_PARAM_STATUSCODE = "STATUSCODE";
    private static final String REPLACE_PARAM_MESSAGE_ID = "MESSAGE_ID";

    private final static String PATH_TO_NOTIFICATION_SERVICE = "NotificationService/exposed/v1/NotificationServicePS";
    private final static String PATH_TO_NOTIFICATION_SERVICE_BCC = "NotificationService/exposed/v1/NotificationServiceBccPS";
    private final static String PATH_TO_NOTIFICATION_API = "NotificationService/operations/sendPurchaseOrderMail/business-service/NotificationApiRef";
    private final static String PATH_TO_FTP_GETFILE = "NotificationService/operations/sendPurchaseOrderMail/business-service/ftpGetFile";

    private static final String PURCHASE_ORDER_ID = "DS13650050";
    private static final String ACTIVITY_CODE_PO_MAIL = "P1003-PO-MAIL";
    private static final String ACTIVITY_CODE_PO_MAIL_BCC = "P1003-PO-MAIL-BCC";
    private static final String CALLBACK_URL_TEMPLATE = "\"url\" : \"https://testing-ext.middleware.app.home24.net/NotificationTrigger/exposed/v1/NotificationServiceCallbackTrigger/purchaseOrderMail?correlationId=%s&activityId=%s\"";

    private int mockServerPort = 8088;
    private String pathToNotificationService;

    private MockResponsePojo getDeliveryNoteMockResponse;
    private MockResponsePojo getLablesFileMockResponse;
    private MockResponsePojo getOrderCsvMockResponse;

    private Map<ExceptionAsserterKey, String> keyToExpectedValues;
    private String salesOrderId;
    private String messageId;
    private String activityId;

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    @Override
    public void setUpOverridable() {

	messageId = UUID.randomUUID().toString();
	salesOrderId = String.valueOf(System.currentTimeMillis());
	activityId = ACTIVITY_CODE_PO_MAIL;

	getDeliveryNoteMockResponse = new MockResponsePojo(ResponseType.SOAP_RESPONSE,
		readClasspathFile("getDeliveryFileResponse.xml"));
	getLablesFileMockResponse = new MockResponsePojo(ResponseType.SOAP_RESPONSE,
		readClasspathFile("getLabelFileResponse.xml"));
	getOrderCsvMockResponse = new MockResponsePojo(ResponseType.SOAP_RESPONSE,
		readClasspathFile("getOrderCsvFileResponse.xml"));

	keyToExpectedValues = new HashMap<>();
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	keyToExpectedValues.put(ExceptionAsserterKey.PROCESS_LIBRARY_ID, "P1003");
	keyToExpectedValues.put(ExceptionAsserterKey.TRANSACTION_ID, salesOrderId);

	declareXpathNS("sn", "http://home24.de/services/servicebus/notificationservice/sendnotification");
	declareXpathNS("data", "http://home24.de/data/custom/notification/v1");
	declareXpathNS("exception", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS("notif",
		"http://home24.de/interfaces/bas/notificationservice/notificationservicemessages/v1");
	declareXpathNS("pomcb",
		"http://home24.de/interfaces/bts/notificationtrigger/notificationtriggermessages/v1");

	setMockServerPort(++mockServerPort);
    }

    @Test
    public void whenAllInformationIsAvailableThenPurchaseOrderMailIsSentToRecipient() throws Exception {

	pathToNotificationService = PATH_TO_NOTIFICATION_SERVICE;

	final String notificationApiResponse = createNotificationServiceResponse(true);

	final DefaultSoapMockService ftpGetFileRef = new DefaultSoapMockService(Lists.newArrayList(
		getDeliveryNoteMockResponse, getLablesFileMockResponse, getOrderCsvMockResponse));

	final String invocationResult = executeServiceChangesAndInvocations(notificationApiResponse, 1,
		ftpGetFileRef);

	assertThat("FtpFileGetRef has not been invoked!", ftpGetFileRef.hasBeenInvoked(), is(true));
	assertThat("FtpFileGetRef has not been invoked for 3 times!", ftpGetFileRef.getNumberOfInvocations(),
		equalTo(3));

	final HttpRequest[] requestsToNotificationServiceApi = getMockServer().retrieveRecordedRequests(null);
	assertThat("NotificationServiceAPI has not been invoked for 1 times!",
		Arrays.asList(requestsToNotificationServiceApi), hasSize(1));
	assertThat("CallbackUrl is not contained in NotificationAPI call, but is expected!",
		requestsToNotificationServiceApi[0].getBodyAsString(), containsPattern(Pattern.quote(
			String.format(CALLBACK_URL_TEMPLATE, PURCHASE_ORDER_ID, ACTIVITY_CODE_PO_MAIL))));

	LOGGER.info(String.format("########### Call to Notification API: %s",
		requestsToNotificationServiceApi[0].getBodyAsString()));

	assertValidPurchaseOrderMailCallback(invocationResult);
    }

    @Test
    public void whenAllInformationIsAvailableThenPurchaseOrderMailIsSentBccRecipients() throws Exception {

	pathToNotificationService = PATH_TO_NOTIFICATION_SERVICE_BCC;
	activityId = ACTIVITY_CODE_PO_MAIL_BCC;

	final String notificationApiResponse = createNotificationServiceResponse(true);

	final DefaultSoapMockService ftpGetFileRef = new DefaultSoapMockService(Lists.newArrayList(
		getDeliveryNoteMockResponse, getLablesFileMockResponse, getOrderCsvMockResponse));

	final String invocationResult = executeServiceChangesAndInvocations(notificationApiResponse, 1,
		ftpGetFileRef);

	assertThat("FtpFileGetRef has not been invoked!", ftpGetFileRef.hasBeenInvoked(), is(true));
	assertThat("FtpFileGetRef has not been invoked for 3 times!", ftpGetFileRef.getNumberOfInvocations(),
		equalTo(3));

	final HttpRequest[] requestsToNotificationServiceApi = getMockServer().retrieveRecordedRequests(null);
	assertThat("NotificationServiceAPI has not been invoked for 1 times!",
		Arrays.asList(requestsToNotificationServiceApi), hasSize(1));
	assertThat("CallbackUrl is not contained in NotificationAPI call, but is expected!",
		requestsToNotificationServiceApi[0].getBodyAsString(), containsPattern(Pattern.quote(
			String.format(CALLBACK_URL_TEMPLATE, PURCHASE_ORDER_ID, ACTIVITY_CODE_PO_MAIL_BCC))));
	assertThat(
		"No valid Bcc recipeint contained in second NoptificationAPI call that sends mail for the BCC recipients!",
		requestsToNotificationServiceApi[0].getBodyAsString(),
		containsPattern(Pattern.quote("\"recipients\" : [ \"middleware.qa@gmail.com\" ]")));

	assertValidPurchaseOrderMailCallback(invocationResult);
    }

    @Test
    public void whenExceptionWhileGettingFileFromFtpThenTechnicalFaultIsReturnedAndNoPurchaseOrderMailToRecipientIsSent() {

	pathToNotificationService = PATH_TO_NOTIFICATION_SERVICE;
	executeWhenExceptionWhileGettingFileFromFtp();
    }

    @Test
    public void whenExceptionWhileGettingFileFromFtpThenTechnicalFaultIsReturnedAndNoPurchaseOrderMailToBccRecipientIsSent() {

	pathToNotificationService = PATH_TO_NOTIFICATION_SERVICE_BCC;
	executeWhenExceptionWhileGettingFileFromFtp();
    }

    @Test
    public void whenExceptionSendingPoMailToRecipientThenTechnicalFaultIsReturned() {

	pathToNotificationService = PATH_TO_NOTIFICATION_SERVICE;
	executeWhenExceptionSendingPoMail();
    }

    @Test
    public void whenExceptionSendingPoMailToBccRecipientThenTechnicalFaultIsReturned() {

	pathToNotificationService = PATH_TO_NOTIFICATION_SERVICE_BCC;
	executeWhenExceptionSendingPoMail();
    }

    @Test
    public void whenNotificationApiReturnUnsuccessfulMailDeliveryToRecipientThenBusinessFaultIsReturned()
	    throws Exception {

	pathToNotificationService = PATH_TO_NOTIFICATION_SERVICE;
	executeNotificationApiReturnsUnsuccessfulMailDelivery();
    }

    @Test
    public void whenNotificationApiReturnUnsuccessfulMailDeliveryToBccRecipientThenBusinessFaultIsReturned()
	    throws Exception {

	pathToNotificationService = PATH_TO_NOTIFICATION_SERVICE_BCC;
	executeNotificationApiReturnsUnsuccessfulMailDelivery();
    }

    private void executeWhenExceptionSendingPoMail() {

	final DefaultSoapMockService ftpGetFileRef = new DefaultSoapMockService(Lists.newArrayList(
		getDeliveryNoteMockResponse, getLablesFileMockResponse, getOrderCsvMockResponse));

	final String invocationResult = executeServiceChangesAndInvocations("", 0, ftpGetFileRef);

	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-11302");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE, "Error calling Mail Renderer");
	exceptionAsserter.assertException(invocationResult, keyToExpectedValues);
    }

    private void executeWhenExceptionWhileGettingFileFromFtp() {
	final MockResponsePojo getLablesFileErrorMockResponse = new MockResponsePojo(ResponseType.FAULT,
		"Exception while getting labels file from FTP");
	final DefaultSoapMockService ftpGetFileRef = new DefaultSoapMockService(
		Lists.newArrayList(getDeliveryNoteMockResponse, getLablesFileErrorMockResponse));

	final String invocationResult = executeServiceChangesAndInvocations("", 0, ftpGetFileRef);

	LOGGER.info(String.format("Invocation result: %s", invocationResult));

	assertThat("FtpFileGetRef has not been invoked!", ftpGetFileRef.hasBeenInvoked(), is(true));
	assertThat("FtpFileGetRef has not been invoked for 2 times!", ftpGetFileRef.getNumberOfInvocations(),
		equalTo(2));

	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-11301");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE, "Error getting label file from FTPS");

	exceptionAsserter.assertException(invocationResult, keyToExpectedValues);
    }

    private String createNotificationServiceResponse(boolean pWasSuccess) {
	return new ParameterReplacer(readClasspathFile(pWasSuccess
		? String.format("%s/SendPurchaseOrderMailNotificationApiResponse.json",
			PATH_TO_NOTIFICATION_SERVICE_RESOURCES)
		: String.format("%s/SendPurchaseOrderMailNotificationApiErrorResponse.json",
			PATH_TO_NOTIFICATION_SERVICE_RESOURCES))).replace(REPLACE_PARAM_STATUS, "SUCCESS")
				.replace(REPLACE_PARAM_DESCRIPTION, "Email enqueued successfully")
				.replace(REPLACE_PARAM_STATUSCODE, "+10")
				.replace(REPLACE_PARAM_MESSAGE_ID, messageId).build();
    }

    private void executeNotificationApiReturnsUnsuccessfulMailDelivery() throws Exception {
	final DefaultSoapMockService ftpGetFileRef = new DefaultSoapMockService(Lists.newArrayList(
		getDeliveryNoteMockResponse, getLablesFileMockResponse, getOrderCsvMockResponse));

	final String notificationServiceFailureResponse = createNotificationServiceResponse(false);
	final String invocationResult = executeServiceChangesAndInvocations(
		notificationServiceFailureResponse, 1, ftpGetFileRef);

	LOGGER.info(String.format("NOTIFICATION: %s", invocationResult));

	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-31301");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"Failure while sending purchase order mail via NotificationAPI");
	exceptionAsserter.assertException(invocationResult, keyToExpectedValues);

	final String originalFaultContent = evaluateXpath(
		"//exception:exception/exception:faultInfo/exception:faultUserArea/*", invocationResult)
			.replaceAll("ns1:", "");
	assertXpathEvaluatesTo("//pomcb:purchaseOrderMailCallback/pomcb:emid/text()", messageId,
		originalFaultContent);
	assertThat("MessageId is not as expected", messageId,
		equalTo(new XpathProcessor().evaluateXPath(
			"//exc:exception/exc:context/exc:additionalInfo/exc:keyValuePair[./exc:key/text()='MessageId']/exc:value/text()",
			invocationResult)));
    }

    private void assertValidPurchaseOrderMailCallback(final String invocationResult) {
	assertXpathEvaluatesTo("count(//notif:sendPurchaseOrderMailResponse/notif:responseHeader)", "1",
		invocationResult);
	assertXpathEvaluatesTo(
		"//notif:sendPurchaseOrderMailResponse/notif:purchaseOrderMailResult/data:messageId/text()",
		messageId, invocationResult);
    }

    private String executeServiceChangesAndInvocations(final String pNotificationApiResponse,
	    int pNumberOfNotificationAPiInvocations, final DefaultSoapMockService pFtpGetFileRef) {

	getMockServer()
		.when(request().withMethod("POST").withPath("/api/v1/notifications"),
			Times.exactly(pNumberOfNotificationAPiInvocations))
		.respond(response().withStatusCode(HttpStatusCode.BAD_REQUEST_400.code())
			.withBody(new StringBody(pNotificationApiResponse)));

	mockOsbBusinessService(PATH_TO_FTP_GETFILE, pFtpGetFileRef);
	setOsbBusinessServiceHttpUri(PATH_TO_NOTIFICATION_API,
		String.format("http://mock-host:%s/api/v1/notifications", mockServerPort));

	String notificationServiceRequest = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationService/sendPurchaseOrderMail/SendPurchaseOrderMailRequest.xml"))
				.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
				.replace(REPLACE_PARAM_SALES_ORDER_ID, salesOrderId)
				.replace(REPLACE_PARAM_ACTIVITY_ID, activityId).build());

	LOGGER.fine(String.format("################### Request: %s", notificationServiceRequest));

	return invokeOsbProxyService(pathToNotificationService, notificationServiceRequest);
    }
}
