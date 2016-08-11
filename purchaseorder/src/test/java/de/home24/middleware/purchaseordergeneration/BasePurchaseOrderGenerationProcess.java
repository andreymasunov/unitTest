package de.home24.middleware.purchaseordergeneration;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import com.google.common.base.Strings;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmPoId;
import de.home24.middleware.entity.OsmSoItemId;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.components.BaseQuery;
import de.home24.middleware.octestframework.components.BaseQuery.SqlOp;
import de.home24.middleware.octestframework.components.OtmDao;
import de.home24.middleware.octestframework.components.QueryPredicate;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class BasePurchaseOrderGenerationProcess extends AbstractBaseSoaTest {

    protected static final String COMPARATION_KEY_STATUS_TEXT = "StatusText";
    protected static final String COMPARATION_KEY_STATUS_CODE = "StatusCode";
    protected static final String COMPARATION_KEY_ERP_ITEM_ID = "ErpItemId";
    protected static final String COMPARATION_KEY_PURCHASEORDERITEMID = "PurchaseOrderItemId";
    protected static final String COMPARATION_KEY_PURCHASEORDERID = "PurchaseOrderId";

    protected static final Logger LOGGER = Logger.getLogger(InitiateDropshipOrderTest.class.getSimpleName());

    protected static final String MOCK_COMPOSITE = "PurchaseOrderGenerationProcess";
    protected static final String MOCK_COMPOSITE_REVISION = "1.3.0.0";

    protected static final String PATH_TO_RESOURCES_PROCESS = "../processes/Dropship/PurchaseOrderGenerationProcess";

    protected static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
    protected static final String REPLACE_PARAM_PAYLOAD = "PAYLOAD";
    protected static final String REPLACE_PARAM_PURCHASE_ORDER_ID = "PURCHASE_ORDER_ID";

    protected static final String PURCHASE_ORDER_ID = "DS13690541";

    protected String salesOrderId;
    protected String initiateDropshipOrderRequest;

    protected DefaultSoapMockService purchaseOrderServiceRef;
    protected DefaultSoapMockService salesOrderServiceRef;
    protected DefaultSoapMockService cancellationInvestigatorProcessRef;
    protected DefaultSoapMockService genericFaultHandlerServiceRef;
    protected DefaultSoapMockService purchaseOrderGroupHandlingProcessRef;

    public BasePurchaseOrderGenerationProcess() {
	super("dropship");
    }

    protected void sendReceivePurchaseOrderCreatedRequestToWaitingInstance(String pFilenameReceivePoCreated) {
	try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
	    HttpPost request = new HttpPost(
		    "http://localhost:7101/soa-infra/services/dropship/PurchaseOrderGenerationProcess/PurchaseOrderGenerationDelegator_ep");
	    request.addHeader("Content-Type", "text/xml; charset=utf-8");
	    request.addHeader("Accept", "text/xml");
	    final String receivePurchaseOrderCreatedRequestSoapRequest = SoapUtil.getInstance()
		    .soapEnvelope(SoapVersion.SOAP11,
			    new ParameterReplacer(readClasspathFile(String.format("%s/%s",
				    PATH_TO_RESOURCES_PROCESS, pFilenameReceivePoCreated)))
					    .replace(REPLACE_PARAM_CORRELATION_ID, salesOrderId)
					    .replace(REPLACE_PARAM_PURCHASE_ORDER_ID, PURCHASE_ORDER_ID)
					    .build());
	    request.setEntity(new StringEntity(receivePurchaseOrderCreatedRequestSoapRequest,
		    ContentType.TEXT_XML.withCharset(StandardCharsets.UTF_8)));

	    httpClient.execute(request);
	} catch (Exception e) {

	    throw new RuntimeException(e);
	}
    }

    protected void checkIfActivityCodeWasWrittenToBalActivitiesOnlyOnce(String pActivityCode,
	    String pActivityText, String pProcessId, boolean pIsError) {
	checkIfActivityCodeWasWrittenToBalActivities(pActivityCode, pActivityText, pProcessId, 1, pIsError);
    }

    protected void checkIfActivityCodeWasWrittenToBalActivities(String pActivityCode, String pActivityText,
	    String pProcessId, int numberOfOccurences, boolean pIsError) {
	final List<BalActivities> balActivities = getOtmDao().query(new BaseQuery<BalActivities>(SqlOp.SELECT,
		new QueryPredicate("correlation_id", salesOrderId).withEquals("activity_code", pActivityCode),
		BalActivities.class));
	assertThat(String.format("BAL ACTIVITIES no row found for activity code [%s]!", pActivityCode),
		balActivities, hasSize(numberOfOccurences));

	final BalActivities balActivity = balActivities.get(0);
	assertStringNotNullOrEmptyAndEqualToExpectedString("ActivityText", balActivity.getActivityText(),
		pActivityText);
	assertStringNotNullOrEmptyAndEqualToExpectedString("ProcessId", balActivity.getProcessId(),
		pProcessId);
	assertStringNotNullOrEmptyAndEqualToExpectedString("ErrorFlag", balActivity.getError(),
		(pIsError ? "Y" : "N"));
    }

    protected void checkIfStatusCodeWasWrittenToOsmPoAndOsmSoItems(
	    Map<String, List<String>> pPropertyToExpectedValue, int pNumberOfSoItems) {

	final QueryPredicate queryPredicateActivityCodeAndStatsuCode = new QueryPredicate("correlation_id",
		salesOrderId).withEquals("status_code",
			pPropertyToExpectedValue.get(COMPARATION_KEY_STATUS_CODE).get(0));
	final List<OsmPoId> osmPos = getOtmDao()
		.query(createQuery(queryPredicateActivityCodeAndStatsuCode, OsmPoId.class, "osm_po"));
	final List<OsmSoItemId> osmSoItems = getOtmDao().query(createQuery(
		queryPredicateActivityCodeAndStatsuCode, OsmSoItemId.class, "osm_so_item", "erp_item_id"));

	assertThat(String.format("OSM PO more than 1 or 0 items found: [%s]", osmPos.size()), osmPos,
		hasSize(1));
	// IF not full cancell case
	if (!pPropertyToExpectedValue.get(COMPARATION_KEY_STATUS_CODE).get(0).equals("P1000-CANCEL")) {
	    assertThat(String.format("OSM SO ITEMS less/more items found than expected: [%s]",
		    osmSoItems.size()), osmSoItems, hasSize(pNumberOfSoItems));
	}

	for (OsmPoId osmPo : osmPos) {

	    if (pPropertyToExpectedValue.containsKey(COMPARATION_KEY_PURCHASEORDERID)) {
		assertStringNotNullOrEmptyAndEqualToExpectedString("PurchaseOrderId",
			osmPo.getPurchaseOrderId(), getValueOnIndexFromList(
				pPropertyToExpectedValue.get(COMPARATION_KEY_PURCHASEORDERID), 0));
	    }
	    assertStringNotNullOrEmptyAndEqualToExpectedString(COMPARATION_KEY_STATUS_TEXT,
		    osmPo.getStatusText(),
		    getValueOnIndexFromList(pPropertyToExpectedValue.get(COMPARATION_KEY_STATUS_TEXT), 0));
	}

	for (int itemIndex = 0; itemIndex < osmSoItems.size(); itemIndex++) {

	    final OsmSoItemId osmSoItemId = osmSoItems.get(itemIndex);

	    if (pPropertyToExpectedValue.containsKey(COMPARATION_KEY_PURCHASEORDERID)) {
		assertStringNotNullOrEmptyAndEqualToExpectedString("PurchaseOrderId",
			osmSoItemId.getPurchaseOrderId(), PURCHASE_ORDER_ID);
		assertStringNotNullOrEmptyAndEqualToExpectedString(COMPARATION_KEY_ERP_ITEM_ID,
			osmSoItemId.getErpItemId(), getValueOnIndexFromList(
				pPropertyToExpectedValue.get(COMPARATION_KEY_ERP_ITEM_ID), itemIndex));
		assertStringNotNullOrEmptyAndEqualToExpectedString(COMPARATION_KEY_PURCHASEORDERITEMID,
			osmSoItemId.getPurchaseOrderItemId(),
			getValueOnIndexFromList(
				pPropertyToExpectedValue.get(COMPARATION_KEY_PURCHASEORDERITEMID),
				itemIndex));
	    }

	    assertThat("ShopItemId is not null", osmSoItemId.getShopItemId(), nullValue());
	    assertStringNotNullOrEmptyAndEqualToExpectedString(COMPARATION_KEY_STATUS_CODE,
		    osmSoItemId.getStatusCode(), getValueOnIndexFromList(
			    pPropertyToExpectedValue.get(COMPARATION_KEY_STATUS_CODE), itemIndex));
	    assertStringNotNullOrEmptyAndEqualToExpectedString(COMPARATION_KEY_STATUS_TEXT,
		    osmSoItemId.getStatusText(), getValueOnIndexFromList(
			    pPropertyToExpectedValue.get(COMPARATION_KEY_STATUS_TEXT), itemIndex));
	}
    }

    /**
     * Returns the value from the {@link List} at the specified index. If the
     * {@link List} has a size of 1 then always the first element is returned
     * 
     * @param pValues
     *            the {@link List} to return the value from
     * @param pIndex
     *            the index for the value to take
     * @return the value at the specified index or the first element, if
     *         {@link List} size is 1
     */
    String getValueOnIndexFromList(List<String> pValues, int pIndex) {

	return pValues.size() == 1 ? pValues.get(0) : pValues.get(pIndex);
    }

    protected int getBalActivitiesCount(String pActivityCode, String pActivityText, String pProcessId,
	    boolean pIsError) {
	final List<BalActivities> balActivities = getOtmDao().query(new BaseQuery<BalActivities>(SqlOp.SELECT,
		new QueryPredicate("correlation_id", salesOrderId).withEquals("activity_code", pActivityCode),
		BalActivities.class));
	return balActivities.size();
    }

    private void assertStringNotNullOrEmptyAndEqualToExpectedString(String pFieldname, String pCurrentValue,
	    String pExpectedValue) {
	assertThat(String.format("%s is not expected to be null", pFieldname), pCurrentValue,
		not(nullValue()));
	assertThat(String.format("%s does not have expected value", pFieldname), pCurrentValue,
		equalTo(pExpectedValue));
    }

    <T> OtmDao.Query<T> createQuery(final QueryPredicate pQueryPredicate, final Class<T> pClass,
	    final String pTablename, final String pOrderByColumn) {
	return new OtmDao.Query<T>() {

	    @Override
	    public Class<T> getExpectedType() {
		return pClass;
	    }

	    @Override
	    public String getQuery() {
		return Strings.isNullOrEmpty(pOrderByColumn)
			? String.format("select * from %s %s", pTablename, pQueryPredicate.getPredicate())
			: String.format("select * from %s %s order by %s", pTablename,
				pQueryPredicate.getPredicate(), pOrderByColumn);
	    }

	    @Override
	    public Object[] getQueryParameters() {
		return pQueryPredicate.getParameters();
	    }
	};
    }

    <T> OtmDao.Query<T> createQuery(final QueryPredicate pQueryPredicate, final Class<T> pClass,
	    final String pTablename) {
	return createQuery(pQueryPredicate, pClass, pTablename, null);
    }
}
