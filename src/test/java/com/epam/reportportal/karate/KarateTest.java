package com.epam.reportportal.karate;

import com.intuit.karate.Results;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KarateTest {
	@Test
	void testParallel() {
		Results results = KarateReportPortalRunner
				.path("classpath:feature")
				.outputCucumberJson(true)
				.tags("~@ignore", "@To_run")
				.parallel(2);
		assertEquals(0, results.getFailCount());
		assertEquals(0, results.getErrors().size());
		assertTrue(results.getSuite().parallel);
		assertEquals(2, results.getScenariosTotal());
	}
}