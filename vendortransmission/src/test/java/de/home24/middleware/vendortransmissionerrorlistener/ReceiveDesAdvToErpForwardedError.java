//TODO NOTE all test casses should be updated with 1.5 release code for this osb app
package de.home24.middleware.vendortransmissionerrorlistener;

import static de.home24.middleware.octestframework.vendortransmission.TestDataPovider.REPLACE_PARAM_ACTIVITY_CODE;
import static de.home24.middleware.octestframework.vendortransmission.TestDataPovider.REPLACE_PARAM_CORRELATION_ID;
import static de.home24.middleware.octestframework.vendortransmission.TestDataPovider.REPLACE_PARAM_FAULT_CODE;
import static de.home24.middleware.octestframework.vendortransmission.TestDataPovider.REPLACE_PARAM_FAULT_MESSAGE;
import static de.home24.middleware.octestframework.vendortransmission.TestDataPovider.REPLACE_PARAM_PAYLOAD;
import static de.home24.middleware.octestframework.vendortransmission.TestDataPovider.RESOURCES_PATH_QUEUES;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isEmptyString;

import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.components.BaseQuery;
import de.home24.middleware.octestframework.components.BaseQuery.SqlOp;
import de.home24.middleware.octestframework.components.MessagingDao.JmsModule;
import de.home24.middleware.octestframework.components.QueryPredicate;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.octestframework.vendortransmission.TestDataPovider;

/**
 * Test implementations for
 * VendorTransmissionErrorListener.receiveDesAdvToErpForwardedError
 * 
 * @author svb
 *
 */
public class ReceiveDesAdvToErpForwardedError extends AbstractBaseSoaTest {

    private static final String PARAM_VALUE_ACTIVITY_CODE = "P1002-DESADV-2ERP-ACK";

    private DefaultSoapMockService ediInboundVendorTransmissionCallbackRef = null;
    private DefaultSoapMockService responseRetryWrapperRef = null;

    private String correlationId = null;
    private String expectedActivityCode = null;
    private Map<ExceptionAsserterKey, String> keyToExpectedExceptionValues = null;

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    @Autowired
    private TestDataPovider testDataPovider;

    @Before
    public void setUp() {

	declareXpathNS("exception", "http://home24.de/data/common/exceptiontypes/v1");

	correlationId = String.valueOf(System.currentTimeMillis());
	expectedActivityCode = String.format("%s-ERR", PARAM_VALUE_ACTIVITY_CODE);

	getOtmDao().query(new BaseQuery<BalActivities>(SqlOp.DELETE,
		new QueryPredicate("correlation_id", correlationId), BalActivities.class));

	keyToExpectedExceptionValues = testDataPovider.createCommonExpectedValuesForException(correlationId);

	ediInboundVendorTransmissionCallbackRef = new DefaultSoapMockService();
	responseRetryWrapperRef = new DefaultSoapMockService();

	mockOsbBusinessService(
		"VendorTransmissionErrorListener/operations/receiveDesAdvToErpForwardedError/business-service/EdiInboundCallbackRef",
		ediInboundVendorTransmissionCallbackRef);
	mockOsbBusinessService(
		"VendorTransmissionErrorListener/operations/receiveDesAdvToErpForwardedError/business-service/RetryResponseWrapperRef",
		responseRetryWrapperRef);
    }

    @Test
    public void whenReceiveValidResponseFromErrFwdDesAdvQThenAnExceptioCallbackContainingTheExceptionDeliveredbyNavisionIsReturned() {

	final String faultCategory = "TechnicalFault";
	final String faultCode = "MW-10301";
	final String faultMessage = "TechnicalFault from NAV received!";

	final String errForwardDesAdvRequest = new ParameterReplacer(readClasspathFile(String.format(
		"%s/ERR_ForwardDesAdv_Q/ReceiveDesAdvtoERPExceptionResponse.xml", RESOURCES_PATH_QUEUES)))
			.replace(REPLACE_PARAM_CORRELATION_ID, correlationId)
			.replace(REPLACE_PARAM_ACTIVITY_CODE, PARAM_VALUE_ACTIVITY_CODE)
			.replace(REPLACE_PARAM_FAULT_CODE, faultCode)
			.replace(REPLACE_PARAM_FAULT_MESSAGE, faultMessage).replace(REPLACE_PARAM_PAYLOAD,
				testDataPovider.createExpectedForwardDesAdvMessage(correlationId))
			.build();

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.EDIFACT, "h24jms.ERR_ForwardDesAdv_Q",
		errForwardDesAdvRequest);

	waitForInvocationOf(ediInboundVendorTransmissionCallbackRef);

	assertThat("ResponseRetryWrapper has been invoked!", responseRetryWrapperRef.hasBeenInvoked(),
		is(false));

	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CATEGORY, faultCategory);
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CODE, faultCode);
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_MESSAGE, faultMessage);

	exceptionAsserter.assertException(ediInboundVendorTransmissionCallbackRef.getLastReceivedRequest(),
		keyToExpectedExceptionValues);

	assertXpathEvaluatesTo("count(exception:exception/exception:context/exception:payload/*)", "0",
		ediInboundVendorTransmissionCallbackRef.getLastReceivedRequest());

	assertOtm(expectedActivityCode);
    }

    @Test
    public void whenReceiveInvalidResponseFromErrFwdDesAdvQThenRetryResponseWrapperIsInvokedWithADataFault() {

	final String faultCategory = "DataFault";
	final String faultCode = "MW-40201";
	final String faultMessage = "Data validation fault for receiveDesAdvForwardedToErpError";

	final String invalidErrForwardDesAdvRequest = new ParameterReplacer(readClasspathFile(
		String.format("%s/ERR_ForwardDesAdv_Q/ReceiveInvalidDesAdvtoERPExceptionResponse.xml",
			RESOURCES_PATH_QUEUES))).replace(REPLACE_PARAM_CORRELATION_ID, correlationId)
				.replace(REPLACE_PARAM_ACTIVITY_CODE, PARAM_VALUE_ACTIVITY_CODE)
				.replace(REPLACE_PARAM_FAULT_CODE, faultCode)
				.replace(REPLACE_PARAM_FAULT_MESSAGE, faultMessage)
				.replace(REPLACE_PARAM_PAYLOAD,
					testDataPovider.createExpectedForwardDesAdvMessage(correlationId))
			.build();

	getOsbAccessor().flushUriChanges();

	getMessagingDao().writeToQueue(JmsModule.EDIFACT, "h24jms.ERR_ForwardDesAdv_Q",
		invalidErrForwardDesAdvRequest);

	waitForInvocationOf(responseRetryWrapperRef);

	assertThat("ResponseRetryWrapper has not been invoked!", responseRetryWrapperRef.hasBeenInvoked(),
		is(true));
	assertThat("EDIInboundVendorTransmissionProcess has not been invoked!",
		ediInboundVendorTransmissionCallbackRef.hasBeenInvoked(), is(true));

	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CATEGORY, faultCategory);
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CODE, faultCode);
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_MESSAGE, faultMessage);

	exceptionAsserter.assertException(ediInboundVendorTransmissionCallbackRef.getLastReceivedRequest(),
		keyToExpectedExceptionValues);

	final String originalExceptionExtractedFromResponseRetryWrapperRequest = evaluateXpath(
		"exception:exception/exception:context/exception:payload/*",
		responseRetryWrapperRef.getLastReceivedRequest());
	assertXmlEquals(invalidErrForwardDesAdvRequest,
		originalExceptionExtractedFromResponseRetryWrapperRequest);

	assertOtm(expectedActivityCode);
    }

    @Test
    public void whenReceiveValidResponseFromErrFwdDesAdvQButTheCallbackMessageCouldNotBeSentThenResponseRetryWrapperIsInvokedWithATechnicalFault() {

	final String faultCategory = "TechnicalFault";
	final String faultCode = "MW-10201";
	final String faultMessage = "echnical fault while calling back EdiVendorTransmissionProcess";

	final String errForwardDesAdvRequest = new ParameterReplacer(readClasspathFile(String.format(
		"%s/ERR_ForwardDesAdv_Q/ReceiveDesAdvtoERPExceptionResponse.xml", RESOURCES_PATH_QUEUES)))
			.replace(REPLACE_PARAM_CORRELATION_ID, correlationId)
			.replace(REPLACE_PARAM_ACTIVITY_CODE, PARAM_VALUE_ACTIVITY_CODE)
			.replace(REPLACE_PARAM_FAULT_CODE, faultCode)
			.replace(REPLACE_PARAM_FAULT_MESSAGE, faultMessage).replace(REPLACE_PARAM_PAYLOAD,
				testDataPovider.createExpectedForwardDesAdvMessage(correlationId))
			.build();

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.EDIFACT, "h24jms.ERR_ForwardDesAdv_Q",
		errForwardDesAdvRequest);

	waitForInvocationOf(responseRetryWrapperRef);

	assertThat("ResponseRetryWrapper has not been invoked!", responseRetryWrapperRef.hasBeenInvoked(),
		is(true));
	assertThat("EDIInboundVendorTransmissionProcess has not been invoked!",
		ediInboundVendorTransmissionCallbackRef.hasBeenInvoked(), is(true));

	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CATEGORY, faultCategory);
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CODE, faultCode);
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_MESSAGE, faultMessage);

	final String originalExceptionExtractedFromResponseRetryWrapperRequest = evaluateXpath(
		"exception:exception/exception:context/exception:payload/*",
		responseRetryWrapperRef.getLastReceivedRequest());
	assertXmlEquals(errForwardDesAdvRequest, originalExceptionExtractedFromResponseRetryWrapperRequest);

	exceptionAsserter.assertException(ediInboundVendorTransmissionCallbackRef.getLastReceivedRequest(),
		keyToExpectedExceptionValues);
    }

    void assertOtm(final String expectedActivityCode) {
	List<BalActivities> balActivity = getOtmDao().query(
		new BaseQuery<BalActivities>(SqlOp.SELECT, new QueryPredicate("correlation_id", correlationId)
			.withEquals("activity_id", expectedActivityCode), BalActivities.class));

	assertThat("More than one entry found!", balActivity, hasSize(1));
	assertThat("ActivityCode does not meet expectation!", balActivity.get(0).getActivityCode(),
		equalTo(expectedActivityCode));
	assertThat("CorrelationId does not meet expectation!", balActivity.get(0).getCorrelationId(),
		equalTo(correlationId));
	assertThat("Error flag is not set properly!", balActivity.get(0).getError(), equalTo("Y"));
	assertThat("Error message is empty!", balActivity.get(0).getErrorMessage(), not(isEmptyString()));
    }
}
