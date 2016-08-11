package de.home24.middleware.carrierservice;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.Before;

import com.opitzconsulting.soa.testing.MockService;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Base test implementation for CarrierService
 * createLabelAndShippingInstructions.
 * 
 * @author svb
 *
 */
public class AbstractCreateLabelAndShippingInstructionsTest extends AbstractBaseSoaTest {

    protected final static Logger LOGGER = Logger
	    .getLogger(AbstractCreateLabelAndShippingInstructionsTest.class.getSimpleName());

    protected final static String PATH_CARRIER_SERVICE = "CarrierService/exposed/v1/CarrierService";
    protected final static String PATH_METAPACK_API = "CarrierService/shared/v1/business-service/MetapapackBlackBoxBusinessService";
    protected final static String PATH_JMS_TRIGGER = "CarrierService/operations/createLabelAndShippingInstructions/business-service/CarrierTriggerJMSQueue";
    protected final static String PATH_CARRIER_CALLBACK = "CarrierService/exposed/v1/business-service/CarrierServiceCallBack";
    protected final static String PATH_CREATE_LABEL_AND_SHIPPING_INSTR_INTERNAL = "CarrierService/operations/createLabelAndShippingInstructions/business-service/CreateLabelAndShippingInstructionsInternalRef.";

    protected final String REPLACE_PARAM_MONTH_YEAR = "MONTH_YEAR";
    protected final String[] metapackOkResponse = new String[4];
    protected final String[] jmsH24FileExpectedRequest = new String[4];
    protected final String[] jmsH24FileRealRequest = new String[4];

    protected int metapackMessageCounter = 0;
    protected int jmsH24FileMessageCounter = 0;
    protected String carrierRequest;
    protected String carrierRealResponse;
    protected String messageId;

    protected String callbackUrl;

    /**
     * Creates a string containing month and year information in the form yyMM
     * 
     * @return the filepath part as string
     */
    protected final String getMonthAndYearFilepathPart() {

	final SimpleDateFormat dateFormat = new SimpleDateFormat("yyMM");
	final String monthYearAsPartOfFilePath = dateFormat.format(new Date());

	return monthYearAsPartOfFilePath;
    }

    @Before
    public void setUp() {

	declareXpathNS("csi", "http://home24.de/interfaces/bas/carrier/carrierservicemessages/v1");
	declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
	declareXpathNS("blac", "http://xlogics.eu/blackbox");
	declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	declareXpathNS("wsa", "http://www.w3.org/2005/08/addressing");

	messageId = UUID.randomUUID().toString();

	metapackOkResponse[0] = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		readClasspathFile("metapackOkResponse1.xml"));
	metapackOkResponse[1] = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		readClasspathFile("metapackOkResponse2.xml"));
	metapackOkResponse[2] = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		readClasspathFile("metapackOkResponse3.xml"));

	jmsH24FileExpectedRequest[0] = new ParameterReplacer(
		readClasspathFile("putH24FileServerDocumentRequest1.xml"))
			.replace(REPLACE_PARAM_MONTH_YEAR, getMonthAndYearFilepathPart()).build();
	jmsH24FileExpectedRequest[1] = new ParameterReplacer(
		readClasspathFile("putH24FileServerDocumentRequest2.xml"))
			.replace(REPLACE_PARAM_MONTH_YEAR, getMonthAndYearFilepathPart()).build();
	jmsH24FileExpectedRequest[2] = new ParameterReplacer(
		readClasspathFile("putH24FileServerDocumentRequest3.xml"))
			.replace(REPLACE_PARAM_MONTH_YEAR, getMonthAndYearFilepathPart()).build();
	jmsH24FileExpectedRequest[3] = new ParameterReplacer(
		readClasspathFile("putH24FileServerDocumentRequest4.xml"))
			.replace(REPLACE_PARAM_MONTH_YEAR, getMonthAndYearFilepathPart()).build();

	mockOsbBusinessService(PATH_METAPACK_API, new MockService() {
	    @Override
	    public String serviceCallReceived(String serviceName, String requestStr)
		    throws ServiceException, Exception {
		return metapackOkResponse[metapackMessageCounter++];
	    }
	});

	mockOsbBusinessService(PATH_JMS_TRIGGER, new MockService() {
	    @Override
	    public String serviceCallReceived(String serviceName, String requestStr)
		    throws ServiceException, Exception {
		jmsH24FileRealRequest[jmsH24FileMessageCounter++] = requestStr;
		return requestStr;
	    }
	});

	setUpOverrideable();

	carrierRequest = SoapUtil.getInstance().setSoapHeader(SoapVersion.SOAP11,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			readClasspathFile("createLabelAndShippingInstructionsRequest.xml")),
		SoapUtil.getInstance().messageIdHeader(messageId),
		SoapUtil.getInstance().relatesToHeader(messageId),
		SoapUtil.getInstance().replyToHeader(callbackUrl));
    }

    protected void setUpOverrideable() {
    }
}
