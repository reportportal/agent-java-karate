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

import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ItemType;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.listeners.LogLevel;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.utils.AttributeParser;
import com.epam.reportportal.utils.ParameterUtils;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.reportportal.utils.formatting.MarkdownUtils;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.karatelabs.core.ScenarioResult;
import io.karatelabs.core.StepResult;
import io.karatelabs.gherkin.*;
import io.reactivex.Maybe;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.utils.ParameterUtils.NULL_VALUE;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Utility methods for mapping Karate runtime entities to ReportPortal API requests.
 */
public class ReportPortalUtils {
	/**
	 * Markdown template for rendering code blocks.
	 */
	public static final String MARKDOWN_CODE_PATTERN = "```\n%s\n```";
	/**
	 * Markdown template for rendering parameter tables.
	 */
	public static final String PARAMETERS_PATTERN = "Parameters:\n\n%s";
	/**
	 * Regex template used to match Karate outline variable placeholders in a step.
	 */
	public static final String VARIABLE_PATTERN = "(?:(?<=#\\()%1$s(?=\\)))|(?:(?<=[\\s=+-/*<>(]|^)%1$s(?=[\\s=+-/*<>)]|(?:\\r?\\n)|$))";
	/**
	 * Agent properties file name used to enrich launch/system attributes.
	 */
	public static final String AGENT_PROPERTIES_FILE = "agent.properties";
	/**
	 * System attribute key that controls skipped issue behavior in ReportPortal.
	 */
	public static final String SKIPPED_ISSUE_KEY = "skippedIssue";
	/**
	 * Code reference template for a concrete scenario.
	 */
	public static final String SCENARIO_CODE_REFERENCE_PATTERN = "%s/[SCENARIO:%s]";
	/**
	 * Code reference template for a scenario outline example.
	 */
	public static final String EXAMPLE_CODE_REFERENCE_PATTERN = "%s/[EXAMPLE:%s%s]";
	/**
	 * Markdown delimiter used to join logical description sections.
	 */
	public static final String MARKDOWN_DELIMITER = MarkdownUtils.LOGICAL_SEPARATOR;
	/**
	 * Pattern for joining two text blocks with a markdown delimiter.
	 */
	public static final String MARKDOWN_DELIMITER_PATTERN = "%s" + MARKDOWN_DELIMITER + "%s";
	/**
	 * Prefix used for naming called (inner) feature items.
	 */
	public static final String FEATURE_TAG = "Feature: ";
	/**
	 * Prefix used for naming called (inner) scenario items.
	 */
	public static final String SCENARIO_TAG = "Scenario: ";
	private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalUtils.class);
	private static final String PARAMETER_ITEMS_START = "[";
	private static final String PARAMETER_ITEMS_END = "]";
	private static final String PARAMETER_ITEMS_DELIMITER = ";";
	private static final String KEY_VALUE_SEPARATOR = ":";

	private ReportPortalUtils() {
		throw new RuntimeException("No instances should exist for the class!");
	}

	/**
	 * Create a String from a parameter Map to be used as a test key and title
	 *
	 * @param example a map of parameters: name-&gt;value
	 * @return a formatted string of parameters
	 */
	public static String formatExampleKey(@Nonnull final Map<String, Object> example) {
		return example.entrySet()
				.stream()
				.sorted(Map.Entry.comparingByKey())
				.map(e -> e.getKey() + KEY_VALUE_SEPARATOR + ofNullable(e.getValue()).map(Object::toString).orElse(NULL_VALUE))
				.collect(Collectors.joining(PARAMETER_ITEMS_DELIMITER, PARAMETER_ITEMS_START, PARAMETER_ITEMS_END));
	}

	/**
	 * Create a launch finish hook which will be called on JVM shutdown. Prevents from long unfinished launches for
	 * interrupted tests.
	 *
	 * @param actions Shutdown actions to perform
	 * @return a Thread which executes Launch finish and exits
	 */
	@Nonnull
	public static Thread createShutdownHook(@Nonnull Runnable actions) {
		return new Thread(actions);
	}

	/**
	 * Create and register a launch finish hook which will be called on JVM shutdown. Prevents from long unfinished
	 * launches for interrupted tests.
	 *
	 * @param actions Shutdown actions to perform
	 * @return a Thread which executes Launch finish and exits
	 */
	@Nonnull
	public static Thread registerShutdownHook(@Nonnull Runnable actions) {
		Thread shutDownHook = createShutdownHook(actions);
		Runtime.getRuntime().addShutdownHook(shutDownHook);
		return shutDownHook;
	}

	/**
	 * Remove a launch finish hook. Use it if the launch finished gracefully.
	 *
	 * @param hook a Thread which represents Launch finish hook
	 */
	public static void unregisterShutdownHook(@Nonnull Thread hook) {
		Runtime.getRuntime().removeShutdownHook(hook);
	}

	/**
	 * Build default start launch event/request
	 *
	 * @param parameters Launch configuration parameters
	 * @return request to ReportPortal
	 */
	@Nonnull
	public static StartLaunchRQ buildStartLaunchRq(@Nonnull ListenerParameters parameters) {
		StartLaunchRQ rq = new StartLaunchRQ();
		rq.setName(parameters.getLaunchName());
		rq.setStartTime(Instant.now());
		rq.setMode(parameters.getLaunchRunningMode());
		rq.setAttributes(new HashSet<>(parameters.getAttributes()));
		if (isNotBlank(parameters.getDescription())) {
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
		rq.getAttributes().addAll(SystemAttributesExtractor.extract(AGENT_PROPERTIES_FILE, ReportPortalUtils.class.getClassLoader()));
		return rq;
	}

	/**
	 * Build default finish launch event/request
	 *
	 * @param parameters Launch configuration parameters
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	public static FinishExecutionRQ buildFinishLaunchRq(@Nonnull ListenerParameters parameters) {
		FinishExecutionRQ rq = new FinishExecutionRQ();
		rq.setEndTime(Instant.now());
		return rq;
	}

	/**
	 * Returns a feature file code reference.
	 *
	 * @param feature Karate feature
	 * @return feature-relative code reference
	 */
	@Nonnull
	public static String getCodeRef(@Nonnull Feature feature) {
		return feature.getResource().getRelativePath();
	}

	/**
	 * Returns code reference for feature files by URI and Scenario reference
	 *
	 * @param scenario Karate's Scenario object instance
	 * @return a code reference
	 */
	@Nonnull
	public static String getCodeRef(@Nonnull Scenario scenario) {
		String featurePath = getCodeRef(scenario.getFeature());
		if (scenario.isOutlineExample()) {
			return String.format(
					EXAMPLE_CODE_REFERENCE_PATTERN,
					featurePath,
					scenario.getName(),
					ReportPortalUtils.formatExampleKey(scenario.getExampleData())
			);
		} else {
			return String.format(SCENARIO_CODE_REFERENCE_PATTERN, featurePath, scenario.getName());
		}
	}

	/**
	 * Build default start test item event/request
	 *
	 * @param name      item's name
	 * @param startTime item's start time in Instant format
	 * @param type      item's type (e.g. feature, scenario, step, etc.)
	 * @return request to ReportPortal
	 */
	@Nonnull
	public static StartTestItemRQ buildStartTestItemRq(@Nonnull String name, @Nonnull Instant startTime, @Nonnull ItemType type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(name);
		rq.setStartTime(startTime);
		rq.setType(type.name());
		return rq;
	}

	/**
	 * Build default finish test item event/request
	 *
	 * @param endTime item's end time
	 * @param status  item's status
	 * @return request to ReportPortal
	 */
	@Nonnull
	public static FinishTestItemRQ buildFinishTestItemRq(@Nonnull Instant endTime, @Nullable ItemStatus status) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(endTime);
		rq.setStatus(ofNullable(status).map(Enum::name).orElse(null));
		return rq;
	}

	/**
	 * Converts Karate tags to ReportPortal attributes.
	 *
	 * @param tags Karate tags
	 * @return ReportPortal attributes or {@code null} when there are no tags
	 */
	@Nullable
	public static Set<ItemAttributesRQ> toAttributes(@Nullable List<Tag> tags) {
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
	 * @param feature Karate's Feature object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	public static StartTestItemRQ buildStartFeatureRq(@Nonnull Feature feature) {
		String featureName = ofNullable(feature.getName()).filter(n -> !n.isBlank()).orElseGet(() -> getCodeRef(feature));
		StartTestItemRQ rq = buildStartTestItemRq(featureName, Instant.now(), ItemType.STORY);
		rq.setAttributes(toAttributes(feature.getTags()));
		String featurePath = feature.getResource().getUri().toString();
		String description = feature.getDescription();
		if (isNotBlank(description)) {
			rq.setDescription(MarkdownUtils.asTwoParts(featurePath, description));
		} else {
			rq.setDescription(featurePath);
		}
		return rq;
	}

	/**
	 * Transform Map of parameters to ReportPortal parameter list.
	 *
	 * @param args argument Map
	 * @return parameters
	 */
	@Nonnull
	public static List<ParameterResource> getParameters(@Nonnull Map<String, Object> args) {
		return args.entrySet().stream().map(e -> {
			ParameterResource parameterResource = new ParameterResource();
			parameterResource.setKey(e.getKey());
			parameterResource.setValue(ofNullable(e.getValue()).map(Object::toString).orElse(NULL_VALUE));
			return parameterResource;
		}).collect(Collectors.toList());
	}

	/**
	 * Extract and transform ScenarioOutline parameters to ReportPortal parameter list.
	 *
	 * @param scenario Karate's Scenario object instance
	 * @return parameters
	 */
	@Nullable
	public static List<ParameterResource> getParameters(@Nonnull Scenario scenario) {
		if (!scenario.isOutlineExample()) {
			return null;
		}
		return getParameters(scenario.getExampleData());
	}

	/**
	 * Return a Test Case ID for a Scenario in a Feature file
	 *
	 * @param scenario Karate's Scenario object instance
	 * @return Test Case ID entity or null if it's not possible to calculate
	 */
	@Nullable
	public static TestCaseIdEntry getTestCaseId(@Nonnull Scenario scenario) {
		return TestCaseIdUtils.getTestCaseId(getCodeRef(scenario), null);
	}

	/**
	 * Build ReportPortal request for start Scenario event
	 *
	 * @param scenario Karate's Scenario object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	public static StartTestItemRQ buildStartScenarioRq(@Nonnull Scenario scenario) {
		StartTestItemRQ rq = buildStartTestItemRq(scenario.getName(), Instant.now(), ItemType.STEP);
		rq.setCodeRef(getCodeRef(scenario));
		rq.setTestCaseId(ofNullable(getTestCaseId(scenario)).map(TestCaseIdEntry::getId).orElse(null));
		rq.setAttributes(toAttributes(scenario.getTags()));
		rq.setParameters(getParameters(scenario));
		rq.setDescription(buildDescription(scenario, null, getParameters(scenario)));
		return rq;
	}

	/**
	 * Build ReportPortal request for finish Scenario event
	 *
	 * @param result Karate's ScenarioResult object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	public static FinishTestItemRQ buildFinishScenarioRq(@Nonnull ScenarioResult result) {
		Scenario scenario = result.getScenario();
		FinishTestItemRQ rq = buildFinishTestItemRq(
				Instant.now(),
				result.getFailureMessageForDisplay() == null ? ItemStatus.PASSED : ItemStatus.FAILED
		);
		rq.setDescription(buildDescription(scenario, result.getFailureMessageForDisplay(), getParameters(scenario)));
		return rq;
	}

	/**
	 * Builds scenario description from parameters, scenario description and optional error details.
	 *
	 * @param scenario     scenario descriptor
	 * @param errorMessage optional error message
	 * @param parameters   optional outline parameters
	 * @return formatted Markdown description
	 */
	@Nonnull
	private static String buildDescription(@Nonnull Scenario scenario, @Nullable String errorMessage,
			@Nullable List<ParameterResource> parameters) {
		StringBuilder descriptionBuilder = new StringBuilder();

		if (parameters != null && !parameters.isEmpty()) {
			descriptionBuilder.append(String.format(PARAMETERS_PATTERN, ParameterUtils.formatParametersAsTable(parameters)));
		}
		if (isNotBlank(scenario.getDescription())) {
			appendWithDelimiter(descriptionBuilder, scenario.getDescription());
		}
		if (isNotBlank(errorMessage)) {
			appendWithDelimiter(descriptionBuilder, String.format("Error:\n%s", errorMessage));
		}

		return descriptionBuilder.toString();
	}

	/**
	 * Appends text using the configured Markdown delimiter when needed.
	 *
	 * @param builder destination description builder
	 * @param text    text block to append
	 */
	private static void appendWithDelimiter(StringBuilder builder, String text) {
		if (!builder.isEmpty()) {
			builder.append(MARKDOWN_DELIMITER);
		}
		builder.append(text);
	}

	/**
	 * Build ReportPortal request for start Background event.
	 *
	 * @param startTime background start time
	 * @param step      Karate's Step object instance
	 * @param scenario  Karate's Scenario object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	public static StartTestItemRQ buildStartBackgroundRq(Instant startTime, @Nonnull Step step, @Nonnull Scenario scenario) {
		StartTestItemRQ rq = buildStartTestItemRq(Background.KEYWORD, startTime, ItemType.STEP);
		rq.setHasStats(false);
		return rq;
	}

	/**
	 * Customize start step test item event/request
	 *
	 * @param step     Karate's Step object instance
	 * @param scenario Karate's Scenario object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	public static StartTestItemRQ buildStartStepRq(@Nonnull Step step, @Nonnull Scenario scenario) {
		String stepName = step.getPrefix() + " " + step.getText();
		StartTestItemRQ rq = buildStartTestItemRq(stepName, Instant.now(), ItemType.STEP);
		rq.setHasStats(false);
		if (step.isOutline()) {
			List<ParameterResource> parameters = scenario.getExampleData()
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
	 * Map Karate's item status to ReportPortal status object.
	 *
	 * @param status Karate item status
	 * @return ReportPortal status
	 */
	@SuppressWarnings("UnnecessaryDefault")
	public static ItemStatus getStepStatus(StepResult.Status status) {
		return switch (status) {
			case FAILED -> ItemStatus.FAILED;
			case PASSED -> ItemStatus.PASSED;
			case SKIPPED -> ItemStatus.SKIPPED;
			default -> {
				LOGGER.warn("Unknown step status received! Set it as SKIPPED");
				yield ItemStatus.SKIPPED;
			}
		};
	}

	/**
	 * Send Step logs to ReportPortal.
	 *
	 * @param itemId  item ID future
	 * @param message log message to send
	 * @param level   log level
	 * @param logTime log time
	 */
	public static void sendLog(Maybe<String> itemId, String message, LogLevel level, Instant logTime) {
		ReportPortal.emitLog(
				itemId, id -> {
					SaveLogRQ rq = new SaveLogRQ();
					rq.setMessage(message);
					rq.setItemUuid(id);
					rq.setLevel(level.name());
					rq.setLogTime(logTime);
					return rq;
				}
		);
	}

	/**
	 * Send Step logs to ReportPortal.
	 *
	 * @param itemId  item ID future
	 * @param message log message to send
	 * @param level   log level
	 */
	public static void sendLog(Maybe<String> itemId, String message, LogLevel level) {
		sendLog(itemId, message, level, Instant.now());
	}

	/**
	 * Embed an attachment to ReportPortal.
	 *
	 * @param time   log time
	 * @param itemId item ID future
	 * @param embed  Karate's Embed object
	 */
	public static void embedAttachment(@Nonnull Instant time, @Nonnull Maybe<String> itemId, @Nonnull StepResult.Embed embed) {
		ReportPortal.emitLog(
				itemId, id -> {
					SaveLogRQ rq = new SaveLogRQ();
					rq.setItemUuid(id);
					rq.setLevel(LogLevel.INFO.name());
					rq.setLogTime(time);
					rq.setMessage("Attachment: " + embed.getMimeType());

					SaveLogRQ.File file = new SaveLogRQ.File();
					file.setName(embed.getFileName());
					file.setContent(embed.getData());
					file.setContentType(embed.getMimeType());
					rq.setFile(file);

					return rq;
				}
		);
	}

	/**
	 * Finish sending Launch data to ReportPortal.
	 *
	 * @param launch       Launch object to finish
	 * @param rq           Request to finish execution
	 * @param shutDownHook Optional shutdown hook to unregister
	 */
	public static void doFinishLaunch(@Nonnull Launch launch, @Nonnull FinishExecutionRQ rq, @Nullable Thread shutDownHook) {
		ListenerParameters parameters = launch.getParameters();
		LOGGER.info(
				"Launch URL: {}/ui/#{}/launches/all/{}",
				parameters.getBaseUrl(),
				parameters.getProjectName(),
				launch.getLaunch().blockingGet()
		);
		launch.finish(rq);
		if (shutDownHook != null && Thread.currentThread() != shutDownHook) {
			unregisterShutdownHook(shutDownHook);
		}
	}

	/**
	 * Builds Markdown representation of some code or script to be logged to ReportPortal
	 *
	 * @param code Code or Script
	 * @return Message to be sent to ReportPortal
	 */
	public static String asMarkdownCode(String code) {
		return String.format(MARKDOWN_CODE_PATTERN, code);
	}

	/**
	 * Build name of inner scenario (called by another scenario).
	 *
	 * @param name Scenario name
	 * @return Inner scenario name
	 */
	public static String getInnerScenarioName(String name) {
		return SCENARIO_TAG + name;
	}

	/**
	 * Build name of inner feature (called by another scenario).
	 *
	 * @param name Feature name
	 * @return Inner feature name
	 */
	public static String getInnerFeatureName(String name) {
		return FEATURE_TAG + name;
	}

	/**
	 * Get step start time. To keep the steps order in case previous step startTime == current step startTime or
	 * previous step startTime &gt; current step startTime.
	 *
	 * @param scenarioUniqueId     Karate's Scenario Unique ID, a key for stepStartTimeMap
	 * @param stepStartTimeMap     a holder for start times for every particular scenario
	 * @param currentStepStartTime scenario start time to use as baseline
	 * @param useMicroseconds      if server supports microseconds
	 * @return step new startTime in Instant format.
	 */
	public static Instant getStepStartTime(@Nullable String scenarioUniqueId, Map<String, Instant> stepStartTimeMap,
			Instant currentStepStartTime, boolean useMicroseconds) {
		if (scenarioUniqueId == null || stepStartTimeMap.isEmpty()) {
			stepStartTimeMap.put(scenarioUniqueId, currentStepStartTime);
			return currentStepStartTime;
		}
		Instant lastStepStartTime = stepStartTimeMap.get(scenarioUniqueId);
		if (lastStepStartTime == null) {
			stepStartTimeMap.put(scenarioUniqueId, currentStepStartTime);
			return currentStepStartTime;
		}
		if (useMicroseconds) {
			if (lastStepStartTime.compareTo(currentStepStartTime) >= 0) {
				currentStepStartTime = lastStepStartTime.plus(1, ChronoUnit.MICROS);
			}
		} else {
			if (lastStepStartTime.truncatedTo(ChronoUnit.MILLIS).compareTo(currentStepStartTime.truncatedTo(ChronoUnit.MILLIS)) >= 0) {
				currentStepStartTime = lastStepStartTime.plus(1, ChronoUnit.MILLIS);
			}
		}
		stepStartTimeMap.put(scenarioUniqueId, currentStepStartTime);
		return currentStepStartTime;
	}

	/**
	 * Get step start time. To keep the steps order in case previous step startTime == current step startTime or
	 * previous step startTime &gt; current step startTime.
	 *
	 * @param scenarioUniqueId Karate's Scenario Unique ID, a key for stepStartTimeMap
	 * @param stepStartTimeMap a holder for start times for every particular scenario
	 * @param useMicroseconds  if server supports microseconds
	 * @return step new startTime in Instant format.
	 */
	public static Instant getStepStartTime(@Nullable String scenarioUniqueId, Map<String, Instant> stepStartTimeMap,
			boolean useMicroseconds) {
		Instant currentStepStartTime = Instant.now().truncatedTo(ChronoUnit.MICROS);
		return getStepStartTime(scenarioUniqueId, stepStartTimeMap, currentStepStartTime, useMicroseconds);
	}
}
