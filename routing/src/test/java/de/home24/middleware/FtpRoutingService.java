package de.home24.middleware;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.queueingservice.QueueingServiceTest;

public class FtpRoutingService extends AbstractBaseSoaTest {

	private final static String PATH_SERVICE = "FtpRoutingService/exposed/v1/FtpRoutingService";

	private final static String PATH_EDI_FTP_READ = "FtpRoutingService/shared/business-service/ediFtpRead";
	private final static String PATH_EDI_FTP_WRITE = "FtpRoutingService/shared/business-service/ediFtpWrite";
	private final static String PATH_EDI_FTP_DELETE = "FtpRoutingService/shared/business-service/ediFtpDelete";

	private final static String PATH_TRACKING_FTP_READ = "FtpRoutingService/shared/business-service/trackingFtpRead";
	private final static String PATH_TRACKING_FTP_WRITE = "FtpRoutingService/shared/business-service/trackingFtpWrite";
	private final static String PATH_TRACKING_FTP_DELETE = "FtpRoutingService/shared/business-service/trackingFtpDelete";

	private final static String PATH_METAPACK_FTP_READ = "FtpRoutingService/shared/business-service/metapackFtpRead";
	private final static String PATH_METAPACK_FTP_WRITE = "FtpRoutingService/shared/business-service/metapackFtpWrite";
	private final static String PATH_METAPACK_FTP_DELETE = "FtpRoutingService/shared/business-service/metapackFtpDelete";

	private DefaultSoapMockService ediReadSuccesMock;
	private DefaultSoapMockService ediWriteSuccesMock;
	private DefaultSoapMockService ediDeleteSuccesMock;
	

	private DefaultSoapMockService trackingReadSuccesMock;
	private DefaultSoapMockService trackingWriteSuccesMock;
	private DefaultSoapMockService trackingDeleteSuccesMock;

	private DefaultSoapMockService metapackReadSuccesMock;
	private DefaultSoapMockService metapackWriteSuccesMock;
	private DefaultSoapMockService metapackDeleteSuccesMock;

	private DefaultSoapMockService faultMock;
	private List<MockResponsePojo> faultMockPojoList = new ArrayList<MockResponsePojo>();

	String randomCorrelationId, ftpFileReadOutout = "";

	private final static Logger LOGGER = LoggerFactory.getLogger(QueueingServiceTest.class);

	@Before
	public void setUp() {

		Random randomNumber = new Random();
		randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));
		ftpFileReadOutout = readClasspathFile("servicebus/Routing/FtpRoutingService/ftpFileReadOutout.xml");

		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("ftprs", "http://home24.de/interfaces/bes/ftproutingservice/ftproutingservicemessages/v1");

		LOGGER.info("+++Create Mocks+++");
		ediReadSuccesMock = new DefaultSoapMockService(ftpFileReadOutout);
		ediWriteSuccesMock = new DefaultSoapMockService("");
		ediDeleteSuccesMock = new DefaultSoapMockService(ftpFileReadOutout);

		trackingReadSuccesMock = new DefaultSoapMockService(ftpFileReadOutout);
		trackingWriteSuccesMock = new DefaultSoapMockService("");
		trackingDeleteSuccesMock = new DefaultSoapMockService(ftpFileReadOutout);

		metapackReadSuccesMock = new DefaultSoapMockService(ftpFileReadOutout);
		metapackWriteSuccesMock = new DefaultSoapMockService("");
		metapackDeleteSuccesMock = new DefaultSoapMockService(ftpFileReadOutout);

		faultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
		faultMock = new DefaultSoapMockService(faultMockPojoList);

	}

	@After
	public void tearDown() {
		LOGGER.info("+++Delete Mocks+++");
		ediReadSuccesMock = null;
		ediWriteSuccesMock = null;
		ediDeleteSuccesMock = null;
		trackingReadSuccesMock = null;
		trackingWriteSuccesMock = null;
		trackingDeleteSuccesMock = null;
		metapackReadSuccesMock = null;
		metapackWriteSuccesMock = null;
		metapackDeleteSuccesMock = null;
		faultMock = null;
	}

	@Test
	public void happyPathEdi() {
		
		mockOsbBusinessService(PATH_EDI_FTP_READ, ediReadSuccesMock);
		mockOsbBusinessService(PATH_EDI_FTP_WRITE, ediWriteSuccesMock);
		mockOsbBusinessService(PATH_EDI_FTP_DELETE, ediDeleteSuccesMock);
		
		mockOsbBusinessService(PATH_TRACKING_FTP_READ, trackingReadSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_WRITE, trackingWriteSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_DELETE, trackingDeleteSuccesMock);

		mockOsbBusinessService(PATH_METAPACK_FTP_READ, metapackReadSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_WRITE, metapackWriteSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_DELETE, metapackDeleteSuccesMock);
		
		String requesXML = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				new ParameterReplacer(
						readClasspathFile("servicebus/Routing/FtpRoutingService/moveFileRequest01.xml"))
				.replace("CORRELATION_ID", randomCorrelationId)
				.replace("CONNECTION", "edi").build());
		try {

			String serviceResponse = invokeOsbProxyService(PATH_SERVICE, requesXML);

			assertThat("ediReadSuccesMock is invoked.", ediReadSuccesMock.getNumberOfInvocations() == 1);
			assertThat("ediWriteSuccesMock is invoked.", ediWriteSuccesMock.getNumberOfInvocations() == 1);
			assertThat("ediDeleteSuccesMock is invoked.", ediDeleteSuccesMock.getNumberOfInvocations() == 1);

			assertThat("trackingReadSuccesMock is not invoked.", trackingReadSuccesMock.getNumberOfInvocations() == 0);
			assertThat("trackingWriteSuccesMock is not invoked.", trackingWriteSuccesMock.getNumberOfInvocations() == 0);
			assertThat("trackingDeleteSuccesMock is not invoked.", trackingDeleteSuccesMock.getNumberOfInvocations() == 0);

			assertThat("metapackReadSuccesMock is not invoked.", metapackReadSuccesMock.getNumberOfInvocations() == 0);
			assertThat("metapackWriteSuccesMock is not invoked.", metapackWriteSuccesMock.getNumberOfInvocations() == 0);
			assertThat("metapackDeleteSuccesMock is not invoked.", metapackDeleteSuccesMock.getNumberOfInvocations() == 0);

			assertXmlEquals(SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, ftpFileReadOutout),
					ediWriteSuccesMock.getLastReceivedRequest());

			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileResponse/ftprs:responseHeader/mht:CorrelationID/text()",
					randomCorrelationId, serviceResponse);

		} catch (ServiceException e) {
			fail();
		}

	}

	@Test
	public void happyPathTracking() {
		
		mockOsbBusinessService(PATH_EDI_FTP_READ, ediReadSuccesMock);
		mockOsbBusinessService(PATH_EDI_FTP_WRITE, ediWriteSuccesMock);
		mockOsbBusinessService(PATH_EDI_FTP_DELETE, ediDeleteSuccesMock);
		
		mockOsbBusinessService(PATH_TRACKING_FTP_READ, trackingReadSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_WRITE, trackingWriteSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_DELETE, trackingDeleteSuccesMock);

		mockOsbBusinessService(PATH_METAPACK_FTP_READ, metapackReadSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_WRITE, metapackWriteSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_DELETE, metapackDeleteSuccesMock);
		
		String requesXML = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				new ParameterReplacer(
						readClasspathFile("servicebus/Routing/FtpRoutingService/moveFileRequest01.xml"))
				.replace("CORRELATION_ID", randomCorrelationId)
				.replace("CONNECTION", "tracking").build());
		try {

			String serviceResponse = invokeOsbProxyService(PATH_SERVICE, requesXML);

			assertThat("ediReadSuccesMock is invoked.", ediReadSuccesMock.getNumberOfInvocations() == 0);
			assertThat("ediWriteSuccesMock is invoked.", ediWriteSuccesMock.getNumberOfInvocations() == 0);
			assertThat("ediDeleteSuccesMock is invoked.", ediDeleteSuccesMock.getNumberOfInvocations() == 0);

			assertThat("trackingReadSuccesMock is not invoked.", trackingReadSuccesMock.getNumberOfInvocations() == 1);
			assertThat("trackingWriteSuccesMock is not invoked.", trackingWriteSuccesMock.getNumberOfInvocations() == 1);
			assertThat("trackingDeleteSuccesMock is not invoked.", trackingDeleteSuccesMock.getNumberOfInvocations() == 1);

			assertThat("metapackReadSuccesMock is not invoked.", metapackReadSuccesMock.getNumberOfInvocations() == 0);
			assertThat("metapackWriteSuccesMock is not invoked.", metapackWriteSuccesMock.getNumberOfInvocations() == 0);
			assertThat("metapackDeleteSuccesMock is not invoked.", metapackDeleteSuccesMock.getNumberOfInvocations() == 0);

			assertXmlEquals(SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, ftpFileReadOutout),
					trackingWriteSuccesMock.getLastReceivedRequest());

			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileResponse/ftprs:responseHeader/mht:CorrelationID/text()",
					randomCorrelationId, serviceResponse);

		} catch (ServiceException e) {
			fail();
		}

	}

	@Test
	public void happyPathMetapack() {
		
		mockOsbBusinessService(PATH_EDI_FTP_READ, ediReadSuccesMock);
		mockOsbBusinessService(PATH_EDI_FTP_WRITE, ediWriteSuccesMock);
		mockOsbBusinessService(PATH_EDI_FTP_DELETE, ediDeleteSuccesMock);
		
		mockOsbBusinessService(PATH_TRACKING_FTP_READ, trackingReadSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_WRITE, trackingWriteSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_DELETE, trackingDeleteSuccesMock);

		mockOsbBusinessService(PATH_METAPACK_FTP_READ, metapackReadSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_WRITE, metapackWriteSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_DELETE, metapackDeleteSuccesMock);
		
		String requesXML = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				new ParameterReplacer(
						readClasspathFile("servicebus/Routing/FtpRoutingService/moveFileRequest01.xml"))
				.replace("CORRELATION_ID", randomCorrelationId)
				.replace("CONNECTION", "metapack").build());
		try {

			String serviceResponse = invokeOsbProxyService(PATH_SERVICE, requesXML);


			assertThat("ediReadSuccesMock is invoked.", ediReadSuccesMock.getNumberOfInvocations() == 0);
			assertThat("ediWriteSuccesMock is invoked.", ediWriteSuccesMock.getNumberOfInvocations() == 0);
			assertThat("ediDeleteSuccesMock is invoked.", ediDeleteSuccesMock.getNumberOfInvocations() == 0);

			assertThat("trackingReadSuccesMock is not invoked.", trackingReadSuccesMock.getNumberOfInvocations() == 0);
			assertThat("trackingWriteSuccesMock is not invoked.", trackingWriteSuccesMock.getNumberOfInvocations() == 0);
			assertThat("trackingDeleteSuccesMock is not invoked.", trackingDeleteSuccesMock.getNumberOfInvocations() == 0);

			assertThat("metapackReadSuccesMock is not invoked.", metapackReadSuccesMock.getNumberOfInvocations() == 1);
			assertThat("metapackWriteSuccesMock is not invoked.", metapackWriteSuccesMock.getNumberOfInvocations() == 1);
			assertThat("metapackDeleteSuccesMock is not invoked.", metapackDeleteSuccesMock.getNumberOfInvocations() == 1);

			assertXmlEquals(SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, ftpFileReadOutout),
					metapackWriteSuccesMock.getLastReceivedRequest());

			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileResponse/ftprs:responseHeader/mht:CorrelationID/text()",
					randomCorrelationId, serviceResponse);

		} catch (ServiceException e) {
			fail();
		}

	}

	/**
	 * Invoke with TechnicalFault
	 */
	@Test
	public void technicalFaultTest() {

		String serviceResponse;

		mockOsbBusinessService(PATH_EDI_FTP_READ, ediReadSuccesMock);
		mockOsbBusinessService(PATH_EDI_FTP_WRITE, faultMock);

		mockOsbBusinessService(PATH_TRACKING_FTP_READ, trackingReadSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_WRITE, trackingWriteSuccesMock);

		mockOsbBusinessService(PATH_METAPACK_FTP_READ, metapackReadSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_WRITE, metapackWriteSuccesMock);
		
		String requesXML = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				new ParameterReplacer(
						readClasspathFile("servicebus/Routing/FtpRoutingService/moveFileRequest01.xml"))
				.replace("CORRELATION_ID", randomCorrelationId)
				.replace("CONNECTION", "edi").build());
		try {
			serviceResponse = invokeOsbProxyService(PATH_SERVICE, requesXML);
			LOGGER.info("+++serviceResponse =" + serviceResponse);
			fail();
		} catch (ServiceException e) {
			e.printStackTrace();

			String exceptionXml = e.getXml();
			LOGGER.info("+++exceptionXml =" + exceptionXml);

			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
					"TechnicalFault", exceptionXml);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:payload/"
					+ "ftprs:moveFileRequest/ftprs:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, exceptionXml);
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:payload/"
					+ "ftprs:moveFileRequest/ftprs:connection/text()", "edi", exceptionXml);
		}
	}

	/**
	 * Invoke with Invalid Connection
	 */
	@Test
	public void businessFaultInvalidConnectionTest() {

		String serviceResponse;

		mockOsbBusinessService(PATH_EDI_FTP_READ, ediReadSuccesMock);
		mockOsbBusinessService(PATH_EDI_FTP_WRITE, faultMock);
		mockOsbBusinessService(PATH_EDI_FTP_DELETE, ediDeleteSuccesMock);
		
		mockOsbBusinessService(PATH_TRACKING_FTP_READ, trackingReadSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_WRITE, trackingWriteSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_DELETE, trackingDeleteSuccesMock);

		mockOsbBusinessService(PATH_METAPACK_FTP_READ, metapackReadSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_WRITE, metapackWriteSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_DELETE, metapackDeleteSuccesMock);
		
		String requesXML = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				new ParameterReplacer(
						readClasspathFile("servicebus/Routing/FtpRoutingService/moveFileRequest01.xml"))
				.replace("CORRELATION_ID", randomCorrelationId)
				.replace("CONNECTION", "?").build());
		try {
			serviceResponse = invokeOsbProxyService(PATH_SERVICE, requesXML);
			LOGGER.info("+++serviceResponse =" + serviceResponse);
			fail();
		} catch (ServiceException e) {
			e.printStackTrace();

			String exceptionXml = e.getXml();
			LOGGER.info("+++exceptionXml =" + exceptionXml);

			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
					"BusinessFault", exceptionXml);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:faultInfo/exc:faultCode/text()",
					"MW-30002", exceptionXml);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:payload/"
					+ "ftprs:moveFileRequest/ftprs:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, exceptionXml);
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:payload/"
					+ "ftprs:moveFileRequest/ftprs:connection/text()", "?", exceptionXml);
		}
	}

	/**
	 * Invoke with Invalid SourceFile
	 */
	@Test
	public void businessFaultInvalidSourceFileTest() {

		String serviceResponse;

		mockOsbBusinessService(PATH_EDI_FTP_READ, ediReadSuccesMock);
		mockOsbBusinessService(PATH_EDI_FTP_WRITE, faultMock);

		mockOsbBusinessService(PATH_TRACKING_FTP_READ, trackingReadSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_WRITE, trackingWriteSuccesMock);

		mockOsbBusinessService(PATH_METAPACK_FTP_READ, metapackReadSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_WRITE, metapackWriteSuccesMock);
		
		String requesXML = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				new ParameterReplacer(
						readClasspathFile("servicebus/Routing/FtpRoutingService/moveFileRequest02.xml"))
				.replace("CORRELATION_ID", randomCorrelationId)
				.replace("CONNECTION", "edi")
				.replace("SOURCE_FILE_REFERENCE", "")
				.replace("DESTINATION_FILE_REFERENCE", "/test/file").build());
		try {
			serviceResponse = invokeOsbProxyService(PATH_SERVICE, requesXML);
			LOGGER.info("+++serviceResponse =" + serviceResponse);
			fail();
		} catch (ServiceException e) {
			e.printStackTrace();

			String exceptionXml = e.getXml();
			LOGGER.info("+++exceptionXml =" + exceptionXml);

			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
					"BusinessFault", exceptionXml);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:faultInfo/exc:faultCode/text()",
					"MW-30000", exceptionXml);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:payload/"
					+ "ftprs:moveFileRequest/ftprs:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, exceptionXml);
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:payload/"
					+ "ftprs:moveFileRequest/ftprs:connection/text()", "edi", exceptionXml);
		}
	}

	/**
	 * Invoke with Invalid Destination Name
	 */
	@Test
	public void businessFaultInvalidDestomationTest() {

		String serviceResponse;

		mockOsbBusinessService(PATH_EDI_FTP_READ, ediReadSuccesMock);
		mockOsbBusinessService(PATH_EDI_FTP_WRITE, faultMock);

		mockOsbBusinessService(PATH_TRACKING_FTP_READ, trackingReadSuccesMock);
		mockOsbBusinessService(PATH_TRACKING_FTP_WRITE, trackingWriteSuccesMock);

		mockOsbBusinessService(PATH_METAPACK_FTP_READ, metapackReadSuccesMock);
		mockOsbBusinessService(PATH_METAPACK_FTP_WRITE, metapackWriteSuccesMock);
		
		String requesXML = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
				new ParameterReplacer(
						readClasspathFile("servicebus/Routing/FtpRoutingService/moveFileRequest02.xml"))
				.replace("CORRELATION_ID", randomCorrelationId)
				.replace("CONNECTION", "edi")
				.replace("SOURCE_FILE_REFERENCE", "/test/file")
				.replace("DESTINATION_FILE_REFERENCE", "").build());
		try {
			serviceResponse = invokeOsbProxyService(PATH_SERVICE, requesXML);
			LOGGER.info("+++serviceResponse =" + serviceResponse);
			fail();
		} catch (ServiceException e) {
			e.printStackTrace();

			String exceptionXml = e.getXml();
			LOGGER.info("+++exceptionXml =" + exceptionXml);

			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
					"BusinessFault", exceptionXml);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:faultInfo/exc:faultCode/text()",
					"MW-30001", exceptionXml);
			assertXpathEvaluatesTo(
					"//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:payload/"
					+ "ftprs:moveFileRequest/ftprs:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, exceptionXml);
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:payload/"
					+ "ftprs:moveFileRequest/ftprs:connection/text()", "edi", exceptionXml);
		}
	}
}
