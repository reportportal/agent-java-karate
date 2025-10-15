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
import com.epam.reportportal.utils.formatting.MarkdownUtils;
import com.epam.reportportal.utils.reflect.Accessible;
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
 * ReportPortal test results reporting hook for Karate. This class publish results in the process of test pass.
 */
public class ReportPortalHook implements RuntimeHook {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalHook.class);
	protected final MemoizingSupplier<Launch> launch;
	private final BlockingConcurrentHashMap<String, Supplier<Maybe<String>>> featureIdMap = new BlockingConcurrentHashMap<>();
	private final Map<String, Maybe<String>> scenarioIdMap = new ConcurrentHashMap<>();
	private final Map<String, Maybe<String>> backgroundIdMap = new ConcurrentHashMap<>();
	private final Map<String, ItemStatus> backgroundStatusMap = new ConcurrentHashMap<>();
	private final Map<String, Maybe<String>> stepIdMap = new ConcurrentHashMap<>();
	private final Map<String, Instant> stepStartTimeMap = new ConcurrentHashMap<>();
	private final Set<Maybe<String>> innerFeatures = Collections.newSetFromMap(new ConcurrentHashMap<>());
	private volatile Thread shutDownHook;

	/**
	 * Create a new instance of the ReportPortalHook with the specified ReportPortal instance.
	 *
	 * @param reportPortal the ReportPortal instance
	 */
	public ReportPortalHook(ReportPortal reportPortal) {
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
	 * Default constructor. Create a new instance of the ReportPortalHook with default ReportPortal instance.
	 */
	@SuppressWarnings("unused")
	public ReportPortalHook() {
		this(ReportPortal.builder().build());
	}

	@SuppressWarnings("unused")
	public ReportPortalHook(Supplier<Launch> launchSupplier) {
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
		ReportPortalUtils.doFinishLaunch(launch.get(), buildFinishLaunchRq(launch.get().getParameters()), shutDownHook);
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
		ofNullable(fr.caller).map(c -> c.arg)
				.map(a -> (Map<String, Object>) a.getValue())
				.filter(args -> !args.isEmpty())
				.ifPresent(args -> {
					// TODO: cover with tests
					String parameters = String.format(PARAMETERS_PATTERN, formatParametersAsTable(getParameters(args)));
					String description = rq.getDescription();
					if (isNotBlank(description)) {
						rq.setDescription(MarkdownUtils.asTwoParts(parameters, description));
					} else {
						rq.setDescription(parameters);
					}
				});
		return rq;
	}

	private String getFeatureNameForReport(FeatureRuntime fr) {
		int callDepth = ofNullable(fr.caller).map(c -> c.depth).orElse(0);
		return callDepth + ":" + fr.featureCall.feature.getNameForReport();
	}

	@Override
	public boolean beforeFeature(FeatureRuntime fr) {
		StartTestItemRQ rq = buildStartFeatureRq(fr);
		featureIdMap.computeIfAbsent(
				getFeatureNameForReport(fr), f -> new MemoizingSupplier<>(() -> {
					if (ofNullable(fr.caller).map(c -> c.depth).orElse(0) == 0) {
						return launch.get().startTestItem(rq);
					} else {
						Maybe<String> scenarioId = scenarioIdMap.get(fr.caller.parentRuntime.scenario.getUniqueId());
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
		return buildFinishTestItemRq(Instant.now(), fr.result.isFailed() ? ItemStatus.FAILED : ItemStatus.PASSED);
	}

	@Override
	public void afterFeature(FeatureRuntime fr) {
		Optional<Maybe<String>> optionalId = ofNullable(featureIdMap.get(getFeatureNameForReport(fr))).map(Supplier::get);
		if (optionalId.isEmpty()) {
			LOGGER.error("ERROR: Trying to finish unspecified feature.");
		}
		optionalId.ifPresent(featureId -> {
			//noinspection ReactiveStreamsUnusedPublisher
			launch.get().finishTestItem(featureId, buildFinishFeatureRq(fr));
			innerFeatures.remove(featureId);
		});
	}

	/**
	 * Build ReportPortal request for start Scenario event.
	 *
	 * @param sr Karate's ScenarioRuntime object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartScenarioRq(@Nonnull ScenarioRuntime sr) {
		StartTestItemRQ rq = ReportPortalUtils.buildStartScenarioRq(sr.result);
		ofNullable(featureIdMap.get(getFeatureNameForReport(sr.featureRuntime))).map(Supplier::get)
				.map(featureId -> innerFeatures.contains(featureId) ? featureId : null)
				.ifPresent(featureId -> {
					rq.setType(ItemType.STEP.name());
					rq.setHasStats(false);
					rq.setName(getInnerScenarioName(rq.getName()));
				});
		return rq;
	}

	@Override
	public boolean beforeScenario(ScenarioRuntime sr) {
		StartTestItemRQ rq = buildStartScenarioRq(sr);
		Optional<Maybe<String>> optionalId = ofNullable(featureIdMap.get(getFeatureNameForReport(sr.featureRuntime))).map(Supplier::get);
		if (optionalId.isEmpty()) {
			LOGGER.error("ERROR: Trying to post unspecified feature.");
		}
		ofNullable(scenarioIdMap.get(sr.scenario.getUniqueId())).map(Maybe::blockingGet).ifPresent(id -> {
			rq.setRetry(true);
			rq.setRetryOf(id);
		});
		optionalId.ifPresent(featureId -> {
			Maybe<String> scenarioId = launch.get().startTestItem(featureId, rq);
			if (innerFeatures.contains(featureId) && StringUtils.isNotBlank(rq.getDescription())) {
				ReportPortalUtils.sendLog(scenarioId, rq.getDescription(), LogLevel.INFO);
			}
			scenarioIdMap.put(sr.scenario.getUniqueId(), scenarioId);
		});
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
		return ReportPortalUtils.buildFinishScenarioRq(sr.result);
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
	 * @return item ID Future
	 */
	public Maybe<String> startBackground(@Nonnull Step step, @Nonnull ScenarioRuntime sr) {
		return backgroundIdMap.computeIfAbsent(
				sr.scenario.getUniqueId(), k -> {
					StartTestItemRQ backgroundRq = buildStartBackgroundRq(step, sr);
					return launch.get().startTestItem(scenarioIdMap.get(sr.scenario.getUniqueId()), backgroundRq);
				}
		);
	}

	/**
	 * Build ReportPortal request for finish Background event.
	 *
	 * @param stepResult Karate's StepResult class instance
	 * @param sr         Karate's ScenarioRuntime object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	protected FinishTestItemRQ buildFinishBackgroundRq(@Nullable StepResult stepResult, @Nonnull ScenarioRuntime sr) {
		return buildFinishTestItemRq(Instant.now(), backgroundStatusMap.remove(sr.scenario.getUniqueId()));
	}

	/**
	 * Finish sending Scenario data to ReportPortal.
	 *
	 * @param stepResult Karate's StepResult class instance
	 * @param sr         Karate's ScenarioRuntime object instance
	 */
	public void finishBackground(@Nullable StepResult stepResult, @Nonnull ScenarioRuntime sr) {
		String uniqueId = sr.scenario.getUniqueId();
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
	 * @param itemId item ID future
	 * @param embed  Karate's Embed object
	 */
	protected void embedAttachment(@Nonnull Maybe<String> itemId, @Nonnull Embed embed) {
		ReportPortalUtils.embedAttachment(itemId, embed);
	}

	/**
	 * Embed an attachment to ReportPortal.
	 *
	 * @param itemId           item ID future
	 * @param embeddedEntities a list of Karate's Embed object
	 */
	protected void embedAttachments(@Nonnull Maybe<String> itemId, @Nullable List<Embed> embeddedEntities) {
		ofNullable(embeddedEntities).ifPresent(embeds -> embeds.forEach(embed -> embedAttachment(itemId, embed)));
	}

	@Override
	public void afterScenario(ScenarioRuntime sr) {
		Maybe<String> scenarioId = scenarioIdMap.get(sr.scenario.getUniqueId());
		stepStartTimeMap.remove(sr.scenario.getUniqueId());
		finishBackground(null, sr);

		if (scenarioId == null) {
			LOGGER.error("ERROR: Trying to finish unspecified scenario.");
			return;
		}

		try {
			@SuppressWarnings("unchecked")
			List<Embed> embeddedEntities = (List<Embed>) new Accessible(sr).field("embeds").getValue();
			embedAttachments(scenarioId, embeddedEntities);
		} catch (Exception e) {
			LOGGER.warn(
					"Unable to retrieve scenario embeddings; attachments (such as screenshots or logs) will not be reported for this" //
							+ " scenario. Test execution and reporting will continue. Exception details:", e
			);
		}

		FinishTestItemRQ rq = buildFinishScenarioRq(sr);
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(scenarioId, rq);
	}

	/**
	 * Get step start time. To keep the steps order in case previous step startTime == current step startTime or
	 * previous step startTime > current step startTime.
	 *
	 * @param scenarioUniqueId Karate's Scenario Unique ID
	 * @return step new startTime in Instant format.
	 */
	@Nonnull
	private Instant getStepStartTime(@Nullable String scenarioUniqueId) {
		return ReportPortalUtils.getStepStartTime(scenarioUniqueId, stepStartTimeMap, launch.get().useMicroseconds());
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
		Instant startTime = getStepStartTime(sr.scenario.getUniqueId());
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
		}
		StartTestItemRQ stepRq = buildStartStepRq(step, sr);

		String scenarioId = sr.scenario.getUniqueId();
		Maybe<String> stepId = launch.get().startTestItem(background ? backgroundId : scenarioIdMap.get(scenarioId), stepRq);
		stepIdMap.put(scenarioId, stepId);
		ofNullable(stepRq.getParameters()).filter(params -> !params.isEmpty())
				.ifPresent(params -> sendLog(stepId, String.format(PARAMETERS_PATTERN, formatParametersAsTable(params)), LogLevel.INFO));
		ofNullable(step.getTable()).ifPresent(table -> sendLog(stepId, "Table:\n\n" + formatDataTable(table.getRows()), LogLevel.INFO));
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

		ofNullable(stepResult.getEmbeds()).ifPresent(embeds -> embeds.forEach(embed -> embedAttachment(stepId, embed)));

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
		return buildFinishTestItemRq(Instant.now(), getStepStatus(stepResult.getResult().getStatus()));
	}

	private void saveBackgroundStatus(@Nonnull StepResult stepResult, @Nonnull ScenarioRuntime sr) {
		backgroundStatusMap.put(
				sr.scenario.getUniqueId(),
				StatusEvaluation.evaluateStatus(
						backgroundStatusMap.get(sr.scenario.getUniqueId()),
						getStepStatus(stepResult.getResult().getStatus())
				)
		);
	}

	@Override
	public void afterStep(StepResult stepResult, ScenarioRuntime sr) {
		boolean background = stepResult.getStep().isBackground();
		if (!background) {
			finishBackground(stepResult, sr);
		}

		sendStepResults(stepResult, sr);
		Maybe<String> stepId = stepIdMap.remove(sr.scenario.getUniqueId());
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
