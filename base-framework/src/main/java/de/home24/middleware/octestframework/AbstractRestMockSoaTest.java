package de.home24.middleware.octestframework;

import org.junit.After;
import org.junit.Before;
import org.mockserver.integration.ClientAndServer;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

/**
 * Base test class for tests that invoke REST service references.
 * 
 * Uses Mock Server that can be used for mocking REST services. For more details
 * how to use MockServer, please visit http://www.mock-server.com/
 * 
 * @author svb
 *
 */
public abstract class AbstractRestMockSoaTest extends AbstractBaseSoaTest {

    public static final Integer DEFAULT_MOCK_SERVER_PORT = Integer.valueOf(8088);

    private ClientAndServer mockServer;
    private Integer mockServerPort;

    @Before
    public final void setUp() throws Exception {

	setUpOverridable();

	if (mockServerPort == null) {
	    mockServerPort = DEFAULT_MOCK_SERVER_PORT;
	}

	mockServer = startClientAndServer(mockServerPort);
    }

    @After
    public final void tearDown() {

	tearDownOverrideable();
	mockServer.stop();
    }

    protected final void setMockServerPort(int pMockServerPort) {

	mockServerPort = pMockServerPort;
    }

    /**
     * Returns an instance of {@link ClientAndServer} that can be used for
     * mocking REST-based services. Default MockServer port is 8088.
     * 
     * @return the {@link ClientAndServer} instance
     */
    protected final ClientAndServer getMockServer() {
	return mockServer;
    }

    /**
     * Executes tear down relevant procedures after executing a test.
     */
    protected void tearDownOverrideable() {
    }

    /**
     * Executes setup relevant procedures before executing a test.
     */
    protected void setUpOverridable() {
    }
}
