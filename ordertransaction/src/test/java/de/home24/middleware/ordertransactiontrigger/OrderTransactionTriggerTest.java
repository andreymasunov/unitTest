package de.home24.middleware.ordertransactiontrigger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.util.List;
import java.util.Random;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmCustComm;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.BaseQuery;
import de.home24.middleware.octestframework.components.BaseQuery.SqlOp;
import de.home24.middleware.octestframework.components.MessagingDao.JmsModule;
import de.home24.middleware.octestframework.components.QueryPredicate;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Tests for OrderTransactionTrigger
 * 
 * @author svb
 *
 */
public class OrderTransactionTriggerTest extends AbstractBaseSoaTest {

	Random randomNumber = new Random();

	private static final String PARAMETER_ACTIVITY_ID = "ACTIVITY_ID";
	private static final String PARAMETER_CORRELATION_ID = "CORRELATION_ID";
	private static final String PARAMETER_TIMESTAMP_INTERFACE = "TIMESTAMP_INTERFACE";
	private static final String PARAMETER_STATUS_OBJECT = "STATUS_OBJECT";

	private static final String ACTIVITY_ID_KNOWN = "P101-SIISNAV";
	private static final String ACTIVITY_ID_UNKNOWN = "P101-SIISNAV1";

	private static final String ACTIVITY_ID_CANCELLATION = "P203-PUBLISH";
	private static final String ACTIVITY_ID_CARRIERSTATUS = "P202-INIT";
	private static final String ACTIVITY_ID_CLOSESHIPMENT = "P201-INIT";
	private static final String ACTIVITY_ID_POGENERATION = "P1000-PO-INIT";
	private static final String ACTIVITY_ID_POPROCESSING = "P1001-LBL";
	private static final String ACTIVITY_ID_SALESORDERCREATION = "P101-PT-ACK";

	private static final String STATUS_OBJECT_SALESORDER = "SalesOrder";

	private static final String CORRELATION_ID = "10101000385761";

	private static final String TIMESTAMP_WITH_MILLISECONDS = "2016-02-10T21:00:00.000+01:00";
	private static final String TIMESTAMP_WITHOUT_MILLISECONDS = "2016-02-10T21:00:00+01:00";

	private DefaultSoapMockService activityProcessStatusMonitoringQRef;

	@Before
	public void setUp() {

		declareXpathNS("ns1",
				"http://home24.de/interfaces/bts/ordertransactiontrigger/ordertransactiontriggermessages/v1");
		declareXpathNS("ns2", "http://home24.de/data/custom/processmonitoring/v1");
		declareXpathNS("ns3", "http://home24.de/data/common/exceptiontypes/v1");

		activityProcessStatusMonitoringQRef = new DefaultSoapMockService();
	}

	@After
	public void tearDown() {

		getOtmDao().delete(new BaseQuery<BalActivities>(SqlOp.DELETE,
				new QueryPredicate("correlation_id", CORRELATION_ID).withEquals("activity_code", ACTIVITY_ID_KNOWN),
				BalActivities.class));
		getOtmDao().delete(new BaseQuery<BalActivities>(SqlOp.DELETE,
				new QueryPredicate("correlation_id", CORRELATION_ID).withEquals("activity_code", ACTIVITY_ID_UNKNOWN),
				BalActivities.class));
		getOtmDao().delete(new BaseQuery<OsmCustComm>(SqlOp.DELETE,
				new QueryPredicate("correlation_id", CORRELATION_ID).withEquals("status_code", ACTIVITY_ID_KNOWN),
				OsmCustComm.class));
		getOtmDao().delete(new BaseQuery<OsmCustComm>(SqlOp.DELETE,
				new QueryPredicate("correlation_id", CORRELATION_ID).withEquals("status_code", ACTIVITY_ID_UNKNOWN),
				OsmCustComm.class));
	}

	@Test
	public void updateTransactionMonitoringWithExceptionSuccessfullOtmInsertion() throws Exception {

		final String requestString = readClasspathFile("UpdateTransactionMonitoringWithExceptionInput.xml");

		mockOsbBusinessService("OrderTransactionTrigger/shared/business-service/BamQueueRef",
				activityProcessStatusMonitoringQRef);

		final String serviceResponse = invokeOsbProxyService(
				"OrderTransactionTrigger/exposed/v1/OrderTransactionTrigger",
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
						new ParameterReplacer(requestString).replace(PARAMETER_ACTIVITY_ID, ACTIVITY_ID_KNOWN)
								.replace(PARAMETER_CORRELATION_ID, CORRELATION_ID).build()));

		assertXpathEvaluatesTo(
				"count(//ns1:updateTransactionMonitoringWithExceptionResponse/ns1:successfulUpdateResponse)", "1",
				serviceResponse);

		waitForInvocationOf(activityProcessStatusMonitoringQRef);

		assertThat("ActivityProcessStatusMonitoringQRef has not been invoked!",
				activityProcessStatusMonitoringQRef.hasBeenInvoked(), is(true));

		verifyBalActivityEntry();
	}

	@Test
	public void whenSendingCancellationOTMRequestWhereTimestampHasNoMillisecondsTheMillisecondsAreAdded() {

		sendOTMRequestTimestampWithoutMilliseconds(ACTIVITY_ID_CANCELLATION);

	}

	@Test
	public void whenSendingCarrierStatusOTMRequestWhereTimestampHasNoMillisecondsTheMillisecondsAreAdded() {

		sendOTMRequestTimestampWithoutMilliseconds(ACTIVITY_ID_CARRIERSTATUS);

	}

	@Test
	public void whenSendingCloseShipmentOTMRequestWhereTimestampHasNoMillisecondsTheMillisecondsAreAdded() {

		sendOTMRequestTimestampWithoutMilliseconds(ACTIVITY_ID_CLOSESHIPMENT);

	}

	@Test
	public void whenSendingSalesOrderCreationOTMRequestWhereTimestampHasNoMillisecondsTheMillisecondsAreAdded() {

		sendOTMRequestTimestampWithoutMilliseconds(ACTIVITY_ID_SALESORDERCREATION);

	}

	@Test
	public void whenSendingPOGenerationOTMRequestWhereTimestampHasNoMillisecondsTheMillisecondsAreAdded() {

		sendOTMRequestTimestampWithoutMilliseconds(ACTIVITY_ID_POGENERATION);

	}

	@Test
	public void whenSendingPOProcessingOTMRequestWhereTimestampHasNoMillisecondsTheMillisecondsAreAdded() {

		sendOTMRequestTimestampWithoutMilliseconds(ACTIVITY_ID_POPROCESSING);

	}

	@Test
	public void whenSendingCancellationOTMRequestWhereTimestampHasMillisecondsTheTimestampRemainsUntouched() {

		sendOTMRequestTimestampWithMilliseconds(ACTIVITY_ID_CANCELLATION);

	}

	@Test
	public void whenSendingCarrierStatusOTMRequestWhereTimestampHasMillisecondsTheTimestampRemainsUntouched() {

		sendOTMRequestTimestampWithMilliseconds(ACTIVITY_ID_CARRIERSTATUS);

	}

	@Test
	public void whenSendingCloseShipmentOTMRequestWhereTimestampHasMillisecondsTheTimestampRemainsUntouched() {

		sendOTMRequestTimestampWithMilliseconds(ACTIVITY_ID_CLOSESHIPMENT);

	}

	@Test
	public void whenSendingPOGenerationOTMRequestWhereTimestampHasMillisecondsTheTimestampRemainsUntouched() {

		sendOTMRequestTimestampWithMilliseconds(ACTIVITY_ID_POGENERATION);

	}

	@Test
	public void whenSendingPOProcessingOTMRequestWhereTimestampHasMillisecondsTheTimestampRemainsUntouched() {

		sendOTMRequestTimestampWithMilliseconds(ACTIVITY_ID_POPROCESSING);

	}

	@Test
	public void whenSendingSalesOrderCreationOTMRequestWhereTimestampHasMillisecondsTheTimestampRemainsUntouched() {

		sendOTMRequestTimestampWithMilliseconds(ACTIVITY_ID_SALESORDERCREATION);

	}

	@Test
	public void updateTransactionMonitoringSuccessfullOtmInsertion() {

		String requestString = readClasspathFile("UpdateTransactionMonitoring_SII.xml");

		mockOsbBusinessService("OrderTransactionTrigger/shared/business-service/BamQueueRef",
				activityProcessStatusMonitoringQRef);

		getOsbAccessor().flushUriChanges();

		getMessagingDao().writeToQueue(JmsModule.LOGGING, "h24jms.REQ_ActivityProcessstatusLogging_Q",
				new ParameterReplacer(requestString).replace(PARAMETER_ACTIVITY_ID, ACTIVITY_ID_KNOWN)
						.replace(PARAMETER_CORRELATION_ID, CORRELATION_ID).build());

		waitForInvocationOf(activityProcessStatusMonitoringQRef);

		assertThat("ActivityProcessStatusMonitoringQRef has not been invoked!",
				activityProcessStatusMonitoringQRef.hasBeenInvoked(), is(true));

		verifyBalActivityEntry();
	}

	@Test
	public void updateTransactionMonitoringWithExceptionWithFailureDuringOtmInsertionDueToUnknownActivityCode() {

		final String requestString = readClasspathFile("UpdateTransactionMonitoringWithExceptionInput.xml");

		try {
			invokeOsbProxyService("OrderTransactionTrigger/exposed/v1/OrderTransactionTrigger",
					SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
							new ParameterReplacer(requestString).replace(PARAMETER_CORRELATION_ID, CORRELATION_ID)
									.replace(PARAMETER_ACTIVITY_ID, ACTIVITY_ID_UNKNOWN).build()));
		} catch (ServiceException e) {

			assertThat("ActivityProcessStatusMonitoringQRef has not been invoked!",
					activityProcessStatusMonitoringQRef.hasBeenInvoked(), is(false));

			assertXpathEvaluatesTo("count(//ns3:exception)", "1", e.getXml());
		}

	}

	void verifyBalActivityEntry() {

		final List<BalActivities> activities = getOtmDao().query(new BaseQuery<BalActivities>(SqlOp.SELECT,
				new QueryPredicate("correlation_id", CORRELATION_ID).withEquals("activity_code", ACTIVITY_ID_KNOWN),
				BalActivities.class));

		assertThat("No entry found in BAL_ACTIVITIES", activities, hasSize(1));
		assertThat("CorrelationId invalid", activities.get(0).getCorrelationId(), equalTo(CORRELATION_ID));
		assertThat("ActivityCode invalid", activities.get(0).getActivityCode(), equalTo(ACTIVITY_ID_KNOWN));
	}

	void verifyBalActivityEntry(String correlationID) {

		final List<BalActivities> activities = getOtmDao().query(new BaseQuery<BalActivities>(SqlOp.SELECT,
				new QueryPredicate("correlation_id", correlationID), BalActivities.class));

		assertThat("No entry found in BAL_ACTIVITIES", activities, hasSize(1));
	}

	private void sendOTMRequestTimestampWithoutMilliseconds(String activityID) {
		String correlationID = String.valueOf(randomNumber.nextInt(1000000));

		String requestString = readClasspathFile("UpdateTransactionMonitoring_Generic.xml");

		mockOsbBusinessService("OrderTransactionTrigger/shared/business-service/BamQueueRef",
				activityProcessStatusMonitoringQRef);

		getOsbAccessor().flushUriChanges();

		getMessagingDao().writeToQueue(JmsModule.LOGGING, "h24jms.REQ_ActivityProcessstatusLogging_Q",
				new ParameterReplacer(requestString).replace(PARAMETER_ACTIVITY_ID, activityID)
						.replace(PARAMETER_CORRELATION_ID, correlationID)
						.replace(PARAMETER_TIMESTAMP_INTERFACE, TIMESTAMP_WITHOUT_MILLISECONDS)
						.replace(PARAMETER_STATUS_OBJECT, STATUS_OBJECT_SALESORDER).build());

		waitForInvocationOf(activityProcessStatusMonitoringQRef);

		assertThat("ActivityProcessStatusMonitoringQRef has not been invoked!",
				activityProcessStatusMonitoringQRef.hasBeenInvoked(), is(true));

		verifyBalActivityEntry(correlationID);

		assertXpathEvaluatesTo("//ns2:timestampInterface/text()", TIMESTAMP_WITH_MILLISECONDS,
				activityProcessStatusMonitoringQRef.getLastReceivedRequest());
	}

	private void sendOTMRequestTimestampWithMilliseconds(String activityID) {
		String correlationID = String.valueOf(randomNumber.nextInt(1000000));

		String requestString = readClasspathFile("UpdateTransactionMonitoring_Generic.xml");

		mockOsbBusinessService("OrderTransactionTrigger/shared/business-service/BamQueueRef",
				activityProcessStatusMonitoringQRef);

		getOsbAccessor().flushUriChanges();

		getMessagingDao().writeToQueue(JmsModule.LOGGING, "h24jms.REQ_ActivityProcessstatusLogging_Q",
				new ParameterReplacer(requestString).replace(PARAMETER_ACTIVITY_ID, activityID)
						.replace(PARAMETER_CORRELATION_ID, correlationID)
						.replace(PARAMETER_TIMESTAMP_INTERFACE, TIMESTAMP_WITH_MILLISECONDS)
						.replace(PARAMETER_STATUS_OBJECT, STATUS_OBJECT_SALESORDER).build());

		waitForInvocationOf(activityProcessStatusMonitoringQRef);

		assertThat("ActivityProcessStatusMonitoringQRef has not been invoked!",
				activityProcessStatusMonitoringQRef.hasBeenInvoked(), is(true));

		verifyBalActivityEntry(correlationID);

		assertXpathEvaluatesTo("//ns2:timestampInterface/text()", TIMESTAMP_WITH_MILLISECONDS,
				activityProcessStatusMonitoringQRef.getLastReceivedRequest());
	}
}
