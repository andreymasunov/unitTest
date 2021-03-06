package de.home24.middleware.cancellationpublisher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.io.InputStreamReader;
import java.util.logging.Logger;

import org.junit.Test;

import com.google.common.io.CharStreams;

import de.home24.middleware.cancellationpublisher.GenericFaultHandlerMock.FaultStrategy;
import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Test for {@link GenericFaultHandlerMock}
 * 
 * @author svb
 *
 */
public class GenericFaultHandlerMockTest extends AbstractBaseSoaTest {

    private static final Logger LOGGER = Logger.getLogger(GenericFaultHandlerMockTest.class.getName());

    private static final String REPLACE_PARAM_ACTIVITY_ID = "ACTIVITY_ID";
    private static final String REPLACE_PARAM_FAULT_STRATEGY = "FAULT_STRATEGY";
    private static final String REPLACE_PARAM_CORRELATION_ID = "CORRELATION_ID";

    private String correlationId = "123456789";
    private String activityId = "P291-GET-ORDER-STAT";

    @Test
    public void whenGenericFaultHandlerRequestForResendingIsPassedThenCorrectResponseIsReturned()
	    throws Exception {

	simulateResponseWithFaultStrategy(FaultStrategy.RESEND);
    }

    @Test
    public void whenGenericFaultHandlerRequestForAbortingIsPassedThenCorrectResponseIsReturned()
	    throws Exception {

	simulateResponseWithFaultStrategy(FaultStrategy.ABORT);
    }

    @Test
    public void whenGenericFaultHandlerIsCalledTwiceThenTheSpecifiedResponsesWithTheDefinedFaultStrategiesAreReturned()
	    throws Exception {

	simulateResponseWithFaultStrategy(FaultStrategy.RESEND, FaultStrategy.ABORT);
    }

    void simulateResponseWithFaultStrategy(final FaultStrategy... pStrategies) throws Exception {

	int executionCounter = 0;

	for (FaultStrategy faultStrategy : pStrategies) {

	    executionCounter++;

	    final String request = new ParameterReplacer(
		    CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream(String
			    .format("../processes/FrameworkServices/GenericFaultHandlerService/handleTechnicalFaultRequest.xml")))))
				    .replace(REPLACE_PARAM_CORRELATION_ID, correlationId)
				    .replace(REPLACE_PARAM_FAULT_STRATEGY, faultStrategy.getFaultStrategy())
				    .replace(REPLACE_PARAM_ACTIVITY_ID, activityId).build();

	    LOGGER.fine(String.format("####################### REQUEST: %s\n", request));

	    final GenericFaultHandlerMock genericFaultHandlerMock = new GenericFaultHandlerMock(
		    faultStrategy);

	    final String mockResponse = genericFaultHandlerMock
		    .serviceCallReceived("GenericFaultHandlerService", request);

	    LOGGER.fine(String.format("####################### MOCK RESPONSE: %s\n", mockResponse));

	    final String expectedResponse = new ParameterReplacer(
		    CharStreams.toString(new InputStreamReader(getClass().getResourceAsStream(String
			    .format("../processes/FrameworkServices/GenericFaultHandlerService/HandleFaultResponse.xml")))))
				    .replace(REPLACE_PARAM_CORRELATION_ID, correlationId)
				    .replace(REPLACE_PARAM_ACTIVITY_ID, activityId)
				    .replace(REPLACE_PARAM_FAULT_STRATEGY, faultStrategy.getFaultStrategy())
				    .build();

	    LOGGER.fine(String.format("####################### RESPONSE: %s\n", expectedResponse));

	    assertXmlEquals(mockResponse, expectedResponse);
	}

	assertThat("Execution count less than expected!", executionCounter, equalTo(pStrategies.length));
    }

}
