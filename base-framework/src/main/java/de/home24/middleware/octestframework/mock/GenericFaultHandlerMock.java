package de.home24.middleware.octestframework.mock;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;

import com.google.common.collect.Lists;
import com.google.common.io.CharStreams;
import com.opitzconsulting.soa.testing.MockService;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.util.ParameterReplacer;
import de.home24.middleware.octestframework.util.XpathProcessor;

/**
 * Specific {@link MockService} implementation to be used for mocking
 * GenericFaultHandler. Creates the mock service response out of the information
 * from the request and return the provided {@link FaultStrategy}.
 * 
 * @author svb
 *
 */
public class GenericFaultHandlerMock extends AbstractSoapMockService {

    /**
     * Enumeration describing possible fault strategy outcomes for
     * GenericFaultHandlerService
     * 
     * @author svb
     *
     */
    public enum FaultStrategy {
	RESEND("Resend"), ABORT("Abort");

	private String faultStrategy;

	private FaultStrategy(String pFaultStrategy) {
	    faultStrategy = pFaultStrategy;
	}

	public String getFaultStrategy() {
	    return faultStrategy;
	}
    }

    private static final String NAMESPACE_GFH_MESSAGES = "http://home24.de/interfaces/bas/genericfaulthandler/genericfaulthandlerservicemessages/v1";

    private List<FaultStrategy> faultStrategiesReturnValues;
    private XpathProcessor xpathProcessor;
    private String genericFaultHandlerPayloadTemplate;

    public GenericFaultHandlerMock(FaultStrategy... pFaultStrategies) {

	faultStrategiesReturnValues = Lists.newArrayList(pFaultStrategies);

	xpathProcessor = new XpathProcessor();
	xpathProcessor.declareNamespace("gfh", NAMESPACE_GFH_MESSAGES);
    }

    @Override
    public String serviceCallReceived(String pServicename, String pRequest)
	    throws ServiceException, Exception {

	final String correlationId = xpathProcessor.evaluateXPath(
		"//gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:transactionId/text()",
		pRequest);
	final String payload = xpathProcessor.evaluateXPath(
		"//gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:payload/*",
		pRequest);
	final String activityId = xpathProcessor.evaluateXPath(
		"//gfh:handleFaultRequest/gfh:faultInformation/exc:exception/exc:context/exc:activityId/text()",
		pRequest);

	int currentFaultStrategy = faultStrategiesReturnValues.size() > 1 ? getNumberOfInvocations() : 0;

	final String genericFaultHandlerResponse = new ParameterReplacer(
		getGenericFaultHandlerPayloadTemplate()).replace("PAYLOAD", payload)
			.replace("CORRELATION_ID", correlationId)
			.replace("FAULT_STRATEGY",
				faultStrategiesReturnValues.get(currentFaultStrategy).getFaultStrategy())
			.replace("PAYLOAD", payload).replace("ACTIVITY_ID", activityId)
			.replace("SOURCE_SERVICE_NAME", "").build();

	getReceivedRequests().add(pRequest);
	increaseInvocationCounter();

	return SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11, genericFaultHandlerResponse);
    }

    protected String getGenericFaultHandlerPayloadTemplate() {

	if (genericFaultHandlerPayloadTemplate == null) {
	    try {
		genericFaultHandlerPayloadTemplate = CharStreams.toString(new InputStreamReader(
			getClass().getResourceAsStream("GenericFaultHandlerResponseTemplate.xml")));
	    } catch (IOException e) {
		throw new RuntimeException(e);
	    }
	}

	return genericFaultHandlerPayloadTemplate;
    }
}
