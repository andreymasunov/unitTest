package de.home24.middleware.carrierstatusprocess;

import static de.home24.middleware.octestframework.components.BaseQuery.createBALQuery;
import static de.home24.middleware.octestframework.components.BaseQuery.createDeleteOSMSOItemQuery;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertTrue;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmParcel;
import de.home24.middleware.entity.OsmPo;
import de.home24.middleware.entity.OsmSoItem;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.OtmDao.Query;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.GenericFaultHandlerMock;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.GenericFaultHandlerMock.FaultStrategy;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Created by TUN on 02.02.2016.
 */
public class CarrierStatusProcessTest extends AbstractBaseSoaTest {

    // Basic configurations
	
	private static final String RESOURCE_DIR = "../processes/GenericProcesses/CarrierStatusProcess/";
    public static final String COMPOSITE = "CarrierStatusProcess";
	public static final String COMPOSITEFH = "GenericFaultHandlerService";
	public static final String REVISION = "1.4.1.0";
	public static final String PROCESS = "CarrierStatusProcessDelegator_ep";
	public static final String P202INIT = "P202-INIT";
	public static final String P202INITERR = "P202-INIT-ERR";
	public static final String P202UPDSOLINE = "P202-UPD-SO-LINE";
	public static final String P202UPDSOLINECB = "P202-UPD-SO-LINE-CB";
	public static final String P202CLSSHIPMDONE = "P202-CLOSE-SHIPM-DONE";
	private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";	
    private static final String CORRELATION_ID = "30201002711693";  
    
    // List of MockResponses
    

    private List<MockResponsePojo> salesOrderServiceMockList;    

    private List<MockResponsePojo> genericFaultHandlerServiceMockList;

    // List of MockServices

    private DefaultSoapMockService salesOrderServiceMock;

    private DefaultSoapMockService genericFaultHandlerServiceMock;

    // Logger

    private static final Logger LOGGER =
        Logger.getLogger(CarrierStatusProcessTest.class.getSimpleName());
      

    public CarrierStatusProcessTest() {
        super("generic");
    }
   
    @Before
    public void beforeTest() throws Exception {
    	declareXpathNS("xsi", "http://www.w3.org/2001/XMLSchema-instance");
		declareXpathNS("ns3",
				"http://home24.de/interfaces/bas/genericfaulthandler/genericfaulthandlerservicemessages/v1");
		declareXpathNS("ns2", "http://home24.de/data/common/exceptiontypes/v1");
        LOGGER.info("+++Clean OTM DB+++");
        getOtmDao().delete(createDeleteQueryForTable("bal_activities"));
        getOtmDao().delete(createDeleteQueryForTable("osm_parcel"));
        getOtmDao().delete(createDeleteQueryForTable("osm_so_item"));
        
    }
    public void setUp() {
		
	}
	
   	@After
    public void afterTest() throws Exception {

        // Null MockResponses

        salesOrderServiceMockList = null;

        genericFaultHandlerServiceMockList = null;

        // Null MockServices

        salesOrderServiceMock = null;

        genericFaultHandlerServiceMock = null;

    }

    @Test
    public void updateSalesOrderLine() {
        // Init happy path UpdateSalesOrderLine
		LOGGER.info("+++Invoke updateSalesOrderLine+++");
        LOGGER.info("+++Create Mocks+++");

        genericFaultHandlerServiceMockList = new ArrayList<>();
        genericFaultHandlerServiceMockList.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
        		readClasspathFile(RESOURCE_DIR +"carrierStatusProcess_Reponse-handleFaultResponse_Quit.xml")));
        genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerServiceMockList);

        salesOrderServiceMockList = new ArrayList<>();
        salesOrderServiceMockList.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
                                                                   readClasspathFile(RESOURCE_DIR +
                                                                                     "salesOrderService_Response_updateSalesOrderLine.xml")));
        salesOrderServiceMock = new DefaultSoapMockService(salesOrderServiceMockList);

        // Mock References
        LOGGER.info("+++Mock Composite References+++");

        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);
        mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);

        // Invocation
        LOGGER.info("+++Invoke Composite Service+++");

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                               SoapUtil.getInstance().soapEnvelope(SoapUtil.SoapVersion.SOAP11,
                                                                   readClasspathFile(RESOURCE_DIR +
                                                                                     "carrierStatusProcess_Request_UpdateCarrierStatus.xml")));
        waitForInvocationOf(salesOrderServiceMock,1);
        // Evaluate assertions

        LOGGER.info("+++Evaluate assertions+++");
        
        assertTrue(salesOrderServiceMock.hasBeenInvoked());
        assertOTM(CORRELATION_ID, P202INIT);
        assertOTM(CORRELATION_ID, P202UPDSOLINE);
        assertOTM(CORRELATION_ID, P202UPDSOLINECB);

        assertOSM(CORRELATION_ID, P202INIT);
        assertOSM(CORRELATION_ID, P202UPDSOLINE);
        assertOSM(CORRELATION_ID, P202UPDSOLINECB);

    }

    @Test
    public void updateSalesOrderLineCloseShipment() {
        // Init happy path UpdateSalesOrderLine
		LOGGER.info("+++Invoke updateSalesOrderLineCloseShipment+++");
        LOGGER.info("+++Create Mocks+++");

        genericFaultHandlerServiceMockList = new ArrayList<>();
        genericFaultHandlerServiceMockList.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
        		readClasspathFile(RESOURCE_DIR +"carrierStatusProcess_Reponse-handleFaultResponse_Quit.xml")));
        genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerServiceMockList);

        salesOrderServiceMockList = new ArrayList<>();
        salesOrderServiceMockList.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
                                                                   readClasspathFile(RESOURCE_DIR +
                                                                                     "salesOrderService_Response_updateSalesOrderLine.xml")));
        salesOrderServiceMock = new DefaultSoapMockService(salesOrderServiceMockList);

        // Mock References
        LOGGER.info("+++Mock Composite References+++");

        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);
        mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);

        // Invocation
        LOGGER.info("+++Invoke Composite Service+++");

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                               SoapUtil.getInstance().soapEnvelope(SoapUtil.SoapVersion.SOAP11,
                                                                   readClasspathFile(RESOURCE_DIR +
                                                                                     "carrierStatusProcess_Request_UpdateCarrierStatusCloseShipment.xml")));
      //Wait for sometime for OTM/OSM activities
        long timeout = 5000;
        try {
			Thread.sleep(timeout);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // Evaluate assertions

        LOGGER.info("+++Evaluate assertions+++");

        assertTrue(!salesOrderServiceMock.hasBeenInvoked());
        assertOTM(CORRELATION_ID, P202CLSSHIPMDONE);
        assertOSM(CORRELATION_ID, P202CLSSHIPMDONE);      
                
    }

    @Test
    public void updateBothSalesOrderLineAndCloseShipment() {
        // Init happy path UpdateSalesOrderLine
		LOGGER.info("+++Invoke updateBothSalesOrderLineAndCloseShipment+++");
        LOGGER.info("+++Create Mocks+++");

        genericFaultHandlerServiceMockList = new ArrayList<>();
        genericFaultHandlerServiceMockList.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
        		readClasspathFile(RESOURCE_DIR +"carrierStatusProcess_Reponse-handleFaultResponse_Quit.xml")));
        genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerServiceMockList);

        salesOrderServiceMockList = new ArrayList<>();
        salesOrderServiceMockList.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
                                                                   readClasspathFile(RESOURCE_DIR +
                                                                                     "salesOrderService_Response_updateSalesOrderLine.xml")));
        salesOrderServiceMock = new DefaultSoapMockService(salesOrderServiceMockList);

        // Mock References
        LOGGER.info("+++Mock Composite References+++");

        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);
        mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);

        // Invocation
        LOGGER.info("+++Invoke Composite Service+++");

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                               SoapUtil.getInstance().soapEnvelope(SoapUtil.SoapVersion.SOAP11,
                                                                   readClasspathFile(RESOURCE_DIR +
                                                                                     "carrierStatusProcess_Request_UpdateBothCarrierStatusAndCloseShipment.xml")));
      //Wait for sometime for OTM/OSM activities
        long timeout = 5000;
        try {
			Thread.sleep(timeout);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        // Evaluate assertions

        LOGGER.info("+++Evaluate assertions+++");

        assertTrue(salesOrderServiceMock.hasBeenInvoked());
        assertOTM(CORRELATION_ID, P202CLSSHIPMDONE);
        assertOSM(CORRELATION_ID, P202CLSSHIPMDONE);
        
        assertOTM(CORRELATION_ID, P202INIT);
        assertOTM(CORRELATION_ID, P202UPDSOLINE);
        assertOTM(CORRELATION_ID, P202UPDSOLINECB);

        assertOSM(CORRELATION_ID, P202INIT);
        assertOSM(CORRELATION_ID, P202UPDSOLINE);
        assertOSM(CORRELATION_ID, P202UPDSOLINECB);
        
    }

    
    @Test
    public void updateSalesOrderLineWithError() {
        // Init happy path UpdateSalesOrderLine
		LOGGER.info("+++Invoke updateSalesOrderLineWithError+++");
        LOGGER.info("+++Create Mocks+++");
        final String handleFaultResponse =
                new ParameterReplacer(readClasspathFile(RESOURCE_DIR +"carrierStatusProcess_Reponse-handleFaultResponse_Quit.xml")).replace("FAULT_TRANSACTION_ID",
                                                                                                                            CORRELATION_ID).replace("FAULT_STRATEGY",
                                                                                                                                                              "Abort").replace("FAULT_PAYLOAD",
                                                                                                                                                                               "EMPTY").build();

        genericFaultHandlerServiceMockList = new ArrayList<>();
        genericFaultHandlerServiceMockList.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE,
                                                                    handleFaultResponse));
        genericFaultHandlerServiceMock = new DefaultSoapMockService(genericFaultHandlerServiceMockList);
                
        final String responseFault =
                new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "BusinessFault.xml")).build();

        salesOrderServiceMockList = new ArrayList<>();
        salesOrderServiceMockList.add(new MockResponsePojo(MockResponsePojo.ResponseType.SOAP_RESPONSE, responseFault,
                "error"));   
        
        salesOrderServiceMock = new DefaultSoapMockService(salesOrderServiceMockList);

        // Mock References
        LOGGER.info("+++Mock Composite References+++");

        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceMock);
        mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMock);

        // Invocation
        LOGGER.info("+++Invoke Composite Service+++");

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                               SoapUtil.getInstance().soapEnvelope(SoapUtil.SoapVersion.SOAP11,
                                                                   readClasspathFile(RESOURCE_DIR +
                                                                                     "carrierStatusProcess_Request_UpdateCarrierStatus.xml")));
        waitForInvocationOf(salesOrderServiceMock,5);
        waitForInvocationOf(genericFaultHandlerServiceMock,1);
        // Evaluate assertions

        LOGGER.info("+++Evaluate assertions+++");

        assertTrue(salesOrderServiceMock.hasBeenInvoked());
        assertTrue(genericFaultHandlerServiceMock.hasBeenInvoked());
        assertOTM(CORRELATION_ID, P202INIT);
        assertOTM(CORRELATION_ID, "P202-UPD-SO-LINE");
        

        assertOSM(CORRELATION_ID, P202INIT);
        assertOSM(CORRELATION_ID, "P202-UPD-SO-LINE");
        

    }

    @Test
  	public void receiveCarrierStatusUpdateRequestSuccess() throws Exception {

		LOGGER.info("+++Invoke receiveCarrierStatusUpdateRequestSuccess+++");
  		final DefaultSoapMockService salesOrderServiceMockRef = new DefaultSoapMockService(
  				readClasspathFile(RESOURCE_DIR + "UpdateSalesOrderLineResponse.xml"));
  		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);

  		final String requestXML = new ParameterReplacer(
  				readClasspathFile(RESOURCE_DIR + "ReceiveCarrierStatusUpdateRequest.xml"))
  						.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID).build();

  		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
  				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

  		waitForInvocationOf(salesOrderServiceMockRef);

  		assertThat("SalesOrderService has been invoked", salesOrderServiceMockRef.hasBeenInvoked(), is(Boolean.TRUE));

  		assertOsmParcel(queryOtm(CORRELATION_ID, P202INIT), P202INIT);

  		assertOsmParcel(queryOtm(CORRELATION_ID, P202UPDSOLINE), P202UPDSOLINE);

  		assertOsmParcel(queryOtm(CORRELATION_ID, P202UPDSOLINECB), P202UPDSOLINECB);
  	}
      
      @Test
  	public void receiveCarrierStatusUpdateRequestException() throws Exception {

		LOGGER.info("+++Invoke receiveCarrierStatusUpdateRequestException+++");
  		final DefaultSoapMockService salesOrderServiceMockRef = new DefaultSoapMockService(
  				readClasspathFile(RESOURCE_DIR + "UpdateSalesOrderLineResponseException.xml"));
  		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);

  		final AbstractSoapMockService genericFaultHandlerMockRef = new GenericFaultHandlerMock(FaultStrategy.ABORT);
  		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerMockRef);

  		final String requestXML = new ParameterReplacer(
  				readClasspathFile(RESOURCE_DIR + "ReceiveCarrierStatusUpdateRequest.xml"))
  						.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID).build();

  		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
  				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

  		waitForInvocationOf(salesOrderServiceMockRef);

  		assertThat("SalesOrderService has been invoked", salesOrderServiceMockRef.hasBeenInvoked(), is(Boolean.TRUE));

  		waitForInvocationOf(genericFaultHandlerMockRef);

  		assertThat("GenericFaultHandlerService has been invoked", genericFaultHandlerMockRef.hasBeenInvoked(),
  				is(Boolean.TRUE));

  		String messageToGFH = genericFaultHandlerMockRef.getLastReceivedRequest();

  		LOGGER.info(String.format("++++++++++++++++++++++++++ Received message: %s", messageToGFH));

  		assertXpathEvaluatesTo(
  				"//ns3:handleFaultRequest/ns3:faultInformation/ns2:exception/ns2:context/ns2:activityId/text()",
  				"P202-UPD-SO-LINE-ERR", messageToGFH);
  	}
      
    private class QueryOSMParcelByCorrelationIDAndActivityCode implements Query<OsmPo> {
        private String correlationId = null;
        private String activityCode = null;

        QueryOSMParcelByCorrelationIDAndActivityCode(String correlationId, String activityCode) {
            this.correlationId = correlationId;
            this.activityCode = activityCode;
        }

        @Override
        public Class<OsmPo> getExpectedType() {
            return OsmPo.class;
        }

        @Override
        public String getQuery() {
            return String.format("select * from OSM_PARCEL where correlation_id = ? and status_code=?");
        }

        @Override
        public Object[] getQueryParameters() {

            String[] params = { correlationId, activityCode };
            return params;
        }
    }
    
  
    void assertOsmParcel(List<OsmParcel> varOsmParcel, String statusCode) {

		assertThat(String.format("No entry found for activityCode [%s] in OTM!", statusCode), varOsmParcel.size(),
				is(3));
	}
    List<OsmParcel> queryOtm(final String correlationId, final String statusCode) throws SQLException {

		return getOtmDao().query(new Query<OsmParcel>() {

			@Override
			public String getQuery() {
				return "select * from osm_parcel where correlation_id = ? and status_code = ?";
			}

			@Override
			public Object[] getQueryParameters() {
				return new Object[] { correlationId, statusCode };
			}

			@Override
			public Class<OsmParcel> getExpectedType() {
				return OsmParcel.class;
			}

		});
	}
    private class QueryOsmSoItemByCorrelationIDAndActivityCode implements Query<OsmSoItem> {
        private String correlationId = null;
        private String activityCode = null;

        QueryOsmSoItemByCorrelationIDAndActivityCode(String correlationId, String activityCode) {
            this.correlationId = correlationId;
            this.activityCode = activityCode;
        }

        @Override
        public Class<OsmSoItem> getExpectedType() {
            return OsmSoItem.class;
        }

        @Override
        public String getQuery() {
            return String.format("select * from OSM_SO_ITEM where correlation_id = ? and status_code = ? ");
        }

        @Override
        public Object[] getQueryParameters() {

            String[] params = { correlationId, activityCode };
            return params;
        }
    }

    private void assertOTM(final String correlationId, final String expectedActivityCode) {
        List<BalActivities> balActivity = getOtmDao().query(createBALQuery(correlationId, expectedActivityCode));

        assertThat("Same number of entries found for " + expectedActivityCode, balActivity, hasSize(1));
        assertThat("ActivityCode meet expectation!", balActivity.get(0).getActivityCode(),
                   equalTo(expectedActivityCode));
        assertThat("CorrelationId meet expectation!", balActivity.get(0).getCorrelationId(),
                   equalTo(correlationId));
        assertThat("Error flag is not set properly!", balActivity.get(0).getError(), equalTo("N"));

    }

    private void assertOSM(final String correlationId, final String expectedActivityCode) {
        assertThat("OSM Parcel should be written for " + expectedActivityCode,
                   getOtmDao().query(new QueryOSMParcelByCorrelationIDAndActivityCode(correlationId,
                                                                                  expectedActivityCode)).size() == 1);
        assertThat("OSM SalesOrderItems should be written for " + expectedActivityCode,
                   getOtmDao().query(new QueryOsmSoItemByCorrelationIDAndActivityCode(correlationId,
                                                                                      expectedActivityCode)).size() ==
                   1);
    }    
  
    private Query<Void> createDeleteQueryForTable(final String pTablename) {
		return new Query<Void>() {

			@Override
			public String getQuery() {
				return String.format("delete from %s where correlation_id = ?", pTablename);
			}

			@Override
			public Object[] getQueryParameters() {
				return new Object[] { CORRELATION_ID };
			}

			@Override
			public Class<Void> getExpectedType() {
				return Void.class;
			}
		};
	}
}
