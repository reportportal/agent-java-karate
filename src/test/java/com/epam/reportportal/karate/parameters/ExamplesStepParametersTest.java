package com.epam.reportportal.karate.parameters;

import com.epam.reportportal.karate.utils.TestUtils;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.utils.TestUtils.*;
import static java.util.stream.Stream.ofNullable;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.*;

public class ExamplesStepParametersTest {
	private final String featureId = CommonUtils.namedId("feature_");
	private final List<String> exampleIds = Stream.generate(() -> CommonUtils.namedId("example_")).limit(2)
			.collect(Collectors.toList());
	private final List<Pair<String, List<String>>> stepIds = exampleIds.stream()
			.map(e -> Pair.of(e, Stream.generate(() -> CommonUtils.namedId("step_"))
					.limit(2).collect(Collectors.toList())))
			.collect(Collectors.toList());

	private final ReportPortalClient client = mock(ReportPortalClient.class);
	private final ReportPortal rp = ReportPortal.create(client, standardParameters(), testExecutor());

	@BeforeEach
	public void setupMock() {
		mockLaunch(client, null, featureId, stepIds);
		mockBatchLogging(client);
	}

	private static Set<String> toParameterStringList(List<ParameterResource> parameters) {
		return ofNullable(parameters)
				.flatMap(Collection::stream)
				.map(p -> p.getKey() + ":" + p.getValue())
				.collect(Collectors.toSet());
	}

	@Test
	public void test_examples_parameters_for_steps() {
		var results = TestUtils.runAsReport(rp, "classpath:feature/examples.feature");
		assertThat(results.getFailCount(), equalTo(0));

		ArgumentCaptor<StartTestItemRQ> captor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(1)).startTestItem(captor.capture());
		verify(client, times(2)).startTestItem(same(featureId), captor.capture());
		ArgumentCaptor<StartTestItemRQ> firstExampleCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(exampleIds.get(0)), firstExampleCaptor.capture());
		ArgumentCaptor<StartTestItemRQ> secondExampleCaptor = ArgumentCaptor.forClass(StartTestItemRQ.class);
		verify(client, times(2)).startTestItem(same(exampleIds.get(1)), secondExampleCaptor.capture());

		List<StartTestItemRQ> firstSteps = firstExampleCaptor.getAllValues();
		Set<String> parameterStrings = toParameterStringList(firstSteps.get(0).getParameters());
		assertThat(parameterStrings, hasSize(2));
		assertThat(parameterStrings, allOf(hasItem("vara:2"), hasItem("varb:2")));
		parameterStrings = toParameterStringList(firstSteps.get(1).getParameters());
		assertThat(parameterStrings, hasSize(1));
		assertThat(parameterStrings, hasItem("result:4"));

		List<StartTestItemRQ> secondSteps = secondExampleCaptor.getAllValues();
		parameterStrings = toParameterStringList(secondSteps.get(0).getParameters());
		assertThat(parameterStrings, hasSize(2));
		assertThat(parameterStrings, allOf(hasItem("vara:1"), hasItem("varb:2")));
		parameterStrings = toParameterStringList(secondSteps.get(1).getParameters());
		assertThat(parameterStrings, hasSize(1));
		assertThat(parameterStrings, hasItem("result:3"));
	}
}
