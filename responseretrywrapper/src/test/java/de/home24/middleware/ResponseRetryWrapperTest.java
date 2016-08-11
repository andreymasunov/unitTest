package de.home24.middleware;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.octestframework.mock.RetryWithExceptionSoapMockService;

/**
 * Tests for ResponseRetryWrapper BPEL implementation.
 * 
 * @author kas, drpd
 *
 */
public class ResponseRetryWrapperTest extends AbstractBaseSoaTest {

	private static final Logger logger = Logger.getLogger(ResponseRetryWrapperTest.class.getSimpleName());

	private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";

	public static final String COMPOSITE = "ResponseRetryWrapper";
	public static final String REVISION = "1.3.0.0";
	public static final String PROCESS = "ResponseRetryDelegator_ep";

	private AbstractSoapMockService genericFaultHandlerMockRef = null;
	private AbstractSoapMockService genericFaultHandlerResendMockRef = null;
	private AbstractSoapMockService genericFaultHandlerAbortMockRef = null;
	private AbstractSoapMockService queueingServiceMockRef = null;
	private AbstractSoapMockService ftpRoutingServiceMockRef = null;
	private AbstractSoapMockService ftpRoutingServiceFaultMockRef = null;
	

	private String randomCorrelationId = "", requestFtpXml = "";

	public ResponseRetryWrapperTest() {
		super("generic");
	}

	@Before
	public void setUp() throws Exception {

		Random randomNumber = new Random();
		randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));

		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("rrw", "http://home24.de/interfaces/bas/responseretrywrapper/responseretrywrappermessages/v1");
		declareXpathNS("gfh", "http://home24.de/interfaces/bas/genericfaulthandler/genericfaulthandlerservicemessages/v1");
		declareXpathNS("ftprs", "http://home24.de/interfaces/bes/ftproutingservice/ftproutingservicemessages/v1");

		requestFtpXml = new ParameterReplacer(
						readClasspathFile("processes/GenericProcesses/ResponseRetryWrapper/onErrorInFTPRoutingRequest01.xml"))
								.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId).build();
		
		genericFaultHandlerAbortMockRef = new DefaultSoapMockService(readClasspathFile("processes/GenericProcesses/ResponseRetryWrapper/GenericFaultHandlerResponseQuit.xml"));
		genericFaultHandlerResendMockRef = new DefaultSoapMockService(readClasspathFile("processes/GenericProcesses/ResponseRetryWrapper/GenericFaultHandlerResponseResend.xml"));

		ftpRoutingServiceMockRef = new DefaultSoapMockService(readClasspathFile("processes/GenericProcesses/ResponseRetryWrapper/moveFileResponse01.xml"));

	}

	@After
	public void tearDown() {
		logger.info("+++Delete Mocks+++");
		genericFaultHandlerMockRef = null;
		genericFaultHandlerAbortMockRef = null;
		genericFaultHandlerResendMockRef = null;
		queueingServiceMockRef = null;
		ftpRoutingServiceMockRef = null;
		
		randomCorrelationId = null;
		requestFtpXml = null;
	}

	@Test
	public void responseRetryWapperResend() throws Exception {

		queueingServiceMockRef = new DefaultSoapMockService(
				readClasspathFile("processes/GenericProcesses/ResponseRetryWrapper/QueueingServiceResponse.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerResendMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "QueueingService", queueingServiceMockRef);

		final String requestXML = readClasspathFile(
				"processes/GenericProcesses/ResponseRetryWrapper/OnErrorInResponseQueueRequest.xml");

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(genericFaultHandlerResendMockRef);
		waitForInvocationOf(queueingServiceMockRef);

		assertThat("QueueingService has been invoked once",
				queueingServiceMockRef.getNumberOfInvocations() == 1);
		assertThat("GenericFaultHandler has been invoked once",
				genericFaultHandlerResendMockRef.getNumberOfInvocations() == 1);
		

	}

	@Test
	public void responseRetryWapperQuit() throws Exception {

		queueingServiceMockRef = new DefaultSoapMockService();

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerAbortMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "QueueingService", queueingServiceMockRef);

		final String requestXML = readClasspathFile(
				"processes/GenericProcesses/ResponseRetryWrapper/OnErrorInResponseQueueRequest.xml");

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		assertThat("QueueingService has been not invoked", 
				queueingServiceMockRef.hasBeenInvoked(), is(Boolean.FALSE));
		assertThat("GenericFaultHandler has been invoked once",
				genericFaultHandlerAbortMockRef.getNumberOfInvocations() == 1);

	}

	@Test
	public void responseRetryWrapperNotSuccess() throws Exception {

		genericFaultHandlerMockRef = new DefaultSoapMockService(Lists.newArrayList(
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(
								"processes/GenericProcesses/ResponseRetryWrapper/GenericFaultHandlerResponseResend.xml")),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(
						"processes/GenericProcesses/ResponseRetryWrapper/GenericFaultHandlerResponseResend.xml"))));
		queueingServiceMockRef = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.BUSINESS_FAULT, ""),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(
						"processes/GenericProcesses/ResponseRetryWrapper/QueueingServiceResponse.xml")));

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "QueueingService", queueingServiceMockRef);

		final String requestXML = readClasspathFile(
				"processes/GenericProcesses/ResponseRetryWrapper/OnErrorInResponseQueueRequest.xml");

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));


		waitForInvocationOf(genericFaultHandlerMockRef, 2, 10);
		waitForInvocationOf(queueingServiceMockRef, 2, 10);

		assertThat("QueueingService has not been invoked twice", 
				queueingServiceMockRef.getNumberOfInvocations() == 2);
		assertThat("GenericFaultHandler has been invoked twice",
				genericFaultHandlerMockRef.getNumberOfInvocations() == 2);

	}

	/**
	 * Sends the message to FtpRoutingService 
	 * after GenericFaultHandler reply with Resend
	 * 
	 * @throws Exception
	 */
	@Test
	public void responseRetryWapperFtpRoutingResend() throws Exception {
		
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerResendMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "FtpRoutingService", ftpRoutingServiceMockRef);

		try {

			invokeCompositeService(COMPOSITE, REVISION, PROCESS,
					SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestFtpXml));

			waitForInvocationOf(genericFaultHandlerResendMockRef);
			waitForInvocationOf(ftpRoutingServiceMockRef);

			assertThat("FtpRoutingService has been invoked once",
					ftpRoutingServiceMockRef.getNumberOfInvocations() == 1);
			assertThat("GenericFaultHandler has been invoked once",
					genericFaultHandlerResendMockRef.getNumberOfInvocations() == 1);
			
			List<String> gfhRequests = genericFaultHandlerResendMockRef.getReceivedRequests();
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, 
					gfhRequests.get(0));
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:transactionId/text()",
					randomCorrelationId, 
					gfhRequests.get(0));

			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileRequest/ftprs:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, 
					ftpRoutingServiceMockRef.getLastReceivedRequest());
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileRequest/ftprs:connection/text()",
					"edi", 
					ftpRoutingServiceMockRef.getLastReceivedRequest());
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileRequest/ftprs:sourceFileReference/text()",
					"testSourceFolder/TestSourceFileName.err", 
					ftpRoutingServiceMockRef.getLastReceivedRequest());
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileRequest/ftprs:destinationFileReference/text()",
					"testDestinationFolder/TestDestinationFileName.ok", 
					ftpRoutingServiceMockRef.getLastReceivedRequest());
			
		} catch (ServiceException e) {
			fail();
		}
	}
	

	/**
	 * Ends the instance after GenericFaultHandler reply with Quit
	 * 
	 * @throws Exception
	 */
	@Test
	public void responseRetryWapperFtpRoutingQuit() throws Exception {

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerAbortMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "FtpRoutingService", ftpRoutingServiceMockRef);

		try {

			invokeCompositeService(COMPOSITE, REVISION, PROCESS,
					SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestFtpXml));

			waitForInvocationOf(genericFaultHandlerAbortMockRef);
			waitForInvocationOf(ftpRoutingServiceMockRef);

			assertThat("FtpRoutingService was not invoked",
					ftpRoutingServiceMockRef.hasBeenInvoked(), is(Boolean.FALSE));
			assertThat("GenericFaultHandler has been invoked once",
					genericFaultHandlerAbortMockRef.getNumberOfInvocations() == 1);
			
			List<String> gfhRequests = genericFaultHandlerAbortMockRef.getReceivedRequests();
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, 
					gfhRequests.get(0));
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:transactionId/text()",
					randomCorrelationId, 
					gfhRequests.get(0));
			
		} catch (ServiceException e) {
			fail();
		}

	}
	

	/**
	 * FtpRoutingService fails 3 times after the 3rd time
	 * GenericFaultHandler will reply with Abort
	 * 
	 * @throws Exception
	 */
	@Test
	public void ftpRoutingResend3TimesThenAbort() throws Exception {

		genericFaultHandlerMockRef = new DefaultSoapMockService(Lists.newArrayList(
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(
						"processes/GenericProcesses/ResponseRetryWrapper/GenericFaultHandlerResponseResend.xml")),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(
						"processes/GenericProcesses/ResponseRetryWrapper/GenericFaultHandlerResponseResend.xml")),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(
						"processes/GenericProcesses/ResponseRetryWrapper/GenericFaultHandlerResponseResend.xml")),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(
						"processes/GenericProcesses/ResponseRetryWrapper/GenericFaultHandlerResponseQuit.xml"))));
		
		ftpRoutingServiceFaultMockRef = new DefaultSoapMockService(Lists.newArrayList(new MockResponsePojo(ResponseType.BUSINESS_FAULT, "")));

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "FtpRoutingService", ftpRoutingServiceFaultMockRef);

		try {

			invokeCompositeService(COMPOSITE, REVISION, PROCESS,
					SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestFtpXml));

			waitForInvocationOf(genericFaultHandlerMockRef, 4, 20);
			waitForInvocationOf(ftpRoutingServiceFaultMockRef, 3, 15);

			assertThat("FtpRoutingService has been invoked 3 times",
					ftpRoutingServiceFaultMockRef.getNumberOfInvocations() == 3);
			assertThat("GenericFaultHandler has been invoked 4 times",
					genericFaultHandlerMockRef.getNumberOfInvocations() == 4);
			
			List<String> gfhRequests = genericFaultHandlerMockRef.getReceivedRequests();
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, 
					gfhRequests.get(0));
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:transactionId/text()",
					randomCorrelationId, 
					gfhRequests.get(0));
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, 
					gfhRequests.get(1));
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:transactionId/text()",
					randomCorrelationId, 
					gfhRequests.get(1));
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, 
					gfhRequests.get(2));
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:transactionId/text()",
					randomCorrelationId, 
					gfhRequests.get(2));

			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileRequest/ftprs:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId, 
					ftpRoutingServiceFaultMockRef.getLastReceivedRequest());
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileRequest/ftprs:connection/text()",
					"edi", 
					ftpRoutingServiceFaultMockRef.getLastReceivedRequest());
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileRequest/ftprs:sourceFileReference/text()",
					"testSourceFolder/TestSourceFileName.err", 
					ftpRoutingServiceFaultMockRef.getLastReceivedRequest());
			assertXpathEvaluatesTo(
					"/soapenv:Envelope/soapenv:Body/ftprs:moveFileRequest/ftprs:destinationFileReference/text()",
					"testDestinationFolder/TestDestinationFileName.ok", 
					ftpRoutingServiceFaultMockRef.getLastReceivedRequest());
			
		} catch (ServiceException e) {
			fail();
		}
	}

}
