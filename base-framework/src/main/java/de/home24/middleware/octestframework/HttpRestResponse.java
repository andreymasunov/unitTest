package de.home24.middleware.octestframework;

import org.apache.http.client.methods.CloseableHttpResponse;

/**
 * Wrapper for information provided in the HTTP response coming from RESTful
 * invocations.
 * 
 * @deprecated use {@link HttpResponseWrapper} instead
 * 
 * @author svb
 * 
 */
@Deprecated
public class HttpRestResponse {

    private int statusCode;
    private long processingDuration;

    public HttpRestResponse(CloseableHttpResponse pHttpResponse, long pProcessingDuration) {

	statusCode = pHttpResponse.getStatusLine().getStatusCode();
	processingDuration = pProcessingDuration;
    }

    /**
     * The duration for the HTTP request-response cycle.
     * 
     * @return the processing duration in milliseconds.
     */
    public long getProcessingDuration() {
	return processingDuration;
    }

    /**
     * Returns the status code of the HTTP response.
     * 
     * @return the HTTP status code
     */
    public int getStatusCode() {
	return statusCode;
    }
}
