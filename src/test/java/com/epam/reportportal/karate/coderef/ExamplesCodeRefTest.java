package com.epam.reportportal.karate.coderef;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.intuit.karate.Results;
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
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class ExamplesCodeRefTest {
	private static final String TEST_FEATURE = "classpath:feature/examples.feature";
	private final String featureId = CommonUtils.namedId("feature_");
	private final List<String> exampleIds = Stream.generate(() -> CommonUtils.namedId("example_")).limit(2)
			.collect(Collectors.toList());
	private final List<Pair<String, List<String>>> stepIds = exampleIds.stream()
			.map(e -> Pair.of(e, Stream.generate(() -> CommonUtils.namedId("step_"))
					.limit(2).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private static final String EXAMPLE_CODE_REFERENCE_PATTERN =
			"feature/examples.feature/[EXAMPLE:Verify different maths[%s]]";
	private static final String FIRST_EXAMPLE_CODE_REFERENCE =
			String.format(EXAMPLE_CODE_REFERENCE_PATTERN, "result:4;vara:2;varb:2");
	private static final String SECOND_EXAMPLE_CODE_REFERENCE =
			String.format(EXAMPLE_CODE_REFERENCE_PATTERN, "result:3;vara:1;varb:2");

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, null, featureId, stepIds);
		mockBatchLogging(client);
	}

	@ParameterizedTest
	@ValueSource(booleans = {true, false})
	public void test_examples_code_reference(boolean report) {
		Results results;
		if (report) {
			results = TestUtils.runAsReport(rp, TEST_FEATURE);
		} else {
			results = TestUtils.runAsHook(rp, TEST_FEATURE);
		}
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(featureCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(featureId), scenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> firstStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(exampleIds.get(0)), firstStepCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> secondStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(exampleIds.get(1)), secondStepCaptor.capture());

		StartTestItemRQ featureRq = featureCaptor.getValue();
		assertThat(featureRq.getType(), allOf(notNullValue(), equalTo(ItemType.STORY.name())));

		List<StartTestItemRQ> scenarios = scenarioCaptor.getAllValues();
		StartTestItemRQ firstScenarioRq = scenarios.get(0);
		assertThat(firstScenarioRq.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
		assertThat(firstScenarioRq.getCodeRef(), allOf(notNullValue(), equalTo(FIRST_EXAMPLE_CODE_REFERENCE)));

		StartTestItemRQ secondScenarioRq = scenarios.get(1);
		assertThat(secondScenarioRq.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
		assertThat(secondScenarioRq.getCodeRef(), allOf(notNullValue(), equalTo(SECOND_EXAMPLE_CODE_REFERENCE)));

		Stream.concat(firstStepCaptor.getAllValues().stream(), secondStepCaptor.getAllValues().stream())
				.forEach(step -> {
					assertThat(step.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
					assertThat(step.isHasStats(), equalTo(Boolean.FALSE));
				});
	}
}
