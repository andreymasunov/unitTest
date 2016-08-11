package de.home24.middleware.octestframework;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.apache.http.client.methods.CloseableHttpResponse;

import com.google.common.io.CharStreams;

/**
 * Wrapper for information provided in the HTTP response coming from RESTful
 * invocations.
 * 
 * @author svb
 *
 */
public class HttpResponseWrapper {

    private int statusCode;
    private Long processingDuration;
    private String httpResponse;

    public HttpResponseWrapper(CloseableHttpResponse pHttpResponse, long pProcessingDuration) {

	statusCode = pHttpResponse.getStatusLine().getStatusCode();
	try {
	    httpResponse = CharStreams.toString(
		    new InputStreamReader(pHttpResponse.getEntity().getContent(), StandardCharsets.UTF_8));
	} catch (Exception e) {
	    throw new RuntimeException(e);
	}
	processingDuration = pProcessingDuration;
    }

    /**
     * The duration for the HTTP request-response cycle.
     * 
     * @return the processing duration in milliseconds.
     */
    public Long getProcessingDuration() {
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

    /**
     * Returns the content of the HTTP response.
     * 
     * @return the HTTP content as string
     */
    public String getHttpResponse() {
	return httpResponse;
    }
}
