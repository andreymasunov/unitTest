package de.home24.middleware;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.fail;

import java.sql.SQLException;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.OtmDao.Query;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.mock.RetryWithExceptionSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import oracle.bpel.services.workflow.task.model.IdentityTypeImpl;
import oracle.bpel.services.workflow.task.model.Task;

/**
 * Tests for the GenericFaultHandlerService.
 * 
 * @author svb
 * @author julian - extended tests - 09/02/2016
 * @author saroj - extended tests for assign groups rule - 05/08/2016
 * @author daniel - fix HT asserts and refactoring of tests  - 09/08/2016
 *
 */
public class GenericFaultHandlerServiceTest extends AbstractBaseSoaTest {

    private static final String COMPOSITE = "GenericFaultHandlerService";
	private static final String REVISION = "1.4.1.0";
    private static final String PROCESS = "GenericFaultHandlerService";
    private static final String ABORT = "Abort";
    private static final String RESEND = "Resend";
    private static final String ACTIVITY_CODE = "P101-SIISHOP-ERR";
    private static final String RESOURCES = "processes/FrameworkServices/GenericFaultHandlerService/";
    private static final String TECHFAULT = "TechnicalFault";
    private static final String BISFAULT = "BusinessFault";
    private static final String LOGFAULT = "LoggingFault";
    private static final String DATFAULT = "DataFault";
    private static final String TRIGFAULT = "TriggerFault";

    private Task hTask = null;
    private IdentityTypeImpl iti;
    private String expectedAssignee, randomCorrelationId, invocationResult, requestXml, faultCode,
	    numRetries = "";
    private AbstractSoapMockService orderTransactionServiceRef, orderTransactionTriggerRef;

    public GenericFaultHandlerServiceTest() {
	super("generic");
    }

    @Before
    public void setUp() throws Exception {
	Random randomNumber = new Random();
	randomCorrelationId = String.valueOf(randomNumber.nextInt(5000000));
	numRetries = "1";
	faultCode = "OneErrorCode";
	expectedAssignee = "FaultAssignees";

	getOtmDao().delete(createDeleteQueryForTable("bal_activities"));

	declareXpathNS("tsk", "http://xmlns.oracle.com/bpel/workflow/task");
	declareXpathNS("exp", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
	declareXpathNS("gfh", "http://home24.de/data/custom/genericfaulthandler/v1");
	declareXpathNS("gfhm",
		"http://home24.de/interfaces/bas/genericfaulthandler/genericfaulthandlerservicemessages/v1");

	saveFileToComposite(COMPOSITE, REVISION, "dvm/ActivityIdToRetryParameters.dvm",
		readClasspathFile(RESOURCES + "ActivityIdToRetryParameters.dvm"));
    }

    @After
    public void tearDown() throws Exception {
	orderTransactionServiceRef = null;
	orderTransactionTriggerRef = null;
	iti = null;
    }

    private Query<Void> createDeleteQueryForTable(final String pTablename) {
	return new Query<Void>() {

	    @Override
	    public String getQuery() {
		return String.format("delete from %s", pTablename);
	    }

	    @Override
	    public Object[] getQueryParameters() {
		return new Object[] {};
	    }

	    @Override
	    public Class<Void> getExpectedType() {
		return Void.class;
	    }
	};
    }

    @Test
    public void technicalFaultResendWhenMaxNumberOfRetiresNotReached() throws Exception {

	orderTransactionServiceRef = new DefaultSoapMockService(new ParameterReplacer(
		readClasspathFile(RESOURCES + "GetActivityRetryCounterMockResponse.xml"))
			.replace("NUM_RETRIES", numRetries).build());

	mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);


	final String requestXml = readClasspathFile(RESOURCES + "handleTechnicalFaultRequest.xml");
	invocationResult = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			new ParameterReplacer(requestXml).replace("CORRELATION_ID", randomCorrelationId)
				.replace("ACTIVITY_ID", ACTIVITY_CODE).replace("FAULT_CODE", faultCode)
				.build()));

	assertBalActivities(queryOtm(randomCorrelationId, ACTIVITY_CODE, "Y"), ACTIVITY_CODE);
	assertBalActivities(queryOtm(randomCorrelationId, String.format("%s-SOLVED", ACTIVITY_CODE), "Y"),
		String.format("%s-SOLVED", ACTIVITY_CODE));
	assertThat("OrderTransactionService Mock has been invoked",
		orderTransactionServiceRef.hasBeenInvoked(), is(Boolean.TRUE));
	assertXpathEvaluatesTo(
		"//gfhm:handleFaultResponse/gfhm:faultHandlingStrategy/gfh:transactionId/text()",
		randomCorrelationId, invocationResult);
	assertXpathEvaluatesTo(
		"//gfhm:handleFaultResponse/gfhm:faultHandlingStrategy/gfh:faultStrategy/text()", RESEND,
		invocationResult);
    }

    @Ignore("Should be updated and activated after we finish Replay epic")
    @Test
    public void technicalFaultWithHumanTaskAssignToLogisticsResend() throws Exception {

	faultCode = "2010";
	expectedAssignee = "Logistics";
	numRetries = "10";

	orderTransactionServiceRef = new DefaultSoapMockService(new ParameterReplacer(
		readClasspathFile(RESOURCES + "GetActivityRetryCounterMockResponse.xml"))
			.replace("NUM_RETRIES", numRetries).build());

	mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);

	Thread threadHT = new Thread() {
	    public void run() {
		try {
		    sleep(50000);
		    hTask = getHumanTaskDao().findByOrderId(randomCorrelationId);
		    getHumanTaskDao().update(hTask, RESEND);
		    iti = (IdentityTypeImpl) hTask.getSystemAttributes().getAssignees().get(0);
		} catch (InterruptedException e) {
		    interrupt();
		}
	    }
	};
	threadHT.start();

	requestXml = readClasspathFile(RESOURCES + "handleTechnicalFaultRequest.xml");
	invocationResult = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			new ParameterReplacer(requestXml).replace("CORRELATION_ID", randomCorrelationId)
				.replace("ACTIVITY_ID", ACTIVITY_CODE).replace("FAULT_CODE", faultCode)
				.build()));

    assertThat("Human Task group assignment meets the expectation!", iti.getId(),
	    equalTo(expectedAssignee));
    assertThat("Human Task title fault category meets the expectation!", hTask.getTitle(),
	    containsString(TECHFAULT));
    
	assertBalActivities(queryOtm(randomCorrelationId, ACTIVITY_CODE, "Y"), ACTIVITY_CODE);
	assertBalActivities(queryOtm(randomCorrelationId, String.format("%s-SOLVED", ACTIVITY_CODE), "Y"),
		String.format("%s-SOLVED", ACTIVITY_CODE));
	assertThat("OrderTransactionService Mock has been invoked",
		orderTransactionServiceRef.hasBeenInvoked(), is(Boolean.TRUE));
	assertXpathEvaluatesTo(
		"//gfhm:handleFaultResponse/gfhm:faultHandlingStrategy/gfh:transactionId/text()",
		randomCorrelationId, invocationResult);
	assertXpathEvaluatesTo(
		"//gfhm:handleFaultResponse/gfhm:faultHandlingStrategy/gfh:faultStrategy/text()", RESEND,
		invocationResult);
	
    }

    @Test
    public void technicalFaultWithHumanTaskAbort() throws Exception {
    	numRetries = "10";
    	String requestXml = readClasspathFile(RESOURCES + "handleTechnicalFaultRequest.xml");
		String faultMessage = "";
		final String taskOutcome = ABORT;
		String expectedAssignee = "FaultAssignees";
		String faultCode = "00";
		final String faultType = TECHFAULT;
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    @Test
    public void businessFaultWithHumanTaskAbort() throws Exception {
    	String requestXml = readClasspathFile(RESOURCES + "handleBusinessFaultRequest.xml");
		String faultMessage = "";
		final String taskOutcome = ABORT;
		String expectedAssignee = "FaultAssignees";
		String faultCode = "00";
		final String faultType = BISFAULT;
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    @Test
    public void dataFaultWithHumanTaskResend() throws Exception {
    	String requestXml = readClasspathFile(RESOURCES + "handleDataFaultRequest.xml");
		String faultMessage = "";
		final String taskOutcome = RESEND;
		String expectedAssignee = "FaultAssignees";
		String faultCode = "00";
		final String faultType = DATFAULT;
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    @Test
    public void dataFaultWithHumanTaskAbort() throws Exception {
    	String requestXml = readClasspathFile(RESOURCES + "handleDataFaultRequest.xml");
		String faultMessage = "";
		final String taskOutcome = ABORT;
		String expectedAssignee = "FaultAssignees";
		String faultCode = "00";
		final String faultType = DATFAULT;
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    @Test
    public void deadLetterFaultWithHumanTaskResend() throws Exception {
    	String requestXml = readClasspathFile(RESOURCES + "handleOtherFaultRequest.xml");
		String faultMessage = "";
		final String taskOutcome = RESEND;
		String expectedAssignee =  "FaultAssignees";
		String faultCode = "00";
		final String faultType = "DeadLetterFault";
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    @Test
    public void deadLetterFaultWithHumanTaskAbort() throws Exception {
    	String requestXml = readClasspathFile(RESOURCES + "handleOtherFaultRequest.xml");
		String faultMessage = "";
		final String taskOutcome = ABORT;
		String expectedAssignee = "FaultAssignees";
		String faultCode =  "00";
		final String faultType = "DeadLetterFault";
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    @Test
    public void errorListenerFaultWithHumanTaskResend() throws Exception {
    	String requestXml = readClasspathFile(RESOURCES + "handleOtherFaultRequest.xml");
		String faultMessage = "";
		final String taskOutcome = RESEND;
		String expectedAssignee = "FaultAssignees";
		String faultCode = "00";
		final String faultType = "ErrorListenerFault";
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    @Test
    public void triggerFaultWithHumanTaskResend() throws Exception {
    	String requestXml = readClasspathFile(RESOURCES + "handleOtherFaultRequest.xml");
		String faultMessage = "";
		final String taskOutcome = RESEND;
		String expectedAssignee = "FaultAssignees";
		String faultCode = "00";
		final String faultType = TRIGFAULT;
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    @Test
    public void uncategorizedFaultWithHumanTaskResend() throws Exception {
    	String requestXml = readClasspathFile(RESOURCES + "handleOtherFaultRequest.xml");
		String faultMessage = "";
		final String taskOutcome = RESEND;
		String expectedAssignee = "FaultAssignees";
		String faultCode = "00";
		final String faultType = "UncategorizedFault";
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    @Test
    public void loggingCatchFaultWithResend() throws Exception {

	orderTransactionTriggerRef = new RetryWithExceptionSoapMockService(1,
		new MockResponsePojo(ResponseType.BUSINESS_FAULT,
			readClasspathFile(RESOURCES + "updateOTMExcpResponse.xml")),
		new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			readClasspathFile(RESOURCES + "updateOTMExcpResponse.xml")));

	mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionTrigger", orderTransactionTriggerRef);

	orderTransactionServiceRef = new DefaultSoapMockService(new ParameterReplacer(
		readClasspathFile(RESOURCES + "GetActivityRetryCounterMockResponse.xml"))
			.replace("NUM_RETRIES", numRetries).build());

	mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);

	Thread threadHT = new Thread() {
	    public void run() {
		try {
		    sleep(50000);
		    hTask = getHumanTaskDao().findByOrderId(randomCorrelationId);
		    getHumanTaskDao().update(hTask, RESEND);
		    iti = (IdentityTypeImpl) hTask.getSystemAttributes().getAssignees().get(0);
		} catch (InterruptedException e) {
		    interrupt();
		}
	    }
	};
	threadHT.start();

	final String requestXml = readClasspathFile(RESOURCES + "handleTechnicalFaultRequest.xml");
	invocationResult = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			new ParameterReplacer(requestXml).replace("CORRELATION_ID", randomCorrelationId)
				.replace("ACTIVITY_ID", ACTIVITY_CODE).replace("FAULT_CODE", faultCode)
				.build()));

    assertThat("Human Task group assignment meets the expectation!", iti.getId(),
	    equalTo(expectedAssignee));
    assertThat("Human Task title fault category meets the expectation!", hTask.getTitle(),
	    containsString(LOGFAULT));
	assertThat("orderTransactionTrigger Mock has been invoked",
		orderTransactionTriggerRef.hasBeenInvoked(), is(Boolean.TRUE));
	assertThat("orderTransactionService Mock has been invoked",
		orderTransactionServiceRef.hasBeenInvoked(), is(Boolean.TRUE));
	assertXpathEvaluatesTo(
		"//gfhm:handleFaultResponse/gfhm:faultHandlingStrategy/gfh:transactionId/text()",
		randomCorrelationId, invocationResult);
	assertXpathEvaluatesTo(
		"//gfhm:handleFaultResponse/gfhm:faultHandlingStrategy/gfh:faultStrategy/text()", RESEND,
		invocationResult);
    }

    @Test
    public void loggingCatchFaultWithAbort() throws Exception {

	orderTransactionTriggerRef = new RetryWithExceptionSoapMockService(1,
		new MockResponsePojo(ResponseType.BUSINESS_FAULT,
			readClasspathFile(RESOURCES + "updateOTMFaultExcpResponse.xml")));

	mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionTrigger", orderTransactionTriggerRef);

	orderTransactionServiceRef = new DefaultSoapMockService(new ParameterReplacer(
		readClasspathFile(RESOURCES + "GetActivityRetryCounterMockResponse.xml"))
			.replace("NUM_RETRIES", numRetries).build());

	mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);

	Thread threadHT = new Thread() {
	    public void run() {
		try {
		    sleep(50000);
		    hTask = getHumanTaskDao().findByOrderId(randomCorrelationId);
		    getHumanTaskDao().update(hTask, ABORT);
		    iti = (IdentityTypeImpl) hTask.getSystemAttributes().getAssignees().get(0);
		} catch (InterruptedException e) {
		    interrupt();
		}
	    }
	};
	threadHT.start();

	try {
	    final String requestXml = readClasspathFile(RESOURCES + "handleOtherFaultRequest.xml");
	    invocationResult = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			    new ParameterReplacer(requestXml).replace("CORRELATION_ID", randomCorrelationId)
				    .replace("ACTIVITY_ID", ACTIVITY_CODE).replace("FAULT_CODE", faultCode)
				    .build()));

	} catch (IllegalStateException e) {
		assertThat("Human Task group assignment meets the expectation!", iti.getId(),
			equalTo(expectedAssignee));
		assertThat("Human Task title fault category meets the expectation!", hTask.getTitle(),
			 containsString(LOGFAULT));
	    assertThat("orderTransactionTrigger Mock has been invoked",
		    orderTransactionTriggerRef.hasBeenInvoked(), is(Boolean.TRUE));
	    assertThat("orderTransactionService Mock has not been invoked",
		    orderTransactionServiceRef.hasBeenInvoked(), is(Boolean.FALSE));
	    assertThat("Composite service result expected to be null", invocationResult, is(nullValue()));
	} catch (Exception e) {
	    fail("Unexpected exception occured!");
	}
    }

    @Test
    public void loggingCatchAllFaultWithResend() throws Exception {

	orderTransactionTriggerRef = new RetryWithExceptionSoapMockService(1,
		new MockResponsePojo(ResponseType.BUSINESS_FAULT, ""),
		new MockResponsePojo(ResponseType.SOAP_RESPONSE,
			readClasspathFile(RESOURCES + "updateOTMExcpResponse.xml")));

	mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionTrigger", orderTransactionTriggerRef);

	orderTransactionServiceRef = new DefaultSoapMockService(new ParameterReplacer(
		readClasspathFile(RESOURCES + "GetActivityRetryCounterMockResponse.xml"))
			.replace("NUM_RETRIES", numRetries).build());

	mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);

	Thread threadHT = new Thread() {
	    public void run() {
		try {
		    sleep(50000);
		    hTask = getHumanTaskDao().findByOrderId(randomCorrelationId);
		    getHumanTaskDao().update(hTask, RESEND);
		    iti = (IdentityTypeImpl) hTask.getSystemAttributes().getAssignees().get(0);
		} catch (InterruptedException e) {
		    interrupt();
		}
	    }
	};
	threadHT.start();

	final String requestXml = readClasspathFile(RESOURCES + "handleTechnicalFaultRequest.xml");
	invocationResult = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			new ParameterReplacer(requestXml).replace("CORRELATION_ID", randomCorrelationId)
				.replace("ACTIVITY_ID", ACTIVITY_CODE).replace("FAULT_CODE", faultCode)
				.build()));

    assertThat("Human Task group assignment meets the expectation!", iti.getId(),
	    equalTo(expectedAssignee));
    assertThat("Human Task title fault category meets the expectation!", hTask.getTitle(),
	    containsString(LOGFAULT));
	assertThat("orderTransactionTrigger Mock has been invoked",
		orderTransactionTriggerRef.hasBeenInvoked(), is(Boolean.TRUE));
	assertThat("orderTransactionService Mock has been invoked",
		orderTransactionServiceRef.hasBeenInvoked(), is(Boolean.TRUE));
	assertXpathEvaluatesTo(
		"//gfhm:handleFaultResponse/gfhm:faultHandlingStrategy/gfh:transactionId/text()",
		randomCorrelationId, invocationResult);
	assertXpathEvaluatesTo(
		"//gfhm:handleFaultResponse/gfhm:faultHandlingStrategy/gfh:faultStrategy/text()", RESEND,
		invocationResult);

    }

    @Test
    public void loggingCatchAllFaultWithAbort() throws Exception {

		orderTransactionTriggerRef = new RetryWithExceptionSoapMockService(1,
			new MockResponsePojo(ResponseType.BUSINESS_FAULT, ""),
			new MockResponsePojo(ResponseType.SOAP_RESPONSE,
				readClasspathFile(RESOURCES + "updateOTMExcpResponse.xml")));
	
		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionTrigger", orderTransactionTriggerRef);
	
		orderTransactionServiceRef = new DefaultSoapMockService(new ParameterReplacer(
			readClasspathFile(RESOURCES + "GetActivityRetryCounterMockResponse.xml"))
				.replace("NUM_RETRIES", numRetries).build());
	
		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);
	
		Thread threadHT = new Thread() {
		    public void run() {
			try {
			    sleep(50000);
			    hTask = getHumanTaskDao().findByOrderId(randomCorrelationId);
			    getHumanTaskDao().update(hTask, ABORT);
			    iti = (IdentityTypeImpl) hTask.getSystemAttributes().getAssignees().get(0);
			} catch (InterruptedException e) {
			    interrupt();
			}
		    }
		};
		threadHT.start();
	
		try {
		    final String requestXml = readClasspathFile(RESOURCES + "handleTechnicalFaultRequest.xml");
		    invocationResult = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
			    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				    new ParameterReplacer(requestXml).replace("CORRELATION_ID", randomCorrelationId)
					    .replace("ACTIVITY_ID", ACTIVITY_CODE).replace("FAULT_CODE", faultCode)
					    .build()));
	
		} catch (IllegalStateException e) {
			assertThat("Human Task group assignment meets the expectation!", iti.getId(),
				equalTo(expectedAssignee));
			assertThat("Human Task title fault category meets the expectation!", hTask.getTitle(),
			    containsString(LOGFAULT));
		    assertThat("orderTransactionTrigger Mock has been invoked",
			    orderTransactionTriggerRef.hasBeenInvoked(), is(Boolean.TRUE));
		    assertThat("orderTransactionService Mock has not been invoked",
			    orderTransactionServiceRef.hasBeenInvoked(), is(Boolean.FALSE));
		    assertThat("Composite service result expected to be null", invocationResult, is(nullValue()));
		} catch (Exception e) {
		    fail("Unexpected exception occured!");
		}
    }

    @Test
    public void businessFault2102WithHumanTaskAssignToLogisticsResend() throws Exception {
    	String requestXml = readClasspathFile(RESOURCES + "handleBusinessFaultRequest.xml");
		String faultMessage = "";
		final String taskOutcome = RESEND;
		String expectedAssignee = "Logistics";
		String faultCode = "2102";
		final String faultType = BISFAULT;
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    @Test
    public void emptyBusinessFaultWithHumanTaskAssignToLogisticsResend() throws Exception {
    	String requestXml = readClasspathFile(RESOURCES + "handleBusinessFaultRequestWithErrorMessageParameter.xml");
		String faultMessage = "Value of Sender ZIP code is incorrect. It have to consist of 5 numbers.";
		final String taskOutcome = RESEND;
		String expectedAssignee = "Logistics";
		String faultCode = "";
		final String faultType = BISFAULT;
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }
    
    @Test
    public void emptyTechnicalFaultWithHumanTaskAssignToLogisticsResend() throws Exception {
    	numRetries = "10";
	    String requestXml = readClasspathFile(RESOURCES + "handleTechnicalFaultRequestWithErrorMessageParameter.xml");
		String faultMessage = "oracle.fabric.common.FabricInvocationException: "
				+ "Unable to invoke endpoint URI \"http://localhost/ItemMasterService\" successfully "
				+ "due to: javax.xml.soap.SOAPException: javax.xml.soap.SOAPException: "
				+ "Message send failed: Connection refused";
		final String taskOutcome = RESEND;
		String expectedAssignee = "Logistics";
		String faultCode = "";
		final String faultType = TECHFAULT;		
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }
    
    @Test
    public void businessFaultWithHumanTaskAssignToCustomerServiceResend() throws Exception {
	    String requestXml = readClasspathFile(RESOURCES + "handleCustomFaultRequest.xml");
		String faultMessage ="Not leitcodable receiver address. Current receiver address...";
		final String taskOutcome = RESEND;
		String expectedAssignee = "CustomerService";
		String faultCode = "2400";
		final String faultType = BISFAULT;		
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }
    
    @Test
    public void businessFault2400WithHumanTaskAssignToCustomerServiceResend() throws Exception {
	    String requestXml = readClasspathFile(RESOURCES + "handleCustomFaultRequest.xml");
		String faultMessage ="Routing code for shipment can ...";
		final String taskOutcome = RESEND;
		String expectedAssignee = "CustomerService";
		String faultCode = "2400";
		final String faultType = BISFAULT;
		String htTitle = faultType;
		defaultGFHTest(requestXml, taskOutcome, faultCode, faultType, faultMessage, expectedAssignee, htTitle);
    }

    private void defaultGFHTest(String requestXml, final String taskOutcome, String faultCode, String faultType, String faultMessage, String expectedAssignee, String htTitle) throws Exception {
    	orderTransactionServiceRef = new DefaultSoapMockService(new ParameterReplacer(
    			readClasspathFile(RESOURCES + "GetActivityRetryCounterMockResponse.xml"))
    				.replace("NUM_RETRIES", numRetries).build());

    		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);

    		Thread threadHT = new Thread() {
    		    public void run() {
    			try {
    			    sleep(50000);
    			    hTask = getHumanTaskDao().findByOrderId(randomCorrelationId);
    			    getHumanTaskDao().update(hTask, taskOutcome);
    			    iti = (IdentityTypeImpl) hTask.getSystemAttributes().getAssignees().get(0);
    			} catch (InterruptedException e) {
    			    interrupt();
    			}
    		    }
    		};
    		threadHT.start();

    		invocationResult = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
    			SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
    				new ParameterReplacer(requestXml)
    					.replace("CORRELATION_ID", randomCorrelationId)
    					.replace("ACTIVITY_ID", ACTIVITY_CODE)
    					.replace("FAULT_CODE", faultCode)
    					.replace("FAULT_MESSAGE", faultMessage)
    					.replace("FAULT_TYPE", faultType)
    					.build()));

    	    assertThat("Human Task group assignment meets the expectation!", iti.getId(),
    		    equalTo(expectedAssignee));
    	    assertThat("Human Task title fault category meets the expectation!", hTask.getTitle(),
    		    containsString(htTitle));
    		assertBalActivities(queryOtm(randomCorrelationId, ACTIVITY_CODE, "Y"), ACTIVITY_CODE);
    		assertBalActivities(queryOtm(randomCorrelationId, String.format("%s-SOLVED", ACTIVITY_CODE), "Y"),
    			String.format("%s-SOLVED", ACTIVITY_CODE));
    		
    		if (faultType == TECHFAULT) {
        		assertThat("OrderTransactionService Mock should be invoked to get the number of retries",
            			orderTransactionServiceRef.hasBeenInvoked(), is(Boolean.TRUE));    			
    		} else {
        		assertThat("OrderTransactionService Mock should not be invoked",
            			orderTransactionServiceRef.hasBeenInvoked(), is(Boolean.FALSE));        			
    		}
    		
    		assertXpathEvaluatesTo(
    			"//gfhm:handleFaultResponse/gfhm:faultHandlingStrategy/gfh:transactionId/text()",
    			randomCorrelationId, invocationResult);
    		assertXpathEvaluatesTo(
    			"//gfhm:handleFaultResponse/gfhm:faultHandlingStrategy/gfh:faultStrategy/text()", taskOutcome,
    			invocationResult);
    }
    
    private void assertBalActivities(List<BalActivities> pBalActivities, String pExpectedActivityCode) {
	assertThat(String.format("No entry found for ACTIVITY_CODE [%s] in OTM!", pExpectedActivityCode),
		pBalActivities.isEmpty(), is(false));
	assertThat(String.format("No entry found for ACTIVITY_CODE [%s] in OTM!", pExpectedActivityCode),
		pBalActivities.size(), is(1));
	assertThat("ActivityCode is different from expectation!", pBalActivities.get(0).getActivityCode(),
		equalTo(pExpectedActivityCode));
    }
    
    private List<BalActivities> queryOtm(final String pCorrelationId, final String pActivityCode,
	    final String pErrorFlag) throws SQLException {
	return getOtmDao().query(new Query<BalActivities>() {

	    @Override
	    public String getQuery() {
		return "select * from bal_activities where correlation_id = ? and activity_code = ? and error = ?";
	    }

	    @Override
	    public Object[] getQueryParameters() {
		return new Object[] { pCorrelationId, pActivityCode, pErrorFlag };
	    }

	    @Override
	    public Class<BalActivities> getExpectedType() {
		return BalActivities.class;
	    }
	});
    }
}
