package de.home24.middleware.itemmaster;

import static org.junit.Assert.fail;

import java.util.List;

import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.matchers.Times;
import org.mockserver.model.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import de.home24.middleware.octestframework.AbstractRestMockSoaTest;
import de.home24.middleware.octestframework.util.ParameterReplacer;

public class ItemMasterServiceTest extends AbstractRestMockSoaTest {

    private final static Logger logger = LoggerFactory.getLogger(ItemMasterServiceTest.class);
    
    private static final String PATH_TO_RESOURCES_PROCESS = "../servicebus/ItemMasterService";

    private final static String PATH_ITEMMASTERDATA_PROXY_URI = "ItemMasterService/exposed/v1/ItemMasterService";
    private final static String PATH_CATALOG_MOCK_URI_TEMPLATE = "http://mock-host:%s/api/v1";
    private final static String PATH_CATALOG_BS_URI = "ItemMasterService/operations/getItemMasterData/business-service/CatalogApiServiceRef";
    private final static String PATH_PARCEL_MOCK_URI_TEMPLATE = "http://mock-host:%s/live";
    private final static String PATH_PARCEL_BS_URI = "ItemMasterService/operations/getItemMasterData/business-service/ParcelApiServiceRef";

    private final static String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";
    private final static String CORRELATION_ID = "11234567890";
    private final static String API_KEY = "BqMTkn87G913FA1DMbMxStThHqJupjYhzoyNf2e0";

    List<Header> headerParcelRefRequest = Lists.newArrayList(new Header("Accept", "application/json"),
	    new Header("X-Request-id", CORRELATION_ID), new Header("X-api-key", API_KEY));
    List<Header> headerCatalogApiRefRequest = Lists.newArrayList(new Header("Accept", "application/json"),
	    new Header("X-Request-id", CORRELATION_ID));

    private static ClientAndServer startClientAndServer;
    private static String itemMasterDataRequest, itemMasterDataExpectedResponse;

    @Override
    public void setUpOverridable() {

	itemMasterDataRequest = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		new ParameterReplacer(readClasspathFile(String.format("%s/getItemMasterDataServiceRequest.xml", PATH_TO_RESOURCES_PROCESS)))
			.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID).build());
    }

    @Override
    protected void tearDownOverrideable() {

	if (startClientAndServer != null) {
	    startClientAndServer.stop();
	}
    };

    @Test
    public void whenValidRequestAndCatalogApiAndParcelAPIAvailableThenReturnMasterData() throws Exception {
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");

	final String catalogAPISuccessMock1 = readClasspathFile(String.format("%s/catalog/articleSuccessResponse1.json", PATH_TO_RESOURCES_PROCESS));
	final String catalogAPISuccessMock2 = readClasspathFile(String.format("%s/catalog/articleSuccessResponse2.json", PATH_TO_RESOURCES_PROCESS));
	final String parcelAPISuccessMock = readClasspathFile(String.format("%s/parcel/parcelInfoSuccessResponse.json", PATH_TO_RESOURCES_PROCESS));

	itemMasterDataExpectedResponse = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		readClasspathFile(String.format("%s/getItemMasterDataServiceResponse.xml", PATH_TO_RESOURCES_PROCESS)));

	getOsbAccessor().setBusinessServiceHttpUri(PATH_CATALOG_BS_URI,
		String.format(PATH_CATALOG_MOCK_URI_TEMPLATE, 8082));
	startClientAndServer = ClientAndServer.startClientAndServer(8082);
	startClientAndServer
		.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD14")
			.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(200)
			.withHeader(new Header("Content-Type", "application/json"))
			.withBody(catalogAPISuccessMock1));
	startClientAndServer
		.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD12")
			.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(200)
			.withHeader(new Header("Content-Type", "application/json"))
			.withBody(catalogAPISuccessMock2));

	getOsbAccessor().setBusinessServiceHttpUri(PATH_PARCEL_BS_URI,
		String.format(PATH_PARCEL_MOCK_URI_TEMPLATE, 8081));
	startClientAndServer = ClientAndServer.startClientAndServer(8081);
	startClientAndServer
		.when(request().withMethod("GET").withPath("/live/parcels")
			.withHeaders(headerParcelRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(200)
			.withHeader(new Header("Content-Type", "application/json"))
			.withBody(parcelAPISuccessMock));

	final String invocationResult = invokeOsbProxyService(PATH_ITEMMASTERDATA_PROXY_URI,
		itemMasterDataRequest);

	assertXmlEquals(invocationResult.replaceAll("soap-env", "soapenv"), itemMasterDataExpectedResponse);
    }
    
    @Test
    public void whenCatalogApiAndParcelAPIAvailableButTransportIsEmptyThenReturnDataFault() throws Exception {
		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
	
		final String catalogAPISuccessMock1 = readClasspathFile(String.format("%s/catalog/articleSuccessResponse1.json", PATH_TO_RESOURCES_PROCESS));
		final String catalogAPISuccessMock2 = readClasspathFile(String.format("%s/catalog/articleSuccessResponse2.json", PATH_TO_RESOURCES_PROCESS));
		final String parcelAPISuccessMock = readClasspathFile(String.format("%s/parcel/parcelInfoNotMatchingAndEmptyTransportationResponse.json", PATH_TO_RESOURCES_PROCESS));
	
		itemMasterDataExpectedResponse = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			readClasspathFile(String.format("%s/getItemMasterDataServiceResponse.xml", PATH_TO_RESOURCES_PROCESS)));
	
		getOsbAccessor().setBusinessServiceHttpUri(PATH_CATALOG_BS_URI,
			String.format(PATH_CATALOG_MOCK_URI_TEMPLATE, 8094));
		startClientAndServer = ClientAndServer.startClientAndServer(8094);
		startClientAndServer
			.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD14")
				.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
			.respond(response().withStatusCode(200)
				.withHeader(new Header("Content-Type", "application/json"))
				.withBody(catalogAPISuccessMock1));
		startClientAndServer
			.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD12")
				.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
			.respond(response().withStatusCode(200)
				.withHeader(new Header("Content-Type", "application/json"))
				.withBody(catalogAPISuccessMock2));
	
		getOsbAccessor().setBusinessServiceHttpUri(PATH_PARCEL_BS_URI,
			String.format(PATH_PARCEL_MOCK_URI_TEMPLATE, 8095));
		startClientAndServer = ClientAndServer.startClientAndServer(8095);
		startClientAndServer
			.when(request().withMethod("GET").withPath("/live/parcels")
				.withHeaders(headerParcelRefRequest), Times.exactly(1))
			.respond(response().withStatusCode(200)
				.withHeader(new Header("Content-Type", "application/json"))
				.withBody(parcelAPISuccessMock));
	
		String invocationResult = "";
		
		try {
			invocationResult = invokeOsbProxyService(PATH_ITEMMASTERDATA_PROXY_URI, itemMasterDataRequest);
			fail("Service Exception is required!");
		} catch (ServiceException e) {
			invocationResult = e.getXml();
			System.out.println("+++"+invocationResult);
		    assertXpathEvaluatesTo(
			    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
			    "DataFault", invocationResult);
		    assertXpathEvaluatesTo(
				    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:faultInfo/exc:faultCode/text()",
				    "MW-41600", invocationResult);
 
		    assertXpathEvaluatesTo(
				    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:sourceSystemName/text()",
				    "Middleware", invocationResult);
		    assertXpathEvaluatesTo(
				    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:transactionId/text()",
				    CORRELATION_ID, invocationResult);
		    
		    
		    final String withoutTransportFaultUserArea =readClasspathFile(String.format("%s/withoutTransportFaultUserArea.xml", PATH_TO_RESOURCES_PROCESS));
		    assertXmlEquals(evaluateXpath("//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:faultInfo/exc:faultUserArea/withoutTransport", invocationResult), withoutTransportFaultUserArea);
		    		    
		} catch (Exception e) {
		    fail("Unexpected exception occured!");
		}
		
	
    }
    
    
    @Test
    public void whenCatalogApiAndParcelAPIAvailableButParcelResponseDoesNotMatchDomainAndLocationTransportWillBeEmptyThenReturnDataFault() throws Exception {
		declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
		declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");
	
		final String catalogAPISuccessMock1 = readClasspathFile(String.format("%s/catalog/articleSuccessResponse1.json", PATH_TO_RESOURCES_PROCESS));
		final String catalogAPISuccessMock2 = readClasspathFile(String.format("%s/catalog/articleSuccessResponse2.json", PATH_TO_RESOURCES_PROCESS));
		final String parcelAPISuccessMock = readClasspathFile(String.format("%s/parcel/parcelInfoNotMatchingLocationAndDomainResponse.json", PATH_TO_RESOURCES_PROCESS));
	
		itemMasterDataExpectedResponse = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			readClasspathFile(String.format("%s/getItemMasterDataServiceResponse.xml", PATH_TO_RESOURCES_PROCESS)));
	
		getOsbAccessor().setBusinessServiceHttpUri(PATH_CATALOG_BS_URI,
			String.format(PATH_CATALOG_MOCK_URI_TEMPLATE, 8092));
		startClientAndServer = ClientAndServer.startClientAndServer(8092);
		startClientAndServer
			.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD14")
				.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
			.respond(response().withStatusCode(200)
				.withHeader(new Header("Content-Type", "application/json"))
				.withBody(catalogAPISuccessMock1));
		startClientAndServer
			.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD12")
				.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
			.respond(response().withStatusCode(200)
				.withHeader(new Header("Content-Type", "application/json"))
				.withBody(catalogAPISuccessMock2));
	
		getOsbAccessor().setBusinessServiceHttpUri(PATH_PARCEL_BS_URI,
			String.format(PATH_PARCEL_MOCK_URI_TEMPLATE, 8093));
		startClientAndServer = ClientAndServer.startClientAndServer(8093);
		startClientAndServer
			.when(request().withMethod("GET").withPath("/live/parcels")
				.withHeaders(headerParcelRefRequest), Times.exactly(1))
			.respond(response().withStatusCode(200)
				.withHeader(new Header("Content-Type", "application/json"))
				.withBody(parcelAPISuccessMock));
	
		String invocationResult = "";
		
		try {
			invocationResult = invokeOsbProxyService(PATH_ITEMMASTERDATA_PROXY_URI, itemMasterDataRequest);
			fail("Service Exception is required!");
		} catch (ServiceException e) {
			invocationResult = e.getXml();
			System.out.println("+++" +invocationResult);
		    assertXpathEvaluatesTo(
			    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
			    "DataFault", invocationResult);
		    assertXpathEvaluatesTo(
				    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:faultInfo/exc:faultCode/text()",
				    "MW-41600", invocationResult);
 
		    assertXpathEvaluatesTo(
				    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:sourceSystemName/text()",
				    "Middleware", invocationResult);
		    assertXpathEvaluatesTo(
				    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:context/exc:transactionId/text()",
				    CORRELATION_ID, invocationResult);
		    
		    
		    final String withoutTransportFaultUserArea =readClasspathFile(String.format("%s/withoutTransportNotMatchingDomainAndStockLocationFaultUserArea.xml", PATH_TO_RESOURCES_PROCESS));
		    assertXmlEquals(evaluateXpath("//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:faultInfo/exc:faultUserArea/withoutTransport", invocationResult), withoutTransportFaultUserArea);
		    		    
		} catch (Exception e) {
		    fail("Unexpected exception occured!");
		}
		
	
    }
    

    @Test
    public void whenCatalogApiReturns404InCaseOfBadRequestThenReturnSoapFaultWithBusinessFault() {
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");

	final String parcelAPISuccessMock = readClasspathFile(String.format("%s/parcel/parcelInfoSuccessResponse.json", PATH_TO_RESOURCES_PROCESS));

	itemMasterDataExpectedResponse = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		readClasspathFile(String.format("%s/getItemMasterDataServiceCatalogError404Response.xml", PATH_TO_RESOURCES_PROCESS)));

	getOsbAccessor().setBusinessServiceHttpUri(PATH_CATALOG_BS_URI,
		String.format(PATH_CATALOG_MOCK_URI_TEMPLATE, 8083));
	startClientAndServer = ClientAndServer.startClientAndServer(8083);
	startClientAndServer
		.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD14")
			.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(404)
			.withHeader(new Header("Content-Type", "application/json")));
	startClientAndServer
		.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD12")
			.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(404)
			.withHeader(new Header("Content-Type", "application/json")));

	getOsbAccessor().setBusinessServiceHttpUri(PATH_PARCEL_BS_URI,
		String.format(PATH_PARCEL_MOCK_URI_TEMPLATE, 8084));
	startClientAndServer = ClientAndServer.startClientAndServer(8084);
	startClientAndServer
		.when(request().withMethod("GET").withPath("/live/parcels")
			.withHeaders(headerParcelRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(200)
			.withHeader(new Header("Content-Type", "application/json"))
			.withBody(parcelAPISuccessMock));

	String invokeResult = "";
	try {
	    invokeResult = invokeOsbProxyService(PATH_ITEMMASTERDATA_PROXY_URI, itemMasterDataRequest);
	    fail("Service Exception is required!");
	} catch (ServiceException e) {
	    invokeResult = e.getXml();
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
		    "BusinessFault", invokeResult);
	} catch (Exception e) {
	    fail("Unexpected exception occured!");
	}
    }

    @Test
    public void whenCatalogApiReturns500InCaseOfInternalErrorsThenReturnSoapFaultWithTechnicalFault() {
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");

	final String parcelAPISuccessMock = readClasspathFile(String.format("%s/parcel/parcelInfoSuccessResponse.json", PATH_TO_RESOURCES_PROCESS));

	itemMasterDataExpectedResponse = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		readClasspathFile(String.format("%s/getItemMasterDataServiceCatalogError500Response.xml", PATH_TO_RESOURCES_PROCESS)));

	getOsbAccessor().setBusinessServiceHttpUri(PATH_CATALOG_BS_URI,
		String.format(PATH_CATALOG_MOCK_URI_TEMPLATE, 8085));
	startClientAndServer = ClientAndServer.startClientAndServer(8085);
	startClientAndServer
		.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD14")
			.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(500)
			.withHeader(new Header("Content-Type", "application/json")));
	startClientAndServer
		.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD12")
			.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(500)
			.withHeader(new Header("Content-Type", "application/json")));

	getOsbAccessor().setBusinessServiceHttpUri(PATH_PARCEL_BS_URI,
		String.format(PATH_PARCEL_MOCK_URI_TEMPLATE, 8086));
	startClientAndServer = ClientAndServer.startClientAndServer(8086);
	startClientAndServer
		.when(request().withMethod("GET").withPath("/live/parcels")
			.withHeaders(headerParcelRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(200)
			.withHeader(new Header("Content-Type", "application/json"))
			.withBody(parcelAPISuccessMock));

	String invokeResult = "";
	try {
	    invokeResult = invokeOsbProxyService(PATH_ITEMMASTERDATA_PROXY_URI, itemMasterDataRequest);
	    fail("Service Exception is required!");
	} catch (ServiceException e) {
	    invokeResult = e.getXml();
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
		    "TechnicalFault", invokeResult);
	} catch (Exception e) {
	    fail("Unexpected exception occured!");
	}
    }

    @Test
    public void whenParcelApiReturns404InCaseOfBadRequestThenReturnSoapFaultWithBusinessFault() {
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");

	final String catalogAPISuccessMock1 = readClasspathFile(String.format("%s/catalog/articleSuccessResponse1.json", PATH_TO_RESOURCES_PROCESS));
	final String catalogAPISuccessMock2 = readClasspathFile(String.format("%s/catalog/articleSuccessResponse2.json", PATH_TO_RESOURCES_PROCESS));

	itemMasterDataExpectedResponse = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		readClasspathFile(String.format("%s/getItemMasterDataServiceParcelError404Response.xml", PATH_TO_RESOURCES_PROCESS)));

	getOsbAccessor().setBusinessServiceHttpUri(PATH_CATALOG_BS_URI,
		String.format(PATH_CATALOG_MOCK_URI_TEMPLATE, 8087));
	startClientAndServer = ClientAndServer.startClientAndServer(8087);
	startClientAndServer
		.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD14")
			.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(200)
			.withHeader(new Header("Content-Type", "application/json"))
			.withBody(catalogAPISuccessMock1));
	startClientAndServer
		.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD12")
			.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(200)
			.withHeader(new Header("Content-Type", "application/json"))
			.withBody(catalogAPISuccessMock2));

	getOsbAccessor().setBusinessServiceHttpUri(PATH_PARCEL_BS_URI,
		String.format(PATH_PARCEL_MOCK_URI_TEMPLATE, 8091));
	startClientAndServer = ClientAndServer.startClientAndServer(8091);
	startClientAndServer
		.when(request().withMethod("GET").withPath("/live/parcels")
			.withHeaders(headerParcelRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(404)
			.withHeader(new Header("Content-Type", "application/json")));

	String invokeResult = "";
	try {
	    invokeResult = invokeOsbProxyService(PATH_ITEMMASTERDATA_PROXY_URI, itemMasterDataRequest);
	    fail("Service Exception is required!");
	} catch (ServiceException e) {
	    invokeResult = e.getXml();
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
		    "TechnicalFault", invokeResult);
	} catch (Exception e) {
	    fail("Unexpected exception occured!");
	}
    }

    @Test
    public void whenInvalidRequestForCatalogApiIsSentThenReturnSoapFaultWithBusinessFault() {
	declareXpathNS("soapenv", "http://schemas.xmlsoap.org/soap/envelope/");
	declareXpathNS("exc", "http://home24.de/data/common/exceptiontypes/v1");

	final String catalogAPISuccessMock1 = readClasspathFile(String.format("%s/catalog/articleSuccessResponse1.json", PATH_TO_RESOURCES_PROCESS));
	final String catalogAPISuccessMock2 = readClasspathFile(String.format("%s/catalog/articleSuccessResponse2.json", PATH_TO_RESOURCES_PROCESS));
	final String parcelAPISuccessMock = readClasspathFile(String.format("%s/parcel/parcelInfoSuccessResponse.json", PATH_TO_RESOURCES_PROCESS));

	itemMasterDataRequest = SoapUtil.getInstance()
		.soapEnvelope(SoapVersion.SOAP11,
			new ParameterReplacer(
				readClasspathFile(String.format("%s/getItemMasterDataServiceParcelErrorRequest.xml", PATH_TO_RESOURCES_PROCESS)))
					.replace(REPLACE_PARAM_CORRELATION_ID, CORRELATION_ID).build());
	itemMasterDataExpectedResponse = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		readClasspathFile(String.format("%s/getItemMasterDataServiceParcelErrorResponse.xml", PATH_TO_RESOURCES_PROCESS)));

	getOsbAccessor().setBusinessServiceHttpUri(PATH_CATALOG_BS_URI,
		String.format(PATH_CATALOG_MOCK_URI_TEMPLATE, 8089));
	startClientAndServer = ClientAndServer.startClientAndServer(8089);
	startClientAndServer
		.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD14")
			.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(200)
			.withHeader(new Header("Content-Type", "application/json"))
			.withBody(catalogAPISuccessMock1));
	startClientAndServer
		.when(request().withMethod("GET").withPath("/api/v1/articles/M-SMOOD12")
			.withHeaders(headerCatalogApiRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(404)
			.withHeader(new Header("Content-Type", "application/json"))
			.withBody(catalogAPISuccessMock2));

	getOsbAccessor().setBusinessServiceHttpUri(PATH_PARCEL_BS_URI,
		String.format(PATH_PARCEL_MOCK_URI_TEMPLATE, 8090));
	startClientAndServer = ClientAndServer.startClientAndServer(8090);
	startClientAndServer
		.when(request().withMethod("GET").withPath("/live/parcels")
			.withHeaders(headerParcelRefRequest), Times.exactly(1))
		.respond(response().withStatusCode(200)
			.withHeader(new Header("Content-Type", "application/json"))
			.withBody(parcelAPISuccessMock));

	String invokeResult = "";
	try {
	    invokeResult = invokeOsbProxyService(PATH_ITEMMASTERDATA_PROXY_URI, itemMasterDataRequest);
	    fail("Service Exception is required!");
	} catch (ServiceException e) {
	    invokeResult = e.getXml();
	    assertXpathEvaluatesTo(
		    "//soapenv:Envelope/soapenv:Body/soapenv:Fault/detail/exc:exception/exc:category/text()",
		    "BusinessFault", invokeResult);
	} catch (Exception e) {
	    fail("Unexpected exception occured!");
	}
    }
}
