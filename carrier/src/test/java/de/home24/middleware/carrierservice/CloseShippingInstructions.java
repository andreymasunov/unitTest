package de.home24.middleware.carrierservice;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;

public class CloseShippingInstructions extends AbstractBaseSoaTest {

	private final static String PATH_CARRIER_SERVICE = "CarrierService/exposed/v1/CarrierService";
	private final static String PATH_METAPACK_API = "CarrierService/shared/v1/business-service/MetapapackBlackBoxBusinessService";

	private static final Logger LOGGER = Logger.getLogger(CloseShippingInstructions.class.getSimpleName());

	private DefaultSoapMockService metapackCloseShippingInstructionsSuccesMock;
	private DefaultSoapMockService metapackCloseShippingInstructionsErrorMock;
	private DefaultSoapMockService metapackCloseShippingInstructionsFaultMock;
	private List<MockResponsePojo> metapackCloseShippingInstructionsFaultMockPojoList = new ArrayList<MockResponsePojo>();

	@BeforeClass
	public static void setUpBeforeClass() {

		testInitialization();
	}

	@Before
	public void setUp() {

		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("csi", "http://home24.de/interfaces/bas/carrier/carrierservicemessages/v1");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("blac", "http://xlogics.eu/blackbox");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");

		LOGGER.info("+++Create Mocks+++");
		metapackCloseShippingInstructionsSuccesMock = new DefaultSoapMockService(
				readClasspathFile("closeshippinginstructions/metapackMarkShipmentsSuccessResponse.xml"));
		metapackCloseShippingInstructionsErrorMock = new DefaultSoapMockService(
				readClasspathFile("closeshippinginstructions/metapackMarkShipmentsBusinessFaultResponse.xml"));
		metapackCloseShippingInstructionsFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT,"",""));
		metapackCloseShippingInstructionsFaultMock = new DefaultSoapMockService(metapackCloseShippingInstructionsFaultMockPojoList);
		
	}

	@After
	public void tearDown() {
		LOGGER.info("+++Delete Mocks+++");
		metapackCloseShippingInstructionsSuccesMock = null;
		metapackCloseShippingInstructionsErrorMock = null;
	}

	/**
	 * Invoke Carrier with success
	 */
	@Test
	public void closeShippingInstructionsSucces() {

		final String carrierRequest = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				readClasspathFile("closeshippinginstructions/closeShippingInstructionsSuccessRequest.xml"));
		final String carrierExpectedResponse = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				readClasspathFile("closeshippinginstructions/closeShippingInstructionsSuccessResponse.xml"));

		mockOsbBusinessService(PATH_METAPACK_API, metapackCloseShippingInstructionsSuccesMock);

		LOGGER.info("+++invoke Carrier closeShippingInstructions with Succes");
		String invocationResult = invokeOsbProxyService(PATH_CARRIER_SERVICE, carrierRequest);
		LOGGER.info("+++invocation Result: "+invocationResult);

		assertTrue(metapackCloseShippingInstructionsSuccesMock.hasBeenInvoked());

		assertXpathEvaluatesTo("//blac:MarkShipmentsRequest/blac:InputParameters/blac:ShippingParameter[blac:Name=\"Closer.Selection\"]/blac:Value/text()", 
				"ParcelReferenceNo",
				metapackCloseShippingInstructionsSuccesMock.getLastReceivedRequest());
		assertXpathEvaluatesTo("//blac:MarkShipmentsRequest/blac:InputParameters/blac:ShippingParameter[blac:Name=\"Closer.Identifier\"]/blac:Value/text()", 
				"999990222204928",
				metapackCloseShippingInstructionsSuccesMock.getLastReceivedRequest());
		assertXpathEvaluatesTo("//blac:MarkShipmentsRequest/blac:InputParameters/blac:ShippingParameter[blac:Name=\"Closer.MarkedState\"]/blac:Value/text()", 
				"True",
				metapackCloseShippingInstructionsSuccesMock.getLastReceivedRequest());
		
		assertXmlEquals(invocationResult, carrierExpectedResponse);

	}
	
	/**
	 * Invoke Carrier with BusinessFault
	 */
	@Test
	public void closeShippingInstructionsBusinessError() {

		final String carrierRequest = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				readClasspathFile("closeshippinginstructions/closeShippingInstructionsBusinessErrorRequest.xml"));
		final String carrierExpectedResponse = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				readClasspathFile("closeshippinginstructions/closeShippingInstructionsBusinessFaultResponse.xml"));
		String carrierResponse="";

		mockOsbBusinessService(PATH_METAPACK_API, metapackCloseShippingInstructionsErrorMock);

		LOGGER.info("+++invoke Carrier closeShippingInstructions with Succes");
		try {
			carrierResponse = invokeOsbProxyService(PATH_CARRIER_SERVICE, carrierRequest);
			LOGGER.info("+++invocation Result: "+carrierResponse);
			fail();
		} catch (ServiceException e) {
			e.printStackTrace();
			LOGGER.info("+++Metapack was invoked ="+String.valueOf(metapackCloseShippingInstructionsErrorMock.hasBeenInvoked()));
			assertTrue(metapackCloseShippingInstructionsErrorMock.hasBeenInvoked());
			assertTrue(metapackCloseShippingInstructionsErrorMock.getNumberOfInvocations()==1);
			LOGGER.info("+++Metapack Number of invocations ="+String.valueOf(metapackCloseShippingInstructionsErrorMock.getNumberOfInvocations()));
			String metapackRequest = metapackCloseShippingInstructionsErrorMock.getLastReceivedRequest();
			LOGGER.info("+++Metapack request ="+metapackRequest);

			assertXpathEvaluatesTo("//blac:MarkShipmentsRequest/blac:InputParameters/blac:ShippingParameter[blac:Name=\"Closer.Selection\"]/blac:Value/text()", 
					"ParcelReferenceNo",
					metapackRequest);
			assertXpathEvaluatesTo("//blac:MarkShipmentsRequest/blac:InputParameters/blac:ShippingParameter[blac:Name=\"Closer.Identifier\"]/blac:Value/text()", 
					"999990222204928",
					metapackRequest);
			assertXpathEvaluatesTo("//blac:MarkShipmentsRequest/blac:InputParameters/blac:ShippingParameter[blac:Name=\"Closer.MarkedState\"]/blac:Value/text()", 
					"True",
					metapackRequest);

			carrierResponse = e.getXml();
			LOGGER.info("+++Carrier expected invocation response ="+carrierExpectedResponse);
			LOGGER.info("+++Carrier invocation response ="+carrierResponse);
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()", 
					"BusinessFault",
					carrierResponse);
		}
	}
		
		/**
		 * Invoke Carrier with TechnicalFault
		 */
		@Test
		public void closeShippingInstructionsTechnicalError() {

			final String carrierRequest = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
					readClasspathFile("closeshippinginstructions/closeShippingInstructionsBusinessErrorRequest.xml"));
			final String carrierExpectedResponse = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
					readClasspathFile("closeshippinginstructions/closeShippingInstructionsTechnicalFaultResponse.xml"));
			String carrierResponse="";

			mockOsbBusinessService(PATH_METAPACK_API, metapackCloseShippingInstructionsFaultMock);

			LOGGER.info("+++invoke Carrier closeShippingInstructions with Succes");
			try {
				carrierResponse = invokeOsbProxyService(PATH_CARRIER_SERVICE, carrierRequest);
				LOGGER.info("+++invocation Result: "+carrierResponse);
				fail();
			} catch (ServiceException e) {
				e.printStackTrace();

				carrierResponse = e.getXml();
				LOGGER.info("+++Carrier expected invocation response ="+carrierExpectedResponse);
				LOGGER.info("+++Carrier invocation response ="+carrierResponse);
				assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()", 
						"TechnicalFault",
						carrierResponse);
			}
	}
}
