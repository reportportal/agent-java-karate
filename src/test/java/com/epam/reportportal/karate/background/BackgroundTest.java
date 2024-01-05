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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.*;

public class BackgroundTest {
	private static final String TEST_FEATURE = "classpath:feature/background.feature";
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
		verify(client).startTestItem(same(featureId), captor.capture());
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(scenarioId), stepCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> nestedStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client).startTestItem(startsWith("step_"), nestedStepCaptor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		assertThat(items, hasSize(2));
		List<StartTestItemRQ> steps = stepCaptor.getAllValues();
		assertThat(steps, hasSize(3));

		List<StartTestItemRQ> backgroundSteps = steps.stream()
				.filter(s -> s.getName().startsWith(Background.KEYWORD)).collect(Collectors.toList());
		assertThat(backgroundSteps, hasSize(1));
		StartTestItemRQ backgroundStep = backgroundSteps.get(0);
		assertThat(backgroundStep.getName(), equalTo(Background.KEYWORD)); // No name for Background in Karate
		assertThat(backgroundStep.isHasStats(), equalTo(Boolean.FALSE));
		assertThat(backgroundStep.getStartTime(), notNullValue());
		assertThat(backgroundStep.getType(), equalTo(ItemType.STEP.name()));

		List<StartTestItemRQ> nestedSteps = nestedStepCaptor.getAllValues();
		assertThat(nestedSteps, hasSize(1));
		StartTestItemRQ nestedStep = nestedSteps.get(0);
		assertThat(nestedStep.getName(), equalTo("Given def four = 4"));
		assertThat(nestedStep.isHasStats(), equalTo(Boolean.FALSE));
	}
}
