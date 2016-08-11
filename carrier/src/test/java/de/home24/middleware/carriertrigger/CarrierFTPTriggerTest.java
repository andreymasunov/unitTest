package de.home24.middleware.carriertrigger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.hamcrest.Matchers.equalTo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.opitzconsulting.soa.testing.ServiceException;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.octestframework.util.XpathProcessor;
//TODO This is test for carrier trigger component which is renamed between revisions it can be renamed when all components are stable
public class CarrierFTPTriggerTest extends AbstractBaseSoaTest {
	
	private static final Logger LOGGER = Logger.getLogger(CarrierFTPTriggerTest.class.getSimpleName());
	private Map<ExceptionAsserterKey, String> keyToExpectedValues;
	@Autowired
	private ExceptionAsserter exceptionAsserter;

	@Before
	public void setUp() {

	LOGGER.setLevel(Level.FINEST);

	declareXpathNS("oagis", "http://www.openapplications.org/oagis/10");
	declareXpathNS("exception", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	declareXpathNS("opaque", "http://xmlns.oracle.com/pcbpel/adapter/opaque/");
	keyToExpectedValues = new HashMap<>();
    }
    
    @Test
    public void successfulSendFilesToH24Ftp() {

	DefaultSoapMockService ftpAdapterPutMock = new DefaultSoapMockService();
	mockOsbBusinessService(
			"CarrierTrigger/operations/sendCarrierParcel/business-service/H24FileServerFtpAdapter",
			ftpAdapterPutMock);

	final String requestMessage =  new ParameterReplacer(
		readClasspathFile("../servicebus/Carrier/CarrierFTPTrigger/putH24FileServerDocumentRequest1.xml"))
			.replace("MONTH_YEAR", "1602").build();

	try {
		invokeOsbProxyService("CarrierTrigger/operations/sendCarrierParcel/ReceiveCarrierParcelMessage",
					requestMessage);

		waitForInvocationOf(ftpAdapterPutMock, 2, 60);

		assertThat("H24 FTP was not invoked twice",
			ftpAdapterPutMock.getNumberOfInvocations(), is(2));
	
		List<String> ftpAdapterMockRequests = ftpAdapterPutMock.getReceivedRequests();

			LOGGER.info("msg1:" + ftpAdapterMockRequests.get(1));
			LOGGER.info("msg0:" + ftpAdapterMockRequests.get(0));

			assertXpathEvaluatesTo("soapenv:Envelope/soapenv:Body/opaque:opaqueElement/text()", "",
					ftpAdapterMockRequests.get(0));
			assertXpathEvaluatesTo("soapenv:Envelope/soapenv:Body/opaque:opaqueElement/text()", "JVBERi==",
					ftpAdapterMockRequests.get(1));

		} catch (ServiceException e) {
			e.printStackTrace();
			fail("ServiceException");
		}

	}

	@Test
	public void faultSendTargetFileToH24Ftp() {
		DefaultSoapMockService ftpFaultAdapterPutMock;
		DefaultSoapMockService jmsErrorQueueMock;
		List<MockResponsePojo> pMockedResponses = new ArrayList<MockResponsePojo>();
		pMockedResponses.add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
		pMockedResponses.add(new MockResponsePojo(ResponseType.FAULT, "fault", "fault msg"));
		ftpFaultAdapterPutMock = new DefaultSoapMockService(pMockedResponses);
		jmsErrorQueueMock = new DefaultSoapMockService();

		mockOsbBusinessService(
				"CarrierTrigger/operations/sendCarrierParcel/business-service/H24FileServerFtpAdapter",
				ftpFaultAdapterPutMock);

		mockOsbBusinessService(
				"CarrierTrigger/operations/sendCarrierParcel/business-service/SendCarrierParcelErrorHandling",
				jmsErrorQueueMock);

		final String requestMessage = new ParameterReplacer(
				readClasspathFile("../servicebus/Carrier/CarrierFTPTrigger/putH24FileServerDocumentRequest1.xml"))
						.replace("MONTH_YEAR", "1602").build();

		invokeOsbProxyService("CarrierTrigger/operations/sendCarrierParcel/ReceiveCarrierParcelMessage",
				requestMessage);

		waitForInvocationOf(jmsErrorQueueMock);

		assertThat("H24 FTP should be invoked two times and was invoked "
				+ ftpFaultAdapterPutMock.getNumberOfInvocations() + " times",
				ftpFaultAdapterPutMock.getNumberOfInvocations(), is(2));

		assertThat("H24 JMS was not invoked once", jmsErrorQueueMock.getNumberOfInvocations(), is(1));

		keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "OSB-380000");
		keyToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE, "Internal Server Error");
		keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TriggerFault");
		keyToExpectedValues.put(ExceptionAsserterKey.PAYLOAD_ELEMENT, requestMessage);

		exceptionAsserter.assertException(jmsErrorQueueMock.getLastReceivedRequest(), keyToExpectedValues);

	}

	@Test
	public void faultSendCheckFileToH24Ftp() {
		DefaultSoapMockService ftpFaultAdapterPutMock;
		DefaultSoapMockService jmsErrorQueueMock;
		List<MockResponsePojo> pMockedResponses = new ArrayList<MockResponsePojo>();		
		pMockedResponses.add(new MockResponsePojo(ResponseType.FAULT, "fault", "fault msg"));
		ftpFaultAdapterPutMock = new DefaultSoapMockService(pMockedResponses);
		jmsErrorQueueMock = new DefaultSoapMockService();

		mockOsbBusinessService(
				"CarrierTrigger/operations/sendCarrierParcel/business-service/H24FileServerFtpAdapter",
				ftpFaultAdapterPutMock);

		mockOsbBusinessService(
				"CarrierTrigger/operations/sendCarrierParcel/business-service/SendCarrierParcelErrorHandling",
				jmsErrorQueueMock);

		final String requestMessage = new ParameterReplacer(
				readClasspathFile("../servicebus/Carrier/CarrierFTPTrigger/putH24FileServerDocumentRequest1.xml"))
						.replace("MONTH_YEAR", "1602").build();

		invokeOsbProxyService("CarrierTrigger/operations/sendCarrierParcel/ReceiveCarrierParcelMessage",
				requestMessage);

		waitForInvocationOf(jmsErrorQueueMock);

		assertThat("H24 FTP should be invoked once and was invoked "
				+ ftpFaultAdapterPutMock.getNumberOfInvocations() + " times",
				ftpFaultAdapterPutMock.getNumberOfInvocations(), is(1));

		assertThat("H24 JMS was not invoked once", jmsErrorQueueMock.getNumberOfInvocations(), is(1));

		keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CODE, "OSB-380000");
		keyToExpectedValues.put(ExceptionAsserterKey.FAULT_MESSAGE, "Internal Server Error");
		keyToExpectedValues.put(ExceptionAsserterKey.FAULT_CATEGORY, "TriggerFault");
		keyToExpectedValues.put(ExceptionAsserterKey.PAYLOAD_ELEMENT, requestMessage);

		exceptionAsserter.assertException(jmsErrorQueueMock.getLastReceivedRequest(), keyToExpectedValues);

	}
}
