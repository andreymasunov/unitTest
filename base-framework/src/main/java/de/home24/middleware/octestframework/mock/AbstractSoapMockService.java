package de.home24.middleware.octestframework.mock;

import java.util.ArrayList;
import java.util.List;

import com.opitzconsulting.soa.testing.MockService;

public abstract class AbstractSoapMockService implements MockService {

	private int invocationCounter;
	private List<String> receivedRequests;
	private List<MockResponsePojo> mockedResponses;

	/**
	 * ArrayList of responses.
	 * 
	 * @param mockedResponses
	 *            List of mock responses (can be null if exception is only
	 *            response)
	 */
	public AbstractSoapMockService() {

		invocationCounter = 0;
		receivedRequests = new ArrayList<String>();
		mockedResponses = new ArrayList<MockResponsePojo>();
	}

	/**
	 * Returns, if this instance of {@link MockService} has been invoked during
	 * test execution.
	 * 
	 * @return true if {@link MockService} has been invoked; otherwise false.
	 */
	public boolean hasBeenInvoked() {
		return invocationCounter > 0;
	}

	/**
	 * Returns the number of invocations.
	 * 
	 * @return number of invocation
	 */
	public int getNumberOfInvocations() {
		return invocationCounter;
	}

	/**
	 * Return the last received request.
	 * 
	 * @return the last received request
	 */
	public String getLastReceivedRequest() {

		return receivedRequests.get(invocationCounter - 1);
	}

	public List<String> getReceivedRequests() {
		return receivedRequests;
	}

	public List<MockResponsePojo> getMockedResponses() {
		return mockedResponses;
	}

	protected void setMockedResponses(List<MockResponsePojo> mockedResponses) {
		this.mockedResponses = mockedResponses;
	}
	
	protected void increaseInvocationCounter()
	{
		invocationCounter++;
	}


	protected void addMockedResponse(MockResponsePojo mockedResponse) {
		this.mockedResponses.add(mockedResponse);
	}

}
