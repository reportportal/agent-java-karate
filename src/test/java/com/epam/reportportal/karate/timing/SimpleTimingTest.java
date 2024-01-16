/*
 * Copyright 2024 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.karate.timing;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.intuit.karate.Results;
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

public class SimpleTimingTest {
    private static final String TEST_FEATURE = "classpath:feature/simple.feature";
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
    public void test_each_item_has_correct_start_date(boolean report) {
        Results results;
        if (report) {
            results = TestUtils.runAsReport(rp, TEST_FEATURE);
        } else {
            results = TestUtils.runAsHook(rp, TEST_FEATURE);
        }
        assertThat(results.getFailCount(), equalTo(0));

        ArgumentCaptor<StartLaunchRQ> launchCaptor = ArgumentCaptor.forClass(StartLaunchRQ.class);
        verify(client).startLaunch(launchCaptor.capture());
        ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client).startTestItem(featureCaptor.capture());
        ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client).startTestItem(same(featureId), scenarioCaptor.capture());
        ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client, times(3)).startTestItem(same(scenarioId), stepCaptor.capture());

        StartLaunchRQ launchRq = launchCaptor.getValue();
        StartTestItemRQ featureRq = featureCaptor.getValue();
        StartTestItemRQ scenarioRq = scenarioCaptor.getValue();

        assertThat("Launch start time is greater than Feature start time.",
                featureRq.getStartTime(), greaterThanOrEqualTo(launchRq.getStartTime()));
        assertThat("Feature start time is greater than Scenario start time.",
                scenarioRq.getStartTime(), greaterThanOrEqualTo(featureRq.getStartTime()));

        List<StartTestItemRQ> steps = stepCaptor.getAllValues();
        StartTestItemRQ firstStep = steps.stream()
                .filter(s -> "Given def four = 4".equals(s.getName())).findAny().orElseThrow();
        StartTestItemRQ secondStep = steps.stream()
                .filter(s -> "When def actualFour = 2 * 2".equals(s.getName())).findAny().orElseThrow();
        StartTestItemRQ thirdStep = steps.stream()
                .filter(s -> "Then assert actualFour == four".equals(s.getName())).findAny().orElseThrow();

        assertThat("Scenario start time is greater than Step start time.",
                firstStep.getStartTime(), greaterThanOrEqualTo(scenarioRq.getStartTime()));
        assertThat("First Step start time is greater or equal than Second Step start time.",
                secondStep.getStartTime(), greaterThan(firstStep.getStartTime()));
        assertThat("Second Step start time is greater or equal than Third Step start time.",
                thirdStep.getStartTime(), greaterThan(secondStep.getStartTime()));
    }
}
