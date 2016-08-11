package de.home24.middleware.printingtrigger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.not;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.BinaryBody;
import org.mockserver.model.Header;
import org.mockserver.model.HttpStatusCode;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.collect.Lists;
import com.google.common.io.ByteStreams;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import de.home24.middleware.octestframework.AbstractRestMockSoaTest;
import de.home24.middleware.octestframework.HttpResponseWrapper;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.components.MessagingDao.JmsModule;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.octestframework.util.XpathProcessor;

public class PrintingTriggerTest extends AbstractRestMockSoaTest {

    private static final String PATH_TO_RESOURCES_DELIVERNOTE_QUEUE = "../queues/h24_PurchaseOrder/RSP_DeliveryNote_Q";
    private static final String PATH_TO_RESOURCES_SB_PRINTINGTRIGGER = "../servicebus/Printing/PrintingTrigger";

    private static final String BUSINESS_SERVICE_URL_PRINTING_SERVICE_INVOKER = "PrintingTrigger/operations/createDeliveryNoteCallback/business-service/PrintingServiceInvokerRef";
    private static final String BUSINESS_SERVICE_URL_DELIVERY_NOTE_WRITER = "PrintingTrigger/operations/createDeliveryNoteCallback/business-service/DeliveryNoteWriterRef";
    private static final String BUSINESS_SERVICE_URL_DELIVERY_NOTE_ENQ = "PrintingTrigger/operations/createDeliveryNoteCallback/business-service/DeliveryNoteEnqueuerRef";

    private static final String NAMESPACE_PREFIX_MESSAGE = "message";
    private static final String NAMESPACE_MESSAGE = "http://home24.de/interfaces/bts/printingtrigger/printingtriggermessages/v1";

    private static final String REPLACE_PARAM_DOC_ID = "DOC_ID";
    private static final String REPLACE_PARAM_PRINT_STATUS = "PRINT_STATUS";
    private static final String REPLACE_PARAM_PURCHASE_ORDER_ID = "PURCHASE_ORDER_ID";
    private static final String REPLACE_PARAM_SALES_ORDER_ID = "SALES_ORDER_ID";
    private static final String REPLACE_PARAM_MOCK_HOST = "MOCK_HOST";
    private static final String REPLACE_PARAM_MOCK_PORT = "MOCK_PORT";

    private static final String DOC_ID = "3341";
    private static final String SALES_ORDER_ID = "7654321";
    private static final String PURCHASE_ORDER_ID = "1234567";
    private static final String EXPECTED_PATH_TO_MOCK_SERVICE = String.format("/api/v1/docs/%s/download",
	    DOC_ID);

    private String jsonRequest;
    private String queueMessage;
    private Map<ExceptionAsserterKey, String> keyToExpectedValues;

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    @Override
    public void setUpOverridable() {

	declareXpathNS("exception", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS(NAMESPACE_PREFIX_MESSAGE, NAMESPACE_MESSAGE);

	keyToExpectedValues = new HashMap<>();
	keyToExpectedValues.put(ExceptionAsserterKey.SOURCE_SYSTEM_NAME, "Middleware");
	keyToExpectedValues.put(ExceptionAsserterKey.PROCESS_LIBRARY_ID, "P1001");
	keyToExpectedValues.put(ExceptionAsserterKey.TRANSACTION_ID, SALES_ORDER_ID);
	keyToExpectedValues.put(ExceptionAsserterKey.PAYLOAD_ELEMENT_NAME, "receiveDeliveryNoteRequest");

	jsonRequest = replacePlaceholders(
		String.format("%s/createDeliveryNoteCallback/ReceiveDeliveryNoteCallback.json",
			PATH_TO_RESOURCES_SB_PRINTINGTRIGGER));
	queueMessage = replacePlaceholders(
		String.format("%s/ReceiveDeliveryNoteCallback.xml", PATH_TO_RESOURCES_DELIVERNOTE_QUEUE));
    }

    private String replacePlaceholders(String pMessageSourceFilename) {

	return new ParameterReplacer(readClasspathFile(pMessageSourceFilename))
		.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
		.replace(REPLACE_PARAM_SALES_ORDER_ID, SALES_ORDER_ID).replace(REPLACE_PARAM_DOC_ID, DOC_ID)
		.replace(REPLACE_PARAM_MOCK_HOST, getMockHost()).replace(REPLACE_PARAM_MOCK_PORT, "8088")
		.build();
    }

    @Test
    public void whenCallbackFromWebshopIsReceivedSuccessfulThenReturn200Within1Second() throws Exception {

	final DefaultSoapMockService deliveryNoteEnqueuerRef = new DefaultSoapMockService();

	mockOsbBusinessService(BUSINESS_SERVICE_URL_DELIVERY_NOTE_ENQ, deliveryNoteEnqueuerRef);

	HttpResponseWrapper httpRestResponse = invokeSbRestProxy(
		String.format("http://%s:%s/%s", getConfig().getOsbServerConfig().getServiceHost(),
			getConfig().getOsbServerConfig().getServicePort(),
			"PrintingTrigger/exposed/v1/PrintingServiceCallbackTrigger/deliveryNote"),
		new ParameterReplacer(jsonRequest).replace(REPLACE_PARAM_PRINT_STATUS, "success").build());

	assertThat("HTTP status code not as expected!", httpRestResponse.getStatusCode(), equalTo(200));
	assertThat("ProcessingDuration too long!", httpRestResponse.getProcessingDuration(),
		not(greaterThanOrEqualTo(1000l)));

	assertThat("DeliveryNoteEnqueuerRef has not been invoked!", deliveryNoteEnqueuerRef.hasBeenInvoked(),
		is(true));
	assertXpathEvaluatesTo("local-name(//*[1])", "receiveDeliveryNoteRequest",
		deliveryNoteEnqueuerRef.getLastReceivedRequest());

	System.out.println(deliveryNoteEnqueuerRef.getLastReceivedRequest());
    }

    @Test
    public void whenCallbackFromWebshopIsNotReceivedSuccessfulThenReturn200Within1SecondAndInvokePrintingServiceInvokerRefWithExceptionCallback()
	    throws Exception {

	final DefaultSoapMockService deliveryNoteEnqueuerRef = new DefaultSoapMockService(Lists.newArrayList(
		new MockResponsePojo(ResponseType.FAULT, "Queue not reachable!", "Queue not reachable!")));
	final DefaultSoapMockService printingServiceInvokerRef = new DefaultSoapMockService();

	mockOsbBusinessService(BUSINESS_SERVICE_URL_DELIVERY_NOTE_ENQ, deliveryNoteEnqueuerRef);
	final String printingServiceInvokerRefMockUri = mockOsbBusinessService(
		BUSINESS_SERVICE_URL_PRINTING_SERVICE_INVOKER, printingServiceInvokerRef);

	setOsbServiceReplacement(
		"PrintingTrigger/operations/createDeliveryNoteCallback/pipeline/ReceiveDeliveryNote",
		Pattern.quote("$body/v1:receiveDeliveryNoteRequest/v1:documentTags/v1:replyToAddress/text()"),
		String.format("'%s'", printingServiceInvokerRefMockUri));

	HttpResponseWrapper httpRestResponse = invokeSbRestProxy(
		String.format("http://%s:%s/%s", getConfig().getOsbServerConfig().getServiceHost(),
			getConfig().getOsbServerConfig().getServicePort(),
			"PrintingTrigger/exposed/v1/PrintingServiceCallbackTrigger/deliveryNote"),
		new ParameterReplacer(jsonRequest).replace(REPLACE_PARAM_PRINT_STATUS, "success").build());

	waitForInvocationOf(printingServiceInvokerRef, 3);

	assertThat("HTTP status code not as expected!", httpRestResponse.getStatusCode(), equalTo(200));
	assertThat("ProcessingDuration too long!", httpRestResponse.getProcessingDuration(),
		not(greaterThanOrEqualTo(1000l)));

	assertThat("DeliveryNoteEnqueuerRef has not been invoked!", deliveryNoteEnqueuerRef.hasBeenInvoked(),
		is(true));
	assertThat("PrintingServiceInvokerRef has not been invoked!",
		printingServiceInvokerRef.hasBeenInvoked(), is(true));

	assertXpathEvaluatesTo("local-name(//*[1])", "receiveDeliveryNoteRequest",
		deliveryNoteEnqueuerRef.getLastReceivedRequest());

	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-10500");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"TechnicalFault while enqueuing Printing API callback");
	keyToExpectedValues.put(ExceptionAsserterKey.PAYLOAD_ELEMENT,
		deliveryNoteEnqueuerRef.getLastReceivedRequest());
	exceptionAsserter.assertException(printingServiceInvokerRef.getLastReceivedRequest(),
		keyToExpectedValues);
    }

    @Test
    public void whenReceivingSuccessfulPrintCallbackThenDownloadDocumentWriteToFtpAndReturnSuccessCallbackToPrintingServiceInvoker()
	    throws Exception {

	final byte[] deliveryNoteResponse = ByteStreams
		.toByteArray(this.getClass().getResourceAsStream("Binarycontent.txt"));

	final DefaultSoapMockService deliveryNoteWriterMock = new DefaultSoapMockService();
	final DefaultSoapMockService callbackToInvokerRefMock = new DefaultSoapMockService();

	getMockServer()
		.when(request().withMethod("GET").withPath(EXPECTED_PATH_TO_MOCK_SERVICE), Times.exactly(1))
		.respond(response().withStatusCode(HttpStatusCode.OK_200.code())
			.withHeaders(new Header("Accept-Ranges", "bytes"),
				new Header("Cache-Control", "public"), new Header("Connection", "keep-alive"),
				new Header("Content-Length", String.valueOf(deliveryNoteResponse.length)),
				new Header("Content-Type", "application/octet-stream"),
				new Header("Content-Transfer-Encoding", "binary"),
				new Header("Content-Disposition",
					"attachment; filename=&quot;3341.pdf&quot;"),
				new Header("Content-Description", "File Transfer"))
			.withBody(new BinaryBody(deliveryNoteResponse)));

	mockOsbBusinessService(BUSINESS_SERVICE_URL_DELIVERY_NOTE_WRITER, deliveryNoteWriterMock);
	final String printingServiceInvokerRefMockUri = mockOsbBusinessService(
		BUSINESS_SERVICE_URL_PRINTING_SERVICE_INVOKER, callbackToInvokerRefMock);
	setOsbServiceReplacement(
		"PrintingTrigger/operations/createDeliveryNoteCallback/pipeline/CreateDeliveryNoteCallbackPipeline",
		Pattern.quote("$body/v1:receiveDeliveryNoteRequest/v1:documentTags/v1:replyToAddress/text()"),
		String.format("'%s'", printingServiceInvokerRefMockUri));

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.PURCHASE_ORDER, "h24jms.RSP_DeliveryNote_Q",
		new ParameterReplacer(queueMessage).replace(REPLACE_PARAM_PRINT_STATUS, "success").build());

	waitForInvocationOf(callbackToInvokerRefMock, 5);

	assertThat("DeliveryNoteWriterRef has not been invoked!", deliveryNoteWriterMock.hasBeenInvoked(),
		is(true));
	assertThat("DeliveryNoteWriterRef has not been invoked once!",
		deliveryNoteWriterMock.getNumberOfInvocations(), is(1));
	assertThat("CallbackToInvokerRef has not been invoked!", callbackToInvokerRefMock.hasBeenInvoked(),
		is(true));
	assertThat("Number of CallbackToInvokerRef invocations does not meet the expectation!",
		callbackToInvokerRefMock.getNumberOfInvocations(), equalTo(1));

	assertXpathEvaluatesTo("count(//exception:exception)", String.valueOf(0),
		callbackToInvokerRefMock.getLastReceivedRequest());
    }

    @Test
    public void whenPrintingTheDeliveryNoteFailsOnTheWebshopSideThenReturnTechnicalFaultToPrintingServiceInvoker()
	    throws Exception {

	final DefaultSoapMockService callbackToInvokerRefMock = new DefaultSoapMockService();
	final DefaultSoapMockService deliveryNoteWriterRefMock = new DefaultSoapMockService();

	mockOsbBusinessService(BUSINESS_SERVICE_URL_DELIVERY_NOTE_WRITER, deliveryNoteWriterRefMock);
	final String printingServiceInvokerRefMockUri = mockOsbBusinessService(
		BUSINESS_SERVICE_URL_PRINTING_SERVICE_INVOKER, callbackToInvokerRefMock);
	setOsbServiceReplacement(
		"PrintingTrigger/operations/createDeliveryNoteCallback/pipeline/CreateDeliveryNoteCallbackPipeline",
		Pattern.quote("$body/v1:receiveDeliveryNoteRequest/v1:documentTags/v1:replyToAddress/text()"),
		String.format("'%s'", printingServiceInvokerRefMockUri));

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.PURCHASE_ORDER, "h24jms.RSP_DeliveryNote_Q",
		new ParameterReplacer(queueMessage).replace(REPLACE_PARAM_PRINT_STATUS, "failure").build());

	waitForInvocationOf(callbackToInvokerRefMock, 3);

	assertThat("DeliveryNoteWriterRef has been invoked, which should not be the case!",
		deliveryNoteWriterRefMock.hasBeenInvoked(), is(false));
	assertThat("CallbackToInvokerRef has not been invoked!", callbackToInvokerRefMock.hasBeenInvoked(),
		is(true));

	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-30500");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE, "Fault while printing delivery note");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "BusinessFault");
	keyToExpectedValues.put(ExceptionAsserterKey.PAYLOAD_ELEMENT,
		new XpathProcessor().evaluateXPath(
			"/soapenv:Envelope/soapenv:Body/exc:exception/exc:context/exc:payload/*",
			callbackToInvokerRefMock.getLastReceivedRequest()));
	exceptionAsserter.assertException(callbackToInvokerRefMock.getLastReceivedRequest(),
		keyToExpectedValues);
    }

    @Test
    public void whenPrintingIsSccuessfulButFtpIsNotAvailableThenReturnTechnicalFaultToPrintingServiceInvoker()
	    throws Exception {

	final DefaultSoapMockService callbackToInvokerRefMock = new DefaultSoapMockService();
	final DefaultSoapMockService deliveryNoteWriterRefMock = new DefaultSoapMockService(
		Lists.newArrayList(new MockResponsePojo(ResponseType.FAULT, "This is a fault!",
			"This is a fault message!")));
	final byte[] deliveryNoteResponse = ByteStreams
		.toByteArray(this.getClass().getResourceAsStream("Binarycontent.txt"));

	getMockServer()
		.when(request().withMethod("GET").withPath(EXPECTED_PATH_TO_MOCK_SERVICE), Times.exactly(1))
		.respond(response().withStatusCode(HttpStatusCode.OK_200.code())
			.withHeaders(new Header("Accept-Ranges", "bytes"),
				new Header("Cache-Control", "public"), new Header("Connection", "keep-alive"),
				new Header("Content-Length", String.valueOf(deliveryNoteResponse.length)),
				new Header("Content-Type", "application/octet-stream"),
				new Header("Content-Transfer-Encoding", "binary"),
				new Header("Content-Disposition",
					"attachment; filename=&quot;3341.pdf&quot;"),
				new Header("Content-Description", "File Transfer"))
			.withBody(new BinaryBody(deliveryNoteResponse)));

	mockOsbBusinessService(BUSINESS_SERVICE_URL_DELIVERY_NOTE_WRITER, deliveryNoteWriterRefMock);
	final String printingServiceInvokerRefMockUri = mockOsbBusinessService(
		BUSINESS_SERVICE_URL_PRINTING_SERVICE_INVOKER, callbackToInvokerRefMock);
	setOsbServiceReplacement(
		"PrintingTrigger/operations/createDeliveryNoteCallback/pipeline/CreateDeliveryNoteCallbackPipeline",
		Pattern.quote("$body/v1:receiveDeliveryNoteRequest/v1:documentTags/v1:replyToAddress/text()"),
		String.format("'%s'", printingServiceInvokerRefMockUri));

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.PURCHASE_ORDER, "h24jms.RSP_DeliveryNote_Q",
		new ParameterReplacer(queueMessage).replace(REPLACE_PARAM_PRINT_STATUS, "success").build());

	waitForInvocationOf(callbackToInvokerRefMock, 5);

	assertThat("DeliveryNoteWriterRef has not been invoked!", deliveryNoteWriterRefMock.hasBeenInvoked(),
		is(true));
	assertThat("CallbackToInvokerRef has not been invoked!", callbackToInvokerRefMock.hasBeenInvoked(),
		is(true));

	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-10501");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"TechnicalFault while sending delivery note to FTP location");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	keyToExpectedValues.put(ExceptionAsserterKey.PAYLOAD_ELEMENT,
		new XpathProcessor().evaluateXPath(
			"/soapenv:Envelope/soapenv:Body/exc:exception/exc:context/exc:payload/*",
			callbackToInvokerRefMock.getLastReceivedRequest()));
	exceptionAsserter.assertException(callbackToInvokerRefMock.getLastReceivedRequest(),
		keyToExpectedValues);
	assertXpathEvaluatesTo("//exception:exception/exception:context/exception:activityId/text()",
		"P1001-DELNT-CB-ERR", callbackToInvokerRefMock.getLastReceivedRequest());
	assertXpathEvaluatesTo(
		"//exception:exception/exception:context/exception:additionalInfo/exception:keyValuePair[exception:key/text()='DocumentInfoLink']/exception:value/text()",
		String.format("http://%s:8088/api/v1/docs/3341", getMockHost()),
		callbackToInvokerRefMock.getLastReceivedRequest());
    }

    @Test
    public void whenPrintingIsSccuessfulAndDownloadDocumentFailsThenReturnTechnicalFaultToPrintingServiceInvoker()
	    throws Exception {

	final DefaultSoapMockService callbackToInvokerRefMock = new DefaultSoapMockService();

	final String printingServiceInvokerRefMockUri = mockOsbBusinessService(
		BUSINESS_SERVICE_URL_PRINTING_SERVICE_INVOKER, callbackToInvokerRefMock);
	setOsbServiceReplacement(
		"PrintingTrigger/operations/createDeliveryNoteCallback/pipeline/CreateDeliveryNoteCallbackPipeline",
		Pattern.quote("$body/v1:receiveDeliveryNoteRequest/v1:documentTags/v1:replyToAddress/text()"),
		String.format("'%s'", printingServiceInvokerRefMockUri));

	getMockServer()
		.when(request().withMethod("GET").withPath(EXPECTED_PATH_TO_MOCK_SERVICE), Times.exactly(1))
		.respond(response().withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500.code()));

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.PURCHASE_ORDER, "h24jms.RSP_DeliveryNote_Q",
		new ParameterReplacer(queueMessage).replace(REPLACE_PARAM_PRINT_STATUS, "success").build());

	waitForInvocationOf(callbackToInvokerRefMock, 5);

	assertThat("CallbackToInvokerRef has not been invoked!", callbackToInvokerRefMock.hasBeenInvoked(),
		is(true));

	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-10502");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"TechnicalFault while downloading delivery note document");
	keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	keyToExpectedValues.put(ExceptionAsserterKey.PAYLOAD_ELEMENT,
		new XpathProcessor().evaluateXPath(
			"/soapenv:Envelope/soapenv:Body/exc:exception/exc:context/exc:payload/*",
			callbackToInvokerRefMock.getLastReceivedRequest()));

	exceptionAsserter.assertException(callbackToInvokerRefMock.getLastReceivedRequest(),
		keyToExpectedValues);
    }

}
