package de.home24.middleware.sii;

import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.MockService;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;

public class SendInboundReclamationTest extends AbstractBaseSoaTest {

    public SendInboundReclamationTest() {
	super("generic");
    }

    @Before
    public void setUp() throws Exception {

	declareXpathNS("ns2",
		"http://home24.de/interfaces/bas/salesorderservice/salesorderservicemessages/v1");
    }

    @Test
    public void sendInboundReclamationToErp() {

	String requestXml = readClasspathFile("SendInboundReclamationRequest.xml");

	mockCompositeReference("SIIMessageHandler", "1.2", "SalesOrderService", new MockService() {

	    @Override
	    public String serviceCallReceived(String serviceName, String requestStr)
		    throws ServiceException, Exception {
		return SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			readClasspathFile("SendInboundReclamationResponse.xml"));
	    }
	});

	final String invocationResult = invokeCompositeService("SIIMessageHandler", "1.2",
		"SIIMessageHandlerProcess",
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXml));

	System.out.println("INVOCATION RESULT: " + invocationResult);

	// assertXpathEvaluatesTo("count(//ns2:sendInboundReclamationToERPResponse)",
	// "1", invocationResult);
    }
}
