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

package com.epam.reportportal.karate.status;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.intuit.karate.Results;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class OneExampleFailedTest {
	private static final String TEST_FEATURE = "classpath:feature/examples_one_failed.feature";
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

	private static void verifyStatus(List<FinishTestItemRQ> rqs, ItemStatus... statuses) {
		for (int i = 0; i < rqs.size(); i++) {
			ItemStatus statusTest = i >= statuses.length ? statuses[statuses.length - 1] : statuses[i];
			assertThat(
					"Failed verifying request number: " + (i + 1),
					rqs.get(i).getStatus(),
					allOf(notNullValue(), equalTo(statusTest.name()))
			);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	public void test_simple_one_step_failed(boolean report) {
		Results results;
		if (report) {
			results = TestUtils.runAsReport(rp, TEST_FEATURE);
		} else {
			results = TestUtils.runAsHook(rp, TEST_FEATURE);
		}
		assertThat(results.getFailCount(), equalTo(1));

		ArgumentCaptor<FinishTestItemRQ> featureCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(featureId), featureCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> firstExampleCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(exampleIds.get(0)), firstExampleCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> secondExampleCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(exampleIds.get(1)), secondExampleCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> firstExampleFirstStepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(stepIds.get(0).getValue().get(0)), firstExampleFirstStepCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> firstExampleSecondStepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(stepIds.get(0).getValue().get(1)), firstExampleSecondStepCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> secondExampleFirstStepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(stepIds.get(1).getValue().get(0)), secondExampleFirstStepCaptor.capture());
		ArgumentCaptor<FinishTestItemRQ> secondExampleSecondStepCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
		verify(client).finishTestItem(same(stepIds.get(1).getValue().get(1)), secondExampleSecondStepCaptor.capture());

		FinishTestItemRQ featureRq = featureCaptor.getValue();
		assertThat(featureRq.getStatus(), allOf(notNullValue(), equalTo(ItemStatus.FAILED.name())));

		verifyStatus(Arrays.asList(firstExampleCaptor.getValue(), secondExampleCaptor.getValue()), ItemStatus.PASSED, ItemStatus.FAILED);

		List<FinishTestItemRQ> steps = Arrays.asList(firstExampleFirstStepCaptor.getValue(),
				firstExampleSecondStepCaptor.getValue(),
				secondExampleFirstStepCaptor.getValue(),
				secondExampleSecondStepCaptor.getValue()
		);

		verifyStatus(steps, ItemStatus.PASSED, ItemStatus.PASSED, ItemStatus.PASSED, ItemStatus.FAILED);
	}
}
