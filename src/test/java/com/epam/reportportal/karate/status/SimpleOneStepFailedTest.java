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
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.intuit.karate.Results;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class SimpleOneStepFailedTest {
    private static final String TEST_FEATURE = "classpath:feature/simple_failed.feature";
    private final String launchUuid = CommonUtils.namedId("launch_");
    private final String featureId = CommonUtils.namedId("feature_");
    private final String scenarioId = CommonUtils.namedId("scenario_");
    private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
            .limit(3).collect(Collectors.toList());

    private final ReportPortalClient client = mock(ReportPortalClient.class);
    private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

    @BeforeEach
    public void setupMock() {
        mockLaunch(client, launchUuid, featureId, scenarioId, stepIds);
        mockBatchLogging(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
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
        ArgumentCaptor<FinishTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
        verify(client).finishTestItem(same(scenarioId), scenarioCaptor.capture());
        List<ArgumentCaptor<FinishTestItemRQ>> stepCaptors =
                Stream.generate(() -> ArgumentCaptor.forClass(FinishTestItemRQ.class)).limit(stepIds.size()).collect(Collectors.toList());
        IntStream.range(0, stepIds.size()).forEach(i -> verify(client).finishTestItem(same(stepIds.get(i)), stepCaptors.get(i).capture()));

        FinishTestItemRQ featureRq = featureCaptor.getValue();
        FinishTestItemRQ scenarioRq = scenarioCaptor.getValue();

        assertThat(featureRq.getStatus(), allOf(notNullValue(), equalTo(ItemStatus.FAILED.name())));
        assertThat(featureRq.getLaunchUuid(), allOf(notNullValue(), equalTo(launchUuid)));
        assertThat(featureRq.getEndTime(), notNullValue());

        assertThat(scenarioRq.getStatus(), allOf(notNullValue(), equalTo(ItemStatus.FAILED.name())));
        assertThat(scenarioRq.getLaunchUuid(), allOf(notNullValue(), equalTo(launchUuid)));
        assertThat(scenarioRq.getEndTime(), notNullValue());

        List<FinishTestItemRQ> steps = stepCaptors.stream().map(ArgumentCaptor::getValue).collect(Collectors.toList());
        steps.forEach(step -> {
            assertThat(step.getLaunchUuid(), allOf(notNullValue(), equalTo(launchUuid)));
            assertThat(step.getEndTime(), notNullValue());
        });
        List<String> statuses = steps.stream().map(FinishExecutionRQ::getStatus).collect(Collectors.toList());
        assertThat(statuses, containsInAnyOrder("PASSED", "PASSED", "FAILED"));
    }
}
