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

package com.epam.reportportal.karate.retry;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.intuit.karate.Results;
import com.intuit.karate.core.ScenarioResult;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.any;

public class RetryFailedTest {
	private static final String TEST_FEATURE = "classpath:feature/simple_failed.feature";
	private final String launchUuid = CommonUtils.namedId("launch_");
	private final String featureId = CommonUtils.namedId("feature_");
	private final List<String> scenarioIds = Stream.generate(() -> CommonUtils.namedId("scenario_")).limit(2).collect(Collectors.toList());
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(6).collect(Collectors.toList());

	private final List<Pair<String, List<String>>> scenarioSteps = Stream.of(
					Pair.of(scenarioIds.get(0), stepIds.subList(0, 3)),
					Pair.of(scenarioIds.get(1), stepIds.subList(3, 6))
			)
			.collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, launchUuid, featureId, scenarioSteps);
		mockBatchLogging(client);
	}

	@Test
	public void test_simple_one_step_failed_retry() {
		Results results = TestUtils.runAsHook(rp, TEST_FEATURE);
		assertThat(results.getFailCount(), equalTo(1));

		List<ScenarioResult> failedResults = results.getScenarioResults().filter(ScenarioResult::isFailed).collect(Collectors.toList());
		assertThat(failedResults, hasSize(1));

		results.getSuite().retryScenario(failedResults.get(0).getScenario());

		verify(client).startTestItem(any(StartTestItemRQ.class));
		ArgumentCaptor<StartTestItemRQ> scenarioStartCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(featureId), scenarioStartCaptor.capture());

		verify(client).finishTestItem(same(featureId), any(FinishTestItemRQ.class));
		ArgumentCaptor<FinishTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(scenarioIds.get(0)), scenarioCaptor.capture());
		verify(client).finishTestItem(same(scenarioIds.get(1)), scenarioCaptor.capture());

		assertThat(scenarioStartCaptor.getAllValues().get(0).isRetry(), anyOf(equalTo(false), nullValue()));
		assertThat(scenarioStartCaptor.getAllValues().get(1).isRetry(), equalTo(true));
		assertThat(scenarioStartCaptor.getAllValues().get(1).getRetryOf(), equalTo(scenarioIds.get(0)));
	}
}
