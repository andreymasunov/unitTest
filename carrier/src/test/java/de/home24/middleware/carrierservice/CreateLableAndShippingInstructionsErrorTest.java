package de.home24.middleware.carrierservice;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.fail;

import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.HttpResponseWrapper;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;

/**
 * Unit test for CarrierService.
 */
public class CreateLableAndShippingInstructionsErrorTest
	extends AbstractCreateLabelAndShippingInstructionsTest {

    private DefaultSoapMockService carrierServiceErrorCallbackService;

    @Override
    protected void setUpOverrideable() {

	carrierServiceErrorCallbackService = new DefaultSoapMockService(
		readClasspathFile("createLabelAndShippingInstructionsWithOneParcelErrorResponse.xml"));
	callbackUrl = mockOsbBusinessService(PATH_CARRIER_CALLBACK, carrierServiceErrorCallbackService);
    }

    @Test
    public void createLabelAndShippingInstructionsWithOneParcelError() {

	metapackOkResponse[3] = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		readClasspathFile("metapackNokResponse.xml"));

	try {
	    final HttpResponseWrapper httpResponseWrapper = invokeSbSoapProxy(
		    String.format("http://%s:%s/%s", getConfig().getOsbServerConfig().getServiceHost(),
			    getConfig().getOsbServerConfig().getServicePort(), PATH_CARRIER_SERVICE),
		    carrierRequest, true);

	    assertThat("Service invocation took longer than 1 second!",
		    httpResponseWrapper.getProcessingDuration(), lessThanOrEqualTo(1000l));

	    waitForInvocationOf(carrierServiceErrorCallbackService);

	    for (int j = 0; j < 3; j++) {
		assertXmlEquals(jmsH24FileRealRequest[j], jmsH24FileExpectedRequest[j]);
	    }

	    assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/exc:exception/exc:category/text()",
		    "BusinessFault", carrierServiceErrorCallbackService.getLastReceivedRequest());

	    LOGGER.info(String.format("CarrierRealResponse: %s",
		    carrierServiceErrorCallbackService.getLastReceivedRequest()));

	} catch (ServiceException e) {
	    e.printStackTrace();
	    fail("ServiceException");
	}
    }

}
