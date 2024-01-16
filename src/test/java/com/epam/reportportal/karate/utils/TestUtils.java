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

package com.epam.reportportal.karate.utils;

import com.epam.reportportal.karate.KarateReportPortalRunner;
import com.epam.reportportal.karate.ReportPortalHook;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.ReportPortalClient;
import com.epam.reportportal.util.test.CommonUtils;
import com.epam.reportportal.utils.http.HttpRequestUtils;
import com.epam.ta.reportportal.ws.model.BatchSaveOperatingRS;
import com.epam.ta.reportportal.ws.model.Constants;
import com.epam.ta.reportportal.ws.model.OperationCompletionRS;
import com.epam.ta.reportportal.ws.model.item.ItemCreatedRS;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRS;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.fasterxml.jackson.core.type.TypeReference;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;
import io.reactivex.Maybe;
import okhttp3.MultipartBody;
import okio.Buffer;
import org.apache.commons.lang3.tuple.Pair;
import org.mockito.stubbing.Answer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
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
		return KarateReportPortalRunner.path(paths).withReportPortal(reportPortal).outputCucumberJson(false).parallel(1);
	}

	public static Results runAsHook(ReportPortal reportPortal, String... paths) {
		return Runner.path(paths).hook(new ReportPortalHook(reportPortal)).outputCucumberJson(false).parallel(1);
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
			@Nullable final String featureUuid, @Nonnull String scenarioUuid, @Nonnull String stepUuid) {
		mockLaunch(client, launchUuid, featureUuid, scenarioUuid, Collections.singleton(stepUuid));
	}

	public static void mockLaunch(@Nonnull final ReportPortalClient client, @Nullable final String launchUuid,
			@Nullable final String featureUuid, @Nonnull String scenarioUuid, @Nonnull Collection<String> stepList) {
		mockLaunch(client, launchUuid, featureUuid, Collections.singletonList(Pair.of(scenarioUuid, stepList)));
	}

	public static <T extends Collection<String>> void mockLaunch(@Nonnull final ReportPortalClient client,
			@Nullable final String launchUuid, @Nullable final String featureUuid,
			@Nonnull final Collection<Pair<String, T>> scenarioSteps) {
		String launch = ofNullable(launchUuid).orElse(CommonUtils.namedId("launch_"));
		when(client.startLaunch(any())).thenReturn(Maybe.just(new StartLaunchRS(launch, 1L)));
		when(client.finishLaunch(eq(launch), any())).thenReturn(Maybe.just(new OperationCompletionRS()));

		mockFeature(client, featureUuid, scenarioSteps);
	}

	public static void mockLaunch(@Nonnull final ReportPortalClient client, @Nullable final String launchUuid) {
		String launch = ofNullable(launchUuid).orElse(CommonUtils.namedId("launch_"));
		when(client.startLaunch(any())).thenReturn(Maybe.just(new StartLaunchRS(launch, 1L)));
		when(client.finishLaunch(eq(launch), any())).thenReturn(Maybe.just(new OperationCompletionRS()));
	}

	public static <T extends Collection<String>> void mockFeature(@Nonnull final ReportPortalClient client,
			@Nullable final String featureUuid, @Nonnull final Collection<Pair<String, T>> scenarioSteps) {
		String rootItemId = ofNullable(featureUuid).orElseGet(() -> CommonUtils.namedId(ROOT_SUITE_PREFIX));
		mockFeatures(client, Collections.singletonList(Pair.of(rootItemId, scenarioSteps)));
	}

	@SuppressWarnings("unchecked")
	public static <T extends Collection<String>> void mockFeatures(@Nonnull final ReportPortalClient client,
			@Nonnull final List<Pair<String, Collection<Pair<String, T>>>> features) {
		if (features.isEmpty()) {
			return;
		}
		String firstFeature = features.get(0).getKey();
		Maybe<ItemCreatedRS> first = Maybe.just(new ItemCreatedRS(firstFeature, firstFeature));
		Maybe<ItemCreatedRS>[] other = (Maybe<ItemCreatedRS>[]) features.subList(1, features.size())
				.stream()
				.map(Pair::getKey)
				.map(s -> Maybe.just(new ItemCreatedRS(s, s)))
				.toArray(Maybe[]::new);
		when(client.startTestItem(any())).thenReturn(first, other);

		features.forEach(i -> {
			Maybe<OperationCompletionRS> rootFinishMaybe = Maybe.just(new OperationCompletionRS());
			when(client.finishTestItem(same(i.getKey()), any())).thenReturn(rootFinishMaybe);
			mockScenario(client, i.getKey(), i.getValue());
		});
	}

	@SuppressWarnings("unchecked")
	public static <T extends Collection<String>> void mockScenario(@Nonnull final ReportPortalClient client,
			@Nonnull final String featureUuid, @Nonnull final Collection<Pair<String, T>> scenarioSteps) {
		List<Maybe<ItemCreatedRS>> testResponses = scenarioSteps.stream()
				.map(Pair::getKey)
				.map(uuid -> Maybe.just(new ItemCreatedRS(uuid, uuid)))
				.collect(Collectors.toList());

		Maybe<ItemCreatedRS> first = testResponses.get(0);
		Maybe<ItemCreatedRS>[] other = testResponses.subList(1, testResponses.size()).toArray(new Maybe[0]);
		when(client.startTestItem(same(featureUuid), any())).thenReturn(first, other);

		scenarioSteps.forEach(test -> {
			String scenarioUuid = test.getKey();
			List<Maybe<ItemCreatedRS>> stepResponses = test.getValue()
					.stream()
					.map(uuid -> Maybe.just(new ItemCreatedRS(uuid, uuid)))
					.collect(Collectors.toList());
			when(client.finishTestItem(same(scenarioUuid), any())).thenReturn(Maybe.just(new OperationCompletionRS()));
			if (!stepResponses.isEmpty()) {
				Maybe<ItemCreatedRS> myFirst = stepResponses.get(0);
				Maybe<ItemCreatedRS>[] myOther = stepResponses.subList(1, stepResponses.size()).toArray(new Maybe[0]);
				when(client.startTestItem(same(scenarioUuid), any())).thenReturn(myFirst, myOther);
				new HashSet<>(test.getValue()).forEach(testMethodUuid -> when(client.finishTestItem(same(testMethodUuid),
						any()
				)).thenReturn(Maybe.just(new OperationCompletionRS())));
			}
		});
	}

	@SuppressWarnings("unchecked")
	public static void mockBatchLogging(final ReportPortalClient client) {
		when(client.log(any(List.class))).thenReturn(Maybe.just(new BatchSaveOperatingRS()));
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

	public static List<SaveLogRQ> extractJsonParts(List<MultipartBody.Part> parts) {
		return parts.stream()
				.filter(p -> ofNullable(p.headers()).map(headers -> headers.get("Content-Disposition"))
						.map(h -> h.contains(Constants.LOG_REQUEST_JSON_PART))
						.orElse(false))
				.map(MultipartBody.Part::body)
				.map(b -> {
					Buffer buf = new Buffer();
					try {
						b.writeTo(buf);
					} catch (IOException ignore) {
					}
					return buf.readByteArray();
				})
				.map(b -> {
					try {
						return HttpRequestUtils.MAPPER.readValue(b, new TypeReference<>() {
						});
					} catch (IOException e) {
						return Collections.<SaveLogRQ>emptyList();
					}
				})
				.flatMap(Collection::stream)
				.collect(Collectors.toList());
	}
}
