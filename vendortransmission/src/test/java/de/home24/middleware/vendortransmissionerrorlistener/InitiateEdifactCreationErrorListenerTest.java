package de.home24.middleware.vendortransmissionerrorlistener;

import static org.hamcrest.MatcherAssert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmSo;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.OtmDao.Query;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class InitiateEdifactCreationErrorListenerTest extends AbstractBaseSoaTest {
	
	private class QueryBALByCorrelationIDAndActivityCode implements Query<BalActivities> {

		private String CORRELATION_ID = randomCorrelationId;

		private String activityCode = null;

		QueryBALByCorrelationIDAndActivityCode(String activityCode) {
			this.activityCode = activityCode;
		}

		@Override
		public Class<BalActivities> getExpectedType() {
			return BalActivities.class;
		}

		@Override
		public String getQuery() {
			return String.format("select * from bal_activities where correlation_id = ? and activity_code=?");
		}

		@Override
		public Object[] getQueryParameters() {

			String[] params = { CORRELATION_ID, activityCode };
			return params;

		}
	}

	private class QueryOSMPOByCorrelationIDAndActivityCode implements Query<OsmSo> {

		private String CORRELATION_ID = randomCorrelationId;

		private String activityCode = null;

		QueryOSMPOByCorrelationIDAndActivityCode(String activityCode) {
			this.activityCode = activityCode;
		}

		@Override
		public Class<OsmSo> getExpectedType() {
			return OsmSo.class;
		}

		@Override
		public String getQuery() {
			return String.format("select * from osm_po where correlation_id = ? and status_code=?");
		}

		@Override
		public Object[] getQueryParameters() {

			String[] params = { CORRELATION_ID, activityCode };
			return params;

		}
	}
	
	private static final Logger LOGGER = Logger.getLogger(InitiateEdifactCreationErrorListenerTest.class.getSimpleName());
	private static final String PATH_EDIFACT_ERR_PROXY = "VendorTransmissionErrorListener/operations/receiveEdifactCreationErrorMessage/ReceiveEdifactCreationErrorMessage";
	private static final String PATH_EDIFACT_CALLBACK_REF = "VendorTransmissionErrorListener/operations/receiveEdifactCreationErrorMessage/business-service/VendorTransmissionServiceCallback";
	private static final String PATH_OTM_SERVICE_REF = "VendorTransmissionErrorListener/shared/business-service/OrderTransactionBusinessService";
	private static final String PATH_RETRY_WRAPPER_REF = "VendorTransmissionErrorListener/shared/business-service/ResponseRetryWrapperService";
	private static final String RPLC_CORRELATION_ID = "CORRELATION_ID";
	private static final String RPLC_PURCHASE_ORDER_NUM = "PURCHASE_ORDER_NUM";
		
	private String randomCorrelationId, randomPoNum, randomMessageId, invocationResult  = "";

	private DefaultSoapMockService initiateEDIFACTCallbackExpMock = null;
	private DefaultSoapMockService initiateEDIFACTCallbackExpFaultMock = null;
	private DefaultSoapMockService responseRetryWrapperMock = null;
	private DefaultSoapMockService orderTransactionServiceMock = null;
	private List<MockResponsePojo> initiateEDIFACTCallbackExpMockPojoList = new ArrayList<MockResponsePojo>();

	@BeforeClass
	public static void setUpBeforeClass() {
		testInitialization();
	}
	
	@Before
	public void setUp() {
		
		declareXpathNS("exp", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("rrw", "http://home24.de/interfaces/bas/responseretrywrapper/responseretrywrappermessages/v1");
		declareXpathNS("efm", "http://home24.de/data/navision/edifactmessages/v1");
		declareXpathNS("mht", "http://home24.de/data/common/messageheadertypes/v1");
		declareXpathNS("edf", "http://home24.de/data/navision/edifact/v1");
		declareXpathNS("wsa05", "http://www.w3.org/2005/08/addressing");
		declareXpathNS("vtm", "http://home24.de/interfaces/bas/vendortransmissionservice/vendortransmissionservicemessages/v1");	
			
		Random randomNumber = new Random();
		randomCorrelationId = String.valueOf(randomNumber.nextInt(1000000));
		randomPoNum = "DS" + String.valueOf(randomNumber.nextInt(1000000));
		randomMessageId = String.valueOf(randomNumber.nextInt(1000000));
		
		LOGGER.info("+++Setup Mocks+++");
		
		initiateEDIFACTCallbackExpMock = new DefaultSoapMockService("");
		responseRetryWrapperMock = new DefaultSoapMockService("");	
		orderTransactionServiceMock = new DefaultSoapMockService("");	
		initiateEDIFACTCallbackExpMockPojoList.add(new MockResponsePojo(ResponseType.FAULT,""));
		initiateEDIFACTCallbackExpFaultMock = new DefaultSoapMockService(initiateEDIFACTCallbackExpMockPojoList);

//		getOtmDao().delete(createDeleteQueryForTable("bal_activities"));
//		getOtmDao().delete(createDeleteQueryForTable("osm_po"));

	}
	
	private Query<Void> createDeleteQueryForTable(final String pTablename) {
		return new Query<Void>() {

			@Override
			public String getQuery() {
				return String.format("delete from %s where correlation_id = ?", pTablename);
			}

			@Override
			public Object[] getQueryParameters() {
				return new Object[] { randomCorrelationId };
			}

			@Override
			public Class<Void> getExpectedType() {
				return Void.class;
			}
		};
	}

	@After
	public void tearDown() {
		LOGGER.info("+++Delete Mocks+++");
		
		initiateEDIFACTCallbackExpMock = null;
		initiateEDIFACTCallbackExpFaultMock = null;
		responseRetryWrapperMock = null;
		orderTransactionServiceMock = null;
	}
	
	@Test
	public void InitiateEdifactErrorListenerHappyPathSuccess() {
		
		final String initiateEdifactCallbackMockUri = mockOsbBusinessService(PATH_EDIFACT_CALLBACK_REF, initiateEDIFACTCallbackExpMock);
		mockOsbBusinessService(PATH_RETRY_WRAPPER_REF, responseRetryWrapperMock);	
		
		final String requestString = new ParameterReplacer(
				readClasspathFile("initiateedifactprocessing/Request_initiateEdifactCreationException.xml"))
						.replace(RPLC_CORRELATION_ID, randomCorrelationId)
						.replace(RPLC_PURCHASE_ORDER_NUM, randomPoNum)
						.build();	
		
		setOsbServiceReplacement(
				"VendorTransmissionErrorListener/operations/receiveEdifactCreationErrorMessage/pipeline/ReceiveEdifactCreationErrorQueuePipeline",
				"\\$replyToURI",
				String.format("'%s'", initiateEdifactCallbackMockUri));
				
		invokeOsbProxyService(PATH_EDIFACT_ERR_PROXY, requestString);
			
		assertThat("InitiateEdifactCallback Exception called with success. ", initiateEDIFACTCallbackExpMock.getNumberOfInvocations()==1);
		assertThat("ResponseRetryWrapperMock not called. ", responseRetryWrapperMock.getNumberOfInvocations()==0);
		
		// P1002-EDIFACT-INIT-ERR must be written once to BAL and OTM (PO)
		assertThat("BAL written for P1002-EDIFACT-INIT-ERR",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P1002-EDIFACT-INIT-ERR")).size() == 1);
		assertThat("OTM PO written for P1002-EDIFACT-INIT-ERR",
				getOtmDao().query(new QueryOSMPOByCorrelationIDAndActivityCode("P1002-EDIFACT-INIT-ERR")).size() == 1);
	}
	
	@Test
	public void InitiateEdifactErrorListenerWithException() {
	
		mockOsbBusinessService(PATH_EDIFACT_CALLBACK_REF, initiateEDIFACTCallbackExpFaultMock);
		mockOsbBusinessService(PATH_RETRY_WRAPPER_REF, responseRetryWrapperMock);
		
		final String requestString = new ParameterReplacer(
				readClasspathFile("initiateedifactprocessing/Request_initiateEdifactCreationException.xml"))
						.replace(RPLC_CORRELATION_ID, randomCorrelationId)
						.replace(RPLC_PURCHASE_ORDER_NUM, randomPoNum)
						.build();	
		
		try {
			invokeOsbProxyService(PATH_EDIFACT_ERR_PROXY, requestString);
		} catch (ServiceException e) {
			e.printStackTrace();
			LOGGER.info("+++invocation exception expected = " + e.getXml());
			
			assertThat("InitiateEdifactCallback Exception not called. ", initiateEDIFACTCallbackExpMock.getNumberOfInvocations()==0);
			assertThat("Response Retry Wrapper called. ", responseRetryWrapperMock.getNumberOfInvocations()==1);
			assertThat("InitiateEdifactCallback Fault called. ", initiateEDIFACTCallbackExpFaultMock.getNumberOfInvocations()==1);

		}	
	}	
}
