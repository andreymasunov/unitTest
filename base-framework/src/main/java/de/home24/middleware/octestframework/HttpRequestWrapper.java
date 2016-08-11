package de.home24.middleware.octestframework;

import org.apache.http.HttpEntity;

/**
 * Wraps the information needed for creating a HTTP request.
 * 
 * @author svb
 *
 */
class HttpRequestWrapper {

    private String httpContentType;
    private String httpAcceptHeader;
    private HttpEntity messageBody;

    HttpRequestWrapper(String pHttpContentType, String pHttpAcceptHeader, HttpEntity pMessageBody) {
	httpContentType = pHttpContentType;
	httpAcceptHeader = pHttpAcceptHeader;
	messageBody = pMessageBody;
    }

    String getHttpContentType() {
	return httpContentType;
    }

    String getHttpAcceptHeader() {
	return httpAcceptHeader;
    }

    HttpEntity getMessageBody() {
	return messageBody;
    }
}
