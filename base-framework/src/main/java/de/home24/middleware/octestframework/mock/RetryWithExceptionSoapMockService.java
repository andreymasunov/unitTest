package de.home24.middleware.octestframework.mock;

import com.opitzconsulting.soa.testing.ServiceException;

import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;

public class RetryWithExceptionSoapMockService extends AbstractSoapMockService {

	private int numberOfRetries = 0;
	
	// defines whether for the given number of retries just an exception is to be returned 
	private boolean flgReturnExceptionsOnly = false;
	
	public RetryWithExceptionSoapMockService(int numberOfRetries) {
		this.numberOfRetries = numberOfRetries;
		this.flgReturnExceptionsOnly = true;
		
		// add empty mock object
		addMockedResponse(new MockResponsePojo(ResponseType.SOAP_RESPONSE, ""));
	}
	
	public RetryWithExceptionSoapMockService(int numberOfRetries, MockResponsePojo mockedErrorResponse) {
		
		addMockedResponse(mockedErrorResponse);
		
		this.numberOfRetries = numberOfRetries;
		this.flgReturnExceptionsOnly = true;
	}
	
	public RetryWithExceptionSoapMockService(int numberOfRetries, MockResponsePojo mockedErrorResponse, MockResponsePojo mockedLastResponse) {
		
		addMockedResponse(mockedErrorResponse);
		addMockedResponse(mockedLastResponse);
		
		this.numberOfRetries = numberOfRetries;
		this.flgReturnExceptionsOnly = false;
	}
	
	@Override
	public String serviceCallReceived(String serviceName, String requestStr) throws ServiceException, Exception {
		
		increaseInvocationCounter();
		getReceivedRequests().add(requestStr);
		
		// for all retries and in case we should generally throw exceptions we get the error reponse and throw an exception
		if (getNumberOfInvocations() <= numberOfRetries || flgReturnExceptionsOnly)
		{
			throw new ServiceException(serviceName, getMockedResponses().get(0).getResponse());
		}
		
		// get response for last call
		return getMockedResponses().get(1).getResponse();
	}

}
