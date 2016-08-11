package de.home24.middleware.purchaseordergrouphandlingprocess;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.octestframework.util.XpathProcessor;

/**
 * Tests for PurchaseOrderGroupHandlingProcess in case of sparepart orders
 * 
 * @author svb
 *
 */
public class PurchaseOrderGroupHandlingProcessSparepartTest extends BasePurchaseOrderGroupHandlingTest {

    private static final String REPLACE_PARAM_PURCHASE_ORDER_ID = "PURCHASE_ORDER_ID";
    private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";

    private static final String PURCHASE_ORDER_ID = "DS13690541";
    private static final String VENDOR_MOTHER_ITEM_NO = "AM99999999";
    private static final String ORIGINAL_PURCHASE_ORDER_NO = "DS123456789";

    @Override
    protected DefaultSoapMockService createCarrierServiceMockRef() {
	return new DefaultSoapMockService(new ParameterReplacer(
		readClasspathFile(String.format("%s/SparepartCarrierServiceOutput.xml", RESOURCES)))
			.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID)
			.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID).build());
    }

    @Test
    public void labelCreationAndPoMailOnly() throws Exception {

	executeLabelCreationAndPoMailOnly(createPurchaseOrderSparepartRequest());
	assertOriginalPurchaseOrderNoAndVendorMotherItemNo(printingServiceMockRef.getLastReceivedRequest());
	assertOriginalPurchaseOrderNoAndVendorMotherItemNo(
		informVendorProcessMockRef.getLastReceivedRequest());
    }

    @Test
    public void noLabelCreationAndEdiVendorTranmissionOnlyWhenSupplierChannelIsDropshipChannelGer12()
	    throws Exception {

	executeNoLabelCreationAndEdiVendorTranmissionOnlyWhenSupplierChannelIsDropshipChannelGer12(
		createPurchaseOrderSparepartRequest());
	assertOriginalPurchaseOrderNoAndVendorMotherItemNo(printingServiceMockRef.getLastReceivedRequest());
	assertOriginalPurchaseOrderNoAndVendorMotherItemNo(
		ediVendorTransmissionOutboundProcessMockRef.getLastReceivedRequest());
    }

    @Test
    public void labelCreationAndEdiVendorTranmissionAndPoMail() throws Exception {

	executeLabelCreationAndEdiVendorTranmissionAndPoMail(createPurchaseOrderSparepartRequest());
	assertOriginalPurchaseOrderNoAndVendorMotherItemNo(printingServiceMockRef.getLastReceivedRequest());
	assertOriginalPurchaseOrderNoAndVendorMotherItemNo(
		ediVendorTransmissionOutboundProcessMockRef.getLastReceivedRequest());
	assertOriginalPurchaseOrderNoAndVendorMotherItemNo(
		informVendorProcessMockRef.getLastReceivedRequest());
    }

    String createPurchaseOrderSparepartRequest() {
	return new ParameterReplacer(readClasspathFile(String.format(
		"../processes/Dropship/PurchaseOrderGenerationProcess/SparepartExpectedProcessPurchaseOrderGroupRequest.xml",
		RESOURCES))).replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID)
			.replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID).build();
    }

    void assertOriginalPurchaseOrderNoAndVendorMotherItemNo(String pReceivedXml) throws Exception {
	final XpathProcessor xpathProcessor = new XpathProcessor();
	xpathProcessor.declareNamespace("oagis", "http://www.openapplications.org/oagis/10");
	xpathProcessor.declareNamespace("msg",
		"http://home24.de/interfaces/bas/printingservice/printingservicemessages/v1");

	final String originalPurchaseOrderNo = xpathProcessor.evaluateXPath(
		"//oagis:PurchaseOrderLine[1]/oagis:LineIDSet/oagis:ID[@typeCode='OriginalPurchaseOrderNo']/text()",
		pReceivedXml);

	final String vendorMotherItemNo = xpathProcessor.evaluateXPath(
		"//oagis:PurchaseOrderLine[1]/oagis:Item/oagis:SupplierItemIdentification/oagis:LineIDSet/oagis:ID[@typeCode='VendorMotherItemNo']/text()",
		pReceivedXml);

	LOGGER.fine("################## Original PO: " + originalPurchaseOrderNo);
	LOGGER.fine("################## VendorMotherItemNo: " + vendorMotherItemNo);

	assertThat("OriginalPurchaseOrderNo should not be null or empty!", originalPurchaseOrderNo,
		equalTo(ORIGINAL_PURCHASE_ORDER_NO));
	assertThat("VendorMotherItemNo should not be null or empty!", vendorMotherItemNo,
		equalTo(VENDOR_MOTHER_ITEM_NO));
    }
}
