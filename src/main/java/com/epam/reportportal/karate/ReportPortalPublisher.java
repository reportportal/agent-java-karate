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
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.utils.AttributeParser;
import com.epam.reportportal.utils.MemoizingSupplier;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
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
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.karate.ReportPortalUtils.AGENT_PROPERTIES_FILE;
import static com.epam.reportportal.utils.ParameterUtils.NULL_VALUE;
import static com.epam.reportportal.utils.ParameterUtils.formatParametersAsTable;
import static com.epam.reportportal.utils.markdown.MarkdownUtils.formatDataTable;
import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ReportPortalPublisher {
	public static final String SCENARIO_CODE_REFERENCE_PATTERN = "%s/[SCENARIO:%s]";
	public static final String EXAMPLE_CODE_REFERENCE_PATTERN = "%s/[EXAMPLE:%s%s]";
	public static final String VARIABLE_PATTERN =
			"(?:(?<=#\\()%1$s(?=\\)))|(?:(?<=[\\s=+-/*<>(]|^)%1$s(?=[\\s=+-/*<>)]|(?:\\r?\\n)|$))";

	private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalPublisher.class);
	private final Map<String, Maybe<String>> featureIdMap = new HashMap<>();
	private final Map<String, Maybe<String>> scenarioIdMap = new HashMap<>();
	private final Map<Maybe<String>, Long> stepStartTimeMap = new HashMap<>();
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
		rq.setRerun(parameters.isRerun());
		if (isNotBlank(parameters.getRerunOf())) {
			rq.setRerunOf(parameters.getRerunOf());
		}
		if (null != parameters.getSkippedAnIssue()) {
			ItemAttributesRQ skippedIssueAttribute = new ItemAttributesRQ();
			skippedIssueAttribute.setKey(ReportPortalUtils.SKIPPED_ISSUE_KEY);
			skippedIssueAttribute.setValue(parameters.getSkippedAnIssue().toString());
			skippedIssueAttribute.setSystem(true);
			rq.getAttributes().add(skippedIssueAttribute);
		}
		rq.getAttributes().addAll(SystemAttributesExtractor.extract(AGENT_PROPERTIES_FILE,
				ReportPortalUtils.class.getClassLoader()));
		return rq;
	}

	public ReportPortalPublisher(ReportPortal reportPortal) {
		launch = new MemoizingSupplier<>(() -> {
			ListenerParameters params = reportPortal.getParameters();
			StartLaunchRQ rq = buildStartLaunchRq(params);
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
		if (scenario.isOutlineExample()) {
			return String.format(EXAMPLE_CODE_REFERENCE_PATTERN, scenario.getFeature().getResource().getRelativePath(),
					scenario.getName(), ReportPortalUtils.formatExampleKey(scenario.getExampleData()));
		} else {
			return String.format(SCENARIO_CODE_REFERENCE_PATTERN, scenario.getFeature().getResource().getRelativePath(),
					scenario.getName());
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

	@Nullable
	private List<ParameterResource> getParameters(@Nonnull Scenario scenario) {
		if (scenario.getExampleIndex() < 0) {
			return null;
		}
		return scenario.getExampleData().entrySet().stream().map(e -> {
			ParameterResource parameterResource = new ParameterResource();
			parameterResource.setKey(e.getKey());
			parameterResource.setValue(ofNullable(e.getValue()).map(Object::toString).orElse(NULL_VALUE));
			return parameterResource;
		}).collect(Collectors.toList());
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
		rq.setParameters(getParameters(scenario));
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
	                                                 @Nonnull ItemStatus status) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(endTime);
		rq.setStatus(status.name());
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
				featureResult.isFailed() ? ItemStatus.FAILED : ItemStatus.PASSED);
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
				scenarioResult.getFailureMessageForDisplay() == null ? ItemStatus.PASSED : ItemStatus.FAILED);
		Maybe<String> removedScenarioId = scenarioIdMap.remove(scenarioResult.getScenario().getName());
		//noinspection ReactiveStreamsUnusedPublisher
		launch.get().finishTestItem(removedScenarioId, rq);
	}

	/**
	 * Customize start step test item event/request
	 *
	 * @param stepResult     Karate's StepResult class instance
	 * @param scenarioResult Karate's ScenarioResult class instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	protected StartTestItemRQ buildStartStepRq(@Nonnull StepResult stepResult, @Nonnull ScenarioResult scenarioResult) {
		Step step = stepResult.getStep();
		String stepName = step.getPrefix() + " " + step.getText();
		StartTestItemRQ rq = buildStartTestItemRq(stepName, getStepStartTime(stepStartTimeMap, stepId), ItemType.STEP);
		rq.setHasStats(false);
		if (step.isOutline()) {
			Scenario scenario = scenarioResult.getScenario();
			List<ParameterResource> parameters = scenario
					.getExampleData()
					.entrySet()
					.stream()
					.filter(e -> Pattern.compile(String.format(VARIABLE_PATTERN, e.getKey())).matcher(step.getText()).find())
					.map(e -> {
						ParameterResource param = new ParameterResource();
						param.setKey(e.getKey());
						var value = ofNullable(e.getValue()).map(Object::toString).orElse(NULL_VALUE);
						param.setValue(value);
						return param;
					})
					.collect(Collectors.toList());
			rq.setParameters(parameters);
		}
		return rq;
	}

	/**
	 * Start sending step data to ReportPortal
	 *
	 * @param stepResult     step result
	 * @param scenarioResult scenario result
	 */
	public void startStep(StepResult stepResult, ScenarioResult scenarioResult) {
		StartTestItemRQ stepRq = buildStartStepRq(stepResult, scenarioResult);
		stepId = launch.get().startTestItem(scenarioIdMap.get(scenarioResult.getScenario().getName()), stepRq);
		stepStartTimeMap.put(stepId, stepRq.getStartTime().getTime());
		ofNullable(stepRq.getParameters())
				.filter(params -> !params.isEmpty())
				.ifPresent(params ->
						sendLog(stepId, "Parameters:\n\n" + formatParametersAsTable(params), LogLevel.INFO));
		ofNullable(stepResult.getStep().getTable())
				.ifPresent(table ->
						sendLog(stepId, "Table:\n\n" + formatDataTable(table.getRows()), LogLevel.INFO));
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
		LogLevel logLevel = getLogLevel(result.getStatus());
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
	public void sendLog(final String message, LogLevel level) {
		ReportPortal.emitLog(itemId -> {
			SaveLogRQ rq = new SaveLogRQ();
			rq.setMessage(message);
			rq.setItemUuid(itemId);
			rq.setLevel(level.name());
			rq.setLogTime(Calendar.getInstance().getTime());
			return rq;
		});
	}

	/**
	 * Send step logs and/or execution results to ReportPortal
	 *
	 * @param itemId  item ID future
	 * @param message log message to send
	 * @param level   log level
	 */
	public void sendLog(Maybe<String> itemId, String message, LogLevel level) {
		ReportPortal.emitLog(itemId, id -> {
			SaveLogRQ rq = new SaveLogRQ();
			rq.setMessage(message);
			rq.setItemUuid(id);
			rq.setLevel(level.name());
			rq.setLogTime(Calendar.getInstance().getTime());
			return rq;
		});
	}

	private ItemStatus getStepStatus(String status) {
		switch (status) {
			case "failed":
				return ItemStatus.FAILED;
			case "passed":
				return ItemStatus.PASSED;
			case "skipped":
				return ItemStatus.SKIPPED;
			case "stopped":
				return ItemStatus.STOPPED;
			case "interrupted":
				return ItemStatus.INTERRUPTED;
			case "cancelled":
				return ItemStatus.CANCELLED;
			default:
				LOGGER.warn("Unknown step status received! Set it as SKIPPED");
				return ItemStatus.SKIPPED;
		}
	}

	private LogLevel getLogLevel(String status) {
		switch (status) {
			case "failed":
				return LogLevel.ERROR;
			case "stopped":
			case "interrupted":
			case "cancelled":
				return LogLevel.WARN;
			default:
				return LogLevel.INFO;
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
	private Date getStepStartTime(Map<Maybe<String>, Long> stepStartTimeMap, Maybe<String> stepId) {
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
