package de.home24.middleware.salesordercreationprocess;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmSo;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.BaseQuery;
import de.home24.middleware.octestframework.components.BaseQuery.SqlOp;
import de.home24.middleware.octestframework.components.QueryPredicate;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class SalesOrderCreationProcessTest extends AbstractBaseSoaTest {

	private final static String CORRELATION_ID = "30201002346654";

	private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
	private static final String REPLACE_PARAM_PAYLOAD = "PAYLOAD";
	public static final String REPLACE_PARAM_NOTE_PURCHASINGORDER = "NOTE_PURCHASING_ORDER";
	public static final String REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL = "NOTE_SUPPRESS_PO_MAIL";
	public static final String REPLACE_PARAM_NAME_EDI_PARTNER_NAME = "NAME_EDI_PARTNER_NAME";
	public static final String REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE = "ORDERS_IS_ACTIVE";
	public static final String REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE = "NO_EDI_PROCESS_AVAILABLE";

	public static final String COMPOSITE = "SalesOrderCreationProcess";
	public static final String REVISION = "1.3.0.0";
	public static final String PROCESS = "SalesOrderCreationDelegator_ep";


	private AbstractSoapMockService salesOrderServiceReferenceRef ;
	private AbstractSoapMockService purchaseOrderGenerationProcessReferenceRef ;
	private AbstractSoapMockService genericFaultHandlerRef ;
	private AbstractSoapMockService cancellationInvestigatorProcessRef ;
	private AbstractSoapMockService erpLegacyFacadeWSRef;
	
	private static final String RESOURCES = "../processes/GenericProcesses/SalesOrderCreationProcess/";

	public SalesOrderCreationProcessTest() {
		super("generic");
	}

	@Before
	public void setUp() throws Exception {

		declareXpathNS("exception", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("po",
				"http://home24.de/interfaces/bps/processpurchaseordergroup/purchaseordergrouphandlingprocessmessages/v1");
		declareXpathNS("header", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("sosm", "http://home24.de/interfaces/bas/salesorderservice/salesorderservicemessages/v1");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("gfhm", "http://home24.de/interfaces/bas/genericfaulthandler/genericfaulthandlerservicemessages/v1");
		declareXpathNS("gfh", "http://home24.de/data/custom/genericfaulthandler/v1");
		
		

		getOtmDao().delete(createDeleteBALQuery());
		getOtmDao().delete(createDeleteOSMSOQuery());
		
		salesOrderServiceReferenceRef = new DefaultSoapMockService(
				readClasspathFile(""));
		purchaseOrderGenerationProcessReferenceRef = new DefaultSoapMockService("");
		genericFaultHandlerRef = new DefaultSoapMockService("");
		cancellationInvestigatorProcessRef = new DefaultSoapMockService(readClasspathFile("../processes/GenericProcesses/CancellationInvestigatorProcess/processCancellationInvestigationResponse01.xml"));
		erpLegacyFacadeWSRef = new DefaultSoapMockService("");
	
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderServiceReference", salesOrderServiceReferenceRef);
		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderGenerationProcessReference", purchaseOrderGenerationProcessReferenceRef);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);
		mockCompositeReference(COMPOSITE, REVISION, "CancellationInvestigatorProcess",
				cancellationInvestigatorProcessRef);
		mockCompositeReference(COMPOSITE, REVISION, "ERPLegacyFacadeWS", erpLegacyFacadeWSRef);
		
	}
	
	
	@After
	public void tearDown() throws Exception {
		salesOrderServiceReferenceRef = null ;
		purchaseOrderGenerationProcessReferenceRef = null ;
		genericFaultHandlerRef = null ;
		cancellationInvestigatorProcessRef = null ;
		erpLegacyFacadeWSRef = null ;
		
	}
	
	@Test
	public void initiateSalesOrderCreationWaitForPaymentPaymentreceived() throws Exception {
		String requestXML = new ParameterReplacer(readClasspathFile(RESOURCES+"initiateSalesOrderCreationRequest01.xml"))
				.build();
		
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));
		this.sendReceivePaymentNotificationRequestToWaitingInstance();
		
		
		waitForInvocationOf(purchaseOrderGenerationProcessReferenceRef);
		
		assertThat("cancellationInvestigatorProcessRef has not been invoked" , cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
		assertThat("purchaseOrderGenerationProcessReference has not been invoked" , purchaseOrderGenerationProcessReferenceRef.hasBeenInvoked(),is(true));
		assertThat("salesOrderServiceReferenceRef should not been invoked" , salesOrderServiceReferenceRef.hasBeenInvoked(),is(false));
		assertThat("genericFaultHandlerRef should not been invoked" , genericFaultHandlerRef.hasBeenInvoked(),is(false));
		assertThat("erpLegacyFacadeWSRef should not been invoked" , erpLegacyFacadeWSRef.hasBeenInvoked(),is(false));
		
		assertThat("BAL has not been written for P101-ERP-ACK",
				getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-WAIT",
				getOtmDao().query(createBALQuery("P101-WAIT")).size() == 1);
		assertThat("BAL has not been written for P101-PAY-ACK",
				getOtmDao().query(createBALQuery("P101-PAY-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-P290",
				getOtmDao().query(createBALQuery("P101-P290")).size() == 1);
		assertThat("BAL has not been written for P101-CALL-P1000",
				getOtmDao().query(createBALQuery("P101-CALL-P1000")).size() == 1);

	}
	
	

	
	@Test
	public void initiateSalesOrderCreationAndCancellOrder() throws Exception {
		String requestXML = new ParameterReplacer(readClasspathFile(RESOURCES+"initiateSalesOrderCreationRequest01.xml"))
				.build();
		
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		this.sendReceiveCancellationRequestToWaitingInstance();
		
		assertThat("cancellationInvestigatorProcessRef should not been invoked" , cancellationInvestigatorProcessRef.hasBeenInvoked(), is(false));
		assertThat("purchaseOrderGenerationProcessReference should not been invoked" , purchaseOrderGenerationProcessReferenceRef.hasBeenInvoked(),is(false));
		assertThat("salesOrderServiceReferenceRef should not been invoked" , salesOrderServiceReferenceRef.hasBeenInvoked(),is(false));
		assertThat("genericFaultHandlerRef should not been invoked" , genericFaultHandlerRef.hasBeenInvoked(),is(false));
		assertThat("erpLegacyFacadeWSRef should not been invoked" , erpLegacyFacadeWSRef.hasBeenInvoked(),is(false));
		
		assertThat("BAL has not been  written for P101-ERP-ACK",
				getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-WAIT",
				getOtmDao().query(createBALQuery("P101-WAIT")).size() == 1);
		assertThat("BAL has not been written for P101-PAY-ACK",
				getOtmDao().query(createBALQuery("P101-CANCEL")).size() == 1);
		assertThat("BAL  should not been written for P101-P290",
				getOtmDao().query(createBALQuery("P101-P290")).size() == 0);
		assertThat("BAL  should not been written for P101-PAY-ACK",
				getOtmDao().query(createOSMSOQuery("P101-PAY-ACK")).size() == 0);
		assertThat("BAL should not been written for P101-CALL-P1000",
				getOtmDao().query(createBALQuery("P101-CALL-P1000")).size() == 0);
		
	}
	

	
	@Test
	public void initiateSalesOrderCreationCancellationInvestigatorSOCancelled() throws Exception {
		String requestXML = new ParameterReplacer(readClasspathFile(RESOURCES+"initiateSalesOrderCreationRequest01.xml"))
				.build();
		
		
		//Prepare Cancellation investigator
		cancellationInvestigatorProcessRef = new DefaultSoapMockService(readClasspathFile("../processes/GenericProcesses/CancellationInvestigatorProcess/processCancellationInvestigationResponseSOCancelled.xml"));
		mockCompositeReference(COMPOSITE, REVISION, "CancellationInvestigatorProcess",
				cancellationInvestigatorProcessRef);
		
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));
		this.sendReceivePaymentNotificationRequestToWaitingInstance();
		
		waitForInvocationOf(cancellationInvestigatorProcessRef);
		
		assertThat("cancellationInvestigatorProcessRef has not been invoked" , cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
		assertThat("purchaseOrderGenerationProcessReference should not been invoked" , purchaseOrderGenerationProcessReferenceRef.hasBeenInvoked(),is(false));
		assertThat("salesOrderServiceReferenceRef should not been invoked" , salesOrderServiceReferenceRef.hasBeenInvoked(),is(false));
		assertThat("genericFaultHandlerRef should not been invoked" , genericFaultHandlerRef.hasBeenInvoked(),is(false));
		assertThat("erpLegacyFacadeWSRef should not been invoked" , erpLegacyFacadeWSRef.hasBeenInvoked(),is(false));
		
		assertThat("BAL has not been written for P101-ERP-ACK",
				getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-WAIT",
				getOtmDao().query(createBALQuery("P101-WAIT")).size() == 1);
		assertThat("BAL has not been written for P101-PAY-ACK",
				getOtmDao().query(createBALQuery("P101-PAY-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-P290",
				getOtmDao().query(createBALQuery("P101-P290")).size() == 1);
		assertThat("BAL should not been written for P101-CALL-P1000",
				getOtmDao().query(createBALQuery("P101-CALL-P1000")).size() == 0);
		
	}
	
	@Test
	public void initiateSalesOrderCreationCancellationInvestigatorAllDSCancelled() throws Exception {
		String requestXML = new ParameterReplacer(readClasspathFile(RESOURCES+"initiateSalesOrderCreationRequest01.xml"))
				.build();
		
		
		//Prepare Cancellation investigator
		cancellationInvestigatorProcessRef = new DefaultSoapMockService(readClasspathFile("../processes/GenericProcesses/CancellationInvestigatorProcess/processCancellationInvestigationResponseAllDSCancelled.xml"));
		mockCompositeReference(COMPOSITE, REVISION, "CancellationInvestigatorProcess",
				cancellationInvestigatorProcessRef);
		
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));
		this.sendReceivePaymentNotificationRequestToWaitingInstance();
		
		waitForInvocationOf(cancellationInvestigatorProcessRef);
		
		assertThat("cancellationInvestigatorProcessRef has not been invoked" , cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
		assertThat("purchaseOrderGenerationProcessReference should not been invoked" , purchaseOrderGenerationProcessReferenceRef.hasBeenInvoked(),is(false));
		assertThat("salesOrderServiceReferenceRef should not been invoked" , salesOrderServiceReferenceRef.hasBeenInvoked(),is(false));
		assertThat("genericFaultHandlerRef should not been invoked" , genericFaultHandlerRef.hasBeenInvoked(),is(false));
		assertThat("erpLegacyFacadeWSRef should not been invoked" , erpLegacyFacadeWSRef.hasBeenInvoked(),is(false));
		
		assertThat("BAL has not been written for P101-ERP-ACK",
				getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-WAIT",
				getOtmDao().query(createBALQuery("P101-WAIT")).size() == 1);
		assertThat("BAL has not been written for P101-PAY-ACK",
				getOtmDao().query(createBALQuery("P101-PAY-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-P290",
				getOtmDao().query(createBALQuery("P101-P290")).size() == 1);
		assertThat("BAL should not been written for P101-CALL-P1000",
				getOtmDao().query(createBALQuery("P101-CALL-P1000")).size() == 0);
		
	}
	
	
	
	@Test
	public void initiateSalesOrderCreationNoWaitForPaymentNoLinesNoWaitForPayment() throws Exception {
		String requestXML = new ParameterReplacer(readClasspathFile(RESOURCES+"initiateSalesOrderCreationRequestNoLinesNoWaitForPayment.xml"))
				.build();
		
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));
		
		waitForInvocationOf(purchaseOrderGenerationProcessReferenceRef);
		
		assertThat("cancellationInvestigatorProcessRef has not been invoked" , cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
		assertThat("purchaseOrderGenerationProcessReference should not been invoked" , purchaseOrderGenerationProcessReferenceRef.hasBeenInvoked(),is(false));
		assertThat("salesOrderServiceReferenceRef should not been invoked" , salesOrderServiceReferenceRef.hasBeenInvoked(),is(false));
		assertThat("genericFaultHandlerRef should not been invoked" , genericFaultHandlerRef.hasBeenInvoked(),is(false));
		assertThat("erpLegacyFacadeWSRef should not been invoked" , erpLegacyFacadeWSRef.hasBeenInvoked(),is(false));
		
		assertThat("BAL has not been written for P101-ERP-ACK",
				getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);
		assertThat("BAL should not been written for P101-WAIT",
				getOtmDao().query(createBALQuery("P101-WAIT")).size() == 0);
		assertThat("BAL should not been written for P101-PAY-ACK",
				getOtmDao().query(createBALQuery("P101-PAY-ACK")).size() == 0);
		assertThat("BAL has not been written for P101-P290",
				getOtmDao().query(createBALQuery("P101-P290")).size() == 1);
		assertThat("BAL should not been written for P101-CALL-P1000",
				getOtmDao().query(createBALQuery("P101-CALL-P1000")).size() == 0);

		
	}
	
	@Test@Ignore
	public void initiateSalesOrderCreationPaymentReceivedPoGroupExceptionHandlingTest() throws Exception {
		//PoGeneration is one way service and is not possible to emulate this kind of errors
	}
	
	@Test
	public void waitForPaymentOnAlarmTest() throws Exception {
		saveFileToComposite(COMPOSITE, REVISION, "DVM/ReceivePaymentUpdateTimeout.dvm",
				readClasspathFile(RESOURCES + "ReceivePaymentUpdateTimeout.dvm"));
		
		String sendCancellationRequestToERPResponse = readClasspathFile("../servicebus/SalesOrder/SalesOrderService/SendCancellationRequestToERPResponse.xml");
		salesOrderServiceReferenceRef = new DefaultSoapMockService(SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, sendCancellationRequestToERPResponse));
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderServiceReference", salesOrderServiceReferenceRef);
		
		String requestXML = new ParameterReplacer(readClasspathFile(RESOURCES+"initiateSalesOrderCreationRequest01.xml"))
				.build();
		
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

				
		waitForInvocationOf(salesOrderServiceReferenceRef);
		
		receiveCancellationResponseFromErpToWaitingInstance();
		
		assertThat("cancellationInvestigatorProcessRef has not been invoked" , cancellationInvestigatorProcessRef.hasBeenInvoked(), is(false));
		assertThat("purchaseOrderGenerationProcessReference has not been invoked" , purchaseOrderGenerationProcessReferenceRef.hasBeenInvoked(),is(false));
		assertThat("salesOrderServiceReferenceRef should not been invoked" , salesOrderServiceReferenceRef.hasBeenInvoked(),is(true));
		assertThat("genericFaultHandlerRef should not been invoked" , genericFaultHandlerRef.hasBeenInvoked(),is(false));
		assertThat("erpLegacyFacadeWSRef should not been invoked" , erpLegacyFacadeWSRef.hasBeenInvoked(),is(false));
		
		
		assertThat("BAL has not been written for P101-ERP-ACK",
				getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-WAIT",
				getOtmDao().query(createBALQuery("P101-WAIT")).size() == 1);
		assertThat("BAL has not been written for P101-PT",
				getOtmDao().query(createBALQuery("P101-PT")).size() == 1);
		
		assertThat("BAL should not been written for P101-P290",
				getOtmDao().query(createBALQuery("P101-P290")).size() == 0);
		assertThat("BAL should not been written for P101-CALL-P1000",
				getOtmDao().query(createBALQuery("P101-CALL-P1000")).size() == 0);
		

		assertThat("OSMSO has not been written for P101-ERP-ACK",
				getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);

		assertThat("OSMSO has not been written for P101-WAIT",
				getOtmDao().query(createOSMSOQuery("P101-WAIT")).size() == 1);
		
		assertThat("OSMSO has not been written for P101-PT-ACK",
				getOtmDao().query(createOSMSOQuery("P101-PT-ACK")).size() == 1);
		
	}
	
	@Test
	public void waitForPaymentOnAlarmTestErrorResponseFromSalesOrderCancellationWithErrorHandlingTests() throws Exception {
		//Prepare DVM
		saveFileToComposite(COMPOSITE, REVISION, "DVM/ReceivePaymentUpdateTimeout.dvm",
				readClasspathFile(RESOURCES + "ReceivePaymentUpdateTimeout.dvm"));
		
		//Prepare Resend and abort mocks 
		List<MockResponsePojo> genericFaultHandlerMockResponses = Lists.newArrayList();
		//Prepare resend
		String genericFaultHandlerResend =/*SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,*/ new ParameterReplacer(
				readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/GenericFaultHandlerResend.xml"))
				.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID)
				.replace(REPLACE_PARAM_PAYLOAD, 
							new ParameterReplacer(
									readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
								.build()
							).build()/*)*/;
		genericFaultHandlerMockResponses.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerResend));
		//Prepare Abort
		String genericFaultHandlerAbort =/*SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,*/ new ParameterReplacer(
				readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/GenericFaultHandlerAbort.xml"))
				.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID)
				.replace(REPLACE_PARAM_PAYLOAD, 
							new ParameterReplacer(
									readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
									.build()
							).build()/*)*/;
		genericFaultHandlerMockResponses.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerAbort));
		
		
		genericFaultHandlerRef =  new DefaultSoapMockService(genericFaultHandlerMockResponses);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);
		
		//End Prepare Resend and abort mocks 
		
		String sendCancellationRequestToERPResponse = readClasspathFile("../servicebus/SalesOrder/SalesOrderService/SendCancellationRequestToERPFaultResponse.xml");
		salesOrderServiceReferenceRef = new DefaultSoapMockService(SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, sendCancellationRequestToERPResponse));
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderServiceReference", salesOrderServiceReferenceRef);
		
		String requestXML = new ParameterReplacer(readClasspathFile(RESOURCES+"initiateSalesOrderCreationRequest01.xml"))
				.build();
		
		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));
		
		waitForInvocationOf(salesOrderServiceReferenceRef);
		onErrorSalesOrderCancellationRequestFromErpToWaitingInstance();
		waitForInvocationOf(genericFaultHandlerRef);
		waitForInvocationOf(salesOrderServiceReferenceRef); // This is by purpose because retry case invoke cancellation again
		onErrorSalesOrderCancellationRequestFromErpToWaitingInstance();
		waitForInvocationOf(genericFaultHandlerRef);
		
		assertThat("cancellationInvestigatorProcessRef should not been invoked" , cancellationInvestigatorProcessRef.hasBeenInvoked(), is(false));
		assertThat("purchaseOrderGenerationProcessReference should not been invoked" , purchaseOrderGenerationProcessReferenceRef.hasBeenInvoked(),is(false));
		assertThat("salesOrderServiceReferenceRef has not been invoked 2x" , salesOrderServiceReferenceRef.getNumberOfInvocations(),is(2));
		assertThat("genericFaultHandlerRef has not been invoked 2x" , genericFaultHandlerRef.getNumberOfInvocations(),is(2));
		assertThat("erpLegacyFacadeWSRef should not been invoked" , erpLegacyFacadeWSRef.hasBeenInvoked(),is(false));
		
		//assert request for second invocation to be sure that it is reused.
		assertXmlEquals(
				new ParameterReplacer(readClasspathFile(
						"../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
								.build(),
				evaluateXpath("//sosm:sendCancellationRequestToERPRequest",
						salesOrderServiceReferenceRef.getReceivedRequests().get(0)));
		assertXmlEquals(
				new ParameterReplacer(
						readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
					.build(),
				evaluateXpath("//sosm:sendCancellationRequestToERPRequest",
					salesOrderServiceReferenceRef.getReceivedRequests().get(1)));
		
		assertThat("BAL has not been written for P101-ERP-ACK",
				getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-WAIT",
				getOtmDao().query(createBALQuery("P101-WAIT")).size() == 1);
		assertThat("BAL has not been written for P101-PT 2x",
				getOtmDao().query(createBALQuery("P101-PT")).size() == 2);
		
		assertThat("BAL should not been written for P101-P290",
				getOtmDao().query(createBALQuery("P101-P290")).size() == 0);
		assertThat("BAL should not been written for P101-CALL-P1000",
				getOtmDao().query(createBALQuery("P101-CALL-P1000")).size() == 0);

		assertThat("OSMSO has not been written for P101-ERP-ACK",
				getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);

		assertThat("OSMSO has not been written for P101-WAIT",
				getOtmDao().query(createOSMSOQuery("P101-WAIT")).size() == 1);
		
		assertThat("OSMSO has not been written for P101-PT 2x",
				getOtmDao().query(createOSMSOQuery("P101-PT")).size() == 2);
				
		assertThat("OSMSO should not been written for P101-PT-ACK",
				getOtmDao().query(createOSMSOQuery("P101-PT-ACK")).size() == 0);
		
	}
	
	@Test
	public void waitForPaymentOnalarmTestSendCancellationRequestToERPTechnicalFault() throws Exception {
		// Prepare DVM
		saveFileToComposite(COMPOSITE, REVISION, "DVM/ReceivePaymentUpdateTimeout.dvm",
				readClasspathFile(RESOURCES + "ReceivePaymentUpdateTimeout.dvm"));

		// Prepare Resend and abort mocks
		//Prepare CancellationRequestToERP error responses
		List<MockResponsePojo> salesOrderServiceMockResponses = Lists.newArrayList();

		MockResponsePojo mockResponsePojo = new MockResponsePojo(ResponseType.FAULT,"SomeTechnicalIssue");

		salesOrderServiceMockResponses.add(mockResponsePojo);
		salesOrderServiceMockResponses.add(mockResponsePojo);

		salesOrderServiceReferenceRef = new DefaultSoapMockService(salesOrderServiceMockResponses);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderServiceReference", salesOrderServiceReferenceRef);
		
		List<MockResponsePojo> genericFaultHandlerMockResponses = Lists.newArrayList();
		// Prepare resend
		String genericFaultHandlerResend = /*
											 * SoapUtil.getInstance().
											 * soapEnvelope(SoapVersion.SOAP11,
											 */ new ParameterReplacer(readClasspathFile(
				"../processes/GenericProcesses/SalesOrderCreationProcess/GenericFaultHandlerResend.xml"))
						.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID)
						.replace(REPLACE_PARAM_PAYLOAD,
								new ParameterReplacer(readClasspathFile(
										"../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
												.build())
						.build()/* ) */;
		genericFaultHandlerMockResponses
				.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerResend));
		// Prepare Abort
		String genericFaultHandlerAbort = /*
											 * SoapUtil.getInstance().soapEnvelope
											 * (SoapVersion.SOAP11,
											 */ new ParameterReplacer(readClasspathFile(
				"../processes/GenericProcesses/SalesOrderCreationProcess/GenericFaultHandlerAbort.xml"))
						.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID)
						.replace(REPLACE_PARAM_PAYLOAD,
								new ParameterReplacer(readClasspathFile(
										"../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
												.build())
						.build()/* ) */;
		genericFaultHandlerMockResponses
				.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerAbort));

		genericFaultHandlerRef = new DefaultSoapMockService(genericFaultHandlerMockResponses);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);

		// End Prepare Resend and abort mocks

		String requestXML = new ParameterReplacer(
				readClasspathFile(RESOURCES + "initiateSalesOrderCreationRequest01.xml")).build();

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(salesOrderServiceReferenceRef);
		waitForInvocationOf(genericFaultHandlerRef,2);

		assertThat("cancellationInvestigatorProcessRef should not been invoked",
				cancellationInvestigatorProcessRef.hasBeenInvoked(), is(false));
		assertThat("purchaseOrderGenerationProcessReference should not been invoked",
				purchaseOrderGenerationProcessReferenceRef.hasBeenInvoked(), is(false));
		assertThat("salesOrderServiceReferenceRef has not been invoked 2x",
				salesOrderServiceReferenceRef.getNumberOfInvocations(), is(2));
		assertThat("genericFaultHandlerRef has not been invoked 2x", genericFaultHandlerRef.getNumberOfInvocations(),
				is(2));
		assertThat("erpLegacyFacadeWSRef should not been invoked", erpLegacyFacadeWSRef.hasBeenInvoked(), is(false));

		// assert request for second invocation to be sure that it is reused.
		assertXmlEquals(
				new ParameterReplacer(readClasspathFile(
						"../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
								.build(),
				evaluateXpath("//sosm:sendCancellationRequestToERPRequest",
						salesOrderServiceReferenceRef.getReceivedRequests().get(0)));
		assertXmlEquals(
				new ParameterReplacer(readClasspathFile(
						"../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
								.build(),
				evaluateXpath("//sosm:sendCancellationRequestToERPRequest",
						salesOrderServiceReferenceRef.getReceivedRequests().get(1)));

		assertThat("BAL has not been written for P101-ERP-ACK", getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-WAIT", getOtmDao().query(createBALQuery("P101-WAIT")).size() == 1);
		assertThat("BAL should not be written for P101-PT", getOtmDao().query(createBALQuery("P101-PT")).size() == 0);

		assertThat("BAL should not been written for P101-P290",
				getOtmDao().query(createBALQuery("P101-P290")).size() == 0);
		assertThat("BAL should not been written for P101-CALL-P1000",
				getOtmDao().query(createBALQuery("P101-CALL-P1000")).size() == 0);

		assertThat("OSMSO has not been written for P101-ERP-ACK", getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);

		assertThat("OSMSO has not been written for P101-WAIT", getOtmDao().query(createOSMSOQuery("P101-WAIT")).size() == 1);

		assertThat("OSMSO should not be written for P101-PT", getOtmDao().query(createOSMSOQuery("P101-PT")).size() == 0);

		assertThat("OSMSO should not been written for P101-PT-ACK",
				getOtmDao().query(createOSMSOQuery("P101-PT-ACK")).size() == 0);
	}
	
	
	@Test
	public void waitForPaymentOnalarmTestSendCancellationRequestToERPBusinessFault() throws Exception {
		// Prepare DVM
		saveFileToComposite(COMPOSITE, REVISION, "DVM/ReceivePaymentUpdateTimeout.dvm",
				readClasspathFile(RESOURCES + "ReceivePaymentUpdateTimeout.dvm"));

		// Prepare Resend and abort mocks
		//Prepare CancellationRequestToERP error responses
		List<MockResponsePojo> salesOrderServiceMockResponses = Lists.newArrayList();

	
		
		String businessFault =new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/BusinessFault.xml"))
				.replace( REPLACE_PARAM_PAYLOAD, 
						new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
									.build())
				.build();
			
		MockResponsePojo mockResponsePojo = new MockResponsePojo(ResponseType.BUSINESS_FAULT,
				businessFault);

		salesOrderServiceMockResponses.add(mockResponsePojo);
		salesOrderServiceMockResponses.add(mockResponsePojo);

		salesOrderServiceReferenceRef = new DefaultSoapMockService(salesOrderServiceMockResponses);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderServiceReference", salesOrderServiceReferenceRef);
		
		List<MockResponsePojo> genericFaultHandlerMockResponses = Lists.newArrayList();
		// Prepare resend
		String genericFaultHandlerResend = /*
											 * SoapUtil.getInstance().
											 * soapEnvelope(SoapVersion.SOAP11,
											 */ new ParameterReplacer(readClasspathFile(
				"../processes/GenericProcesses/SalesOrderCreationProcess/GenericFaultHandlerResend.xml"))
						.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID)
						.replace(REPLACE_PARAM_PAYLOAD,
								new ParameterReplacer(readClasspathFile(
										"../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
												.build())
						.build()/* ) */;
		genericFaultHandlerMockResponses
				.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerResend));
		// Prepare Abort
		String genericFaultHandlerAbort = /*
											 * SoapUtil.getInstance().soapEnvelope
											 * (SoapVersion.SOAP11,
											 */ new ParameterReplacer(readClasspathFile(
				"../processes/GenericProcesses/SalesOrderCreationProcess/GenericFaultHandlerAbort.xml"))
						.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID)
						.replace(REPLACE_PARAM_PAYLOAD,
								new ParameterReplacer(readClasspathFile(
										"../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
												.build())
						.build()/* ) */;
		genericFaultHandlerMockResponses
				.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerAbort));

		genericFaultHandlerRef = new DefaultSoapMockService(genericFaultHandlerMockResponses);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);

		// End Prepare Resend and abort mocks

		String requestXML = new ParameterReplacer(
				readClasspathFile(RESOURCES + "initiateSalesOrderCreationRequest01.xml")).build();

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(salesOrderServiceReferenceRef);
		waitForInvocationOf(genericFaultHandlerRef,2);

		assertThat("cancellationInvestigatorProcessRef should not been invoked",
				cancellationInvestigatorProcessRef.hasBeenInvoked(), is(false));
		assertThat("purchaseOrderGenerationProcessReference should not been invoked",
				purchaseOrderGenerationProcessReferenceRef.hasBeenInvoked(), is(false));
		assertThat("salesOrderServiceReferenceRef has not been invoked 2x",
				salesOrderServiceReferenceRef.getNumberOfInvocations(), is(2));
		assertThat("genericFaultHandlerRef has not been invoked 2x", genericFaultHandlerRef.getNumberOfInvocations(),
				is(2));
		assertThat("erpLegacyFacadeWSRef should not been invoked", erpLegacyFacadeWSRef.hasBeenInvoked(), is(false));

		// assert request for second invocation to be sure that it is reused.
		assertXmlEquals(
				new ParameterReplacer(readClasspathFile(
						"../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
								.build(),
				evaluateXpath("//sosm:sendCancellationRequestToERPRequest",
						salesOrderServiceReferenceRef.getReceivedRequests().get(0)));
		assertXmlEquals(
				new ParameterReplacer(readClasspathFile(
						"../processes/GenericProcesses/SalesOrderCreationProcess/SendCancellationRequestToERPRequest.xml"))
								.build(),
				evaluateXpath("//sosm:sendCancellationRequestToERPRequest",
						salesOrderServiceReferenceRef.getReceivedRequests().get(1)));

		assertThat("BAL has not been written for P101-ERP-ACK", getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);
		assertThat("BAL has not been written for P101-WAIT", getOtmDao().query(createBALQuery("P101-WAIT")).size() == 1);
		assertThat("BAL should not be written for P101-PT", getOtmDao().query(createBALQuery("P101-PT")).size() == 0);

		assertThat("BAL should not been written for P101-P290",
				getOtmDao().query(createBALQuery("P101-P290")).size() == 0);
		assertThat("BAL should not been written for P101-CALL-P1000",
				getOtmDao().query(createBALQuery("P101-CALL-P1000")).size() == 0);

		assertThat("OSMSO has not been written for P101-ERP-ACK", getOtmDao().query(createBALQuery("P101-ERP-ACK")).size() == 1);

		assertThat("OSMSO has not been written for P101-WAIT", getOtmDao().query(createOSMSOQuery("P101-WAIT")).size() == 1);

		assertThat("OSMSO should not be written for P101-PT", getOtmDao().query(createOSMSOQuery("P101-PT")).size() == 0);

		assertThat("OSMSO should not been written for P101-PT-ACK",
				getOtmDao().query(createOSMSOQuery("P101-PT-ACK")).size() == 0);
	}
	
	
	
	
	
	void sendReceivePaymentNotificationRequestToWaitingInstance() {
		try (
				CloseableHttpClient httpClient = HttpClientBuilder.create().build()) 
		{
			 HttpPost request = new HttpPost(
					 	"http://localhost:7101/soa-infra/services/generic/SalesOrderCreationProcess/SalesOrderCreationDelegator_ep");
				    request.addHeader("Content-Type", "text/xml; charset=utf-8");
				    request.addHeader("Accept", "text/xml");
				    final String receivePurchaseOrderCreatedRequestSoapRequest = SoapUtil.getInstance()
					    .soapEnvelope(SoapVersion.SOAP11,
						    new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/receivePaymentNotificationRequest01.xml"))
								    .build());
				    request.setEntity(new StringEntity(receivePurchaseOrderCreatedRequestSoapRequest,
					    ContentType.TEXT_XML.withCharset(StandardCharsets.UTF_8)));
				    httpClient.execute(request);
				    
				   // new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/receivePaymentNotificationRequest01.xml"))

		} catch (Exception e) {

			throw new RuntimeException(e);
		}
	}
	
	void sendReceiveCancellationRequestToWaitingInstance() {
		try (
				CloseableHttpClient httpClient = HttpClientBuilder.create().build()) 
		{
			 HttpPost request = new HttpPost(
					 	"http://localhost:7101/soa-infra/services/generic/SalesOrderCreationProcess/SalesOrderCreationDelegator_ep");
				    request.addHeader("Content-Type", "text/xml; charset=utf-8");
				    request.addHeader("Accept", "text/xml");
				    final String receivePurchaseOrderCreatedRequestSoapRequest = SoapUtil.getInstance()
					    .soapEnvelope(SoapVersion.SOAP11,
						    new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/receiveCancellationRequest01.xml"))
								    .build());
				    request.setEntity(new StringEntity(receivePurchaseOrderCreatedRequestSoapRequest,
					    ContentType.TEXT_XML.withCharset(StandardCharsets.UTF_8)));
				    httpClient.execute(request);
				    
				   // new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/receivePaymentNotificationRequest01.xml"))

		} catch (Exception e) {

			throw new RuntimeException(e);
		}
	}
	
	void receiveCancellationResponseFromErpToWaitingInstance() {
		try (
				CloseableHttpClient httpClient = HttpClientBuilder.create().build()) 
		{
			 HttpPost request = new HttpPost(
					 	"http://localhost:7101/soa-infra/services/generic/SalesOrderCreationProcess/SalesOrderCreationDelegator_ep");
				    request.addHeader("Content-Type", "text/xml; charset=utf-8");
				    request.addHeader("Accept", "text/xml");
				    final String receivePurchaseOrderCreatedRequestSoapRequest = SoapUtil.getInstance()
					    .soapEnvelope(SoapVersion.SOAP11,
						    new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/ReceiveCancellationResponseFromErp.xml"))
								    .build());
				    request.setEntity(new StringEntity(receivePurchaseOrderCreatedRequestSoapRequest,
					    ContentType.TEXT_XML.withCharset(StandardCharsets.UTF_8)));
				    httpClient.execute(request);
				    
				   // new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/receivePaymentNotificationRequest01.xml"))

		} catch (Exception e) {

			throw new RuntimeException(e);
		}
	}
	
	

	void onErrorSalesOrderCancellationRequestFromErpToWaitingInstance() {
		try (
				CloseableHttpClient httpClient = HttpClientBuilder.create().build()) 
		{
			 HttpPost request = new HttpPost(
					 	"http://localhost:7101/soa-infra/services/generic/SalesOrderCreationProcess/SalesOrderCreationErrorListener");
				    request.addHeader("Content-Type", "text/xml; charset=utf-8");
				    request.addHeader("Accept", "text/xml");
				    final String receivePurchaseOrderCreatedRequestSoapRequest = SoapUtil.getInstance()
					    .soapEnvelope(SoapVersion.SOAP11,
						    new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/onErrorSalesOrderCancellationRequest01.xml"))
								    .replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID)
								    .replace(REPLACE_PARAM_PAYLOAD, 
								    		 new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/ReceiveCancellationResponseFromErp.xml"))
											    .build()
											    )
								    .build());
				    request.setEntity(new StringEntity(receivePurchaseOrderCreatedRequestSoapRequest,
					    ContentType.TEXT_XML.withCharset(StandardCharsets.UTF_8)));
				    httpClient.execute(request);
				    
				   // new ParameterReplacer(readClasspathFile("../processes/GenericProcesses/SalesOrderCreationProcess/receivePaymentNotificationRequest01.xml"))

		} catch (Exception e) {

			throw new RuntimeException(e);
		}
	}


	private BaseQuery<BalActivities> createBALQuery(String activityCode) {
		return new BaseQuery<>(SqlOp.SELECT,
				new QueryPredicate("correlation_id", CORRELATION_ID).withEquals("activity_code", activityCode),
				BalActivities.class);
	}
	
	private BaseQuery<OsmSo> createOSMSOQuery(String activityCode) {
		return new BaseQuery<>(SqlOp.SELECT,
				new QueryPredicate("correlation_id", CORRELATION_ID).withEquals("status_code", activityCode),
				OsmSo.class);
	}

	private BaseQuery<BalActivities> createDeleteBALQuery() {
		return new BaseQuery<>(SqlOp.DELETE, new QueryPredicate("correlation_id", CORRELATION_ID), BalActivities.class);
	}

	private BaseQuery<OsmSo> createDeleteOSMSOQuery() {
		return new BaseQuery<>(SqlOp.DELETE, new QueryPredicate("correlation_id", CORRELATION_ID), OsmSo.class);
	}

}
