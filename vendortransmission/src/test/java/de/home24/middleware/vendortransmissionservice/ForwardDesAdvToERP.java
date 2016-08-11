//TODO NOTE all test casses should be updated with 1.5 release code for this osb app
package de.home24.middleware.vendortransmissionservice;

import static de.home24.middleware.octestframework.vendortransmission.TestDataPovider.RESOURCES_PATH_EXAMPLES;
import static de.home24.middleware.octestframework.vendortransmission.TestDataPovider.RESOURCES_PATH_SB;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.ServiceException;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.octestframework.vendortransmission.TestDataPovider;

/**
 * Tests for VendorTransmissionService.forwardDesAdvToERP
 * 
 * @author svb
 *
 */
public class ForwardDesAdvToERP extends AbstractBaseSoaTest {

    private static final Logger LOGGER = Logger.getLogger(ForwardDesAdvToERP.class.getSimpleName());

    private String forwardDesAdvRequest = null;
    private DefaultSoapMockService reqForwardDesAdvQueueRef = null;

    private String correlationId = null;
    private Map<ExceptionAsserterKey, String> keyToExpectedExceptionValues = null;

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    @Autowired
    private TestDataPovider testDataPovider;

    @Before
    public void setUp() {

	correlationId = String.valueOf(System.currentTimeMillis());

	keyToExpectedExceptionValues = testDataPovider.createCommonExpectedValuesForException(correlationId);

	final String desAdvMessage = readClasspathFile(String.format("%s/WMF.xml", RESOURCES_PATH_EXAMPLES));
	final String desAdvElement = evaluateXpath("//DesadvMessage/*", desAdvMessage);
	forwardDesAdvRequest = new ParameterReplacer(
		readClasspathFile(String.format("%s/ForwardDesAdvToERPRequest.xml", RESOURCES_PATH_SB)))
			.replace("CORRELATION_ID", correlationId).replace("DESADV_CONTENT", desAdvElement)
			.build();

	final DefaultSoapMockService reqForwardDesAdvQueueRef = new DefaultSoapMockService();
	mockOsbBusinessService(
		"VendorTransmissionService/operations/forwardDesAdvToERP/business-services/reqForwardDesAdvQueueRef",
		reqForwardDesAdvQueueRef);
    }

    @Test
    public void whenInvokedWithValidDataThenSendDesAdvMessageToReqFwdDesAdvQ() {

	invokeOsbProxyService("VendorTransmissionService/exposed/v1/VendorTransmissionService",
		forwardDesAdvRequest);

	assertThat("ReqForwardDesAdvQueueRef has not been invoked!",
		reqForwardDesAdvQueueRef.hasBeenInvoked(), is(true));
	assertXmlEquals(testDataPovider.createExpectedForwardDesAdvMessage(correlationId),
		reqForwardDesAdvQueueRef.getLastReceivedRequest());
    }

    @Test
    public void whenInvokedWithInvalidDataThenAnExceptionCallbackContainingADataFaultIsReturned()
	    throws Exception {

	final String orderNumberReferenceThatisTooLongAccordingToSchema = "1234567890123456789012345678901234567890";

	forwardDesAdvRequest = manipulateOrderNumberInDesAdv(
		orderNumberReferenceThatisTooLongAccordingToSchema);

	LOGGER.info(String.format("+++++++++++++ Message after ordernumber replacement: %s",
		forwardDesAdvRequest));

	try {
	    invokeOsbProxyService("VendorTransmissionService/exposed/v1/VendorTransmissionService",
		    forwardDesAdvRequest);
	    fail(String.format("Exception is expected!"));
	} catch (ServiceException e) {

	    assertThat("ReqForwardDesAdvQueueRef has been invoked!",
		    reqForwardDesAdvQueueRef.hasBeenInvoked(), is(false));

	    keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "DataFault");
	    keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-40102");
	    keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		    "Data validation fault for forwardDesAdvToERPRequest");
	    exceptionAsserter.assertException(e.getXml(), keyToExpectedExceptionValues);
	} catch (Throwable e) {
	    fail(String.format("Unexpected exception occurs!"));
	}

    }

    @Test
    public void whenInvokedWithValidDataButWritingToReqFwdDesAdvQFailsThenAnExceptionCallbackContainingATechnicalFaultIsReturned() {

	reqForwardDesAdvQueueRef = new DefaultSoapMockService(Lists.newArrayList(new MockResponsePojo(
		ResponseType.FAULT, "Queue is not available!", "Queue is not available!")));

	try {
	    invokeOsbProxyService("VendorTransmissionService/exposed/v1/VendorTransmissionService",
		    forwardDesAdvRequest);
	    fail(String.format("Exception is expected!"));
	} catch (ServiceException e) {

	    assertThat("ReqForwardDesAdvQueueRef has not been invoked!",
		    reqForwardDesAdvQueueRef.hasBeenInvoked(), is(true));

	    keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	    keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_CODE, "MW-10102");
	    keyToExpectedExceptionValues.put(ExceptionAsserterKey.FAULT_MESSAGE,
		    "Technical fault while forward DESADV message to REQ_ForwardDesAdv_Q");
	    exceptionAsserter.assertException(e.getXml(), keyToExpectedExceptionValues);
	} catch (Throwable e) {
	    fail(String.format("Unexpected exception occurs!"));
	}

    }

    private String manipulateOrderNumberInDesAdv(final String pOrderNumberReplacement) throws Exception {
	final Document forwardDesAdvDocument = DocumentBuilderFactory.newInstance().newDocumentBuilder()
		.parse(new InputSource(new StringReader(forwardDesAdvRequest)));
	Node orderNumberNode = (Node) XPathFactory.newInstance().newXPath().evaluate(
		"//DESADV/HEAD/OrderNumberRef/DocRefNumber", forwardDesAdvDocument, XPathConstants.NODE);
	orderNumberNode.setTextContent(pOrderNumberReplacement);

	String manipulatedDesAdvRequest = null;
	try (StringWriter writer = new StringWriter()) {
	    TransformerFactory.newInstance().newTransformer().transform(new DOMSource(forwardDesAdvDocument),
		    new StreamResult(writer));

	    manipulatedDesAdvRequest = writer.toString();
	}

	return manipulatedDesAdvRequest;
    }
}
