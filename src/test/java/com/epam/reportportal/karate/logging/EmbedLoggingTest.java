/*
 * Copyright 2025 EPAM Systems
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
import com.intuit.karate.Results;
import okhttp3.MultipartBody;
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
import static org.mockito.Mockito.*;

public class EmbedLoggingTest {
	private static final String TEST_FEATURE = "classpath:feature/embed.feature";
	private final String launchUuid = CommonUtils.namedId("launch_");
	private final String featureId = CommonUtils.namedId("feature_");
	private final String scenarioId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, launchUuid, featureId, scenarioId, stepIds);
		mockBatchLogging(client);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public void test_embed_image_attachment(boolean report) {
		Results results;
		if (report) {
			results = TestUtils.runAsReport(rp, TEST_FEATURE);
		} else {
			results = TestUtils.runAsHook(rp, TEST_FEATURE);
		}
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<List> logCaptor = ArgumentCaptor.forClass(List.class);
		verify(client).log(logCaptor.capture());

		List<SaveLogRQ> logs = logCaptor.getAllValues()
				.stream()
				.flatMap(rq -> extractJsonParts((List<MultipartBody.Part>) rq).stream())
				.collect(Collectors.toList());
		assertThat(logs, hasSize(2));

		List<SaveLogRQ> attachmentLogs = logs.stream()
				.filter(log -> log.getFile() != null)
				.filter(log -> log.getMessage() != null && log.getMessage().startsWith("Attachment: "))
				.collect(Collectors.toList());

		assertThat("Should have one attachment log message", attachmentLogs, hasSize(1));

		// Verify the attachment log properties
		SaveLogRQ attachmentLog = attachmentLogs.get(0);
		assertThat("Attachment log should have INFO level", attachmentLog.getLevel(), equalTo(LogLevel.INFO.name()));
		assertThat("Attachment log should have item UUID", attachmentLog.getItemUuid(), notNullValue());
		assertThat("Attachment log should have log time", attachmentLog.getLogTime(), notNullValue());
		assertThat("Attachment message should contain image/png", attachmentLog.getMessage(), equalTo("Attachment: image/png"));

		List<Pair<String, byte[]>> attachments = logCaptor.getAllValues()
				.stream()
				.flatMap(rq -> extractBinaryParts((List<MultipartBody.Part>) rq).stream())
				.collect(Collectors.toList());
		assertThat(attachments, hasSize(1));
		assertThat(attachments.get(0).getKey(), equalTo("image/png"));
		assertThat(attachments.get(0).getValue().length, greaterThan(0));
	}
}

