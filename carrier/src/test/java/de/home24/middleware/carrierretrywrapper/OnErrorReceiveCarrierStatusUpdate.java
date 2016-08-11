package de.home24.middleware.carrierretrywrapper;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Before;
import org.junit.Test;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.mock.RetryWithExceptionSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Tests for operation onErrorReceivedCarrierStatusUpdate in CarrierTrigger
 * 
 * @author svb
 *
 */
public class OnErrorReceiveCarrierStatusUpdate extends AbstractBaseSoaTest {

    private static final String COMPOSITE = "CarrierRetryWrapperProcess";
    private static final String REVISION = "1.4.0.0";

    private static final String REPLACE_PARAM_PAYLOAD = "PAYLOAD";

    public OnErrorReceiveCarrierStatusUpdate() {
	super("generic");
    }

    @Before
    public void setUp() {

    }

    @Test
    public void handleOnErrorNxsdTransformation() {

	final String opaqueElementPayload = readClasspathFile("OpaqueElementPayload.xml");

	final DefaultSoapMockService genericFaultHandlerRef = new DefaultSoapMockService(
		new ParameterReplacer(readClasspathFile("GenericFaultHandlerResendCallback.xml"))
			.replace(REPLACE_PARAM_PAYLOAD, opaqueElementPayload).build());
	mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);

	invokeCompositeService(COMPOSITE, REVISION, "CarrierStatusErrorListener",
		new ParameterReplacer(readClasspathFile("OnErrorNxsdTransformationRequest.xml"))
			.replace(REPLACE_PARAM_PAYLOAD, opaqueElementPayload).build());

	waitForInvocationOf(genericFaultHandlerRef);

	assertThat("GenericFaultHandler has not been invoked!", genericFaultHandlerRef.hasBeenInvoked(),
		is(true));
    }

    @Test
    public void handleOnErrorH24FtpUnavailable() {

	final String opaqueElementPayload = readClasspathFile("OpaqueElementPayload.xml");

	final DefaultSoapMockService genericFaultHandlerRef = new DefaultSoapMockService(
		new ParameterReplacer(readClasspathFile("GenericFaultHandlerResendCallback.xml"))
			.replace(REPLACE_PARAM_PAYLOAD, opaqueElementPayload).build());
	final DefaultSoapMockService carrierServiceRef = new DefaultSoapMockService(
		readClasspathFile("../carrierservice/toggleCarrierStatusUpdatePollingResponse.xml"));

	mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);
	mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceRef);

	invokeCompositeService(COMPOSITE, REVISION, "CarrierStatusErrorListener",
		new ParameterReplacer(readClasspathFile("OnErrorH24FtpUnavailableRequest.xml"))
			.replace(REPLACE_PARAM_PAYLOAD, opaqueElementPayload).build());

	assertThat("GenericFaultHandler has not been invoked!", genericFaultHandlerRef.hasBeenInvoked(),
		is(true));
	assertThat("CarrierService has not been invoked!", carrierServiceRef.hasBeenInvoked(), is(true));
    }

    @Test
    public void handleOnErrorH24FtpUnavailableWithErrorDuringResend() throws Exception {

	final String toggleCarrierStatusPollingRequest = readClasspathFile(
		"ToggleCarrierStatusPollingRequest.xml");

	final DefaultSoapMockService genericFaultHandlerRef = new DefaultSoapMockService(
		new ParameterReplacer(readClasspathFile("GenericFaultHandlerResendCallback.xml"))
			.replace(REPLACE_PARAM_PAYLOAD, toggleCarrierStatusPollingRequest).build());
	final RetryWithExceptionSoapMockService carrierServiceRef = new RetryWithExceptionSoapMockService(3,
		new MockResponsePojo(ResponseType.BUSINESS_FAULT,
			readClasspathFile("../carrierservice/toggleCarrierStatusUpdatePollingResponse.xml")),
		new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			readClasspathFile("../carrierservice/toggleCarrierStatusUpdatePollingResponse.xml")));

	mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);
	mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceRef);

	invokeCompositeService(COMPOSITE, REVISION, "CarrierStatusErrorListener",
		new ParameterReplacer(readClasspathFile("OnErrorH24FtpUnavailableRequest.xml"))
			.replace(REPLACE_PARAM_PAYLOAD, readClasspathFile("OpaqueElementPayload.xml"))
			.build());

	try {
	    Thread.sleep(10000);
	} catch (InterruptedException e) {

	    Thread.currentThread().interrupt();
	    throw new RuntimeException(e);
	}

	assertThat("GenericFaultHandler has not been invoked!", genericFaultHandlerRef.hasBeenInvoked(),
		is(true));
	assertThat("GenericFaultHandler has been invoked less than 4 times!",
		genericFaultHandlerRef.getNumberOfInvocations(), equalTo(4));

	assertThat("CarrierService has not been invoked!", carrierServiceRef.hasBeenInvoked(), is(true));
	assertThat("CarrierService has been invoked less than 4 times!",
		carrierServiceRef.getNumberOfInvocations(), equalTo(4));

	// for (String request : carrierServiceRef.getReceivedRequests()) {
	//
	// final XPath toggleCarrierStatusUpdateXpath =
	// XPathFactory.newInstance().newXPath();
	// toggleCarrierStatusUpdateXpath.setNamespaceContext(new
	// NamespaceContext() {
	//
	// @Override
	// public Iterator getPrefixes(String pNamespaceURI) {
	// return Lists.newArrayList("ns1").iterator();
	// }
	//
	// @Override
	// public String getPrefix(String pNamespaceURI) {
	// return "ns1";
	// }
	//
	// @Override
	// public String getNamespaceURI(String pPrefix) {
	// return
	// "http://home24.de/interfaces/bas/carrier/carrierservicemessages/v1";
	// }
	// });
	//
	// assertThat(
	// String.format("Differences found between XML messages! Expected: %s,
	// Received: %s",
	// request, toggleCarrierStatusPollingRequest),
	// DiffBuilder
	// .compare(toggleCarrierStatusUpdateXpath
	// .evaluate("//ns1:toggleCarrierStatusUpdatePollingRequest", request))
	// .withTest(toggleCarrierStatusPollingRequest)
	// .withDifferenceEvaluator(DifferenceEvaluators.Default).build().hasDifferences(),
	// is(false));
	// }
    }

    @Test
    public void handleOnErrorProcessInvocationError() {

	final String receiveCarrierStatusUpdatePayload = readClasspathFile(
		"ReceiveCarrierStatusUpdatePayload.xml");

	final DefaultSoapMockService genericFaultHandlerRef = new DefaultSoapMockService(
		new ParameterReplacer(readClasspathFile("GenericFaultHandlerResendCallback.xml"))
			.replace(REPLACE_PARAM_PAYLOAD, receiveCarrierStatusUpdatePayload).build());
	final DefaultSoapMockService carrierStatusProcessRef = new DefaultSoapMockService();

	mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);
	mockCompositeReference(COMPOSITE, REVISION, "CarrierStatusProcess", carrierStatusProcessRef);

	invokeCompositeService(COMPOSITE, REVISION, "CarrierStatusErrorListener",
		new ParameterReplacer(readClasspathFile("OnErrorCarrierRetryProcessInvocationRequest.xml"))
			.replace(REPLACE_PARAM_PAYLOAD, receiveCarrierStatusUpdatePayload).build());

	assertThat("GenericFaultHandler has not been invoked!", genericFaultHandlerRef.hasBeenInvoked(),
		is(true));
	assertThat("CarrierStatusProcess has not been invoked!", carrierStatusProcessRef.hasBeenInvoked(),
		is(true));
    }

    @Test
    public void handleOnErrorProcessInvocationErrorWithErrorDuringResend() {

	final String receiveCarrierStatusUpdatePayload = readClasspathFile(
		"ReceiveCarrierStatusUpdatePayload.xml");

	final DefaultSoapMockService genericFaultHandlerRef = new DefaultSoapMockService(
		new ParameterReplacer(readClasspathFile("GenericFaultHandlerResendCallback.xml"))
			.replace(REPLACE_PARAM_PAYLOAD, receiveCarrierStatusUpdatePayload).build());

	mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);

	invokeCompositeService(COMPOSITE, REVISION, "CarrierStatusErrorListener",
		new ParameterReplacer(readClasspathFile("OnErrorCarrierRetryProcessInvocationRequest.xml"))
			.replace(REPLACE_PARAM_PAYLOAD, receiveCarrierStatusUpdatePayload).build());

	assertThat("GenericFaultHandler has not been invoked!", genericFaultHandlerRef.hasBeenInvoked(),
		is(true));
    }

}
