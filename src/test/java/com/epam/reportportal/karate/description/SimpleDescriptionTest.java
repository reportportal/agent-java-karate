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

package com.epam.reportportal.karate.description;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.intuit.karate.Results;
import com.intuit.karate.core.Background;
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
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class SimpleDescriptionTest {
    public static final String SCENARIO_DESCRIPTION = "This is my Scenario description.";
    private static final String TEST_FEATURE = "classpath:feature/description.feature";
    private final String featureId = CommonUtils.namedId("feature_");
    private final String scenarioId = CommonUtils.namedId("scenario_");
    private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
            .limit(3).collect(Collectors.toList());
    private final List<Pair<String, String>> nestedStepIds = stepIds.stream()
            .map(id -> Pair.of(id, CommonUtils.namedId("nested_step_"))).collect(Collectors.toList());
    private final ReportPortalClient client = mock(ReportPortalClient.class);
    private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

    @BeforeEach
    public void setupMock() {
        mockLaunch(client, null, featureId, scenarioId, stepIds);
        mockNestedSteps(client, nestedStepIds);
        mockBatchLogging(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_description_for_all_possible_items(boolean report) {
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

        StartTestItemRQ featureStart = featureCaptor.getValue();
        assertThat(featureStart.getDescription(), endsWith("feature/description.feature\n\n---\n\nThis is my Feature description."));

        StartTestItemRQ scenarioStart = scenarioCaptor.getValue();
        assertThat(scenarioStart.getDescription(), equalTo(SCENARIO_DESCRIPTION));

        List<StartTestItemRQ> backgroundSteps = stepCaptor.getAllValues().stream()
                .filter(s -> s.getName().startsWith(Background.KEYWORD)).collect(Collectors.toList());
        assertThat(backgroundSteps, hasSize(1));
        StartTestItemRQ backgroundStep = backgroundSteps.get(0);
        assertThat("No support of Background description in Karate yet. But this is a part of Gherkin standard.",
                backgroundStep.getDescription(), nullValue());
    }
}
