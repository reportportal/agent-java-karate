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

package com.epam.reportportal.karate.parameters;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.intuit.karate.Results;
import okhttp3.MultipartBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static java.util.stream.Stream.ofNullable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class ExamplesStepParametersTest {
	private static final String TEST_FEATURE = "classpath:feature/examples.feature";
	private final String featureId = CommonUtils.namedId("feature_");
	private final List<String> exampleIds = Stream.generate(() -> CommonUtils.namedId("example_")).limit(2).collect(Collectors.toList());
	private final List<Pair<String, List<String>>> stepIds = exampleIds.stream()
			.map(e -> Pair.of(e, Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	private static Set<String> toParameterStringList(List<ParameterResource> parameters) {
		return ofNullable(parameters).flatMap(Collection::stream).map(p -> p.getKey() + ":" + p.getValue()).collect(Collectors.toSet());
	}

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, null, featureId, stepIds);
		mockBatchLogging(client);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void test_examples_parameters_for_steps(boolean report) {
		Results results;
		if (report) {
			results = TestUtils.runAsReport(rp, TEST_FEATURE);
		} else {
			results = TestUtils.runAsHook(rp, TEST_FEATURE);
		}
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(captor.capture());
		verify(client, times(2)).startTestItem(same(featureId), captor.capture());
		ArgumentCaptor<StartTestItemRQ> firstExampleCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(exampleIds.get(0)), firstExampleCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> secondExampleCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(exampleIds.get(1)), secondExampleCaptor.capture());

		List<StartTestItemRQ> firstSteps = firstExampleCaptor.getAllValues();
		Set<String> parameterStrings = toParameterStringList(firstSteps.get(0).getParameters());
		assertThat(parameterStrings, hasSize(2));
		assertThat(parameterStrings, allOf(hasItem("vara:2"), hasItem("varb:2")));
		parameterStrings = toParameterStringList(firstSteps.get(1).getParameters());
		assertThat(parameterStrings, hasSize(1));
		assertThat(parameterStrings, hasItem("result:4"));

		List<StartTestItemRQ> secondSteps = secondExampleCaptor.getAllValues();
		parameterStrings = toParameterStringList(secondSteps.get(0).getParameters());
		assertThat(parameterStrings, hasSize(2));
		assertThat(parameterStrings, allOf(hasItem("vara:1"), hasItem("varb:2")));
		parameterStrings = toParameterStringList(secondSteps.get(1).getParameters());
		assertThat(parameterStrings, hasSize(1));
		assertThat(parameterStrings, hasItem("result:3"));

		ArgumentCaptor<List> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeastOnce()).log(logCaptor.capture());
		Map<String, SaveLogRQ> logs = logCaptor.getAllValues()
				.stream()
				.flatMap(rq -> extractJsonParts((List<MultipartBody.Part>) rq).stream())
				.filter(rq -> LogLevel.INFO.name().equals(rq.getLevel()))
				.collect(Collectors.toMap(SaveLogRQ::getItemUuid, v -> v));

		List<String> stepIdList = stepIds.stream().flatMap(e -> e.getValue().stream()).collect(Collectors.toList());
		assertThat(logs.keySet(), hasSize(stepIdList.size()));
		stepIdList.forEach(id -> assertThat(logs, hasKey(id)));
		assertThat(
				logs.values().stream().map(SaveLogRQ::getMessage).collect(Collectors.toList()),
				everyItem(startsWith("Parameters:\n\n"))
		);
	}
}
