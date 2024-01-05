/*
 * Copyright 2023 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.karate.attributes;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.intuit.karate.Results;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class LaunchAttributesTest {
	private static final String TEST_FEATURE = "classpath:feature/simple.feature";
	private final String featureId = CommonUtils.namedId("feature_");
	private final String scenarioId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
			.limit(3).collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private ReportPortal rp;

	@BeforeEach
	public void setupMock() {
		ListenerParameters parameters = standardParameters();
		parameters.setAttributes(new HashSet<>(Arrays.asList(new ItemAttributesRQ("key", "value"),
				new ItemAttributesRQ(null, "value"))));
		rp = ReportPortal.create(client, parameters, testExecutor());
		mockLaunch(client, null, featureId, scenarioId, stepIds);
		mockBatchLogging(client);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void verify_start_launch_request_contains_launch_attributes(boolean report) {
		Results results;
		if (report) {
			results = TestUtils.runAsReport(rp, TEST_FEATURE);
		} else {
			results = TestUtils.runAsHook(rp, TEST_FEATURE);
		}
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartLaunchRQ> startCaptor = ArgumentCaptor.forClass(StartLaunchRQ.class);
		verify(client).startLaunch(startCaptor.capture());

		StartLaunchRQ launchStart = startCaptor.getValue();

		Set<ItemAttributesRQ> attributes = launchStart.getAttributes();
		assertThat(attributes, allOf(notNullValue(), hasSize(greaterThan(0))));
		Set<String> attributesStr = attributes.stream()
				.filter(a -> !a.isSystem())
				.map(e -> e.getKey() + ":" + e.getValue())
				.collect(Collectors.toSet());
		assertThat(attributesStr, hasSize(2));
		assertThat(attributesStr, hasItem("key:value"));
		assertThat(attributesStr, hasItem("null:value"));
	}
}
