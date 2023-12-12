package com.epam.reportportal.karate.utils;

import com.epam.reportportal.karate.KarateReportPortalRunner;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.ta.reportportal.ws.model.BatchSaveOperatingRS;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.item.ItemCreatedRS;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;
import com.intuit.karate.Results;
import io.reactivex.Maybe;
import org.apache.commons.lang3.tuple.Pair;
import org.mockito.stubbing.Answer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.epam.reportportal.util.test.CommonUtils.generateUniqueId;
import static java.util.Optional.ofNullable;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

public class TestUtils {
	public static final String ROOT_SUITE_PREFIX = "root_";

	private TestUtils() {
	}

	public static ExecutorService testExecutor() {
		return Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r);
			t.setDaemon(true);
			return t;
		});
	}

	public static Results runAsReport(ReportPortal reportPortal, String... paths) {
		return KarateReportPortalRunner
				.path(paths)
				.withReportPortal(reportPortal)
				.outputCucumberJson(false)
				.parallel(1);
	}

	public static ListenerParameters standardParameters() {
		ListenerParameters result = new ListenerParameters();
		result.setClientJoin(false);
		result.setLaunchName("My-test-launch" + generateUniqueId());
		result.setProjectName("test-project");
		result.setEnable(true);
		result.setCallbackReportingEnabled(true);
		result.setBaseUrl("http://localhost:8080");
		return result;
	}

	public static void mockLaunch(@Nonnull final ReportPortalClient client, @Nullable final String launchUuid,
	                              @Nullable final String storyUuid, @Nonnull String testClassUuid,
	                              @Nonnull String stepUuid) {
		mockLaunch(client, launchUuid, storyUuid, testClassUuid, Collections.singleton(stepUuid));
	}

	public static void mockLaunch(@Nonnull final ReportPortalClient client, @Nullable final String launchUuid,
	                              @Nullable final String storyUuid, @Nonnull String testClassUuid,
	                              @Nonnull Collection<String> stepList) {
		mockLaunch(client, launchUuid, storyUuid, Collections.singletonList(Pair.of(testClassUuid, stepList)));
	}

	public static <T extends Collection<String>> void mockLaunch(
			@Nonnull final ReportPortalClient client, @Nullable final String launchUuid,
			@Nullable final String storyUuid, @Nonnull final Collection<Pair<String, T>> testSteps) {
		String launch = ofNullable(launchUuid).orElse(CommonUtils.namedId("launch_"));
		when(client.startLaunch(any())).thenReturn(Maybe.just(new StartLaunchRS(launch, 1L)));
		when(client.finishLaunch(eq(launch), any())).thenReturn(Maybe.just(new OperationCompletionRS()));

		mockFeature(client, storyUuid, testSteps);
	}

	public static <T extends Collection<String>> void mockFeature(
			@Nonnull final ReportPortalClient client, @Nullable final String storyUuid,
			@Nonnull final Collection<Pair<String, T>> testSteps) {
		String rootItemId = ofNullable(storyUuid).orElseGet(() -> CommonUtils.namedId(ROOT_SUITE_PREFIX));
		mockFeatures(client, Collections.singletonList(Pair.of(rootItemId, testSteps)));
	}

	@SuppressWarnings("unchecked")
	public static <T extends Collection<String>> void mockFeatures(
			@Nonnull final ReportPortalClient client,
			@Nonnull final List<Pair<String, Collection<Pair<String, T>>>> stories) {
		if (stories.isEmpty()) {
			return;
		}
		String firstStory = stories.get(0).getKey();
		Maybe<ItemCreatedRS> first = Maybe.just(new ItemCreatedRS(firstStory, firstStory));
		Maybe<ItemCreatedRS>[] other = (Maybe<ItemCreatedRS>[]) stories.subList(1, stories.size())
				.stream()
				.map(Pair::getKey)
				.map(s -> Maybe.just(new ItemCreatedRS(s, s)))
				.toArray(Maybe[]::new);
		when(client.startTestItem(any())).thenReturn(first, other);

		stories.forEach(i -> {
			Maybe<OperationCompletionRS> rootFinishMaybe = Maybe.just(new OperationCompletionRS());
			when(client.finishTestItem(same(i.getKey()), any())).thenReturn(rootFinishMaybe);
			mockScenario(client, i.getKey(), i.getValue());
		});
	}

	@SuppressWarnings("unchecked")
	public static <T extends Collection<String>> void mockScenario(
			@Nonnull final ReportPortalClient client, @Nonnull final String storyUuid,
			@Nonnull final Collection<Pair<String, T>> testSteps) {
		List<Maybe<ItemCreatedRS>> testResponses = testSteps.stream()
				.map(Pair::getKey)
				.map(uuid -> Maybe.just(new ItemCreatedRS(uuid, uuid)))
				.collect(Collectors.toList());

		Maybe<ItemCreatedRS> first = testResponses.get(0);
		Maybe<ItemCreatedRS>[] other = testResponses.subList(1, testResponses.size()).toArray(new Maybe[0]);
		when(client.startTestItem(same(storyUuid), any())).thenReturn(first, other);

		testSteps.forEach(test -> {
			String testClassUuid = test.getKey();
			List<Maybe<ItemCreatedRS>> stepResponses = test.getValue()
					.stream()
					.map(uuid -> Maybe.just(new ItemCreatedRS(uuid, uuid)))
					.collect(Collectors.toList());
			when(client.finishTestItem(same(testClassUuid), any())).thenReturn(Maybe.just(new OperationCompletionRS()));
			if (!stepResponses.isEmpty()) {
				Maybe<ItemCreatedRS> myFirst = stepResponses.get(0);
				Maybe<ItemCreatedRS>[] myOther = stepResponses.subList(1, stepResponses.size()).toArray(new Maybe[0]);
				when(client.startTestItem(same(testClassUuid), any())).thenReturn(myFirst, myOther);
				new HashSet<>(test.getValue()).forEach(testMethodUuid -> when(
						client.finishTestItem(same(testMethodUuid),
								any()
						)).thenReturn(Maybe.just(new OperationCompletionRS())));
			}
		});
	}

	@SuppressWarnings("unchecked")
	public static void mockBatchLogging(final ReportPortalClient client) {
		when(client.log(any(List.class))).thenReturn(Maybe.just(new BatchSaveOperatingRS()));
	}

	public static void mockNestedSteps(final ReportPortalClient client, final Pair<String, String> parentNestedPair) {
		mockNestedSteps(client, Collections.singletonList(parentNestedPair));
	}

	@SuppressWarnings("unchecked")
	public static void mockNestedSteps(final ReportPortalClient client, final List<Pair<String, String>> parentNestedPairs) {
		Map<String, List<String>> responseOrders = parentNestedPairs.stream()
				.collect(Collectors.groupingBy(Pair::getKey, Collectors.mapping(Pair::getValue, Collectors.toList())));
		responseOrders.forEach((k, v) -> {
			List<Maybe<ItemCreatedRS>> responses = v.stream()
					.map(uuid -> Maybe.just(new ItemCreatedRS(uuid, uuid)))
					.collect(Collectors.toList());

			Maybe<ItemCreatedRS> first = responses.get(0);
			Maybe<ItemCreatedRS>[] other = responses.subList(1, responses.size()).toArray(new Maybe[0]);
			when(client.startTestItem(eq(k), any())).thenReturn(first, other);
		});
		parentNestedPairs.forEach(p -> when(client.finishTestItem(same(p.getValue()),
				any()
		)).thenAnswer((Answer<Maybe<OperationCompletionRS>>) invocation -> Maybe.just(new OperationCompletionRS())));
	}
}
