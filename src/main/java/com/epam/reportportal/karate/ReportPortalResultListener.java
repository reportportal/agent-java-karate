/*
 * Copyright 2026 EPAM Systems
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

package com.epam.reportportal.karate;

import com.epam.reportportal.karate.utils.BlockingConcurrentHashMap;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.utils.MemoizingSupplier;
import com.epam.reportportal.utils.StatusEvaluation;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import io.karatelabs.core.*;
import io.karatelabs.gherkin.Feature;
import io.karatelabs.gherkin.Scenario;
import io.karatelabs.gherkin.Step;
import io.karatelabs.output.ResultListener;
import io.reactivex.Maybe;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.epam.reportportal.karate.ReportPortalUtils.*;
import static com.epam.reportportal.utils.ParameterUtils.formatParametersAsTable;
import static com.epam.reportportal.utils.formatting.MarkdownUtils.formatDataTable;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * ReportPortal result listener for Karate that reports completed execution output.
 */
public class ReportPortalResultListener implements ResultListener {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalResultListener.class);
	/**
	 * Lazily initialized ReportPortal launch facade used for all item operations.
	 */
	protected final MemoizingSupplier<Launch> launch;
	private final BlockingConcurrentHashMap<String, MemoizingSupplier<Maybe<String>>> featureIdMap = new BlockingConcurrentHashMap<>();
	private final Map<String, Maybe<String>> scenarioIdMap = new ConcurrentHashMap<>();
	private final Map<String, Instant> stepStartTimeMap = new HashMap<>();
	private final Map<String, Maybe<String>> backgroundIdMap = new ConcurrentHashMap<>();
	private final Map<String, ItemStatus> backgroundStatusMap = new ConcurrentHashMap<>();
	private final Map<String, Maybe<String>> stepIdMap = new ConcurrentHashMap<>();
	private final Set<Maybe<String>> innerFeatures = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private final ThreadLocal<Deque<Scenario>> parentScenarios = ThreadLocal.withInitial(LinkedList::new);
	private volatile Thread shutDownHook;

	/**
	 * Creates a listener instance backed by the provided ReportPortal client.
	 *
	 * @param reportPortal ReportPortal client instance
	 */
	public ReportPortalResultListener(ReportPortal reportPortal) {
		ListenerParameters params = reportPortal.getParameters();
		StartLaunchRQ rq = buildStartLaunchRq(params);
		launch = new MemoizingSupplier<>(() -> {
			Launch newLaunch = reportPortal.newLaunch(rq);
			//noinspection ReactiveStreamsUnusedPublisher
			newLaunch.start();
			shutDownHook = registerShutdownHook(this::finishLaunch);
			return newLaunch;
		});
	}

	/**
	 * Creates a listener with a default ReportPortal client configuration.
	 */
	@SuppressWarnings("unused")
	public ReportPortalResultListener() {
		this(ReportPortal.builder().build());
	}

	/**
	 * Creates a listener with a preconfigured launch supplier.
	 *
	 * @param launchSupplier launch supplier used to lazily initialize launch interactions
	 */
	@SuppressWarnings("unused")
	public ReportPortalResultListener(Supplier<Launch> launchSupplier) {
		launch = new MemoizingSupplier<>(launchSupplier);
	}

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
	 * Customizes the launch finish request.
	 *
	 * @param parameters launch configuration parameters
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
		ReportPortalUtils.doFinishLaunch(launch.get(), buildFinishLaunchRq(launch.get().getParameters()), shutDownHook);
	}

	/**
	 * Build ReportPortal request for start Feature event.
	 *
	 * @param fr Karate feature descriptor
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartFeatureRq(@Nonnull Feature fr) {
		return ReportPortalUtils.buildStartFeatureRq(fr);
	}

	/**
	 * Builds a unique feature key that includes parent scenario nesting depth.
	 *
	 * @param feature feature descriptor
	 * @return depth-qualified feature name
	 */
	private String getFeatureNameForReport(Feature feature) {
		return parentScenarios.get().size() + ":" + feature.getNameForReport();
	}

	/**
	 * Starts a feature item in ReportPortal.
	 *
	 * @param feature feature descriptor
	 */
	@Override
	public void onFeatureStart(Feature feature) {
		Scenario parentScenario = parentScenarios.get().peekLast();
		StartTestItemRQ rq = buildStartFeatureRq(feature);
		featureIdMap.computeIfAbsent(
				getFeatureNameForReport(feature), f -> new MemoizingSupplier<>(() -> {
					if (parentScenario == null) {
						return launch.get().startTestItem(rq);
					} else {
						Maybe<String> scenarioId = scenarioIdMap.get(parentScenario.getUniqueId());
						if (scenarioId == null) {
							LOGGER.error("ERROR: Trying to post unspecified scenario.");
							return launch.get().startTestItem(rq);
						}
						rq.setType(ItemType.STEP.name());
						rq.setHasStats(false);
						rq.setName(getInnerFeatureName(rq.getName()));
						Maybe<String> itemId = launch.get().startTestItem(scenarioId, rq);
						innerFeatures.add(itemId);
						if (StringUtils.isNotBlank(rq.getDescription())) {
							ReportPortalUtils.sendLog(itemId, rq.getDescription(), LogLevel.INFO, (Instant) rq.getStartTime());
						}
						return itemId;
					}
				})
		);
	}

	/**
	 * Build ReportPortal request for finish Feature event.
	 *
	 * @param fr Karate feature result
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected FinishTestItemRQ buildFinishFeatureRq(@Nonnull FeatureResult fr) {
		return buildFinishTestItemRq(Instant.now(), fr.isFailed() ? ItemStatus.FAILED : ItemStatus.PASSED);
	}

	/**
	 * Finishes a feature item in ReportPortal.
	 *
	 * @param fr feature result
	 */
	@Override
	public void onFeatureEnd(FeatureResult fr) {
		MemoizingSupplier<Maybe<String>> supplier = featureIdMap.get(getFeatureNameForReport(fr.getFeature()));
		if (supplier == null || !supplier.isInitialized()) {
			return;
		}
		Maybe<String> featureId = supplier.get();
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(featureId, buildFinishFeatureRq(fr));
		innerFeatures.remove(featureId);
	}

	/**
	 * Build ReportPortal request for start Scenario event.
	 *
	 * @param sr Karate scenario descriptor
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartScenarioRq(@Nonnull Scenario sr) {
		StartTestItemRQ rq = ReportPortalUtils.buildStartScenarioRq(sr);
		ofNullable(featureIdMap.get(getFeatureNameForReport(sr.getFeature()))).map(Supplier::get)
				.map(featureId -> innerFeatures.contains(featureId) ? featureId : null)
				.ifPresent(featureId -> {
					rq.setType(ItemType.STEP.name());
					rq.setHasStats(false);
					rq.setName(getInnerScenarioName(rq.getName()));
				});
		return rq;
	}

	/**
	 * Starts a scenario item in ReportPortal.
	 *
	 * @param scenario Karate scenario descriptor
	 */
	@Override
	public void onScenarioStart(Scenario scenario) {
		StartTestItemRQ rq = buildStartScenarioRq(scenario);
		Optional<Maybe<String>> optionalId = ofNullable(featureIdMap.get(getFeatureNameForReport(scenario.getFeature()))).map(Supplier::get);
		if (optionalId.isEmpty()) {
			LOGGER.error("ERROR: Trying to post unspecified feature.");
		}
		ofNullable(scenarioIdMap.get(scenario.getUniqueId())).map(Maybe::blockingGet).ifPresent(id -> {
			rq.setRetry(true);
			rq.setRetryOf(id);
		});
		optionalId.ifPresent(featureId -> {
			Maybe<String> scenarioId = launch.get().startTestItem(featureId, rq);
			if (innerFeatures.contains(featureId) && StringUtils.isNotBlank(rq.getDescription())) {
				ReportPortalUtils.sendLog(scenarioId, rq.getDescription(), LogLevel.INFO);
			}
			scenarioIdMap.put(scenario.getUniqueId(), scenarioId);
			parentScenarios.get().add(scenario);
		});
	}

	/**
	 * Build ReportPortal request for finish Scenario event.
	 *
	 * @param sr Karate scenario result
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected FinishTestItemRQ buildFinishScenarioRq(@Nonnull ScenarioResult sr) {
		return ReportPortalUtils.buildFinishScenarioRq(sr);
	}

	/**
	 * Build ReportPortal request for start Background event.
	 *
	 * @param startTime background start time
	 * @param step      Karate step descriptor
	 * @param sr        Karate scenario descriptor
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartBackgroundRq(Instant startTime, @Nonnull Step step, @Nonnull Scenario sr) {
		return ReportPortalUtils.buildStartBackgroundRq(startTime, step, sr);
	}

	/**
	 * Start sending Background data to ReportPortal.
	 *
	 * @param startTime background start time
	 * @param step      Karate step descriptor
	 * @param sr        Karate scenario descriptor
	 * @return item ID Future
	 */
	public Maybe<String> startBackground(Instant startTime, @Nonnull Step step, @Nonnull Scenario sr) {
		return backgroundIdMap.computeIfAbsent(
				sr.getUniqueId(), k -> {
					StartTestItemRQ backgroundRq = buildStartBackgroundRq(startTime, step, sr);
					return launch.get().startTestItem(scenarioIdMap.get(sr.getUniqueId()), backgroundRq);
				}
		);
	}

	/**
	 * Build ReportPortal request for finish Background event.
	 *
	 * @param stepResult Karate step result
	 * @param sr         Karate scenario result
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected FinishTestItemRQ buildFinishBackgroundRq(@Nullable StepResult stepResult, @Nonnull ScenarioResult sr) {
		long duration = ofNullable(stepResult).map(StepResult::getDurationNanos).orElse(0L);
		Instant startTime = ofNullable(stepResult).map(StepResult::getStartTime).map(Instant::ofEpochMilli).orElseGet(Instant::now);
		Instant endTime = startTime.plusNanos(duration);
		return buildFinishTestItemRq(endTime, backgroundStatusMap.remove(sr.getScenario().getUniqueId()));
	}

	/**
	 * Finishes the background item for a scenario, if it was started.
	 *
	 * @param stepResult step result that completed the background flow
	 * @param sr         scenario result
	 */
	public void finishBackground(@Nullable StepResult stepResult, @Nonnull ScenarioResult sr) {
		String uniqueId = sr.getScenario().getUniqueId();
		Maybe<String> backgroundId = backgroundIdMap.remove(uniqueId);
		if (backgroundId != null) {
			FinishTestItemRQ finishRq = buildFinishBackgroundRq(stepResult, sr);

			//noinspection ReactiveStreamsUnusedPublisher
			launch.get().finishTestItem(backgroundId, finishRq);
		}
	}

	/**
	 * Embed an attachment to ReportPortal.
	 *
	 * @param time   log time
	 * @param itemId item ID future
	 * @param embed  Karate's Embed object
	 */
	protected void embedAttachment(@Nonnull Instant time, @Nonnull Maybe<String> itemId, @Nonnull StepResult.Embed embed) {
		ReportPortalUtils.embedAttachment(time, itemId, embed);
	}

	/**
	 * Replays collected step results and finishes the scenario in ReportPortal.
	 *
	 * @param sr scenario result
	 */
	@Override
	public void onScenarioEnd(ScenarioResult sr) {
		String scenarioUniqueId = sr.getScenario().getUniqueId();
		Maybe<String> scenarioId = scenarioIdMap.get(scenarioUniqueId);
		stepStartTimeMap.remove(scenarioUniqueId);
		finishBackground(null, sr);

		if (scenarioId == null) {
			LOGGER.error("ERROR: Trying to finish unspecified scenario.");
			return;
		}

		sr.getStepResults().forEach(stepResult -> {
			beforeStep(stepResult, sr);
			afterStep(stepResult, sr);
		});

		FinishTestItemRQ rq = buildFinishScenarioRq(sr);
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(scenarioId, rq);
		parentScenarios.get().removeFirst();
	}

	/**
	 * Get step start time. To keep the steps order in case previous step startTime == current step startTime or
	 * previous step startTime > current step startTime.
	 *
	 * @param scenarioUniqueId Karate's Scenario Unique ID
	 * @return step new startTime in Instant format.
	 */
	@Nonnull
	private Instant getStepStartTime(@Nullable String scenarioUniqueId, Instant stepStartTime) {
		return ReportPortalUtils.getStepStartTime(scenarioUniqueId, stepStartTimeMap, stepStartTime, launch.get().useMicroseconds());
	}

	/**
	 * Customize start Step test item event/request.
	 *
	 * @param startTime step start time
	 * @param step      Karate step descriptor
	 * @param sr        Karate scenario descriptor
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartStepRq(Instant startTime, @Nonnull Step step, @Nonnull Scenario sr) {
		StartTestItemRQ rq = ReportPortalUtils.buildStartStepRq(step, sr);
		Instant newStartTime = getStepStartTime(sr.getUniqueId(), startTime);
		rq.setStartTime(newStartTime);
		return rq;
	}

	/**
	 * Send Step logs to ReportPortal.
	 *
	 * @param time    log time
	 * @param itemId  item ID future
	 * @param message log message to send
	 * @param level   log level
	 */
	protected void sendLog(Instant time, Maybe<String> itemId, String message, LogLevel level) {
		ReportPortalUtils.sendLog(itemId, message, level, time);
	}

	/**
	 * Starts a step item and emits step input details.
	 *
	 * @param stepResult step execution result
	 * @param sr         scenario result
	 */
	public void beforeStep(StepResult stepResult, ScenarioResult sr) {
		Step step = stepResult.getStep();
		Scenario scenario = sr.getScenario();
		Instant startTime = Instant.ofEpochMilli(stepResult.getStartTime());

		boolean background = step.isBackground();
		Maybe<String> backgroundId = null;
		if (background) {
			backgroundId = startBackground(startTime, step, scenario);
		}
		StartTestItemRQ stepRq = buildStartStepRq(startTime, step, scenario);

		String scenarioId = scenario.getUniqueId();
		Maybe<String> stepId = launch.get().startTestItem(background ? backgroundId : scenarioIdMap.get(scenarioId), stepRq);
		stepIdMap.put(scenarioId, stepId);
		ofNullable(stepRq.getParameters()).filter(params -> !params.isEmpty())
				.ifPresent(params -> sendLog(
						startTime.plusMillis(1),
						stepId,
						String.format(PARAMETERS_PATTERN, formatParametersAsTable(params)),
						LogLevel.INFO
				));
		ofNullable(step.getTable()).ifPresent(table -> sendLog(
				startTime.plusMillis(2),
				stepId,
				"Table:\n\n" + formatDataTable(table.getRows()),
				LogLevel.INFO
		));
		String docString = step.getDocString();
		if (isNotBlank(docString)) {
			sendLog(startTime.plusMillis(3), stepId, "Docstring:\n\n" + asMarkdownCode(docString), LogLevel.INFO);
		}
	}

	/**
	 * Send Step execution results to ReportPortal.
	 *
	 * @param stepResult step execution results
	 * @param sr         Karate scenario result
	 */
	public void sendStepResults(StepResult stepResult, ScenarioResult sr) {
		List<StepResult.Embed> embeds = ofNullable(stepResult.getEmbeds()).orElse(Collections.emptyList());
		String log = ofNullable(stepResult.getLog()).filter(logs -> !logs.isBlank()).orElse(null);

		Instant itemTime = Instant.ofEpochMilli(stepResult.getStartTime());
		long stepDuration = stepResult.getDurationNanos();
		int numberOfArtefacts = embeds.size() + (log != null ? 1 : 0) + (stepResult.isFailed() ? 1 : 0);
		int numberOfSteps = numberOfArtefacts + 1; // To always log a little bit after step start time
		long duration = stepDuration / numberOfSteps;

		Maybe<String> stepId = stepIdMap.get(sr.getScenario().getUniqueId());
		Step step = stepResult.getStep();
		for (var embed : embeds) {
			itemTime = itemTime.plusNanos(duration);
			embedAttachment(itemTime, stepId, embed);
		}

		if (log != null) {
			itemTime = itemTime.plusNanos(duration);
			sendLog(itemTime, stepId, stripConsoleColors(log), LogLevel.INFO);
		}

		if (stepResult.isFailed()) {
			String fullErrorMessage = step.getPrefix() + " " + step.getText();
			String errorMessage = stepResult.getErrorMessage();
			if (isNotBlank(errorMessage)) {
				fullErrorMessage = fullErrorMessage + "\n" + errorMessage;
			}
			itemTime = itemTime.plusNanos(duration);
			sendLog(itemTime, stepId, fullErrorMessage, LogLevel.ERROR);
		}
	}

	/**
	 * Build ReportPortal request for finish Step event.
	 *
	 * @param stepResult Karate step result
	 * @param sr         Karate scenario result
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected FinishTestItemRQ buildFinishStepRq(@Nonnull StepResult stepResult, @Nonnull ScenarioResult sr) {
		Instant endTime = Instant.ofEpochMilli(stepResult.getStartTime()).plusNanos(stepResult.getDurationNanos());
		return buildFinishTestItemRq(endTime, getStepStatus(stepResult.getStatus()));
	}

	/**
	 * Aggregates background status across background steps.
	 *
	 * @param stepResult background step result
	 * @param sr         scenario result
	 */
	private void saveBackgroundStatus(@Nonnull StepResult stepResult, @Nonnull ScenarioResult sr) {
		backgroundStatusMap.put(
				sr.getScenario().getUniqueId(),
				StatusEvaluation.evaluateStatus(
						backgroundStatusMap.get(sr.getScenario().getUniqueId()),
						getStepStatus(stepResult.getStatus())
				)
		);
	}

	/**
	 * Finishes a step item and sends corresponding step output.
	 *
	 * @param stepResult step execution result
	 * @param sr         scenario result
	 */
	public void afterStep(StepResult stepResult, ScenarioResult sr) {
		boolean background = stepResult.getStep().isBackground();
		if (!background) {
			finishBackground(stepResult, sr);
		}
		sendStepResults(stepResult, sr);
		Maybe<String> stepId = stepIdMap.remove(sr.getScenario().getUniqueId());
		if (stepId == null) {
			LOGGER.error("ERROR: Trying to finish unspecified step.");
			return;
		}

		FinishTestItemRQ rq = buildFinishStepRq(stepResult, sr);
		if (background) {
			saveBackgroundStatus(stepResult, sr);
		}
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(stepId, rq);
	}

	/**
	 * Initializes the launch at suite start.
	 *
	 * @param suite suite descriptor
	 */
	@Override
	public void onSuiteStart(Suite suite) {
		launch.get(); // Trigger launch start
	}

	/**
	 * Karate suite callback after execution.
	 *
	 * @param suite suite result
	 */
	@Override
	public void onSuiteEnd(SuiteResult suite) {
		// Omit Suite logic, since there is no Suite names in Karate
	}
}
