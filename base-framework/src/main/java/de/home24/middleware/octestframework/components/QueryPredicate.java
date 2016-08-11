package de.home24.middleware.octestframework.components;

import java.util.Iterator;
import java.util.List;

import com.google.common.collect.Lists;

import de.home24.middleware.octestframework.components.OtmDao.Query;

/**
 * Represents a predicate which is used in {@link Query} as filter in WHERE
 * clauses.
 * 
 * @author svb
 *
 */
public class QueryPredicate {

    private List<String> predicateValues;
    private List<Object> parameterValues;

    public QueryPredicate() {

	predicateValues = Lists.newArrayList();
	parameterValues = Lists.newArrayList();
    }

    public QueryPredicate(String pColumnName, Object pParameterValue) {

	this();
	withEquals(pColumnName, pParameterValue);
    }

    /**
     * Add a predicate that does a equals comparison.
     * 
     * @param pColumnName
     *            the column name to check
     * @param pParameterValue
     *            the value of the parameter
     * @return the current {@link QueryPredicate} instance
     */
    public QueryPredicate withEquals(String pColumnName, Object pParameterValue) {

	predicateValues.add(getEqualsPredicate(pColumnName));
	parameterValues.add(pParameterValue);
	return this;
    }

    public QueryPredicate withEquals(String pColumnName, Object... pParameterValue) {

	predicateValues.add(getInPredicate(pColumnName, pParameterValue.length));
	parameterValues.add(pParameterValue);
	return this;
    }

    /**
     * Builds the predicate string and returns it.
     * 
     * @return the predicate string
     */
    public String getPredicate() {

	final StringBuilder predicateString = new StringBuilder("where ");
	final Iterator<String> predicates = predicateValues.iterator();

	while (predicates.hasNext()) {

	    predicateString.append(predicates.next());

	    if (predicates.hasNext()) {
		predicateString.append(" and ");
	    }
	}

	return predicateString.toString();
    }

    /**
     * Returns the parameters.
     * 
     * @return the parameters
     */
    public Object[] getParameters() {
	return parameterValues.toArray();
    }

    private String getEqualsPredicate(String pColumnName) {
	return String.format("%s = ?", pColumnName);
    }

    private String getInPredicate(String pColumnName, int pParameterCounter) {

	final StringBuilder parameters = new StringBuilder();
	for (int i = 0; i < pParameterCounter; i++) {
	    parameters.append("?");

	    if (i < pParameterCounter - 1) {
		parameters.append(",");
	    }
	}

	return String.format("%s in (%s)", pColumnName, parameters.toString());
    }
}
