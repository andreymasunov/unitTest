package de.home24.middleware.purchaseordergrouphandlingprocess;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;
import de.home24.middleware.octestframework.mock.*;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;
import org.junit.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;

public class PurchaseOrderGroupHandlingProcessTest extends BasePurchaseOrderGroupHandlingTest {

    @Test
    public void labelCreationAndPoMailOnly() throws Exception {

        executeLabelCreationAndPoMailOnly(
                readClasspathFile(String.format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)));
    }

    @Test
    public void labelCreationOnlyWithSalesOrderLineUpdateException() throws Exception {

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

        // PreparedSalesOrdLine Update exception callback test
        // Prepare Resend and abort mocks

        List<MockResponsePojo> genericFaultHandlerMockResponses = Lists.newArrayList();
        // Prepare resend
        String genericFaultHandlerResend = /*
                        * SoapUtil.getInstance().
					    * soapEnvelope(SoapVersion.SOAP11,
					    */ new ParameterReplacer(
                readClasspathFile(String.format("%s/GenericFaultHandlerResend.xml", RESOURCES))).replace(
                REPLACE_PARAM_PAYLOAD,
                new ParameterReplacer(readClasspathFile(
                        String.format("%s/UpdateSalesOrderLineRequest.xml", RESOURCES))).build())
                .build()/* ) */;
        genericFaultHandlerMockResponses
                .add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerResend));

        String genericFaultHandlerAbort = new ParameterReplacer(
                readClasspathFile(String.format("%s/GenericFaultHandlerAbort.xml", RESOURCES))).replace(
                REPLACE_PARAM_PAYLOAD,
                new ParameterReplacer(readClasspathFile(
                        String.format("%s/UpdateSalesOrderLineRequest.xml", RESOURCES))).build())
                .build()/* ) */;
        genericFaultHandlerMockResponses
                .add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerAbort));

        genericFaultHandler = new DefaultSoapMockService(genericFaultHandlerMockResponses);

        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);

        salesOrderServiceMockRef = new DefaultSoapMockService(
                new ParameterReplacer(
                        readClasspathFile(String.format("%s/CallbackExceptionSample.xml", RESOURCES)))
                        .replace(REPLACE_PARAM_PAYLOAD,
                                readClasspathFile(String.format("%s/UpdateSalesOrderLineRequest.xml",
                                        RESOURCES)))
                        .replace(REPLACE_PARAM_ACTIVITY_ID, "P1001-UPD-SO-LINE").build());

        mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "InformVendorProcess", informVendorProcessMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "AttachmentService", attachmentService);

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                SoapUtil.getInstance()
                        .soapEnvelope(SoapVersion.SOAP11,
                                new ParameterReplacer(readClasspathFile(String
                                        .format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)))
                                        .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7")
                                        .build()));

        waitForInvocationOf(salesOrderServiceMockRef, 2);

        waitForInvocationOf(genericFaultHandler, 2, 20);

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
                is(Boolean.FALSE));

        assertThat("InformVendorService has not been invoked", genericFaultHandler.getNumberOfInvocations(),
                is(2));

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

        // P1001-UPD-SO-LINE must be written two times because retry to BAL
        assertThat("BAL not written for P1001-UPD-SO-LINE 2x",
                getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE")).size() == 2);

        // P1001-UPD-SO-LINE-CB must be written once to BAL
        assertThat("BAL should not been written for P1001-UPD-SO-LINE-CB",
                getOtmDao().query(createBALQuery("P1001-UPD-SO-LINE-CB")).size() == 0);

        // P1001-DELNT must be written once to BAL
        assertThat("BAL not written for P1001-DELNT",
                getOtmDao().query(createBALQuery("P1001-DELNT")).size() == 1);

        // P1001-DELNT-CB must be written once to BAL
        assertThat("BAL not written for P1001-DELNT-CB",
                getOtmDao().query(createBALQuery("P1001-DELNT-CB")).size() == 1);

        // P1001-CALL-P1002 must NOT be written to BAL
        assertThat("BAL should not been written for P1001-CALL-P1002",
                getOtmDao().query(createBALQuery("P1001-CALL-P1002")).size() == 0);

        // P1001-CALL-P1003 must be written once to BAL
        assertThat("BAL should not been written for P1001-CALL-P1003",
                getOtmDao().query(createBALQuery("P1001-CALL-P1003")).size() == 0);

        // Assertion for generic fault handler requests.
        String genericFaultActivityId = evaluateXpath(
                "//gfhm:handleFaultRequest/gfhm:faultInformation/exception:exception/exception:context/exception" +
                        ":activityId/text()",
                genericFaultHandler.getReceivedRequests().get(0));

        assertThat(
                "GenericFaultHandler activity ID in update  sales order line case is not P1001-UPD-SO-LINE-ERR",
                genericFaultActivityId, is("P1001-UPD-SO-LINE-ERR"));
    }

    @Test
    public void labelCreationOnlyDeliveryNoteException() throws Exception {

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

        List<MockResponsePojo> genericFaultHandlerMockResponses = Lists.newArrayList();

        String genericFaultHandlerResend = new ParameterReplacer(
                readClasspathFile(String.format("%s/GenericFaultHandlerResend.xml", RESOURCES)))
                .replace(REPLACE_PARAM_PAYLOAD,
                        new ParameterReplacer(readClasspathFile(
                                String.format("%s/CreateDeliveryNoteRequest.xml", RESOURCES)))
                                .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build())
                .build();

        genericFaultHandlerMockResponses
                .add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerResend));

        String genericFaultHandlerAbort = new ParameterReplacer(
                readClasspathFile(String.format("%s/GenericFaultHandlerAbort.xml", RESOURCES)))
                .replace(REPLACE_PARAM_PAYLOAD,
                        new ParameterReplacer(readClasspathFile(
                                String.format("%s/CreateDeliveryNoteRequest.xml", RESOURCES)))
                                .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build())
                .build();
        genericFaultHandlerMockResponses
                .add(new MockResponsePojo(ResponseType.SOAP_RESPONSE, genericFaultHandlerAbort));

        genericFaultHandler = new DefaultSoapMockService(genericFaultHandlerMockResponses);
        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);

        printingServiceMockRef = new DefaultSoapMockService(new ParameterReplacer(
                readClasspathFile(String.format("%s/CallbackExceptionSample.xml", RESOURCES)))
                .replace(REPLACE_PARAM_PAYLOAD,
                        new ParameterReplacer(readClasspathFile(
                                String.format("%s/CreateDeliveryNoteRequest.xml", RESOURCES)))
                                .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build())
                .replace(REPLACE_PARAM_ACTIVITY_ID, "P1001-DELNT").build());

        mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "InformVendorProcess", informVendorProcessMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "AttachmentService", attachmentService);

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                SoapUtil.getInstance()
                        .soapEnvelope(SoapVersion.SOAP11,
                                new ParameterReplacer(readClasspathFile(String
                                        .format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)))
                                        .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7")
                                        .build()));

        waitForInvocationOf(printingServiceMockRef, 2);
        waitForInvocationOf(genericFaultHandler, 2, 20);
        waitForInvocationOf(salesOrderServiceMockRef);

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
                is(Boolean.FALSE));

        assertThat("InformVendorService has not been invoked", genericFaultHandler.getNumberOfInvocations(),
                is(2));

        assertThat("PrintingServiceMockRef has not been invoked",
                printingServiceMockRef.getNumberOfInvocations(), is(2));

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

        assertThat("BAL not written for P1001-DELNT 2x",
                getOtmDao().query(createBALQuery("P1001-DELNT")).size() == 2);

        assertThat("BAL should not been written for P1001-DELNT-CB",
                getOtmDao().query(createBALQuery("P1001-DELNT-CB")).size() == 0);

        assertThat("BAL should not been written for P1001-CALL-P1002",
                getOtmDao().query(createBALQuery("P1001-CALL-P1002")).size() == 0);

        assertThat("BAL should not been written for P1001-CALL-P1003",
                getOtmDao().query(createBALQuery("P1001-CALL-P1003")).size() == 0);

        String genericFaultActivityId = evaluateXpath(
                "//gfhm:handleFaultRequest/gfhm:faultInformation/exception:exception/exception:context/exception" +
                        ":activityId/text()",
                genericFaultHandler.getReceivedRequests().get(0));

        assertThat("GenericFaultHandler activity ID in update  sales order line case is not P1001-DELNT-ERR",
                genericFaultActivityId, is("P1001-DELNT-ERR"));
    }

    @Test
    public void noLabelCreationAndEdiVendorTranmissionOnlyWhenSupplierChannelIsDropshipChannelGer12()
            throws Exception {

        executeNoLabelCreationAndEdiVendorTranmissionOnlyWhenSupplierChannelIsDropshipChannelGer12(
                readClasspathFile(String.format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)));
    }

    @Test
    public void labelCreationAndEdiVendorTranmissionAndPoMail() throws Exception {

        executeLabelCreationAndEdiVendorTranmissionAndPoMail(
                readClasspathFile(String.format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)));
    }

    @Test
    public void retryPrintingService() {

        final String requestXML = new ParameterReplacer(
                readClasspathFile(String.format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)))
                .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

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

        printingServiceMockRef = new DefaultSoapMockService(
                Lists.newArrayList(
                        new MockResponsePojo(ResponseType.SOAP_RESPONSE,
                                readClasspathFile(
                                        String.format("%s/PrintingServiceExceptionOutput.xml", RESOURCES))),
                        new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(
                                String.format("%s/PrintingServiceOutput.xml", RESOURCES)))));

        genericFaultHandler = new DefaultSoapMockService(
                readClasspathFile(String.format("%s/GenericFaultHandlerResendDNOutput.xml", RESOURCES)));

        mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

        waitForInvocationOf(informVendorProcessMockRef);

        assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(),
                is(true));
        assertThat("PrintingService has not been invoked!", printingServiceMockRef.hasBeenInvoked(),
                is(true));
        assertThat(
                String.format("PrintingService has been invoked for %s times only!",
                        printingServiceMockRef.getNumberOfInvocations()),
                printingServiceMockRef.getNumberOfInvocations(), equalTo(2));

        declareXpathNS("dn", "http://home24.de/interfaces/bas/printingservice/printingservicemessages/v1");

        assertXpathEvaluatesTo("//dn:createDeliveryNoteRequest/dn:requestHeader/header:CorrelationID/text()",
                "1231376876", printingServiceMockRef.getLastReceivedRequest());
    }

    @Test
    public void retryCarrierService() {

        final String requestXML = new ParameterReplacer(
                readClasspathFile(String.format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)))
                .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

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

        carrierServiceMockRef = new RetryWithExceptionSoapMockService(1,
                new MockResponsePojo(ResponseType.SOAP_RESPONSE,
                        readClasspathFile(String.format("%s/CarrierServiceFault.xml", RESOURCES))),
                new MockResponsePojo(ResponseType.SOAP_RESPONSE,
                        readClasspathFile(String.format("%s/CarrierServiceOutput.xml", RESOURCES))));

        genericFaultHandler = new DefaultSoapMockService(readClasspathFile(
                String.format("%s/GenericFaultHandlerResendOutput_CreateLabel.xml", RESOURCES)));

        mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

        waitForInvocationOf(attachmentService);

        assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(),
                is(true));
        assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(),
                is(true));
        assertThat("PurchaseOrder service has not been invoked!",
                purchaseOrderServiceMockRef.hasBeenInvoked(), is(true));
        assertThat("CarrierService has not been invoked exactly twice!",
                carrierServiceMockRef.getNumberOfInvocations() == 2);
    }

    @Test
    public void retryAttachmentService() {

        final String requestXML = new ParameterReplacer(
                readClasspathFile(String.format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)))
                .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

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

        attachmentService = new RetryWithExceptionSoapMockService(1,
                new MockResponsePojo(ResponseType.SOAP_RESPONSE,
                        readClasspathFile(String.format("%s/AttachmentServiceFault.xml", RESOURCES))),
                new MockResponsePojo(ResponseType.SOAP_RESPONSE,
                        readClasspathFile(String.format("%s/AttachmentServiceResponse.xml", RESOURCES))));

        genericFaultHandler = new DefaultSoapMockService(readClasspathFile(
                String.format("%s/GenericFaultHandlerResendOutput_CreateLabel.xml", RESOURCES)));

        printingServiceMockRef = new DefaultSoapMockService(
                readClasspathFile(String.format("%s/PrintingServiceOutput.xml", RESOURCES)));

        mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "AttachmentService", attachmentService);
        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
        mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

        waitForInvocationOf(attachmentService);
        waitForInvocationOf(genericFaultHandler);

        assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(),
                is(true));
        assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(),
                is(true));
        assertThat("PurchaseOrder service has not been invoked!",
                purchaseOrderServiceMockRef.hasBeenInvoked(), is(true));
        assertThat("CarrierService has not been invoked", carrierServiceMockRef.hasBeenInvoked(), is(true));
        assertThat("AttachmentService has not been invoked twice",
                attachmentService.getNumberOfInvocations() == 2);

        assertThat("BAL not written for P1001-MRG-PDF",
                getOtmDao().query(createBALQuery("P1001-MRG-PDF")).size() == 2);
        assertThat("BAL not written for P1001-MRG-PDF-CB",
                getOtmDao().query(createBALQuery("P1001-MRG-PDF-CB")).size() == 1);

    }

    @Test
    public void retryItemMasterServiceBusinessFault() {

        final String requestXML = new ParameterReplacer(
                readClasspathFile(String.format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)))
                .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "DNK-9").build();

        final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
                new ParameterReplacer(
                        readClasspathFile(String.format("%s/PurchaseOrderServiceOutput.xml", RESOURCES)))
                        .replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
                        .replace(REPLACE_PARAM_EDI_ORDERS_PARTNER, "").build());

        final AbstractSoapMockService itemMasterServiceMockRef = new RetryWithExceptionSoapMockService(1,
                new MockResponsePojo(ResponseType.BUSINESS_FAULT,
                        readClasspathFile(String.format("%s/ItemMasterServiceBusinessFault.xml", RESOURCES))),
                new MockResponsePojo(ResponseType.SOAP_RESPONSE,
                        readClasspathFile(String.format("%s/ItemMasterServiceOutput.xml", RESOURCES))));

        genericFaultHandler = new DefaultSoapMockService(readClasspathFile(
                String.format("%s/GenericFaultHandlerResendOutput_GetItemMasterData.xml", RESOURCES)));

        mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

        waitForInvocationOf(purchaseOrderServiceMockRef);

        assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(),
                is(true));
        assertThat("ItemMaster service has not been invoked exactly twice!",
                itemMasterServiceMockRef.getNumberOfInvocations() == 2);
        assertThat("PurchaseOrder service has not been invoked!",
                purchaseOrderServiceMockRef.hasBeenInvoked(), is(true));
    }

    @Test
    public void retryItemMasterServiceTechnicalFault() {

        final String requestXML = new ParameterReplacer(
                readClasspathFile(String.format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)))
                .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "DNK-9").build();

        final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
                new ParameterReplacer(
                        readClasspathFile(String.format("%s/PurchaseOrderServiceOutput.xml", RESOURCES)))
                        .replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.FALSE.toString())
                        .replace(REPLACE_PARAM_EDI_ORDERS_PARTNER, "").build());

        final AbstractSoapMockService itemMasterServiceMockRef = new RetryWithExceptionSoapMockService(1,
                new MockResponsePojo(ResponseType.BUSINESS_FAULT, ""),
                new MockResponsePojo(ResponseType.SOAP_RESPONSE,
                        readClasspathFile(String.format("%s/ItemMasterServiceOutput.xml", RESOURCES))));

        genericFaultHandler = new DefaultSoapMockService(readClasspathFile(
                String.format("%s/GenericFaultHandlerResendOutput_GetItemMasterData.xml", RESOURCES)));

        mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

        waitForInvocationOf(purchaseOrderServiceMockRef);

        assertThat("GenericFaultHandler has not been invoked!", genericFaultHandler.hasBeenInvoked(),
                is(true));
        assertThat("ItemMaster service has not been invoked exactly twice!",
                itemMasterServiceMockRef.getNumberOfInvocations() == 2);
        assertThat("PurchaseOrder service has not been invoked!",
                purchaseOrderServiceMockRef.hasBeenInvoked(), is(true));
    }

    @Test
    public void retryPurchaseOrderServiceBusinessFault() {

        retryPurchaseOrderService(new RetryWithExceptionSoapMockService(1,
                new MockResponsePojo(ResponseType.BUSINESS_FAULT,
                        readClasspathFile(String.format("%s/PurchaseOrderServiceBusinessFault.xml",
                                RESOURCES))),
                new MockResponsePojo(ResponseType.SOAP_RESPONSE,
                        new ParameterReplacer(readClasspathFile(
                                String.format("%s/PurchaseOrderServiceOutput.xml", RESOURCES)))
                                .replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.TRUE.toString())
                                .replace(REPLACE_PARAM_EDI_ORDERS_PARTNER, "PARTNER").build())));
    }

    @Test
    public void retryPurchaseOrderServiceTechnicalFault() {

        retryPurchaseOrderService(new RetryWithExceptionSoapMockService(1,
                new MockResponsePojo(ResponseType.BUSINESS_FAULT, ""),
                new MockResponsePojo(ResponseType.SOAP_RESPONSE,
                        new ParameterReplacer(readClasspathFile(
                                String.format("%s/PurchaseOrderServiceOutput.xml", RESOURCES)))
                                .replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.TRUE.toString())
                                .replace(REPLACE_PARAM_EDI_ORDERS_PARTNER, "PARTNER").build())));
    }

    void retryPurchaseOrderService(RetryWithExceptionSoapMockService pPurchaseOrderMockService) {

        final String requestXML = new ParameterReplacer(
                readClasspathFile(String.format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)))
                .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").build();

        final AbstractSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
                readClasspathFile(String.format("%s/ItemMasterServiceOutput.xml", RESOURCES)));

        genericFaultHandler = new DefaultSoapMockService(readClasspathFile(
                String.format("%s/GenericFaultHandlerResendOutput_ReadVendorInformation.xml", RESOURCES)));

        printingServiceMockRef = new DefaultSoapMockService(
                readClasspathFile(String.format("%s/PrintingServiceOutput.xml", RESOURCES)));

        mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", pPurchaseOrderMockService);
        mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandler);
        mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, requestXML));

        waitForInvocationOf(printingServiceMockRef);

        assertThat("GenericFaultHandler has not been invoked exactly once!",
                genericFaultHandler.getNumberOfInvocations() == 1);
        assertThat("ItemMaster service has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(),
                is(true));
        assertThat("PurchaseOrder service has not been invoked!", pPurchaseOrderMockService.hasBeenInvoked(),
                is(true));
        assertThat("Printing service has not been invoked!", printingServiceMockRef.hasBeenInvoked(),
                is(true));
    }

    @Test
    public void whenSupplierChannelIsNoDropshipChannelDnk9ThenTerminateProcessInstance(
            final String pRequestXml) {

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
        mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "AttachmentService", attachmentService);
        mockCompositeReference(COMPOSITE, REVISION, "EDIVendorTransmissionOutboundProcess",
                ediVendorTransmissionOutboundProcessMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "InformVendorProcess", informVendorProcessMockRef);

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                SoapUtil.getInstance()
                        .soapEnvelope(SoapVersion.SOAP11,
                                new ParameterReplacer(readClasspathFile(String
                                        .format("%s/PurchaseOrderGroupHandlingProcessInput.xml", RESOURCES)))
                                        .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "DNK-9")
                                        .build()));

        waitForInvocationOf(ediVendorTransmissionOutboundProcessMockRef);

        assertThat("PurchaseOrderServiceRef has not been invoked!",
                purchaseOrderServiceMockRef.hasBeenInvoked(), equalTo(Boolean.TRUE));
        assertThat("ItemMasterService has not been invoked!", itemMasterServiceMockRef.hasBeenInvoked(),
                equalTo(Boolean.TRUE));
        assertThat("PrintingService has been invoked!", printingServiceMockRef.hasBeenInvoked(),
                equalTo(Boolean.FALSE));
        assertThat("CarrierService has not been invoked!", carrierServiceMockRef.hasBeenInvoked(),
                equalTo(Boolean.FALSE));
        assertThat("AttachmentService has not been invoked!", attachmentService.hasBeenInvoked(),
                equalTo(Boolean.FALSE));
        assertThat("EDIVendorTransmissionOutboundProcess has not been invoked!",
                ediVendorTransmissionOutboundProcessMockRef.hasBeenInvoked(), equalTo(Boolean.FALSE));
        assertThat("PurchaseOrderInformVendorProcess has not been invoked!",
                informVendorProcessMockRef.hasBeenInvoked(), equalTo(Boolean.FALSE));
    }

    @Test
    public void whenCarrierServiceRetunsWrongAddressFautlThenProcessShouldSuccessfullyCompleteAfterAddressCorrection() {

        final DefaultSoapMockService purchaseOrderServiceMockRef = new DefaultSoapMockService(
                new ParameterReplacer(
                        readClasspathFile(String.format("%s/PurchaseOrderServiceOutput.xml", RESOURCES)))
                        .replace(REPLACE_PARAM_NOTE_SUPPRESS_PO_MAIL, Boolean.TRUE.toString())
                        .replace(REPLACE_PARAM_EDI_ORDERS_PARTNER, "").build());

        final DefaultSoapMockService itemMasterServiceMockRef = new DefaultSoapMockService(
                new ParameterReplacer(
                        readClasspathFile(String.format("%s/ItemMasterServiceOutput.xml", RESOURCES)))
                        .replace(REPLACE_PARAM_NO_EDI_PROCESS_AVAILABLE, Boolean.FALSE.toString())
                        .build());

//        Lists
//                .newArrayList(new MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile
//                        (String.format("%s/GetAddressDataSuccessResponse.xml", RESOURCES))),
        final DefaultSoapMockService salesOrderServiceRef = new DefaultSoapMockService(readClasspathFile((String.format
                ("%s/ReceiveSalesOrderLineUpdatedRequest.xml", RESOURCES))));

        final DefaultSoapMockService carrierServiceWornAddressMockRef = new DefaultAsyncSoapMockService(Lists
                .newArrayList(new MockResponsePojo(ResponseType.SOAP_RESPONSE, new
                        ParameterReplacer(readClasspathFile(String.format("%s/CarrierServiceWrongAddressFault.xml",
                        RESOURCES))).replace(REPLACE_PARAM_CORRELATION_ID, correlationId).build()), new
                        MockResponsePojo(ResponseType.SOAP_RESPONSE, readClasspathFile(String.format
                        ("%s/CarrierServiceOutput.xml", RESOURCES)))));

        final AbstractSoapMockService genericFaultHandlerServiceRef = new GenericFaultHandlerMock
                (GenericFaultHandlerMock.FaultStrategy.RESEND);

        mockCompositeReference(COMPOSITE, REVISION, "PurchaseOrderService", purchaseOrderServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "ItemMasterService", itemMasterServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "PrintingService", printingServiceMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "CarrierService", carrierServiceWornAddressMockRef);
        mockCompositeReference(COMPOSITE, REVISION, "GenericFaultHandler", genericFaultHandlerServiceRef);
        mockCompositeReference(COMPOSITE, REVISION, "SalesOrderService", salesOrderServiceRef);
//        mockCompositeReference(COMPOSITE, REVISION, "AttachmentService", attachmentService);
//        mockCompositeReference(COMPOSITE, REVISION, "EDIVendorTransmissionOutboundProcess",
//                ediVendorTransmissionOutboundProcessMockRef);
//        mockCompositeReference(COMPOSITE, REVISION, "InformVendorProcess", informVendorProcessMockRef);

        invokeCompositeService(COMPOSITE, REVISION, PROCESS,
                SoapUtil.getInstance()
                        .soapEnvelope(SoapVersion.SOAP11,
                                new ParameterReplacer(readClasspathFile(String
                                        .format("%s/PurchaseOrderGroupHandlingProcessWrongAddressInput.xml",
                                                RESOURCES)))
                                        .replace(REPLACE_PARAM_NOTE_PURCHASINGORDER, "GER-7").replace
                                        (REPLACE_PARAM_CORRELATION_ID, correlationId)
                                        .build()));

        waitForInvocationOf(informVendorProcessMockRef);

        assertThat("PurchaseOrderServiceMockRef should be invoked", purchaseOrderServiceMockRef.hasBeenInvoked(),
                equalTo(true));
        assertThat("ItemMasterServiceMockRef should be invoked", itemMasterServiceMockRef.hasBeenInvoked(), equalTo
                (true));
        assertThat("CarrierServiceMockRef should be invoked", carrierServiceWornAddressMockRef.hasBeenInvoked(),
                equalTo(true));
        assertThat("SalesOrderServiceRef should be invoked", salesOrderServiceRef.hasBeenInvoked(), equalTo(true));
        assertThat("GenericFaultHandlerServiceRef should be invoked", genericFaultHandlerServiceRef.hasBeenInvoked(),
                equalTo(true));
        assertThat("PrintingServiceMockRef should be invoked", printingServiceMockRef.hasBeenInvoked(), equalTo(true));
        assertThat("InformVendorProcessMockRef should be invoked", informVendorProcessMockRef.hasBeenInvoked(),
                equalTo(true));
        assertThat("EdiVendorTransmissionOutboundProcessMockRef should not be invoked",
                ediVendorTransmissionOutboundProcessMockRef.hasBeenInvoked(), equalTo(false));
    }
}
