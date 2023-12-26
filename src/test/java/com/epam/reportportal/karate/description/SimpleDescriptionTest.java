package com.epam.reportportal.karate.description;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.intuit.karate.core.Background;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
	private final String featureId = CommonUtils.namedId("feature_");
	private final String scenarioId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
			.limit(3).collect(Collectors.toList());
	private final List<Pair<String, String>> nestedStepIds = stepIds.stream()
			.map(id -> Pair.of(id, CommonUtils.namedId("nested_step_"))).collect(Collectors.toList());

	public static final String SCENARIO_DESCRIPTION = "This is my Scenario description.";

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, null, featureId, scenarioId, stepIds);
		mockNestedSteps(client, nestedStepIds);
		mockBatchLogging(client);
	}

	@Test
	public void test_description_for_all_possible_items() {
		var results = TestUtils.runAsReport(rp, "classpath:feature/description.feature");
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
