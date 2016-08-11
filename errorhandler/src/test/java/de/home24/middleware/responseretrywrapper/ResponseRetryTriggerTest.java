package de.home24.middleware.responseretrywrapper;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.util.Random;
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
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class ResponseRetryTriggerTest extends AbstractBaseSoaTest {

	private final static String PATH_JMS_SERVICE = "ResponseRetryTrigger/operations/receiveJMSError/ReceiveJMSError";
	private final static String PATH_FTP_SERVICE = "ResponseRetryTrigger/operations/receiveFTPError/ReceiveFTPError";
	private final static String PATH_RESPONSE_RETRY_MOCK = "ResponseRetryTrigger/shared/business-service/ResponseRetryWrapperRef";	

	private final static String PATH_RESOURCE ="../servicebus/ErrorHandler/ResponseRetryTrigger/";
	private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
	private static final String TRANSACTION_ID = "123456";
	

	private String randomCorrelationId, randomMessageId = "";

	private static final Logger LOGGER = Logger.getLogger(ResponseRetryTriggerTest.class.getSimpleName());

	private DefaultSoapMockService responseRetryWrapperRefMock;
	
	@BeforeClass
	public static void setUpBeforeClass() {
		testInitialization();
	}

	@Before
	public void setUp() {
		
		Random randomNumber = new Random();
		randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));		
		randomMessageId = String.valueOf(randomNumber.nextInt(1000000));

		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("ns1", "http://home24.de/interfaces/bas/responseretrywrapper/responseretrywrappermessages/v1");
		declareXpathNS("oagis", "http://www.openapplications.org/oagis/10");
		declareXpathNS("ns2", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("ns4", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("wsa05", "http://www.w3.org/2005/08/addressing");

		LOGGER.info("+++Create Mocks+++");
		responseRetryWrapperRefMock = new DefaultSoapMockService("");

	}

	@After
	public void tearDown() {
		LOGGER.info("+++Delete Mocks+++");
		responseRetryWrapperRefMock = null;
	}

	@Test
	public void receiveJMSErrorSuccesTest() {
		try {
			mockOsbBusinessService(PATH_RESPONSE_RETRY_MOCK, responseRetryWrapperRefMock);
			String requestXML = new ParameterReplacer(readClasspathFile(PATH_RESOURCE+"onErrorInResponseQueueRequest.xml"))
					.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId).build();

			final String invokerRefMockUri = mockOsbBusinessService(PATH_RESPONSE_RETRY_MOCK,
					responseRetryWrapperRefMock);

			requestXML = getFullSoapRequest(randomMessageId, invokerRefMockUri, requestXML);

			invokeOsbProxyService(PATH_JMS_SERVICE, requestXML);

			assertThat("One message is sent to the Response Retry Wrapper process.",
					responseRetryWrapperRefMock.getNumberOfInvocations() == 1);
			
			assertThat("No error occours.", responseRetryWrapperRefMock.hasBeenInvoked() == true);
			
			String receivedQueueMsg = responseRetryWrapperRefMock.getLastReceivedRequest();

			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns1:onErrorInResponseQueueRequest/ns1:requestHeader/ns2:CorrelationID/text()", 
					randomCorrelationId,
					receivedQueueMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns1:onErrorInResponseQueueRequest/ns1:exception/ns4:context/ns4:transactionId/text()", 
					TRANSACTION_ID,
					receivedQueueMsg);
			
		} catch (ServiceException e) {
			fail();
		}

	}

	@Test
	public void receiveFTPErrorSuccesTest() {
		try {
			mockOsbBusinessService(PATH_RESPONSE_RETRY_MOCK, responseRetryWrapperRefMock);
			String requestXML = new ParameterReplacer(readClasspathFile(PATH_RESOURCE+"onErrorInFTPRoutingRequest.xml"))
					.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId).build();

			final String invokerRefMockUri = mockOsbBusinessService(PATH_RESPONSE_RETRY_MOCK,
					responseRetryWrapperRefMock);

			requestXML = getFullSoapRequest(randomMessageId, invokerRefMockUri, requestXML);

			invokeOsbProxyService(PATH_FTP_SERVICE, requestXML);

			assertThat("One message is sent to the Response Retry Wrapper process.",
					responseRetryWrapperRefMock.getNumberOfInvocations() == 1);
			assertThat("No error occours.", responseRetryWrapperRefMock.hasBeenInvoked() == true);
			
			String receivedQueueMsg = responseRetryWrapperRefMock.getLastReceivedRequest();
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns1:onErrorInFTPRoutingRequest/ns1:requestHeader/ns2:CorrelationID/text()", 
					randomCorrelationId,
					receivedQueueMsg);
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns1:onErrorInFTPRoutingRequest/ns1:exception/ns4:context/ns4:transactionId/text()", 
					TRANSACTION_ID,
					receivedQueueMsg);
			
		} catch (ServiceException e) {
			fail();
		}

	}
	
	@Test
	public void receiveJMSErrorReturnErrorTest() {		
		try {
			mockOsbBusinessService(PATH_RESPONSE_RETRY_MOCK, responseRetryWrapperRefMock);
			String requestXML = new ParameterReplacer(readClasspathFile(PATH_RESOURCE+"onErrorInFTPRoutingRequestWithError.xml"))
					.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId).build();			

			final String invokerRefMockUri = mockOsbBusinessService(PATH_RESPONSE_RETRY_MOCK,
					responseRetryWrapperRefMock);

			requestXML = getFullSoapRequest(randomMessageId, invokerRefMockUri, requestXML);

			invokeOsbProxyService(PATH_FTP_SERVICE, requestXML);
			
			String receivedQueueMsg = responseRetryWrapperRefMock.getLastReceivedRequest();
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns1:onErrorInResponseQueueRequest/ns1:requestHeader/ns2:CorrelationID/text()", 
					randomCorrelationId,
					receivedQueueMsg);
			fail();
		} catch (Exception e) {
						
			assertThat("Service tries to send a message to the wrapper.",
					responseRetryWrapperRefMock.getNumberOfInvocations() == 1);

		}

	}
	
	@Test
	public void receiveFTPErrorReturnErrorTest() {		
		try {
			mockOsbBusinessService(PATH_RESPONSE_RETRY_MOCK, responseRetryWrapperRefMock);
			String requestXML = new ParameterReplacer(readClasspathFile(PATH_RESOURCE+"onErrorInFTPRoutingRequestWithError.xml"))
					.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId).build();			

			final String invokerRefMockUri = mockOsbBusinessService(PATH_RESPONSE_RETRY_MOCK,
					responseRetryWrapperRefMock);

			requestXML = getFullSoapRequest(randomMessageId, invokerRefMockUri, requestXML);

			invokeOsbProxyService(PATH_FTP_SERVICE, requestXML);
			
			String receivedQueueMsg = responseRetryWrapperRefMock.getLastReceivedRequest();
			
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Body/ns1:onErrorInFTPRoutingRequest/ns1:requestHeader/ns2:CorrelationID/text()", 
					randomCorrelationId,
					receivedQueueMsg);
			fail();
		} catch (Exception e) {
						
			assertThat("Service tries to send a message to the wrapper.",
					responseRetryWrapperRefMock.getNumberOfInvocations() == 1);

		}

	}

	private String getFullSoapRequest(final String pMessageId, final String pCallbackUrl, final String pRequestString) {
		return SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, pRequestString),
				SoapUtil.getInstance().messageIdHeader(pMessageId), SoapUtil.getInstance().relatesToHeader(pMessageId),
				SoapUtil.getInstance().replyToHeader(pCallbackUrl));
	}
}