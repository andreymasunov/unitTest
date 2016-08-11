package de.home24.middleware.vendortransmissionservice;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

public class CreatePurchaseOrderToCsvTest extends AbstractBaseSoaTest {

    private static final String RESOURCE_DIR = "../servicebus/VendorTransmission/VendorTransmissionService/";

    private Map<ExceptionAsserter.ExceptionAsserterKey, String> keysToExcpectedValuesForException = new HashMap<>();

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    @Before
    public void setUp() {
        declareXpathNS("oagis", "http://www.openapplications.org/oagis/10");
        declareXpathNS("ns3", "http://TargetNamespace.com/ftpReference");
        declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");

        keysToExcpectedValuesForException.put(ExceptionAsserter.ExceptionAsserterKey.SOURCE_SYSTEM_NAME, "Middleware");
        keysToExcpectedValuesForException.put(ExceptionAsserter.ExceptionAsserterKey.TRANSACTION_ID, "1231376876");
        keysToExcpectedValuesForException.put(ExceptionAsserter.ExceptionAsserterKey.PROCESS_LIBRARY_ID, "P1003");
        keysToExcpectedValuesForException.put(ExceptionAsserter.ExceptionAsserterKey.PAYLOAD_ELEMENT_NAME,
                "convertPurchaseOrderToCSVRequest");
        keysToExcpectedValuesForException.put(ExceptionAsserter.ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
        keysToExcpectedValuesForException.put(ExceptionAsserter.ExceptionAsserterKey.FAULT_CODE, "MW-10101");
        keysToExcpectedValuesForException.put(ExceptionAsserter.ExceptionAsserterKey.FAULT_MESSAGE,
                "Technical fault while converting the PO to CSV");
    }

    @Test
    public void whenOrderIsRegularDropshipOrderThenValuesForMutterSkuKulanzMangelAndUrsprungsbestellNrShouldBeEmpty()
            throws Exception {

        final DefaultSoapMockService putPoCsvToFtpSucessMockRef = new DefaultSoapMockService(
                Lists.newArrayList(new MockResponsePojo(ResponseType.SOAP_RESPONSE, "")));
        mockOsbBusinessService(
                "VendorTransmissionService/operations/convertPurchaseOrderToCSV/business-service/PutPOCsvToFtp",
                putPoCsvToFtpSucessMockRef);

        try {

            final String createPurchaseOrderCsv = readClasspathFile(String.format("%sconvertPurchaseOrderToCSVRequest/ConvertPurchaseOrderToCSVRequest.xml", RESOURCE_DIR));
            invokeOsbProxyService("VendorTransmissionService/exposed/v1/VendorTransmissionService",
                    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, createPurchaseOrderCsv));

            waitForInvocationOf(putPoCsvToFtpSucessMockRef);
            assertThat("Reference PutPoCsvToFtpMockRef has not been invoked!",
                    putPoCsvToFtpSucessMockRef.hasBeenInvoked(), is(true));

            String fileToFtp = putPoCsvToFtpSucessMockRef.getLastReceivedRequest();
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Empfaenger_GLN/text()", "\"4018848000003\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Sender_GLN/text()", "\"4260266310008\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Rechnungs_GLN/text()", "\"4260266310008\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Kaeufer_GLN/text()", "\"4260266310008\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Mutter_SKU/text()", "\"\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Kulanz_Mangel/text()", "\"\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:UrsprungsbestellNr/text()", "\"\"", fileToFtp);

        } catch (ServiceException e) {
            fail();
        }
    }

    @Test
    public void whenOrderIsSparepartOrderThenValuesForMutterSkuKulanzMangelAndUrsprungsbestellNrShouldBeFilled()
            throws Exception {

        final DefaultSoapMockService putPoCsvToFtpSucessMockRef = new DefaultSoapMockService(
                Lists.newArrayList(new MockResponsePojo(ResponseType.SOAP_RESPONSE, "")));
        mockOsbBusinessService(
                "VendorTransmissionService/operations/convertPurchaseOrderToCSV/business-service/PutPOCsvToFtp",
                putPoCsvToFtpSucessMockRef);

        try {

            final String createPurchaseOrderCsv = readClasspathFile(String.format("%sconvertPurchaseOrderToCSVRequest/ConvertSPPurchaseOrderToCSVRequest.xml", RESOURCE_DIR));
            invokeOsbProxyService("VendorTransmissionService/exposed/v1/VendorTransmissionService",
                    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, createPurchaseOrderCsv));

            waitForInvocationOf(putPoCsvToFtpSucessMockRef);
            assertThat("Reference PutPoCsvToFtpMockRef has not been invoked!",
                    putPoCsvToFtpSucessMockRef.hasBeenInvoked(), is(true));

            String fileToFtp = putPoCsvToFtpSucessMockRef.getLastReceivedRequest();
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Empfaenger_GLN/text()", "\"3467\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Sender_GLN/text()", "\"4260266310008\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Rechnungs_GLN/text()", "\"4260266310008\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Kaeufer_GLN/text()", "\"4260266310008\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Mutter_SKU/text()", "\"AM9868899\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:Kulanz_Mangel/text()", "\"Wandboard Drake - Kernnuss\"", fileToFtp);
            assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns3:PurchaseOrder/ns3:PurchaseOrderLine[1]/ns3:UrsprungsbestellNr/text()", "\"DS13690542\"", fileToFtp);

        } catch (ServiceException e) {
            fail();
        }
    }

    @Test
    public void whenWritingToFtpServerFailsThenReturnASoapFaultContainingAnExceptionWithCategoryTechnicalFault()
            throws Exception {

        final DefaultSoapMockService putPoCsvToFtpErrorMockRef = new DefaultSoapMockService(
                Lists.newArrayList(new MockResponsePojo(ResponseType.FAULT, "",
                        "Fault while transport PO CSV to Ftp!")));
        mockOsbBusinessService(
                "VendorTransmissionService/operations/convertPurchaseOrderToCSV/business-service/PutPOCsvToFtp",
                putPoCsvToFtpErrorMockRef);

        try {

            final String createPurchaseOrderCsv = readClasspathFile(
                    "convertPurchaseOrderToXmlRequest/ConvertPurchaseOrderToXmlRequest.xml");
            invokeOsbProxyService("VendorTransmissionService/exposed/v1/VendorTransmissionService",
                    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, createPurchaseOrderCsv));
            fail(String.format("Exception expected for this invocation of purchaseOrderToCSV operation"));
        } catch (ServiceException e) {

            assertThat("Reference PutPoCsvToFtpMockRef has not been invoked!",
                    putPoCsvToFtpErrorMockRef.hasBeenInvoked(), is(true));

            exceptionAsserter.assertException(e.getXml(), keysToExcpectedValuesForException);
        }
    }
}
