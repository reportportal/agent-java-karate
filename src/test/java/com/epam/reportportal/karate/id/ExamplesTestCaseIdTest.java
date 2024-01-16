/*
 * Copyright 2024 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.karate.id;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.intuit.karate.Results;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class ExamplesTestCaseIdTest {
	private static final String TEST_FEATURE = "classpath:feature/examples.feature";
	private static final String EXAMPLE_TEST_CASE_ID_PATTERN = "feature/examples.feature/[EXAMPLE:Verify different maths[%s]]";
	private static final String FIRST_EXAMPLE_TEST_CASE_ID = String.format(EXAMPLE_TEST_CASE_ID_PATTERN, "result:4;vara:2;varb:2");
	private static final String SECOND_EXAMPLE_TEST_CASE_ID = String.format(EXAMPLE_TEST_CASE_ID_PATTERN, "result:3;vara:1;varb:2");
	private final String featureId = CommonUtils.namedId("feature_");
	private final List<String> exampleIds = Stream.generate(() -> CommonUtils.namedId("example_")).limit(2).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> stepIds = exampleIds.stream()
			.map(e -> Pair.of(e, Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList())))
			.collect(Collectors.toList());
	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, null, featureId, stepIds);
		mockBatchLogging(client);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void test_examples_test_case_id(boolean report) {
		Results results;
		if (report) {
			results = TestUtils.runAsReport(rp, TEST_FEATURE);
		} else {
			results = TestUtils.runAsHook(rp, TEST_FEATURE);
		}
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(captor.capture());
		verify(client, times(2)).startTestItem(same(featureId), captor.capture());
		verify(client, times(2)).startTestItem(same(exampleIds.get(0)), captor.capture());
		verify(client, times(2)).startTestItem(same(exampleIds.get(1)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		assertThat(items, hasSize(7));

		StartTestItemRQ firstScenarioRq = items.get(1);
		assertThat(firstScenarioRq.getTestCaseId(), allOf(notNullValue(), equalTo(FIRST_EXAMPLE_TEST_CASE_ID)));

		StartTestItemRQ secondScenarioRq = items.get(2);
		assertThat(secondScenarioRq.getTestCaseId(), allOf(notNullValue(), equalTo(SECOND_EXAMPLE_TEST_CASE_ID)));
	}
}
