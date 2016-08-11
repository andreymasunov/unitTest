package de.home24.middleware.vendortransmissionservice;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class InitiateEdifactCreationTest extends AbstractBaseSoaTest {

    private static final Logger LOGGER = Logger.getLogger(InitiateEdifactCreationTest.class.getSimpleName());
    private static final String PATH_VENDOR_SERVICE = "VendorTransmissionService/exposed/v1/VendorTransmissionService";
    private static final String PATH_REQ_EDIFACT_Q = "VendorTransmissionService/operations/InitiateEDIFACTProcessingInERP/business-service/InitiateEdifactCreation";
    private static final String PATH_VENDOR_EXP_CALLBACK = "VendorTransmissionService/shared/business-service/VendorTransmissionCallbackRef";
    private static final String CALLBACK_URL = "https://localhost:7102/soa-infra/services/dropship/EDIVendorTransmissionProcess!1.0*soa_622dcd65-31bb-46f5-85b8-e9396be2dc68/VendorTransmissionService%23InitiateEDIFACTProcessinginERP/VendorTransmissionService";
    private static final String RPLC_CORRELATION_ID = "CORRELATION_ID";
    private static final String RPLC_PURCHASE_ORDER_NUM = "PURCHASE_ORDER_NUM";

    private String randomCorrelationId, randomPoNum, randomMessageId, invocationResult = "";

    private DefaultSoapMockService writeToEdifactSucessQueueMock = null;
    private DefaultSoapMockService writeToEdifactFaultQueueMock = null;
    private DefaultSoapMockService edifactProcessingCallbackExceptionMock = null;
    private List<MockResponsePojo> edifactFaultMockPojoList = new ArrayList<MockResponsePojo>();

    @Before
    public void setUp() {

	declareXpathNS("exp", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS("rrw",
		"http://home24.de/interfaces/bas/responseretrywrapper/responseretrywrappermessages/v1");
	declareXpathNS("efm", "http://home24.de/data/navision/edifactmessages/v1");
	declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
	declareXpathNS("edf", "http://home24.de/data/navision/edifact/v1");
	declareXpathNS("wsa05", "http://www.w3.org/2005/08/addressing");
	declareXpathNS("vtm",
		"http://home24.de/interfaces/bas/vendortransmissionservice/vendortransmissionservicemessages/v1");

	Random randomNumber = new Random();
	randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));
	randomPoNum = "DS" + String.valueOf(randomNumber.nextInt(1000000));
	randomMessageId = String.valueOf(randomNumber.nextInt(1000000));

	LOGGER.info("+++Setup Mocks+++");

	writeToEdifactSucessQueueMock = new DefaultSoapMockService("");
	edifactProcessingCallbackExceptionMock = new DefaultSoapMockService("");
	edifactFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, ""));
	writeToEdifactFaultQueueMock = new DefaultSoapMockService(edifactFaultMockPojoList);
    }

    @After
    public void tearDown() {
	LOGGER.info("+++Delete Mocks+++");

	edifactProcessingCallbackExceptionMock = null;
	writeToEdifactSucessQueueMock = null;
	writeToEdifactFaultQueueMock = null;
    }

    @Test
    public void whenReceivingAValidRequestMessageIsSentThenReturnWithSuccessfulEdifactCreation() {

	mockOsbBusinessService(PATH_REQ_EDIFACT_Q, writeToEdifactSucessQueueMock);
	mockOsbBusinessService(PATH_VENDOR_EXP_CALLBACK, edifactProcessingCallbackExceptionMock);

	final String requestString = new ParameterReplacer(
		readClasspathFile("initiateedifactprocessing/InitiateEdifactProcessingInERPRequest.xml"))
			.replace(RPLC_CORRELATION_ID, randomCorrelationId)
			.replace(RPLC_PURCHASE_ORDER_NUM, randomPoNum).build();

	invokeOsbProxyService(PATH_VENDOR_SERVICE,
		getSoapRequest(randomMessageId, CALLBACK_URL, requestString));

	assertThat("Message has not been sent to the queue",
		writeToEdifactSucessQueueMock.getNumberOfInvocations() == 1);
	assertThat("Unexpected error occour.",
		edifactProcessingCallbackExceptionMock.getNumberOfInvocations() == 0);

	assertXpathEvaluatesTo("count(//efm:initiateEdifactCreation)", String.valueOf(1),
		writeToEdifactSucessQueueMock.getLastReceivedRequest());
	assertXpathEvaluatesTo("//efm:initiateEdifactCreation/efm:header/mht:CorrelationID/text()",
		randomCorrelationId, writeToEdifactSucessQueueMock.getLastReceivedRequest());
	assertXpathEvaluatesTo("//efm:edifactCreationRequest/edf:purchaseOrderNumber/text()", randomPoNum,
		writeToEdifactSucessQueueMock.getLastReceivedRequest());
    }

    @Test
    public void whenReceivingAValidRequestMessageIsSentButEditfactProcessingIsNotPossibleThenReturnWithException() {
	try {

	    mockOsbBusinessService(PATH_REQ_EDIFACT_Q, writeToEdifactFaultQueueMock);
	    mockOsbBusinessService(PATH_VENDOR_EXP_CALLBACK, edifactProcessingCallbackExceptionMock);

	    final String requestString = new ParameterReplacer(
		    readClasspathFile("initiateedifactprocessing/InitiateEdifactProcessingInERPRequest.xml"))
			    .replace(RPLC_CORRELATION_ID, randomCorrelationId)
			    .replace(RPLC_PURCHASE_ORDER_NUM, randomPoNum).build();

	    invocationResult = invokeOsbProxyService(PATH_VENDOR_SERVICE,
		    getSoapRequest(randomMessageId, CALLBACK_URL, requestString));

	    fail();
	} catch (ServiceException e) {
	    e.printStackTrace();

	    assertThat("Invocation result is expected to be null!", invocationResult, isEmptyOrNullString());
	    assertThat("No message is sent to the queue.",
		    writeToEdifactFaultQueueMock.getNumberOfInvocations() == 1);
	    assertThat("Error occours.",
		    edifactProcessingCallbackExceptionMock.getNumberOfInvocations() == 1);

	    assertXpathEvaluatesTo("count(//exp:exception)", String.valueOf(1),
		    edifactProcessingCallbackExceptionMock.getLastReceivedRequest());
	    assertXpathEvaluatesTo(
		    "//vtm:initiateEdifactProcessingInERPRequest/vtm:requestHeader/mht:CorrelationID/text()",
		    randomCorrelationId, edifactProcessingCallbackExceptionMock.getLastReceivedRequest());
	    assertXpathEvaluatesTo(
		    "//vtm:initiateEdifactProcessingInERPRequest/vtm:purchaseOrderNumber/text()", randomPoNum,
		    edifactProcessingCallbackExceptionMock.getLastReceivedRequest());
	}
    }

    private String getSoapRequest(final String pMessageId, final String pCallbackUrl,
	    final String pRequestString) {
	return SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, pRequestString),
		SoapUtil.getInstance().messageIdHeader(pMessageId),
		SoapUtil.getInstance().relatesToHeader(pMessageId),
		SoapUtil.getInstance().replyToHeader(pCallbackUrl));
    }

}
