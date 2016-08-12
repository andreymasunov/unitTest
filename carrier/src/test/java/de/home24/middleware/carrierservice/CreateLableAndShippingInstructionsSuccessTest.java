package de.home24.middleware.carrierservice;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.HttpResponseWrapper;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;

/**
 * Unit test for CarrierService.
 */
public class CreateLableAndShippingInstructionsSuccessTest
	extends AbstractCreateLabelAndShippingInstructionsTest {

    private static final String REPLY_TO = "http://localhost:7101/CarrierService/exposed/CarrierServiceCallback";

    private DefaultSoapMockService carrierServiceCallbackService;

    @Override
    protected void setUpOverrideable() {

	carrierServiceCallbackService = new DefaultSoapMockService(
		readClasspathFile("createLabelAndShippingInstructionsResponse.xml"));
	callbackUrl = mockOsbBusinessService(PATH_CARRIER_CALLBACK, carrierServiceCallbackService);
    }

    @Test
    public void createLabelAndShippingInstructionsWithSucess() {

	metapackOkResponse[3] = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		readClasspathFile("metapackOkResponse4.xml"));

	try {
		LOGGER.info("+++ invoke createLabelAndShippingInstructionsWithSucess");
	    final HttpResponseWrapper httpResponseWrapper = invokeSbSoapProxy(
		    String.format("http://%s:%s/%s", getConfig().getOsbServerConfig().getServiceHost(),
			    getConfig().getOsbServerConfig().getServicePort(), PATH_CARRIER_SERVICE),
		    carrierRequest, true);

	    assertThat("Service invocation took longer than 1 second!",
		    httpResponseWrapper.getProcessingDuration(), lessThanOrEqualTo(1000l));

	    waitForInvocationOf(carrierServiceCallbackService);

	    for (int j = 0; j < 4; j++) {
		assertXmlEquals(jmsH24FileRealRequest[j], jmsH24FileExpectedRequest[j]);
	    }

	    LOGGER.info(String.format("CarrierRealResponse: %s",
		    carrierServiceCallbackService.getLastReceivedRequest()));

	} catch (ServiceException e) {
	    e.printStackTrace();
	    fail("ServiceException");
	}
    }

    /**
     * Ignore until bug in OC Testing framework is fixed (Biz services are not
     * mocked properly, if name exceeds 32 chars.)
     */
    @Ignore
    @Test
    public void whenWsaHeaderInformationContainedinRequestThenForwardToInternalOperationsHttpProxy() {

	LOGGER.info("+++ invoke whenWsaHeaderInformationContainedinRequestThenForwardToInternalOperationsHttpProxy");
	DefaultSoapMockService createLabelAndShippingInstructionsInternalRef = new DefaultSoapMockService();
	mockOsbBusinessService(PATH_CREATE_LABEL_AND_SHIPPING_INSTR_INTERNAL,
		createLabelAndShippingInstructionsInternalRef);

	final String carrierRequest = SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			readClasspathFile("createLabelAndShippingInstructionsRequest.xml")),
		SoapUtil.getInstance().messageIdHeader(messageId),
		SoapUtil.getInstance().relatesToHeader(messageId),
		SoapUtil.getInstance().replyToHeader(REPLY_TO));

	final HttpResponseWrapper httpResponseWrapper = invokeSbSoapProxy(
		String.format("http://%s:%s/%s", getConfig().getOsbServerConfig().getServiceHost(),
			getConfig().getOsbServerConfig().getServicePort(), PATH_CARRIER_SERVICE),
		carrierRequest, true);

	waitForInvocationOf(createLabelAndShippingInstructionsInternalRef);

	assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Header/wsa:RelatesTo/text()", messageId,
		createLabelAndShippingInstructionsInternalRef.getLastReceivedRequest());
	assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Header/wsa:ReplyTo/wsa:Address/text()", REPLY_TO,
		createLabelAndShippingInstructionsInternalRef.getLastReceivedRequest());

	assertThat("Service invocation took longer than 2 second!",
		httpResponseWrapper.getProcessingDuration(), lessThanOrEqualTo(2000l));

	LOGGER.info(String.format("CarrierRealResponse: %s", carrierRealResponse));
    }
}
