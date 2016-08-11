package de.home24.middleware.octestframework.mock;

import com.google.common.base.Strings;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

public class MockResponsePojo {
    /**
     * @author eno.ahmedspahic SOAP_RESPONSE response BUSINESS_FAULT trigger
     *         business fault FAULT trigger other faults
     *
     */
    public static enum ResponseType {
	SOAP_RESPONSE, BUSINESS_FAULT, FAULT
    }

    private ResponseType responseType;
    private String responseBody;
    private String faultString;

    /**
     * Regarding type, response is prepared appropriately. ResponseType from
     * this static class Response body is used for response but also fro fault
     * preparation. Fault string is used for business faults. Default value is
     * "fault".
     * 
     * @param responseType
     * @param responseBody
     * @param faultString
     *            default is "fault"
     */
    public MockResponsePojo(ResponseType responseType, String responseBody, String faultString) {

	this.responseType = responseType;
	this.responseBody = responseBody;
	if (Strings.isNullOrEmpty(faultString)) {
	    faultString = "fault";
	}
    }

    public MockResponsePojo(ResponseType responseType, String responseBody) {

	this(responseType, responseBody, null);
    }

    public String getResponse() {
	switch (responseType) {
	case SOAP_RESPONSE:
	    return SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		    Strings.nullToEmpty(this.responseBody));

	case BUSINESS_FAULT:
	    return SoapUtil.getInstance().soapFault(SoapVersion.SOAP11, this.faultString, responseBody);

	case FAULT:
	    throw new javax.xml.ws.WebServiceException(this.faultString + " - " + this.responseBody);

	default:
	    return SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
		    Strings.nullToEmpty(this.responseBody));
	}
    }

    public ResponseType getResponseType() {
	return responseType;
    }

    public String getResponseBody() {
	return responseBody;
    }

}
