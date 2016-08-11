package de.home24.middleware.closeshipmentprocess;

import static de.home24.middleware.octestframework.components.BaseQuery.createBALQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmSoItem;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.OtmDao.Query;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Tests for CloseShipmentProcess.
 * 
 * @author Daniel Dias
 *
 */
public class CloseShipmentProcessTest extends AbstractBaseSoaTest {

	private final String COMPOSITE = "CloseShipmentProcess";
	private final String REVISION = "1.4.0.1";
	private final String PROCESS = "CloseShipmentProcessDelegator_ep";
	private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
	private static final String REPLACE_PARAM_LINE_ID_1 = "LINE_ID_1";
	private static final String REPLACE_PARAM_LINE_ID_2 = "LINE_ID_2";
	private static final String REPLACE_PARAM_PSHOP_ID_1 = "PSHOP_ID_1";
	private static final String REPLACE_PARAM_PSHOP_ID_2 = "PSHOP_ID_2";
	private static final String REPLACE_PARAM_PERP_ID_1 = "PERP_ID_1";
	private static final String REPLACE_PARAM_PERP_ID_2 = "PERP_ID_2";
	private static final String REPLACE_PARAM_METAPACK_AUTH_VALUE = "METAPACK_AUTH_VALUE";
	private static final String REPLACE_METAPACK_AUTH_VALUE = "70195";
	private static final String RESOURCE_DIR = "../processes/GenericProcesses/CloseShipmentProcess/";
	
	private static final Logger LOGGER = Logger.getLogger(CloseShipmentProcessTest.class.getSimpleName());

	private DefaultSoapMockService genericFaultHandlerResendCallback, genericFaultHandlerResend, genericFaultHandlerAbort, salesOrderServiceSuccess,
			salesOrderServiceException, carrierServiceSuccess, carrierServiceBusinessFaultMock,
			salesOrderServiceTechnicalFaultMock, carrierServiceTechnicalFaultMock;

	private List<MockResponsePojo> carrierServiceBusinessFaultMockPojoList = new ArrayList<MockResponsePojo>();
	private List<MockResponsePojo> salesOrderServiceTechnicalFaultMockPojoList = new ArrayList<MockResponsePojo>();
	private List<MockResponsePojo> carrierServiceTechnicalFaultMockPojoList = new ArrayList<MockResponsePojo>();	
	private List<MockResponsePojo> genericFaultHandlerResendMockPojoList = new ArrayList<MockResponsePojo>();		
	private List<MockResponsePojo> genericFaultHandlerAbortMockPojoList = new ArrayList<MockResponsePojo>();		
	private List<MockResponsePojo> salesOrderServiceExceptionMockPojoList = new ArrayList<MockResponsePojo>();	
	private List<MockResponsePojo> successResponseList = new ArrayList<MockResponsePojo>();

	private String randomCorrelationId, 
			randomLineId1, randomLineId2, 
			technicalFaultMsg, 
			randomParentShopItemId1, randomParentErpItemId1,
			randomParentShopItemId2, randomParentErpItemId2;

	public CloseShipmentProcessTest() {
		super("generic");
	}

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		testInitialization();

	}

	@Before
	public void setUp() throws Exception {

		LOGGER.info("+++Create Mocks+++");
		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("csi", "http://home24.de/interfaces/bas/carrier/carrierservicemessages/v1");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("gfh", "http://home24.de/interfaces/bas/genericfaulthandler/genericfaulthandlerservicemessages/v1");

		Random randomNumber = new Random();
		randomCorrelationId = "DS" + String.valueOf(randomNumber.nextInt(100000));
		randomLineId1 = String.valueOf(randomNumber.nextInt(1000));
		randomLineId2 = String.valueOf(randomNumber.nextInt(1000));
		randomParentShopItemId1 = String.valueOf(randomNumber.nextInt(10));
		randomParentErpItemId1 = String.valueOf(randomNumber.nextInt(10000));
		randomParentShopItemId2 = String.valueOf(randomNumber.nextInt(10));
		randomParentErpItemId2 = String.valueOf(randomNumber.nextInt(10000));
		technicalFaultMsg = "General runtime error: Unrecognized SSL message,plaintext connection?";

		// Happy Path Mocks
		successResponseList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		successResponseList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		successResponseList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		successResponseList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		carrierServiceSuccess = new DefaultSoapMockService(successResponseList);

		salesOrderServiceSuccess = new DefaultSoapMockService(
				readClasspathFile(RESOURCE_DIR+"Response_updateSalesOrderLineSuccess.xml"));
	
		// UpdateSalesOrderLineCallback Exception
		salesOrderServiceExceptionMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR+"Response_updateSalesOrderLineException.xml")));
		salesOrderServiceExceptionMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR+"Response_updateSalesOrderLineSuccess.xml")));
		salesOrderServiceException = new DefaultSoapMockService(salesOrderServiceExceptionMockPojoList);
					
		// BusinessFault Mocks	
		carrierServiceBusinessFaultMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR+"carrierCloseShipmentBusinessFault.xml")));
		carrierServiceBusinessFaultMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		carrierServiceBusinessFaultMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		carrierServiceBusinessFaultMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		carrierServiceBusinessFaultMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		carrierServiceBusinessFaultMock = new DefaultSoapMockService(carrierServiceBusinessFaultMockPojoList);

		// TechnicalFault Mocks
		salesOrderServiceTechnicalFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, technicalFaultMsg, ""));
		salesOrderServiceTechnicalFaultMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR+"Response_updateSalesOrderLineSuccess.xml")));
		salesOrderServiceTechnicalFaultMock = new DefaultSoapMockService(salesOrderServiceTechnicalFaultMockPojoList);
		
		carrierServiceTechnicalFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, technicalFaultMsg, ""));
		carrierServiceTechnicalFaultMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		carrierServiceTechnicalFaultMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		carrierServiceTechnicalFaultMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		carrierServiceTechnicalFaultMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		carrierServiceTechnicalFaultMock = new DefaultSoapMockService(carrierServiceTechnicalFaultMockPojoList);
	
		//GenericFaultHandler Mocks
		genericFaultHandlerResendMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR+"Response_ResendGenericFaultHandler.xml")));
		genericFaultHandlerResendMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR+"Response_ResendUpdSOGenericFaultHandler.xml")));
		genericFaultHandlerResend = new DefaultSoapMockService(genericFaultHandlerResendMockPojoList);

		genericFaultHandlerAbortMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR+"Response_AbortGenericFaultHandler.xml")));
		genericFaultHandlerAbortMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR+"Response_AbortUpdSOGenericFaultHandler.xml")));
		genericFaultHandlerAbort = new DefaultSoapMockService(genericFaultHandlerAbortMockPojoList);
		
		genericFaultHandlerResendCallback = new DefaultSoapMockService(
				readClasspathFile(RESOURCE_DIR+"Response_ResendUpdSOGenericFaultHandler.xml"));
		
	}

	@After
	public void tearDown() throws Exception {

		LOGGER.info("+++Delete Mocks+++");
		// Happy Path Mocks
		salesOrderServiceSuccess = null;
		carrierServiceSuccess = null;

		//UpdateCallBackException
		salesOrderServiceException = null;
		
		// BusinessFault Mocks
		carrierServiceBusinessFaultMockPojoList = null;
		carrierServiceBusinessFaultMock = null;

		// TechnicalFault Mocks
		salesOrderServiceTechnicalFaultMockPojoList = null;
		salesOrderServiceTechnicalFaultMock = null;
		carrierServiceTechnicalFaultMockPojoList = null;
		carrierServiceTechnicalFaultMock = null;
		

		//GenericFaulHandler Mocks
		genericFaultHandlerResend = null;
		genericFaultHandlerAbort = null;
		genericFaultHandlerResendCallback = null;
		genericFaultHandlerResendMockPojoList = null;
		genericFaultHandlerAbortMockPojoList = null;
		
	}

	
	@Test
	public void closeShipmentProcessHappyPathTest() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR+"processCloseShipmentRequest.xml"))
				.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
				.replace(REPLACE_PARAM_LINE_ID_1, randomLineId1)
				.replace(REPLACE_PARAM_LINE_ID_2, randomLineId2)
				.replace(REPLACE_PARAM_PSHOP_ID_1, randomParentShopItemId1)
				.replace(REPLACE_PARAM_PSHOP_ID_2, randomParentShopItemId2)
				.replace(REPLACE_PARAM_PERP_ID_1, randomParentErpItemId1)
				.replace(REPLACE_PARAM_PERP_ID_2, randomParentErpItemId2)
				.replace(REPLACE_PARAM_METAPACK_AUTH_VALUE, REPLACE_METAPACK_AUTH_VALUE).build();

		LOGGER.info("+++Mock Composite References");
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceSuccess);
		mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceSuccess);

		LOGGER.info("+++Invoke Composite Service");
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(salesOrderServiceSuccess);
		LOGGER.info("+++Assertions for CarrierService");	
		assertTrue(carrierServiceSuccess.getNumberOfInvocations()==4);
		assertFalse(carrierServiceBusinessFaultMock.hasBeenInvoked());
		assertFalse(carrierServiceTechnicalFaultMock.hasBeenInvoked());
		
		List<String> responseXML = carrierServiceSuccess.getReceivedRequests(); 
		
		for (String rsp : responseXML) {
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/csi:closeShippingInstructionsRequest/csi:requestHeader/mht:KeyValueList/mht:KeyValuePair[./mht:Key='UnitName']/mht:Value/text()",
					REPLACE_METAPACK_AUTH_VALUE, rsp);
		}
			
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/csi:closeShippingInstructionsRequest/csi:parcelReferenceNumber/text()",
				randomCorrelationId + "." + randomLineId1 + "." + "01", responseXML.get(0));
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/csi:closeShippingInstructionsRequest/csi:parcelReferenceNumber/text()",
				randomCorrelationId + "." + randomLineId1 + "." + "02", responseXML.get(1));
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/csi:closeShippingInstructionsRequest/csi:parcelReferenceNumber/text()",
				randomCorrelationId + "." + randomLineId2 + "." + "01", responseXML.get(2));
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/csi:closeShippingInstructionsRequest/csi:parcelReferenceNumber/text()",
				randomCorrelationId + "." + randomLineId2 + "." + "02", responseXML.get(3));
		
		LOGGER.info("+++Assertions for UpdateSalesOrderLine Callback with success");	
		assertTrue(salesOrderServiceSuccess.hasBeenInvoked());
		assertTrue(salesOrderServiceSuccess.getNumberOfInvocations()==1);
		assertFalse(salesOrderServiceTechnicalFaultMock.hasBeenInvoked());

		LOGGER.info("+++Testing OTM for line1:" + randomLineId1 + randomParentShopItemId1 + randomParentErpItemId1);	
		LOGGER.info("+++Testing OTM for lin2:" + randomLineId2 + randomParentShopItemId2 + randomParentErpItemId2);	
		// P201-INIT must be written once to BAL
		assertOTM(randomCorrelationId, "P201-INIT");
		// P201-CLOSE-SHPM must be written 8 times to BAL
		assertThat(getOtmDao().query(createBALQuery(randomCorrelationId, "P201-CLOSE-SHPM")).size(), is(4));
		// P201-UPD-SO-LINE must be written once to BAL
		assertOTM(randomCorrelationId, "P201-UPD-SO-LINE");
		// P201-UPD-SO-LINE must be written once to BAL
		assertOTM(randomCorrelationId, "P201-UPD-SO-LINE-CB");
		// P201-INIT first item must be written in SOItems
		assertOSM(randomCorrelationId, "P201-INIT", randomLineId1, randomParentShopItemId1, randomParentErpItemId1);
		// P201-INIT second item must be written in SOItems
		assertOSM(randomCorrelationId, "P201-INIT", randomLineId2, randomParentShopItemId2, randomParentErpItemId2);
		
	}	
	
	
	@Test
	public void closeShipmentProcessBusinessFaultWithResendTest() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR+"processCloseShipmentRequest.xml"))
				.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
				.replace(REPLACE_PARAM_LINE_ID_1, randomLineId1)
				.replace(REPLACE_PARAM_LINE_ID_2, randomLineId2)
				.replace(REPLACE_PARAM_PSHOP_ID_1, randomParentShopItemId1)
				.replace(REPLACE_PARAM_PSHOP_ID_2, randomParentShopItemId2)
				.replace(REPLACE_PARAM_PERP_ID_1, randomParentErpItemId1)
				.replace(REPLACE_PARAM_PERP_ID_2, randomParentErpItemId2)
				.replace(REPLACE_PARAM_METAPACK_AUTH_VALUE, REPLACE_METAPACK_AUTH_VALUE).build();

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerResend);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceSuccess);
		mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceBusinessFaultMock);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(salesOrderServiceSuccess);
		LOGGER.info("+++Assertions for CarrierService with Business fault outcome as Reendd");	
		//CarrierServiceCall	
		assertTrue(carrierServiceBusinessFaultMock.hasBeenInvoked());
		assertFalse(carrierServiceSuccess.hasBeenInvoked());
		assertFalse(carrierServiceTechnicalFaultMock.hasBeenInvoked());		
		assertFalse(genericFaultHandlerAbort.hasBeenInvoked());
		assertTrue(genericFaultHandlerResend.hasBeenInvoked());
		assertTrue(carrierServiceBusinessFaultMock.getNumberOfInvocations()==5);

		String responseXML = genericFaultHandlerResend.getLastReceivedRequest(); 	
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:category/text()",
				"BusinessFault",responseXML);		

		LOGGER.info("+++Assertions for UpdateSalesOrderLine Callback with success");	
		assertTrue(salesOrderServiceSuccess.hasBeenInvoked());
		assertTrue(salesOrderServiceSuccess.getNumberOfInvocations()==1);
		assertFalse(salesOrderServiceTechnicalFaultMock.hasBeenInvoked());
	}	

	
	@Test
	public void closeShipmentProcessTechnicalFaultWithResendTest() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR+"processCloseShipmentRequest.xml"))
				.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
				.replace(REPLACE_PARAM_LINE_ID_1, randomLineId1)
				.replace(REPLACE_PARAM_LINE_ID_2, randomLineId2)
				.replace(REPLACE_PARAM_PSHOP_ID_1, randomParentShopItemId1)
				.replace(REPLACE_PARAM_PSHOP_ID_2, randomParentShopItemId2)
				.replace(REPLACE_PARAM_PERP_ID_1, randomParentErpItemId1)
				.replace(REPLACE_PARAM_PERP_ID_2, randomParentErpItemId2)
				.replace(REPLACE_PARAM_METAPACK_AUTH_VALUE, REPLACE_METAPACK_AUTH_VALUE).build();

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerResend);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceTechnicalFaultMock);
		mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceTechnicalFaultMock);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(salesOrderServiceTechnicalFaultMock);
		LOGGER.info("+++Assertions for CarrierService with Technical fault response");	
		assertTrue(carrierServiceTechnicalFaultMock.hasBeenInvoked());
		assertFalse(carrierServiceSuccess.hasBeenInvoked());
		assertFalse(carrierServiceBusinessFaultMock.hasBeenInvoked());		
		assertFalse(genericFaultHandlerAbort.hasBeenInvoked());
		assertTrue(genericFaultHandlerResend.hasBeenInvoked());
		assertTrue(carrierServiceTechnicalFaultMock.getNumberOfInvocations()==5);
		
		//In this case we evaluate two calls to GenericFaultHandler. 
		//First for CarrierService Fault and Second for UpdateSalesOrderLine Fault
		//assertTrue(genericFaultHandlerResend.getNumberOfInvocations()==2);
		assertTrue(genericFaultHandlerResend.hasBeenInvoked());
		
		List<String> responseXML = genericFaultHandlerResend.getReceivedRequests(); 	
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:category/text()", 
				"TechnicalFault",responseXML.get(0));		

		LOGGER.info("+++Assertions for UpdateSalesOrderLine Callback after with technical Fault Resend");	
		assertTrue(salesOrderServiceTechnicalFaultMock.hasBeenInvoked());	 	
//		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:category/text()", 
//				"TechnicalFault",responseXML.get(1));
	}	
	
	
	@Test
	public void closeShipmentProcessBusinessFaultWithAbortTest() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR+"processCloseShipmentRequest.xml"))
				.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
				.replace(REPLACE_PARAM_LINE_ID_1, randomLineId1)
				.replace(REPLACE_PARAM_LINE_ID_2, randomLineId2)
				.replace(REPLACE_PARAM_PSHOP_ID_1, randomParentShopItemId1)
				.replace(REPLACE_PARAM_PSHOP_ID_2, randomParentShopItemId2)
				.replace(REPLACE_PARAM_PERP_ID_1, randomParentErpItemId1)
				.replace(REPLACE_PARAM_PERP_ID_2, randomParentErpItemId2)
				.replace(REPLACE_PARAM_METAPACK_AUTH_VALUE, REPLACE_METAPACK_AUTH_VALUE).build();

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerAbort);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceSuccess);
		mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceBusinessFaultMock);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(carrierServiceBusinessFaultMock);
		LOGGER.info("+++Assertions for CarrierService with Business fault response");	
		//CarrierServiceCall	
		assertTrue(carrierServiceBusinessFaultMock.hasBeenInvoked());
		assertFalse(carrierServiceSuccess.hasBeenInvoked());
		assertFalse(carrierServiceTechnicalFaultMock.hasBeenInvoked());		
		assertFalse(genericFaultHandlerResend.hasBeenInvoked());
		assertTrue(genericFaultHandlerAbort.hasBeenInvoked());
		
		String responseXML = genericFaultHandlerAbort.getLastReceivedRequest(); 	
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:category/text()", 
				"BusinessFault",responseXML);
		
		LOGGER.info("+++Assertions for CarrierService after Business fault outcome Abort");	
		assertTrue(carrierServiceBusinessFaultMock.getNumberOfInvocations()==1);	
		assertFalse(salesOrderServiceSuccess.hasBeenInvoked());
		assertFalse(salesOrderServiceTechnicalFaultMock.hasBeenInvoked());
	}	
	
	@Test
	public void closeShipmentProcessTechnicalFaultWithAbortTest() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR+"processCloseShipmentRequest.xml"))
				.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
				.replace(REPLACE_PARAM_LINE_ID_1, randomLineId1)
				.replace(REPLACE_PARAM_LINE_ID_2, randomLineId2)
				.replace(REPLACE_PARAM_PSHOP_ID_1, randomParentShopItemId1)
				.replace(REPLACE_PARAM_PSHOP_ID_2, randomParentShopItemId2)
				.replace(REPLACE_PARAM_PERP_ID_1, randomParentErpItemId1)
				.replace(REPLACE_PARAM_PERP_ID_2, randomParentErpItemId2)
				.replace(REPLACE_PARAM_METAPACK_AUTH_VALUE, REPLACE_METAPACK_AUTH_VALUE).build();

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerAbort);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceSuccess);
		mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceTechnicalFaultMock);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(carrierServiceTechnicalFaultMock);
		LOGGER.info("+++Assertions for CarrierService with Technical fault response");	
		assertTrue(carrierServiceTechnicalFaultMock.hasBeenInvoked());
		assertFalse(carrierServiceSuccess.hasBeenInvoked());
		assertFalse(carrierServiceBusinessFaultMock.hasBeenInvoked());		
		assertTrue(genericFaultHandlerAbort.hasBeenInvoked());
		assertFalse(genericFaultHandlerResend.hasBeenInvoked());
		
		String responseXML = genericFaultHandlerAbort.getLastReceivedRequest(); 	
		System.out.println("retryResponseXML: " + responseXML);
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:category/text()", 
				"TechnicalFault",responseXML);		

		LOGGER.info("+++Assertions for CarrierService after technical fault outcome Abort");	
		assertTrue(carrierServiceTechnicalFaultMock.getNumberOfInvocations()==1);	
		assertFalse(salesOrderServiceSuccess.hasBeenInvoked());
		assertFalse(salesOrderServiceTechnicalFaultMock.hasBeenInvoked());
	}	
	
	
	@Test
	public void closeShipmentProcessUpdSOCallbackExceptionTest() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR+"processCloseShipmentRequest.xml"))
				.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
				.replace(REPLACE_PARAM_LINE_ID_1, randomLineId1)
				.replace(REPLACE_PARAM_LINE_ID_2, randomLineId2)
				.replace(REPLACE_PARAM_PSHOP_ID_1, randomParentShopItemId1)
				.replace(REPLACE_PARAM_PSHOP_ID_2, randomParentShopItemId2)
				.replace(REPLACE_PARAM_PERP_ID_1, randomParentErpItemId1)
				.replace(REPLACE_PARAM_PERP_ID_2, randomParentErpItemId2)
				.replace(REPLACE_PARAM_METAPACK_AUTH_VALUE, REPLACE_METAPACK_AUTH_VALUE).build();

		LOGGER.info("+++Mock Composite References");
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerResendCallback);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceException);
		mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceSuccess);

		LOGGER.info("+++Invoke Composite Service");
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(salesOrderServiceException);
		LOGGER.info("+++Assertions for CarrierService");	
		assertTrue(carrierServiceSuccess.getNumberOfInvocations()==4);
		assertFalse(carrierServiceBusinessFaultMock.hasBeenInvoked());
		assertFalse(carrierServiceTechnicalFaultMock.hasBeenInvoked());
		
		List<String> responseXML = carrierServiceSuccess.getReceivedRequests(); 
		
		for (String rsp : responseXML) {
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/csi:closeShippingInstructionsRequest/csi:requestHeader/mht:KeyValueList/mht:KeyValuePair[./mht:Key='UnitName']/mht:Value/text()",
					REPLACE_METAPACK_AUTH_VALUE, rsp);
		}
			
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/csi:closeShippingInstructionsRequest/csi:parcelReferenceNumber/text()",
				randomCorrelationId + "." + randomLineId1 + "." + "01", responseXML.get(0));
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/csi:closeShippingInstructionsRequest/csi:parcelReferenceNumber/text()",
				randomCorrelationId + "." + randomLineId1 + "." + "02", responseXML.get(1));
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/csi:closeShippingInstructionsRequest/csi:parcelReferenceNumber/text()",
				randomCorrelationId + "." + randomLineId2 + "." + "01", responseXML.get(2));
		assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/csi:closeShippingInstructionsRequest/csi:parcelReferenceNumber/text()",
				randomCorrelationId + "." + randomLineId2 + "." + "02", responseXML.get(3));
		
		waitForInvocationOf(salesOrderServiceException, 2, 60);
		LOGGER.info("+++Assertions for UpdateSalesOrderLine Callback Exception");	
		System.out.println("salesOrderServiceExceptionNumber: " + salesOrderServiceException.getNumberOfInvocations());
		assertTrue(salesOrderServiceException.hasBeenInvoked());
		assertTrue(salesOrderServiceException.getNumberOfInvocations()==2);
		assertFalse(salesOrderServiceSuccess.hasBeenInvoked());			
		assertTrue(genericFaultHandlerResendCallback.hasBeenInvoked());	
	
	}	
	
	private class QueryOsmSoItemByCorrelationIDAndActivityCode implements Query<OsmSoItem> {
		private String correlationId = null;
		private String activityCode = null;
		private String erpItemId = null;
		private String parentShopItemId = null;
		private String parentErpItemId = null;
		QueryOsmSoItemByCorrelationIDAndActivityCode(String correlationId, String activityCode, 
				String erpItemId, String parentShopItemId, String parentErpItemId) {
			this.correlationId = correlationId;
			this.activityCode = activityCode;
			this.erpItemId = erpItemId;
			this.parentShopItemId = parentShopItemId;
			this.parentErpItemId = parentErpItemId;
		}
		@Override
		public Class<OsmSoItem> getExpectedType() {
			return OsmSoItem.class;
		}
		@Override
		public String getQuery() {
			return String.format("select * from OSM_SO_ITEM where correlation_id = ? and status_code = ? "
					+ " and ERP_ITEM_ID = ?"
					+ " and PARENT_SHOP_ITEM_ID = ?"
					+ " and PARENT_ERP_ITEM_ID = ?");
		}
		@Override
		public Object[] getQueryParameters() {

			String[] params = { correlationId, activityCode, erpItemId, parentShopItemId, parentErpItemId };
			return params;
		}
	}
		
	private void assertOTM(final String correlationId, final String expectedActivityCode) {
		List<BalActivities> balActivity = getOtmDao().query(createBALQuery(correlationId, expectedActivityCode));

		assertThat("Different number of entries found for " + expectedActivityCode, balActivity, hasSize(1));
		assertThat("ActivityCode does not meet expectation!", balActivity.get(0).getActivityCode(),
				equalTo(expectedActivityCode));
		assertThat("CorrelationId does not meet expectation!", balActivity.get(0).getCorrelationId(),
				equalTo(correlationId));
		assertThat("Error flag is not set properly!", balActivity.get(0).getError(), equalTo("N"));

	}

	private void assertOSM(final String correlationId, final String expectedActivityCode, 
			final String erpItemId, final String parentShopItemId, final String ParentErpItemId) {
		int querySize = getOtmDao().query(new QueryOsmSoItemByCorrelationIDAndActivityCode(
				correlationId, expectedActivityCode,
				erpItemId, parentShopItemId, ParentErpItemId)).size();
		assertThat("OSM SalesOrderItems should be written for " + expectedActivityCode,
				querySize == 1);
	}
}
