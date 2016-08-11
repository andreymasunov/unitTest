package de.home24.middleware.vendortransmissionservice;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.bea.common.security.xacml.context.Request;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter;
import de.home24.middleware.octestframework.assertion.ExceptionAsserter.ExceptionAsserterKey;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class InitiateComboxTransmissionTest extends AbstractBaseSoaTest {

    private final static String PATH_SERVICE = "VendorTransmissionService/exposed/v1/VendorTransmissionService";
    private final static String PATH_BS_WRAPPER = "VendorTransmissionService/operations/InitiateComboxTransmission/business-service/BpelToNavisionService";

    private String randomCorrelationId, zipFileReference, comboxId = "";

    private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
    private static final String REPLACE_PARAM_COMBOX_ID = "COMBOX_ID";
    private static final String REPLACE_PARAM_ZIP_FILE_REFERENCE = "ZIP_FILE_REFERENCE";

    private static final String PARTNER_ID_IWOFURN_RTP = "IWOfurn-RTP";

    private static final Logger LOGGER = Logger
	    .getLogger(InitiateComboxTransmissionTest.class.getSimpleName());

    private DefaultSoapMockService wrapperSuccessMock;
    private DefaultSoapMockService wrapperErrortMock;
    private DefaultSoapMockService wrapperTechnicalFaultMock;
    private List<MockResponsePojo> wrapperTechnicalFaultMockPojoList = new ArrayList<MockResponsePojo>();

    private Map<ExceptionAsserterKey, String> keysToExcpectedValuesForException = new HashMap<>();

    @Autowired
    private ExceptionAsserter exceptionAsserter;

    @Before
    public void setUp() {

	declareXpathNS("v1",
		"http://home24.de/interfaces/bas/vendortransmissionservice/vendortransmissionservicemessages/v1");
	declareXpathNS("typ", "http://bpeltonavision.soa.opitzconsulting.com/types/");
	declareXpathNS("cbx", "http://home24.de/ComboxAPIWS/v1.0");
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
	declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS("typ", "http://bpeltonavision.soa.opitzconsulting.com/types/");

	Random randomNumber = new Random();
	randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));
	zipFileReference = String.format("//navtestdev1/spublic/Test%s.txt",  randomCorrelationId); //This should not use File.separator becuse failed test on windows machine
	comboxId = String.valueOf(randomNumber.nextInt(1000000));

	LOGGER.info("+++Create Mocks+++");
	wrapperSuccessMock = new DefaultSoapMockService(new ParameterReplacer(
		readClasspathFile("initiatecomboxtransmission/wrapperSuccessResponse.xml"))
			.replace(REPLACE_PARAM_COMBOX_ID, comboxId).build());

	wrapperErrortMock = new DefaultSoapMockService(new ParameterReplacer(
		readClasspathFile("initiatecomboxtransmission/wrapperErrorResponse.xml"))
			.replace(REPLACE_PARAM_ZIP_FILE_REFERENCE, zipFileReference).build());

	wrapperTechnicalFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
	wrapperTechnicalFaultMock = new DefaultSoapMockService(wrapperTechnicalFaultMockPojoList);

	initializeCommonExceptionValidations();
    }

    public void initializeCommonExceptionValidations() {

	keysToExcpectedValuesForException.put(ExceptionAsserterKey.SOURCE_SYSTEM_NAME, "Middleware");
	keysToExcpectedValuesForException.put(ExceptionAsserterKey.TRANSACTION_ID, randomCorrelationId);
	keysToExcpectedValuesForException.put(ExceptionAsserterKey.PROCESS_LIBRARY_ID, "P1002");
	keysToExcpectedValuesForException.put(ExceptionAsserterKey.PAYLOAD_ELEMENT_NAME,
		"initiateComboxTransmissionRequest");
    }

    @After
    public void tearDown() {
	LOGGER.info("+++Delete Mocks+++");
	wrapperSuccessMock = null;
	wrapperErrortMock = null;
	wrapperTechnicalFaultMock = null;
	wrapperTechnicalFaultMockPojoList = null;
    }

    /**
     * Invoke with success
     */
    @Test
    public void whenValidRequestAndNoErrorsWhileProcessingThenReturnInitiateComboxTransmissionSuccess() {

	final String requestXML = new ParameterReplacer(
		readClasspathFile("initiatecomboxtransmission/initiateComboxTransmissionRequest.xml"))
			.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
			.replace(REPLACE_PARAM_ZIP_FILE_REFERENCE, zipFileReference).build();

	mockOsbBusinessService(PATH_BS_WRAPPER, wrapperSuccessMock);

	try {
	    LOGGER.info("+++invoke Service with Success");
	    String invocationResult = invokeOsbProxyService(PATH_SERVICE,
		    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));
	    LOGGER.info("+++invocation Result: " + invocationResult);

	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/v1:initiateComboxTransmissionResponse/v1:responseHeader/mht:CorrelationID/text()",
		    randomCorrelationId, invocationResult);
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/v1:initiateComboxTransmissionResponse/v1:comboxID/text()",
		    comboxId, invocationResult);
	    assertThat("NavisionWrapper has not been invoked", wrapperSuccessMock.hasBeenInvoked(), is(true));
	    assertXpathEvaluatesTo(
		    "//typ:processNonLegacyElement/typ:input/typ:payload/cbx:SendFileToComBox/cbx:PartnerID/text()",
		    PARTNER_ID_IWOFURN_RTP, wrapperSuccessMock.getLastReceivedRequest());

	} catch (ServiceException e) {
	    String serviceExceptionXml = e.getXml();
	    LOGGER.info("+++ServiceException =" + serviceExceptionXml);
	    fail();
	}

    }

    /**
     * Invoke with BusinessFault
     */
    @Test
    public void whenValidRequestAndErrorOnComboxSideWhileProcessingTheRequestThenReturnBusinessFaultWithErrorCodeMW30100() {

	final String requestXML = new ParameterReplacer(
		readClasspathFile("initiatecomboxtransmission/initiateComboxTransmissionRequest.xml"))
			.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
			.replace(REPLACE_PARAM_ZIP_FILE_REFERENCE, zipFileReference).build();
	

	mockOsbBusinessService(PATH_BS_WRAPPER, wrapperErrortMock);
	String invocationResult = "";
	try {
	    LOGGER.info("+++invoke Service with Success");
	    invocationResult = invokeOsbProxyService(PATH_SERVICE,
		    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));
	    LOGGER.info("+++invocation Result: " + invocationResult);
	    fail();

	} catch (ServiceException e) {
	    e.printStackTrace();
	    invocationResult = e.getXml();
	    LOGGER.info("+++ServiceException =" + invocationResult);

	    invocationResult = e.getXml();

	    keysToExcpectedValuesForException.put(ExceptionAsserterKey.FAULT_CATEGORY, "BusinessFault");
	    keysToExcpectedValuesForException.put(ExceptionAsserterKey.FAULT_CODE, "MW-30100");
	    keysToExcpectedValuesForException.put(ExceptionAsserterKey.FAULT_MESSAGE,
		    String.format("File not found %s", zipFileReference));

	    exceptionAsserter.assertException(e.getXml(),
		    keysToExcpectedValuesForException);
	}

    }

    /**
     * Invoke with TechnicalFault
     */
    @Test
    public void whenValidRequestAndErrorWhileCallingComboxThenReturnTechnicalFaultWithErrorCodeMW10100() {

	final String requestXML = new ParameterReplacer(
		readClasspathFile("initiatecomboxtransmission/initiateComboxTransmissionRequest.xml"))
			.replace(REPLACE_PARAM_CORRELATION_ID, randomCorrelationId)
			.replace(REPLACE_PARAM_ZIP_FILE_REFERENCE, zipFileReference).build();

	String response = "";

	mockOsbBusinessService(PATH_BS_WRAPPER, wrapperTechnicalFaultMock);

	LOGGER.info("+++invoke Service with Success");
	try {
	    response = invokeOsbProxyService(PATH_SERVICE,
		    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

	    LOGGER.info("+++invocation Result: " + response);
	    fail();

	} catch (ServiceException e) {
	    e.printStackTrace();
	    String serviceExceptionXml = e.getXml();
	    LOGGER.info("+++ServiceException =" + serviceExceptionXml);

	    keysToExcpectedValuesForException.put(ExceptionAsserterKey.FAULT_CATEGORY, "TechnicalFault");
	    keysToExcpectedValuesForException.put(ExceptionAsserterKey.FAULT_CODE, "MW-10100");
	    keysToExcpectedValuesForException.put(ExceptionAsserterKey.FAULT_MESSAGE,
		    "Technical fault while processing data to Combox");

	    exceptionAsserter.assertException(e.getXml(),
		    keysToExcpectedValuesForException);
	}
    }

}
