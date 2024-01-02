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
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.utils.MemoizingSupplier;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.intuit.karate.core.*;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;

import static com.epam.reportportal.karate.ReportPortalUtils.*;
import static com.epam.reportportal.utils.ParameterUtils.formatParametersAsTable;
import static com.epam.reportportal.utils.markdown.MarkdownUtils.formatDataTable;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ReportPortalPublisher {
	public static final String MARKDOWN_CODE_PATTERN = "```\n%s\n```";

	private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalPublisher.class);
	private final Map<String, Maybe<String>> featureIdMap = new HashMap<>();
	private final Map<String, Maybe<String>> scenarioIdMap = new HashMap<>();
	private final Map<Maybe<String>, Long> stepStartTimeMap = new HashMap<>();
	private Maybe<String> backgroundId;
	private Maybe<String> stepId;

	protected final MemoizingSupplier<Launch> launch;

	private Thread shutDownHook;

	/**
	 * Customize start launch event/request
	 *
	 * @param parameters Launch configuration parameters
	 * @return request to ReportPortal
	 */
	protected StartLaunchRQ buildStartLaunchRq(ListenerParameters parameters) {
		return ReportPortalUtils.buildStartLaunchRq(parameters);
	}

	public ReportPortalPublisher(ReportPortal reportPortal) {
		launch = new MemoizingSupplier<>(() -> {
			ListenerParameters params = reportPortal.getParameters();
			StartLaunchRQ rq = buildStartLaunchRq(params);
			Launch newLaunch = reportPortal.newLaunch(rq);
			shutDownHook = registerShutdownHook(() -> newLaunch);
			return newLaunch;
		});
	}

	public ReportPortalPublisher(Supplier<Launch> launchSupplier) {
		launch = new MemoizingSupplier<>(launchSupplier);
		shutDownHook = registerShutdownHook(launch);
	}

	/**
	 * Start sending Launch data to ReportPortal.
	 */
	public void startLaunch() {
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().start();
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
		unregisterShutdownHook(shutDownHook);
	}

	/**
	 * Build ReportPortal request for start Feature event.
	 *
	 * @param featureResult Karate's FeatureResult object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartFeatureRq(@Nonnull FeatureResult featureResult) {
		return ReportPortalUtils.buildStartFeatureRq(featureResult.getFeature());
	}

	/**
	 * Start sending Feature data to ReportPortal.
	 *
	 * @param featureResult feature result
	 */
	public void startFeature(@Nonnull FeatureResult featureResult) {
		StartTestItemRQ rq = buildStartFeatureRq(featureResult);
		Maybe<String> featureId = launch.get().startTestItem(rq);
		featureIdMap.put(featureResult.getCallNameForReport(), featureId);
	}

	/**
	 * Build ReportPortal request for finish Feature event.
	 *
	 * @param featureResult Karate's FeatureResult object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected FinishTestItemRQ buildFinishFeatureRq(@Nonnull FeatureResult featureResult) {
		return buildFinishTestItemRq(Calendar.getInstance().getTime(),
				featureResult.isFailed() ? ItemStatus.FAILED : ItemStatus.PASSED);
	}

	/**
	 * Finish sending Feature data to ReportPortal.
	 *
	 * @param featureResult feature result
	 */
	public void finishFeature(FeatureResult featureResult) {
		if (!featureIdMap.containsKey(featureResult.getCallNameForReport())) {
			LOGGER.error("ERROR: Trying to finish unspecified feature.");
		}

		for (ScenarioResult scenarioResult : featureResult.getScenarioResults()) {
			startScenario(scenarioResult, featureResult);
			List<StepResult> stepResults = scenarioResult.getStepResults();

			for (StepResult stepResult : stepResults) {
				startStep(stepResult, scenarioResult);
				sendStepResults(stepResult);
				finishStep(stepResult, scenarioResult);
			}

			stepStartTimeMap.clear();
			finishScenario(scenarioResult);
		}

		FinishTestItemRQ rq = buildFinishFeatureRq(featureResult);
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(featureIdMap.remove(featureResult.getCallNameForReport()), rq);
	}

	/**
	 * Build ReportPortal request for start Scenario event.
	 *
	 * @param scenarioResult Karate's ScenarioResult object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartScenarioRq(@Nonnull ScenarioResult scenarioResult) {
		return ReportPortalUtils.buildStartScenarioRq(scenarioResult.getScenario());
	}

	/**
	 * Start sending Scenario data to ReportPortal.
	 *
	 * @param scenarioResult scenario result
	 * @param featureResult  feature result
	 */
	public void startScenario(ScenarioResult scenarioResult, FeatureResult featureResult) {
		StartTestItemRQ rq = buildStartScenarioRq(scenarioResult);

		Maybe<String> scenarioId = launch.get().startTestItem(featureIdMap.get(featureResult.getCallNameForReport()), rq);
		scenarioIdMap.put(scenarioResult.getScenario().getName(), scenarioId);
	}

	/**
	 * Build ReportPortal request for finish Scenario event.
	 *
	 * @param scenarioResult Karate's ScenarioResult object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected FinishTestItemRQ buildFinishScenarioRq(@Nonnull ScenarioResult scenarioResult) {
		return buildFinishTestItemRq(Calendar.getInstance().getTime(),
				scenarioResult.getFailureMessageForDisplay() == null ? ItemStatus.PASSED : ItemStatus.FAILED);

	}

	/**
	 * Finish sending Scenario data to ReportPortal.
	 *
	 * @param scenarioResult scenario result
	 */
	public void finishScenario(ScenarioResult scenarioResult) {
		if (!scenarioIdMap.containsKey(scenarioResult.getScenario().getName())) {
			LOGGER.error("ERROR: Trying to finish unspecified scenario.");
		}

		FinishTestItemRQ rq = buildFinishScenarioRq(scenarioResult);
		Maybe<String> removedScenarioId = scenarioIdMap.remove(scenarioResult.getScenario().getName());
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(removedScenarioId, rq);
		finishBackground(null, scenarioResult);
	}

	/**
	 * Build ReportPortal request for start Background event.
	 *
	 * @param stepResult     Karate's StepResult object instance
	 * @param scenarioResult Karate's ScenarioResult object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected StartTestItemRQ buildStartBackgroundRq(@Nonnull StepResult stepResult, @Nonnull ScenarioResult scenarioResult) {
		return ReportPortalUtils.buildStartBackgroundRq(stepResult.getStep(), scenarioResult.getScenario());
	}

	/**
	 * Start sending Background data to ReportPortal.
	 *
	 * @param stepResult     step result
	 * @param scenarioResult scenario result
	 */
	public void startBackground(@Nonnull StepResult stepResult, @Nonnull ScenarioResult scenarioResult) {
		backgroundId = ofNullable(backgroundId).orElseGet(() -> {
			StartTestItemRQ backgroundRq = buildStartBackgroundRq(stepResult, scenarioResult);
			return launch.get().startTestItem(scenarioIdMap.get(scenarioResult.getScenario().getName()),
					backgroundRq);
		});
	}

	/**
	 * Build ReportPortal request for finish Background event.
	 *
	 * @param scenarioResult Karate's ScenarioResult object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected FinishTestItemRQ buildFinishBackgroundRq(@Nullable StepResult stepResult, @Nonnull ScenarioResult scenarioResult) {
		return buildFinishTestItemRq(Calendar.getInstance().getTime(), null);

	}

	/**
	 * Finish sending Scenario data to ReportPortal.
	 *
	 * @param stepResult     step result
	 * @param scenarioResult scenario result
	 */
	public void finishBackground(@Nullable StepResult stepResult, @Nonnull ScenarioResult scenarioResult) {
		ofNullable(backgroundId).ifPresent(id -> {
			FinishTestItemRQ finishRq = buildFinishBackgroundRq(stepResult, scenarioResult);
			//noinspection ReactiveStreamsUnusedPublisher
			launch.get().finishTestItem(id, finishRq);
		});
		backgroundId = null;
	}

	/**
	 * Get step start time. To keep the steps order in case previous step startTime == current step startTime or
	 * previous step startTime > current step startTime.
	 *
	 * @param stepId step ID.
	 * @return step new startTime in Date format.
	 */
	private Date getStepStartTime(@Nonnull Maybe<String> stepId) {
		long currentStepStartTime = Calendar.getInstance().getTime().getTime();

		if (!stepStartTimeMap.keySet().isEmpty()) {
			long lastStepStartTime = stepStartTimeMap.get(stepId);

			if (lastStepStartTime >= currentStepStartTime) {
				currentStepStartTime += (lastStepStartTime - currentStepStartTime) + 1;
			}
		}
		return new Date(currentStepStartTime);
	}

	/**
	 * Customize start Step test item event/request.
	 *
	 * @param stepResult     Karate's StepResult class instance
	 * @param scenarioResult Karate's ScenarioResult class instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartStepRq(@Nonnull StepResult stepResult, @Nonnull ScenarioResult scenarioResult) {
		StartTestItemRQ rq = ReportPortalUtils.buildStartStepRq(stepResult.getStep(), scenarioResult.getScenario());
		Date startTime = getStepStartTime(stepId);
		rq.setStartTime(startTime);
		return rq;
	}

	/**
	 * Start sending Step data to ReportPortal.
	 *
	 * @param stepResult     step result
	 * @param scenarioResult scenario result
	 */
	public void startStep(StepResult stepResult, ScenarioResult scenarioResult) {
		if (stepResult.getStep().isBackground()) {
			startBackground(stepResult, scenarioResult);
		} else {
			finishBackground(stepResult, scenarioResult);
		}
		StartTestItemRQ stepRq = buildStartStepRq(stepResult, scenarioResult);
		stepId = launch.get()
				.startTestItem(
						backgroundId != null ? backgroundId : scenarioIdMap.get(scenarioResult.getScenario().getName()),
						stepRq
				);
		stepStartTimeMap.put(stepId, stepRq.getStartTime().getTime());
		ofNullable(stepRq.getParameters())
				.filter(params -> !params.isEmpty())
				.ifPresent(params ->
						sendLog(stepId, String.format(PARAMETERS_PATTERN, formatParametersAsTable(params)),
								LogLevel.INFO));
		ofNullable(stepResult.getStep().getTable())
				.ifPresent(table ->
						sendLog(stepId, "Table:\n\n" + formatDataTable(table.getRows()), LogLevel.INFO));
	}

	/**
	 * Build ReportPortal request for finish Step event.
	 *
	 * @param stepResult     Karate's StepResult class instance
	 * @param scenarioResult Karate's ScenarioResult class instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected FinishTestItemRQ buildFinishStepRq(@Nonnull StepResult stepResult, @Nonnull ScenarioResult scenarioResult) {
		return buildFinishTestItemRq(Calendar.getInstance().getTime(), getStepStatus(stepResult.getResult().getStatus()));
	}

	/**
	 * Finish sending Step data to ReportPortal.
	 *
	 * @param stepResult     Karate's StepResult class instance
	 * @param scenarioResult Karate's ScenarioResult class instance
	 */
	public void finishStep(StepResult stepResult, ScenarioResult scenarioResult) {
		if (stepId == null) {
			LOGGER.error("ERROR: Trying to finish unspecified step.");
			return;
		}

		FinishTestItemRQ rq = buildFinishStepRq(stepResult, scenarioResult);
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(stepId, rq);
	}

	/**
	 * Send Step execution results to ReportPortal.
	 *
	 * @param stepResult step execution results
	 */
	public void sendStepResults(StepResult stepResult) {
		Step step = stepResult.getStep();
		String docString = step.getDocString();
		if (isNotBlank(docString)) {
			sendLog(stepId, "Docstring:\n\n" + String.format(MARKDOWN_CODE_PATTERN, step.getDocString()),
					LogLevel.INFO);
		}

		Result result = stepResult.getResult();
		String stepLog = stepResult.getStepLog();
		if (isNotBlank(stepLog)) {
			sendLog(stepId, stepLog, LogLevel.INFO);
		}
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
	 * Send Step logs to ReportPortal.
	 *
	 * @param itemId  item ID future
	 * @param message log message to send
	 * @param level   log level
	 */
	protected void sendLog(Maybe<String> itemId, String message, LogLevel level) {
		ReportPortal.emitLog(itemId, id -> {
			SaveLogRQ rq = new SaveLogRQ();
			rq.setMessage(message);
			rq.setItemUuid(id);
			rq.setLevel(level.name());
			rq.setLogTime(Calendar.getInstance().getTime());
			return rq;
		});
	}
}
