package de.home24.middleware.purchaseordertrigger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.util.logging.Logger;

import org.junit.Test;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.MessagingDao.JmsModule;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.util.XpathProcessor;

/**
 * Tests for PurchaseOrderTrigger, operation: startPurchaseOrderProcess
 * 
 * @author svb
 *
 */
public class PurchaseOrderTriggerStartPurchaseOrderProcess extends AbstractBaseSoaTest {

    private static final Logger LOGGER = Logger
	    .getLogger(PurchaseOrderTriggerStartPurchaseOrderProcess.class.getSimpleName());

    private static final String PO_GENERATION_BUSINESS_SERVICE_NAME = "PurchaseOrderTrigger/operations/startPurchaseOrderProcess/business-service/PurchaseOrderGenerationProcess";

    @Test
    public void whenNavisionSendsPurchaseOrderResponseThenConvertToOagisPurchaseOrder() throws Exception {

	final DefaultSoapMockService poGenerationProcessRef = new DefaultSoapMockService();
	mockOsbBusinessService(PO_GENERATION_BUSINESS_SERVICE_NAME, poGenerationProcessRef);

	final String purchaseOrderResponse = readClasspathFile(
		"../queues/h24_PurchaseOrder/RSP_PurchaseOrder_Q/PurchaseOrderResponseFromNav.xml");

	getOsbAccessor().flushUriChanges();
	getMessagingDao().writeToQueue(JmsModule.PURCHASE_ORDER, "h24jms.RSP_PurchaseOrder_Q",
		purchaseOrderResponse);

	waitForInvocationOf(poGenerationProcessRef);

	LOGGER.fine(String.format("################ Transformed PO: %s",
		poGenerationProcessRef.getLastReceivedRequest()));

	assertThat(poGenerationProcessRef.hasBeenInvoked(), equalTo(Boolean.TRUE));

	final String expectedPurchaseOrderRequest = readClasspathFile(
		"../servicebus/PurchaseOrder/PurchaseOrderTrigger/startPurchaseOrderProcess/ReceivePurchaseOrderCreatedRequest.xml");

	final String poGenerationRequestWithoutSoapEnvelope = new XpathProcessor().evaluateXPath(
		"/soapenv:Envelope/soapenv:Body/*", poGenerationProcessRef.getLastReceivedRequest());

	assertXmlEquals(expectedPurchaseOrderRequest, poGenerationRequestWithoutSoapEnvelope);
    }
}
