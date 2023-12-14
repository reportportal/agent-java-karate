package com.epam.reportportal.karate.coderef;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import org.apache.commons.lang3.tuple.Pair;
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

public class ExamplesCodeRefTest {
	private final String featureId = CommonUtils.namedId("feature_");
	private final List<String> exampleIds = Stream.generate(() -> CommonUtils.namedId("example_")).limit(2)
			.collect(Collectors.toList());
	private final List<Pair<String, List<String>>> stepIds = exampleIds.stream()
			.map(e -> Pair.of(e, Stream.generate(() -> CommonUtils.namedId("step_"))
					.limit(2).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private static final String EXAMPLE_CODE_REFERENCE_PATTERN = "feature/examples.feature/[EXAMPLE:Verify different maths[%s]]";
	private static final String FIRST_EXAMPLE_CODE_REFERENCE = String.format(EXAMPLE_CODE_REFERENCE_PATTERN, "vara:2;varb:2;result:4");
	private static final String SECOND_EXAMPLE_CODE_REFERENCE = String.format(EXAMPLE_CODE_REFERENCE_PATTERN, "vara:1;varb:2;result:3");

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, null, featureId, stepIds);
		mockBatchLogging(client);
	}

	@Test
	public void test_examples_code_reference() {
		var results = TestUtils.runAsReport(rp, "classpath:feature/examples.feature");
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(captor.capture());
		verify(client, times(2)).startTestItem(same(featureId), captor.capture());
		verify(client, times(2)).startTestItem(same(exampleIds.get(0)), captor.capture());
		verify(client, times(2)).startTestItem(same(exampleIds.get(1)), captor.capture());

		List<StartTestItemRQ> items = captor.getAllValues();
		assertThat(items, hasSize(7));

		StartTestItemRQ featureRq = items.get(0);

		assertThat(featureRq.getType(), allOf(notNullValue(), equalTo(ItemType.STORY.name())));

		StartTestItemRQ firstScenarioRq = items.get(1);
		assertThat(firstScenarioRq.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
		assertThat(firstScenarioRq.getCodeRef(), allOf(notNullValue(), equalTo(FIRST_EXAMPLE_CODE_REFERENCE)));

		StartTestItemRQ secondScenarioRq = items.get(2);
		assertThat(secondScenarioRq.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
		assertThat(secondScenarioRq.getCodeRef(), allOf(notNullValue(), equalTo(SECOND_EXAMPLE_CODE_REFERENCE)));

		List<StartTestItemRQ> stepRqs = items.subList(3, items.size());
		IntStream.range(0, stepRqs.size()).forEach(i -> {
			StartTestItemRQ step = stepRqs.get(i);
			assertThat(step.getType(), allOf(notNullValue(), equalTo(ItemType.STEP.name())));
			assertThat(step.isHasStats(), equalTo(Boolean.FALSE));
		});
	}
}
