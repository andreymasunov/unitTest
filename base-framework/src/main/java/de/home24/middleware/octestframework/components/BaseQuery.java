package de.home24.middleware.octestframework.components;

import javax.persistence.Table;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmPo;
import de.home24.middleware.entity.OsmSoItem;
import de.home24.middleware.octestframework.components.OtmDao.Query;

/**
 * Base implementation for {@link Query}.
 * 
 * @author svb
 *
 * @param <T>
 */
public class BaseQuery<T> implements Query<T> {

    public enum SqlOp {
	SELECT("select * from"), DELETE("delete from");

	private String sqlIntro;

	private SqlOp(String pSqlIntro) {
	    sqlIntro = pSqlIntro;
	}

	public String getSqlIntro() {
	    return sqlIntro;
	}
    }

    private SqlOp sqlOperation;
    private QueryPredicate queryPredicate;
    private Class<T> clazz;

    public BaseQuery(SqlOp pSqlOperation, QueryPredicate pQueryPredicate, Class<T> pClass) {

	sqlOperation = pSqlOperation;
	queryPredicate = pQueryPredicate;
	clazz = pClass;
    }

    @Override
    public Object[] getQueryParameters() {
	return queryPredicate.getParameters();
    }

    @Override
    public Class<T> getExpectedType() {
	return clazz;
    }

    @Override
    public String getQuery() {

	Table tableAnnotation = clazz.getAnnotation(Table.class);

	if (tableAnnotation == null) {
	    throw new IllegalArgumentException(
		    String.format("Expected annonation %s was not found on class %s", Table.class.getName(),
			    clazz.getName()));
	}

	return String.format("%s %s %s", sqlOperation.sqlIntro, tableAnnotation.name().toLowerCase(),
		queryPredicate.getPredicate());
    }

    public static BaseQuery<BalActivities> createBALQuery(String correlationId, String activityCode) {
	return new BaseQuery<>(SqlOp.SELECT,
		new QueryPredicate("correlation_id", correlationId).withEquals("activity_code", activityCode),
		BalActivities.class);
    }

    public static BaseQuery<BalActivities> createDeleteBALQuery(String correlationId) {
	return new BaseQuery<>(SqlOp.DELETE, new QueryPredicate("correlation_id", correlationId),
		BalActivities.class);
    }

    public static BaseQuery<OsmPo> createDeleteOSMPOQuery(String correlationId) {
	return new BaseQuery<>(SqlOp.DELETE, new QueryPredicate("correlation_id", correlationId),
		OsmPo.class);
    }
	
	public static BaseQuery<OsmSoItem> createDeleteOSMSOItemQuery(String correlationId) {
	return new BaseQuery<>(SqlOp.DELETE, new QueryPredicate("correlation_id", correlationId), 
		OsmSoItem.class);
	}
}