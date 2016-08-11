package de.home24.middleware.octestframework.mock;

import java.util.List;

import com.opitzconsulting.soa.testing.MockService;
import com.opitzconsulting.soa.testing.ServiceException;

import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;

/**
 * A default {@link MockService} implementation that provides the last received
 * request and a invocation counter.
 * 
 * @author svb
 * @author eno.ahmedspahic configurable list of requests and responses.
 * @authot adb extracted abstract superclass
 *
 */
public class DefaultSoapMockService extends AbstractSoapMockService {

    /**
     * Used only to mock service with same response every time
     * 
     * @param pPlainResponseBody
     */
    public DefaultSoapMockService(String pPlainResponseBody) {
	addMockedResponse(new MockResponsePojo(ResponseType.SOAP_RESPONSE, pPlainResponseBody));
    }

    /**
     * ArrayList of responses.
     * 
     * @param mockedResponses
     *            List of mock responses (can be null if exception is only
     *            response)
     */
    public DefaultSoapMockService(List<MockResponsePojo> pMockedResponses) {
	setMockedResponses(pMockedResponses);
    }

    /**
     * Default constructor
     */
    public DefaultSoapMockService() {

	this("");
    }

    @Override
    public String serviceCallReceived(String serviceName, String requestStr)
	    throws ServiceException, Exception {

	getReceivedRequests().add(requestStr);

	increaseInvocationCounter();

	MockResponsePojo response = null;

	if (getMockedResponses().size() == 1) {
	    response = getMockedResponses().get(0);
	} else {
	    response = getMockedResponses().get(getNumberOfInvocations() - 1);
	}

	return response.getResponse();
    }

}
