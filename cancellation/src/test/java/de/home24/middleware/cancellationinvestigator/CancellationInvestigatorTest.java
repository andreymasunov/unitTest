package de.home24.middleware.cancellationinvestigator;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmSo;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.OtmDao.Query;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.mock.RetryWithExceptionSoapMockService;

public class CancellationInvestigatorTest extends AbstractBaseSoaTest {

	private class QueryBALByCorrelationIDAndActivityCode implements Query<BalActivities> {

		private String CORRELATION_ID = "30115002345288";

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

	private class QueryOSMSOByCorrelationIDAndActivityCode implements Query<OsmSo> {

		private String CORRELATION_ID = "30115002345288";

		private String activityCode = null;

		QueryOSMSOByCorrelationIDAndActivityCode(String activityCode) {
			this.activityCode = activityCode;
		}

		@Override
		public Class<OsmSo> getExpectedType() {
			return OsmSo.class;
		}

		@Override
		public String getQuery() {
			return String.format("select * from osm_so where correlation_id = ? and status_code=?");
		}

		@Override
		public Object[] getQueryParameters() {

			String[] params = { CORRELATION_ID, activityCode };
			return params;

		}
	}

	private final static Logger LOGGER = LoggerFactory.getLogger(CancellationInvestigatorTest.class);

	public static final String REPLACE_PARAM_NOTE_PURCHASINGORDER = "NOTE_PURCHASING_ORDER";
	public static final String REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL = "NOTE_SUPPRESS_PO_MAIL";
	public static final String REPLACE_PARAM_NAME_EDI_PARTNER_NAME = "NAME_EDI_PARTNER_NAME";
	public static final String REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE = "ORDERS_IS_ACTIVE";
	public static final String REPLACE_PARAM_NAME_EDI_PROCESS_AVAILABLE = "EDI_PROCESS_AVAILABLE";

	public static final String COMPOSITE = "CancellationInvestigatorProcess";
	public static final String REVISION = "1.2.0.2";
	public static final String PROCESS = "CancellationInvestigatorProcess_ep";

	private AbstractSoapMockService salesOrderServiceRef;
	private AbstractSoapMockService genericFaultHandlerRef;
	private AbstractSoapMockService orderTransactionServiceRef;

	private String compositeRequestXML;

	public CancellationInvestigatorTest() {
		super("generic");
	}

	@Before
	public void setUp() throws Exception {

		declareXpathNS("cancelinvest",
				"http://home24.de/interfaces/bps/cancellationinvestigatorprocess/cancellationinvestigatorprocessmessages/v1");

		getOtmDao().delete(createDeleteQueryForTable("bal_activities"));
		getOtmDao().delete(createDeleteQueryForTable("osm_so"));

		compositeRequestXML = readClasspathFile("ProcessCancellationInvestigatorRequest.xml");

		// set default values for mocked services (for order transaction service
		// default makes no sense)
		salesOrderServiceRef = new DefaultSoapMockService(readClasspathFile("GetCancellationSubStatusResponse.xml"));
		genericFaultHandlerRef = new DefaultSoapMockService(readClasspathFile("GenericFaultHandlerResendOutput.xml"));
	}

	private Query<Void> createDeleteQueryForTable(final String pTablename) {
		return new Query<Void>() {

			@Override
			public String getQuery() {
				return String.format("delete from %s where correlation_id = ?", pTablename);
			}

			@Override
			public Object[] getQueryParameters() {
				return new Object[] { "30115002345288" };
			}

			@Override
			public Class<Void> getExpectedType() {
				return Void.class;
			}
		};
	}

	@Test
	public void cancellationRequested() {

		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceRef);

		orderTransactionServiceRef = new DefaultSoapMockService(
				readClasspathFile("GetCancellationStatusResponse_CancelRequest.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);

		// in case that a cancellation request has been detected the process
		// waits for a mid-process callback so we
		// need to mock this call in order to receive the async callback from
		// the initial call
		Thread threadMidprocessReceive = new Thread() {
			public void run() {

				try {
					sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				String soapRequest = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
						readClasspathFile("ReceiveCancellationRequest.xml"));

				String syncProcessResponse = invokeCompositeService(COMPOSITE, REVISION, PROCESS, soapRequest);

				LOGGER.info(String.format("Response received from one-way call to receiveCancellation: %s",
						syncProcessResponse));

			}
		};

		threadMidprocessReceive.start();

		String compositeResponse = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, compositeRequestXML));

		LOGGER.info(String.format("Response received from callback: %s", compositeResponse));

		assertThat("OrderTransactionService has not been invoked", orderTransactionServiceRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has been invoked", salesOrderServiceRef.hasBeenInvoked(), is(Boolean.FALSE));
		assertThat("GenericFaultHandler has been invoked", genericFaultHandlerRef.hasBeenInvoked(), is(Boolean.FALSE));

		assertXmlEquals(readClasspathFile("CancellationInvestigatorResponse_CancelRequest.xml"),
				evaluateXpath("//cancelinvest:processCancellationInvestigationResponse", compositeResponse));

		// P290-GET-CSTAT must be written once to BAL
		assertThat("BAL not written for P290-GET-CSTAT",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-GET-CSTAT")).size() == 1);

		// P290-GET-CSUB must NOT be written to BAL
		assertThat("BAL written for P290-GET-CSUB",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-GET-CSUB")).size() == 0);

		// P290-WAIT must be written once to BAL and OTM (SO)
		assertThat("BAL not written for P290-WAIT",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-WAIT")).size() == 1);
		assertThat("OTM SO not written for P290-WAIT",
				getOtmDao().query(new QueryOSMSOByCorrelationIDAndActivityCode("P290-WAIT")).size() == 1);

		// P290-CANCEL must be written once to BAL and OTM (SO)
		assertThat("BAL not for P290-CANCEL",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-CANCEL")).size() == 1);
		assertThat("OTM SO not written for P290-CANCEL",
				getOtmDao().query(new QueryOSMSOByCorrelationIDAndActivityCode("P290-CANCEL")).size() == 1);
	}

	@Test
	public void cancellationCompleted() {

		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceRef);

		orderTransactionServiceRef = new DefaultSoapMockService(
				readClasspathFile("GetCancellationStatusResponse_Cancelled.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);

		String compositeResponse = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, compositeRequestXML));

		LOGGER.info(String.format("Response received from callback: %s", compositeResponse));

		assertThat("OrderTransactionService has not been invoked", orderTransactionServiceRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has not been invoked", salesOrderServiceRef.hasBeenInvoked(), is(Boolean.TRUE));
		assertThat("GenericFaultHandler has been invoked", genericFaultHandlerRef.hasBeenInvoked(), is(Boolean.FALSE));

		assertXmlEquals(readClasspathFile("CancellationInvestigatorResponse_Cancelled.xml"),
				evaluateXpath("//cancelinvest:processCancellationInvestigationResponse", compositeResponse));

		// P290-GET-CSTAT must be written once to BAL
		assertThat("BAL not written for P290-GET-CSTAT",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-GET-CSTAT")).size() == 1);

		// P290-GET-CSUB must be written once to BAL
		assertThat("BAL not written for P290-GET-CSUB",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-GET-CSUB")).size() == 1);

		// P290-WAIT must NOT be written to BAL and OTM (SO)
		assertThat("BAL written for P290-WAIT",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-WAIT")).size() == 0);
		assertThat("OTM SO written for P290-WAIT",
				getOtmDao().query(new QueryOSMSOByCorrelationIDAndActivityCode("P290-WAIT")).size() == 0);

		// P290-CANCEL must NOT be written to BAL and OTM (SO)
		assertThat("BAL written for P290-CANCEL",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-CANCEL")).size() == 0);
		assertThat("OTM SO written for P290-CANCEL",
				getOtmDao().query(new QueryOSMSOByCorrelationIDAndActivityCode("P290-CANCEL")).size() == 0);

	}

	@Test
	public void noCancellation() {

		orderTransactionServiceRef = new DefaultSoapMockService(
				readClasspathFile("GetCancellationStatusResponse_NoCancelAction.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);

		String compositeResponse = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, compositeRequestXML));

		LOGGER.info(String.format("Response received from callback: %s", compositeResponse));

		assertThat("OrderTransactionService has not been invoked", orderTransactionServiceRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has been invoked", salesOrderServiceRef.hasBeenInvoked(), is(Boolean.FALSE));
		assertThat("GenericFaultHandler has been invoked", genericFaultHandlerRef.hasBeenInvoked(), is(Boolean.FALSE));

		assertXmlEquals(readClasspathFile("CancellationInvestigatorResponse_NoCancelAction.xml"),
				evaluateXpath("//cancelinvest:processCancellationInvestigationResponse", compositeResponse));

		// P290-GET-CSTAT must be written once to BAL
		assertThat("BAL not written for P290-GET-CSTAT",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-GET-CSTAT")).size() == 1);

		// P290-GET-CSUB must NOT be written to BAL
		assertThat("BAL written for P290-GET-CSUB",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-GET-CSUB")).size() == 0);

		// P290-WAIT must NOT be written to BAL and OTM (SO)
		assertThat("BAL written for P290-WAIT",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-WAIT")).size() == 0);
		assertThat("OTM SO written for P290-WAIT",
				getOtmDao().query(new QueryOSMSOByCorrelationIDAndActivityCode("P290-WAIT")).size() == 0);

		// P290-CANCEL must NOT be written to BAL and OTM (SO)
		assertThat("BAL written for P290-CANCEL",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-CANCEL")).size() == 0);
		assertThat("OTM SO written for P290-CANCEL",
				getOtmDao().query(new QueryOSMSOByCorrelationIDAndActivityCode("P290-CANCEL")).size() == 0);

	}

	@Test
	public void errorInOrderTransactionServiceCall() {

		orderTransactionServiceRef = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.BUSINESS_FAULT, ""), 
				new MockResponsePojo(ResponseType.SOAP_RESPONSE,
						readClasspathFile("GetCancellationStatusResponse_NoCancelAction.xml")));

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);

		genericFaultHandlerRef = new DefaultSoapMockService(readClasspathFile("GenericFaultHandlerResendOutput.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);

		// the instance should fail when calling the OrderTransactionService for
		// the first time, the second time should succeed
		String compositeResponse = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, compositeRequestXML));

		LOGGER.info(String.format("Response received from callback: %s", compositeResponse));

		assertThat("OrderTransactionService has not been invoked twice",
				orderTransactionServiceRef.getNumberOfInvocations() == 2);
		assertThat("SalesOrderService has been invoked", salesOrderServiceRef.hasBeenInvoked(), is(Boolean.FALSE));
		assertThat("GenericFaultHandler has not been invoked", genericFaultHandlerRef.hasBeenInvoked(),
				is(Boolean.TRUE));

		// the result of the composite all must be identical to the "normal"
		// flow without the exception
		assertXmlEquals(readClasspathFile("CancellationInvestigatorResponse_NoCancelAction.xml"),
				evaluateXpath("//cancelinvest:processCancellationInvestigationResponse", compositeResponse));

		// P290-GET-CSTAT must be written to BAL once (after the retry was
		// successful)
		assertThat("BAL not written for P290-GET-CSTAT",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-GET-CSTAT")).size() == 1);

		// P290-GET-CSUB must NOT be written to BAL
		assertThat("BAL written for P290-GET-CSUB",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-GET-CSUB")).size() == 0);

		// P290-WAIT must NOT be written to BAL and OTM (SO)
		assertThat("BAL written for P290-WAIT",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-WAIT")).size() == 0);
		assertThat("OTM SO written for P290-WAIT",
				getOtmDao().query(new QueryOSMSOByCorrelationIDAndActivityCode("P290-WAIT")).size() == 0);

		// P290-CANCEL must NOT be written to BAL and OTM (SO)
		assertThat("BAL written for P290-CANCEL",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-CANCEL")).size() == 0);
		assertThat("OTM SO written for P290-CANCEL",
				getOtmDao().query(new QueryOSMSOByCorrelationIDAndActivityCode("P290-CANCEL")).size() == 0);

	}

	@Test
	public void errorInSalesOrderServiceCall() {

		genericFaultHandlerRef = new DefaultSoapMockService(readClasspathFile("GenericFaultHandlerResendOutput.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerRef);

		salesOrderServiceRef = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.BUSINESS_FAULT, readClasspathFile("GetCancelSubStatusExceptionOutput.xml")), 
				new MockResponsePojo(ResponseType.SOAP_RESPONSE,  readClasspathFile("GetCancellationSubStatusResponse.xml")));

		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceRef);

		final DefaultSoapMockService orderTransactionServiceRef = new DefaultSoapMockService(
				readClasspathFile("GetCancellationStatusResponse_Cancelled.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "OrderTransactionService", orderTransactionServiceRef);

		// the instance should fail when calling the SalesOrderService for the
		// first time, the second time should succeed
		String compositeResponse = invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, compositeRequestXML));

		LOGGER.info(String.format("Response received from callback: %s", compositeResponse));

		assertThat("OrderTransactionService has not been invoked", orderTransactionServiceRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has not been invoked twice", salesOrderServiceRef.getNumberOfInvocations() == 2);
		assertThat("GenericFaultHandler has not been invoked", genericFaultHandlerRef.hasBeenInvoked(),
				is(Boolean.TRUE));

		// the result of the composite all must be identical to the "normal"
		// flow without the exception
		assertXmlEquals(readClasspathFile("CancellationInvestigatorResponse_Cancelled.xml"),
				evaluateXpath("//cancelinvest:processCancellationInvestigationResponse", compositeResponse));

		// P290-GET-CSTAT must be written to BAL
		assertThat("BAL not written for P290-GET-CSTAT",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-GET-CSTAT")).size() == 1);

		// P290-GET-CSUB must be written to BAL once (after the retry was
		// successful)
		assertThat("BAL written for P290-GET-CSUB",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-GET-CSUB")).size() == 1);

		// P290-WAIT must NOT be written to BAL and OTM (SO)
		assertThat("BAL written for P290-WAIT",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-WAIT")).size() == 0);
		assertThat("OTM SO written for P290-WAIT",
				getOtmDao().query(new QueryOSMSOByCorrelationIDAndActivityCode("P290-WAIT")).size() == 0);

		// P290-CANCEL must NOT be written to BAL and OTM (SO)
		assertThat("BAL written for P290-CANCEL",
				getOtmDao().query(new QueryBALByCorrelationIDAndActivityCode("P290-CANCEL")).size() == 0);
		assertThat("OTM SO written for P290-CANCEL",
				getOtmDao().query(new QueryOSMSOByCorrelationIDAndActivityCode("P290-CANCEL")).size() == 0);

	}
}
