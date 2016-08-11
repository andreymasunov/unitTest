package de.home24.middleware.attachmentservice;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Stopwatch;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.HttpResponseWrapper;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class MergePDFsTest extends AbstractBaseSoaTest {

    private final static String PATH_ATTACHMENT_SERVICE = "AttachmentService/exposed/v1/AttachmentService";
    private final static String PATH_FTP_WRITE = "AttachmentService/shared/business-service/home24FtpWrite";
    private final static String PATH_FTP_READ = "AttachmentService/shared/business-service/home24FtpRead";
    private final static String PATH_ATTACHMENTSERVICE_CALLBACK = "AttachmentService/shared/business-service/AttachmentServiceCallback";

    private String randomCorrelationId = "";

    private String mergedPdfReference = "";
    private final static String PDF_REFERENCE_1 = "/cfiler-fs/erpdev/EDI_Outbound/711106/1512/DS13690541_M-L2-000105_01.pdf";
    private final static String PDF_REFERENCE_2 = "/cfiler-fs/erpdev/EDI_Outbound/711106/1512/DS13690541_M-L2-000105_02.pdf";

    private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
    private static final String REPLACE_PARAM_MERGED_PDF_REFERENCE = "MERGED_PDF_REFERENCE";
    private static final String REPLACE_PARAM_PDF_REFERENCE_1 = "PDF_REFERENCE_1";
    private static final String REPLACE_PARAM_PDF_REFERENCE_2 = "PDF_REFERENCE_2";

    private static final Logger LOGGER = Logger.getLogger(MergePDFsTest.class.getSimpleName());

    private DefaultSoapMockService home24FtpReadSuccessMock;
    private DefaultSoapMockService home24FtpWriteSuccessMock;
    private DefaultSoapMockService home24FtpReadFaultMock;
    private List<MockResponsePojo> home24FtpReadFaultMockPojoList = new ArrayList<MockResponsePojo>();
    private DefaultSoapMockService home24FtpWriteFaultMock;
    private List<MockResponsePojo> home24FtpWriteFaultMockPojoList = new ArrayList<MockResponsePojo>();

    private DefaultSoapMockService attachmentServiceCallbackMock;

    private String messageId;

    @Before
    public void setUp() {

	declareXpathNS("ns1", "http://home24.de/interfaces/bas/attachment/attachmentservicemessages/v1");
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
	declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS("wsa", "http://www.w3.org/2005/08/addressing");

	messageId = UUID.randomUUID().toString();

	Random randomNumber = new Random();
	randomCorrelationId = "DS" + String.valueOf(randomNumber.nextInt(1000000));
	mergedPdfReference = "/cfiler-fs/erpdev/EDI_Outbound/711106/1512/DHL_Lables_" + randomCorrelationId
		+ ".pdf";

	LOGGER.info("+++Create Mocks+++");
	attachmentServiceCallbackMock = new DefaultSoapMockService("");
	home24FtpReadSuccessMock = new DefaultSoapMockService(
		readClasspathFile("mergepdfs/base64PDFResponse.xml"));
	home24FtpWriteSuccessMock = new DefaultSoapMockService("");

	home24FtpWriteFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
	home24FtpWriteFaultMock = new DefaultSoapMockService(home24FtpWriteFaultMockPojoList);

	home24FtpReadFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
	home24FtpReadFaultMock = new DefaultSoapMockService(home24FtpReadFaultMockPojoList);

    }

    /**
     * Invoke with success
     */
    @Test
    public void mergePDFsSucces() {

	final String requestXML = new ParameterReplacer(readClasspathFile("mergepdfs/merge2PDFsRequest.xml"))
		.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
		.replace(REPLACE_PARAM_MERGED_PDF_REFERENCE, mergedPdfReference)
		.replace(REPLACE_PARAM_PDF_REFERENCE_1, PDF_REFERENCE_1)
		.replace(REPLACE_PARAM_PDF_REFERENCE_2, PDF_REFERENCE_2).build();

	mockOsbBusinessService(PATH_FTP_READ, home24FtpReadSuccessMock);
	mockOsbBusinessService(PATH_FTP_WRITE, home24FtpWriteSuccessMock);
	final String attachmentServiceCallbackUrl = mockOsbBusinessService(PATH_ATTACHMENTSERVICE_CALLBACK,
		attachmentServiceCallbackMock);

	try {
	    LOGGER.info("+++invoke Service with Success");

	    final HttpResponseWrapper httpResponseWrapper = invokeSbSoapProxy(
		    String.format("http://%s:%s/%s", getConfig().getOsbServerConfig().getServiceHost(),
			    getConfig().getOsbServerConfig().getServicePort(), PATH_ATTACHMENT_SERVICE),
		    SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
			    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML),
			    SoapUtil.getInstance().messageIdHeader(messageId),
			    SoapUtil.getInstance().relatesToHeader(messageId),
			    SoapUtil.getInstance().replyToHeader(attachmentServiceCallbackUrl)),
		    true);

	    assertThat("Service invocation took longer than 1 second!",
		    httpResponseWrapper.getProcessingDuration(), lessThanOrEqualTo(1000l));

	    waitForInvocationOf(attachmentServiceCallbackMock);

	    String callback = attachmentServiceCallbackMock.getLastReceivedRequest();
	    assertTrue(attachmentServiceCallbackMock.hasBeenInvoked());
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/ns1:mergePDFsResponse/ns1:responseHeader/mht:CorrelationID/text()",
		    randomCorrelationId, callback);
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/ns1:mergePDFsResponse/ns1:mergedPDFReference/text()",
		    mergedPdfReference, callback);

	    LOGGER.info(String.format("+++invocation Result: %s. Invocation takes %s ms...",
		    httpResponseWrapper.getHttpResponse(), httpResponseWrapper.getProcessingDuration()));

	} catch (ServiceException e) {
	    e.printStackTrace();
	    String serviceExceptionXml = e.getXml();
	    LOGGER.info("+++ServiceException =" + serviceExceptionXml);
	    fail();
	}
    }

    /**
     * Merge 50 pfds with success
     */
    @Test
    public void merge50PDFsSucces() {

	final String requestXML = new ParameterReplacer(readClasspathFile("mergepdfs/merge50PDFsRequest.xml"))
		.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
		.replace(REPLACE_PARAM_MERGED_PDF_REFERENCE, mergedPdfReference)
		.replace(REPLACE_PARAM_PDF_REFERENCE_1, PDF_REFERENCE_1)
		.replace(REPLACE_PARAM_PDF_REFERENCE_2, PDF_REFERENCE_2).build();

	mockOsbBusinessService(PATH_FTP_READ, home24FtpReadSuccessMock);
	mockOsbBusinessService(PATH_FTP_WRITE, home24FtpWriteSuccessMock);
	final String attachmentServiceCallbackUrl = mockOsbBusinessService(PATH_ATTACHMENTSERVICE_CALLBACK,
		attachmentServiceCallbackMock);

	final Stopwatch stopwatch = Stopwatch.createStarted();
	String invocationResult = null;

	try {
	    LOGGER.info("+++Merge 50 pfds with success");

	    final HttpResponseWrapper httpResponseWrapper = invokeSbSoapProxy(
		    String.format("http://%s:%s/%s", getConfig().getOsbServerConfig().getServiceHost(),
			    getConfig().getOsbServerConfig().getServicePort(), PATH_ATTACHMENT_SERVICE),
		    SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
			    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML),
			    SoapUtil.getInstance().messageIdHeader(messageId),
			    SoapUtil.getInstance().relatesToHeader(messageId),
			    SoapUtil.getInstance().replyToHeader(attachmentServiceCallbackUrl)),
		    true);

	    waitForInvocationOf(attachmentServiceCallbackMock, 180);

	    assertThat("Service invocation took longer than 1 second!",
		    httpResponseWrapper.getProcessingDuration(), lessThanOrEqualTo(1000l));

	    String callback = attachmentServiceCallbackMock.getLastReceivedRequest();
	    assertTrue(attachmentServiceCallbackMock.hasBeenInvoked());
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/ns1:mergePDFsResponse/ns1:responseHeader/mht:CorrelationID/text()",
		    randomCorrelationId, callback);
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/ns1:mergePDFsResponse/ns1:mergedPDFReference/text()",
		    mergedPdfReference, callback);

	    LOGGER.info(String.format("+++invocation Result: %s. Invocation takes %s ms...",
		    httpResponseWrapper.getHttpResponse(), httpResponseWrapper.getProcessingDuration()));
	} catch (ServiceException e) {
	    e.printStackTrace();
	    String serviceExceptionXml = e.getXml();
	    LOGGER.info("+++ServiceException =" + serviceExceptionXml);
	    fail();
	}

	LOGGER.info(String.format("+++invocation Result: %s. Invocation takes %s s...", invocationResult,
		stopwatch.elapsed(TimeUnit.SECONDS)));
    }

    /**
     * Invoke with TechnicalFault
     */
    @Test
    public void mergePDFsTechnicalError() {

	final String requestXML = new ParameterReplacer(readClasspathFile("mergepdfs/merge2PDFsRequest.xml"))
		.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
		.replace(REPLACE_PARAM_MERGED_PDF_REFERENCE, "").replace(REPLACE_PARAM_PDF_REFERENCE_1, "")
		.replace(REPLACE_PARAM_PDF_REFERENCE_2, "").build();

	mockOsbBusinessService(PATH_FTP_READ, home24FtpReadFaultMock);
	mockOsbBusinessService(PATH_FTP_WRITE, home24FtpWriteFaultMock);
	final String attachmentServiceCallbackUrl = mockOsbBusinessService(PATH_ATTACHMENTSERVICE_CALLBACK,
		attachmentServiceCallbackMock);

	try {
	    LOGGER.info("+++Invoke with TechnicalFault");

	    final HttpResponseWrapper httpResponseWrapper = invokeSbSoapProxy(
		    String.format("http://%s:%s/%s", getConfig().getOsbServerConfig().getServiceHost(),
			    getConfig().getOsbServerConfig().getServicePort(), PATH_ATTACHMENT_SERVICE),
		    SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
			    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML),
			    SoapUtil.getInstance().messageIdHeader(messageId),
			    SoapUtil.getInstance().relatesToHeader(messageId),
			    SoapUtil.getInstance().replyToHeader(attachmentServiceCallbackUrl)),
		    true);

	    assertThat("Service invocation took longer than 1 second!",
		    httpResponseWrapper.getProcessingDuration(), lessThanOrEqualTo(1000l));

	    waitForInvocationOf(attachmentServiceCallbackMock);

	    String callback = attachmentServiceCallbackMock.getLastReceivedRequest();

	    LOGGER.info("+++Invocation callback =" + callback);

	    assertTrue(attachmentServiceCallbackMock.hasBeenInvoked());

	    assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/exc:exception/exc:category/text()",
		    "TechnicalFault", callback);
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/exc:exception/exc:context/exc:transactionId/text()",
		    randomCorrelationId, callback);

	    LOGGER.info(String.format("+++invocation Result: %s. Invocation takes %s ms...",
		    httpResponseWrapper.getHttpResponse(), httpResponseWrapper.getProcessingDuration()));

	} catch (ServiceException e) {
	    e.printStackTrace();
	    String serviceExceptionXml = e.getXml();
	    LOGGER.info("+++ServiceException =" + serviceExceptionXml);
	    fail();
	}
    }

    @Test
    public void whenWsaHeaderInformationContainedinRequestThenForwardToInternalOperationsHttpProxy() {

	LOGGER.info("+++when Wsa Header Information Contained in Request Then Forward To Internal Operations HttpProxy");
	DefaultSoapMockService createLabelAndShippingInstructionsInternalRef = new DefaultSoapMockService();
	mockOsbBusinessService("AttachmentService/operations/mergePDFs/business-service/MergePdfsInternalRef",
		createLabelAndShippingInstructionsInternalRef);

	final String messageId = UUID.randomUUID().toString();
	final String replyToAddress = "http://localhost:7101/service/callbackRef";

	final String requestXML = new ParameterReplacer(readClasspathFile("mergepdfs/merge2PDFsRequest.xml"))
		.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
		.replace(REPLACE_PARAM_MERGED_PDF_REFERENCE, mergedPdfReference)
		.replace(REPLACE_PARAM_PDF_REFERENCE_1, PDF_REFERENCE_1)
		.replace(REPLACE_PARAM_PDF_REFERENCE_2, PDF_REFERENCE_2).build();

	final HttpResponseWrapper httpResponseWrapper = invokeSbSoapProxy(
		String.format("http://%s:%s/%s", getConfig().getOsbServerConfig().getServiceHost(),
			getConfig().getOsbServerConfig().getServicePort(), PATH_ATTACHMENT_SERVICE),
		SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
			SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML),
			SoapUtil.getInstance().messageIdHeader(messageId),
			SoapUtil.getInstance().relatesToHeader(messageId),
			SoapUtil.getInstance().replyToHeader(replyToAddress)),
		true);

	waitForInvocationOf(createLabelAndShippingInstructionsInternalRef);

	assertThat("Service invocation took longer than 2 second!",
		httpResponseWrapper.getProcessingDuration(), lessThanOrEqualTo(2000l));

	assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Header/wsa:RelatesTo/text()", messageId,
		createLabelAndShippingInstructionsInternalRef.getLastReceivedRequest());
	assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Header/wsa:ReplyTo/wsa:Address/text()",
		replyToAddress, createLabelAndShippingInstructionsInternalRef.getLastReceivedRequest());
    }

}
