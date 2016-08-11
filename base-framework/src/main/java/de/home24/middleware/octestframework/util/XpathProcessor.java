package de.home24.middleware.octestframework.util;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;

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

import com.opitzconsulting.soa.testing.util.SoapUtil;

/**
 * Utility class for processing XPath constructs. Defines default namespace for:
 * <ul>
 * <li>http://home24.de/data/common/exceptiontypes/v1</li>
 * <li>http://schemas.xmlsoap.org/soap/envelope/</li>
 * </ul>
 * 
 * @author svb
 *
 */
public class XpathProcessor {

    public static final String NAMESPACE_EXCEPTION = "http://home24.de/data/common/exceptiontypes/v1";
    public static final String NAMESPACE_PREFIX_EXCEPTION = "exc";

    private NamespaceContext namespaces;
    private XPathFactory xPathFactory;
    private Transformer transformer;

    public XpathProcessor() {

	namespaces = new SimpleNamespaceContext();
	declareNamespace("soapenv", SoapUtil.SoapVersion.SOAP11.getNamespace());
	declareNamespace(NAMESPACE_PREFIX_EXCEPTION, NAMESPACE_EXCEPTION);

	xPathFactory = XPathFactory.newInstance();
    }

    /**
     * Declare additional namespace to be used in XPath expression.
     * 
     * @param pNamespacePrefix
     *            the namespace prefix
     * @param pNamespace
     *            the namespace
     */
    public void declareNamespace(String pNamespacePrefix, String pNamespace) {

	((SimpleNamespaceContext) namespaces).bindNamespaceUri(pNamespacePrefix, pNamespace);
    }

    /**
     * Executes the passed XPath string on the XML string.
     * 
     * @param pXpathString
     *            the XPath string
     * @param pXmlInput
     *            the source XML string
     * @return the resulting XML structure as {@link String}
     * @throws Exception
     */
    public String evaluateXPath(String pXpathString, String pXmlInput) throws Exception {

	final XPath xpathResolver = xPathFactory.newXPath();
	xpathResolver.setNamespaceContext(namespaces);

	final Node xmlNodeResult = (Node) xpathResolver.evaluate(pXpathString,
		new InputSource(new ByteArrayInputStream(pXmlInput.getBytes(StandardCharsets.UTF_8))),
		XPathConstants.NODE);

	return convertToString(xmlNodeResult);
    }

    /**
     * Converts a {@link Node} to a {@link String} using a {@link Transformer}
     * instance.
     * 
     * @param xmlNodeResult
     *            the {@link Node} to be converted
     * @return the XML string
     * @throws Exception
     */
    String convertToString(Node xmlNodeResult) throws Exception {

	if (transformer == null) {
	    transformer = TransformerFactory.newInstance().newTransformer();
	    transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
	    transformer.setOutputProperty(OutputKeys.INDENT, "yes");
	}

	final StringWriter xmlResult = new StringWriter();
	transformer.transform(new DOMSource(xmlNodeResult), new StreamResult(xmlResult));

	return xmlResult.toString();
    }
}
