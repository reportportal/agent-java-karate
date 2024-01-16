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

package com.epam.reportportal.karate.background;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.ItemType;
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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

public class BackgroundExamplesTest {
    private static final String TEST_FEATURE = "classpath:feature/background_examples.feature";
    private final String featureId = CommonUtils.namedId("feature_");
    private final List<String> scenarioIds = Stream.generate(() -> CommonUtils.namedId("scenario_"))
            .limit(2).collect(Collectors.toList());

    private final List<Pair<String, List<String>>> scenarioSteps = scenarioIds
            .stream().map(s ->
                    Pair.of(s, Stream.generate(() ->
                            CommonUtils.namedId("step_")).limit(3).collect(Collectors.toList())))
            .collect(Collectors.toList());
    private final List<Pair<String, String>> nestedStepIds = scenarioSteps
            .stream()
            .flatMap(s -> s.getValue().stream())
            .map(s -> Pair.of(s, CommonUtils.namedId("nested_step_")))
            .collect(Collectors.toList());

    private final ReportPortalClient client = mock(ReportPortalClient.class);
    private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

    @BeforeEach
    public void setupMock() {
        mockLaunch(client, null, featureId, scenarioSteps);
        mockNestedSteps(client, nestedStepIds);
        mockBatchLogging(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_background_steps(boolean report) {
        Results results;
        if (report) {
            results = TestUtils.runAsReport(rp, TEST_FEATURE);
        } else {
            results = TestUtils.runAsHook(rp, TEST_FEATURE);
        }
        assertThat(results.getFailCount(), equalTo(0));

        ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client).startTestItem(captor.capture());
        verify(client, times(2)).startTestItem(same(featureId), captor.capture());
        ArgumentCaptor<StartTestItemRQ> firstStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client, times(3)).startTestItem(same(scenarioIds.get(0)), firstStepCaptor.capture());
        ArgumentCaptor<StartTestItemRQ> secondStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client, times(3)).startTestItem(same(scenarioIds.get(1)), secondStepCaptor.capture());
        ArgumentCaptor<StartTestItemRQ> nestedStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client, times(2)).startTestItem(startsWith("step_"), nestedStepCaptor.capture());

        List<StartTestItemRQ> items = captor.getAllValues();
        assertThat(items, hasSize(3));
        List<StartTestItemRQ> firstSteps = firstStepCaptor.getAllValues();
        assertThat(firstSteps, hasSize(3));
        List<StartTestItemRQ> secondSteps = secondStepCaptor.getAllValues();
        assertThat(firstSteps, hasSize(3));

        List<StartTestItemRQ> firstBackgroundSteps = firstSteps.stream()
                .filter(s -> s.getName().startsWith(Background.KEYWORD)).collect(Collectors.toList());
        assertThat(firstBackgroundSteps, hasSize(1));
        List<StartTestItemRQ> secondBackgroundSteps = secondSteps.stream()
                .filter(s -> s.getName().startsWith(Background.KEYWORD)).collect(Collectors.toList());
        assertThat(secondBackgroundSteps, hasSize(1));

        Stream.concat(firstBackgroundSteps.stream(), secondBackgroundSteps.stream()).forEach(backgroundStep -> {
            assertThat(backgroundStep.getName(), equalTo(Background.KEYWORD)); // No name for Background in Karate
            assertThat(backgroundStep.isHasStats(), equalTo(Boolean.FALSE));
            assertThat(backgroundStep.getStartTime(), notNullValue());
            assertThat(backgroundStep.getType(), equalTo(ItemType.STEP.name()));
        });

        List<StartTestItemRQ> nestedSteps = nestedStepCaptor.getAllValues();
        assertThat(nestedSteps, hasSize(2));
        nestedSteps.forEach(step -> assertThat(step.isHasStats(), equalTo(Boolean.FALSE)));
        Set<String> nestedStepNames = nestedSteps.stream().map(StartTestItemRQ::getName).collect(Collectors.toSet());

        assertThat(nestedStepNames, hasSize(1));
        assertThat(nestedStepNames, hasItem("Given def varb = 2"));
    }
}
