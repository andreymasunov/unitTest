package de.home24.middleware.salesorderservice;

import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.AbstractSoaTest;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.SoaConfig;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

public class SendInboundReclamationTest extends AbstractSoaTest {

	private static final Logger LOGGER = Logger
			.getLogger(SendInboundReclamationTest.class.getSimpleName());

	public SendInboundReclamationTest() {
		super(
				SoaConfig
						.readConfigFromPropsFile("de/home24/middleware/config/soaconfig.local.properties"));
	}

	@Before
	public void setup() {

		declareXpathNS(
				"ns2",
				"http://home24.de/interfaces/bas/salesorderservice/salesorderservicemessages/v1");
	}

	@Test
	public void sendSparepartInboundReclamation() {

		sendMessageAndExpectSuccessfulProcessing("InboundReclamationSparepart.xml");
	}

	@Test
	public void sendSparepartWithCommentsInboundReclamation() {

		sendMessageAndExpectSuccessfulProcessing("InboundReclamationSparepartWithComments.xml");
	}

	@Test
	public void sendInboundReclamationWithComments() {

		sendMessageAndExpectSuccessfulProcessing("InboundReclamationWithComments.xml");
	}

	@Test
	public void sendInboundReclamationWithUnknownOrderNumber() {

		declareXpathNS(
				"soap-env",
				"http://schemas.xmlsoap.org/soap/envelope/");

		declareXpathNS(
				"ns3",
				"http://home24.de/data/common/exceptiontypes/v1");

		sendMessageAndExpectFaultyProcessing("InboundReclamationWithUnknownOrderNumber.xml");
	}

	private void sendMessageAndExpectSuccessfulProcessing(
			String pRequestFilename) {

		final String requestString = readClasspathFile("InboundReclamationWithComments.xml");

		final String soapEnvelope = SoapUtil.getInstance().soapEnvelope(
				SoapVersion.SOAP11, requestString);

		final String serviceResponse = invokeOsbProxyService(
				"SalesOrderService/exposed/v1/SalesOrderService", soapEnvelope);

		LOGGER.fine(String.format("Response from service %s", serviceResponse));

		assertXpathEvaluatesTo(
				"//ns2:sendInboundReclamationToERPResponse/ns2:updateAcknowledgement/text()",
				"ACK", serviceResponse);
	}

	private void sendMessageAndExpectFaultyProcessing(
			String pRequestFilename) {
		
		final String requestString = readClasspathFile("InboundReclamationWithUnknownOrderNumber.xml");

		final String soapEnvelope = SoapUtil.getInstance().soapEnvelope(
				SoapVersion.SOAP11, requestString);
		try {
			final String serviceResponse = invokeOsbProxyService(
				"SalesOrderService/exposed/v1/SalesOrderService", soapEnvelope);
		} catch (ServiceException e) {
			
			//System.out.println(e.getXml());
			
			assertXpathEvaluatesTo(
					"//soap-env:Fault/detail/ns3:exception/ns3:context/ns3:activityId/text()",
					"P101-SIISHOP-ACK-ERR", e.getXml());
		}

	}
}
