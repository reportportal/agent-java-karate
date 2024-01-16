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
import com.epam.reportportal.utils.markdown.MarkdownUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.intuit.karate.Results;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

public class CallWithParametersHookTest {
    private static final String TEST_FEATURE = "classpath:feature/call.feature";
    private static final String PARAMETERS_DESCRIPTION_PATTERN =
            "Parameters:\n\n"
                    + MarkdownUtils.TABLE_INDENT
                    + "| vara | result |\n"
                    + MarkdownUtils.TABLE_INDENT
                    + "|------|--------|\n"
                    + MarkdownUtils.TABLE_INDENT
                    + "|  2   |   4    |\n\n"
                    + MarkdownUtils.TABLE_ROW_SEPARATOR;
    private final List<String> featureIds = Stream.generate(() -> CommonUtils.namedId("feature_"))
            .limit(2).collect(Collectors.toList());
    private final List<String> scenarioIds = Stream.generate(() -> CommonUtils.namedId("scenario_"))
            .limit(2).collect(Collectors.toList());
    private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
            .limit(4).collect(Collectors.toList());
    private final List<Pair<String, Collection<Pair<String, List<String>>>>> features =
            Stream.of(
                    Pair.of(featureIds.get(0), (Collection<Pair<String, List<String>>>) Collections.singletonList(Pair.of(scenarioIds.get(0), Collections.singletonList(stepIds.get(0))))),
                    Pair.of(featureIds.get(1), (Collection<Pair<String, List<String>>>) Collections.singletonList(Pair.of(scenarioIds.get(1), stepIds.subList(1, stepIds.size()))))
            ).collect(Collectors.toList());
    private final ReportPortalClient client = mock(ReportPortalClient.class);
    private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

    @BeforeEach
    public void setupMock() {
        mockLaunch(client, null);
        mockFeatures(client, features);
        mockBatchLogging(client);
    }

    @Test
    public void test_call_feature_with_parameters_hook_reporting() {
        Results results = TestUtils.runAsHook(rp, TEST_FEATURE);
        assertThat(results.getFailCount(), equalTo(0));

        ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client, times(2)).startTestItem(featureCaptor.capture());
        ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client).startTestItem(same(featureIds.get(0)), scenarioCaptor.capture());
        verify(client).startTestItem(same(featureIds.get(1)), scenarioCaptor.capture());
        ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
        verify(client).startTestItem(same(scenarioIds.get(0)), stepCaptor.capture());
        verify(client, times(3)).startTestItem(same(scenarioIds.get(1)), stepCaptor.capture());

        StartTestItemRQ calledFeature = featureCaptor.getAllValues().stream()
                .filter(rq -> "a feature which is called with parameters".equals(rq.getName())).findAny().orElseThrow();

        assertThat(calledFeature.getDescription(), allOf(endsWith("feature/called.feature"), startsWith(PARAMETERS_DESCRIPTION_PATTERN)));
    }
}
