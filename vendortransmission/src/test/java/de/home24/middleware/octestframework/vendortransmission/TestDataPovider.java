package de.home24.middleware.octestframework.vendortransmission;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.google.common.io.CharStreams;

import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Test data provider to provide common functionality to create test data for
 * VendorTransmission tests
 * 
 * @author svb
 *
 */
@Component
public class TestDataPovider {

    public static final String RESOURCES_PATH_SB = "../servicebus/VendorTransmission/VendorTransmissionService/forwardDesAdvToERP";
    public static final String RESOURCES_PATH_EXAMPLES = "../examples/iwofurn/DESADV";
    public static final String RESOURCES_PATH_QUEUES = "../queues/h24_Edifact/";

    public static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
    public static final String REPLACE_PARAM_ACTIVITY_CODE = "ACTIVITY_CODE";
    public static final String REPLACE_PARAM_FAULT_CODE = "FAULT_CODE";
    public static final String REPLACE_PARAM_FAULT_CATEGORY = "FAULT_CATEGORY";
    public static final String REPLACE_PARAM_FAULT_MESSAGE = "FAULT_MESSAGE";
    public static final String REPLACE_PARAM_PAYLOAD = "PAYLOAD";

    /**
     * Provide common data regarding to be asserted on the created exception
     * elements using the {@link ExceptionAsserter}.
     * 
     * @param pCorrelationId
     *            the correlation_id to be used
     * @return a {@link Map} containing the expected values
     */
    public Map<ExceptionAsserterKey, String> createCommonExpectedValuesForException(String pCorrelationId) {

	final Map<ExceptionAsserterKey, String> keyToExpectedExceptionValues = new HashMap<>();
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.PROCESS_LIBRARY_ID, "P1002");
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.SEVERITY, "ERROR");
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.SOURCE_SYSTEM_NAME, "Middleware");
	keyToExpectedExceptionValues.put(ExceptionAsserterKey.TRANSACTION_ID, pCorrelationId);

	return keyToExpectedExceptionValues;
    }

    /**
     * Provide test data for a expected forwardDesAdv message which is send to
     * the REQ_ForwardDesAdv_Q.
     * 
     * @param pCorrelationId
     *            the correlation_id to be used
     * @return a {@link Map} containing the expected value
     */
    public String createExpectedForwardDesAdvMessage(String pCorrelationId) {

	String expectedForwardDesAdvMessage = null;
	try {
	    expectedForwardDesAdvMessage = CharStreams
		    .toString(new InputStreamReader(getClass().getResourceAsStream(
			    (String.format("../%s/REQ_ForwardDesAdv_Q/ExpectedForwardDesAdvToERPMessage.xml",
				    RESOURCES_PATH_QUEUES)))));
	} catch (IOException e) {
	    throw new RuntimeException(e);
	}

	return new ParameterReplacer(expectedForwardDesAdvMessage)
		.replace(REPLACE_PARAM_CORRELATION_ID, pCorrelationId).build();
    }

}
