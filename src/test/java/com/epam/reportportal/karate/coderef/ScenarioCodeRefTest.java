package com.epam.reportportal.karate.coderef;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class ScenarioCodeRefTest {
	private final String featureId = CommonUtils.namedId("feature_");
	private final String scenarioId = CommonUtils.namedId("scenario_");
	private final List<String> stepIds = Stream.generate(() -> CommonUtils.namedId("step_"))
			.limit(3).collect(Collectors.toList());

	private static final String SIMPLE_CODE_REFERENCE = "feature/simple.feature/[SCENARIO:Verify math]";

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, null, featureId, scenarioId, stepIds);
		mockBatchLogging(client);
	}

	@Test
	public void test_scenario_code_reference() {
		var results = TestUtils.runAsReport(rp, "classpath:feature/simple.feature");
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(captor.capture());
		verify(client, times(1)).startTestItem(same(featureId), captor.capture());
		verify(client, times(3)).startTestItem(same(scenarioId), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		assertThat(items, hasSize(5));

		StartTestItemRQ featureRq = items.get(0);
		StartTestItemRQ scenarioRq = items.get(1);

		assertThat(featureRq.getType(), allOf(notNullValue(), equalTo(ItemType.STORY.name())));

		assertThat(scenarioRq.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
		assertThat(scenarioRq.getCodeRef(), allOf(notNullValue(), equalTo(SIMPLE_CODE_REFERENCE)));

		List<StartTestItemRQ> stepRqs = items.subList(2, items.size());
		IntStream.range(0, stepRqs.size()).forEach(i -> {
			StartTestItemRQ step = stepRqs.get(i);
			assertThat(step.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
			assertThat(step.isHasStats(), equalTo(Boolean.FALSE));
		});
	}
}
