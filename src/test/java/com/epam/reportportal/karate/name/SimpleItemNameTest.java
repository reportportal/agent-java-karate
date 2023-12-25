package com.epam.reportportal.karate.name;

import com.epam.reportportal.karate.utils.TestUtils;
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

public class SimpleItemNameTest {
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

	@Test
	public void test_scenario_code_reference() {
		var results = TestUtils.runAsReport(rp, "classpath:feature/simple.feature");
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(featureCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(same(featureId), scenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> stepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(3)).startTestItem(same(scenarioId), stepCaptor.capture());

		StartTestItemRQ featureRq = featureCaptor.getValue();
		StartTestItemRQ scenarioRq = scenarioCaptor.getValue();

		assertThat(featureRq.getName(), allOf(notNullValue(), equalTo("the very basic test to run by Karate")));

		assertThat(scenarioRq.getName(), allOf(notNullValue(), equalTo("Verify math")));

		List<StartTestItemRQ> stepRqs = stepCaptor.getAllValues();
		String[] stepNames = new String[]{"Given def four = 4", "When def actualFour = 2 * 2",
				"Then assert actualFour == four"};
		IntStream.range(0, stepRqs.size()).forEach(i -> {
			StartTestItemRQ step = stepRqs.get(i);
			assertThat(step.getName(), allOf(notNullValue(), equalTo(stepNames[i])));
		});
	}
}
