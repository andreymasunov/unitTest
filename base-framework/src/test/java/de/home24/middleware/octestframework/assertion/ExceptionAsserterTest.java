package de.home24.middleware.octestframework.assertion;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import com.google.common.io.CharStreams;

/**
 * Test for {@link ExceptionAsserter}
 * 
 * @author svb
 *
 */
public class ExceptionAsserterTest {

    @Test
    public void whenValidExceptionAndAssertionsWithoutExplictlyDefiningSeverityThenReturnValidationSuccess()
	    throws Exception {

	final String exceptionStructure = CharStreams
		.toString(new InputStreamReader(getClass().getResourceAsStream("ExampleException.xml")));
	String expectedPayload = CharStreams
		.toString(new InputStreamReader(getClass().getResourceAsStream("ExamplePayload.xml")));

	Map<ExceptionAsserter.ExceptionAsserterKey, String> keysToExpectedValues = new HashMap<>();
	keysToExpectedValues.put(ExceptionAsserter.ExceptionAsserterKey.TRANSACTION_ID, "1231376876");
	keysToExpectedValues.put(ExceptionAsserter.ExceptionAsserterKey.SOURCE_SYSTEM_NAME, "Middleware");
	keysToExpectedValues.put(ExceptionAsserter.ExceptionAsserterKey.PROCESS_LIBRARY_ID, "P1003");
	keysToExpectedValues.put(ExceptionAsserter.ExceptionAsserterKey.PAYLOAD_ELEMENT_NAME,
		"convertPurchaseOrderToCSVRequest");
	keysToExpectedValues.put(ExceptionAsserter.ExceptionAsserterKey.PAYLOAD_ELEMENT, expectedPayload);
	keysToExpectedValues.put(ExceptionAsserter.ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	keysToExpectedValues.put(ExceptionAsserter.ExceptionAsserterKey.FAULT_CODE, "MW-10101");
	keysToExpectedValues.put(ExceptionAsserter.ExceptionAsserterKey.FAULT_MESSAGE,
		"Technical fault while converting the PO to CSV");

	new ExceptionAsserter().assertException(exceptionStructure, keysToExpectedValues);
    }
}
