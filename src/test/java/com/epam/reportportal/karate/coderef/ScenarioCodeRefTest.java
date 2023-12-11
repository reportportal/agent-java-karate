package com.epam.reportportal.karate.coderef;

import com.epam.reportportal.karate.utils.TestUtils;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class ScenarioCodeRefTest {
	@Test
	public void test_scenario_code_reference() {
		var results = TestUtils.run("classpath:feature/simple.feature");
		assertThat(results.getFailCount(), equalTo(0));
	}
}
