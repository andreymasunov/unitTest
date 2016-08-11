package de.home24.middleware.carriertrigger;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.DatatypeConverter;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Tests for CarrierTrigger operation readCarrierStatusUpdate
 * 
 * @author svb
 *
 */
public class ReadCarrierStatusUpdateTest extends AbstractBaseSoaTest {

    private static final Logger LOGGER = Logger.getLogger(ReadCarrierStatusUpdateTest.class.getSimpleName());

    private static final String FAULT_CODE_TRANSMIT_H24FTP_TRANSMISSION_ERROR = "CAR-UPD-00001";
    private static final String FAULT_CODE_NXSD_TRANSFORM_ERROR = "CAR-UPD-00002";
    private static final String FAULT_CODE_TRANSMIT_CARRIER_STATUS_UPD_PROCESS = "CAR-UPD-00003";

    private static final String REPLACE_PARAM_FILENAME = "FILENAME";
    private static final String REPLACE_PARAM_SALES_ORDER_ID = "SALES_ORDER_ID";

    private static final String SALES_ORDER_ID = "76291231";
    
    private static final String RESOURCE_DIR = "../ftp/Metapack/";
    private static final String SUCCESS_CSV_FILE = "71808_ExportFile_201601181741597347.csv";
    private static final String FAILURE_CSV_FILE = "71808_ExportFile_201601181741597347_invalid.csv";
    private static final String METAPACK_FILTER_CSV_FILE = "71808_ExportFile_filter_201601181741597347.csv";
    
    private DefaultSoapMockService carrierStatusErrorListener;

    @Before
    public void setUp() {

	LOGGER.setLevel(Level.FINEST);

	declareXpathNS("ns1", "http://home24.de/interfaces/bas/carrier/carrierservicemessages/v1");
	declareXpathNS("ns2", "http://home24.de/data/common/messageheadertypes/v1");
	declareXpathNS("ns3",
		"http://home24.de/interfaces/bps/carrierstatusprocesserrorlistener/carrierstatuserrorlistenermessages/v1");
	declareXpathNS("ns4",
		"http://home24.de/interfaces/bps/carrierstatus/carrierstatusprocessmessages/v1");
	declareXpathNS("oagis", "http://www.openapplications.org/oagis/10");
	declareXpathNS("exception", "http://home24.de/data/common/exceptiontypes/v1");

	carrierStatusErrorListener = new DefaultSoapMockService();
	mockOsbBusinessService(
		"CarrierTrigger/operations/receiveCarrierStatusUpdate/business-service/CarrierStatusErrorListenerRef",
		carrierStatusErrorListener);
    }

    @Test
    public void successfulTransmissionFromMetapackToH24Ftp() {

	DefaultSoapMockService h24FileServerFtpCarrierUpdateRef = new DefaultSoapMockService();

	final String receivedRequestFromMetapack = getBase64EncodedRequest(SUCCESS_CSV_FILE,RESOURCE_DIR);

	mockOsbBusinessService(
		"CarrierTrigger/operations/receiveCarrierStatusUpdate/business-service/H24FileServerFtpCarrierUpdateRef",
		h24FileServerFtpCarrierUpdateRef);

	invokeOsbProxyService("CarrierTrigger/operations/receiveCarrierStatusUpdate/ReadCarrierStatusUpdate",
		receivedRequestFromMetapack);

	waitForInvocationOf(h24FileServerFtpCarrierUpdateRef);

	assertThat("Message has been transfered to H24 FTP server",
		h24FileServerFtpCarrierUpdateRef.hasBeenInvoked(), is(Boolean.TRUE));
	assertThat("ErrorListener was not invoked!", carrierStatusErrorListener.hasBeenInvoked(),
		is(Boolean.FALSE));

	LOGGER.fine(String.format("Message received by H24FileServerFtpCarrierUpdateRef: %s",
		h24FileServerFtpCarrierUpdateRef.getLastReceivedRequest()));

	assertThat("Message is equal to original message",
		h24FileServerFtpCarrierUpdateRef.getLastReceivedRequest(),
		equalTo(receivedRequestFromMetapack));
    }

    @Test
    public void errorneousTransmissionFromMetapackToH24Ftp() {

	final DefaultSoapMockService h24FileServerFtpCarrierUpdateRef = new DefaultSoapMockService(Lists
		.newArrayList(new MockResponsePojo(ResponseType.FAULT, "", "FTP server not available!")));
	final DefaultSoapMockService carrierServiceRef = new DefaultSoapMockService();
	final String receivedRequestFromMetapack = getBase64EncodedRequest(SUCCESS_CSV_FILE,RESOURCE_DIR);

	mockOsbBusinessService(
		"CarrierTrigger/operations/receiveCarrierStatusUpdate/business-service/H24FileServerFtpCarrierUpdateRef",
		h24FileServerFtpCarrierUpdateRef);
	mockOsbBusinessService(
		"CarrierTrigger/operations/receiveCarrierStatusUpdate/business-service/CarrierServiceRef",
		carrierServiceRef);

	try {

	    invokeOsbProxyService(
		    "CarrierTrigger/operations/receiveCarrierStatusUpdate/ReadCarrierStatusUpdate",
		    receivedRequestFromMetapack);
	} catch (Exception e) {

	    waitForInvocationOf(h24FileServerFtpCarrierUpdateRef);

	    assertThat("Message has not been transfered to H24 FTP server",
		    h24FileServerFtpCarrierUpdateRef.hasBeenInvoked(), is(Boolean.TRUE));
	    assertThat("Carrier Service has not been invoked", carrierServiceRef.hasBeenInvoked(),
		    is(Boolean.TRUE));
	    assertThat("Error listener has not been invoked!", carrierStatusErrorListener.hasBeenInvoked(),
		    is(Boolean.TRUE));

	    LOGGER.fine(String.format("Message received by CarrierStatusErrorListener: %s",
		    carrierStatusErrorListener.getLastReceivedRequest()));
	    LOGGER.fine(String.format("Message received by CarrierServiceRef: %s",
		    carrierServiceRef.getLastReceivedRequest()));

	    assertXpathEvaluatesTo("//ns1:requestHeader/ns2:CorrelationID/text()",SUCCESS_CSV_FILE,
		    carrierServiceRef.getLastReceivedRequest());
	    assertXpathEvaluatesTo(
		    "//ns1:toggleCarrierStatusUpdatePollingRequest/ns1:requestHeader/ns2:ActivityID/text()",
		    "P202-INIT", carrierServiceRef.getLastReceivedRequest());
	    assertXpathEvaluatesTo("//ns1:toggleCarrierStatusUpdatePollingRequest/ns1:isActive/text()",
		    Boolean.FALSE.toString(), carrierServiceRef.getLastReceivedRequest());
	    assertExceptionAndFaultCode(carrierStatusErrorListener.getLastReceivedRequest(),
		    FAULT_CODE_TRANSMIT_H24FTP_TRANSMISSION_ERROR);
	    assertXpathEvaluatesTo("//ns3:requestHeader/ns2:CorrelationID/text()", SUCCESS_CSV_FILE,
		    carrierStatusErrorListener.getLastReceivedRequest());
	    assertXpathEvaluatesTo("local-name(//ns3:exception/exception:context/exception:payload/*[1])",
		    "opaqueElement", carrierStatusErrorListener.getLastReceivedRequest());
	}
    }
    
    @Test
    public void readInvalidCsvThatFailsAtXmlCreation() {

	final DefaultSoapMockService writeErrorneousFileRef = new DefaultSoapMockService();

	try {

	    mockOsbBusinessService(
		    "CarrierTrigger/operations/receiveCarrierStatusUpdate/business-service/FileMarkerRef",
		    writeErrorneousFileRef);

	    invokeOsbProxyService(
		    "CarrierTrigger/operations/receiveCarrierStatusUpdate/ReceiveCarrierStatusUpdate",
		    getBase64EncodedRequest(FAILURE_CSV_FILE,RESOURCE_DIR));

	} catch (ServiceException e) {

	    assertThat("CarrireStatusErrorListenerRef was not invoked!",
		    carrierStatusErrorListener.hasBeenInvoked(), is(Boolean.TRUE));
	    assertThat("WriteErrorneousFileRef was not invoked!", writeErrorneousFileRef.hasBeenInvoked(),
		    is(Boolean.TRUE));

	    LOGGER.info(String.format("Message received by CarrierStatusErrorListenerRef: %s",
		    carrierStatusErrorListener.getLastReceivedRequest()));

	    assertExceptionAndFaultCode(carrierStatusErrorListener.getLastReceivedRequest(),
		    FAULT_CODE_NXSD_TRANSFORM_ERROR);
	    assertXpathEvaluatesTo("//ns3:requestHeader/ns2:CorrelationID/text()", RESOURCE_DIR+FAILURE_CSV_FILE,
		    carrierStatusErrorListener.getLastReceivedRequest());
	    assertXpathEvaluatesTo("local-name(//ns3:exception/exception:context/exception:payload/*[1])",
		    "opaqueElement", carrierStatusErrorListener.getLastReceivedRequest());
	}
    }

    @Test
    public void errorWhenSendingCarrierStatusUpdateDataToCarrierStatusUpdateProcess() {

	final DefaultSoapMockService carrierStatusProcessRef = new DefaultSoapMockService(
		Lists.newArrayList(new MockResponsePojo(ResponseType.FAULT,
			"<fault>CarrierStatusProcess could not be invoked!</fault>")));

	try {

	    mockOsbBusinessService(
		    "CarrierTrigger/operations/receiveCarrierStatusUpdate/business-service/CarrierStatusProcessRef",
		    carrierStatusProcessRef);

	    invokeOsbProxyService(
		    "CarrierTrigger/operations/receiveCarrierStatusUpdate/ReceiveCarrierStatusUpdate",
		    getBase64EncodedRequest(SUCCESS_CSV_FILE,RESOURCE_DIR));

	} catch (ServiceException e) {

	    assertThat("CarrireStatusProcessRef was not invoked!", carrierStatusProcessRef.hasBeenInvoked(),
		    is(Boolean.TRUE));
	    assertThat("CarrierStatusErrorListenerRef was not invoked!",
		    carrierStatusErrorListener.hasBeenInvoked(), is(Boolean.TRUE));

	    LOGGER.info(String.format("Message received by CarrierStatusErrorListenerRef: %s",
		    carrierStatusErrorListener.getLastReceivedRequest()));

	    assertExceptionAndFaultCode(carrierStatusErrorListener.getLastReceivedRequest(),
		    FAULT_CODE_TRANSMIT_CARRIER_STATUS_UPD_PROCESS);
	    assertXpathEvaluatesTo("//ns3:requestHeader/ns2:CorrelationID/text()", SALES_ORDER_ID,
		    carrierStatusErrorListener.getLastReceivedRequest());
	    assertXpathEvaluatesTo("local-name(//ns3:exception/exception:context/exception:payload/*[1])",
		    "receiveCarrierStatusUpdateRequest", carrierStatusErrorListener.getLastReceivedRequest());
	}
    }

    @Test
    public void readValidCsvWithMetapackFilterXLStatusCode() {

	DefaultSoapMockService carrierStatusProcessRef = new DefaultSoapMockService();

	mockOsbBusinessService(
		"CarrierTrigger/operations/receiveCarrierStatusUpdate/business-service/CarrierStatusProcessRef",
		carrierStatusProcessRef);

	invokeOsbProxyService(
		"CarrierTrigger/operations/receiveCarrierStatusUpdate/ReceiveCarrierStatusUpdate",
		getBase64EncodedRequest(METAPACK_FILTER_CSV_FILE,RESOURCE_DIR));

	assertThat("CarrierStatusProcess has not been invoked!", carrierStatusProcessRef.hasBeenInvoked(),
		is(false));
	assertThat("CarrierStatusProcess has not been invoked the expected number of times!",
		carrierStatusProcessRef.getNumberOfInvocations(), equalTo(0));
	assertThat("ErrorListener was not invoked!", carrierStatusErrorListener.hasBeenInvoked(),
		is(Boolean.FALSE));
    }
    
    @Test
    public void readValidCsvWithoutMetapackFilter() {

	DefaultSoapMockService carrierStatusProcessRef = new DefaultSoapMockService();

	mockOsbBusinessService(
		"CarrierTrigger/operations/receiveCarrierStatusUpdate/business-service/CarrierStatusProcessRef",
		carrierStatusProcessRef);

	invokeOsbProxyService(
		"CarrierTrigger/operations/receiveCarrierStatusUpdate/ReceiveCarrierStatusUpdate",
		getBase64EncodedRequest(SUCCESS_CSV_FILE,RESOURCE_DIR));

	LOGGER.info(String.format("Message received by CarrierStatusProcessRef: %s",
		carrierStatusProcessRef.getLastReceivedRequest()));

	assertThat("CarrierStatusProcess has been invoked!", carrierStatusProcessRef.hasBeenInvoked(),
		is(true));
	assertThat("CarrierStatusProcess has been invoked the expected number of times!",
		carrierStatusProcessRef.getNumberOfInvocations(), equalTo(1));
	assertXpathEvaluatesTo("count(//ns4:salesOrder)", String.valueOf(1),
		carrierStatusProcessRef.getLastReceivedRequest());
	assertXpathEvaluatesTo("count(//oagis:SalesOrderLine)", String.valueOf(1),
		carrierStatusProcessRef.getLastReceivedRequest());
	assertXpathEvaluatesTo("count(//oagis:Packaging)", String.valueOf(1),
		carrierStatusProcessRef.getLastReceivedRequest());

	assertThat("ErrorListener was not invoked!", carrierStatusErrorListener.hasBeenInvoked(),
		is(Boolean.FALSE));
    }
    
    private void assertExceptionAndFaultCode(String pRequest, String pExpectedFaultCode) {
	assertXpathEvaluatesTo("count(//ns3:exception)", String.valueOf(1), pRequest);
	assertXpathEvaluatesTo("//exception:faultCode/text()", pExpectedFaultCode, pRequest);
    }

    private String getBase64EncodedRequest(String pRequestFilename, String pRequestDirectoryName) {
	String originalFileContent = new ParameterReplacer(readClasspathFile(pRequestDirectoryName+pRequestFilename))
		.replace(REPLACE_PARAM_SALES_ORDER_ID, SALES_ORDER_ID).build();

	final String csvRequestBase64Binary = DatatypeConverter
		.printBase64Binary(originalFileContent.getBytes(StandardCharsets.UTF_8));

	LOGGER.fine(csvRequestBase64Binary);

	return SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			String.format(
				"<opaq:opaqueElement xmlns:opaq=\"http://xmlns.oracle.com/pcbpel/adapter/opaque/\">%s</opaq:opaqueElement>",
				csvRequestBase64Binary)),
		new ParameterReplacer(readClasspathFile("InboundFTPHeaderType.xml"))
			.replace(REPLACE_PARAM_FILENAME, pRequestFilename).build());
    }
}
