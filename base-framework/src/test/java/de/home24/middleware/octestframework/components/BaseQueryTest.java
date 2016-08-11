package de.home24.middleware.octestframework.components;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.octestframework.components.BaseQuery.SqlOp;

/**
 * Tests for {@link BaseQuery}
 * 
 * @author svb
 *
 */
public class BaseQueryTest {

    @Test
    public void createBaseSelectQuery() {

	final String correlationId = "0815";
	final BaseQuery<BalActivities> query = new BaseQuery<>(SqlOp.SELECT,
		new QueryPredicate("correlation_id", correlationId), BalActivities.class);

	assertThat("Generated Query does not meet the expectations!", query.getQuery(),
		equalTo("select * from bal_activities where correlation_id = ?"));
	assertThat("Query parameter is invalid", (String) query.getQueryParameters()[0],
		equalTo(correlationId));
    }

    @Test
    public void createBaseDeleteQuery() {

	final String correlationId = "0815";
	final BaseQuery<BalActivities> query = new BaseQuery<>(SqlOp.DELETE,
		new QueryPredicate("correlation_id", correlationId), BalActivities.class);

	assertThat("Generated Query does not meet the expectations!", query.getQuery(),
		equalTo("delete from bal_activities where correlation_id = ?"));
	assertThat("Query parameter is invalid", (String) query.getQueryParameters()[0],
		equalTo(correlationId));
    }
}
