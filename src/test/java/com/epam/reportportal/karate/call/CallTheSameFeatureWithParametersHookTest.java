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

package com.epam.reportportal.karate.call;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.intuit.karate.Results;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class CallTheSameFeatureWithParametersHookTest {
	private static final String TEST_FEATURE = "classpath:feature/call_same_feature.feature";
	private final String featureId = CommonUtils.namedId("feature_");
	private final String scenarioId = CommonUtils.namedId("scenario_");
	private final String innerFeatureId = CommonUtils.namedId("feature_step_");
	private final List<String> stepIds = Arrays.asList(CommonUtils.namedId("step_"), CommonUtils.namedId("step_"),
			CommonUtils.namedId("step_"), innerFeatureId);
	private final String innerScenarioId = CommonUtils.namedId("scenario_step_");
	private final List<String> innerStepIds = Stream.generate(() -> CommonUtils.namedId("inner_step_"))
			.limit(4)
			.collect(Collectors.toList());

	private final List<Pair<String, Collection<Pair<String, List<String>>>>> features = Stream.of(Pair.of(featureId,
					(Collection<Pair<String, List<String>>>) Collections.singletonList(Pair.of(scenarioId, stepIds))
			))
			.collect(Collectors.toList());
	private final List<Pair<String, String>> nestedSteps = Stream.concat(
			Stream.of(Pair.of(innerFeatureId, innerScenarioId)),
			innerStepIds.stream().map(id -> Pair.of(innerScenarioId, id))
	).collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, null);
		mockFeatures(client, features);
		mockNestedSteps(client, nestedSteps);
		mockBatchLogging(client);
	}

	@Test
	@Timeout(value = 30, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
	public void test_call_feature_with_parameters_hook_reporting() {
		Results results = TestUtils.runAsHook(rp, TEST_FEATURE);
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(featureCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(featureId), scenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(scenarioId), stepCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> innerScenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(innerFeatureId), innerScenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> innerStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(innerScenarioId), innerStepCaptor.capture());
	}
}
