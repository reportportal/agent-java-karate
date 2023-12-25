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

package com.epam.reportportal.karate.description;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class NoLaunchDescriptionTest {
	private final String featureId = CommonUtils.namedId("feature_");
	private final String scenarioId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
			.limit(3).collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, null, featureId, scenarioId, stepIds);
		mockBatchLogging(client);
	}

	public static List<String> dataValues() {
		return Arrays.asList(
				null,
				"",
				"   "
		);
	}

	@ParameterizedTest
	@MethodSource("dataValues")
	public void verify_start_launch_request_contains_no_launch_description(String description) {
		ListenerParameters parameters = standardParameters();
		parameters.setDescription(description);
		ReportPortal rp = ReportPortal.create(client, parameters, testExecutor());
		var results = TestUtils.runAsReport(rp, "classpath:feature/simple.feature");
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartLaunchRQ> startCaptor = ArgumentCaptor.forClass(StartLaunchRQ.class);
		verify(client).startLaunch(startCaptor.capture());

		StartLaunchRQ launchStart = startCaptor.getValue();

		assertThat(launchStart.getDescription(), nullValue());
	}
}
