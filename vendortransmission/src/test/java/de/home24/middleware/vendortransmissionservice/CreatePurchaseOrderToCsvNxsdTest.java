package de.home24.middleware.vendortransmissionservice;

import com.opitzconsulting.soa.testing.AbstractXmlTest;
import com.opitzconsulting.soa.testing.nxsdtransation.XmlNativeTranslator;
import oracle.tip.pc.services.translation.framework.Translator;
import org.junit.Test;

import java.io.File;
import java.util.logging.Logger;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

/**
 * Test for PurchaseOrderCsv.xsd NXSD transformation.
 * <p>
 * Created by svb on 14/07/16.
 */
public class CreatePurchaseOrderToCsvNxsdTest extends AbstractXmlTest {

    private static final Logger LOGGER = Logger.getLogger(CreatePurchaseOrderToCsvNxsdTest.class.getSimpleName());
    private static final String PATH_TO_PURCHASEORDER_TO_CSV_NXSD = "../nxsd/VendorTransmission/VendorTransmissionService/operations/convertPurchaseOrderToCSV/wsdl/xsd/PurchaseOrderCsv.xsd";
    private static final String PATH_TO_VENDORTRANSMISSION_RESOURCES = "../servicebus/VendorTransmission/VendorTransmissionService/convertPurchaseOrderToCSVRequest";
    private static final String PURCHASE_ORDER_ROOT_ELEMENT = "PurchaseOrder";

    @Test
    public void whenValidPurchaseOrderXmlIsPassedToNxsdThenCorrespondingCsvIsCreated() throws Exception {

        final File purchaseOrderToCsvNxsd = new File(getClass().getResource(PATH_TO_PURCHASEORDER_TO_CSV_NXSD).toURI());

        assertThat("NXSD file should not be empty!", purchaseOrderToCsvNxsd, not(nullValue()));

        final Translator translator = XmlNativeTranslator.createNxsdTranslator(purchaseOrderToCsvNxsd, PURCHASE_ORDER_ROOT_ELEMENT);

        final String purchaseOrderNxsdInput = readClasspathFile(String.format("%s/ConvertOrderToCsvNxsdInput.xml", PATH_TO_VENDORTRANSMISSION_RESOURCES));
        final String purchaseOrderExpectedOutput = readClasspathFile(String.format("%s/ConvertOrderToCsvNxsdExpectedOutput.csv", PATH_TO_VENDORTRANSMISSION_RESOURCES));

        assertThat("Expected output must not be null!", purchaseOrderExpectedOutput, not(nullValue()));

        final String nxsdTransformationResult = XmlNativeTranslator.translateToNative(purchaseOrderNxsdInput, translator);

        LOGGER.fine(String.format("Result of NXSD transformation: %s", nxsdTransformationResult));

        assertThat("Output from NXSD transformation is not as expected!", purchaseOrderExpectedOutput, equalTo(nxsdTransformationResult));
    }
}
