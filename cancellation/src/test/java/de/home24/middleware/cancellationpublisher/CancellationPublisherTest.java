package de.home24.middleware.cancellationpublisher;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.util.List;

import org.joda.time.DateTime;
import org.joda.time.format.ISODateTimeFormat;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.MockService;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.cancellationpublisher.GenericFaultHandlerMock.FaultStrategy;
import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.BaseQuery;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Tests for CancellationPublisherProcess (P291).
 * 
 * @author svb
 *
 */
public class CancellationPublisherTest extends AbstractBaseSoaTest {

    private static final String PATH_TO_RESOURCES_SB_SALES_ORDER = "../servicebus/SalesOrder/SalesOrderService";
    private static final String PATH_TO_RESOURCES_SB_ORDER_TRANSACTION = "../servicebus/OrderTransaction/OrderTransactionService";
    private static final String PATH_TO_RESOURCES_SB_COMMON = "../servicebus/Common";
    private static final String PATH_TO_RESOURCES_PROCESS_CANCELLATION_PUBLISHER = "../processes/GenericProcesses/CancellationPublisherProcess";

    private static final String REPLACE_PARAM_SALES_ORDER_NO = "SALES_ORDER_NO";
    private static final String REPLACE_PARAM_CANCELLATION_SUB_STATUS = "CANCELLATION_SUB_STATUS";
    private static final String REPLACE_PARAM_REPLACE_VALUE = "REPLACE_VALUE";

    private static final String CANCELLATION_SUB_STATUS_FULL_SO = "SO";
    private static final String CANCELLATION_SUB_STATUS_ALL_DS = "AllDS";
    private static final String CANCELLATION_STATUS_CANCELLED = "Cancelled";
    private static final String CANCELLATION_STATUS_NOT_CANCELLED = "Not Cancelled";
    private static final String SALES_ORDER_STATUS_WAIT_FOR_PO_CREATION = "P1000-WAIT";
    private static final String SALES_ORDER_STATUS_WAIT_FOR_PAYMENT = "P101-WAIT";
    private static final String SALES_ORDER_STATUS_CANCELLATION_INVESTIGATOR_WAITING = "P290-WAIT";

    private static final String ACTIVITY_ID_P291_GET_ORDER_STAT = "P291-GET-ORDER-STAT";
    private static final String ACTIVITY_ID_P291_GET_CSTAT = "P291-GET-CSTAT";
    private static final String ACTIVITY_ID_P291_GET_CSUB = "P291-GET-CSUB";
    private static final String ACTIVITY_ID_P291_CALL_P101 = "P291-CALL-P101";
    private static final String ACTIVITY_ID_P291_CALL_P1000 = "P291-CALL-P1000";
    private static final String ACTIVITY_ID_P291_CALL_P290 = "P291-CALL-P290";

    private static final String COMPOSITE_NAME = "CancellationPublisherProcess";
    private static final String COMPOSITE_REVISION = "1.3.0.0";

    private String salesOrderNo;
    private String processCancellationPublisherRequest;

    private DefaultSoapMockService orderTransactionServiceRef;
    private DefaultSoapMockService salesOrderServiceRef;
    private DefaultSoapMockService purchaseOrderGenerationProcessRef;
    private DefaultSoapMockService salesOrderCreationProcessRef;
    private DefaultSoapMockService cancellationInvestigatorProcessRef;

    public CancellationPublisherTest() {
	super("generic");
    }

    @Before
    public void setUp() {

	salesOrderNo = String.valueOf(System.currentTimeMillis());

	processCancellationPublisherRequest = new ParameterReplacer(
		readClasspathFile(String.format("%s/ProcessCancellationPublisherRequest.xml",
			PATH_TO_RESOURCES_PROCESS_CANCELLATION_PUBLISHER)))
				.replace(REPLACE_PARAM_SALES_ORDER_NO, salesOrderNo).build();

	purchaseOrderGenerationProcessRef = new DefaultSoapMockService();
	salesOrderCreationProcessRef = new DefaultSoapMockService();
	cancellationInvestigatorProcessRef = new DefaultSoapMockService();
    }

    @Test
    public void whenPoGenerationHasBeenInitiatedAndFullSoHasBeenCancelledThenCancelPoGenerationProcess() {

	mockOrderTransactionService(SALES_ORDER_STATUS_WAIT_FOR_PO_CREATION, CANCELLATION_STATUS_CANCELLED);
	mockSalesOrderService(CANCELLATION_SUB_STATUS_FULL_SO);
	simulateWaitingPoGenerationScenario(true);
    }

    @Test
    public void whenPoGenerationHasBeenInitiatedAndAllDsLinesHasBeenCancelledThenCancelPoGenerationProcess() {

	mockOrderTransactionService(SALES_ORDER_STATUS_WAIT_FOR_PO_CREATION, CANCELLATION_STATUS_CANCELLED);
	mockSalesOrderService(CANCELLATION_SUB_STATUS_ALL_DS);
	simulateWaitingPoGenerationScenario(true);
    }

    @Test
    public void whenPoGenerationHasBeenInitiatedAndoCancellationIsOngoingThenDoNothing() {

	mockOrderTransactionService(SALES_ORDER_STATUS_WAIT_FOR_PO_CREATION,
		CANCELLATION_STATUS_NOT_CANCELLED);
	mockSalesOrderService(CANCELLATION_SUB_STATUS_ALL_DS);
	simulateWaitingPoGenerationScenario(false);
    }

    void simulateWaitingPoGenerationScenario(boolean pIsCancellationExpected) {

	mockCompositeReference(COMPOSITE_NAME, COMPOSITE_REVISION, "PurchaseOrderGenerationProcess",
		purchaseOrderGenerationProcessRef);

	invokeCompositeService(COMPOSITE_NAME, COMPOSITE_REVISION, "CancellationPublisherProcess_ep",
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processCancellationPublisherRequest));

	waitForInvocationOf(purchaseOrderGenerationProcessRef);

	assertServiceInvocation(orderTransactionServiceRef, 2);
	assertServiceInvocation(salesOrderServiceRef, 1);
	assertServiceInvocation(purchaseOrderGenerationProcessRef, pIsCancellationExpected ? 1 : 0);

	validateCommonBalActivityEntries();

	if (pIsCancellationExpected) {
	    validateBalEntry(ACTIVITY_ID_P291_CALL_P1000, "Inform P1000");
	}
    }

    @Test
    public void whenSoIsWaitingForPaymentAndFullSoHasBeenCancelledThenCancelSoCreationProcess() {

	mockOrderTransactionService(SALES_ORDER_STATUS_WAIT_FOR_PAYMENT, CANCELLATION_STATUS_CANCELLED);
	mockSalesOrderService(CANCELLATION_SUB_STATUS_FULL_SO);
	simulateWaitingSoScenario(true);
    }

    @Test
    public void whenSoIsWaitingForPaymentAndAllDsLinesHasBeenCancelledThenCancelSoCreationProcess() {

	mockOrderTransactionService(SALES_ORDER_STATUS_WAIT_FOR_PAYMENT, CANCELLATION_STATUS_CANCELLED);
	mockSalesOrderService(CANCELLATION_SUB_STATUS_ALL_DS);
	simulateWaitingSoScenario(true);
    }

    @Test
    public void whenSoIsWaitingForPaymentAndNoCancellationIsOngoingThenDoNothing() {

	mockOrderTransactionService(SALES_ORDER_STATUS_WAIT_FOR_PAYMENT, CANCELLATION_STATUS_NOT_CANCELLED);
	mockSalesOrderService("");
	simulateWaitingSoScenario(false);
    }

    void simulateWaitingSoScenario(boolean pIsCancellationExpected) {
	mockCompositeReference(COMPOSITE_NAME, COMPOSITE_REVISION, "SalesOrderCreationProcess",
		salesOrderCreationProcessRef);

	invokeCompositeService(COMPOSITE_NAME, COMPOSITE_REVISION, "CancellationPublisherProcess_ep",
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processCancellationPublisherRequest));

	waitForInvocationOf(salesOrderCreationProcessRef);

	assertServiceInvocation(orderTransactionServiceRef, 2);
	assertServiceInvocation(salesOrderServiceRef, 1);
	assertServiceInvocation(salesOrderCreationProcessRef, pIsCancellationExpected ? 1 : 0);

	validateCommonBalActivityEntries();

	if (pIsCancellationExpected) {
	    validateBalEntry(ACTIVITY_ID_P291_CALL_P101, "Inform P101");
	}
    }

    @Test
    public void whenCancellationInvestigatorIsWaitingThenCallCancellationInvestigatorProcess() {

	mockOrderTransactionService(SALES_ORDER_STATUS_CANCELLATION_INVESTIGATOR_WAITING,
		CANCELLATION_STATUS_CANCELLED);
	mockSalesOrderService(CANCELLATION_SUB_STATUS_FULL_SO);
	mockCompositeReference(COMPOSITE_NAME, COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);

	invokeCompositeService(COMPOSITE_NAME, COMPOSITE_REVISION, "CancellationPublisherProcess_ep",
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processCancellationPublisherRequest));

	waitForInvocationOf(cancellationInvestigatorProcessRef);

	assertServiceInvocation(orderTransactionServiceRef, 2);
	assertServiceInvocation(salesOrderServiceRef, 1);
	assertServiceInvocation(cancellationInvestigatorProcessRef, 1);

	validateCommonBalActivityEntries();
	validateBalEntry(ACTIVITY_ID_P291_CALL_P290, "Inform P290");
    }

    @Test
    public void whenExpectedExceptionsWhileCallingTheServicesThenRetryTheFailedServiceCallsAndSuccessfulCloseTheProject() {

	final String purchaseOrderServciceExceptionMessage = createExceptionResponse("MW-10101",
		"Technical fault while converting the PO to CSV");
	final String orderTransactionServiceExceptionMessage = createExceptionResponse("MW-11000",
		"Technical fault while accessing OrderTransaction database");

	orderTransactionServiceRef = new DefaultSoapMockService(Lists.newArrayList(
		new MockResponsePojo(ResponseType.BUSINESS_FAULT, orderTransactionServiceExceptionMessage),
		new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			createGetReferenceValueResponse(
				SALES_ORDER_STATUS_CANCELLATION_INVESTIGATOR_WAITING)),
		new MockResponsePojo(ResponseType.BUSINESS_FAULT, orderTransactionServiceExceptionMessage),
		new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			createGetReferenceValueResponse(CANCELLATION_STATUS_CANCELLED))));

	salesOrderServiceRef = new DefaultSoapMockService(Lists.newArrayList(
		new MockResponsePojo(ResponseType.BUSINESS_FAULT, purchaseOrderServciceExceptionMessage),
		new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			createGetCancellationSubStatusResponse(CANCELLATION_SUB_STATUS_ALL_DS))));

	simulateErrorScenario();
    }

    @Test
    public void whenUnexpectedExceptionsWhileCallingTheServicesThenRetryTheFailedServiceCallsAndSuccessfulCloseTheProject() {

	orderTransactionServiceRef = new DefaultSoapMockService(
		Lists.newArrayList(new MockResponsePojo(ResponseType.FAULT, "Unexpected error"),
			new MockResponsePojo(ResponseType.SOAP_RESPONSE,
				createGetReferenceValueResponse(
					SALES_ORDER_STATUS_CANCELLATION_INVESTIGATOR_WAITING)),
			new MockResponsePojo(ResponseType.FAULT, "Unexpected error"),
			new MockResponsePojo(ResponseType.SOAP_RESPONSE,
				createGetReferenceValueResponse(CANCELLATION_STATUS_CANCELLED))));

	salesOrderServiceRef = new DefaultSoapMockService(
		Lists.newArrayList(new MockResponsePojo(ResponseType.FAULT, "Unexpected error"),
			new MockResponsePojo(ResponseType.SOAP_RESPONSE,
				createGetCancellationSubStatusResponse(CANCELLATION_SUB_STATUS_ALL_DS))));

	simulateErrorScenario();
    }

    /**
     * Simulate the steps needed for the error test cases including
     * GenericFaultHandler.
     */
    private void simulateErrorScenario() {

	final GenericFaultHandlerMock genericFaultHandlerMock = new GenericFaultHandlerMock(
		FaultStrategy.RESEND);

	mockCompositeReference(COMPOSITE_NAME, COMPOSITE_REVISION, "OrderTransactionService",
		orderTransactionServiceRef);
	mockCompositeReference(COMPOSITE_NAME, COMPOSITE_REVISION, "SalesOrderService", salesOrderServiceRef);
	mockCompositeReference(COMPOSITE_NAME, COMPOSITE_REVISION, "CancellationInvestigatorProcess",
		cancellationInvestigatorProcessRef);
	mockCompositeReference(COMPOSITE_NAME, COMPOSITE_REVISION, "GenericFaultHandler",
		genericFaultHandlerMock);

	invokeCompositeService(COMPOSITE_NAME, COMPOSITE_REVISION, "CancellationPublisherProcess_ep",
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, processCancellationPublisherRequest));

	waitForInvocationOf(cancellationInvestigatorProcessRef);

	assertServiceInvocation(orderTransactionServiceRef, 4);
	assertServiceInvocation(salesOrderServiceRef, 2);
	assertServiceInvocation(genericFaultHandlerMock, 3);
	assertServiceInvocation(cancellationInvestigatorProcessRef, 1);

	validateCommonBalActivityEntries();
	validateBalEntry(ACTIVITY_ID_P291_CALL_P290, "Inform P290");
    }

    /**
     * Creates an exception response message with the specified faultCode and
     * faultMessage.
     * 
     * @param pFaultCode
     *            the fault code to be included in the exception
     * @param pFaultMessage
     *            the fault message to be included in the exception
     * @return the exception message
     */
    String createExceptionResponse(String pFaultCode, String pFaultMessage) {

	return new ParameterReplacer(readClasspathFile(
		String.format("%s/ExceptionResponseTemplate.xml", PATH_TO_RESOURCES_SB_COMMON)))
			.replace("SOURCE_SYSTEM_NAME", "Middleware").replace("TRANSACTION_ID", salesOrderNo)
			.replace("ACTVITY_ID", "").replace("PAYLOAD", "").replace("SEVERITY", "ERROR")
			.replace("CATEGORY", "TechnicalFault").replace("FAULT_CODE", pFaultCode)
			.replace("FAULT_MESSAGE", pFaultMessage).replace("FAULT_DETAILS", "")
			.replace("FAULT_TIMESTAMP",
				ISODateTimeFormat.basicDateTime().withOffsetParsed().print(DateTime.now()))
			.replace("FAULT_USERAREA", "").build();
    }

    /**
     * Creates a GenericFaultHandlerResponse message containing the specified
     * parameters.
     * 
     * @param pFaultStrategy
     *            the faultStrategy to be returned
     * @param pPayload
     *            the payload to be returned from the GenericFaultHandlerService
     * @param pActivityId
     *            the activityId
     * @return
     */
    String createGenericFaultHandlerResponse(String pFaultStrategy, String pPayload, String pActivityId) {

	return null;
    }

    /**
     * Validates that the common activities has been executed and logged to OTM.
     */
    void validateCommonBalActivityEntries() {
	validateBalEntry(ACTIVITY_ID_P291_GET_ORDER_STAT, "getSalesOrderStatus");
	validateBalEntry(ACTIVITY_ID_P291_GET_CSTAT, "getCancellationStatus");
	validateBalEntry(ACTIVITY_ID_P291_GET_CSUB, "Get Cancellation SubStatus");
    }

    /**
     * Validation for a {@link BalActivities} entry.
     * 
     * @param pActivityId
     *            the expected activityId
     * @param pActivityText
     *            the expected activityText
     */
    void validateBalEntry(String pActivityId, String pActivityText) {

	List<BalActivities> balActivity = getOtmDao()
		.query(BaseQuery.createBALQuery(salesOrderNo, pActivityId));

	assertThat(String.format("More than one entry found for ActivityId %s and salesOrderNo %s",
		pActivityId, salesOrderNo), balActivity, hasSize(1));
	assertThat(String.format("ActivityText for ActivityId %s and salesOrderNo %s not correct",
		pActivityId, salesOrderNo), balActivity.get(0).getActivityText(), equalTo(pActivityText));
    }

    /**
     * Assert if a specific {@link MockService} has been invoked or not.
     * 
     * @param pDefaultSoapMockService
     *            the {@link MockService} implementation
     * @param pNumberOfInvocations
     *            number of expected invocations
     */
    void assertServiceInvocation(AbstractSoapMockService pDefaultSoapMockService, int pNumberOfInvocations) {

	boolean shouldBeInvoked = pNumberOfInvocations > 0;

	assertThat("ServiceRef has not been invoked!", pDefaultSoapMockService.hasBeenInvoked(),
		is(shouldBeInvoked));
	assertThat(String.format("ServiceRef has not been invoked for %s times!", pNumberOfInvocations),
		pDefaultSoapMockService.getNumberOfInvocations(), equalTo(pNumberOfInvocations));
    }

    /**
     * Mock the SalesOrderService call and return specified
     * SubCancellationStatus.
     * 
     * @param pCancellationSubStatus
     *            the SubCancellationStatus to be returned
     */
    void mockSalesOrderService(String pCancellationSubStatus) {

	final String getCancellationSubStatusResponse = createGetCancellationSubStatusResponse(
		pCancellationSubStatus);
	salesOrderServiceRef = new DefaultSoapMockService(getCancellationSubStatusResponse);

	mockCompositeReference(COMPOSITE_NAME, COMPOSITE_REVISION, "SalesOrderService", salesOrderServiceRef);
    }

    /**
     * Returns the getCancellationSubStatusResponse containing a respective
     * value.
     * 
     * @param pCancellationSubStatus
     *            the SubCancellationStatus to be returned
     * @return the getCancellationSubStatusResponse message
     */
    String createGetCancellationSubStatusResponse(String pCancellationSubStatus) {

	return new ParameterReplacer(readClasspathFile(
		String.format("%s/getCancellationSubStatus/GetCancellationSubStatusResponse.xml",
			PATH_TO_RESOURCES_SB_SALES_ORDER)))
				.replace(REPLACE_PARAM_CANCELLATION_SUB_STATUS, pCancellationSubStatus)
				.replace(REPLACE_PARAM_SALES_ORDER_NO, salesOrderNo).build();
    }

    /**
     * Mock the OrderTransactionService call and return the specified
     * SalesOrderStatus and the specified CancellationStatus.
     * 
     * @param pSalesOrderStatus
     *            the SalesOrderStatus to be returned
     * @param pCancellationStatus
     *            the CancellationStatus to be returned
     */
    void mockOrderTransactionService(String pSalesOrderStatus, String pCancellationStatus) {

	final MockResponsePojo getSalesOrderStatusResponse = new MockResponsePojo(ResponseType.SOAP_RESPONSE,
		createGetReferenceValueResponse(pSalesOrderStatus));
	final MockResponsePojo getCancellationStatusResponse = new MockResponsePojo(
		ResponseType.SOAP_RESPONSE, createGetReferenceValueResponse(pCancellationStatus));

	orderTransactionServiceRef = new DefaultSoapMockService(
		Lists.newArrayList(getSalesOrderStatusResponse, getCancellationStatusResponse));

	mockCompositeReference(COMPOSITE_NAME, COMPOSITE_REVISION, "OrderTransactionService",
		orderTransactionServiceRef);
    }

    /**
     * Returns a getReferenceValueResponse containing the specified value
     * 
     * @param pGetReferenceReturnValue
     *            the value to be returned by getReferenceValueCall
     * @return the getReferenceValueResponse message
     */
    String createGetReferenceValueResponse(String pGetReferenceReturnValue) {

	return new ParameterReplacer(
		readClasspathFile(String.format("%s/getReferenceValue/GetReferenceValueSimpleResponse.xml",
			PATH_TO_RESOURCES_SB_ORDER_TRANSACTION)))
				.replace(REPLACE_PARAM_REPLACE_VALUE, pGetReferenceReturnValue).build();
    }
}
