package de.home24.middleware.octestframework.components;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

import java.util.Date;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.annotation.PropertySources;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.AbstractTransactionalJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

import de.home24.middleware.entity.BalActivities;
import de.home24.middleware.entity.OsmCustCommId;
import de.home24.middleware.octestframework.components.OtmDao.Query;

/**
 * Test implementations for {@link OtmDao}
 * 
 * @author svb
 *
 */
@RunWith(SpringJUnit4ClassRunner.class)
@TestExecutionListeners({ DependencyInjectionTestExecutionListener.class })
@ContextConfiguration(locations = { "classpath:application-context-test.xml" })
@PropertySources({ @PropertySource("classpath:de/home24/middleware/config/soaconfig.local.properties") })
public class OtmDaoTest extends AbstractTransactionalJUnit4SpringContextTests {

    @Autowired
    private OtmDao otmDao;

    @Test
    public void queryAllFromBalActivities() {

	List<BalActivities> result = otmDao.query(new Query<BalActivities>() {

	    @Override
	    public String getQuery() {
		return "select * from bal_activities";
	    }

	    @Override
	    public Object[] getQueryParameters() {
		return new Object[] {};
	    }

	    @Override
	    public Class<BalActivities> getExpectedType() {
		return BalActivities.class;
	    }
	});

	assertNotNull(result);
	assertThat(result.isEmpty(), is(false));

	for (BalActivities balActivities : result) {

	    assertThat(balActivities.getBalActivityId(), not(nullValue()));
	    assertThat(balActivities.getActivityCode(), not(nullValue()));
	    assertThat(balActivities.getCorrelationId(), not(nullValue()));
	    assertThat(balActivities.getCreated(), not(nullValue()));
	}
    }

    @Test
    public void queryAllFromOsmCustComm() {

	List<OsmCustCommId> result = otmDao.query(new Query<OsmCustCommId>() {

	    @Override
	    public String getQuery() {
		return "select * from osm_cust_comm";
	    }

	    @Override
	    public Object[] getQueryParameters() {
		return new Object[] {};
	    }

	    @Override
	    public Class<OsmCustCommId> getExpectedType() {
		return OsmCustCommId.class;
	    }
	});

	assertNotNull(result);
	assertThat(result.isEmpty(), is(false));

	for (OsmCustCommId osmCustComm : result) {

	    assertCustomerCommunication(osmCustComm);
	}
    }

    @Test
    @Rollback
    public void selectSpecificRowOnly() {

	final String correlationId = "4711";
	final Date created = new Date();
	final String statusCode = "Test";
	final String statusText = "Test";
	final String statusObject = "CustomerCommunication";

	otmDao.getJdbcTemplate().update(
		"insert into osm_cust_comm (correlation_id, created, status_code, status_text, status_object) values (?,?,?,?,?)",
		new Object[] { correlationId, created, statusCode, statusText, statusObject });

	List<OsmCustCommId> result = otmDao.query(createOsmCustCommQuery(correlationId));

	assertThat(result.size(), is(1));
	assertCustomerCommunication(result.get(0));
    }

    @Test
    @Rollback
    public void deleteSpecificRow() {

	final String correlationId = "4711";
	final Date created = new Date();
	final String statusCode = "Test";
	final String statusText = "Test";
	final String statusObject = "CustomerCommunication";

	otmDao.getJdbcTemplate().update(
		"insert into osm_cust_comm (correlation_id, created, status_code, status_text, status_object) values (?,?,?,?,?)",
		new Object[] { correlationId, created, statusCode, statusText, statusObject });

	final Query<OsmCustCommId> createOsmCustCommQuery = createOsmCustCommQuery(correlationId);

	List<OsmCustCommId> result = otmDao.query(createOsmCustCommQuery);

	assertThat(result.size(), is(1));
	assertCustomerCommunication(result.get(0));

	otmDao.delete(new Query<Void>() {

	    @Override
	    public String getQuery() {
		return "delete from osm_cust_comm where correlation_id = ?";
	    }

	    @Override
	    public Object[] getQueryParameters() {
		return new Object[] { correlationId };
	    }

	    @Override
	    public Class<Void> getExpectedType() {
		return Void.class;
	    }
	});

	result = otmDao.query(createOsmCustCommQuery);

	assertThat(result.isEmpty(), is(true));
    }

    private Query<OsmCustCommId> createOsmCustCommQuery(final String correlationId) {
	return new Query<OsmCustCommId>() {

	    @Override
	    public String getQuery() {
		return "select * from osm_cust_comm where correlation_id = ?";
	    }

	    @Override
	    public Object[] getQueryParameters() {
		return new Object[] { correlationId };
	    }

	    @Override
	    public Class<OsmCustCommId> getExpectedType() {
		return OsmCustCommId.class;
	    }
	};
    }

    private void assertCustomerCommunication(OsmCustCommId osmCustComm) {
	assertThat(osmCustComm.getStatusCode(), not(nullValue()));
	assertThat(osmCustComm.getStatusText(), not(nullValue()));
	assertThat(osmCustComm.getStatusObject(), not(nullValue()));
	assertThat(osmCustComm.getStatusObject(), equalTo("CustomerCommunication"));
	assertThat(osmCustComm.getCorrelationId(), not(nullValue()));
	assertThat(osmCustComm.getCreated(), not(nullValue()));
    }
}
