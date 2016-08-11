package de.home24.middleware.maintenance;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.MessagingDao;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import org.junit.Before;
import org.junit.Test;

import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Tests for ActivityProcessstatusLoggingRecoveryTriggerTest in application Maintenance
 * <p>
 * Created by svb on 22/07/16.
 */
public class ActivityProcessstatusLoggingRecoveryTriggerTest extends AbstractBaseSoaTest {

    private static final Logger LOGGER = Logger.getLogger(ActivityProcessstatusLoggingRecoveryTriggerTest.class
            .getSimpleName());

    private static final String TIMESTAMP_INTERFACE = "2016-07-20T12:02:39.98+02:00";

    private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
    private static final String REPLACE_PARAM_STATUS_CODE = "STATUS_CODE";
    private static final String REPLACE_PARAM_TIMESTAMP_INTERFACE = "TIMESTAMP_INTERFACE";

    private String correlationId;
//    private DefaultSoapMockService tempLoggingQueueRef;
//    private DefaultSoapMockService insertOtmOsmSoItemsRef;

    @Before
    public void setUp() {

        declareXpathNS("ns3", "http://home24.de/data/common/exceptiontypes/v1");
        declareXpathNS("ns2", "http://xmlns.oracle.com/pcbpel/adapter/db/top/InsertToOtmOsmSoItemRef");
        declareXpathNS("ns1", "http://home24.de/interfaces/bes/ordertransactionservice" +
                "/ordertransactionservicemessages/v1");

        correlationId = String.valueOf(System.currentTimeMillis());

//        tempLoggingQueueRef = new DefaultSoapMockService();
//        insertOtmOsmSoItemsRef = new DefaultSoapMockService();
    }

    @Test
    public void whenMessageHasStatusCodeP201InitThenWriteToOsmSoItems() {

        executeTestWIthRelevantCloseShipmentStatusCode("P201-INIT");
    }

    @Test
    public void whenMessageHasStatusCodeP201CloseShpmThenWriteToOsmSoItems() {

        executeTestWIthRelevantCloseShipmentStatusCode("P201-CLOSE-SHPM");
    }

    private void executeTestWIthRelevantCloseShipmentStatusCode(String pStatusCode) {
        final String errorMessage = new ParameterReplacer(readClasspathFile
                ("../queues/h24_ActivityProcessstatusLogging/ERR_ActivityProcessstatusLogging_Q" +
                        "/ErrorLoggingFaultTemplate.xml")).replace(REPLACE_PARAM_CORRELATION_ID, correlationId)
                .replace(REPLACE_PARAM_STATUS_CODE, pStatusCode).replace(REPLACE_PARAM_TIMESTAMP_INTERFACE,
                        TIMESTAMP_INTERFACE).build();

        LOGGER.info(String.format("########## Error message: %s", errorMessage));

        DefaultSoapMockService tempLoggingQueueRef = new DefaultSoapMockService();
        DefaultSoapMockService insertOtmOsmSoItemsRef = new DefaultSoapMockService();

        mockOsbBusinessService("ActivityProcessstatusLoggingRecoveryTrigger/operations" +
                "/recoverCloseShipmentLoggingErrors/business-service/TempLoggingQueueRef", tempLoggingQueueRef);
        mockOsbBusinessService("ActivityProcessstatusLoggingRecoveryTrigger/operations" +
                "/recoverCloseShipmentLoggingErrors/business-service/InsertToOtmOsmSoItemRef", insertOtmOsmSoItemsRef);

        getOsbAccessor().flushUriChanges();

        getMessagingDao().writeToQueue(MessagingDao.JmsModule.LOGGING, "h24jms.ERR_ActivityProcessstatusLogging_Q",
                errorMessage);

//        waitForInvocationOf(insertOtmOsmSoItemsRef);
//
//        assertThat("TempLoggingQueueRef has been invoked", tempLoggingQueueRef.hasBeenInvoked(), equalTo(false));
//        assertThat("InsertOtmOsmSoItemsRef has not been invoked", insertOtmOsmSoItemsRef.hasBeenInvoked(), equalTo
// (true));
//
//
//        final String expectedRequest = new ParameterReplacer(readClasspathFile
//                ("../servicebus/Maintenance/ActivityProcessstatusLoggingRecoveryTrigger" +
//                        "/recoverCloseShipmentLoggingErrors/.xml")).replace
//                (REPLACE_PARAM_CORRELATION_ID, correlationId).replace(REPLACE_PARAM_STATUS_CODE, pStatusCode).build();
//
//        assertXmlEquals(expectedRequest, insertOtmOsmSoItemsRef.getLastReceivedRequest());
    }


    @Test
    public void whenMessageDoesHaveOtherCloseShipmentStatusCodeThenDoNothing() {

        final String purchaseOrderGroupHandlingStatusCode = "P201-UPD-SO-LINE";

        final String errorMessage = new ParameterReplacer(readClasspathFile
                ("../queues/h24_ActivityProcessstatusLogging/ERR_ActivityProcessstatusLogging_Q" +
                        "/ErrorLoggingFaultTemplate.xml")).replace(REPLACE_PARAM_CORRELATION_ID, correlationId)
                .replace(REPLACE_PARAM_STATUS_CODE, purchaseOrderGroupHandlingStatusCode).replace
                        (REPLACE_PARAM_TIMESTAMP_INTERFACE,
                        TIMESTAMP_INTERFACE).build();

        LOGGER.info(String.format("########## Error message: %s", errorMessage));

        DefaultSoapMockService tempLoggingQueueRef = new DefaultSoapMockService();
        DefaultSoapMockService insertOtmOsmSoItemsRef = new DefaultSoapMockService();

        mockOsbBusinessService("ActivityProcessstatusLoggingRecoveryTrigger/operations" +
                "/recoverCloseShipmentLoggingErrors/business-service/TempLoggingQueueRef", tempLoggingQueueRef);
        mockOsbBusinessService("ActivityProcessstatusLoggingRecoveryTrigger/operations" +
                "/recoverCloseShipmentLoggingErrors/business-service/InsertToOtmOsmSoItemRef", insertOtmOsmSoItemsRef);

        getOsbAccessor().flushUriChanges();

        getMessagingDao().writeToQueue(MessagingDao.JmsModule.LOGGING, "h24jms.ERR_ActivityProcessstatusLogging_Q",
                errorMessage);

//        waitForInvocationOf(insertOtmOsmSoItemsRef);
//
//        assertThat("TempLoggingQueueRef has been invoked", tempLoggingQueueRef.hasBeenInvoked(), equalTo(true));
//        assertThat("InsertOtmOsmSoItemsRef has not been invoked", insertOtmOsmSoItemsRef.hasBeenInvoked(), equalTo
//                (false));
//
//        assertXpathEvaluatesTo("count(//exc:exception)", "1", tempLoggingQueueRef.getLastReceivedRequest());
    }

    @Test
    public void whenMessageDoesHaveOtherCloseShipmentRelatedStatusCodeShpmThenEnqueueToTempQueue() {

        final String purchaseOrderGroupHandlingStatusCode = "P1001-LBL";

        final String errorMessage = new ParameterReplacer(readClasspathFile
                ("../queues/h24_ActivityProcessstatusLogging/ERR_ActivityProcessstatusLogging_Q" +
                        "/ErrorLoggingFaultTemplate.xml")).replace(REPLACE_PARAM_CORRELATION_ID, correlationId)
                .replace(REPLACE_PARAM_STATUS_CODE, purchaseOrderGroupHandlingStatusCode).replace
                        (REPLACE_PARAM_TIMESTAMP_INTERFACE,
                                TIMESTAMP_INTERFACE).build();

        LOGGER.info(String.format("########## Error message: %s", errorMessage));

        DefaultSoapMockService tempLoggingQueueRef = new DefaultSoapMockService();
        DefaultSoapMockService insertOtmOsmSoItemsRef = new DefaultSoapMockService();

        mockOsbBusinessService("ActivityProcessstatusLoggingRecoveryTrigger/operations" +
                "/recoverCloseShipmentLoggingErrors/business-service/TempLoggingQueueRef", tempLoggingQueueRef);
        mockOsbBusinessService("ActivityProcessstatusLoggingRecoveryTrigger/operations" +
                "/recoverCloseShipmentLoggingErrors/business-service/InsertToOtmOsmSoItemRef", insertOtmOsmSoItemsRef);

        getOsbAccessor().flushUriChanges();

        getMessagingDao().writeToQueue(MessagingDao.JmsModule.LOGGING, "h24jms.ERR_ActivityProcessstatusLogging_Q",
                errorMessage);

//        waitForInvocationOf(insertOtmOsmSoItemsRef);
//
//        assertThat("TempLoggingQueueRef has been invoked", tempLoggingQueueRef.hasBeenInvoked(), equalTo(false));
//        assertThat("InsertOtmOsmSoItemsRef has not been invoked", insertOtmOsmSoItemsRef.hasBeenInvoked(), equalTo
//                (false));
    }
}
