package de.home24.middleware.octestframework.ht;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import com.google.common.collect.Lists;

import oracle.bpel.services.workflow.WorkflowException;
import oracle.bpel.services.workflow.query.ITaskQueryService;
import oracle.bpel.services.workflow.repos.Predicate;
import oracle.bpel.services.workflow.repos.TableConstants;
import oracle.bpel.services.workflow.task.impl.TaskAssignee;
import oracle.bpel.services.workflow.task.model.Task;
import oracle.bpel.services.workflow.verification.IWorkflowContext;

/**
 * DataAccessObject to Oralce HT engine.
 * 
 * @author svb
 *
 */
@Component
@PropertySources({ @PropertySource("classpath:de/home24/middleware/config/soaconfig.local.properties") })
public class HumanTaskDao {

    private final List<String> QUERY_COLUMNS = Lists.newArrayList("TASKNUMBER", "TASKID", "TITLE", "OUTCOME",
	    "STATE", "PRIORITY");

    private HumanTaskConnectionFactory connectionFactory;
    private IWorkflowContext workflowContext;

    @Autowired
    private Environment environment;

    /**
     * Finds a HT by the Ordernumber that is part of the instance title.
     * 
     * @param pOrderId
     *            the OrderId to sewarch for.
     * @return the task for the OrderId; null, if task could not be found.
     */
    @SuppressWarnings("deprecation")
    public Task findByOrderId(String pOrderId) {

	List<Task> tasks = null;

	try {
	    final Predicate titlePredicate = new Predicate(TableConstants.WFTASK_TITLE_COLUMN,
		    Predicate.OP_LIKE, String.format("%%%s%%", pOrderId));

	    tasks = connectionFactory.getConnection().getTaskQueryService().queryTasks(getWorkflowContext(),
		    QUERY_COLUMNS, null, ITaskQueryService.AssignmentFilter.ADMIN, null, titlePredicate, null,
		    0, 0);
	} catch (Exception e) {

	    throw new RuntimeException("Exception while query tasks!", e);
	}

	return tasks.size() == 0 ? null : tasks.get(0);
    }

    /**
     * Updates a tasks outcome.
     * 
     * @param pTask
     *            the {@link Task} to be updated
     * @param pOutcome
     *            the outcome for the {@link Task}
     */
    public void update(Task pTask, String pOutcome) {

	try {
	    connectionFactory.getConnection()
		    .getTaskService().reassignTask(getWorkflowContext(), pTask.getSystemAttributes()
			    .getTaskId(),
		    Lists.newArrayList(new TaskAssignee(environment.getProperty("soa.hteUser"), "user")));

	    connectionFactory.getConnection().getTaskService().updateTaskOutcome(getWorkflowContext(),
		    pTask.getSystemAttributes().getTaskId(), pOutcome);

	} catch (Exception e) {

	    throw new RuntimeException("Exception while updating a task!", e);
	}
    }

    private IWorkflowContext getWorkflowContext() {

	try {
	    if (workflowContext == null) {
		workflowContext = connectionFactory.getConnection().getTaskQueryService().authenticate(
			environment.getProperty("soa.hteUser"),
			environment.getProperty("soa.htePass").toCharArray(), null);
	    }
	} catch (WorkflowException e) {
	    throw new RuntimeException("WorkflowContext could not be created!", e);
	}

	return workflowContext;
    }

    @Autowired
    protected void setHumanTaskConnectionFactory(HumanTaskConnectionFactory pHumanTaskConnectionFactory) {

	connectionFactory = pHumanTaskConnectionFactory;
    }
}
