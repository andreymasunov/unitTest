package de.home24.middleware;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;

import de.home24.middleware.carrierservice.AbstractCreateLabelAndShippingInstructionsTest;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;

public class SubmitTrackingNumber extends AbstractBaseSoaTest {

    protected final static Logger logger = Logger
	    .getLogger(AbstractCreateLabelAndShippingInstructionsTest.class.getSimpleName());

    protected final static String PATH_SERVICE = "CarrierService/exposed/v1/CarrierService";
    protected final static String PATH_METAPACK_API = "CarrierService/shared/v1/business-service/MetapapackBlackBoxBusinessService";

	private String randomCorrelationId;

	private DefaultSoapMockService metapackSuccessMock;
	private DefaultSoapMockService metapackBusinessFaultMock;
	private DefaultSoapMockService metapackTechnicalFaultMock;
	private List<MockResponsePojo> metapackTechnicalFaultMockPojoList;


	@Before
	public void setUp() {

		Random randomNumber = new Random();
		randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));

		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");

		logger.info("+++Create Mocks+++");
		metapackTechnicalFaultMockPojoList.add(new MockResponsePojo(ResponseType.FAULT, "", ""));
		metapackTechnicalFaultMock = new DefaultSoapMockService(metapackTechnicalFaultMockPojoList);
		metapackBusinessFaultMock = new DefaultSoapMockService("");
		metapackSuccessMock = new DefaultSoapMockService("");
		
	}

	@After
	public void tearDown() {
		logger.info("+++Delete Mocks+++");
		metapackTechnicalFaultMockPojoList = null;
		metapackTechnicalFaultMock = null;
		metapackBusinessFaultMock = null;
		metapackSuccessMock = null;
	}

    
}
