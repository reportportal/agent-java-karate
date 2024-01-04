/*
 *  Copyright 2023 EPAM Systems
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.epam.reportportal.karate;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.utils.MemoizingSupplier;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.intuit.karate.RuntimeHook;
import com.intuit.karate.Suite;
import com.intuit.karate.core.*;
import com.intuit.karate.http.HttpRequest;
import com.intuit.karate.http.Response;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.util.Calendar;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.epam.reportportal.karate.ReportPortalUtils.*;
import static com.epam.reportportal.utils.ParameterUtils.formatParametersAsTable;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ReportPortalHook implements RuntimeHook {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalHook.class);

	private final Map<String, Maybe<String>> featureIdMap = new ConcurrentHashMap<>();

	protected final MemoizingSupplier<Launch> launch;

	private volatile Thread shutDownHook;

	/**
	 * Customize start launch event/request
	 *
	 * @param parameters Launch configuration parameters
	 * @return request to ReportPortal
	 */
	protected StartLaunchRQ buildStartLaunchRq(ListenerParameters parameters) {
		return ReportPortalUtils.buildStartLaunchRq(parameters);
	}

	/**
	 * Customize start Launch finish event/request.
	 *
	 * @param parameters Launch configuration parameters
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected FinishExecutionRQ buildFinishLaunchRq(@Nonnull ListenerParameters parameters) {
		return ReportPortalUtils.buildFinishLaunchRq(parameters);
	}

	/**
	 * Finish sending Launch data to ReportPortal.
	 */
	public void finishLaunch() {
		Launch launchObject = launch.get();
		ListenerParameters parameters = launchObject.getParameters();
		FinishExecutionRQ rq = buildFinishLaunchRq(parameters);
		LOGGER.info("Launch URL: {}/ui/#{}/launches/all/{}", parameters.getBaseUrl(), parameters.getProjectName(),
				System.getProperty("rp.launch.id"));
		launchObject.finish(rq);
		if (Thread.currentThread() != shutDownHook) {
			unregisterShutdownHook(shutDownHook);
		}
	}

	public ReportPortalHook(ReportPortal reportPortal) {
		launch = new MemoizingSupplier<>(() -> {
			ListenerParameters params = reportPortal.getParameters();
			StartLaunchRQ rq = buildStartLaunchRq(params);
			Launch newLaunch = reportPortal.newLaunch(rq);
			//noinspection ReactiveStreamsUnusedPublisher
			newLaunch.start();
			shutDownHook = registerShutdownHook(this::finishLaunch);
			return newLaunch;
		});
	}

	public ReportPortalHook() {
		this(ReportPortal.builder().build());
	}

	public ReportPortalHook(Supplier<Launch> launchSupplier) {
		launch = new MemoizingSupplier<>(launchSupplier);
		shutDownHook = registerShutdownHook(this::finishLaunch);
	}

	/**
	 * Build ReportPortal request for start Feature event.
	 *
	 * @param fr Karate's FeatureRuntime object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unchecked")
	protected StartTestItemRQ buildStartFeatureRq(@Nonnull FeatureRuntime fr) {
		StartTestItemRQ rq = ReportPortalUtils.buildStartFeatureRq(fr.featureCall.feature);
		ofNullable(fr.caller).map(c -> c.arg).map(a -> (Map<String, Object>) a.getValue())
				.filter(args -> !args.isEmpty()).ifPresent(args -> {
					// TODO: cover with tests
					String parameters = String.format(PARAMETERS_PATTERN, formatParametersAsTable(getParameters(args)));
					String description = rq.getDescription();
					if (isNotBlank(description)) {
						rq.setDescription(String.format(MARKDOWN_DELIMITER_PATTERN, parameters, description));
					} else {
						rq.setDescription(parameters);
					}
				});
		return rq;
	}

	@Override
	public boolean beforeFeature(FeatureRuntime fr) {
		StartTestItemRQ rq = buildStartFeatureRq(fr);
		Maybe<String> featureId = launch.get().startTestItem(rq);
		Feature feature = fr.featureCall.feature;
		featureIdMap.put(feature.getNameForReport(), featureId);
		return true;
	}

	/**
	 * Build ReportPortal request for finish Feature event.
	 *
	 * @param fr Karate's FeatureRuntime object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected FinishTestItemRQ buildFinishFeatureRq(@Nonnull FeatureRuntime fr) {
		return buildFinishTestItemRq(Calendar.getInstance().getTime(),
				fr.result.isFailed() ? ItemStatus.FAILED : ItemStatus.PASSED);
	}

	@Override
	public void afterFeature(FeatureRuntime fr) {
		Feature feature = fr.featureCall.feature;
		Maybe<String> featureId = featureIdMap.remove(feature.getNameForReport());
		if (featureId == null) {
			LOGGER.error("ERROR: Trying to finish unspecified feature.");
		}
		FinishTestItemRQ rq = buildFinishFeatureRq(fr);
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(featureId, rq);
	}

	@Override
	public boolean beforeScenario(ScenarioRuntime sr) {
		return RuntimeHook.super.beforeScenario(sr);
	}

	@Override
	public void afterScenario(ScenarioRuntime sr) {
		RuntimeHook.super.afterScenario(sr);
	}

	@Override
	public boolean beforeStep(Step step, ScenarioRuntime sr) {
		return RuntimeHook.super.beforeStep(step, sr);
	}

	@Override
	public void afterStep(StepResult result, ScenarioRuntime sr) {
		RuntimeHook.super.afterStep(result, sr);
	}

	@Override
	public void beforeHttpCall(HttpRequest request, ScenarioRuntime sr) {
		RuntimeHook.super.beforeHttpCall(request, sr);
	}

	@Override
	public void afterHttpCall(HttpRequest request, Response response, ScenarioRuntime sr) {
		RuntimeHook.super.afterHttpCall(request, response, sr);
	}

	@Override
	public void beforeSuite(Suite suite) {
		// Omit Suite logic, since there is no Suite names in Karate
	}

	@Override
	public void afterSuite(Suite suite) {
		// Omit Suite logic, since there is no Suite names in Karate
	}
}
