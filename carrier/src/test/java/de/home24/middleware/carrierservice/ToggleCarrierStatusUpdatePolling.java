package de.home24.middleware.carrierservice;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Before;
import org.junit.Test;

import java.util.logging.Logger;

import com.google.common.collect.Lists;
import com.opitzconsulting.soa.testing.ServiceException;
import com.opitzconsulting.soa.testing.util.SoapUtil;
import com.opitzconsulting.soa.testing.util.SoapUtil.SoapVersion;

import de.home24.middleware.octestframework.AbstractBaseSoaTest;
import de.home24.middleware.octestframework.mock.DefaultSoapMockService;
import de.home24.middleware.octestframework.mock.MockResponsePojo;
import de.home24.middleware.octestframework.mock.MockResponsePojo.ResponseType;
import de.home24.middleware.octestframework.util.ParameterReplacer;

/**
 * Test for operation toggleCarrierStatusUpdatePolling in CarrierService.
 * 
 * @author svb
 *
 */
public class ToggleCarrierStatusUpdatePolling extends AbstractBaseSoaTest {

    private static final String REPLACE_PARAM_IS_ACTIVE = "IS_ACTIVE";

    @Before
    public void setUp() {

	declareXpathNS("msg", "http://home24.de/interfaces/bas/carrier/carrierservicemessages/v1");
	declareXpathNS("exception", "http://home24.de/data/common/exceptiontypes/v1");
    }

    @Test
    public void activateFtpPollingByAddingTriggerFile() {

	LOGGER.info("+++ invoke activateFtpPollingByAddingTriggerFile");
	final DefaultSoapMockService writeTriggerFileMockRef = new DefaultSoapMockService();

	mockOsbBusinessService(
		"CarrierService/operations/toggleCarrierStatusUpdatePolling/business-service/MetapackCreateFileService",
		writeTriggerFileMockRef);

	final String invocationResult = invokeOsbProxyService("CarrierService/exposed/v1/CarrierService",
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			new ParameterReplacer(
				readClasspathFile("toggleCarrierStatusUpdatePollingRequest.xml"))
					.replace(REPLACE_PARAM_IS_ACTIVE, Boolean.TRUE.toString()).build()));

	assertThat("WriteTriggerFileRef has not been invoked!", writeTriggerFileMockRef.hasBeenInvoked(),
		is(true));
	assertXpathEvaluatesTo("count(//exception:exception)", String.valueOf(0), invocationResult);
	assertXpathEvaluatesTo("count(//msg:toggleCarrierStatusUpdatePollingResponse)", String.valueOf(1),
		invocationResult);
    }

    @Test
    public void deactivateFtpPollingByRemovingTriggerFile() {

	LOGGER.info("+++ invoke deactivateFtpPollingByRemovingTriggerFile");
	final DefaultSoapMockService removeTriggerFileMockRef = new DefaultSoapMockService();

	mockOsbBusinessService(
		"CarrierService/operations/toggleCarrierStatusUpdatePolling/business-service/MetapackSyncReadFileService",
		removeTriggerFileMockRef);

	final String invocationResult = invokeOsbProxyService("CarrierService/exposed/v1/CarrierService",
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			new ParameterReplacer(
				readClasspathFile("toggleCarrierStatusUpdatePollingRequest.xml"))
					.replace(REPLACE_PARAM_IS_ACTIVE, Boolean.FALSE.toString()).build()));

	assertThat("RemoveTriggerFileMockRef has not been invoked!",
		removeTriggerFileMockRef.hasBeenInvoked(), is(true));
	assertXpathEvaluatesTo("count(//exception:exception)", String.valueOf(0), invocationResult);
	assertXpathEvaluatesTo("count(//msg:toggleCarrierStatusUpdatePollingResponse)", String.valueOf(1),
		invocationResult);
    }

    @Test
    public void receiveErrorWhileDeletingTriggerFileCausedByAlreadyDeletedTriggerFile() {

	LOGGER.info("+++ invoke receiveErrorWhileDeletingTriggerFileCausedByAlreadyDeletedTriggerFile");
	final DefaultSoapMockService removeTriggerFileMockRef = new DefaultSoapMockService(Lists
		.newArrayList(new MockResponsePojo(ResponseType.BUSINESS_FAULT, "JCA-11007", "JCA-11007")));

	mockOsbBusinessService(
		"CarrierService/operations/toggleCarrierStatusUpdatePolling/business-service/MetapackSyncReadFileService",
		removeTriggerFileMockRef);

	final String invocationResult = invokeOsbProxyService("CarrierService/exposed/v1/CarrierService",
		SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			new ParameterReplacer(
				readClasspathFile("toggleCarrierStatusUpdatePollingRequest.xml"))
					.replace(REPLACE_PARAM_IS_ACTIVE, Boolean.FALSE.toString()).build()));

	assertThat("RemoveTriggerFileMockRef has not been invoked!",
		removeTriggerFileMockRef.hasBeenInvoked(), is(true));
	assertXpathEvaluatesTo("count(//exception:exception)", String.valueOf(0), invocationResult);
	assertXpathEvaluatesTo("count(//msg:toggleCarrierStatusUpdatePollingResponse)", String.valueOf(1),
		invocationResult);
    }

    @Test
    public void receiveErrorWhileDeletingTriggerFileCausedByFtpUnavailability() {

	LOGGER.info("+++ invoke receiveErrorWhileDeletingTriggerFileCausedByFtpUnavailability");
	String invocationResult = null;

	final DefaultSoapMockService writeTriggerFileMockRef = new DefaultSoapMockService(
		Lists.newArrayList(new MockResponsePojo(ResponseType.FAULT,
			"Remote fault: FTP not available!", "Remote fault: FTP not available!")));
	mockOsbBusinessService(
		"CarrierService/operations/toggleCarrierStatusUpdatePolling/business-service/MetapackCreateFileService",
		writeTriggerFileMockRef);

	try {
	    invocationResult = invokeOsbProxyService("CarrierService/exposed/v1/CarrierService",
		    SoapUtil.getInstance().soapEnvelope(SoapVersion.SOAP11,
			    new ParameterReplacer(
				    readClasspathFile("toggleCarrierStatusUpdatePollingRequest.xml"))
					    .replace(REPLACE_PARAM_IS_ACTIVE, Boolean.TRUE.toString())
					    .build()));
	} catch (ServiceException e) {

	    invocationResult = e.getXml();

	    assertThat("WriteTriggerFileRef has not been invoked!", writeTriggerFileMockRef.hasBeenInvoked(),
		    is(true));
	    assertXpathEvaluatesTo("count(//exception:exception)", String.valueOf(1), invocationResult);
	    assertThat("Category is not TechnicalFault!", invocationResult, containsString("TechnicalFault"));
	}
    }
}
