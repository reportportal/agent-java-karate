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

package com.epam.reportportal.karate.tags;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.intuit.karate.Results;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class SimpleTagInclusionTest {
	private static final String[] TEST_FEATURES = new String[] { "classpath:feature/tags.feature",
			"classpath:feature/http_request_tag.feature" };
	private final String launchUuid = CommonUtils.namedId("launch_");
	private final String featureId = CommonUtils.namedId("feature_");
	private final String scenarioId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, launchUuid, featureId, scenarioId, stepIds);
		mockBatchLogging(client);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void test_simple_all_passed(boolean report) {
		List<String> tagsToRun = Collections.singletonList("scope=smoke");
		Results results;
		if (report) {
			results = TestUtils.runAsReport(rp, tagsToRun, TEST_FEATURES);
		} else {
			results = TestUtils.runAsHook(rp, tagsToRun, TEST_FEATURES);
		}
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(featureCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(same(featureId), scenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(scenarioId), stepCaptor.capture());

		ArgumentCaptor<FinishTestItemRQ> featureFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(featureId), featureFinishCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> scenarioFinishCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(scenarioId), scenarioFinishCaptor.capture());
		List<ArgumentCaptor<FinishTestItemRQ>> stepFinishCaptors = Stream.generate(() -> ArgumentCaptor.forClass(FinishTestItemRQ.class))
				.limit(stepIds.size())
				.collect(Collectors.toList());
		IntStream.range(0, stepIds.size())
				.forEach(i -> verify(client).finishTestItem(same(stepIds.get(i)), stepFinishCaptors.get(i).capture()));

		FinishTestItemRQ featureRq = featureFinishCaptor.getValue();
		FinishTestItemRQ scenarioRq = scenarioFinishCaptor.getValue();

		assertThat(featureRq.getStatus(), allOf(notNullValue(), equalTo(ItemStatus.PASSED.name())));
		assertThat(featureRq.getLaunchUuid(), allOf(notNullValue(), equalTo(launchUuid)));
		assertThat(featureRq.getEndTime(), notNullValue());

		assertThat(scenarioRq.getStatus(), allOf(notNullValue(), equalTo(ItemStatus.PASSED.name())));
		assertThat(scenarioRq.getLaunchUuid(), allOf(notNullValue(), equalTo(launchUuid)));
		assertThat(scenarioRq.getEndTime(), notNullValue());

		stepFinishCaptors.forEach(stepFinishCaptor -> {
			FinishTestItemRQ step = stepFinishCaptor.getValue();
			assertThat(step.getStatus(), allOf(notNullValue(), equalTo(ItemStatus.PASSED.name())));
			assertThat(step.getLaunchUuid(), allOf(notNullValue(), equalTo(launchUuid)));
			assertThat(step.getEndTime(), notNullValue());
		});
	}
}
