package de.home24.middleware.printingservice;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.junit.Test;
import org.mockserver.matchers.Times;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpStatusCode;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import de.home24.middleware.octestframework.AbstractRestMockSoaTest;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Tests for PrintingService SB implementation.
 * 
 * Note: Tests can only be executed sequentially in isolation!!!
 * 
 * TODO: Investigate problems with tests...
 * 
 * @author svb
 *
 */
public class PrintingServiceTest extends AbstractRestMockSoaTest {

    private static final String EXPECTED_PATH_TO_PRINTING_TRIGGER_SERVICE_MOCK = "/PrintingTrigger/exposed/v1/PrintingServiceCallbackTrigger/deliveryNote";

    private final static Logger LOGGER = Logger.getLogger(PrintingServiceTest.class.getName());

    private static final String PRINTING_SERVICE_PROXY_URI = "PrintingService/exposed/v1/PrintingService";
    private static final String PRINTING_SERVICE_MOCK_URI = "http://%s:8088/api/v1";
    private static final String PRINTING_SERVICE_BIZ_URI = "PrintingService/operations/createDeliveryNote/business-service/WebshopPrintingServiceRef";
    private static final String PRINTING_TRIGGER_BIZ_URI = "PrintingService/operations/createDeliveryNote/business-service/PrintingTriggerRef";

    private static final String MESSAGE_ID = "123456789";
    private static final String CALLBACK_URL = "https://%s:7102/soa-infra/services/dropship/PurchaseGroupHandlingProcess!1.0*soa_622dcd65-31bb-46f5-85b8-e9396be2dc68/PrintingServiceService%%23PrintDocument/PrintingService";

    private String callbackUrl;
    private String printingServiceMockUrl;
    private Map<ExceptionAsserter.ExceptionAsserterKey, String> keysToExpectedValues;

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    @Override
    protected void setUpOverridable() {

	printingServiceMockUrl = String.format(PRINTING_SERVICE_MOCK_URI, getMockHost());
	callbackUrl = String.format(CALLBACK_URL, getMockHost());

	declareXpathNS("ns2", "http://home24.de/interfaces/thirdparty/webshop/printingservice/v1");
	declareXpathNS("ns1", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS("wsa05", "http://www.w3.org/2005/08/addressing");
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");

	keysToExpectedValues = new HashMap<>();
	keysToExpectedValues.put(ExceptionAsserterKey.TRANSACTION_ID, "30101002023905");
	keysToExpectedValues.put(ExceptionAsserterKey.SOURCE_SYSTEM_NAME, "Middleware");
	keysToExpectedValues.put(ExceptionAsserterKey.PROCESS_LIBRARY_ID, "P1001");
    }

    @Test
    public void whenWebshopApiIsAvailableAndOrderIsNoSparepartOrderThenSendPrintingRequestWithoutOriginalPoNoAndVendorMotherItemNo()
	    throws Exception {

	final String expectedPathToRestServiceMock = "/api/v1/templates/DN-DS-APPDOM1-DEU/pdf";
	final String docReference = "/api/doc/1234";

	getOsbAccessor().setBusinessServiceHttpUri(PRINTING_SERVICE_BIZ_URI, printingServiceMockUrl);

	final HttpRequest httpRequest = request().withMethod("POST").withPath(expectedPathToRestServiceMock)
		.withHeaders(new Header("Content-Type", "application/json; charset=utf-8"));
	getMockServer().when(httpRequest, Times.exactly(1))
		.respond(response().withStatusCode(201).withHeaders(new Header("Location", docReference)));

	final String invocationResult = invokeOsbProxyService(PRINTING_SERVICE_PROXY_URI,
		getPrintingServiceSoapRequest(MESSAGE_ID, callbackUrl, readClasspathFile(
			"../servicebus/Printing/PrintingService/createDeliveryNote/PrintDeliveryNoteRequest.xml")));

	assertThat("Invocation result is not expected to be null!", invocationResult, not(nullValue()));

	LOGGER.fine(String.format("############## Received HTTP Request: %s",
		getMockServer().retrieveRecordedRequests(httpRequest)[0].getBodyAsString()));

	assertJsonRequestToWebshopApi(httpRequest, "AM9868892", null);
    }

    @Test
    public void whenWebshopApiIsAvailableAndOrderIsSparepartOrderThenSendPrintingRequestWithOriginalPoNoAndVendorMotherItemNo()
	    throws Exception {

	final String expectedPathToRestServiceMock = "/api/v1/templates/DN-DS-APPDOM1-DEU/pdf";
	final String docReference = "/api/doc/1234";

	getOsbAccessor().setBusinessServiceHttpUri(PRINTING_SERVICE_BIZ_URI, printingServiceMockUrl);

	final HttpRequest httpRequest = request().withMethod("POST").withPath(expectedPathToRestServiceMock)
		.withHeaders(new Header("Content-Type", "application/json; charset=utf-8"));
	getMockServer().when(httpRequest, Times.exactly(1))
		.respond(response().withStatusCode(201).withHeaders(new Header("Location", docReference)));

	final String invocationResult = invokeOsbProxyService(PRINTING_SERVICE_PROXY_URI,
		getPrintingServiceSoapRequest(MESSAGE_ID, callbackUrl, readClasspathFile(
			"../servicebus/Printing/PrintingService/createDeliveryNote/PrintDeliveryNoteRequestForSparepart.xml")));

	assertThat("Invocation result is not expected to be null!", invocationResult, not(nullValue()));

	LOGGER.fine(String.format("############## Received HTTP Request: %s",
		getMockServer().retrieveRecordedRequests(httpRequest)[0].getBodyAsString()));

	assertJsonRequestToWebshopApi(httpRequest, "AM9868999", "DS13690541");
    }

    void assertJsonRequestToWebshopApi(final HttpRequest httpRequest, final String expectedVendorItemNo,
	    final String pExpectedOriginalPoNo) throws IOException, JsonProcessingException {
	final JsonNode jsonRootObject = new ObjectMapper()
		.readTree(getMockServer().retrieveRecordedRequests(httpRequest)[0].getBodyAsRawBytes());

	Iterator<JsonNode> lineItems = jsonRootObject.path("vars").path("blockVars").path("block1")
		.elements();
	JsonNode singleLineItem = lineItems.next();

	boolean isOriginalPoNoExpected = !Strings.isNullOrEmpty(pExpectedOriginalPoNo);

	assertThat("JSON key 'OriginalPurchaseOrderNo' not contained!",
		singleLineItem.has("OriginalPurchaseOrderNo"), equalTo(isOriginalPoNoExpected));

	if (isOriginalPoNoExpected) {
	    assertThat("JSON key 'OriginalPurchaseOrderNo' has unexpected value!",
		    singleLineItem.path("OriginalPurchaseOrderNo").asText(), equalTo(pExpectedOriginalPoNo));
	}

	assertThat("JSON key 'VendorItemNo' not contained!", singleLineItem.has("VendorItemNo"),
		equalTo(true));
	assertThat("JSON key 'VendorItemNo' has unexpected value!",
		singleLineItem.path("VendorItemNo").asText(), equalTo(expectedVendorItemNo));
    }

    @Test
    public void whenWebshopApiIsNotAvailableAndReturns404ThenReturnExceptionContainingTechnicalFault() {

	final String expectedPathToRestServiceMock = "/api/v1/templates/DN-DS-APPDOM1-DEU/pdf";
	final String errorneousResponse = readClasspathFile(
		"../servicebus/Printing/PrintingService/createDeliveryNote/PrintingServiceError404.xml");
	final DefaultSoapMockService printingServiceInvokerMockService = new DefaultSoapMockService();

	getMockServer()
		.when(request().withMethod("POST").withPath(expectedPathToRestServiceMock).withHeaders(
			new Header("Content-Type", "application/json; charset=utf-8")), Times.exactly(1))
		.respond(response().withStatusCode(404).withBody(errorneousResponse));

	getOsbAccessor().setBusinessServiceHttpUri(PRINTING_SERVICE_BIZ_URI, printingServiceMockUrl);
	final String printingServiceInvokerCallbackRefMockUrl = getOsbAccessor().mockBusinessService(
		"PrintingService/operations/createDeliveryNote/business-service/PrintingServiceInvokerCallbackRef",
		printingServiceInvokerMockService);
	setOsbServiceReplacement(
		"PrintingService/operations/createDeliveryNote/pipeline/CreateDeliveryNotePipeline",
		Pattern.quote("$headerIncoming/wsa05:ReplyTo/wsa05:Address/text()"),
		String.format("'%s'", printingServiceInvokerCallbackRefMockUrl));

	invokeOsbProxyService(PRINTING_SERVICE_PROXY_URI,
		getPrintingServiceSoapRequest(MESSAGE_ID, callbackUrl, readClasspathFile(
			"../servicebus/Printing/PrintingService/createDeliveryNote/PrintDeliveryNoteRequest.xml")));

	assertThat("PrintingServiceInvokerCallback has not been invoked!",
		printingServiceInvokerMockService.hasBeenInvoked(), is(true));
	assertThat("Invocation result is not expected to be null!",
		printingServiceInvokerMockService.getLastReceivedRequest(), not(nullValue()));
	assertXpathEvaluatesTo("count(//ns1:exception)", "1",
		printingServiceInvokerMockService.getLastReceivedRequest());
	assertThat("Invocation result should contain original error message from server!",
		printingServiceInvokerMockService.getLastReceivedRequest(),
		containsString(errorneousResponse));

	assertXpathEvaluatesTo("count(/soapenv:Envelope/soapenv:Body/soapenv:Fault)", "1",
		printingServiceInvokerMockService.getLastReceivedRequest());

	// TODO: Enable, as check for empty payload is implemented properly
	// in base-framework
	// keysToExpectedValues.put(ExceptionAsserterKey.PAYLOAD_ELEMENT,
	// "");
	keysToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	keysToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-10400");
	keysToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"Technical fault while calling Webshop API for PrintingService");

	exceptionAsserter.assertException(printingServiceInvokerMockService.getLastReceivedRequest(),
		keysToExpectedValues);

	LOGGER.fine(String.format("MockService request: %s",
		printingServiceInvokerMockService.getLastReceivedRequest()));

	assertXpathEvaluatesTo("//wsa05:RelatesTo/text()", MESSAGE_ID,
		printingServiceInvokerMockService.getLastReceivedRequest());
	assertXpathEvaluatesTo("//wsa05:To/text()", callbackUrl,
		printingServiceInvokerMockService.getLastReceivedRequest());
    }

    @Test
    public void whenResendPrintingServiceBecauseOfExceptionCallbackThenReinvokePrintingTriggerInsteadOfWebshopApi() {

	getMockServer().when(
		request().withMethod("POST").withPath(EXPECTED_PATH_TO_PRINTING_TRIGGER_SERVICE_MOCK)
			.withHeaders(new Header("Content-Type", "application/json; charset=utf-8")),
		Times.exactly(1)).respond(response().withStatusCode(HttpStatusCode.OK_200.code()));

	getOsbAccessor().setBusinessServiceHttpUri(PRINTING_TRIGGER_BIZ_URI,
		"http://mock-host:8088/PrintingTrigger/exposed/v1/PrintingServiceCallbackTrigger");

	invokeOsbProxyService(PRINTING_SERVICE_PROXY_URI,
		getPrintingServiceSoapRequest(MESSAGE_ID, callbackUrl, readClasspathFile(
			"../servicebus/Printing/PrintingService/createDeliveryNote/ResendDeliveryNoteRequest.xml")));
    }

    @Test
    public void whenResendPrintingServiceBecauseOfExceptionCallbackButPrintingTriggerIsNotReachableThenReturnTechnicalFault() {

	final DefaultSoapMockService printingServiceInvokerMockService = new DefaultSoapMockService();

	final String printingServiceInvokerCallbackRefMockUrl = getOsbAccessor().mockBusinessService(
		"PrintingService/operations/createDeliveryNote/business-service/PrintingServiceInvokerCallbackRef",
		printingServiceInvokerMockService);
	setOsbServiceReplacement(
		"PrintingService/operations/createDeliveryNote/pipeline/ResendPrintingServiceCallbackPipeline",
		Pattern.quote("$header/wsa05:ReplyTo/wsa05:Address/text()"),
		String.format("'%s'", printingServiceInvokerCallbackRefMockUrl));

	getMockServer()
		.when(request().withMethod("POST").withPath(EXPECTED_PATH_TO_PRINTING_TRIGGER_SERVICE_MOCK)
			.withHeaders(new Header("Content-Type", "application/json; charset=utf-8")),
			Times.exactly(1))
		.respond(response().withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500.code()));

	getOsbAccessor().setBusinessServiceHttpUri(PRINTING_TRIGGER_BIZ_URI,
		"http://mock-host:8088/PrintingTrigger/exposed/v1/PrintingServiceCallbackTrigger");

	invokeOsbProxyService(PRINTING_SERVICE_PROXY_URI,
		getPrintingServiceSoapRequest(MESSAGE_ID, callbackUrl, readClasspathFile(
			"../servicebus/Printing/PrintingService/createDeliveryNote/ResendDeliveryNoteRequest.xml")));

	assertThat("PrintingServiceInvokerRef has not been invoked!",
		printingServiceInvokerMockService.hasBeenInvoked(), is(true));

	LOGGER.fine(String.format("################ Exception: %s",
		printingServiceInvokerMockService.getLastReceivedRequest()));

	assertXpathEvaluatesTo("count(/soapenv:Envelope/soapenv:Body/soapenv:Fault)", "1",
		printingServiceInvokerMockService.getLastReceivedRequest());

	keysToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	keysToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-10401");
	keysToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"Technical fault while resending callback message to PrintingTrigger");

	exceptionAsserter.assertException(printingServiceInvokerMockService.getLastReceivedRequest(),
		keysToExpectedValues);
    }

    /**
     * Testcase for MID-2706
     */
    @Test
    public void whenUnknownAppDomainThenReturnWithTechnicalFault() {

	final DefaultSoapMockService printingServiceInvokerMockService = new DefaultSoapMockService();

	final String printingServiceInvokerCallbackRefMockUrl = getOsbAccessor().mockBusinessService(
		"PrintingService/operations/createDeliveryNote/business-service/PrintingServiceInvokerCallbackRef",
		printingServiceInvokerMockService);
	setOsbServiceReplacement(
		"PrintingService/operations/createDeliveryNote/pipeline/CreateDeliveryNotePipeline",
		Pattern.quote("$headerIncoming/wsa05:ReplyTo/wsa05:Address/text()"),
		String.format("'%s'", printingServiceInvokerCallbackRefMockUrl));

	getMockServer()
		.when(request().withMethod("POST").withPath(EXPECTED_PATH_TO_PRINTING_TRIGGER_SERVICE_MOCK)
			.withHeaders(new Header("Content-Type", "application/json; charset=utf-8")),
			Times.exactly(1))
		.respond(response().withStatusCode(HttpStatusCode.NOT_FOUND_404.code()));

	final String salesOrderId = String.valueOf(System.currentTimeMillis());
	final String purchaseOrderId = String.format("DS%s", String.valueOf(System.currentTimeMillis()));

	final String printingServiceRequest = getPrintingServiceSoapRequest(MESSAGE_ID, callbackUrl,
		new ParameterReplacer(readClasspathFile(
			"../servicebus/Printing/PrintingService/createDeliveryNote/CreateDeliveryNoteWithUnknownAppDomain.xml"))
				.replace("SALES_ORDER_ID", salesOrderId)
				.replace("PURCHASE_ORDER_ID", purchaseOrderId).build());

	LOGGER.info(String.format("################ Request: %s", printingServiceRequest));

	invokeOsbProxyService(PRINTING_SERVICE_PROXY_URI, printingServiceRequest);

	assertThat("PrintingServiceInvokerRef has not been invoked!",
		printingServiceInvokerMockService.hasBeenInvoked(), is(true));

	LOGGER.info(String.format("################ Exception: %s",
		printingServiceInvokerMockService.getLastReceivedRequest()));

	assertXpathEvaluatesTo("count(/soapenv:Envelope/soapenv:Body/soapenv:Fault)", "1",
		printingServiceInvokerMockService.getLastReceivedRequest());

	keysToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	keysToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-10400");
	keysToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		"Technical fault while calling Webshop API for PrintingService");
	keysToExpectedValues.put(ExceptionAsserterKey.TRANSACTION_ID, purchaseOrderId);

	exceptionAsserter.assertException(printingServiceInvokerMockService.getLastReceivedRequest(),
		keysToExpectedValues);
    }

    private String getPrintingServiceSoapRequest(final String pMessageId, final String pCallbackUrl,
	    final String pRequestString) {
	return SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, pRequestString),
		SoapUtil.getInstance().messageIdHeader(pMessageId),
		SoapUtil.getInstance().relatesToHeader(pMessageId),
		SoapUtil.getInstance().replyToHeader(pCallbackUrl));
    }
}
