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

package com.epam.reportportal.karate.logging;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.karatelabs.core.SuiteResult;
import okhttp3.MultipartBody;
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

public class HttpRequestLoggingTest {
	private static final String TEST_FEATURE = "classpath:feature/http_request.feature";
	private static final String DOCSTRING_LOG_ENTRY = """
			Docstring:
			
			```
			{
			  username: 'user',
			  password: 'password',
			  grant_type: 'password'
			}
			```""";
	private final String launchUuid = CommonUtils.namedId("launch_");
	private final String featureId = CommonUtils.namedId("feature_");
	private final String scenarioId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(6).collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, launchUuid, featureId, scenarioId, stepIds);
		mockBatchLogging(client);
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private List<String> extractMessages() {
		ArgumentCaptor<List> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client, atLeastOnce()).log(logCaptor.capture());
		List<SaveLogRQ> logs = logCaptor.getAllValues()
				.stream()
				.flatMap(rq -> extractJsonParts((List<MultipartBody.Part>) rq).stream())
				.filter(rq -> LogLevel.INFO.name().equals(rq.getLevel()) || LogLevel.DEBUG.name().equals(rq.getLevel()))
				.collect(Collectors.toList());

		assertThat(logs, hasSize(greaterThanOrEqualTo(2)));
		return logs.stream().map(SaveLogRQ::getMessage).collect(Collectors.toList());
	}

	@Test
	public void test_http_request_logging_result_listener() {
		SuiteResult results = TestUtils.runAsResultListener(rp, TEST_FEATURE);
		assertThat(results.getFeatureFailedCount(), equalTo(1));
		List<String> messages = extractMessages();
		assertThat(messages, hasItem(equalTo(DOCSTRING_LOG_ENTRY)));
		assertThat(messages, hasItem(containsString("{\"username\":\"user\",\"password\":\"password\",\"grant_type\":\"password\"}")));
	}

	@Test
	public void test_http_request_logging_run_listener() {
		SuiteResult results = TestUtils.runAsEventListener(rp, TEST_FEATURE);
		assertThat(results.getFeatureFailedCount(), equalTo(1));
		List<String> messages = extractMessages();
		assertThat(messages, hasItem(equalTo(DOCSTRING_LOG_ENTRY)));
		assertThat(messages, hasItem(containsString("**>>> REQUEST**")));
		assertThat(messages, hasItem(containsString("**<<< RESPONSE**")));
		assertThat(messages, hasItem(containsString("\"username\" : \"user\"")));
	}
}
