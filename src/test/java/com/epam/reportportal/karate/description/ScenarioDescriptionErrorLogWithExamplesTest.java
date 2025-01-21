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
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.reportportal.utils.formatting.MarkdownUtils;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.intuit.karate.Results;
import okhttp3.MultipartBody;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.ReportPortalUtils.MARKDOWN_DELIMITER_PATTERN;
import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class ScenarioDescriptionErrorLogWithExamplesTest {

    public static final String ERROR = "did not evaluate to 'true': mathResult == 5\nclasspath:feature/simple_failed_examples.feature:5";
    public static final String ERROR_MESSAGE = "Then assert mathResult == 5\n" + ERROR;
    public static final String DESCRIPTION_ERROR_LOG = "Error:\n" + ERROR;
    private static final String EXAMPLE_PARAMETERS_DESCRIPTION_PATTERN =
            "Parameters:\n\n" + MarkdownUtils.TABLE_INDENT + "| vara | varb | result |\n" + MarkdownUtils.TABLE_INDENT
                    + "|------|------|--------|\n" + MarkdownUtils.TABLE_INDENT;
    public static final String FIRST_EXAMPLE_DESCRIPTION = EXAMPLE_PARAMETERS_DESCRIPTION_PATTERN + "|  2   |  2   |   4    |";
    public static final String SECOND_EXAMPLE_DESCRIPTION = EXAMPLE_PARAMETERS_DESCRIPTION_PATTERN + "|  1   |  2   |   5    |";

    public static final String SECOND_EXAMPLE_DESCRIPTION_WITH_ERROR_LOG = String.format(
            MARKDOWN_DELIMITER_PATTERN,
            SECOND_EXAMPLE_DESCRIPTION,
            DESCRIPTION_ERROR_LOG
    );

    private static final String TEST_FEATURE = "classpath:feature/simple_failed_examples.feature";
    private final String featureId = CommonUtils.namedId("feature_");
    private final List<String> exampleIds = Stream.generate(() -> CommonUtils.namedId("example_")).limit(2).collect(Collectors.toList());
    private final List<Pair<String, List<String>>> stepIds = exampleIds.stream()
            .map(e -> Pair.of(e, Stream.generate(() -> CommonUtils.namedId("step_")).limit(2).collect(Collectors.toList())))
            .collect(Collectors.toList());

    private final ReportPortalClient client = mock(ReportPortalClient.class);
    private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

    @BeforeEach
    public void setupMock() {
        mockLaunch(client, null, featureId, stepIds);
        mockBatchLogging(client);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void test_error_log_and_examples_in_description(boolean report) {
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
        assertThat(log.getMessage(), equalTo(ERROR_MESSAGE));

        ArgumentCaptor<FinishTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(FinishTestItemRQ.class);
        verify(client).finishTestItem(same(exampleIds.get(0)), scenarioCaptor.capture());
        verify(client).finishTestItem(same(exampleIds.get(1)), scenarioCaptor.capture());

        List<ArgumentCaptor<FinishTestItemRQ>> stepCaptors = new ArrayList<>(Collections.nCopies(stepIds.size(), ArgumentCaptor.forClass(FinishTestItemRQ.class)));
        stepIds.forEach(pair -> pair.getValue().forEach(id -> verify(client).finishTestItem(same(id), stepCaptors.get(0).capture())));

        FinishTestItemRQ firstScenarioRq = scenarioCaptor.getAllValues().get(0);
        assertThat(firstScenarioRq.getStatus(), allOf(notNullValue(), equalTo(ItemStatus.PASSED.name())));
        assertThat(firstScenarioRq.getDescription(), allOf(notNullValue(), equalTo(FIRST_EXAMPLE_DESCRIPTION)));

        FinishTestItemRQ secondScenarioRq = scenarioCaptor.getAllValues().get(1);
        assertThat(secondScenarioRq.getStatus(), allOf(notNullValue(), equalTo(ItemStatus.FAILED.name())));
        assertThat(secondScenarioRq.getDescription(), allOf(notNullValue(), equalTo(SECOND_EXAMPLE_DESCRIPTION_WITH_ERROR_LOG)));
    }
}
