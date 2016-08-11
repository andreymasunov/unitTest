package de.home24.middleware.salesorderservice;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.AbstractSoaTest;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.SoaConfig;
import com.opitzconsulting.soa.testing.util.SoapUtil;

import de.home24.middleware.octestframework.mock.DefaultSoapMockService;

import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

public class GetAddressDataTest extends AbstractSoaTest {

	private static final Logger LOGGER = Logger.getLogger(SendInboundReclamationTest.class.getSimpleName());
	
	private DefaultSoapMockService getAddressDataMockService = null;
	
	public GetAddressDataTest() {
		super(SoaConfig.readConfigFromPropsFile("de/home24/middleware/config/soaconfig.local.properties"));
	}
	
	@Before
	public void setup() {

		declareXpathNS("ns2", "http://home24.de/interfaces/bas/salesorderservice/salesorderservicemessages/v1");
		declareXpathNS("ns3", "http://www.openapplications.org/oagis/10");
		declareXpathNS("ns4", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("soap-env", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
	}
	
	@After
	public void tearDown()
	{
		getAddressDataMockService = null;
	}

	@Test
	public void getAddressDataSuccess() {
		
		final String requestString = readClasspathFile("GetAddressDataRequest.xml");

		final String soapEnvelope = SoapUtil.getInstance().soapEnvelope(
				SoapVersion.SOAP11, requestString);

		final String serviceResponse = invokeOsbProxyService(
				"SalesOrderService/exposed/v1/SalesOrderService", soapEnvelope);

		LOGGER.fine(String.format("Response from service %s", serviceResponse));

		assertXpathEvaluatesTo(
				"//ns2:getAddressDataResponse/ns2:customerNumber/text()",
				"1000219662", serviceResponse);
	}
	
	@Test
	public void getAddressDataNoSuccess() {
		
		final String requestString = readClasspathFile("GetAddressDataRequestNoSuccess.xml");

		final String soapEnvelope = SoapUtil.getInstance().soapEnvelope(
				SoapVersion.SOAP11, requestString);

		try {
			
			final String serviceResponse = invokeOsbProxyService(
					"SalesOrderService/exposed/v1/SalesOrderService", soapEnvelope);

		} catch (ServiceException e) {
			
			assertXpathEvaluatesTo(
					"//soap-env:Fault/detail/exc:exception/exc:context/exc:activityId/text()",
					"P1001-GET-ADDR-ERR", e.getXml());
			
			assertXpathEvaluatesTo(
					"//soap-env:Fault/detail/exc:exception/exc:faultInfo/exc:faultCode/text()",
					"GET-ADR-00001", e.getXml());

		}

	}
	@Test
	public void getAddressDataMockSuccess() {
		
		final String requestString = readClasspathFile("GetAddressDataRequest.xml");

		final String soapEnvelope = SoapUtil.getInstance().soapEnvelope(
				SoapVersion.SOAP11, requestString);

		getAddressDataMockService = new DefaultSoapMockService(readClasspathFile("MockGetAddressDataResponseSuccess.xml"));
		
		mockOsbBusinessService(
			"SalesOrderService/shared/business-service/BpelToNavisionService",
			getAddressDataMockService);

		final String serviceResponse = invokeOsbProxyService(
				"SalesOrderService/exposed/v1/SalesOrderService", soapEnvelope);

		LOGGER.fine(String.format("Response from service %s", serviceResponse));

		assertXpathEvaluatesTo(
				"//ns2:getAddressDataResponse/ns2:customerNumber/text()",
				"1000219662", serviceResponse);
	}
	
	@Test
	public void getAddressDataMockNoSuccess() {
		
		final String requestString = readClasspathFile("GetAddressDataRequestNoSuccess.xml");

		final String soapEnvelope = SoapUtil.getInstance().soapEnvelope(
				SoapVersion.SOAP11, requestString);

		getAddressDataMockService = new DefaultSoapMockService(readClasspathFile("MockGetAddressDataResponseNoSuccess.xml"));
		
		mockOsbBusinessService(
			"SalesOrderService/shared/business-service/BpelToNavisionService",
			getAddressDataMockService);
		
		try {
			
			final String serviceResponse = invokeOsbProxyService(
					"SalesOrderService/exposed/v1/SalesOrderService", soapEnvelope);

		} catch (ServiceException e) {
			
			assertXpathEvaluatesTo(
					"//soap-env:Fault/detail/exc:exception/exc:context/exc:activityId/text()",
					"P1001-GET-ADDR-ERR", e.getXml());
			
			assertXpathEvaluatesTo(
					"//soap-env:Fault/detail/exc:exception/exc:faultInfo/exc:faultCode/text()",
					"GET-ADR-00001", e.getXml());

		}

	}
}
