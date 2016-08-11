package de.home24.middleware.salesordertrigger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xmlunit.diff.ElementSelector;
import org.xmlunit.diff.ElementSelectors;
import org.xmlunit.matchers.CompareMatcher;

import com.opitzconsulting.soa.testing.ServiceException;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class ReceiveCloseShipmentTest extends AbstractBaseSoaTest {

	private static final Logger LOGGER = Logger.getLogger(UpdateSalesOrderLineCallbackTest.class.getSimpleName());

	private final static String PATH_INVOKE_SERVICE = "SalesOrderTrigger/operations/receiveCloseShipment/ReceiveCloseShipment";
	private final static String PATH_CLOSESHIPTMENT_PROCESS = "SalesOrderTrigger/shared/business-service/CloseShipmentProcess";
	private final static String PATH_CLOSESHIPTMENT_ERR_Q = "SalesOrderTrigger/operations/receiveCloseShipment/business-service/CloseShipmentErrorQueue";

	private DefaultSoapMockService closeShipmentProcessSuccessMock = null;
	private DefaultSoapMockService closeShipmentProcessErrorMock = null;
	private List<MockResponsePojo> closeShipmentProcessTechnicalFaultMockPojoList = new ArrayList<MockResponsePojo>();
	private DefaultSoapMockService closeShipmentErrorQueueMock = null;
	private String randomCorrelationId = "";
	private String randomLine1 = "";
	private String randomLine2 = "";
	private String randomVendorId1 = "";
	private String randomVendorId2 = "";

	@BeforeClass
	public static void setUpBeforeClass() {
		testInitialization();
	}

	@Before
	public void setUp() throws Exception {

		Random randomNumber = new Random();
		randomCorrelationId = "DS" + String.valueOf(randomNumber.nextInt(1000000));
		randomLine1 = String.valueOf(randomNumber.nextInt(1000000));
		randomLine2 = String.valueOf(randomNumber.nextInt(1000000));
		randomVendorId1 = String.valueOf(randomNumber.nextInt(1000000));
		randomVendorId2 = String.valueOf(randomNumber.nextInt(1000000));

		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("csp", "http://home24.de/interfaces/bps/closeshipmentprocess/closeshipmentprocessmessages/v1");
		declareXpathNS("tns", "http://home24.de/data/navision/shipmentmessages/v1");
		declareXpathNS("oagis", "http://www.openapplications.org/oagis/10");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");

		closeShipmentProcessSuccessMock = new DefaultSoapMockService();
		closeShipmentProcessErrorMock = new DefaultSoapMockService(closeShipmentProcessTechnicalFaultMockPojoList);
		closeShipmentProcessTechnicalFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
		closeShipmentErrorQueueMock = new DefaultSoapMockService();
		
	}

	@After
	public void tearDown() {
		closeShipmentProcessSuccessMock = null;
		closeShipmentProcessErrorMock = null;
		closeShipmentErrorQueueMock = null;
	}

	@Test
	public void closeShipmentHappyPathTest() {

		final String requestXML = new ParameterReplacer(readClasspathFile("closeshipment/Request_closeShipment.xml"))
				.replace("CORRELATION_ID", randomCorrelationId).replace("LINE_1", randomLine1)
				.replace("LINE_2", randomLine2).replace("VENDOR_ID_1", randomVendorId1)
				.replace("VENDOR_ID_2", randomVendorId2).build();

		mockOsbBusinessService(PATH_CLOSESHIPTMENT_PROCESS, closeShipmentProcessSuccessMock);
		mockOsbBusinessService(PATH_CLOSESHIPTMENT_ERR_Q, closeShipmentErrorQueueMock);
		
		LOGGER.info("+++invoke service with Succes");
		try {
			invokeOsbProxyService(PATH_INVOKE_SERVICE, requestXML);

			assertTrue(closeShipmentProcessSuccessMock.hasBeenInvoked());
			assertFalse(closeShipmentErrorQueueMock.hasBeenInvoked());
			String responseXML = closeShipmentProcessSuccessMock.getLastReceivedRequest();
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/csp:processCloseShipmentRequest/csp:salesOrder/oagis:SalesOrderHeader/oagis:ID/text()",
					randomCorrelationId, responseXML);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/csp:processCloseShipmentRequest/csp:salesOrder/oagis:SalesOrderLine[1]/oagis:LineNumberID/text()",
					randomLine1, responseXML);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/csp:processCloseShipmentRequest/csp:salesOrder/oagis:SalesOrderLine[2]/oagis:LineNumberID/text()",
					randomLine2, responseXML);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/csp:processCloseShipmentRequest/csp:salesOrder/oagis:SalesOrderLine[1]/oagis:SupplierParty/oagis:ID/text()",
					randomVendorId1, responseXML);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/csp:processCloseShipmentRequest/csp:salesOrder/oagis:SalesOrderLine[2]/oagis:SupplierParty/oagis:ID/text()",
					randomVendorId2, responseXML);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/csp:processCloseShipmentRequest/csp:salesOrder/oagis:SalesOrderLine[1]/oagis:Item/oagis:Packaging[1]/oagis:ID/text()",
					randomCorrelationId + "." + randomLine1 + ".01", responseXML);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/csp:processCloseShipmentRequest/csp:salesOrder/oagis:SalesOrderLine[1]/oagis:Item/oagis:Packaging[2]/oagis:ID/text()",
					randomCorrelationId + "." + randomLine1 + ".02", responseXML);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/csp:processCloseShipmentRequest/csp:salesOrder/oagis:SalesOrderLine[2]/oagis:Item/oagis:Packaging[1]/oagis:ID/text()",
					randomCorrelationId + "." + randomLine2 + ".01", responseXML);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/csp:processCloseShipmentRequest/csp:salesOrder/oagis:SalesOrderLine[2]/oagis:Item/oagis:Packaging[2]/oagis:ID/text()",
					randomCorrelationId + "." + randomLine2 + ".02", responseXML);

		} catch (ServiceException e) {
			e.printStackTrace();
			String serviceExceptionXml = e.getXml();
			LOGGER.info("+++ServiceException =" + serviceExceptionXml);
			fail();
		}
	}

	@Test
	public void closeShipmentErrorTest() {

		final String requestXML = new ParameterReplacer(readClasspathFile("closeshipment/Request_closeShipment.xml"))
				.replace("CORRELATION_ID", randomCorrelationId).replace("LINE_1", randomLine1)
				.replace("LINE_2", randomLine2).replace("VENDOR_ID_1", randomVendorId1)
				.replace("VENDOR_ID_2", randomVendorId2).build();
		
		mockOsbBusinessService(PATH_CLOSESHIPTMENT_PROCESS, closeShipmentProcessErrorMock);
		mockOsbBusinessService(PATH_CLOSESHIPTMENT_ERR_Q, closeShipmentErrorQueueMock);
		LOGGER.info("+++invoke service with Error");
		try {
			invokeOsbProxyService(PATH_INVOKE_SERVICE, requestXML);
			fail();

		} catch (ServiceException e) {
			e.printStackTrace();
			String serviceExceptionXml = e.getXml();
			LOGGER.info("+++ServiceException =" + serviceExceptionXml);
			
			assertTrue(closeShipmentProcessErrorMock.hasBeenInvoked());
			assertTrue(closeShipmentErrorQueueMock.hasBeenInvoked());
			
			String responseXML = closeShipmentErrorQueueMock.getLastReceivedRequest();
			
			assertXpathEvaluatesTo("//exc:exception/exc:category/text()",
					"TriggerFault", responseXML);
			
			assertXpathEvaluatesTo("//exc:exception/exc:context/exc:transactionId/text()",
					randomCorrelationId, responseXML);
			
			assertXpathEvaluatesTo("//exc:exception/exc:context/exc:payload/tns:closeShipment/tns:header/mht:CorrelationID/text()",
					randomCorrelationId, responseXML);
			
			try {
				DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
				DocumentBuilder builder = factory.newDocumentBuilder();
				
				Document docResponseXML = builder.parse(( new InputSource( new StringReader( responseXML ) )));
				Document docRequestXML = builder.parse(( new InputSource( new StringReader( requestXML ) )));
				
				XPathFactory xPathfactory = XPathFactory.newInstance();
				XPath xpath = xPathfactory.newXPath();
				XPathExpression expr = xpath.compile("//*[local-name()='payload']/*");
							
				Node payload = (Node) expr.evaluate(docResponseXML, XPathConstants.NODE);

				assertThat("Request reader isIdenticalTo Exception payload header", 
						docRequestXML.getFirstChild().getFirstChild(), 
						CompareMatcher.isIdenticalTo(payload.getFirstChild()));
				assertThat("Request body isIdenticalTo Exception payload body", 
						docRequestXML.getFirstChild().getLastChild(), 
						CompareMatcher.isIdenticalTo(payload.getLastChild()));
				
			} catch (Exception xmlException) {
				xmlException.printStackTrace();
				fail();
			}
			
		}
	}
}
