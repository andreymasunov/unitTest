package de.home24.middleware.octestframework.components;

import java.util.List;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Encapsulates the functionalities for accessing, querying and manipulating a
 * database.
 * 
 * @author svb
 *
 */
@Component
public final class OtmDao {

    /**
     * Representation of a SQL Query and it's corresponding information, needed
     * for executing the query against the database.
     * 
     * @author svb
     *
     * @param <T>
     */
    public interface Query<T> {

	/**
	 * Returns the SQL query string.
	 * 
	 * @return the SQL query string
	 */
	String getQuery();

	/**
	 * Returns the parameters for the query.
	 * 
	 * @return the SQl query parameters
	 */
	Object[] getQueryParameters();

	/**
	 * Returns the expected type to be returned by the query.
	 * 
	 * @return the class expected of the expected object.
	 */
	Class<T> getExpectedType();
    }

    private JdbcTemplate jdbcTemplate;

    public JdbcTemplate getJdbcTemplate() {
	return jdbcTemplate;
    }

    @Autowired
    protected void setDataSource(DataSource pDataSource) {

	jdbcTemplate = new JdbcTemplate(pDataSource);
    }

    /**
     * Executes a SQL query against the database and return the result list.
     * 
     * @param pQuery
     *            the defined {@link Query}
     * @return List of objects from type T
     */
    public <T> List<T> query(Query<T> pQuery) {

	return jdbcTemplate.query(pQuery.getQuery(), pQuery.getQueryParameters(),
		new BeanPropertyRowMapper<T>(pQuery.getExpectedType()));
    }

    /**
     * Deletes rows from the database as specified by the {@link Query}
     * 
     * @param pQuery
     *            the defined {@link Query}
     */
    public <T> void delete(Query<T> pQuery) {

	jdbcTemplate.update(pQuery.getQuery(), pQuery.getQueryParameters());
    }
}
