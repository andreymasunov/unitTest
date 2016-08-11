package de.home24.middleware.purchaseordergeneration;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 * Tests for PurchaseOrderGenerationProcess for sparepart orders
 * 
 * @author svb
 *
 */
public class InitiateDropshipOrderSparepartTest extends BasePurchaseOrderGenerationProcess {

    private final static String REPLACE_PARAM_NOTE_PURCHASING_CODE = "NOTE_PURCHASING_ORDER";

    @Before
    public void setUp() {

	salesOrderId = String.format("SP%s", String.valueOf(System.currentTimeMillis()));
	initiateDropshipOrderRequest = SoapUtil.getInstance()
		.soapEnvelope(SoapVersion.SOAP11,
			new ParameterReplacer(readClasspathFile(String.format(
				"%s/SparepartInitiateDropshipOrderRequest.xml", PATH_TO_RESOURCES_PROCESS)))
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

    @Test
    public void whenSparepartPurchaseOrderIsCreatedSuccessfullyAndNoCancellationHasOccuredThenPurchaseOrderGroupHandlingIsStarted() {

	invokeCompositeService(MOCK_COMPOSITE, MOCK_COMPOSITE_REVISION, "PurchaseOrderGenerationDelegator_ep",
		initiateDropshipOrderRequest);

	waitForInvocationOf(purchaseOrderServiceRef, 1, 5);
	sendReceivePurchaseOrderCreatedRequestToWaitingInstance(
		"SparepartReceivePurchaseOrderCreatedRequest.xml");
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
	final String purchaseOrderGroupRequest = evaluateXpath("//ns1:processPurchaseOrderGroupRequest",
		purchaseOrderGroupHandlingProcessRef.getLastReceivedRequest());
	assertXmlEquals(
		new ParameterReplacer(readClasspathFile(
			String.format("%s/SparepartExpectedProcessPurchaseOrderGroupRequest.xml",
				PATH_TO_RESOURCES_PROCESS)))
					.replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
					.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
					.replace(REPLACE_PARAM_NOTE_PURCHASING_CODE, "GER-7").build(),
		purchaseOrderGroupRequest);

	LOGGER.fine(String.format("############## %s ", purchaseOrderGroupRequest));

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
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue, 1);

	propertyToExpectedValue.clear();
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_CODE, Lists.newArrayList("P1000-PO-ACK"));
	propertyToExpectedValue.put(COMPARATION_KEY_STATUS_TEXT, Lists.newArrayList("POs created"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERID, Lists.newArrayList(PURCHASE_ORDER_ID));
	propertyToExpectedValue.put(COMPARATION_KEY_ERP_ITEM_ID, Lists.newArrayList("10000"));
	propertyToExpectedValue.put(COMPARATION_KEY_PURCHASEORDERITEMID, Lists.newArrayList("10000"));
	checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(propertyToExpectedValue, 1);
    }

}
