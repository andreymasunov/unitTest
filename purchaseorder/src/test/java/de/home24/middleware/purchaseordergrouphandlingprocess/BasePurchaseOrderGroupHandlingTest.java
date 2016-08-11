package de.home24.middleware.purchaseordergrouphandlingprocess;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.util.logging.Logger;

import org.junit.Before;

import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmPo;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.BaseQuery;
import de.home24.middleware.octestframework.components.BaseQuery.SqlOp;
import de.home24.middleware.octestframework.components.QueryPredicate;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class BasePurchaseOrderGroupHandlingTest extends AbstractBaseSoaTest {

    protected static final Logger LOGGER = Logger
	    .getLogger(BasePurchaseOrderGroupHandlingTest.class.getSimpleName());

    protected static final String CORRELATION_ID = "1231376876";

	protected static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
    protected static final String REPLACE_PARAM_NOTE_PURCHASINGORDER = "NOTE_PURCHASING_ORDER";
    protected static final String REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL = "NOTE_SUPPRESS_PO_MAIL";
    protected static final String REPLACE_PARAM_EDI_ORDERS_PARTNER = "EDI_ORDERS_PARTNER";
    protected static final String REPLACE_PARAM_NO_EDI_PROCESS_AVAILABLE = "NO_EDI_PROCESS_AVAILABLE";
    protected static final String REPLACE_PARAM_PAYLOAD = "PAYLOAD";
    protected static final String REPLACE_PARAM_ACTIVITY_ID = "ACTIVITY_ID";

    protected static final String COMPOSITE = "PurchaseOrderGroupHandlingProcess";
    protected static final String REVISION = "1.4.0.1";
    protected static final String PROCESS = "PurchaseOrderGroupHandlingDelegator_ep";

    protected AbstractSoapMockService carrierServiceMockRef;
    protected AbstractSoapMockService salesOrderServiceMockRef;
    protected AbstractSoapMockService printingServiceMockRef;
    protected AbstractSoapMockService informVendorProcessMockRef;
    protected AbstractSoapMockService ediVendorTransmissionOutboundProcessMockRef;
    protected AbstractSoapMockService attachmentService;
    protected AbstractSoapMockService genericFaultHandler;

    protected static final String RESOURCES = "../processes/Dropship/PurchaseOrderGroupHandlingProcess/";

	protected String correlationId;

    public BasePurchaseOrderGroupHandlingTest() {
	super("dropship");
    }

    @Before
    public void setUp() throws Exception {

	declareXpathNS("exception", "http://home24.de/data/common/exceptiontypes/v1");
	declareXpathNS("po",
		"http://home24.de/interfaces/bps/processpurchaseordergroup/purchaseordergrouphandlingprocessmessages/v1");
	declareXpathNS("header", "http://home24.de/data/common/messageheadertypes/v1");
	declareXpathNS("gfhm",
		"http://home24.de/interfaces/bas/genericfaulthandler/genericfaulthandlerservicemessages/v1");
	declareXpathNS("gfh", "http://home24.de/data/custom/genericfaulthandler/v1");
	declareXpathNS("oagis", "http://www.openapplications.org/oagis/10");

	getOtmDao().delete(createDeleteBALQuery());
	getOtmDao().delete(createDeleteOSMPOQuery());

	correlationId = String.valueOf(System.currentTimeMillis());

	carrierServiceMockRef = createCarrierServiceMockRef();
	salesOrderServiceMockRef = new DefaultSoapMockService(
		readClasspathFile(String.format("%s/ReceiveSalesOrderLineUpdatedRequest.xml", RESOURCES)));
	printingServiceMockRef = new DefaultSoapMockService(
		readClasspathFile(String.format("%s/PrintingServiceOutput.xml", RESOURCES)));
	informVendorProcessMockRef = new DefaultSoapMockService("");
	ediVendorTransmissionOutboundProcessMockRef = new DefaultSoapMockService("");
	attachmentService = new DefaultSoapMockService(
		readClasspathFile(String.format("%s/AttachmentServiceResponse.xml", RESOURCES)));
	genericFaultHandler = new DefaultSoapMockService(
		readClasspathFile(String.format("%s/GenericFaultHandlerResendOutput.xml", RESOURCES)));

	mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "InformVendorProcess", informVendorProcessMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "EDIVendorTransmissionOutboundProcess",
		ediVendorTransmissionOutboundProcessMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "AttachmentService", attachmentService);
	mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
	saveFileToComposite(COMPOSITE, REVISION, "DVM/WaitForLableCreation.dvm",
		readClasspathFile(RESOURCES + "WaitForLableCreation.dvm"));
    }

    protected DefaultSoapMockService createCarrierServiceMockRef() {
	return new DefaultSoapMockService(
		readClasspathFile(String.format("%s/CarrierServiceOutput.xml", RESOURCES)));
    }

    public void executeLabelCreationAndPoMailOnly(String pRequestXml) throws Exception {

	final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
		new ParameterReplacer(
			readClasspathFile(String.format("%s/PurchaseOrderServiceOutput.xml", RESOURCES)))
				.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
				.replace(REPLACE_PARAM_EDI_ORDERS_PARTNER, "").build());

	final DefaultSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
		new ParameterReplacer(
			readClasspathFile(String.format("%s/ItemMasterServiceOutput.xml", RESOURCES)))
				.replace(REPLACE_PARAM_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString())
				.build());

	mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "InformVendorProcess", informVendorProcessMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "AttachmentService", attachmentService);

	invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, new ParameterReplacer(pRequestXml)
			.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build()));

	waitForInvocationOf(informVendorProcessMockRef);

	assertThat("ItemMasterService has not been invoked", itemMasterServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("PurchaseOrderService has not been invoked", purchaseOrderServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("SalesOrderService has not been invoked", salesOrderServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("CarrierService has not been invoked", carrierServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("PrintingService has not been invoked", printingServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("InformVendorService has not been invoked", informVendorProcessMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));

	assertThat("EDIVendorTransmissionProcess has been invoked",
		ediVendorTransmissionOutboundProcessMockRef.hasBeenInvoked(), is(Boolean.FALSE));

	assertThat("BAL not written for P1001-GET-PET",
		getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 1);

	assertThat("BAL not written for P1001-GET-VENDOR",
		getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 1);

	assertThat("BAL not written for P1001-LBL",
		getOtmDao().query(createBALQuery("P1001-LBL")).size() == 1);

	assertThat("BAL not written for P1001-LBL-CB",
		getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 1);

	assertThat("BAL not written for P1001-MRG-PDF",
		getOtmDao().query(createBALQuery("P1001-MRG-PDF")).size() == 1);

	assertThat("BAL not written for P1001-UPD-SO-LINE",
		getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE")).size() == 1);

	assertThat("BAL not written for P1001-UPD-SO-LINE-CB",
		getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE-CB")).size() == 1);

	assertThat("BAL not written for P1001-DELNT",
		getOtmDao().query(createBALQuery("P1001-DELNT")).size() == 1);

	assertThat("BAL not written for P1001-DELNT-CB",
		getOtmDao().query(createBALQuery("P1001-DELNT-CB")).size() == 1);

	assertThat("BAL written for P1001-CALL-P1002",
		getOtmDao().query(createBALQuery("P1001-CALL-P1002")).size() == 0);

	assertThat("BAL not written for P1001-CALL-P1003",
		getOtmDao().query(createBALQuery("P1001-CALL-P1003")).size() == 1);
    }

    protected BaseQuery<BalActivities> createBALQuery(String activityCode) {
	return new BaseQuery<>(SqlOp.SELECT, new QueryPredicate("correlation_id", CORRELATION_ID)
		.withEquals("activity_code", activityCode), BalActivities.class);
    }

    protected BaseQuery<BalActivities> createDeleteBALQuery() {
	return new BaseQuery<>(SqlOp.DELETE, new QueryPredicate("correlation_id", CORRELATION_ID),
		BalActivities.class);
    }

    protected BaseQuery<OsmPo> createDeleteOSMPOQuery() {
	return new BaseQuery<>(SqlOp.DELETE, new QueryPredicate("correlation_id", CORRELATION_ID),
		OsmPo.class);
    }

    protected void executeNoLabelCreationAndEdiVendorTranmissionOnlyWhenSupplierChannelIsDropshipChannelGer12(
	    final String pRequestXml) {
	executeNoLabelCreationAndEdiVendorTranmissionOnly(
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, new ParameterReplacer(pRequestXml)
			.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-12").build()));
    }

    private void executeNoLabelCreationAndEdiVendorTranmissionOnly(final String pRequestXml) {
	final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
		new ParameterReplacer(
			readClasspathFile(String.format("%s/PurchaseOrderServiceOutput.xml", RESOURCES)))
				.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.TRUE.toString())
				.replace(REPLACE_PARAM_EDI_ORDERS_PARTNER, "PARTNER").build());

	final DefaultSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
		new ParameterReplacer(
			readClasspathFile(String.format("%s/ItemMasterServiceOutput.xml", RESOURCES)))
				.replace(REPLACE_PARAM_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString())
				.build());

	mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "EDIVendorTransmissionOutboundProcess",
		ediVendorTransmissionOutboundProcessMockRef);

	invokeCompositeService(COMPOSITE, REVISION, PROCESS, pRequestXml);

	waitForInvocationOf(ediVendorTransmissionOutboundProcessMockRef);

	assertThat("ItemMasterService has not been invoked", itemMasterServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("PurchaseOrderService has not been invoked", purchaseOrderServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("PrintingService has not been invoked", printingServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("EDIVendorTransmissionProcess has not been invoked",
		ediVendorTransmissionOutboundProcessMockRef.hasBeenInvoked(), is(Boolean.TRUE));

	assertThat("CarrierService has not been invoked", carrierServiceMockRef.hasBeenInvoked(),
		is(Boolean.FALSE));
	assertThat("SalesOrderService has not been invoked", salesOrderServiceMockRef.hasBeenInvoked(),
		is(Boolean.FALSE));
	assertThat("InformVendorService has not been invoked", informVendorProcessMockRef.hasBeenInvoked(),
		is(Boolean.FALSE));

	assertThat("BAL not written for P1001-GET-PET",
		getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 1);

	assertThat("BAL not written for P1001-GET-VENDOR",
		getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 1);

	assertThat("BAL written for P1001-LBL", getOtmDao().query(createBALQuery("P1001-LBL")).size() == 0);

	assertThat("BAL written for P1001-LBL-CB",
		getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 0);

	assertThat("BAL not written for P1001-MRG-PDF",
		getOtmDao().query(createBALQuery("P1001-MRG-PDF")).size() == 0);

	assertThat("BAL written for P1001-UPD-SO-LINE",
		getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE")).size() == 0);

	assertThat("BAL written for P1001-UPD-SO-LINE-CB",
		getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE-CB")).size() == 0);

	assertThat("BAL not written for P1001-DELNT",
		getOtmDao().query(createBALQuery("P1001-DELNT")).size() == 1);

	assertThat("BAL not written for P1001-DELNT-CB",
		getOtmDao().query(createBALQuery("P1001-DELNT-CB")).size() == 1);

	assertThat("BAL not written for P1001-CALL-P1002",
		getOtmDao().query(createBALQuery("P1001-CALL-P1002")).size() == 1);

	assertThat("BAL written for P1001-CALL-P1003",
		getOtmDao().query(createBALQuery("P1001-CALL-P1003")).size() == 0);
    }

    protected void executeLabelCreationAndEdiVendorTranmissionAndPoMail(final String pRequestXML) {
	final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
		new ParameterReplacer(
			readClasspathFile(String.format("%s/PurchaseOrderServiceOutput.xml", RESOURCES)))
				.replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
				.replace(REPLACE_PARAM_EDI_ORDERS_PARTNER, "PARTNER").build());
	final DefaultSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
		new ParameterReplacer(
			readClasspathFile(String.format("%s/ItemMasterServiceOutput.xml", RESOURCES)))
				.replace(REPLACE_PARAM_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString())
				.build());

	mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "InformVendorProcess", informVendorProcessMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
	mockCompositeReference(COMPOSITE, REVISION, "AttachmentService", attachmentService);
	mockCompositeReference(COMPOSITE, REVISION, "EDIVendorTransmissionOutboundProcess",
		ediVendorTransmissionOutboundProcessMockRef);

	invokeCompositeService(COMPOSITE, REVISION, PROCESS,
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, new ParameterReplacer(pRequestXML)
			.replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build()));

	waitForInvocationOf(ediVendorTransmissionOutboundProcessMockRef);
	waitForInvocationOf(informVendorProcessMockRef);

	assertThat("ItemMasterService has not been invoked", itemMasterServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("PurchaseOrderService has not been invoked", purchaseOrderServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("PrintingService has not been invoked", printingServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("CarrierService has not been invoked", carrierServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("SalesOrderService has not been invoked", salesOrderServiceMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("InformVendorService has not been invoked", informVendorProcessMockRef.hasBeenInvoked(),
		is(Boolean.TRUE));
	assertThat("EDIVendorTransmissionProcess has been invoked",
		ediVendorTransmissionOutboundProcessMockRef.hasBeenInvoked(), is(Boolean.TRUE));

	assertThat("BAL not written for P1001-GET-PET",
		getOtmDao().query(createBALQuery("P1001-GET-PET")).size() == 1);

	assertThat("BAL not written for P1001-GET-VENDOR",
		getOtmDao().query(createBALQuery("P1001-GET-VENDOR")).size() == 1);

	assertThat("BAL not written for P1001-LBL",
		getOtmDao().query(createBALQuery("P1001-LBL")).size() == 1);

	assertThat("BAL not written for P1001-LBL-CB",
		getOtmDao().query(createBALQuery("P1001-LBL-CB")).size() == 1);

	assertThat("BAL not written for P1001-MRG-PDF",
		getOtmDao().query(createBALQuery("P1001-MRG-PDF")).size() == 1);

	assertThat("BAL not written for P1001-UPD-SO-LINE",
		getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE")).size() == 1);

	assertThat("BAL not written for P1001-UPD-SO-LINE-CB",
		getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE-CB")).size() == 1);

	assertThat("BAL not written for P1001-DELNT",
		getOtmDao().query(createBALQuery("P1001-DELNT")).size() == 1);

	assertThat("BAL not written for P1001-DELNT-CB",
		getOtmDao().query(createBALQuery("P1001-DELNT-CB")).size() == 1);

	assertThat("BAL not written for P1001-CALL-P1002",
		getOtmDao().query(createBALQuery("P1001-CALL-P1002")).size() == 1);

	assertThat("BAL not written for P1001-CALL-P1003",
		getOtmDao().query(createBALQuery("P1001-CALL-P1003")).size() == 1);
    }

}
