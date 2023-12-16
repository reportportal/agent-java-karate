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

import com.epam.reportportal.karate.enums.ItemLogLevelEnum;
import com.epam.reportportal.karate.enums.ItemStatusEnum;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.utils.AttributeParser;
import com.epam.reportportal.utils.MemoizingSupplier;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.intuit.karate.core.*;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ReportPortalPublisher {
	public static final String SCENARIO_CODE_REFERENCE_PATTERN = "%s/[SCENARIO:%s]";
	public static final String EXAMPLE_CODE_REFERENCE_PATTERN = "%s/[EXAMPLE:%s%s]";

	private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalPublisher.class);
	private final ConcurrentHashMap<String, Maybe<String>> featureIdMap = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, Maybe<String>> scenarioIdMap = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Maybe<String>, Long> stepStartTimeMap = new ConcurrentHashMap<>();
	private Maybe<String> stepId;

	private final MemoizingSupplier<Launch> launch;

	private Thread shutDownHook;

	private static Thread getShutdownHook(final Supplier<Launch> launch) {
		return new Thread(() -> {
			FinishExecutionRQ rq = new FinishExecutionRQ();
			rq.setEndTime(Calendar.getInstance().getTime());
			launch.get().finish(rq);
		});
	}

	/**
	 * Customize start launch event/request
	 *
	 * @param parameters Launch configuration parameters
	 * @return request to ReportPortal
	 */
	protected StartLaunchRQ buildStartLaunchRq(ListenerParameters parameters) {
		StartLaunchRQ rq = new StartLaunchRQ();
		rq.setName(parameters.getLaunchName());
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setMode(parameters.getLaunchRunningMode());
		rq.setAttributes(new HashSet<>(parameters.getAttributes()));
		if (!isNullOrEmpty(parameters.getDescription())) {
			rq.setDescription(parameters.getDescription());
		}
		rq.setStartTime(Calendar.getInstance().getTime());
		rq.setRerun(parameters.isRerun());
		if (isNotBlank(parameters.getRerunOf())) {
			rq.setRerunOf(parameters.getRerunOf());
		}
		return rq;
	}

	public ReportPortalPublisher(ReportPortal reportPortal) {
		launch = new MemoizingSupplier<>(() -> {
			ListenerParameters params = reportPortal.getParameters();
			StartLaunchRQ rq = buildStartLaunchRq(params);
			rq.setStartTime(Calendar.getInstance().getTime());
			Launch newLaunch = reportPortal.newLaunch(rq);
			shutDownHook = getShutdownHook(() -> newLaunch);
			Runtime.getRuntime().addShutdownHook(shutDownHook);
			return newLaunch;
		});
	}

	public ReportPortalPublisher(Supplier<Launch> launchSupplier) {
		launch = new MemoizingSupplier<>(launchSupplier);
		shutDownHook = getShutdownHook(launch);
		Runtime.getRuntime().addShutdownHook(shutDownHook);
	}

	/**
	 * Starts launch instance
	 */
	public void startLaunch() {
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().start();
	}

	/**
	 * Finish launch
	 */
	public void finishLaunch() {
		FinishExecutionRQ rq = new FinishExecutionRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		ListenerParameters parameters = launch.get().getParameters();
		LOGGER.info("Launch URL: {}/ui/#{}/launches/all/{}", parameters.getBaseUrl(), parameters.getProjectName(),
				System.getProperty("rp.launch.id"));
		launch.get().finish(rq);
		Runtime.getRuntime().removeShutdownHook(shutDownHook);
	}

	/**
	 * Returns code reference for feature files by URI and Scenario reference
	 *
	 * @param scenario Karate's Scenario object instance
	 * @return a code reference
	 */
	@Nonnull
	protected String getCodeRef(@Nonnull Scenario scenario) {
		if (scenario.getExampleIndex() < 0) {
			return String.format(SCENARIO_CODE_REFERENCE_PATTERN, scenario.getFeature().getResource().getRelativePath(),
					scenario.getName());
		} else {
			return String.format(EXAMPLE_CODE_REFERENCE_PATTERN, scenario.getFeature().getResource().getRelativePath(),
					scenario.getName(), ReportPortalUtils.formatExampleKey(scenario.getExampleData()));
		}
	}

	/**
	 * Return a Test Case ID for a Scenario in a Feature file
	 *
	 * @param scenario Karate's Scenario object instance
	 * @return Test Case ID entity or null if it's not possible to calculate
	 */
	@Nullable
	protected TestCaseIdEntry getTestCaseId(@Nonnull Scenario scenario) {
		return TestCaseIdUtils.getTestCaseId(getCodeRef(scenario), null);
	}

	/**
	 * Customize start test item event/request
	 *
	 * @param name      item's name
	 * @param startTime item's start time in Date format
	 * @param type      item's type (e.g. feature, scenario, step, etc.)
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartTestItemRq(@Nonnull String name, @Nonnull Date startTime,
	                                               @Nonnull ItemType type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(name);
		rq.setStartTime(startTime);
		rq.setType(type.name());
		return rq;
	}

	/**
	 * Customize start test item event/request
	 *
	 * @param name      item's name
	 * @param startTime item's start time
	 * @param type      item's type (e.g. feature, scenario, step, etc.)
	 * @param hasStats  enables nested items
	 * @return request to ReportPortal
	 */
	protected StartTestItemRQ buildStartTestItemRq(@Nonnull String name, @Nonnull Date startTime,
	                                               @Nonnull ItemType type, boolean hasStats) {
		StartTestItemRQ rq = buildStartTestItemRq(name, startTime, type);
		rq.setHasStats(hasStats);
		return rq;
	}

	@Nullable
	private Set<ItemAttributesRQ> toAttributes(@Nullable List<Tag> tags) {
		Set<ItemAttributesRQ> attributes = ofNullable(tags).orElse(Collections.emptyList()).stream().flatMap(tag -> {
			if (tag.getValues().isEmpty()) {
				return Stream.of(new ItemAttributesRQ(null, tag.getName()));
			}
			return AttributeParser.createItemAttributes(tag.getName(), tag.getValues().toArray(new String[0])).stream();
		}).collect(Collectors.toSet());
		return attributes.isEmpty() ? null : attributes;
	}

	/**
	 * Build ReportPortal request for start Feature event.
	 *
	 * @param featureResult Karate's FeatureResult object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartFeatureRq(@Nonnull FeatureResult featureResult) {
		StartTestItemRQ rq = buildStartTestItemRq(String.valueOf(featureResult.toCucumberJson().get("name")),
				Calendar.getInstance().getTime(),
				ItemType.STORY);
		Feature feature = featureResult.getFeature();
		rq.setAttributes(toAttributes(feature.getTags()));
		return rq;
	}

	/**
	 * Build ReportPortal request for start Scenario event
	 *
	 * @param scenarioResult Karate's ScenarioResult object instance
	 * @return request to ReportPortal
	 */
	protected StartTestItemRQ buildStartScenarioRq(@Nonnull ScenarioResult scenarioResult) {
		StartTestItemRQ rq = buildStartTestItemRq(scenarioResult.getScenario().getName(),
				Calendar.getInstance().getTime(),
				ItemType.STEP);
		Scenario scenario = scenarioResult.getScenario();
		rq.setCodeRef(getCodeRef(scenario));
		rq.setTestCaseId(ofNullable(getTestCaseId(scenario)).map(TestCaseIdEntry::getId).orElse(null));
		rq.setAttributes(toAttributes(scenario.getTags()));
		return rq;
	}

	/**
	 * Customize start test item event/request
	 *
	 * @param endTime item's end time
	 * @param status  item's status
	 * @return request to ReportPortal
	 */
	protected FinishTestItemRQ buildFinishTestItemRq(@Nonnull Date endTime,
	                                                 @Nonnull String status) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(endTime);
		rq.setStatus(status);
		return rq;
	}

	/**
	 * Start sending feature data to ReportPortal.
	 *
	 * @param featureResult feature result
	 */
	public void startFeature(@Nonnull FeatureResult featureResult) {
		StartTestItemRQ rq = buildStartFeatureRq(featureResult);
		Maybe<String> featureId = launch.get().startTestItem(rq);
		featureIdMap.put(featureResult.getCallNameForReport(), featureId);
	}

	/**
	 * Finish sending feature data to ReportPortal
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
				finishStep(stepResult);
			}

			stepStartTimeMap.clear();
			finishScenario(scenarioResult);
		}

		FinishTestItemRQ rq = buildFinishTestItemRq(Calendar.getInstance().getTime(),
				featureResult.isFailed() ? ItemStatusEnum.FAILED.toString() : ItemStatusEnum.PASSED.toString());
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(featureIdMap.remove(featureResult.getCallNameForReport()), rq);
	}

	/**
	 * Start sending scenario data to ReportPortal
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
	 * Finish sending scenario data to ReportPortal
	 *
	 * @param scenarioResult scenario result
	 */
	public void finishScenario(ScenarioResult scenarioResult) {
		if (!scenarioIdMap.containsKey(scenarioResult.getScenario().getName())) {
			LOGGER.error("ERROR: Trying to finish unspecified scenario.");
		}

		FinishTestItemRQ rq = buildFinishTestItemRq(Calendar.getInstance().getTime(),
				scenarioResult.getFailureMessageForDisplay() == null ? ItemStatusEnum.PASSED.toString() : ItemStatusEnum.FAILED.toString());
		Maybe<String> removedScenarioId = scenarioIdMap.remove(scenarioResult.getScenario().getName());
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(removedScenarioId, rq);
	}

	/**
	 * Start sending step data to ReportPortal
	 *
	 * @param stepResult     step result
	 * @param scenarioResult scenario result
	 */
	public void startStep(StepResult stepResult, ScenarioResult scenarioResult) {
		String stepName = stepResult.getStep().getPrefix() + " " + stepResult.getStep().getText();
		StartTestItemRQ rq = buildStartTestItemRq(stepName, getStepStartTime(stepStartTimeMap, stepId), ItemType.STEP, false);
		stepId = launch.get().startTestItem(scenarioIdMap.get(scenarioResult.getScenario().getName()), rq);
		stepStartTimeMap.put(stepId, rq.getStartTime().getTime());
	}

	/**
	 * Finish sending scenario data to ReportPortal
	 *
	 * @param stepResult step result
	 */
	public void finishStep(StepResult stepResult) {
		if (stepId == null) {
			LOGGER.error("ERROR: Trying to finish unspecified step.");
			return;
		}

		FinishTestItemRQ rq = buildFinishTestItemRq(Calendar.getInstance().getTime(),
				getStepStatus(stepResult.getResult().getStatus()));
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(stepId, rq);
	}


	/**
	 * Send step execution results to ReportPortal
	 *
	 * @param stepResult step execution results
	 */
	public void sendStepResults(StepResult stepResult) {
		Result result = stepResult.getResult();
		String logLevel = getLogLevel(result.getStatus());
		Step step = stepResult.getStep();

		if (step.getDocString() != null) {
			sendLog("\n-----------------DOC_STRING-----------------\n" + step.getDocString(), logLevel);
		}

		if (stepResult.getStepLog() != null
				&& !stepResult.getStepLog().isEmpty()
				&& !stepResult.getStepLog().equals(" ")) {
			sendLog(stepResult.getStepLog(), logLevel);
		}
	}

	/**
	 * Send step logs and/or execution results to ReportPortal
	 *
	 * @param message log message to send
	 * @param level   log level
	 */
	public void sendLog(final String message, final String level) {
		ReportPortal.emitLog(itemId -> {
			SaveLogRQ rq = new SaveLogRQ();
			rq.setMessage(message);
			rq.setItemUuid(itemId);
			rq.setLevel(level);
			rq.setLogTime(Calendar.getInstance().getTime());
			return rq;
		});
	}

	private String getStepStatus(String status) {
		switch (status) {
			case "failed":
				return ItemStatusEnum.FAILED.toString();
			case "passed":
				return ItemStatusEnum.PASSED.toString();
			case "skipped":
				return ItemStatusEnum.SKIPPED.toString();
			case "stopped":
				return ItemStatusEnum.STOPPED.toString();
			case "interrupted":
				return ItemStatusEnum.RESETED.toString();
			case "cancelled":
				return ItemStatusEnum.CANCELLED.toString();
			default:
				LOGGER.warn("Unknown step status received! Set it as SKIPPED");
				return ItemStatusEnum.SKIPPED.toString();
		}
	}

	private String getLogLevel(String status) {
		switch (status) {
			case "failed":
				return ItemLogLevelEnum.ERROR.toString();
			case "stopped":
			case "interrupted":
			case "cancelled":
				return ItemLogLevelEnum.WARN.toString();
			default:
				return ItemLogLevelEnum.INFO.toString();
		}
	}

	/**
	 * Get step start time to keep the steps order
	 * in case previous step startTime == current step startTime or previous step startTime > current step startTime.
	 *
	 * @param stepStartTimeMap ConcurrentHashMap of steps within a scenario.
	 * @param stepId           step ID.
	 * @return step new startTime in Date format.
	 */
	private Date getStepStartTime(ConcurrentHashMap<Maybe<String>, Long> stepStartTimeMap, Maybe<String> stepId) {
		long currentStepStartTime = Calendar.getInstance().getTime().getTime();

		if (!stepStartTimeMap.keySet().isEmpty()) {
			long lastStepStartTime = stepStartTimeMap.get(stepId);

			if (lastStepStartTime >= currentStepStartTime) {
				currentStepStartTime += (lastStepStartTime - currentStepStartTime) + 1;
			}
		}

		return new Date(currentStepStartTime);
	}
}
