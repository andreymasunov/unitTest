package de.home24.middleware.octestframework;

import static org.hamcrest.MatcherAssert.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.context.transaction.TransactionalTestExecutionListener;

import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.opitzconsulting.soa.testing.AbstractSoaTest;
import com.opitzconsulting.soa.testing.SoaConfig;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.components.MessagingDao;
import de.home24.middleware.octestframework.components.OtmDao;
import de.home24.middleware.octestframework.ht.HumanTaskDao;
import de.home24.middleware.octestframework.mock.AbstractSoapMockService;

/**
 * Base test to be used for all test classes. Extends {@link AbstractSoaTest}
 * and provides additional functionalities.
 * 
 * @author svb
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class,
	TransactionalTestExecutionListener.class })
@ContextConfiguration(locations = { "classpath:application-context.xml" })
@PropertySources({ @PropertySource("classpath:de/home24/middleware/config/soaconfig.local.properties") })
public abstract class AbstractBaseSoaTest extends AbstractSoaTest {

    private static final String MDS_BASE_URL_ENV_VAR = "mds.base.url";
    private static final String ENV_VARIABLE_MDS_HOME = "MDS_HOME";
    private static final String PROPERTIES_FILE_REFERENCE = "de/home24/middleware/config/soaconfig.local.properties";

    private static Properties CONFIG_PROPERTIES = new Properties();

    private HumanTaskDao humanTaskDao;
    private OtmDao otmDao;
    private MessagingDao messagingDao;

    @Autowired
    protected Environment environment;

    public AbstractBaseSoaTest() {

	this(null);
    }

    public AbstractBaseSoaTest(String pPartition) {
	super(setUpSoaConfiguration(pPartition));
    }

    /**
     * Initialize relevant test resources.
     */
    @BeforeClass
    public static void testInitialization() {

	System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
		"com.sun.org.apache.xerces.internal.jaxp.DocumentBuilderFactoryImpl");
	System.setProperty("javax.xml.parsers.SAXParserFactory",
		"com.sun.org.apache.xerces.internal.jaxp.SAXParserFactoryImpl");
	System.setProperty("javax.xml.transform.TransformerFactory",
		"com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl");

	// System.setProperty(MDS_BASE_URL_ENV_VAR,
	// String.format("file://%s", System.getenv(ENV_VARIABLE_MDS_HOME)));

	File mdsBasePath = new File(System.getenv(ENV_VARIABLE_MDS_HOME));
	try {
	    String mdsBasePathURL = mdsBasePath.toURI().toURL().toString();
	    System.setProperty(MDS_BASE_URL_ENV_VAR, mdsBasePathURL);
	} catch (MalformedURLException e) {
	    String errorMessage = String.format(
		    "Couldn't create system property '%s' out of environment variable '%s'.",
		    MDS_BASE_URL_ENV_VAR, ENV_VARIABLE_MDS_HOME);
	    throw new RuntimeException(errorMessage, e);
	}

    }

    /**
     * Initialization of SOA configuration for the test.
     * 
     * @param pPartition
     *            the SOA Partition, where composites to test are deployed.
     */
    private static SoaConfig setUpSoaConfiguration(String pPartition) {

	SoaConfig soaConfig = null;

	try {
	    CONFIG_PROPERTIES.load(AbstractBaseSoaTest.class.getClassLoader()
		    .getResourceAsStream(PROPERTIES_FILE_REFERENCE));

	    if (!Strings.isNullOrEmpty(pPartition)) {
		CONFIG_PROPERTIES.setProperty("soa.soaPartition", pPartition);
	    }

	    soaConfig = SoaConfig.readConfigFromProps(CONFIG_PROPERTIES);

	} catch (Exception e) {

	    throw new RuntimeException(String.format("Exception while loading properties from location %s",
		    PROPERTIES_FILE_REFERENCE), e);
	}

	return soaConfig;
    }

    /**
     * Invoke a OSB REST proxy service. Supports only POST invocations at the
     * moment.
     * 
     * @deprecated use invokeSbRestProxy() instead.
     * 
     * @param pServiceUrl
     *            REST-URL to be invoked
     * @param jsonRequest
     *            the JSON Request to be sent in the body
     */
    @Deprecated
    protected HttpRestResponse invokeOsbRestProxy(String pServiceUrl, String jsonRequest) {

	getOsbAccessor().flushUriChanges();

	CloseableHttpResponse httpResponse = null;
	HttpRestResponse httpRestResponse = null;

	try (CloseableHttpClient httpClient = HttpClientBuilder.create().build()) {
	    HttpPost request = new HttpPost(pServiceUrl);
	    request.addHeader("Content-Type", "application/json; charset=utf-8");
	    request.addHeader("Accept", "application/json");
	    request.setEntity(new StringEntity(jsonRequest));

	    final Stopwatch stopwatch = Stopwatch.createStarted();
	    httpResponse = httpClient.execute(request);
	    stopwatch.stop();

	    httpRestResponse = new HttpRestResponse(httpResponse, stopwatch.elapsed(TimeUnit.MILLISECONDS));

	} catch (Exception e) {

	    throw new RuntimeException(e);
	} finally {

	    if (httpResponse != null) {

		try {
		    httpResponse.close();
		} catch (Exception e) {
		    throw new RuntimeException(e);
		}
	    }
	}

	return httpRestResponse;
    }

    /**
     * Invoke a SB REST proxy service. Supports only POST invocations at the
     * moment.
     * 
     * @param pServiceUrl
     *            REST-URL to be invoked
     * @param jsonRequest
     *            the JSON Request to be sent in the body
     */
    protected HttpResponseWrapper invokeSbRestProxy(String pServiceUrl, String jsonRequest) throws Exception {

	getOsbAccessor().flushUriChanges();

	return invokeHttpEndpoint(pServiceUrl, new HttpRequestWrapper("application/json; charset=utf-8",
		"application/json", new StringEntity(jsonRequest)));
    }

    /**
     * Invoke a SB SOAP proxy service.
     * 
     * @param pServiceUrl
     *            Service URL to be invoked
     * @param pSoapRequest
     *            the SOAP Request to be sent in the body
     * @param pIsSoapEnvelopContained
     *            defines if SOAP envelope is already contained
     */
    protected HttpResponseWrapper invokeSbSoapProxy(String pServiceUrl, String pSoapRequest,
	    boolean pIsSoapEnvelopContained) {

	String requestMessage = pSoapRequest;

	getOsbAccessor().flushUriChanges();

	if (!pIsSoapEnvelopContained) {
	    requestMessage = SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, pSoapRequest);
	}

	return invokeHttpEndpoint(pServiceUrl, new HttpRequestWrapper("text/xml; charset=utf-8", "text/xml",
		new StringEntity(requestMessage, ContentType.TEXT_XML.withCharset(StandardCharsets.UTF_8))));
    }

    /**
     * Return the name of the mock host, specified by the property "mock.host"
     * within the {@link SoaConfig}. Waits until the specified service has been
     * invoked. The maximum wait time is passed by the MaxWaitTime parameter.
     * 
     * @return name of the mock host
     */
    protected final String getMockHost() {

	return CONFIG_PROPERTIES.getProperty("mock.host");
    }

    /**
     * Returns an instance of {@link HumanTaskDao} that allows access to Oracle
     * HT engine.
     * 
     * @return the {@link HumanTaskDao} instance
     */
    protected HumanTaskDao getHumanTaskDao() {

	return humanTaskDao;
    }

    /**
     * Returns an instance of {@link OtmDao} that allows queries against the
     * OrderTransaction database.
     * 
     * @return the {@link OtmDao} instance
     */
    protected OtmDao getOtmDao() {

	return otmDao;
    }

    /**
     * Returns an instance of {@link MessagingDao} that should be used for
     * communications with WLS JMS infrastructure.
     * 
     * @return the {@link MessagingDao} instance
     */
    protected MessagingDao getMessagingDao() {
	return messagingDao;
    }

    /**
     * Waits until the specified service has been invoked. The maximum default
     * wait time are 15 seconds.
     * 
     * @param pDefaultSoapMockService
     *            the {@link AbstractSoapMockService} implementation that should
     *            be waited for.
     */
    protected final void waitForInvocationOf(AbstractSoapMockService pAbstractSoapMockService) {

	waitForInvocationOf(pAbstractSoapMockService, 15);
    }

    /**
     * Waits until the service has been invoked the specified number of times.
     * The maximum wait time is passed by the MaxWaitTime parameter.
     * 
     * @param pAbstractSoapMockService
     *            the {@link AbstractSoapMockService} implementation that should
     *            be waited for.
     * @param pNumberOfInvocations
     *            number of invocations to wait for
     * @param pMaxWaitTime
     *            maximum wait time in seconds
     */
    protected final void waitForInvocationOf(AbstractSoapMockService pAbstractSoapMockService,
	    int pNumberOfInvocations, int pMaxWaitTime) {

	try {
	    int counter = 0;
	    while (pAbstractSoapMockService.getNumberOfInvocations() < pNumberOfInvocations
		    && counter++ < pMaxWaitTime) {
		Thread.sleep(1000);
	    }

	    Thread.sleep((1000 * pMaxWaitTime) / pNumberOfInvocations);
	    assertThat("The service was not invoked the expected number of times",
		    pAbstractSoapMockService.getNumberOfInvocations() == pNumberOfInvocations);

	} catch (Exception e) {

	    Thread.currentThread().interrupt();
	    throw new RuntimeException(e);
	}
    }

    /**
     * Waits until the specified service has been invoked. The maximum wait time
     * is passed by the MaxWaitTime parameter.
     * 
     * @param pAbstractSoapMockService
     *            the {@link AbstractSoapMockService} implementation that should
     *            be waited for.
     * @param pMaxWaitTime
     *            maximum wait time in seconds
     */
    protected final void waitForInvocationOf(AbstractSoapMockService pAbstractSoapMockService,
	    int pMaxWaitTime) {

	try {
	    int counter = 0;
	    int numberOfInvocations = 0;
	    while ((pAbstractSoapMockService.getNumberOfInvocations() > numberOfInvocations
		    || !pAbstractSoapMockService.hasBeenInvoked()) && counter++ < pMaxWaitTime) {
		numberOfInvocations = pAbstractSoapMockService.getNumberOfInvocations();
		Thread.sleep(1000);
	    }
	} catch (Exception e) {

	    Thread.currentThread().interrupt();
	    throw new RuntimeException(e);
	}
    }

    /**
     * Setter method for {@link HumanTaskDao} used by Spring method injection.
     * 
     * @param pOtmDao
     *            the concrete {@link HumanTaskDao} instance
     */
    @Autowired
    protected void setHumanTaskDao(HumanTaskDao pHumanTaskDao) {
	humanTaskDao = pHumanTaskDao;
    }

    /**
     * Setter method for {@link OtmDao} used by Spring method injection.
     * 
     * @param pOtmDao
     *            the concrete {@link OtmDao} instance
     */
    @Autowired
    protected void setOtmDao(OtmDao pOtmDao) {
	otmDao = pOtmDao;
    }

    /**
     * Setter method for {@link MessagingDao} used by Spring method injection.
     * 
     * @param pMessagingDao
     *            the concrete {@link MessagingDao} instance
     */
    @Autowired
    protected void setMessagingDao(MessagingDao pMessagingDao) {
	messagingDao = pMessagingDao;
    }

    /**
     * Sends a message to a specific endpoint Can be used to mock a callback
     * 
     * @param endpoint
     * @param msg
     */
    protected final void createdRequestToWaitingInstance(String endpoint, String msg) {

	invokeHttpEndpoint(endpoint,
		new HttpRequestWrapper("text/xml; charset=utf-8", "text/xml",
			new StringEntity(SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, msg),
				ContentType.TEXT_XML.withCharset(StandardCharsets.UTF_8))));
    }

    /**
     * Invokes a regular HTTP endpoint. It enriches the message with the given
     * parameters in {@link HttpRequestWrapper}.
     * 
     * @param endpoint
     *            the endpoint to call
     * @param pHttpRequestWrapper
     *            the request information to send
     * @return
     */
    HttpResponseWrapper invokeHttpEndpoint(String endpoint, HttpRequestWrapper pHttpRequestWrapper) {

	CloseableHttpResponse httpResponse = null;
	HttpResponseWrapper httpResponseWrapper = null;

	try (CloseableHttpClient httpClient = HttpClientBuilder.create().build();) {
	    final HttpPost request = new HttpPost(endpoint);
	    request.addHeader("Content-Type", pHttpRequestWrapper.getHttpContentType());
	    request.addHeader("Accept", pHttpRequestWrapper.getHttpAcceptHeader());
	    request.setEntity(pHttpRequestWrapper.getMessageBody());

	    final Stopwatch stopwatch = Stopwatch.createStarted();
	    httpResponse = httpClient.execute(request);
	    stopwatch.stop();

	    httpResponseWrapper = new HttpResponseWrapper(httpResponse,
		    stopwatch.elapsed(TimeUnit.MILLISECONDS));
	} catch (final Exception e) {

	    throw new RuntimeException(e);
	} finally {

	    try {
		if (httpResponse != null) {
		    httpResponse.close();
		}
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}

	return httpResponseWrapper;
    }

}
