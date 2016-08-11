package de.home24.middleware.octestframework.ht;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import oracle.bpel.services.workflow.WorkflowException;
import oracle.bpel.services.workflow.client.IWorkflowServiceClient;
import oracle.bpel.services.workflow.client.IWorkflowServiceClientConstants;
import oracle.bpel.services.workflow.client.WorkflowServiceClientFactory;

/**
 * Class that creates a connection to the Oracle human task engine.
 * 
 * @author svb
 *
 */
@Component
@PropertySources({ @PropertySource("classpath:de/home24/middleware/config/soaconfig.local.properties") })
class HumanTaskConnectionFactory {

    private IWorkflowServiceClient workflowServiceClient;

    @Autowired
    private Environment environment;

    /**
     * Returns connection to human task engine.
     * 
     * @return an instance of {@link IWorkflowServiceClient}
     */
    IWorkflowServiceClient getConnection() {

	try {
	    if (workflowServiceClient == null) {

		createWorkflowServiceClient();
	    }
	} catch (Exception e) {

	    throw new RuntimeException(e);
	}

	return workflowServiceClient;
    }

    private void createWorkflowServiceClient() {
	try {
	    Map<IWorkflowServiceClientConstants.CONNECTION_PROPERTY, String> connProperties = new HashMap<IWorkflowServiceClientConstants.CONNECTION_PROPERTY, String>();
	    connProperties.put(IWorkflowServiceClientConstants.CONNECTION_PROPERTY.CLIENT_TYPE,
		    WorkflowServiceClientFactory.REMOTE_CLIENT);
	    connProperties.put(IWorkflowServiceClientConstants.CONNECTION_PROPERTY.EJB_PROVIDER_URL,
		    String.format("t3://%s:%s", environment.getProperty("soa.soaHost"),
			    environment.getProperty("soa.soaPort")));
	    connProperties.put(
		    IWorkflowServiceClientConstants.CONNECTION_PROPERTY.EJB_INITIAL_CONTEXT_FACTORY,
		    "weblogic.jndi.WLInitialContextFactory");

	    workflowServiceClient = WorkflowServiceClientFactory.getWorkflowServiceClient(connProperties,
		    null, null);

	} catch (WorkflowException e) {

	    throw new RuntimeException(e);
	}
    }
}
