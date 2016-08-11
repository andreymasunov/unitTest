package de.home24.middleware.octestframework.components;

import static org.hamcrest.CoreMatchers.endsWith;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.Test;

/**
 * Tests for {@link QueryPredicate}
 * 
 * @author svb
 *
 */
public class QueryPredicateTest {

    @Test
    public void buildQueryPredicateWithTwoParameters() {

	final String expectedPredicateString = "where activity_code = ? and correlation_id = ?";
	final String activityCodeValue = "P203-INIT";
	final String correlationIdValue = "12344553";

	final QueryPredicate predicate = new QueryPredicate().withEquals("activity_code", activityCodeValue)
		.withEquals("correlation_id", "12344553");

	String predicateString = predicate.getPredicate();
	Object[] parameters = predicate.getParameters();

	assertThat("Predicate string should not be empty", predicateString, not(nullValue()));
	assertThat("Parameters should be set", parameters, not(nullValue()));
	assertThat("Predicate string does not end with '?'", predicateString, endsWith("?"));
	assertThat("Predicate string does not correspond to the expected string", predicateString,
		equalTo(expectedPredicateString));
	assertThat("First parameter value is not as expected", parameters[0].toString(),
		equalTo(activityCodeValue));
	assertThat("Second parameter value is not as expected", parameters[1].toString(),
		equalTo(correlationIdValue));
    }

    @Test
    public void buildQueryPredicateWithOneParameterThatContainsMultipleParameters() {

	final String expectedPredicateString = "where activity_code in (?,?)";
	final String activityCodeValueInit = "P203-INIT";
	final String activityCodeValueInitError = "P203-INIT-ERR";

	final QueryPredicate predicate = new QueryPredicate().withEquals("activity_code",
		activityCodeValueInit, activityCodeValueInitError);

	String predicateString = predicate.getPredicate();
	Object[] parameters = predicate.getParameters();

	assertThat("Predicate string should not be empty", predicateString, not(nullValue()));
	assertThat("Parameters should be set", parameters, not(nullValue()));
	assertThat("Predicate string does not correspond to the expected string", predicateString,
		equalTo(expectedPredicateString));
    }
}
