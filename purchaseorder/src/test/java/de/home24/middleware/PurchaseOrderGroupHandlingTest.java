package de.home24.middleware;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmPo;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.BaseQuery;
import de.home24.middleware.octestframework.components.QueryPredicate;
import de.home24.middleware.octestframework.components.BaseQuery.SqlOp;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.RetryWithExceptionSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class PurchaseOrderGroupHandlingTest extends AbstractBaseSoaTest {

	private final static String CORRELATION_ID = "1231376876";

	public static final String REPLACE_PARAM_NOTE_PURCHASINGORDER = "NOTE_PURCHASING_ORDER";
	public static final String REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL = "NOTE_SUPPRESS_PO_MAIL";
	public static final String REPLACE_PARAM_NAME_EDI_PARTNER_NAME = "NAME_EDI_PARTNER_NAME";
	public static final String REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE = "ORDERS_IS_ACTIVE";
	public static final String REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE = "NO_EDI_PROCESS_AVAILABLE";
	public static final String REPLACE_PARAM_METAPACK_ERROR_CODE = "METAPACK_ERROR_CODE";
	public static final String RESOURCE_DIR = "processes/Dropship/PurchaseOrderGroupHandlingProcess/";

	public static final String COMPOSITE = "PurchaseOrderGroupHandlingProcess";
	public static final String REVISION = "1.4.0.0";
	public static final String PROCESS = "PurchaseOrderGroupHandlingDelegator_ep";

	private AbstractSoapMockService carrierServiceMockRef, salesOrderServiceMockRef, printingServiceMockRef, 
	informVendorProcessMockRef, ediVendorTransmissionOutboundProcessMockRef, attachmentService, 
	genericFaultHandler, itemMasterServiceMockRef, purchaseOrderServiceMockRef, purchaseOrderGroupHandlingProcessRef;

	public PurchaseOrderGroupHandlingTest() {
		super("dropship");
	}

	@Before
	public void setUp() throws Exception {

		declareXpathNS("exception", "http://home24.de/data/common/exceptiontypes/v1");
		declareXpathNS("po",
				"http://home24.de/interfaces/bps/processpurchaseordergroup/purchaseordergrouphandlingprocessmessages/v1");
		declareXpathNS("header", "http://home24.de/data/common/messageheadertypes/v1");

		getOtmDao().delete(createDeleteBALQuery());
		getOtmDao().delete(createDeleteOSMPOQuery());

		carrierServiceMockRef = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml"));
		salesOrderServiceMockRef = new DefaultSoapMockService(
				readClasspathFile(RESOURCE_DIR + "ReceiveSalesOrderLineUpdatedRequest.xml"));
		
		printingServiceMockRef = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "PrintingServiceOutput.xml"));
		
		informVendorProcessMockRef = new DefaultSoapMockService("");
		ediVendorTransmissionOutboundProcessMockRef = new DefaultSoapMockService("");
		attachmentService = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "AttachmentServiceResponse.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "InformVendorProcess", informVendorProcessMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "EDIVendorTransmissionOutboundProcess",
				ediVendorTransmissionOutboundProcessMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "AttachmentService", attachmentService);
	}
	
	@After
	public void tearDown() throws Exception {
		carrierServiceMockRef = null;
		salesOrderServiceMockRef = null;
		informVendorProcessMockRef = null;
		ediVendorTransmissionOutboundProcessMockRef = null;
		attachmentService = null;
		genericFaultHandler = null;
		printingServiceMockRef = null;
		itemMasterServiceMockRef = null;
		purchaseOrderServiceMockRef = null;
		purchaseOrderGroupHandlingProcessRef = null;
		
		}

	@Test
	public void noLabelCreationAndPoMailOnly() throws Exception {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "DNK-9").build();

		final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
						.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
						.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
						.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());
		final DefaultSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
						.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(informVendorProcessMockRef);

		assertThat("ItemMasterService has not been invoked", itemMasterServiceMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("PurchaseOrderService has not been invoked", purchaseOrderServiceMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("PrintingService has not been invoked", printingServiceMockRef.hasBeenInvoked(), is(Boolean.TRUE));
		assertThat("InformVendorService has not been invoked", informVendorProcessMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));

		assertThat("SalesOrderService has been invoked", salesOrderServiceMockRef.hasBeenInvoked(), is(Boolean.FALSE));
		assertThat("CarrierService has been invoked", carrierServiceMockRef.hasBeenInvoked(), is(Boolean.FALSE));
		assertThat("EDIVendorTransmissionProcess has been invoked",
				ediVendorTransmissionOutboundProcessMockRef.hasBeenInvoked(), is(Boolean.FALSE));

		// P1001-GET-PET must be written once to BAL
		assertThat("BAL not written for P1001-GET-PET",
				getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 1);

		// P1001-GET-VENDOR must be written once to BAL
		assertThat("BAL not written for P1001-GET-VENDOR",
				getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 1);

		// P1001-LBL must NOT be written once to BAL
		assertThat("BAL written for P1001-LBL",
				getOtmDao().query(createBALQuery("P1001-LBL")).size() == 0);

		// P1001-LBL-CB must NOT be written once to BAL
		assertThat("BAL written for P1001-LBL-CB",
				getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 0);

		// P1001-MRG-PDF must NOT be written once to BAL
		assertThat("BAL not written for P1001-MRG-PDF",
				getOtmDao().query(createBALQuery("P1001-MRG-PDF")).size() == 0);

		// P1001-UPD-SO-LINE must NOT be written once to BAL
		assertThat("BAL written for P1001-UPD-SO-LINE",
				getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE")).size() == 0);

		// P1001-UPD-SO-LINE-CB must NOT be written once to BAL
		assertThat("BAL written for P1001-UPD-SO-LINE-CB",
				getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE-CB")).size() == 0);

		// P1001-DELNT must be written once to BAL
		assertThat("BAL not written for P1001-DELNT",
				getOtmDao().query(createBALQuery("P1001-DELNT")).size() == 1);

		// P1001-DELNT-CB must be written once to BAL
		assertThat("BAL not written for P1001-DELNT-CB",
				getOtmDao().query(createBALQuery("P1001-DELNT-CB")).size() == 1);

		// P1001-CALL-P1002 must NOT be written once to BAL
		assertThat("BAL written for P1001-CALL-P1002",
				getOtmDao().query(createBALQuery("P1001-CALL-P1002")).size() == 0);

		// P1001-CALL-P1003 must be written once to BAL
		assertThat("BAL not written for P1001-CALL-P1003",
				getOtmDao().query(createBALQuery("P1001-CALL-P1003")).size() == 1);

	}

	@Test
	public void labelCreationAndPoMailOnly() throws Exception {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

		final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
						.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
						.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
						.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

		final DefaultSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
						.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(informVendorProcessMockRef);

		assertThat("ItemMasterService has not been invoked", itemMasterServiceMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("PurchaseOrderService has not been invoked", purchaseOrderServiceMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("SalesOrderService has not been invoked", salesOrderServiceMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("CarrierService has not been invoked", carrierServiceMockRef.hasBeenInvoked(), is(Boolean.TRUE));
		assertThat("PrintingService has not been invoked", printingServiceMockRef.hasBeenInvoked(), is(Boolean.TRUE));
		assertThat("InformVendorService has not been invoked", informVendorProcessMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));

		assertThat("EDIVendorTransmissionProcess has been invoked",
				ediVendorTransmissionOutboundProcessMockRef.hasBeenInvoked(), is(Boolean.FALSE));

		// P1001-GET-PET must be written once to BAL
		assertThat("BAL not written for P1001-GET-PET",
				getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 1);

		// P1001-GET-VENDOR must be written once to BAL
		assertThat("BAL not written for P1001-GET-VENDOR",
				getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 1);

		// P1001-LBL must be written once to BAL
		assertThat("BAL not written for P1001-LBL",
				getOtmDao().query(createBALQuery("P1001-LBL")).size() == 1);

		// P1001-LBL-CB must be written once to BAL
		assertThat("BAL not written for P1001-LBL-CB",
				getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 1);

		// P1001-MRG-PDF must be written once to BAL
		assertThat("BAL not written for P1001-MRG-PDF",
				getOtmDao().query(createBALQuery("P1001-MRG-PDF")).size() == 1);

		// P1001-UPD-SO-LINE must be written once to BAL
		assertThat("BAL not written for P1001-UPD-SO-LINE",
				getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE")).size() == 1);

		// P1001-UPD-SO-LINE-CB must be written once to BAL
		assertThat("BAL not written for P1001-UPD-SO-LINE-CB",
				getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE-CB")).size() == 1);

		// P1001-DELNT must be written once to BAL
		assertThat("BAL not written for P1001-DELNT",
				getOtmDao().query(createBALQuery("P1001-DELNT")).size() == 1);

		// P1001-DELNT-CB must be written once to BAL
		assertThat("BAL not written for P1001-DELNT-CB",
				getOtmDao().query(createBALQuery("P1001-DELNT-CB")).size() == 1);

		// P1001-CALL-P1002 must NOT be written once to BAL
		assertThat("BAL written for P1001-CALL-P1002",
				getOtmDao().query(createBALQuery("P1001-CALL-P1002")).size() == 0);

		// P1001-CALL-P1003 must be written once to BAL
		assertThat("BAL not written for P1001-CALL-P1003",
				getOtmDao().query(createBALQuery("P1001-CALL-P1003")).size() == 1);

	}

	@Test
	public void noLabelCreationAndEdiVendorTransmissionOnly() throws Exception {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "DNK-9").build();

		final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
						.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.TRUE.toString())
						.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "PARTNER")
						.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.TRUE.toString()).build());

		final DefaultSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
						.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(ediVendorTransmissionOutboundProcessMockRef);

		assertThat("ItemMasterService has not been invoked", itemMasterServiceMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("PurchaseOrderService has not been invoked", purchaseOrderServiceMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("PrintingService has not been invoked", printingServiceMockRef.hasBeenInvoked(), is(Boolean.TRUE));
		assertThat("EDIVendorTransmissionProcess has not been invoked",
				ediVendorTransmissionOutboundProcessMockRef.hasBeenInvoked(), is(Boolean.TRUE));

		assertThat("CarrierService has not been invoked", carrierServiceMockRef.hasBeenInvoked(), is(Boolean.FALSE));
		assertThat("SalesOrderService has not been invoked", salesOrderServiceMockRef.hasBeenInvoked(),
				is(Boolean.FALSE));
		assertThat("InformVendorService has not been invoked", informVendorProcessMockRef.hasBeenInvoked(),
				is(Boolean.FALSE));

		// P1001-GET-PET must be written once to BAL
		assertThat("BAL not written for P1001-GET-PET",
				getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 1);

		// P1001-GET-VENDOR must be written once to BAL
		assertThat("BAL not written for P1001-GET-VENDOR",
				getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 1);

		// P1001-LBL must NOT be written once to BAL
		assertThat("BAL written for P1001-LBL",
				getOtmDao().query(createBALQuery("P1001-LBL")).size() == 0);

		// P1001-LBL-CB must NOT be written once to BAL
		assertThat("BAL written for P1001-LBL-CB",
				getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 0);

		// P1001-MRG-PDF must NOT be written once to BAL
		assertThat("BAL not written for P1001-MRG-PDF",
				getOtmDao().query(createBALQuery("P1001-MRG-PDF")).size() == 0);

		// P1001-UPD-SO-LINE must NOT be written once to BAL
		assertThat("BAL written for P1001-UPD-SO-LINE",
				getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE")).size() == 0);

		// P1001-UPD-SO-LINE-CB must NOT be written once to BAL
		assertThat("BAL written for P1001-UPD-SO-LINE-CB",
				getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE-CB")).size() == 0);

		// P1001-DELNT must be written once to BAL
		assertThat("BAL not written for P1001-DELNT",
				getOtmDao().query(createBALQuery("P1001-DELNT")).size() == 1);

		// P1001-DELNT-CB must be written once to BAL
		assertThat("BAL not written for P1001-DELNT-CB",
				getOtmDao().query(createBALQuery("P1001-DELNT-CB")).size() == 1);

		// P1001-CALL-P1002 must be written once to BAL
		assertThat("BAL not written for P1001-CALL-P1002",
				getOtmDao().query(createBALQuery("P1001-CALL-P1002")).size() == 1);

		// P1001-CALL-P1003 must NOT be written once to BAL
		assertThat("BAL written for P1001-CALL-P1003",
				getOtmDao().query(createBALQuery("P1001-CALL-P1003")).size() == 0);
	}

	@Test
	public void labelCreationAndEdiVendorTransmissionAndPoMail() throws Exception {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

		final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
						.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
						.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "PARTNER")
						.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.TRUE.toString()).build());
		final DefaultSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
						.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(ediVendorTransmissionOutboundProcessMockRef);
		waitForInvocationOf(informVendorProcessMockRef);

		assertThat("ItemMasterService has not been invoked", itemMasterServiceMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("PurchaseOrderService has not been invoked", purchaseOrderServiceMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("PrintingService has not been invoked", printingServiceMockRef.hasBeenInvoked(), is(Boolean.TRUE));
		assertThat("CarrierService has not been invoked", carrierServiceMockRef.hasBeenInvoked(), is(Boolean.TRUE));
		assertThat("SalesOrderService has not been invoked", salesOrderServiceMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("InformVendorService has not been invoked", informVendorProcessMockRef.hasBeenInvoked(),
				is(Boolean.TRUE));
		assertThat("EDIVendorTransmissionProcess has been invoked",
				ediVendorTransmissionOutboundProcessMockRef.hasBeenInvoked(), is(Boolean.TRUE));

		// P1001-GET-PET must be written once to BAL
		assertThat("BAL not written for P1001-GET-PET",
				getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 1);

		// P1001-GET-VENDOR must be written once to BAL
		assertThat("BAL not written for P1001-GET-VENDOR",
				getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 1);

		// P1001-LBL must be written once to BAL
		assertThat("BAL not written for P1001-LBL",
				getOtmDao().query(createBALQuery("P1001-LBL")).size() == 1);

		// P1001-LBL-CB must be written once to BAL
		assertThat("BAL not written for P1001-LBL-CB",
				getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 1);

		// P1001-MRG-PDF must be written once to BAL
		assertThat("BAL not written for P1001-MRG-PDF",
				getOtmDao().query(createBALQuery("P1001-MRG-PDF")).size() == 1);

		// P1001-UPD-SO-LINE must be written once to BAL
		assertThat("BAL not written for P1001-UPD-SO-LINE",
				getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE")).size() == 1);

		// P1001-UPD-SO-LINE-CB must be written once to BAL
		assertThat("BAL not written for P1001-UPD-SO-LINE-CB",
				getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE-CB")).size() == 1);

		// P1001-DELNT must be written once to BAL
		assertThat("BAL not written for P1001-DELNT",
				getOtmDao().query(createBALQuery("P1001-DELNT")).size() == 1);

		// P1001-DELNT-CB must be written once to BAL
		assertThat("BAL not written for P1001-DELNT-CB",
				getOtmDao().query(createBALQuery("P1001-DELNT-CB")).size() == 1);

		// P1001-CALL-P1002 must be written once to BAL
		assertThat("BAL not written for P1001-CALL-P1002",
				getOtmDao().query(createBALQuery("P1001-CALL-P1002")).size() == 1);

		// P1001-CALL-P1003 must be written once to BAL
		assertThat("BAL not written for P1001-CALL-P1003",
				getOtmDao().query(createBALQuery("P1001-CALL-P1003")).size() == 1);
	}

	@Test
	public void retryPrintingService() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "DNK-9").build();

		final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
						.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
						.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
						.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());
		final DefaultSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
						.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

		printingServiceMockRef = new DefaultSoapMockService(Lists.newArrayList(
				new MockResponsePojo(ResponseType.SOAP_RESPONSE,
						readClasspathFile(RESOURCE_DIR + "PrintingServiceExceptionOutput.xml")),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "PrintingServiceOutput.xml"))));

		genericFaultHandler = new DefaultSoapMockService(
				readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_CreateDeliveryNote.xml"));
		
		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(informVendorProcessMockRef);

		assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
		assertThat("PrintingService has not been invoked!", printingServiceMockRef.hasBeenInvoked(), is(true));
		assertThat(
				String.format("PrintingService has been invoked for %s times only!",
						printingServiceMockRef.getNumberOfInvocations()),
				printingServiceMockRef.getNumberOfInvocations(), equalTo(2));

		declareXpathNS("dn", "http://home24.de/interfaces/bas/printingservice/printingservicemessages/v1");
System.out.println("printing REQUEST: " + printingServiceMockRef.getLastReceivedRequest()); 
		assertXpathEvaluatesTo(
				"//dn:createDeliveryNoteRequest/dn:requestHeader/header:KeyValueList/header:KeyValuePair[header:Key/text()='DocumentInfoLink']/header:Value/text()",
				"http://localhost:8088/api/v1/docs/3341", printingServiceMockRef.getLastReceivedRequest());
	}

	@Test
	public void retryCarrierService() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

		final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
						.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
						.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
						.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

		final DefaultSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
						.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

		carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, 
						new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "CarrierServiceFault.xml"))
						.replace(REPLACE_PARAM_METAPACK_ERROR_CODE, "00001").build()),			
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml")));

		genericFaultHandler = new DefaultSoapMockService(
				readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_CreateLabel.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(attachmentService);

		assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
		assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(), is(true));
		assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),
				is(true));
		assertThat("CarrierService has not been invoked exactly twice!",
				carrierServiceMockRef.getNumberOfInvocations() == 2);
	}

	@Test
	public void retryItemMasterServiceBusinessFault() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "DNK-9").build();

		final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
						.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
						.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
						.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());
		
		final AbstractSoapMockService itemMasterServiceMockRef = new RetryWithExceptionSoapMockService(
				1,				
				new MockResponsePojo(ResponseType.BUSINESS_FAULT, readClasspathFile(RESOURCE_DIR + "ItemMasterServiceBusinessFault.xml")),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml")));

		genericFaultHandler = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_GetItemMasterData.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(purchaseOrderServiceMockRef);

		assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
		assertThat("ItemMaster service has not been invoked exactly twice!",
				itemMasterServiceMockRef.getNumberOfInvocations() == 2);
		assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),
				is(true));
	}

	@Test
	public void retryItemMasterServiceTechnicalFault() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "DNK-9").build();

		final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
						.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
						.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
						.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

		final AbstractSoapMockService itemMasterServiceMockRef = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.BUSINESS_FAULT, ""),
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml")));

		genericFaultHandler = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_GetItemMasterData.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(purchaseOrderServiceMockRef);

		assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
		assertThat("ItemMaster service has not been invoked exactly twice!",
				itemMasterServiceMockRef.getNumberOfInvocations() == 2);
		assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(), is(true));
	}

	@Test
	public void retryPurchaseOrderServiceBusinessFault() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "DNK-9").build();

		final AbstractSoapMockService purchaseOrderServiceMockRef = new RetryWithExceptionSoapMockService(
				1, 
				new MockResponsePojo(ResponseType.BUSINESS_FAULT, readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceBusinessFault.xml")),				
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml")));
		
		final AbstractSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"));

		genericFaultHandler = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_ReadVendorInformation.xml"));
		
		printingServiceMockRef = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "PrintingServiceOutput.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
		mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(printingServiceMockRef);

		assertThat("GenericFaultHandler has not been invoked exactly once!",
				genericFaultHandler.getNumberOfInvocations() == 1);
		assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(), is(true));
		assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),
				is(true));
		assertThat("Printing service has not been invoked!", printingServiceMockRef.hasBeenInvoked(), is(true));
	}

	@Test
	public void retryPurchaseOrderServiceTechnicalFault() {

		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "DNK-9").build();

		final AbstractSoapMockService purchaseOrderServiceMockRef = new RetryWithExceptionSoapMockService(
				1, 
				new MockResponsePojo(ResponseType.BUSINESS_FAULT, ""),				
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml")));
		
		final AbstractSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"));

		genericFaultHandler = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_ReadVendorInformation.xml"));
		
		printingServiceMockRef = new DefaultSoapMockService(readClasspathFile(RESOURCE_DIR + "PrintingServiceOutput.xml"));

		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
		mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(printingServiceMockRef);

		assertThat("GenericFaultHandler has not been invoked exactly once!",
				genericFaultHandler.getNumberOfInvocations() == 1);
		assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(), is(true));
		assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),
				is(true));
		assertThat("Printing service has not been invoked!", printingServiceMockRef.hasBeenInvoked(), is(true));
	}

	
	@Test
	public void poCarrierMetaPackError2400WrongAddressHTResendTest() {
		
		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

		purchaseOrderServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
						.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
						.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
						.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

		itemMasterServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
						.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

		carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, 
						new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "CarrierServiceFault.xml"))
						.replace(REPLACE_PARAM_METAPACK_ERROR_CODE, "2400").build()),			
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml")));

		genericFaultHandler = new DefaultSoapMockService(
				readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_CreateLabel.xml"));

		salesOrderServiceMockRef = new DefaultSoapMockService(
				readClasspathFile(RESOURCE_DIR + "GetAddressDataSuccessResponse.xml"));
		
		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(attachmentService);

		assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
		assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(), is(true));
		assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),
				is(true));
		assertThat("CarrierService has not been invoked exactly twice!",
				carrierServiceMockRef.getNumberOfInvocations() == 2);
		assertThat("SalesOrderService has not been invoked!", salesOrderServiceMockRef.hasBeenInvoked(), is(true));
		
		// P1001-LBL must be written twice to BAL
		assertThat("BAL not written twice for P1001-LBL",
					getOtmDao().query(createBALQuery("P1001-LBL")).size() == 2);

		// P1001-LBL-CB must be written once to BAL
		assertThat("BAL not written for P1001-LBL-CB",
					getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 1);
		
		// P1001-WRONG-ADR must be written once to BAL
		assertThat("BAL not written for P1001-WRONG-ADR",
					getOtmDao().query(createBALQuery("P1001-WRONG-ADR")).size() == 1);
		
		// P1001-GET-ADDR must be written once to BAL
		assertThat("BAL not written for P1001-GET-ADDR",
					getOtmDao().query(createBALQuery("P1001-GET-ADDR")).size() == 1);
		
		// P1001-ADR-CORRECTED must be written once to BAL
		assertThat("BAL not written for P1001-ADR-CORRECTED",
					getOtmDao().query(createBALQuery("P1001-ADR-CORRECTED")).size() == 1);

		// P1001-WRONG-MD must not be written once to BAL
		assertThat("BAL written for P1001-WRONG-MD",
					getOtmDao().query(createBALQuery("P1001-WRONG-MD")).size() == 0);

		// P1001-MD-CORRECTED must not be written once to BAL
		assertThat("BAL written for P1001-MD-CORRECTED",
					getOtmDao().query(createBALQuery("P1001-MD-CORRECTED")).size() == 0);

	 }
	
	
	@Test
	public void poCarrierMetaPackError2400WrongAddressHTAbortTest() {
		
		final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
				.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

		purchaseOrderServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
						.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
						.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
						.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

		itemMasterServiceMockRef = new DefaultSoapMockService(
				new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
						.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

		carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, 
						new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "CarrierServiceFault.xml"))
						.replace(REPLACE_PARAM_METAPACK_ERROR_CODE, "2400").build()),			
				new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml")));

		genericFaultHandler = new DefaultSoapMockService(
				readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerAbortOutput_GetAddressData.xml"));

		salesOrderServiceMockRef = new DefaultSoapMockService(
				readClasspathFile(RESOURCE_DIR + "GetAddressDataSuccessResponse.xml"));
		
		mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
		mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
		mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

		invokeCompositeService(COMPOSITE, REVISION, PROCESS,
				SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

		waitForInvocationOf(attachmentService);

		
		assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(), is(true));
		assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),
				is(true));
		assertThat("CarrierService has not been invoked!", carrierServiceMockRef.hasBeenInvoked(), is(true));
		assertThat("SalesOrderService has been invoked!", salesOrderServiceMockRef.hasBeenInvoked(), is(false));
		assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
	
		// P1001-GET-PET must be written once to BAL
		assertThat("BAL not written once for P1001-GET-PET",
					getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 1);
		
		// P1001-GET-VENDOR must be written once to BAL
		assertThat("BAL not written once for P1001-GET-VENDOR",
					getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 1);
		
		// P1001-LBL must be written once to BAL
		assertThat("BAL not written once for P1001-LBL",
					getOtmDao().query(createBALQuery("P1001-LBL")).size() == 1);

		// P1001-LBL-CB must not be written to BAL
		assertThat("BAL written for P1001-LBL-CB",
					getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 0);
		
		// P1001-WRONG-ADR must be written once to BAL
		assertThat("BAL not written once for P1001-WRONG-ADR",
					getOtmDao().query(createBALQuery("P1001-WRONG-ADR")).size() == 1);
		
		// P1001-GET-ADDR must not be written to BAL
		assertThat("BAL written for P1001-GET-ADDR",
					getOtmDao().query(createBALQuery("P1001-GET-ADDR")).size() == 0);
		
		// P1001-ADR-CORRECTED must not be written to BAL
		assertThat("BAL written for P1001-ADR-CORRECTED",
					getOtmDao().query(createBALQuery("P1001-ADR-CORRECTED")).size() == 0);

		// P1001-WRONG-MD must not be written once to BAL
		assertThat("BAL written for P1001-WRONG-MD",
					getOtmDao().query(createBALQuery("P1001-WRONG-MD")).size() == 0);

		// P1001-MD-CORRECTED must not be written once to BAL
		assertThat("BAL written for P1001-MD-CORRECTED",
					getOtmDao().query(createBALQuery("P1001-MD-CORRECTED")).size() == 0);

	 }
	
	 @Test
	 public void poCarrierMetaPackError2400WrongAddressGetAddressDataBusinessFaultResendTest() {
		  
		  final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
					.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

			purchaseOrderServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
							.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
							.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
							.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

			itemMasterServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
							.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

			carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, 
							new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "CarrierServiceFault.xml"))
							.replace(REPLACE_PARAM_METAPACK_ERROR_CODE, "2400").build()),			
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml")));

			genericFaultHandler = new RetryWithExceptionSoapMockService(1,
					new MockResponsePojo(ResponseType.SOAP_RESPONSE,
							readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_CreateLabel.xml")),
					new MockResponsePojo(ResponseType.SOAP_RESPONSE,
							readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_GetAddressData.xml")));

			final AbstractSoapMockService salesOrderServiceMockRef = new RetryWithExceptionSoapMockService(
					1,				
					new MockResponsePojo(ResponseType.BUSINESS_FAULT, readClasspathFile(RESOURCE_DIR + "GetAddressDataBusinessFaultResponse.xml")),
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "GetAddressDataSuccessResponse.xml")));
			
			mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
			mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

			invokeCompositeService(COMPOSITE, REVISION, PROCESS,
					SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

			waitForInvocationOf(attachmentService);

			assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
			assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(), is(true));
			assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(), is(true));
			assertThat("CarrierService has not been invoked!", salesOrderServiceMockRef.hasBeenInvoked(), is(true));
			assertThat("SalesOrderService has not been invoke!",salesOrderServiceMockRef.hasBeenInvoked(), is(true));
			
			// P1001-LBL must be written twice to BAL
			assertThat("BAL not written twice for P1001-LBL",
						getOtmDao().query(createBALQuery("P1001-LBL")).size() == 2);

			// P1001-LBL-CB must be written to BAL
			assertThat("BAL not written for P1001-LBL-CB",
						getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 1);
			
			// P1001-WRONG-ADR must be written once to BAL
			assertThat("BAL not written for P1001-WRONG-ADR",
						getOtmDao().query(createBALQuery("P1001-WRONG-ADR")).size() == 1);
			
			// P1001-GET-ADDR must be written once to BAL
			assertThat("BAL not written for P1001-GET-ADDR",
						getOtmDao().query(createBALQuery("P1001-GET-ADDR")).size() == 1);
			
			// P1001-ADR-CORRECTED must be written once to BAL
			assertThat("BAL not written for P1001-ADR-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-ADR-CORRECTED")).size() == 1);

			// P1001-WRONG-MD must be written once to BAL
			assertThat("BAL written for P1001-WRONG-MD",
						getOtmDao().query(createBALQuery("P1001-WRONG-MD")).size() == 0);

			// P1001-MD-CORRECTED must be written once to BAL
			assertThat("BAL written for P1001-MD-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-MD-CORRECTED")).size() == 0);
			
	  }
	 
	 @Test
	 public void poCarrierMetaPackError2400WrongAddressGetAddressDataBusinessFaultAbortTest() {
		  
		  final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
					.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

			purchaseOrderServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
							.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
							.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
							.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

			itemMasterServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
							.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

			carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, 
							new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "CarrierServiceFault.xml"))
							.replace(REPLACE_PARAM_METAPACK_ERROR_CODE, "2400").build()),			
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml")));

			genericFaultHandler = new RetryWithExceptionSoapMockService(1,
					new MockResponsePojo(ResponseType.SOAP_RESPONSE,
							readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_CreateLabel.xml")),
					new MockResponsePojo(ResponseType.SOAP_RESPONSE,
							readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerAbortOutput_GetAddressData.xml")));

			final AbstractSoapMockService salesOrderServiceMockRef = new RetryWithExceptionSoapMockService(
					1,				
					new MockResponsePojo(ResponseType.BUSINESS_FAULT, readClasspathFile(RESOURCE_DIR + "GetAddressDataBusinessFaultResponse.xml")),
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "GetAddressDataSuccessResponse.xml")));
			
			mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
			mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

			invokeCompositeService(COMPOSITE, REVISION, PROCESS,
					SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

			waitForInvocationOf(attachmentService);

			assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
			assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(), is(true));
			assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),
					is(true));
			assertThat("CarrierService has not been invoked!", salesOrderServiceMockRef.hasBeenInvoked(), is(true));
			assertThat("SalesOrderService has not been invoke!",salesOrderServiceMockRef.hasBeenInvoked(), is(true));
			
			// P1001-LBL must be written once to BAL
			assertThat("BAL not written for P1001-LBL",
						getOtmDao().query(createBALQuery("P1001-LBL")).size() == 1);

			// P1001-LBL-CB must not be written once to BAL
			assertThat("BAL written for P1001-LBL-CB",
						getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 0);
			
			// P1001-WRONG-ADR must be written once to BAL
			assertThat("BAL not written for P1001-WRONG-ADR",
						getOtmDao().query(createBALQuery("P1001-WRONG-ADR")).size() == 1);
			
			// P1001-GET-ADDR must not be written once to BAL
			assertThat("BAL written for P1001-GET-ADDR",
						getOtmDao().query(createBALQuery("P1001-GET-ADDR")).size() == 0);
			
			// P1001-ADR-CORRECTED must not be written once to BAL
			assertThat("BAL written for P1001-ADR-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-ADR-CORRECTED")).size() == 0);

			// P1001-WRONG-MD must be written once to BAL
			assertThat("BAL written for P1001-WRONG-MD",
						getOtmDao().query(createBALQuery("P1001-WRONG-MD")).size() == 0);

			// P1001-MD-CORRECTED must be written once to BAL
			assertThat("BAL written for P1001-MD-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-MD-CORRECTED")).size() == 0);
			
	  }
	  
	 @Test
	 public void poCarrierMetaPackError2400WrongAddressGetAddressDataTechnicalFaultResendTest() {
		  
		 final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
					.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

			purchaseOrderServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
							.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
							.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
							.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

			itemMasterServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
							.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

			carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, 
							new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "CarrierServiceFault.xml"))
							.replace(REPLACE_PARAM_METAPACK_ERROR_CODE, "2400").build()),			
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml")));

			genericFaultHandler = new RetryWithExceptionSoapMockService(1,
					new MockResponsePojo(ResponseType.SOAP_RESPONSE,
							readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_CreateLabel.xml")),
					new MockResponsePojo(ResponseType.SOAP_RESPONSE,
							readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_GetAddressData.xml")));

			final AbstractSoapMockService salesOrderServiceMockRef = new RetryWithExceptionSoapMockService(
					1,				
					new MockResponsePojo(ResponseType.BUSINESS_FAULT, ""),
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "GetAddressDataSuccessResponse.xml")));
			
			mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
			mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

			invokeCompositeService(COMPOSITE, REVISION, PROCESS,
					SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

			waitForInvocationOf(attachmentService);

			assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
			assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(), is(true));
			assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(), is(true));
			assertThat("CarrierService has not been invoked!",	carrierServiceMockRef.hasBeenInvoked(), is(true));
			assertThat("SalesOrderService has not been invoked!", salesOrderServiceMockRef.hasBeenInvoked(), is(true));
			
			// P1001-LBL must be written twice to BAL
			assertThat("BAL not written twice for P1001-LBL",
						getOtmDao().query(createBALQuery("P1001-LBL")).size() == 2);

			// P1001-LBL-CB must be written once to BAL
			assertThat("BAL not written for P1001-LBL-CB",
						getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 1);
			
			// P1001-WRONG-ADR must be written once to BAL
			assertThat("BAL not written for P1001-WRONG-ADR",
						getOtmDao().query(createBALQuery("P1001-WRONG-ADR")).size() == 1);
			
			// P1001-GET-ADDR must be written once to BAL
			assertThat("BAL not written for P1001-GET-ADDR",
						getOtmDao().query(createBALQuery("P1001-GET-ADDR")).size() == 1);
			
			// P1001-ADR-CORRECTED must be written once to BAL
			assertThat("BAL not written for P1001-ADR-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-ADR-CORRECTED")).size() == 1);

			// P1001-WRONG-MD must be written once to BAL
			assertThat("BAL written for P1001-WRONG-MD",
						getOtmDao().query(createBALQuery("P1001-WRONG-MD")).size() == 0);

			// P1001-MD-CORRECTED must be written once to BAL
			assertThat("BAL written for P1001-MD-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-MD-CORRECTED")).size() == 0);
			
		  
	  }
	  
	  
	 @Test
	public void poCarrierMetaPackError2400WrongAddressGetAddressDataTechnicalFaultAbortTest() {
			  
			  final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
						.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

				purchaseOrderServiceMockRef = new DefaultSoapMockService(
						new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
								.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
								.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
								.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

				itemMasterServiceMockRef = new DefaultSoapMockService(
						new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
								.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

				carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
						new MockResponsePojo(ResponseType.SOAP_RESPONSE, 
								new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "CarrierServiceFault.xml"))
								.replace(REPLACE_PARAM_METAPACK_ERROR_CODE, "2400").build()),			
						new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml")));

				genericFaultHandler = new RetryWithExceptionSoapMockService(1,
						new MockResponsePojo(ResponseType.SOAP_RESPONSE,
								readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_CreateLabel.xml")),
						new MockResponsePojo(ResponseType.SOAP_RESPONSE,
								readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerAbortOutput_GetAddressData.xml")));

				final AbstractSoapMockService salesOrderServiceMockRef = new RetryWithExceptionSoapMockService(
						1,				
						new MockResponsePojo(ResponseType.BUSINESS_FAULT, ""),
						new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "GetAddressDataSuccessResponse.xml")));
				
				mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
				mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
				mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
				mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
				mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
				mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

				invokeCompositeService(COMPOSITE, REVISION, PROCESS,
						SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

				waitForInvocationOf(attachmentService);

				assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
				assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(), is(true));
				assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),	is(true));
				assertThat("CarrierService has not been invoked!",carrierServiceMockRef.hasBeenInvoked(),	is(true));
				assertThat("SalesOrderService has not been invoked!",	salesOrderServiceMockRef.hasBeenInvoked(),	is(true));
				
				// P1001-LBL must be written once to BAL
				assertThat("BAL not written for P1001-LBL",
							getOtmDao().query(createBALQuery("P1001-LBL")).size() == 1);

				// P1001-LBL-CB must not be written once to BAL
				assertThat("BAL written for P1001-LBL-CB",
							getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 0);
				
				// P1001-WRONG-ADR must be written once to BAL
				assertThat("BAL not written for P1001-WRONG-ADR",
							getOtmDao().query(createBALQuery("P1001-WRONG-ADR")).size() == 1);
				
				// P1001-GET-ADDR must not be written to BAL
				assertThat("BAL written for P1001-GET-ADDR",
							getOtmDao().query(createBALQuery("P1001-GET-ADDR")).size() == 0);
				
				// P1001-ADR-CORRECTED must not be written once to BAL
				assertThat("BAL written for P1001-ADR-CORRECTED",
							getOtmDao().query(createBALQuery("P1001-ADR-CORRECTED")).size() == 0);

				// P1001-WRONG-MD must be written once to BAL
				assertThat("BAL written for P1001-WRONG-MD",
							getOtmDao().query(createBALQuery("P1001-WRONG-MD")).size() == 0);

				// P1001-MD-CORRECTED must be written once to BAL
				assertThat("BAL written for P1001-MD-CORRECTED",
							getOtmDao().query(createBALQuery("P1001-MD-CORRECTED")).size() == 0);
			  
		  }
		  
	  
	  @Test
	  public void poCarrierMetaPackError2010MasterDataFoundHTResendTest() {
		  
		 final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
					.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

			purchaseOrderServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
							.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
							.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
							.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

			itemMasterServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
							.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

			carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, 
							new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "CarrierServiceFault.xml"))
							.replace(REPLACE_PARAM_METAPACK_ERROR_CODE, "2010").build()),			
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml")));
						
			genericFaultHandler = new DefaultSoapMockService(
					readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_CreateLabel.xml")); 
			
			purchaseOrderGroupHandlingProcessRef = new DefaultSoapMockService("");

			mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
			mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderGroupHandlingProcess", purchaseOrderGroupHandlingProcessRef); 

			invokeCompositeService(COMPOSITE, REVISION, PROCESS,
					SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

			waitForInvocationOf(attachmentService);

			assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
			assertThat("ItemMaster has not been invoked!",itemMasterServiceMockRef.hasBeenInvoked(),is(true));
			assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),	is(true));
			assertThat("CarrierService has not been invoked!",carrierServiceMockRef.hasBeenInvoked(), is(true));
			assertThat("PurchaseOrderGroupHandlingProcess has not been invoked!", purchaseOrderGroupHandlingProcessRef.hasBeenInvoked(), is(true));
					
			
			// P1001-GET-PET must be written to BAL
			assertThat("BAL not written for P1001-GET-PET",
						getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 1);
			
			// P1001-GET-VENDOR must be written to BAL
			assertThat("BAL not written for P1001-GET-VENDOR",
						getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 1);
			
			// P1001-LBL must not be written to BAL
			assertThat("BAL written for P1001-LBL",
						getOtmDao().query(createBALQuery("P1001-LBL")).size() == 1);
			
			// P1001-LBL-CB must not be written to BAL
			assertThat("BAL written for P1001-LBL-CB",
						getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 0);
			
			// P1001-WRONG-ADR must not be written to BAL
			assertThat("BAL written for P1001-WRONG-ADR",
						getOtmDao().query(createBALQuery("P1001-WRONG-ADR")).size() == 0);
			
			// P1001-GET-ADDR must not be written to BAL
			assertThat("BAL written for P1001-GET-ADDR",
						getOtmDao().query(createBALQuery("P1001-GET-ADDR")).size() == 0);
			
			// P1001-ADR-CORRECTED must not be written to BAL
			assertThat("BAL written for P1001-ADR-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-ADR-CORRECTED")).size() == 0);

			// P1001-WRONG-MD must be written to BAL
			assertThat("BAL not written for P1001-WRONG-MD",
						getOtmDao().query(createBALQuery("P1001-WRONG-MD")).size() == 1);

			// P1001-MD-CORRECTED must be written to BAL
			assertThat("BAL not written for P1001-MD-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-MD-CORRECTED")).size() == 1);
			
	  }
	  
	  @Test
	  public void poCarrierMetaPackError2010MasterDataFoundHTResendWithoutReinvokeMockTest() {
		  
		 final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
					.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

			purchaseOrderServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
							.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
							.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
							.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

			itemMasterServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
							.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

			carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, 
							new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "CarrierServiceFault.xml"))
							.replace(REPLACE_PARAM_METAPACK_ERROR_CODE, "2010").build()),			
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml")));
						
			genericFaultHandler = new DefaultSoapMockService(
					readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerResendOutput_CreateLabel.xml")); 
			
			mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
			mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

			try {
				invokeCompositeService(COMPOSITE, REVISION, PROCESS,
						SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));
			}catch (ServiceException e) {
			System.out.println("error: " + e.getMessage()); 
			}
			

			waitForInvocationOf(attachmentService);

			assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
			assertThat("ItemMaster has not been invoked!",itemMasterServiceMockRef.hasBeenInvoked(),is(true));
			assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),	is(true));
			assertThat("CarrierService has not been invoked!",carrierServiceMockRef.hasBeenInvoked(), is(true));
					
			
			// P1001-GET-PET must be written to BAL twice
			assertThat("BAL not written twice for P1001-GET-PET",
						getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 2);
			
			// P1001-GET-VENDOR must be written to BAL twice
			assertThat("BAL not written twice for P1001-GET-VENDOR",
						getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 2);
			
			// P1001-LBL must not be written to BAL twice 
			assertThat("BAL written for twice P1001-LBL",
						getOtmDao().query(createBALQuery("P1001-LBL")).size() == 2);
			
			// P1001-LBL-CB must not be written to BAL
			assertThat("BAL written for P1001-LBL-CB",
						getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 1);
			
			// P1001-WRONG-ADR must not be written to BAL
			assertThat("BAL written for P1001-WRONG-ADR",
						getOtmDao().query(createBALQuery("P1001-WRONG-ADR")).size() == 0);
			
			// P1001-GET-ADDR must not be written to BAL
			assertThat("BAL written for P1001-GET-ADDR",
						getOtmDao().query(createBALQuery("P1001-GET-ADDR")).size() == 0);
			
			// P1001-ADR-CORRECTED must not be written to BAL
			assertThat("BAL written for P1001-ADR-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-ADR-CORRECTED")).size() == 0);

			// P1001-WRONG-MD must be written to BAL
			assertThat("BAL not written for P1001-WRONG-MD",
						getOtmDao().query(createBALQuery("P1001-WRONG-MD")).size() == 1);

			// P1001-MD-CORRECTED must be written to BAL
			assertThat("BAL not written for P1001-MD-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-MD-CORRECTED")).size() == 1);
			
			// P1001-MRG-PDF must be written to BAL
			assertThat("BAL not written for P1001-MRG-PDF",
						getOtmDao().query(createBALQuery("P1001-MRG-PDF")).size() == 1);
		
			
	  }
	  
	 @Test
	 public void poCarrierMetaPackError2010WrongMasterDataFoundHTAbortTest() {
		  
		 final String requestXML = new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderGroupHandlingProcessInput.xml"))
					.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

			purchaseOrderServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "PurchaseOrderServiceOutput.xml"))
							.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
							.replace(REPLACE_PARAM_NAME_EDI_PARTNER_NAME, "")
							.replace(REPLACE_PARAM_NAME_ORDERS_IS_ACTIVE, Boolean.FALSE.toString()).build());

			itemMasterServiceMockRef = new DefaultSoapMockService(
					new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "ItemMasterServiceOutput.xml"))
							.replace(REPLACE_PARAM_NAME_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString()).build());

			carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, 
							new ParameterReplacer(readClasspathFile(RESOURCE_DIR + "CarrierServiceFault.xml"))
							.replace(REPLACE_PARAM_METAPACK_ERROR_CODE, "2010").build()),			
					new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(RESOURCE_DIR + "CarrierServiceOutput.xml")));

			genericFaultHandler = new DefaultSoapMockService(
					readClasspathFile(RESOURCE_DIR + "GenericFaultHandlerAbortOutput_CreateLabel.xml"));

			
			mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
			mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
			mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

			invokeCompositeService(COMPOSITE, REVISION, PROCESS,
					SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

			waitForInvocationOf(attachmentService);

			assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(), is(true));
			assertThat("ItemMaster has not been invoked!",itemMasterServiceMockRef.hasBeenInvoked(), is(true));
			assertThat("PurchaseOrder service has not been invoked!", purchaseOrderServiceMockRef.hasBeenInvoked(),	is(true));
			assertThat("CarrierService has not been invoked!", carrierServiceMockRef.hasBeenInvoked(),is(true));
			
			// P1001-GET-PET must be written once to BAL
			assertThat("BAL not written once for P1001-GET-PET",
						getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 1);
			
			// P1001-GET-VENDOR must be written once to BAL
			assertThat("BAL not written once for P1001-GET-VENDOR",
						getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 1);
			
			// P1001-LBL must be written once to BAL
			assertThat("BAL not written once for P1001-LBL",
						getOtmDao().query(createBALQuery("P1001-LBL")).size() == 1);
			
			// P1001-LBL-CB must not be written to BAL
			assertThat("BAL written for P1001-LBL-CB",
						getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 0);
			
			// P1001-WRONG-ADR must not be written to BAL
			assertThat("BAL written for P1001-WRONG-ADR",
						getOtmDao().query(createBALQuery("P1001-WRONG-ADR")).size() == 0);
			
			// P1001-GET-ADDR must not be written to BAL
			assertThat("BAL written for P1001-GET-ADDR",
						getOtmDao().query(createBALQuery("P1001-GET-ADDR")).size() == 0);
			
			// P1001-ADR-CORRECTED must not be written to BAL
			assertThat("BAL written for P1001-ADR-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-ADR-CORRECTED")).size() == 0);

			// P1001-WRONG-MD must be written to BAL
			assertThat("BAL not written for P1001-WRONG-MD",
						getOtmDao().query(createBALQuery("P1001-WRONG-MD")).size() == 1);

			// P1001-MD-CORRECTED must  not be written once to BAL
			assertThat("BAL written for P1001-MD-CORRECTED",
						getOtmDao().query(createBALQuery("P1001-MD-CORRECTED")).size() == 0);
			
	  }
	 
	 private BaseQuery<BalActivities> createBALQuery(String activityCode) {
			return new BaseQuery<>(SqlOp.SELECT,
					new QueryPredicate("correlation_id", CORRELATION_ID).withEquals("activity_code", activityCode),
					BalActivities.class);
		}

		private BaseQuery<BalActivities> createDeleteBALQuery() {
			return new BaseQuery<>(SqlOp.DELETE,
					new QueryPredicate("correlation_id", CORRELATION_ID),
					BalActivities.class);
		}

		private BaseQuery<OsmPo> createDeleteOSMPOQuery() {
			return new BaseQuery<>(SqlOp.DELETE,
					new QueryPredicate("correlation_id", CORRELATION_ID),
					OsmPo.class);
		}


}
