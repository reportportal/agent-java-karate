package com.epam.reportportal.karate.description;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.reportportal.utils.markdown.MarkdownUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class NoDescriptionExamplesTest {
	private final String featureId = CommonUtils.namedId("feature_");
	private final List<String> exampleIds = Stream.generate(() -> CommonUtils.namedId("example_")).limit(2)
			.collect(Collectors.toList());
	private final List<Pair<String, List<String>>> stepIds = exampleIds.stream()
			.map(e -> Pair.of(e, Stream.generate(() -> CommonUtils.namedId("step_"))
					.limit(2).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private static final String EXAMPLE_PARAMETERS_DESCRIPTION_PATTERN =
			"Parameters:\n\n"
					+ MarkdownUtils.TABLE_INDENT
					+ "| vara | varb | result |\n"
					+ MarkdownUtils.TABLE_INDENT
					+ "|------|------|--------|\n"
					+ MarkdownUtils.TABLE_INDENT;
	private static final String FIRST_EXAMPLE_DESCRIPTION = EXAMPLE_PARAMETERS_DESCRIPTION_PATTERN
			+ "|  2   |  2   |   4    |";
	private static final String SECOND_EXAMPLE_DESCRIPTION = EXAMPLE_PARAMETERS_DESCRIPTION_PATTERN
			+ "|  1   |  2   |   3    |";

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, null, featureId, stepIds);
		mockBatchLogging(client);
	}

	@Test
	public void test_examples_no_description() {
		var results = TestUtils.runAsReport(rp, "classpath:feature/examples.feature");
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> featureCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(featureCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> scenarioCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(featureId), scenarioCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> firstStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(exampleIds.get(0)), firstStepCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> secondStepCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(exampleIds.get(1)), secondStepCaptor.capture());

		List<StartTestItemRQ> scenarios = scenarioCaptor.getAllValues();
		StartTestItemRQ firstScenarioRq = scenarios.get(0);
		assertThat(firstScenarioRq.getDescription(), allOf(notNullValue(), equalTo(FIRST_EXAMPLE_DESCRIPTION)));

		StartTestItemRQ secondScenarioRq = scenarios.get(1);
		assertThat(secondScenarioRq.getDescription(), allOf(notNullValue(), equalTo(SECOND_EXAMPLE_DESCRIPTION)));
	}
}
