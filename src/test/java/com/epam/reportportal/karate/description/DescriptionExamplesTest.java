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

package com.epam.reportportal.karate.description;

import com.epam.reportportal.karate.ReportPortalUtils;
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

public class DescriptionExamplesTest {
	public static final String FIRST_EXAMPLE_DESCRIPTION = String.format(
			ReportPortalUtils.MARKDOWN_DELIMITER_PATTERN,
			NoDescriptionExamplesTest.FIRST_EXAMPLE_DESCRIPTION,
			SimpleDescriptionTest.SCENARIO_DESCRIPTION
	);
	public static final String SECOND_EXAMPLE_DESCRIPTION = String.format(
			ReportPortalUtils.MARKDOWN_DELIMITER_PATTERN,
			NoDescriptionExamplesTest.SECOND_EXAMPLE_DESCRIPTION,
			SimpleDescriptionTest.SCENARIO_DESCRIPTION
	);
	private static final String TEST_FEATURE = "classpath:feature/description_examples.feature";
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
	public void test_examples_description(boolean report) {
		Results results;
		if (report) {
			results = TestUtils.runAsReport(rp, TEST_FEATURE);
		} else {
			results = TestUtils.runAsHook(rp, TEST_FEATURE);
		}
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(featureCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(featureId), scenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> firstStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(exampleIds.get(0)), firstStepCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> secondStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(exampleIds.get(1)), secondStepCaptor.capture());

		List<StartTestItemRQ> scenarios = scenarioCaptor.getAllValues();
		StartTestItemRQ firstScenarioRq = scenarios.get(0);
		assertThat(firstScenarioRq.getDescription(), allOf(notNullValue(), equalTo(FIRST_EXAMPLE_DESCRIPTION)));

		StartTestItemRQ secondScenarioRq = scenarios.get(1);
		assertThat(secondScenarioRq.getDescription(), allOf(notNullValue(), equalTo(SECOND_EXAMPLE_DESCRIPTION)));
	}
}
