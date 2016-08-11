package de.home24.middleware.notificationtrigger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.HttpResponseWrapper;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.octestframework.util.XpathProcessor;

/**
 * Tests for the NotificationServiceTrigger component
 * 
 * @author svb
 *
 */
public class NotificationTriggerTest extends AbstractBaseSoaTest {

    private static final Logger LOGGER = Logger.getLogger(NotificationTriggerTest.class.getName());

    private static final String REST_PROXY_INVOKE_URL = "http://localhost:7101/NotificationTrigger/exposed/v1/NotificationServiceCallbackTrigger/purchaseOrderMail?correlationId=%s&activityId=%s";

    private static final String INFORM_VENDOR_PROCESS_REF_NAME = "NotificationTrigger/operations/createPurchaseOrderMailCallback/business-service/InformVendorProcessRef";

    private static final String ACTIVITY_ID_PO_MAIL = "P1003-PO-MAIL";
    private static final String ACTIVITY_ID_PO_MAIL_BCC = "P1003-PO-MAIL-BCC";
    private static final String ACTIVITY_ID_PO_MAIL_CB = "P1003-PO-MAIL-CB";
    private static final String ACTIVITY_ID_PO_MAIL_BCC_CB = "P1003-PO-MAIL-BCC-CB";

    private static final String REPLACE_PARAM_MESSAGE_ID = "MESSAGE_ID";

    private String correlationId;
    private String messageId;

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    @Before
    public void setUp() {

	declareXpathNS("ns1",
		"http://home24.de/interfaces/bps/informvendorprocess/informvendorprocessmessages/v1");
	declareXpathNS("ns2", "http://home24.de/data/common/messageheadertypes/v1");
	declareXpathNS("ns3", "http://home24.de/data/custom/notification/v1");
	declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");

	correlationId = String.valueOf(System.currentTimeMillis());
	messageId = UUID.randomUUID().toString();
    }

    @Test
    public void whenNotificationCallbackIsSentAndEmailHasBeenSentSuccessfullyThenCallbackNotificationServiceInvoker() {

	final DefaultSoapMockService informVendorProcessRef = new DefaultSoapMockService();

	mockOsbBusinessService(INFORM_VENDOR_PROCESS_REF_NAME, informVendorProcessRef);

	final HttpResponseWrapper httpResponse = invokeOsbRestProxy(
		String.format(REST_PROXY_INVOKE_URL, correlationId, ACTIVITY_ID_PO_MAIL),
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/CreatePurchaseOrderMailCallbackRequest.json"))
				.replace(REPLACE_PARAM_MESSAGE_ID, messageId).build());

	waitForInvocationOf(informVendorProcessRef, 5);

	assertThat("HttpStatusCode is other than expected", httpResponse.getStatusCode(), equalTo(200));
	assertThat("Processing duration takes longer than expected", 1000l,
		greaterThanOrEqualTo(httpResponse.getProcessingDuration()));

	LOGGER.fine(String.format("###################### NotificationServiceRef receives: %s",
		informVendorProcessRef.getLastReceivedRequest()));

	assertThat("NotificationServiceInvokerRef has not been invoked",
		informVendorProcessRef.hasBeenInvoked(), is(true));
	assertXpathEvaluatesTo(
		"//ns1:receivePurchaseOrderMailSentRequest/ns1:requestHeader/ns2:CorrelationID/text()",
		correlationId, informVendorProcessRef.getLastReceivedRequest());
	assertXpathEvaluatesTo(
		"//ns1:receivePurchaseOrderMailSentRequest/ns1:requestHeader/ns2:ActivityID/text()",
		String.format("%s", ACTIVITY_ID_PO_MAIL_CB), informVendorProcessRef.getLastReceivedRequest());
	assertXpathEvaluatesTo("//ns1:receivePurchaseOrderMailSentRequest/ns1:messageId/text()", messageId,
		informVendorProcessRef.getLastReceivedRequest());
    }

    @Test
    public void whenNotificationCallbackContainsDeliveryInformationIncludingBouncedAndComplaintsThenCallbackNotificationServiceInvokerWithBusinessFault()
	    throws Exception {

	executeCallbackCausingBusinessFaultScenario(
		"CreatePurchaseOrderMailCallbackDeliveryWithComplaintAndBounce.json");
    }

    @Test
    public void whenNotificationCallbackContainsDeliveryInformationIncludingBouncedThenCallbackNotificationServiceInvokerWithBusinessFault()
	    throws Exception {

	executeCallbackCausingBusinessFaultScenario("CreatePurchaseOrderMailCallbackDeliveryWithBounce.json");
    }

    @Test
    public void whenNotificationCallbackContainsDeliveryInformationIncludingComplaintsThenCallbackNotificationServiceInvokerWithBusinessFault()
	    throws Exception {

	executeCallbackCausingBusinessFaultScenario(
		"CreatePurchaseOrderMailCallbackDeliveryWithComplaint.json");
    }

    @Test
    public void whenNotificationCallbackContainsBouncedAndComplaintInformationOnlyThenCallbackNotificationServiceInvokerWithBusinessFault()
	    throws Exception {

	executeCallbackCausingBusinessFaultScenario(
		"CreatePurchaseOrderMailCallbackWithComplaintAndBounce.json");
    }

    void executeCallbackCausingBusinessFaultScenario(String pInputFilename) throws Exception {
	final DefaultSoapMockService informVendorProcessRef = new DefaultSoapMockService();

	mockOsbBusinessService(INFORM_VENDOR_PROCESS_REF_NAME, informVendorProcessRef);

	final String inputMessage = new ParameterReplacer(readClasspathFile(String.format(
		"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/%s",
		pInputFilename))).replace(REPLACE_PARAM_MESSAGE_ID, messageId).build();

	LOGGER.info(String.format("###################### Input messsage to be sent: %s", inputMessage));

	final HttpResponseWrapper httpResponse = invokeOsbRestProxy(
		String.format(REST_PROXY_INVOKE_URL, correlationId, ACTIVITY_ID_PO_MAIL_BCC), inputMessage);

	waitForInvocationOf(informVendorProcessRef, 5);

	assertThat("HttpStatusCode is other than expected", httpResponse.getStatusCode(), equalTo(200));
	assertThat("Processing duration takes longer than expected", 1000l,
		greaterThanOrEqualTo(httpResponse.getProcessingDuration()));

	LOGGER.info(String.format("###################### NotificationServiceRef receives: %s",
		informVendorProcessRef.getLastReceivedRequest()));

	assertThat("NotificationServiceInvokerRef has not been invoked",
		informVendorProcessRef.hasBeenInvoked(), is(true));

	final Map<ExceptionAsserterKey, String> keyToExpectedValues = new HashMap<>();
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "BusinessFault");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-31401");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"Failure while sending purchase order mail via NotificationAPI");
	keyToExpectedValues.put(ExceptionAsserterKey.PROCESS_LIBRARY_ID, "P1003");
	keyToExpectedValues.put(ExceptionAsserterKey.SOURCE_SYSTEM_NAME, "Webshop NotificationService");
	keyToExpectedValues.put(ExceptionAsserterKey.TRANSACTION_ID, correlationId);

	exceptionAsserter.assertException(informVendorProcessRef.getLastReceivedRequest(),
		keyToExpectedValues);

	assertThat("MessageId is not as expected", messageId,
		equalTo(new XpathProcessor().evaluateXPath(
			"//exc:exception/exc:context/exc:additionalInfo/exc:keyValuePair[./exc:key/text()='MessageId']/exc:value/text()",
			informVendorProcessRef.getLastReceivedRequest())));
	assertThat("ActivityId is not as expected", ACTIVITY_ID_PO_MAIL_BCC_CB,
		equalTo(new XpathProcessor().evaluateXPath(
			"//exc:exception/exc:context/exc:activityId/text()",
			informVendorProcessRef.getLastReceivedRequest())));
	assertThat("FaultUserArea does not contain expected root element", "sesCallbackMessage",
		equalTo(evaluateXpath("local-name(//exc:exception/exc:faultInfo/exc:faultUserArea/node()[1])",
			informVendorProcessRef.getLastReceivedRequest())));
    }

    @Test
    public void whenNotificationCallbackHasBadStructureThenReturnHttpStatusCode500() {

	final HttpResponseWrapper httpResponse = invokeOsbRestProxy(
		String.format(REST_PROXY_INVOKE_URL, correlationId, ACTIVITY_ID_PO_MAIL_BCC),
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Notification/NotificationTrigger/createPurchaseOrderMailCallback/CreatePurchaseOrderMailCallbackRequest.json"))
				.replace(REPLACE_PARAM_MESSAGE_ID, messageId).build()
				.replace("messageId", "messageId123"));

	assertThat("HttpStatusCode is other than expected", httpResponse.getStatusCode(), equalTo(500));
	assertThat("Processing duration takes longer than expected", 1000l,
		greaterThanOrEqualTo(httpResponse.getProcessingDuration()));
    }
}
