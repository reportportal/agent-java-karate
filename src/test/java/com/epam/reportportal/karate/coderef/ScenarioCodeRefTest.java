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

package com.epam.reportportal.karate.coderef;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class ScenarioCodeRefTest {
    private static final String TEST_FEATURE = "classpath:feature/simple.feature";
    private static final String SIMPLE_CODE_REFERENCE = "feature/simple.feature/[SCENARIO:Verify math]";
    private final String featureId = CommonUtils.namedId("feature_");
    private final String scenarioId = CommonUtils.namedId("scenario_");
    private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
            .limit(3).collect(Collectors.toList());
    private final ReportPortalClient client = mock(ReportPortalClient.class);
    private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

    @BeforeEach
    public void setupMock() {
        mockLaunch(client, null, featureId, scenarioId, stepIds);
        mockBatchLogging(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_scenario_code_reference(boolean report) {
        Results results;
        if (report) {
            results = TestUtils.runAsReport(rp, TEST_FEATURE);
        } else {
            results = TestUtils.runAsHook(rp, TEST_FEATURE);
        }
        assertThat(results.getFailCount(), equalTo(0));

        ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client).startTestItem(featureCaptor.capture());
        ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client).startTestItem(same(featureId), scenarioCaptor.capture());
        ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client, times(3)).startTestItem(same(scenarioId), stepCaptor.capture());

        StartTestItemRQ featureRq = featureCaptor.getValue();
        StartTestItemRQ scenarioRq = scenarioCaptor.getValue();

        assertThat(featureRq.getType(), allOf(notNullValue(), equalTo(ItemType.STORY.name())));

        assertThat(scenarioRq.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
        assertThat(scenarioRq.getCodeRef(), allOf(notNullValue(), equalTo(SIMPLE_CODE_REFERENCE)));

        stepCaptor.getAllValues().forEach(step -> {
            assertThat(step.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
            assertThat(step.isHasStats(), equalTo(Boolean.FALSE));
        });
    }
}
