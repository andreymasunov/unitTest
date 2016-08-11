package de.home24.middleware.cancellationpublisher;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.xml.namespace.NamespaceContext;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.springframework.util.xml.SimpleNamespaceContext;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.opitzconsulting.soa.testing.MockService;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Specific {@link MockService} implementation to be used for mocking
 * GenericFaultHandler. Creates the mock service response out of the information
 * from the request and return the provided {@link FaultStrategy}.
 * 
 * @author svb
 *
 */
public class GenericFaultHandlerMock extends AbstractSoapMockService {

    /**
     * Enumeration describing possible fault strategy outcomes for
     * GenericFaultHandlerService
     * 
     * @author svb
     *
     */
    public enum FaultStrategy {
	RESEND("Resend"), ABORT("Abort");

	private String faultStrategy;

	private FaultStrategy(String pFaultStrategy) {
	    faultStrategy = pFaultStrategy;
	}

	public String getFaultStrategy() {
	    return faultStrategy;
	}
    }

    private static final String PATH_TO_RESOURCES_PROCESS_COMMON = "../processes/Common";

    private static final String NAMESPACE_GFH_MESSAGES = "http://home24.de/interfaces/bas/genericfaulthandler/genericfaulthandlerservicemessages/v1";
    private static final String NAMESPACE_EXCEPTION = "http://home24.de/data/common/exceptiontypes/v1";

    private List<FaultStrategy> faultStrategiesReturnValues;
    private NamespaceContext namespaces;
    private String genericFaultHandlerPayloadTemplate;

    public GenericFaultHandlerMock(FaultStrategy... pFaultStrategies) {

	faultStrategiesReturnValues = Lists.newArrayList(pFaultStrategies);

	namespaces = new SimpleNamespaceContext();
	((SimpleNamespaceContext) namespaces).bindNamespaceUri("exc", NAMESPACE_EXCEPTION);
	((SimpleNamespaceContext) namespaces).bindNamespaceUri("gfh", NAMESPACE_GFH_MESSAGES);
    }

    @Override
    public String serviceCallReceived(String pServicename, String pRequest)
	    throws ServiceException, Exception {

	final String correlationId = evaluateXPath(
		"//gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:transactionId/text()",
		pRequest);
	final String payload = evaluateXPath(
		"//gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:payload/*",
		pRequest);
	final String activityId = evaluateXPath(
		"//gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:activityId/text()",
		pRequest);

	int currentFaultStrategy = faultStrategiesReturnValues.size() > 1 ? getNumberOfInvocations() : 0;

	final String genericFaultHandlerResponse = new ParameterReplacer(
		getGenericFaultHandlerPayloadTemplate()).replace("PAYLOAD", payload)
			.replace("CORRELATION_ID", correlationId)
			.replace("FAULT_STRATEGY",
				faultStrategiesReturnValues.get(currentFaultStrategy).getFaultStrategy())
			.replace("PAYLOAD", payload).replace("ACTIVITY_ID", activityId)
			.replace("SOURCE_SERVICE_NAME", "").build();

	getReceivedRequests().add(pRequest);
	increaseInvocationCounter();

	return SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, genericFaultHandlerResponse);
    }

    protected String getGenericFaultHandlerPayloadTemplate() {

	if (genericFaultHandlerPayloadTemplate == null) {

	    try {
		genericFaultHandlerPayloadTemplate = CharStreams.toString(new InputStreamReader(getClass()
			.getResourceAsStream(String.format("%s/GenericFaultHandlerResponseTemplate.xml",
				PATH_TO_RESOURCES_PROCESS_COMMON))));
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}

	return genericFaultHandlerPayloadTemplate;
    }

    protected String evaluateXPath(String pXpathString, String pXmlInput) throws Exception {

	final XPath xpathResolver = XPathFactory.newInstance().newXPath();
	xpathResolver.setNamespaceContext(namespaces);

	InputSource source = new InputSource(
		new ByteArrayInputStream(pXmlInput.getBytes(StandardCharsets.UTF_8)));
	Node xmlNodeResult = (Node) xpathResolver.evaluate(pXpathString, source, XPathConstants.NODE);

	return convertToString(xmlNodeResult);
    }

    protected String convertToString(Node xmlNodeResult) throws Exception {

	StringWriter xmlResult = new StringWriter();
	Transformer xform = TransformerFactory.newInstance().newTransformer();
	xform.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
	xform.setOutputProperty(OutputKeys.INDENT, "yes");
	xform.transform(new DOMSource(xmlNodeResult), new StreamResult(xmlResult));

	return xmlResult.toString();
    }
}
