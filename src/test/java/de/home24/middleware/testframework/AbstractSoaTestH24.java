package de.home24.middleware.testframework;

import org.junit.Before;

import com.opitzconsulting.soa.testing.AbstractSoaTest;

/**
 * Base class for SOA tests.
 * 
 * @author svb
 *
 */
public abstract class AbstractSoaTestH24 extends AbstractSoaTest {

	@Before
	public void setUp() {

		AbstractSoaTestH24.class.getClassLoader().getResourceAsStream(
				"de/home24/middleware/config/soaconfig.environment.properties");

	}
}
