package de.home24.middleware.attachmentservice;

import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class GenerateZipPackageTest extends AbstractBaseSoaTest {

    private final static String PATH_ATTACHMENT_SERVICE = "AttachmentService/exposed/v1/AttachmentService";
    private final static String PATH_FTP_WRITE = "AttachmentService/shared/business-service/home24FtpWrite";
    private final static String PATH_FTP_READ = "AttachmentService/shared/business-service/home24FtpRead";

    private String randomCorrelationId = "";

    private String zipPackageReference = "";
    private final static String FILE_REFERENCE_1 = "/cfiler-fs/erpdev/EDI_Outbound/711106/1601/purchaseOrderXMLReference.xml";
    private final static String FILE_REFERENCE_2 = "/cfiler-fs/erpdev/EDI_Outbound/711106/1601/deliveryNoteReference.txt";
    private final static String FILE_REFERENCE_3 = "/cfiler-fs/erpdev/EDI_Outbound/711106/1601/DHL_Lables.pdf";

    private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
    private static final String REPLACE_PARAM_ZIP_PACKAGE_REFERENCE = "ZIP_PACKAGE_REFERENCE";
    private static final String REPLACE_PARAM_FILE_REFERENCE_1 = "FILE_REFERENCE_1";
    private static final String REPLACE_PARAM_FILE_REFERENCE_2 = "FILE_REFERENCE_2";
    private static final String REPLACE_PARAM_FILE_REFERENCE_3 = "FILE_REFERENCE_3";

    private static final Logger LOGGER = Logger.getLogger(MergePDFsTest.class.getSimpleName());

    private DefaultSoapMockService home24FtpReadSuccessMock;
    private List<MockResponsePojo> home24FtpReadSuccessMockPojoList = new ArrayList<MockResponsePojo>();
    private DefaultSoapMockService home24FtpWriteSuccessMock;
    private DefaultSoapMockService home24FtpReadFaultMock;
    private List<MockResponsePojo> home24FtpReadFaultMockPojoList = new ArrayList<MockResponsePojo>();
    private DefaultSoapMockService home24FtpWriteFaultMock;
    private List<MockResponsePojo> home24FtpWriteFaultMockPojoList = new ArrayList<MockResponsePojo>();

    @BeforeClass
    public static void setUpBeforeClass() {

	testInitialization();
    }

    @Before
    public void setUp() {

	declareXpathNS("ns1", "http://home24.de/interfaces/bas/attachment/attachmentservicemessages/v1");
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
	declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");

	Random randomNumber = new Random();
	randomCorrelationId = "DS" + String.valueOf(randomNumber.nextInt(1000000));
	zipPackageReference = "/cfiler-fs/erpdev/EDI_Outbound/711106/1601/" + randomCorrelationId
		+ "_Package.zip";

	LOGGER.info("+++Create Mocks+++");
	home24FtpReadSuccessMock = new DefaultSoapMockService(home24FtpReadSuccessMockPojoList);
	home24FtpReadSuccessMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE,
		readClasspathFile("generatezippackage/base64TextFileResponse.xml")));
	home24FtpReadSuccessMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE,
		readClasspathFile("generatezippackage/base64TextFileResponse.xml")));
	home24FtpReadSuccessMockPojoList.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE,
		readClasspathFile("generatezippackage/base64PDFResponse.xml")));

	home24FtpWriteSuccessMock = new DefaultSoapMockService("");

	home24FtpWriteFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
	home24FtpWriteFaultMock = new DefaultSoapMockService(home24FtpWriteFaultMockPojoList);

	home24FtpReadFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
	home24FtpReadFaultMock = new DefaultSoapMockService(home24FtpReadFaultMockPojoList);

    }

    @After
    public void tearDown() {
	LOGGER.info("+++Delete Mocks+++");
	home24FtpWriteSuccessMock = null;
	home24FtpReadSuccessMock = null;
	home24FtpWriteFaultMock = null;
	home24FtpReadFaultMock = null;
    }

    /**
     * Invoke with success
     */
    @Test
    public void generateZipPackageSucces() {

	final String requestXML = new ParameterReplacer(
		readClasspathFile("generatezippackage/generateZipPackageRequest.xml"))
			.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
			.replace(REPLACE_PARAM_ZIP_PACKAGE_REFERENCE, zipPackageReference)
			.replace(REPLACE_PARAM_FILE_REFERENCE_1, FILE_REFERENCE_1)
			.replace(REPLACE_PARAM_FILE_REFERENCE_2, FILE_REFERENCE_2)
			.replace(REPLACE_PARAM_FILE_REFERENCE_3, FILE_REFERENCE_3).build();

	mockOsbBusinessService(PATH_FTP_READ, home24FtpReadSuccessMock);
	mockOsbBusinessService(PATH_FTP_WRITE, home24FtpWriteSuccessMock);

	LOGGER.info("+++generate Zip Package Success");
	String invocationResult = invokeOsbProxyService(PATH_ATTACHMENT_SERVICE,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));
	LOGGER.info("+++invocation Result: " + invocationResult);

	assertXpathEvaluatesTo(
		"//soapenv:Envelope/soapenv:Body/ns1:generateZipPackageResponse/ns1:responseHeader/mht:CorrelationID/text()",
		randomCorrelationId, invocationResult);
	assertXpathEvaluatesTo(
		"//soapenv:Envelope/soapenv:Body/ns1:generateZipPackageResponse/ns1:zipPackageReference/text()",
		zipPackageReference, invocationResult);
    }

    /**
     * Invoke with success, without label reference
     */
    @Test
    public void generateZipWithoutLabelPackageSucces() {

	final String requestXML = new ParameterReplacer(
		readClasspathFile("generatezippackage/generateZipPackageRequest.xml"))
			.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
			.replace(REPLACE_PARAM_ZIP_PACKAGE_REFERENCE, zipPackageReference)
			.replace(REPLACE_PARAM_FILE_REFERENCE_1, FILE_REFERENCE_1)
			.replace(REPLACE_PARAM_FILE_REFERENCE_2, FILE_REFERENCE_2)
			.replace(REPLACE_PARAM_FILE_REFERENCE_3, "").build();

	mockOsbBusinessService(PATH_FTP_READ, home24FtpReadSuccessMock);
	mockOsbBusinessService(PATH_FTP_WRITE, home24FtpWriteSuccessMock);

	LOGGER.info("+++Invoke with success, without label reference");
	String invocationResult = invokeOsbProxyService(PATH_ATTACHMENT_SERVICE,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));
	LOGGER.info("+++invocation Result: " + invocationResult);

	assertXpathEvaluatesTo(
		"//soapenv:Envelope/soapenv:Body/ns1:generateZipPackageResponse/ns1:responseHeader/mht:CorrelationID/text()",
		randomCorrelationId, invocationResult);
	assertXpathEvaluatesTo(
		"//soapenv:Envelope/soapenv:Body/ns1:generateZipPackageResponse/ns1:zipPackageReference/text()",
		zipPackageReference, invocationResult);
    }

    /**
     * Invoke with TechnicalFault
     */
    @Test
    public void generateZipPackageTechnicalFault() {

	final String requestXML = new ParameterReplacer(
		readClasspathFile("generatezippackage/generateZipPackageRequest.xml"))
			.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
			.replace(REPLACE_PARAM_ZIP_PACKAGE_REFERENCE, zipPackageReference)
			.replace(REPLACE_PARAM_FILE_REFERENCE_1, "")
			.replace(REPLACE_PARAM_FILE_REFERENCE_2, "")
			.replace(REPLACE_PARAM_FILE_REFERENCE_3, "").build();

	String response = "";

	mockOsbBusinessService(PATH_FTP_READ, home24FtpReadFaultMock);
	mockOsbBusinessService(PATH_FTP_WRITE, home24FtpWriteFaultMock);

	LOGGER.info("+++generate Zip Package Technical Fault");
	try {
	    response = invokeOsbProxyService(PATH_ATTACHMENT_SERVICE,
		    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

	    LOGGER.info("+++invocation Result: " + response);
	    fail();

	} catch (ServiceException e) {
	    LOGGER.log(Level.SEVERE, "+++ServiceException =", e);
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
		    "TechnicalFault", e.getXml());
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:transactionId/text()",
		    randomCorrelationId, e.getXml());
	}
    }

}
