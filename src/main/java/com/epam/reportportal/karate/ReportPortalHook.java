/*
 * Copyright 2024 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.karate;

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.LogLevel;
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
import javax.annotation.Nullable;
import java.util.Calendar;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.epam.reportportal.karate.ReportPortalUtils.*;
import static com.epam.reportportal.utils.ParameterUtils.formatParametersAsTable;
import static com.epam.reportportal.utils.markdown.MarkdownUtils.formatDataTable;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * ReportPortal test results reporting hook for Karate. This class publish results in the process of test pass.
 */
public class ReportPortalHook implements RuntimeHook {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalHook.class);

	private final Map<String, Maybe<String>> featureIdMap = new ConcurrentHashMap<>();
	private final Map<String, Maybe<String>> scenarioIdMap = new ConcurrentHashMap<>();
	private final Map<String, Maybe<String>> backgroundIdMap = new ConcurrentHashMap<>();
	private final Map<String, Maybe<String>> stepIdMap = new ConcurrentHashMap<>();
	private final Map<Maybe<String>, Date> stepStartTimeMap = new ConcurrentHashMap<>();

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
		Maybe<String> featureId = launch.get().startTestItem(buildStartFeatureRq(fr));
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

	/**
	 * Build ReportPortal request for start Scenario event.
	 *
	 * @param sr Karate's ScenarioRuntime object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartScenarioRq(@Nonnull ScenarioRuntime sr) {
		return ReportPortalUtils.buildStartScenarioRq(sr.scenario);
	}

	@Override
	public boolean beforeScenario(ScenarioRuntime sr) {
		StartTestItemRQ rq = buildStartScenarioRq(sr);

		Maybe<String> scenarioId = launch.get()
				.startTestItem(featureIdMap.get(sr.featureRuntime.featureCall.feature.getNameForReport()), rq);
		scenarioIdMap.put(sr.scenario.getUniqueId(), scenarioId);
		return true;
	}

	/**
	 * Build ReportPortal request for finish Scenario event.
	 *
	 * @param sr Karate's ScenarioRuntime object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected FinishTestItemRQ buildFinishScenarioRq(@Nonnull ScenarioRuntime sr) {
		return buildFinishTestItemRq(Calendar.getInstance().getTime(),
				sr.result.getFailureMessageForDisplay() == null ? ItemStatus.PASSED : ItemStatus.FAILED);
	}

	/**
	 * Build ReportPortal request for start Background event.
	 *
	 * @param step Karate's Step object instance
	 * @param sr   Karate's ScenarioRuntime object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected StartTestItemRQ buildStartBackgroundRq(@Nonnull Step step, @Nonnull ScenarioRuntime sr) {
		return ReportPortalUtils.buildStartBackgroundRq(step, sr.scenario);
	}

	/**
	 * Start sending Background data to ReportPortal.
	 *
	 * @param step Karate's Step object instance
	 * @param sr   Karate's ScenarioRuntime object instance
	 */
	public Maybe<String> startBackground(@Nonnull Step step, @Nonnull ScenarioRuntime sr) {
		return backgroundIdMap.computeIfAbsent(sr.scenario.getUniqueId(), k -> {
			StartTestItemRQ backgroundRq = buildStartBackgroundRq(step, sr);
			return launch.get().startTestItem(scenarioIdMap.get(sr.scenario.getUniqueId()),
					backgroundRq);
		});
	}

	/**
	 * Build ReportPortal request for finish Background event.
	 *
	 * @param step Karate's Step object instance
	 * @param sr   Karate's ScenarioRuntime object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected FinishTestItemRQ buildFinishBackgroundRq(@Nullable Step step, @Nonnull ScenarioRuntime sr) {
		return buildFinishTestItemRq(Calendar.getInstance().getTime(), null);

	}

	/**
	 * Finish sending Scenario data to ReportPortal.
	 *
	 * @param step Karate's Step object instance
	 * @param sr   Karate's ScenarioRuntime object instance
	 */
	public void finishBackground(@Nullable Step step, @Nonnull ScenarioRuntime sr) {
		Maybe<String> backgroundId = backgroundIdMap.remove(sr.scenario.getUniqueId());
		if (backgroundId != null) {
			FinishTestItemRQ finishRq = buildFinishBackgroundRq(step, sr);
			//noinspection ReactiveStreamsUnusedPublisher
			launch.get().finishTestItem(backgroundId, finishRq);
		}
	}

	@Override
	public void afterScenario(ScenarioRuntime sr) {
		Maybe<String> scenarioId = scenarioIdMap.remove(sr.scenario.getUniqueId());
		if (scenarioId == null) {
			LOGGER.error("ERROR: Trying to finish unspecified scenario.");
		}

		FinishTestItemRQ rq = buildFinishScenarioRq(sr);
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(scenarioId, rq);
		finishBackground(null, sr);
	}

	/**
	 * Get step start time. To keep the steps order in case previous step startTime == current step startTime or
	 * previous step startTime > current step startTime.
	 *
	 * @param stepId step ID.
	 * @return step new startTime in Date format.
	 */
	@Nonnull
	private Date getStepStartTime(@Nullable Maybe<String> stepId) {
		Date currentStepStartTime = Calendar.getInstance().getTime();
		if (stepId == null || stepStartTimeMap.isEmpty()) {
			return currentStepStartTime;
		}
		Date lastStepStartTime = stepStartTimeMap.get(stepId);
		if (lastStepStartTime.compareTo(currentStepStartTime) >= 0) {
			currentStepStartTime.setTime(lastStepStartTime.getTime() + 1);
		}
		return currentStepStartTime;
	}

	/**
	 * Customize start Step test item event/request.
	 *
	 * @param step Karate's Step object instance
	 * @param sr   Karate's ScenarioRuntime object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartStepRq(@Nonnull Step step, @Nonnull ScenarioRuntime sr) {
		StartTestItemRQ rq = ReportPortalUtils.buildStartStepRq(step, sr.scenario);
		Maybe<String> stepId = stepIdMap.get(sr.scenario.getUniqueId());
		Date startTime = getStepStartTime(stepId);
		rq.setStartTime(startTime);
		return rq;
	}

	/**
	 * Send Step logs to ReportPortal.
	 *
	 * @param itemId  item ID future
	 * @param message log message to send
	 * @param level   log level
	 */
	protected void sendLog(Maybe<String> itemId, String message, LogLevel level) {
		ReportPortalUtils.sendLog(itemId, message, level);
	}

	@Override
	public boolean beforeStep(Step step, ScenarioRuntime sr) {
		boolean background = step.isBackground();
		Maybe<String> backgroundId = null;
		if (background) {
			backgroundId = startBackground(step, sr);
		} else {
			finishBackground(step, sr);
		}
		StartTestItemRQ stepRq = buildStartStepRq(step, sr);

		String scenarioId = sr.scenario.getUniqueId();
		Maybe<String> stepId = launch.get()
				.startTestItem(
						background ? backgroundId : scenarioIdMap.get(scenarioId),
						stepRq
				);
		stepStartTimeMap.put(stepId, stepRq.getStartTime());
		stepIdMap.put(scenarioId, stepId);
		ofNullable(stepRq.getParameters())
				.filter(params -> !params.isEmpty())
				.ifPresent(params ->
						sendLog(stepId, String.format(PARAMETERS_PATTERN, formatParametersAsTable(params)),
								LogLevel.INFO));
		ofNullable(step.getTable())
				.ifPresent(table ->
						sendLog(stepId, "Table:\n\n" + formatDataTable(table.getRows()), LogLevel.INFO));
		String docString = step.getDocString();
		if (isNotBlank(docString)) {
			sendLog(stepId, "Docstring:\n\n" + asMarkdownCode(step.getDocString()), LogLevel.INFO);
		}
		return true;
	}

	/**
	 * Send Step execution results to ReportPortal.
	 *
	 * @param stepResult step execution results
	 * @param sr         Karate's ScenarioRuntime object instance
	 */
	public void sendStepResults(StepResult stepResult, ScenarioRuntime sr) {
		Maybe<String> stepId = stepIdMap.get(sr.scenario.getUniqueId());
		Step step = stepResult.getStep();
		Result result = stepResult.getResult();
		if (result.isFailed()) {
			String fullErrorMessage = step.getPrefix() + " " + step.getText();
			String errorMessage = result.getErrorMessage();
			if (isNotBlank(errorMessage)) {
				fullErrorMessage = fullErrorMessage + "\n" + errorMessage;
			}
			sendLog(stepId, fullErrorMessage, LogLevel.ERROR);
		}
	}

	/**
	 * Build ReportPortal request for finish Step event.
	 *
	 * @param stepResult Karate's StepResult class instance
	 * @param sr         Karate's ScenarioRuntime object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected FinishTestItemRQ buildFinishStepRq(@Nonnull StepResult stepResult, @Nonnull ScenarioRuntime sr) {
		return buildFinishTestItemRq(Calendar.getInstance().getTime(), getStepStatus(stepResult.getResult().getStatus()));
	}

	@Override
	public void afterStep(StepResult stepResult, ScenarioRuntime sr) {
		sendStepResults(stepResult, sr);
		Maybe<String> stepId = stepIdMap.get(sr.scenario.getUniqueId());
		if (stepId == null) {
			LOGGER.error("ERROR: Trying to finish unspecified step.");
			return;
		}

		FinishTestItemRQ rq = buildFinishStepRq(stepResult, sr);
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(stepId, rq);
	}

	@Override
	public void beforeHttpCall(HttpRequest request, ScenarioRuntime sr) {
		// TODO: Implement better HTTP request logging later
		RuntimeHook.super.beforeHttpCall(request, sr);
	}

	@Override
	public void afterHttpCall(HttpRequest request, Response response, ScenarioRuntime sr) {
		// TODO: Implement better HTTP response logging later
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
