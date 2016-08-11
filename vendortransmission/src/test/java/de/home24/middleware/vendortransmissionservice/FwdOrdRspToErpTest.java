//TODO NOTE all test casses should be updated with 1.5 release code for this osb app
package de.home24.middleware.vendortransmissionservice;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.isEmptyOrNullString;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
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
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class FwdOrdRspToErpTest extends AbstractBaseSoaTest {
	
	private static final Logger LOGGER = Logger.getLogger(FwdOrdRspToErpTest.class.getSimpleName());
	private static final String PATH_VENDOR_SERVICE = "VendorTransmissionService/exposed/v1/VendorTransmissionService";
	
	
	
	private static final String PATH_REQ_ORDERSP_Q = "VendorTransmissionService/operations/FwdOrdRspToERP/business-service/FwdOrdRsp";
	private static final String PATH_VENDOR_TRANSMISSION_CALLBACK = "VendorTransmissionService/shared/business-service/VendorTransmissionCallbackRef";
	private static final String CALLBACK_URL = "https://localhost:7102/soa-infra/services/EDIInbound/VendorTransmissionService";
	private static final String RPLC_CORRELATION_ID = "CORRELATION_ID";
	private static final String RPLC_PURCHASE_ORDER_NUM = "PURCHASE_ORDER_NUM";
	private static final String MESSAGE_ID = "MESSAGE_ID";
	private static final String REPLY_TO = "REPLY_TO";

	
	private String randomCorrelationId, randomPoNum, randomMessageId, invocationResult  = "";
	
	private DefaultSoapMockService writeOrdRspToERPRequestQueueSuccessMock = null;
	private DefaultSoapMockService writeOrdRspToERPRequestQueueFaultMock = null;
	private DefaultSoapMockService vendortransmissionExceptionCallbackMock = null;	
	private List<MockResponsePojo> writeOrdRspFaultMockPojoList = new ArrayList<MockResponsePojo>();
	
	@BeforeClass
	public static void setUpBeforeClass() {
		testInitialization();
	}
	
	@Before
	public void setUp() {
		
		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("wsa05", "http://www.w3.org/2005/08/addressing");
		declareXpathNS("edf", "http://home24.de/data/navision/edifact/v1");
		declareXpathNS("iwofurn", "http://home24.de/data/thirdparty/iwofurn/v1");
		declareXpathNS("exp", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("rrw", "http://home24.de/interfaces/bas/responseretrywrapper/responseretrywrappermessages/v1");
		declareXpathNS("efm", "http://home24.de/data/navision/edifactmessages/v1");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("vtm", "http://home24.de/interfaces/bas/vendortransmissionservice/vendortransmissionservicemessages/v1");
	
		
		
		
		
		
		Random randomNumber = new Random();
		randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));
		randomMessageId = String.valueOf(randomNumber.nextInt(1000000));
		randomPoNum = "DS" + String.valueOf(randomNumber.nextInt(1000000));
			
		LOGGER.info("+++Setup Mocks+++");
		
		writeOrdRspToERPRequestQueueSuccessMock = new DefaultSoapMockService("");
		vendortransmissionExceptionCallbackMock = new DefaultSoapMockService("");
		
		writeOrdRspFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT,""));
		writeOrdRspToERPRequestQueueFaultMock = new DefaultSoapMockService(writeOrdRspFaultMockPojoList);
	}

	@After
	public void tearDown() {
		LOGGER.info("+++Delete Mocks+++");
		
		vendortransmissionExceptionCallbackMock = null;
		writeOrdRspToERPRequestQueueSuccessMock = null;
		writeOrdRspToERPRequestQueueFaultMock = null;
	}
	
	/**
	 * Assert that ORDRSP (EDIFACT purchase order response) enqueue is invoked
	 * Assert that exception callback is not invoked.
	 * Assert that request is created correctly
	 * Assert that message ID and reply to are defined correctly in Key value list
	 */
	@Test
	public void fwdOrdRspToErpRequestQueueSuccessTest() {

		mockOsbBusinessService(PATH_REQ_ORDERSP_Q, writeOrdRspToERPRequestQueueSuccessMock);
		mockOsbBusinessService(PATH_VENDOR_TRANSMISSION_CALLBACK, vendortransmissionExceptionCallbackMock);

		final String requestString = new ParameterReplacer(
				readClasspathFile("fwdORDRSPToErpTest/FwdOrdRspToErpTestRequest.xml"))
						.replace(RPLC_CORRELATION_ID, randomCorrelationId)
						.replace(RPLC_PURCHASE_ORDER_NUM, randomPoNum)
						.build();
		
		final String soapRequestString = getSoapRequest(randomMessageId, CALLBACK_URL, requestString);
		LOGGER.info("+++Generated SOAP Request :" +soapRequestString);
		
		invokeOsbProxyService(PATH_VENDOR_SERVICE, soapRequestString);
		
		
		
		//Assert that ORDRSP (EDIFACT purchase order response) is written to ERP request queue.
		assertThat("ORDRSP has not been enqueued.", writeOrdRspToERPRequestQueueSuccessMock.getNumberOfInvocations() == 1);
		//Assert that exception callback is not invoked.
		assertThat("ExceptionCallback should not be invoked.", vendortransmissionExceptionCallbackMock.getNumberOfInvocations() == 0);

		
		String erpOrdRspRequestString = new ParameterReplacer(readClasspathFile("fwdORDRSPToErpTest/ReqFwdOrdRspErpQueueMessage.xml"))
		.replace(RPLC_CORRELATION_ID, randomCorrelationId)
		.replace(RPLC_PURCHASE_ORDER_NUM, randomPoNum)
		.replace(REPLY_TO, CALLBACK_URL)
		.replace(MESSAGE_ID, randomMessageId)
		.build();
		
		//If this is working this is not necessary
//		
		System.out.println("+++writeOrdRspToERPRequestQueueSuccessMock.getLastReceivedRequest() "+writeOrdRspToERPRequestQueueSuccessMock.getLastReceivedRequest());
		assertXpathEvaluatesTo("//efm:forwardOrdRspToERP/efm:body/iwofurn:OrdrspMessage/iwofurn:ORDRSP/iwofurn:HEAD/iwofurn:OrderNumberRef/iwofurn:DocRefNumber/text()", 
				randomPoNum,
				writeOrdRspToERPRequestQueueSuccessMock.getLastReceivedRequest());
		assertXmlEquals(writeOrdRspToERPRequestQueueSuccessMock.getLastReceivedRequest(), erpOrdRspRequestString);
//		
//		
//		//Assert that request is created correctly
//		assertXpathEvaluatesTo("count(efm:forwardOrdRspToERP)", String.valueOf(1),
//				writeOrdRspToERPRequestQueueSuccessMock.getLastReceivedRequest());
//		assertXpathEvaluatesTo("//efm:forwardOrdRspToERP/efm:header/mht:CorrelationID/text()", randomCorrelationId,
//				writeOrdRspToERPRequestQueueSuccessMock.getLastReceivedRequest());
//		assertXpathEvaluatesTo("//efm:forwardOrdRspToERP/efm:header/mht:ActivityID/text()", "P1002-ORDRSP-2ERP",
//				writeOrdRspToERPRequestQueueSuccessMock.getLastReceivedRequest());
//		
//		//Assert that message ID and reply to are defined correctly in Key value list
//		assertXpathEvaluatesTo("//efm:forwardOrdRspToERP/efm:header/mht:KeyValueList/mht:KeyValuePair[./mht:Key='MessageID']/mht:Value/text()", 
//				randomMessageId,
//				writeOrdRspToERPRequestQueueSuccessMock.getLastReceivedRequest());
//		assertXpathEvaluatesTo("//efm:forwardOrdRspToERP/efm:header/mht:KeyValueList/mht:KeyValuePair[./mht:Key='ReplyTo']/mht:Value/text()", 
//				CALLBACK_URL,
//				writeOrdRspToERPRequestQueueSuccessMock.getLastReceivedRequest());
//		
		assertXpathEvaluatesTo("//efm:forwardOrdRspToERP/efm:body/iwofurn:OrdrspMessage/iwofurn:ORDRSP/iwofurn:HEAD/iwofurn:OrderNumberRef/iwofurn:DocRefNumber/text()", 
				randomPoNum,
				writeOrdRspToERPRequestQueueSuccessMock.getLastReceivedRequest());
	}
	
	/**
	 * Test that invocation result should be empty or null.
	 * Enqueue mock with failure should be invoked and cause failure.
	 * ProcessingCallbackExceptionMock should be invoked.
	 */
	@Test
	public void fwdOrdRspToErpErrorTestWithFailureOnEnqueueOperation() {
		try{
		
			mockOsbBusinessService(PATH_REQ_ORDERSP_Q, writeOrdRspToERPRequestQueueFaultMock);
			mockOsbBusinessService(PATH_VENDOR_TRANSMISSION_CALLBACK, vendortransmissionExceptionCallbackMock);

			final String requestString = new ParameterReplacer(
					readClasspathFile("fwdORDRSPToErpTest/FwdOrdRspToErpTestRequest.xml"))
							.replace(RPLC_CORRELATION_ID, randomCorrelationId)
							.replace(RPLC_PURCHASE_ORDER_NUM, randomPoNum)
							.build();
			
			final String soapRequestString = getSoapRequest(randomMessageId, CALLBACK_URL, requestString);
			LOGGER.info("+++Generated SOAP Request :" +soapRequestString);

			invocationResult = invokeOsbProxyService(PATH_VENDOR_SERVICE, soapRequestString
					);
			
			fail();
		} catch (ServiceException e) {
			e.printStackTrace();
			
			LOGGER.info("vendortransmissionExceptionCallbackMock : "+vendortransmissionExceptionCallbackMock.getLastReceivedRequest());
			
			assertThat("Invocation result is expected to be null!", invocationResult, isEmptyOrNullString());
			assertThat("Message has not been sent to queue.", writeOrdRspToERPRequestQueueFaultMock.getNumberOfInvocations()==1);
			assertThat("VendortransmissionExceptionCallbackMock should be invoked.", vendortransmissionExceptionCallbackMock.getNumberOfInvocations()==1);

			//assertions from toAsyncResponseHeaderTransform xquery
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Header/wsa05:To/text()",CALLBACK_URL,
					vendortransmissionExceptionCallbackMock.getLastReceivedRequest());	
			assertXpathEvaluatesTo("/soapenv:Envelope/soapenv:Header/wsa05:RelatesTo/text()",randomMessageId,
					vendortransmissionExceptionCallbackMock.getLastReceivedRequest());		
		
			//Assert exception and payload 
			assertXpathEvaluatesTo("count(//soapenv:Envelope/soapenv:Body/exp:exception)", String.valueOf(1),
					vendortransmissionExceptionCallbackMock.getLastReceivedRequest());
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/exp:exception/exp:context/exp:payload/vtm:forwardOrdRspToERPRequest/vtm:requestHeader/mht:CorrelationID/text()",
					randomCorrelationId,
					vendortransmissionExceptionCallbackMock.getLastReceivedRequest());
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/exp:exception/exp:context/exp:payload/vtm:forwardOrdRspToERPRequest/vtm:requestHeader/mht:ActivityID/text()",
					"P1002-ORDRSP-2ERP",
					vendortransmissionExceptionCallbackMock.getLastReceivedRequest());
			
			//Assert document ref no
			assertXpathEvaluatesTo("//soapenv:Envelope/soapenv:Body/exp:exception/exp:context/exp:payload/vtm:forwardOrdRspToERPRequest/vtm:iwofurnOrdRsp/iwofurn:OrdrspMessage/iwofurn:ORDRSP/iwofurn:HEAD/iwofurn:OrderNumberRef/iwofurn:DocRefNumber/text()",
					randomPoNum,
					vendortransmissionExceptionCallbackMock.getLastReceivedRequest());
			
			
		}
		
		
	}
		
	private String getSoapRequest(final String pMessageId, final String pCallbackUrl,
			final String pRequestString) {
		return SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, pRequestString),
				SoapUtil.getInstance().messageIdHeader(pMessageId), 
				SoapUtil.getInstance().relatesToHeader(pMessageId),
				SoapUtil.getInstance().replyToHeader(pCallbackUrl));
	}

	

}
