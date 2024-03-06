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

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.intuit.karate.Results;
import okhttp3.MultipartBody;
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

public class ScenarioDescriptionErrorLogTest {

    public static final String ERROR = "did not evaluate to 'true': actualFour != four\nclasspath:feature/simple_failed.feature:6";
    public static final String ERROR_MESSAGE = "Then assert actualFour != four\n" + ERROR;
    public static final String DESCRIPTION_ERROR_LOG = "Error:\n" + ERROR;
    private static final String TEST_FEATURE = "classpath:feature/simple_failed.feature";
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
    @ValueSource(booleans = {true, false})
    public void test_error_log_in_description(boolean report) {
        Results results;
        if (report) {
            results = TestUtils.runAsReport(rp, TEST_FEATURE);
        } else {
            results = TestUtils.runAsHook(rp, TEST_FEATURE);
        }
        assertThat(results.getFailCount(), equalTo(1));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<MultipartBody.Part>> logCaptor = ArgumentCaptor.forClass(List.class);
        verify(client, atLeastOnce()).log(logCaptor.capture());
        List<SaveLogRQ> logs = logCaptor.getAllValues()
                .stream()
                .flatMap(rq -> extractJsonParts(rq).stream())
                .filter(rq -> LogLevel.ERROR.name().equals(rq.getLevel()))
                .collect(Collectors.toList());

        assertThat(logs, hasSize(greaterThan(0)));
        SaveLogRQ log = logs.get(logs.size() - 1);
        assertThat(log.getItemUuid(), oneOf(stepIds.toArray(new String[0])));
        assertThat(log.getLaunchUuid(), equalTo(launchUuid));
        assertThat(log.getMessage(), equalTo(ERROR_MESSAGE));

        ArgumentCaptor<FinishTestItemRQ> scenarioCaptorFinish = ArgumentCaptor.forClass(FinishTestItemRQ.class);
        verify(client, times(1)).finishTestItem(same(scenarioId), scenarioCaptorFinish.capture());

        List<FinishTestItemRQ> scenarios = scenarioCaptorFinish.getAllValues();
        FinishTestItemRQ scenario = scenarios.get(0);
        assertThat(scenario.getDescription(), allOf(notNullValue(), equalTo(DESCRIPTION_ERROR_LOG)));
    }
}
