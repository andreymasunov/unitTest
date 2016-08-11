package de.home24.middleware.octestframework.assertion;

import static org.junit.Assert.fail;

import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.transform.stream.StreamSource;

import org.custommonkey.xmlunit.jaxp13.Validator;
import org.springframework.stereotype.Component;
import org.xml.sax.SAXParseException;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;

/**
 * Asserter utility that implements the logic to assert a
 * {http://home24.de/data/common/exceptiontypes/v1}exception XML element
 * 
 * @author svb
 *
 */
@Component
public class ExceptionAsserter extends AbstractBaseSoaTest {

    /**
     * Encapsulates the assertion key representing a xpath string and if the
     * assertion is a simple or a complex one.
     * 
     * @author svb
     *
     */
    public enum ExceptionAsserterKey {

	SOURCE_SYSTEM_NAME("exception:context/exception:sourceSystemName", true),
	TRANSACTION_ID("exception:context/exception:transactionId", true),
	PROCESS_LIBRARY_ID("exception:context/exception:processLibraryId", true),
	PAYLOAD_ELEMENT_NAME("local-name(//exception:exception/exception:context/exception:payload/*[1])",
		false),
	PAYLOAD_ELEMENT("exception:context/exception:payload/*", false),
	SEVERITY("exception:severity", true),
	FAULT_CATEGORY("exception:category", true),
	FAULT_CODE("exception:faultInfo/exception:faultCode", true),
	FAULT_MESSAGE("exception:faultInfo/exception:faultMessage", true);

	private String xpath;
	private boolean isSimpleAssertion;

	private ExceptionAsserterKey(String pXpath, boolean pIsSimpleAssertion) {

	    xpath = pXpath;
	    isSimpleAssertion = pIsSimpleAssertion;
	}
    }

    private static final Logger LOGGER = Logger.getLogger(ExceptionAsserter.class.getSimpleName());

    private static final String NS_EXCEPTION = "http://home24.de/data/common/exceptiontypes/v1";
    private static final String NS_PREFIX_EXCEPTION = "exception";

    /**
     * Validates a {http://home24.de/data/common/exceptiontypes/v1}exception XML
     * element against the schema and checks passed values.
     * 
     * Currently the method validates:
     * <ul>
     * <li>exception:context/exception:sourceSystemName - value</li>
     * <li>exception:context/exception:transactionId - value</li>
     * <li>exception:context/exception:processLibraryId - value</li>
     * <li>exception:context/exception:payload - name of payload element</li>
     * <li>exception:context/exception:payload - XML comparison of payload
     * element</li>
     * <li>exception:severity - value (static: ERROR)</li>
     * <li>exception:category - value</li>
     * <li>exception:faultInfo/exception:faultCode - value</li>
     * <li>exception:faultInfo/exception:faultMessage - value</li>
     * </ul>
     * 
     * @param pInvocationResult
     *            the string represents the
     *            {http://home24.de/data/common/exceptiontypes/v1}exception XML
     *            element
     * @param pKeyToExpectedValues
     *            a map contains the expected values for the corresponding
     *            exception values
     */
    public void assertException(String pInvocationResult,
	    Map<ExceptionAsserterKey, String> pKeyToExpectedValues) {

	declareXpathNS(NS_PREFIX_EXCEPTION, NS_EXCEPTION);
	final String exceptionNodeString = evaluateXpath("//exception:exception", pInvocationResult);
	validateElementWithXsd(exceptionNodeString);
	assertBaseException(exceptionNodeString, pKeyToExpectedValues);
    }

    /**
     * Validates a {http://home24.de/data/common/exceptiontypes/v1}exception XML
     * element against the schema.
     * 
     * @param pExceptionNodeString
     *            the string represents the
     *            {http://home24.de/data/common/exceptiontypes/v1}exception XML
     *            element
     */
    @SuppressWarnings(value = "rawtypes")
    public void validateElementWithXsd(String exceptionNodeString) {

	final String schemaFileContent = readClasspathFile(
		"../../apps/home24/interfaces/common/v1/ExceptionTypes.xsd");

	final Validator validator = new Validator();
	validator.addSchemaSource(new StreamSource(new StringReader(schemaFileContent)));

	final StreamSource streamSource = new StreamSource(new StringReader(exceptionNodeString));
	final List instanceErrors = validator.getInstanceErrors(streamSource);

	if (!instanceErrors.isEmpty()) {

	    final Iterator exceptionIterator = instanceErrors.iterator();
	    while (exceptionIterator.hasNext()) {

		final SAXParseException saxParseException = (SAXParseException) exceptionIterator.next();
		LOGGER.log(Level.SEVERE, String.format("XSD validator fault"), saxParseException);
	    }

	    fail(String.format("XSD validation return %s faults!", instanceErrors.size()));
	}
    }

    /**
     * Validates a {http://home24.de/data/common/exceptiontypes/v1}exception XML
     * element against the schema and checks passed values.
     * 
     * @param pExceptionNodeString
     *            the string represents the
     *            {http://home24.de/data/common/exceptiontypes/v1}exception XML
     *            element
     * @param pKeyToExpectedValues
     *            a map contains the expected values for the corresponding
     *            exception values
     */
    void assertBaseException(String pExceptionNodeString,
	    Map<ExceptionAsserterKey, String> pKeyToExpectedValues) {

	declareXpathNS(NS_PREFIX_EXCEPTION, NS_EXCEPTION);

	for (ExceptionAsserterKey key : pKeyToExpectedValues.keySet()) {

	    if (key.isSimpleAssertion) {
		assertXpathEvaluatesTo(String.format("//exception:exception/%s/text()", key.xpath),
			pKeyToExpectedValues.get(key), pExceptionNodeString);
	    }
	}

	assertDefaultSeverityIfNeeded(pExceptionNodeString, pKeyToExpectedValues);
	assertPayloadElementNameIfNeeded(pExceptionNodeString, pKeyToExpectedValues);
	assertPayloadIfNeeded(pExceptionNodeString, pKeyToExpectedValues);
    }

    /**
     * Assert the payload element of a
     * {http://home24.de/data/common/exceptiontypes/v1}exception
     * 
     * @param pExceptionNodeString
     *            the string represents the
     *            {http://home24.de/data/common/exceptiontypes/v1}exception XML
     *            element
     * @param pKeyToExpectedValues
     *            a map contains the expected values for the corresponding
     *            exception values
     */
    void assertPayloadIfNeeded(String pExceptionNodeString,
	    Map<ExceptionAsserterKey, String> pKeyToExpectedValues) {
	if (pKeyToExpectedValues.containsKey(ExceptionAsserterKey.PAYLOAD_ELEMENT)) {

	    final String payloadElement = evaluateXpath(
		    String.format("//exception:exception/%s", ExceptionAsserterKey.PAYLOAD_ELEMENT.xpath),
		    pExceptionNodeString);
	    assertXmlEquals(payloadElement, pKeyToExpectedValues.get(ExceptionAsserterKey.PAYLOAD_ELEMENT));
	}
    }

    /**
     * Assert the payload element name of a
     * {http://home24.de/data/common/exceptiontypes/v1}exception
     * 
     * @param pExceptionNodeString
     *            the string represents the
     *            {http://home24.de/data/common/exceptiontypes/v1}exception XML
     *            element
     * @param pKeyToExpectedValues
     *            a map contains the expected values for the corresponding
     *            exception values
     */
    void assertPayloadElementNameIfNeeded(String pExceptionNodeString,
	    Map<ExceptionAsserterKey, String> pKeyToExpectedValues) {
	if (pKeyToExpectedValues.containsKey(ExceptionAsserterKey.PAYLOAD_ELEMENT_NAME)) {
	    assertXpathEvaluatesTo(ExceptionAsserterKey.PAYLOAD_ELEMENT_NAME.xpath,
		    pKeyToExpectedValues.get(ExceptionAsserterKey.PAYLOAD_ELEMENT_NAME),
		    pExceptionNodeString);
	}
    }

    /**
     * Assert the severity element of a
     * {http://home24.de/data/common/exceptiontypes/v1}exception, to contain the
     * default severity (ERROR) in case it is not explictly defined to be
     * asserted
     * 
     * @param pExceptionNodeString
     *            the string represents the
     *            {http://home24.de/data/common/exceptiontypes/v1}exception XML
     *            element
     * @param pKeyToExpectedValues
     *            a map contains the expected values for the corresponding
     *            exception values
     */
    void assertDefaultSeverityIfNeeded(String pExceptionNodeString,
	    Map<ExceptionAsserterKey, String> pKeyToExpectedValues) {
	if (!pKeyToExpectedValues.containsKey(ExceptionAsserterKey.SEVERITY)) {
	    assertXpathEvaluatesTo(
		    String.format("//exception:exception/%s/text()", ExceptionAsserterKey.SEVERITY.xpath),
		    "ERROR", pExceptionNodeString);
	}
    }
}
