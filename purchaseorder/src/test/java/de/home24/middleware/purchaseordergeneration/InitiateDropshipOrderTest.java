package de.home24.middleware.purchaseordergeneration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Test for the PurchaseOrderGenerationProcess, operation initiateDropshipOrder
 * 
 * @author svb
 *
 */
public class InitiateDropshipOrderTest extends BasePurchaseOrderGenerationProcess {

    @Before
    public void setUp() {

	salesOrderId = String.valueOf(System.currentTimeMillis());
	initiateDropshipOrderRequest = SoapUtil.getInstance()
		.soapEnvelope(SoapVersion.SOAP11,
			new ParameterReplacer(readClasspathFile(String
				.format("%s/InitiateDropshipOrderRequest.xml", PATH_TO_RESOURCES_PROCESS)))
					.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build());

	List<MockResponsePojo> cancellationInvestigatorMockResponses = Lists
		.newArrayList(new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			new ParameterReplacer(readClasspathFile(String.format(
				"%s/CancellationInvestigatorProcessCallback.xml", PATH_TO_RESOURCES_PROCESS)))
					.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build()));

	purchaseOrderServiceRef = new DefaultSoapMockService();
	salesOrderServiceRef = new DefaultSoapMockService();
	cancellationInvestigatorProcessRef = new DefaultSoapMockService(
		cancellationInvestigatorMockResponses);
	genericFaultHandlerServiceRef = new DefaultSoapMockService();
	purchaseOrderGroupHandlingProcessRef = new DefaultSoapMockService();

	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CreatePurchaseOrderService",
		purchaseOrderServiceRef);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION,
		"PurchaseOrderGroupHandlingProcessReference", purchaseOrderGroupHandlingProcessRef);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "GenericFaultHandler",
		genericFaultHandlerServiceRef);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "SalesOrderService",
		salesOrderServiceRef);
    }

    @After
    public void tearDown() {
    }

    @Test
    public void whenPurchaseOrderIsCreatedSuccessfullyAndNoCancellationHasOccuredThenPurchaseOrderGroupHandlingIsStarted() {

	LOGGER.info(String.format("InitiateDropshipRequest: %s", initiateDropshipOrderRequest));

	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 1, 5);
	sendReceivePurchaseOrderCreatedRequestToWaitingInstance("ReceivePurchaseOrderCreatedRequest.xml");
	waitForInvocationOf(purchaseOrderGroupHandlingProcessRef, 1, 20);

	assertThat("PurchaseOrderServiceRef has not been invoked", purchaseOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("PurchaseOrderGroupHandlingProcessRef has not been invoked",
		purchaseOrderGroupHandlingProcessRef.hasBeenInvoked(), is(true));
	assertThat("CancellationInvestigatorProcessRef has not been invoked",
		cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
	assertThat("SalesOrderServiceRef has not been invoked", salesOrderServiceRef.hasBeenInvoked(),
		is(false));
	assertThat("GenericFaultHandlerServiceRef has not been invoked",
		genericFaultHandlerServiceRef.hasBeenInvoked(), is(false));

	declareXpathNS("ns1",
		"http://home24.de/interfaces/bps/processpurchaseordergroup/purchaseordergrouphandlingprocessmessages/v1");
	assertXmlEquals(
		new ParameterReplacer(readClasspathFile(String.format(
			"%s/ExpectedProcessPurchaseOrderGroupRequest.xml", PATH_TO_RESOURCES_PROCESS)))
				.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
				.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID).build(),
		evaluateXpath("//ns1:processPurchaseOrderGroupRequest",
			purchaseOrderGroupHandlingProcessRef.getLastReceivedRequest()));

	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-INIT", "Create PurchaseOrder", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-WAIT", "Update OTM [P1000-Wait]", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-ACK", "Receive Generated PO Data",
		"P1000", false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-P290",
		"Subprocess: Cancellation Investigator", "P1000", false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-CALL-P1001",
		"P1001: PurchaseOrderProcess", "P1000", false);

	Map<String, List<String>> propertyToExpectedValue = new HashMap<>();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-INIT"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("PO creation requested"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);

	propertyToExpectedValue.clear();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-ACK"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("POs created"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERID, Lists.newArrayList(PURCHASE_ORDER_ID));
	propertyToExpectedValue.put(COMPARATION_KEY_ERP_ITEM_ID, Lists.newArrayList("10000", "20000"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERITEMID,
		Lists.newArrayList("10000", "20000"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
    }

    /**
     * Cancelled SO case. Does not do anything else.
     */
    @Test
    public void whenPurchaseOrderIsCreatedSuccessfullyAndCancelledSO() {

	LOGGER.info(String.format("InitiateDropshipRequest: %s", initiateDropshipOrderRequest));
	List<MockResponsePojo> cancellationInvestigatorMockResponses = Lists
		.newArrayList(
			new MockResponsePojo(ResponseType.SOAP_RESPONSE,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/CancellationInvestigatorProcessCallbackCancelledSO.xml",
					PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
						.build()));

	cancellationInvestigatorProcessRef = new DefaultSoapMockService(
		cancellationInvestigatorMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);

	System.out.println("+++ test " + cancellationInvestigatorMockResponses.get(0).getResponse());

	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 1, 5);
	sendReceivePurchaseOrderCreatedRequestToWaitingInstance("ReceivePurchaseOrderCreatedRequest.xml");
	waitForInvocationOf(cancellationInvestigatorProcessRef, 1, 20);

	assertThat("PurchaseOrderServiceRef has not been invoked", purchaseOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("PurchaseOrderGroupHandlingProcessRef should not bee invoked",
		purchaseOrderGroupHandlingProcessRef.hasBeenInvoked(), is(false));
	assertThat("CancellationInvestigatorProcessRef has not been invoked",
		cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
	assertThat("SalesOrderServiceRef should not bee invoked", salesOrderServiceRef.hasBeenInvoked(),
		is(false));
	assertThat("GenericFaultHandlerServiceRef should not bee invoked",
		genericFaultHandlerServiceRef.hasBeenInvoked(), is(false));

	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-INIT", "Create PurchaseOrder", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-WAIT", "Update OTM [P1000-Wait]", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-ACK", "Receive Generated PO Data",
		"P1000", false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-P290",
		"Subprocess: Cancellation Investigator", "P1000", false);

	Map<String, List<String>> propertyToExpectedValue = new HashMap<>();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-INIT"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("PO creation requested"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);

	propertyToExpectedValue.clear();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-ACK"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("POs created"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERID, Lists.newArrayList(PURCHASE_ORDER_ID));
	propertyToExpectedValue.put(COMPARATION_KEY_ERP_ITEM_ID, Lists.newArrayList("10000", "20000"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERITEMID,
		Lists.newArrayList("10000", "20000"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
    }

    /**
     * Cancelled All DS case. Does not do anything else.
     */
    @Test
    public void whenPurchaseOrderIsCreatedSuccessfullyAndCancelledAllDS() {
	LOGGER.info(String.format("InitiateDropshipRequest: %s", initiateDropshipOrderRequest));

	List<MockResponsePojo> cancellationInvestigatorMockResponses = Lists
		.newArrayList(
			new MockResponsePojo(ResponseType.SOAP_RESPONSE,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/CancellationInvestigatorProcessCallbackCancelledAllDS.xml",
					PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
						.build()));

	cancellationInvestigatorProcessRef = new DefaultSoapMockService(
		cancellationInvestigatorMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);

	System.out.println("+++ test " + cancellationInvestigatorMockResponses.get(0).getResponse());

	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 1, 5);
	sendReceivePurchaseOrderCreatedRequestToWaitingInstance("ReceivePurchaseOrderCreatedRequest.xml");
	waitForInvocationOf(cancellationInvestigatorProcessRef, 1, 20);

	assertThat("PurchaseOrderServiceRef has not been invoked", purchaseOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("PurchaseOrderGroupHandlingProcessRef should not bee invoked",
		purchaseOrderGroupHandlingProcessRef.hasBeenInvoked(), is(false));
	assertThat("CancellationInvestigatorProcessRef has not been invoked",
		cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
	assertThat("SalesOrderServiceRef should not bee invoked", salesOrderServiceRef.hasBeenInvoked(),
		is(false));
	assertThat("GenericFaultHandlerServiceRef should not bee invoked",
		genericFaultHandlerServiceRef.hasBeenInvoked(), is(false));

	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-INIT", "Create PurchaseOrder", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-WAIT", "Update OTM [P1000-Wait]", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-ACK", "Receive Generated PO Data",
		"P1000", false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-P290",
		"Subprocess: Cancellation Investigator", "P1000", false);

	Map<String, List<String>> propertyToExpectedValue = new HashMap<>();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-INIT"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("PO creation requested"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);

	propertyToExpectedValue.clear();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-ACK"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("POs created"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERID, Lists.newArrayList(PURCHASE_ORDER_ID));
	propertyToExpectedValue.put(COMPARATION_KEY_ERP_ITEM_ID, Lists.newArrayList("10000", "20000"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERITEMID,
		Lists.newArrayList("10000", "20000"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
    }

    /**
     * Test partialy cancelled case with Purchase order canceled. This PO will
     * not create request for POgroup handling. PoGroup handling will not be
     * invoked.
     */
    @Test
    public void whenPurchaseOrderIsCreatedSuccessfullyPartiallyCancelledCasePOCancelled() {
	// Prepare partially cancelled cancellation investigator response
	List<MockResponsePojo> cancellationInvestigatorMockResponses = Lists
		.newArrayList(new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			new ParameterReplacer(readClasspathFile(String.format(
				"%s/CancellationInvestigatorProcessCallbackCancelledPartially.xml",
				PATH_TO_RESOURCES_PROCESS)))
					.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build()));

	cancellationInvestigatorProcessRef = new DefaultSoapMockService(
		cancellationInvestigatorMockResponses);

	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);

	// Prepare Sales OrderService
	String salesOrderServiceGetPOCancellationStatusMockResponses = new ParameterReplacer(
		readClasspathFile(String.format("%s/GetPOCancellationStatusResponseCanceledPO.xml",
			PATH_TO_RESOURCES_PROCESS))).replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
				.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID).build();

	salesOrderServiceRef = new DefaultSoapMockService(
		salesOrderServiceGetPOCancellationStatusMockResponses);

	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "SalesOrderService",
		salesOrderServiceRef);

	// Start process
	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 1, 5);
	sendReceivePurchaseOrderCreatedRequestToWaitingInstance("ReceivePurchaseOrderCreatedRequest.xml");
	;
	waitForInvocationOf(salesOrderServiceRef, 1, 30);

	assertThat("PurchaseOrderServiceRef has not been invoked", purchaseOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("PurchaseOrderGroupHandlingProcessRef should not been invoked",
		purchaseOrderGroupHandlingProcessRef.hasBeenInvoked(), is(false));
	assertThat("CancellationInvestigatorProcessRef has not been invoked",
		cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
	assertThat("SalesOrderServiceRef has not been invoked", salesOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("GenericFaultHandlerServiceRef has not been invoked",
		genericFaultHandlerServiceRef.hasBeenInvoked(), is(false));

	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-INIT", "Create PurchaseOrder", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-WAIT", "Update OTM [P1000-Wait]", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-ACK", "Receive Generated PO Data",
		"P1000", false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-P290",
		"Subprocess: Cancellation Investigator", "P1000", false);

	Map<String, List<String>> propertyToExpectedValue = new HashMap<>();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-INIT"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("PO creation requested"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
	propertyToExpectedValue.clear();

	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-ACK"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("POs created"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERID, Lists.newArrayList(PURCHASE_ORDER_ID));
	propertyToExpectedValue.put(COMPARATION_KEY_ERP_ITEM_ID, Lists.newArrayList("10000", "20000"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERITEMID,
		Lists.newArrayList("10000", "20000"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
    }

    /**
     * Test partially cancelled case with Purchase order with one line cancelled
     * and one not. Group handling request will contain PO and only not
     * cancelled poLine
     */
    @Test
    public void whenPurchaseOrderIsCreatedSuccessfullyPartiallyCancelledCasePartiallyCancelledPO() {
	// Prepare partially cancelled cancellation investigator response
	List<MockResponsePojo> cancellationInvestigatorMockResponses = Lists
		.newArrayList(new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			new ParameterReplacer(readClasspathFile(String.format(
				"%s/CancellationInvestigatorProcessCallbackCancelledPartially.xml",
				PATH_TO_RESOURCES_PROCESS)))
					.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build()));

	cancellationInvestigatorProcessRef = new DefaultSoapMockService(
		cancellationInvestigatorMockResponses);

	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);

	// Prepare Sales OrderService
	String salesOrderServiceGetPOCancellationStatusMockResponses = new ParameterReplacer(
		readClasspathFile(String.format("%s/GetPOCancellationStatusResponsePartiallyCanceledPO.xml",
			PATH_TO_RESOURCES_PROCESS))).replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
				.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID).build();

	salesOrderServiceRef = new DefaultSoapMockService(
		salesOrderServiceGetPOCancellationStatusMockResponses);

	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "SalesOrderService",
		salesOrderServiceRef);

	// Start process
	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 1, 5);
	sendReceivePurchaseOrderCreatedRequestToWaitingInstance("ReceivePurchaseOrderCreatedRequest.xml");
	;
	waitForInvocationOf(purchaseOrderGroupHandlingProcessRef, 1, 30);

	assertThat("PurchaseOrderServiceRef has not been invoked", purchaseOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("PurchaseOrderGroupHandlingProcessRef has not been invoked",
		purchaseOrderGroupHandlingProcessRef.hasBeenInvoked(), is(true));
	assertThat("CancellationInvestigatorProcessRef has not been invoked",
		cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
	assertThat("SalesOrderServiceRef has not been invoked", salesOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("GenericFaultHandlerServiceRef has not been invoked",
		genericFaultHandlerServiceRef.hasBeenInvoked(), is(false));

	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-INIT", "Create PurchaseOrder", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-WAIT", "Update OTM [P1000-Wait]", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-ACK", "Receive Generated PO Data",
		"P1000", false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-P290",
		"Subprocess: Cancellation Investigator", "P1000", false);
	checkIfActivityCodeWasWrittenToBalActivities("P1000-CALL-P1001", "P1001: PurchaseOrderProcess",
		"P1000", 1, false);

	declareXpathNS("ns1",
		"http://home24.de/interfaces/bps/processpurchaseordergroup/purchaseordergrouphandlingprocessmessages/v1");
	assertXmlEquals(
		new ParameterReplacer(readClasspathFile(String.format(
			"%s/ExpectedProcessPurchaseOrderGroupRequesPartialyCancelledPartialPO.xml",
			PATH_TO_RESOURCES_PROCESS))).replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
				.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID).build(),
		evaluateXpath("//ns1:processPurchaseOrderGroupRequest",
			purchaseOrderGroupHandlingProcessRef.getReceivedRequests().get(0)));

	Map<String, List<String>> propertyToExpectedValue = new HashMap<>();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-INIT"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("PO creation requested"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
	propertyToExpectedValue.clear();

	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-ACK"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("POs created"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERID, Lists.newArrayList(PURCHASE_ORDER_ID));
	propertyToExpectedValue.put(COMPARATION_KEY_ERP_ITEM_ID, Lists.newArrayList("10000", "20000"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERITEMID,
		Lists.newArrayList("10000", "20000"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
    }

    /**
     * This impossible case but is created to test transformation which prepare
     * po group handling request.
     */
    @Test
    public void whenPurchaseOrderIsCreatedSuccessfullyCancelledPartiallyButWithoutPOItemCancelled() {
	// Prepare partially cancelled cancellation investigator response
	List<MockResponsePojo> cancellationInvestigatorMockResponses = Lists
		.newArrayList(new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			new ParameterReplacer(readClasspathFile(String.format(
				"%s/CancellationInvestigatorProcessCallbackCancelledPartially.xml",
				PATH_TO_RESOURCES_PROCESS)))
					.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build()));

	cancellationInvestigatorProcessRef = new DefaultSoapMockService(
		cancellationInvestigatorMockResponses);

	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);

	// Prepare Sales OrderService
	String salesOrderServiceGetPOCancellationStatusMockResponses = new ParameterReplacer(
		readClasspathFile(
			String.format("%s/GetPOCancellationStatusResponse.xml", PATH_TO_RESOURCES_PROCESS)))
				.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
				.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID).build();

	salesOrderServiceRef = new DefaultSoapMockService(
		salesOrderServiceGetPOCancellationStatusMockResponses);

	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "SalesOrderService",
		salesOrderServiceRef);

	// Start process
	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 1, 5);
	sendReceivePurchaseOrderCreatedRequestToWaitingInstance("ReceivePurchaseOrderCreatedRequest.xml");
	waitForInvocationOf(purchaseOrderGroupHandlingProcessRef, 1, 30);

	assertThat("PurchaseOrderServiceRef has not been invoked", purchaseOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("PurchaseOrderGroupHandlingProcessRef has not been invoked",
		purchaseOrderGroupHandlingProcessRef.hasBeenInvoked(), is(true));
	assertThat("CancellationInvestigatorProcessRef has not been invoked",
		cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
	assertThat("SalesOrderServiceRef has not been invoked", salesOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("GenericFaultHandlerServiceRef has not been invoked",
		genericFaultHandlerServiceRef.hasBeenInvoked(), is(false));

	declareXpathNS("ns1",
		"http://home24.de/interfaces/bps/processpurchaseordergroup/purchaseordergrouphandlingprocessmessages/v1");
	assertXmlEquals(
		new ParameterReplacer(readClasspathFile(String.format(
			"%s/ExpectedProcessPurchaseOrderGroupRequesPartialyCancelledCompletePO.xml",
			PATH_TO_RESOURCES_PROCESS))).replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
				.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID).build(),
		evaluateXpath("//ns1:processPurchaseOrderGroupRequest",
			purchaseOrderGroupHandlingProcessRef.getReceivedRequests().get(0)));

	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-INIT", "Create PurchaseOrder", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-WAIT", "Update OTM [P1000-Wait]", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-ACK", "Receive Generated PO Data",
		"P1000", false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-P290",
		"Subprocess: Cancellation Investigator", "P1000", false);
	checkIfActivityCodeWasWrittenToBalActivities("P1000-CALL-P1001", "P1001: PurchaseOrderProcess",
		"P1000", 1, false);

	Map<String, List<String>> propertyToExpectedValue = new HashMap<>();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-INIT"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("PO creation requested"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);

	propertyToExpectedValue.clear();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-ACK"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("POs created"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERID, Lists.newArrayList(PURCHASE_ORDER_ID));
	propertyToExpectedValue.put(COMPARATION_KEY_ERP_ITEM_ID, Lists.newArrayList("10000", "20000"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERITEMID,
		Lists.newArrayList("10000", "20000"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
    }

    /**
     * 
     * This test check receive cancellation request. Business fault is added
     * before we added separated tests for exception handling
     */
    @Test
    public void createPurchaseOrderServiceCatchScopeBusinessFaultAndReceiveCancellation() {
	LOGGER.info(String.format("InitiateDropshipRequest: %s", initiateDropshipOrderRequest));

	// Prepare purchaseOrderServiceRef business fault and normal response
	// for second tyme
	List<MockResponsePojo> purchaseOrderServiceMockResponses = Lists.newArrayList();

	MockResponsePojo mockResponsePojo = new MockResponsePojo(ResponseType.BUSINESS_FAULT,
		new ParameterReplacer(readClasspathFile(
			String.format("%s/BusinessFault.xml", PATH_TO_RESOURCES_PROCESS))).replace(
				REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/InitiateDropshipOrderRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
				.build());

	purchaseOrderServiceMockResponses.add(mockResponsePojo);
	purchaseOrderServiceMockResponses.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, SoapUtil
		.getInstance()
		.soapEnvelope(SoapVersion.SOAP11, new ParameterReplacer(readClasspathFile(
			String.format("%s/CreatePurchaseOrderInErpResponse.xml", PATH_TO_RESOURCES_PROCESS)))
				.build())));

	purchaseOrderServiceRef = new DefaultSoapMockService(purchaseOrderServiceMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CreatePurchaseOrderService",
		purchaseOrderServiceRef);

	// Prepare Mock for genericFaultHandler
	String genericFaultHandlerResend = /*
					    * SoapUtil.getInstance().
					    * soapEnvelope(SoapVersion.SOAP11,
					    */ new ParameterReplacer(readClasspathFile(
		String.format("%s/GenericFaultHandlerResend.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/CreatePurchaseOrderInErpRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build()/* ) */;
	System.out.println("+++Resend " + genericFaultHandlerResend);
	genericFaultHandlerServiceRef = new DefaultSoapMockService(genericFaultHandlerResend);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "GenericFaultHandler",
		genericFaultHandlerServiceRef);

	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 2, 10);

	try {
	    Thread.sleep(2000);
	} catch (InterruptedException e) {
	    e.printStackTrace();
	}
	sendReceiveCancellationRequestToWaitingInstance();

	assertThat("PurchaseOrderServiceRef has not been invoked", purchaseOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("PurchaseOrderGroupHandlingProcessRef should not been invoked",
		purchaseOrderGroupHandlingProcessRef.hasBeenInvoked(), is(false));
	assertThat("CancellationInvestigatorProcessRef should not been invoked",
		cancellationInvestigatorProcessRef.hasBeenInvoked(), is(false));
	assertThat("SalesOrderServiceRef should not been invoked", salesOrderServiceRef.hasBeenInvoked(),
		is(false));
	assertThat("GenericFaultHandlerServiceRef should not been invoked",
		genericFaultHandlerServiceRef.hasBeenInvoked(), is(true));

	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-INIT", "Create PurchaseOrder", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-WAIT", "Update OTM [P1000-Wait]", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-CANCEL", "Receive Complete Cancellation",
		"P1000", false);

	// checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-P290",
	// "Subprocess: Cancellation Investigator",
	// "P1000", false);
	// checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-CALL-P1001",
	// "P1001: PurchaseOrderProcess",
	// "P1000", false);

	// xxxCheck for items is it necessary
	Map<String, List<String>> propertyToExpectedValue = new HashMap<>();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-INIT"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("PO creation requested"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);

	propertyToExpectedValue.clear();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-CANCEL"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT,
		Lists.newArrayList("Receive Complete Cancellation"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
    }

    @Test
    public void createPurchaseOrderServiceCatchScopeBusinessFault() {
	LOGGER.info(String.format("InitiateDropshipRequest: %s", initiateDropshipOrderRequest));

	// Prepare purchaseOrderServiceRef business fault and normal response
	// for second tyme
	List<MockResponsePojo> purchaseOrderServiceMockResponses = Lists.newArrayList();

	String businessFault = new ParameterReplacer(
		readClasspathFile(String.format("%s/BusinessFault.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/InitiateDropshipOrderRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build();

	MockResponsePojo mockResponsePojo = new MockResponsePojo(ResponseType.BUSINESS_FAULT, businessFault);

	purchaseOrderServiceMockResponses.add(mockResponsePojo);
	purchaseOrderServiceMockResponses.add(mockResponsePojo);

	purchaseOrderServiceRef = new DefaultSoapMockService(purchaseOrderServiceMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CreatePurchaseOrderService",
		purchaseOrderServiceRef);

	// Prepare Mock for genericFaultHandler
	List<MockResponsePojo> genericFaultHandlerMockResponses = Lists.newArrayList();
	// Prepare resend
	String genericFaultHandlerResend = /*
					    * SoapUtil.getInstance().
					    * soapEnvelope(SoapVersion.SOAP11,
					    */ new ParameterReplacer(readClasspathFile(
		String.format("%s/GenericFaultHandlerResend.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/CreatePurchaseOrderInErpRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build()/* ) */;
	genericFaultHandlerMockResponses
		.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerResend));
	// Prepare Abort
	String genericFaultHandlerAbort = /*
					   * SoapUtil.getInstance().soapEnvelope
					   * (SoapVersion.SOAP11,
					   */ new ParameterReplacer(readClasspathFile(
		String.format("%s/GenericFaultHandlerAbort.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/CreatePurchaseOrderInErpRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build()/* ) */;
	genericFaultHandlerMockResponses
		.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerAbort));

	System.out.println("+++Resend " + genericFaultHandlerResend);
	genericFaultHandlerServiceRef = new DefaultSoapMockService(genericFaultHandlerMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "GenericFaultHandler",
		genericFaultHandlerServiceRef);

	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 2, 10);

	// PurchaseOrder should be invoked 2 times
	assertThat("PurchaseOrderServiceRef should be invoked 2 times ",
		purchaseOrderServiceRef.getNumberOfInvocations() == 2);
	assertThat("GenericFaultHandlerServiceRef should be invoked 2 times ",
		genericFaultHandlerServiceRef.getNumberOfInvocations() == 2);

	// This is created on complete invocation but in this test should not be
	// executed.
	assertThat("Bal records should not be created for P1000-PO-INIT",
		getBalActivitiesCount("P1000-PO-INIT", "Create PurchaseOrder", "P1000", false) == 0);
    }

    @Test
    public void createPurchaseOrderServiceCatchScopeOtherFaults() {
	LOGGER.info(String.format("InitiateDropshipRequest: %s", initiateDropshipOrderRequest));

	// Prepare purchaseOrderServiceRef business fault and normal response
	// for second tyme
	List<MockResponsePojo> purchaseOrderServiceMockResponses = Lists.newArrayList();

	MockResponsePojo mockResponsePojo = new MockResponsePojo(ResponseType.FAULT, "SomeTechnicalIssue");

	purchaseOrderServiceMockResponses.add(mockResponsePojo);
	purchaseOrderServiceMockResponses.add(mockResponsePojo);

	purchaseOrderServiceRef = new DefaultSoapMockService(purchaseOrderServiceMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CreatePurchaseOrderService",
		purchaseOrderServiceRef);

	// Prepare Mock for genericFaultHandler
	List<MockResponsePojo> genericFaultHandlerMockResponses = Lists.newArrayList();
	// Prepare resend
	String genericFaultHandlerResend = /*
					    * SoapUtil.getInstance().
					    * soapEnvelope(SoapVersion.SOAP11,
					    */ new ParameterReplacer(readClasspathFile(
		String.format("%s/GenericFaultHandlerResend.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/CreatePurchaseOrderInErpRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build()/* ) */;
	genericFaultHandlerMockResponses
		.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerResend));
	// Prepare Abort
	String genericFaultHandlerAbort = /*
					   * SoapUtil.getInstance().soapEnvelope
					   * (SoapVersion.SOAP11,
					   */ new ParameterReplacer(readClasspathFile(
		String.format("%s/GenericFaultHandlerAbort.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/CreatePurchaseOrderInErpRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build()/* ) */;
	genericFaultHandlerMockResponses
		.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerAbort));

	System.out.println("+++Resend " + genericFaultHandlerResend);
	genericFaultHandlerServiceRef = new DefaultSoapMockService(genericFaultHandlerMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "GenericFaultHandler",
		genericFaultHandlerServiceRef);

	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 2, 10);

	// PurchaseOrder should be invoked 2 times
	assertThat("PurchaseOrderServiceRef should be invoked 2 times ",
		purchaseOrderServiceRef.getNumberOfInvocations() == 2);
	assertThat("GenericFaultHandlerServiceRef should be invoked 2 times ",
		genericFaultHandlerServiceRef.getNumberOfInvocations() == 2);

	// This is created on complete invocation but in this test should not be
	// executed.
	assertThat("Bal records should not be created for P1000-PO-INIT",
		getBalActivitiesCount("P1000-PO-INIT", "Create PurchaseOrder", "P1000", false) == 0);
    }

    @Test
    public void CancellationInvestigatorCallbackException() {

	MockResponsePojo mockResponsePojo = new MockResponsePojo(ResponseType.SOAP_RESPONSE,
		new ParameterReplacer(readClasspathFile(
			String.format("%s/BusinessFault.xml", PATH_TO_RESOURCES_PROCESS))).replace(
				REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/CancellationInvestigatorRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
				.build());

	List<MockResponsePojo> cancellationInvestigatorMockResponses = Lists.newArrayList();
	cancellationInvestigatorMockResponses.add(mockResponsePojo);

	cancellationInvestigatorProcessRef = new DefaultSoapMockService(
		cancellationInvestigatorMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);

	LOGGER.info(String.format("InitiateDropshipRequest: %s", initiateDropshipOrderRequest));

	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 1, 5);
	sendReceivePurchaseOrderCreatedRequestToWaitingInstance("ReceivePurchaseOrderCreatedRequest.xml");
	waitForInvocationOf(cancellationInvestigatorProcessRef, 1, 5);

	assertThat("PurchaseOrderGroupHandlingProcessRef should not be invoked",
		purchaseOrderGroupHandlingProcessRef.getNumberOfInvocations() == 0);
	assertThat("PurchaseOrderServiceRef has not been invoked", purchaseOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("PurchaseOrderGroupHandlingProcessRef should not been invoked",
		purchaseOrderGroupHandlingProcessRef.hasBeenInvoked(), is(false));
	assertThat("CancellationInvestigatorProcessRef has not been invoked",
		cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
	assertThat("SalesOrderServiceRef has not been invoked", salesOrderServiceRef.hasBeenInvoked(),
		is(false));
	assertThat("GenericFaultHandlerServiceRef should not been invoked",
		genericFaultHandlerServiceRef.hasBeenInvoked(), is(false));

	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-INIT", "Create PurchaseOrder", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-WAIT", "Update OTM [P1000-Wait]", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-ACK", "Receive Generated PO Data",
		"P1000", false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-P290",
		"Subprocess: Cancellation Investigator", "P1000", false);

	Map<String, List<String>> propertyToExpectedValue = new HashMap<>();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-INIT"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("PO creation requested"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);

	propertyToExpectedValue.clear();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-ACK"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("POs created"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERID, Lists.newArrayList(PURCHASE_ORDER_ID));
	propertyToExpectedValue.put(COMPARATION_KEY_ERP_ITEM_ID, Lists.newArrayList("10000", "20000"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERITEMID,
		Lists.newArrayList("10000", "20000"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
    }

    @Test
    public void POCancellationStatusBusinesFaultCatchTest() {
	// Prepare partially cancelled cancellation investigator response
	List<MockResponsePojo> cancellationInvestigatorMockResponses = Lists
		.newArrayList(new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			new ParameterReplacer(readClasspathFile(String.format(
				"%s/CancellationInvestigatorProcessCallbackCancelledPartially.xml",
				PATH_TO_RESOURCES_PROCESS)))
					.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build()));

	cancellationInvestigatorProcessRef = new DefaultSoapMockService(
		cancellationInvestigatorMockResponses);

	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);

	// Prepare Sales OrderService
	List<MockResponsePojo> salesOrderServiceMockResponses = Lists.newArrayList();
	String businessFault = new ParameterReplacer(
		readClasspathFile(String.format("%s/BusinessFault.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/GetPOCancellationStatusRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build();

	MockResponsePojo mockResponsePojo = new MockResponsePojo(ResponseType.BUSINESS_FAULT, businessFault);

	salesOrderServiceMockResponses.add(mockResponsePojo);
	salesOrderServiceMockResponses.add(mockResponsePojo);

	salesOrderServiceRef = new DefaultSoapMockService(salesOrderServiceMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "SalesOrderService",
		salesOrderServiceRef);

	// Prepare GenericFault handler
	ArrayList<MockResponsePojo> genericFaultHandlerMockResponses = Lists.newArrayList();

	String genericFaultHandlerResend = new ParameterReplacer(readClasspathFile(
		String.format("%s/GenericFaultHandlerResend.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/GetPOCancellationStatusRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build();
	genericFaultHandlerMockResponses
		.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerResend));
	// Prepare Abort
	String genericFaultHandlerAbort = new ParameterReplacer(readClasspathFile(
		String.format("%s/GenericFaultHandlerAbort.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/GetPOCancellationStatusRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build();
	genericFaultHandlerMockResponses
		.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerAbort));

	genericFaultHandlerServiceRef = new DefaultSoapMockService(genericFaultHandlerMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "GenericFaultHandler",
		genericFaultHandlerServiceRef);

	LOGGER.info(String.format("InitiateDropshipRequest: %s", initiateDropshipOrderRequest));

	// Start process
	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 1, 5);
	sendReceivePurchaseOrderCreatedRequestToWaitingInstance("ReceivePurchaseOrderCreatedRequest.xml");
	waitForInvocationOf(salesOrderServiceRef, 2, 20);

	POCancellationStatusExceptionCassesAssertions();
    }

    @Test
    public void POCancellationStatusCatchAllFaultsTest() {

	// Prepare partially cancelled cancellation investigator response
	List<MockResponsePojo> cancellationInvestigatorMockResponses = Lists
		.newArrayList(new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			new ParameterReplacer(readClasspathFile(String.format(
				"%s/CancellationInvestigatorProcessCallbackCancelledPartially.xml",
				PATH_TO_RESOURCES_PROCESS)))
					.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build()));

	cancellationInvestigatorProcessRef = new DefaultSoapMockService(
		cancellationInvestigatorMockResponses);

	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);

	// Prepare Sales OrderService
	List<MockResponsePojo> salesOrderServiceMockResponses = Lists.newArrayList();

	MockResponsePojo mockResponsePojo = new MockResponsePojo(ResponseType.FAULT, "SomeTechnicalIssue");

	salesOrderServiceMockResponses.add(mockResponsePojo);
	salesOrderServiceMockResponses.add(mockResponsePojo);

	salesOrderServiceRef = new DefaultSoapMockService(salesOrderServiceMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "SalesOrderService",
		salesOrderServiceRef);

	// Prepare generic fault handler
	ArrayList<MockResponsePojo> genericFaultHandlerMockResponses = Lists.newArrayList();

	String genericFaultHandlerResend = new ParameterReplacer(readClasspathFile(
		String.format("%s/GenericFaultHandlerResend.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/GetPOCancellationStatusRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build();
	genericFaultHandlerMockResponses
		.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerResend));
	// Prepare Abort
	String genericFaultHandlerAbort = new ParameterReplacer(readClasspathFile(
		String.format("%s/GenericFaultHandlerAbort.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
			.replace(REPLACE_PARAM_PAYLOAD,
				new ParameterReplacer(readClasspathFile(String.format(
					"%s/GetPOCancellationStatusRequest.xml", PATH_TO_RESOURCES_PROCESS)))
						.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId).build())
			.build();
	genericFaultHandlerMockResponses
		.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerAbort));

	genericFaultHandlerServiceRef = new DefaultSoapMockService(genericFaultHandlerMockResponses);
	mockCompositeReference(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "GenericFaultHandler",
		genericFaultHandlerServiceRef);

	LOGGER.info(String.format("InitiateDropshipRequest: %s", initiateDropshipOrderRequest));

	// Start process
	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 1, 5);
	sendReceivePurchaseOrderCreatedRequestToWaitingInstance("ReceivePurchaseOrderCreatedRequest.xml");
	waitForInvocationOf(salesOrderServiceRef, 2, 20);

	POCancellationStatusExceptionCassesAssertions();

    }

    /**
     * Helper method to hold shared assertions for
     * POCancellationStatusExceptionCasses BusinessFault and catch all faults.
     */
    private void POCancellationStatusExceptionCassesAssertions() {
	assertThat("PurchaseOrderServiceRef has not been invoked", purchaseOrderServiceRef.hasBeenInvoked(),
		is(true));
	assertThat("PurchaseOrderGroupHandlingProcessRef should not been invoked",
		purchaseOrderGroupHandlingProcessRef.hasBeenInvoked(), is(false));
	assertThat("CancellationInvestigatorProcessRef has not been invoked",
		cancellationInvestigatorProcessRef.hasBeenInvoked(), is(true));
	assertThat("SalesOrderServiceRef has not been invoked 2x times ",
		salesOrderServiceRef.getNumberOfInvocations() == 2);
	assertThat("GenericFaultHandlerServiceRef has not been invoked 2x times",
		genericFaultHandlerServiceRef.getNumberOfInvocations() == 2);

	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-INIT", "Create PurchaseOrder", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-WAIT", "Update OTM [P1000-Wait]", "P1000",
		false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-PO-ACK", "Receive Generated PO Data",
		"P1000", false);
	checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce("P1000-P290",
		"Subprocess: Cancellation Investigator", "P1000", false);

	Map<String, List<String>> propertyToExpectedValue = new HashMap<>();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-INIT"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("PO creation requested"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);

	propertyToExpectedValue.clear();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-ACK"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("POs created"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERID, Lists.newArrayList(PURCHASE_ORDER_ID));
	propertyToExpectedValue.put(COMPARATION_KEY_ERP_ITEM_ID, Lists.newArrayList("10000", "20000"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERITEMID,
		Lists.newArrayList("10000", "20000"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue);
    }

    void sendReceiveCancellationRequestToWaitingInstance() {
	try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
	    HttpPost request = new HttpPost(
		    "http://localhost:7101/soa-infra/services/dropship/PurchaseOrderGenerationProcess/PurchaseOrderGenerationDelegator_ep");
	    request.addHeader("Content-Type", "text/xml; charset=utf-8");
	    request.addHeader("Accept", "text/xml");
	    final String onErrorPurchaseOrdercreationRequest = SoapUtil.getInstance()
		    .soapEnvelope(SoapVersion.SOAP11,
			    new ParameterReplacer(readClasspathFile(String
				    .format("%s/ReceiveCancellationRequest.xml", PATH_TO_RESOURCES_PROCESS)))
					    .replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
					    .replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
					    .build());
	    request.setEntity(new StringEntity(onErrorPurchaseOrdercreationRequest,
		    ContentType.TEXT_XML.withCharset(StandardCharsets.UTF_8)));

	    httpClient.execute(request);
	} catch (Exception e) {

	    throw new RuntimeException(e);
	}
    }

    private void checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(
	    Map<String, List<String>> pPropertyToExpectedValue) {
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(pPropertyToExpectedValue, 2);
    }
}
