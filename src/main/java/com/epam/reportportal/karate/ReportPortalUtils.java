/*
 * Copyright 2023 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.utils.AttributeParser;
import com.epam.reportportal.utils.ParameterUtils;
import com.epam.reportportal.utils.TestCaseIdUtils;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.ParameterResource;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.intuit.karate.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.epam.reportportal.utils.ParameterUtils.NULL_VALUE;
import static java.util.Optional.ofNullable;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * Set of useful utils related to Karate -&gt; ReportPortal integration
 */
public class ReportPortalUtils {
	private static final Logger LOGGER = LoggerFactory.getLogger(ReportPortalUtils.class);

	public static final String PARAMETERS_PATTERN = "Parameters:\n\n%s";
	private static final String PARAMETER_ITEMS_START = "[";
	private static final String PARAMETER_ITEMS_END = "]";
	private static final String PARAMETER_ITEMS_DELIMITER = ";";
	private static final String KEY_VALUE_SEPARATOR = ":";
	public static final String VARIABLE_PATTERN =
			"(?:(?<=#\\()%1$s(?=\\)))|(?:(?<=[\\s=+-/*<>(]|^)%1$s(?=[\\s=+-/*<>)]|(?:\\r?\\n)|$))";

	public static final String AGENT_PROPERTIES_FILE = "agent.properties";
	public static final String SKIPPED_ISSUE_KEY = "skippedIssue";

	public static final String SCENARIO_CODE_REFERENCE_PATTERN = "%s/[SCENARIO:%s]";
	public static final String EXAMPLE_CODE_REFERENCE_PATTERN = "%s/[EXAMPLE:%s%s]";

	public static final String MARKDOWN_DELIMITER_PATTERN = "%s\n\n---\n\n%s";

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
				.map(e -> e.getKey() + KEY_VALUE_SEPARATOR + e.getValue().toString())
				.collect(Collectors.joining(PARAMETER_ITEMS_DELIMITER, PARAMETER_ITEMS_START, PARAMETER_ITEMS_END));
	}

	/**
	 * Create a launch finish hook which will be called on JVM shutdown. Prevents from long unfinished launches for
	 * interrupted tests.
	 *
	 * @param launch Launch object provider
	 * @return a Thread which executes Launch finish and exits
	 */
	@Nonnull
	public static Thread createShutdownHook(@Nonnull Supplier<Launch> launch) {
		return new Thread(() -> {
			FinishExecutionRQ rq = new FinishExecutionRQ();
			rq.setEndTime(Calendar.getInstance().getTime());
			launch.get().finish(rq);
		});
	}

	/**
	 * Create and register a launch finish hook which will be called on JVM shutdown. Prevents from long unfinished
	 * launches for interrupted tests.
	 *
	 * @param launch Launch object provider
	 * @return a Thread which executes Launch finish and exits
	 */
	@Nonnull
	public static Thread registerShutdownHook(@Nonnull Supplier<Launch> launch) {
		Thread shutDownHook = createShutdownHook(launch);
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
		rq.setStartTime(Calendar.getInstance().getTime());
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
		rq.getAttributes().addAll(SystemAttributesExtractor.extract(AGENT_PROPERTIES_FILE,
				ReportPortalUtils.class.getClassLoader()));
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
		rq.setEndTime(Calendar.getInstance().getTime());
		return rq;
	}

	/**
	 * Returns code reference for feature files by URI and Scenario reference
	 *
	 * @param scenario Karate's Scenario object instance
	 * @return a code reference
	 */
	@Nonnull
	public static String getCodeRef(@Nonnull Scenario scenario) {
		if (scenario.isOutlineExample()) {
			return String.format(EXAMPLE_CODE_REFERENCE_PATTERN, scenario.getFeature().getResource().getRelativePath(),
					scenario.getName(), ReportPortalUtils.formatExampleKey(scenario.getExampleData()));
		} else {
			return String.format(SCENARIO_CODE_REFERENCE_PATTERN, scenario.getFeature().getResource().getRelativePath(),
					scenario.getName());
		}
	}

	/**
	 * Build default start test item event/request
	 *
	 * @param name      item's name
	 * @param startTime item's start time in Date format
	 * @param type      item's type (e.g. feature, scenario, step, etc.)
	 * @return request to ReportPortal
	 */
	@Nonnull
	public static StartTestItemRQ buildStartTestItemRq(@Nonnull String name, @Nonnull Date startTime,
	                                                   @Nonnull ItemType type) {
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
	public static FinishTestItemRQ buildFinishTestItemRq(@Nonnull Date endTime, @Nullable ItemStatus status) {
		FinishTestItemRQ rq = new FinishTestItemRQ();
		rq.setEndTime(endTime);
		rq.setStatus(ofNullable(status).map(Enum::name).orElse(null));
		return rq;
	}

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
		StartTestItemRQ rq = buildStartTestItemRq(feature.getName(), Calendar.getInstance().getTime(), ItemType.STORY);
		rq.setAttributes(toAttributes(feature.getTags()));
		String featurePath = feature.getResource().getUri().toString();
		String description = feature.getDescription();
		if (isNotBlank(description)) {
			rq.setDescription(String.format(MARKDOWN_DELIMITER_PATTERN, featurePath, description));
		} else {
			rq.setDescription(featurePath);
		}
		return rq;
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
		return scenario.getExampleData().entrySet().stream().map(e -> {
			ParameterResource parameterResource = new ParameterResource();
			parameterResource.setKey(e.getKey());
			parameterResource.setValue(ofNullable(e.getValue()).map(Object::toString).orElse(NULL_VALUE));
			return parameterResource;
		}).collect(Collectors.toList());
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
		StartTestItemRQ rq = buildStartTestItemRq(scenario.getName(),
				Calendar.getInstance().getTime(),
				ItemType.STEP);
		rq.setCodeRef(getCodeRef(scenario));
		rq.setTestCaseId(ofNullable(getTestCaseId(scenario)).map(TestCaseIdEntry::getId).orElse(null));
		rq.setAttributes(toAttributes(scenario.getTags()));
		List<ParameterResource> parameters = getParameters(scenario);
		boolean hasParameters = ofNullable(parameters).filter(p -> !p.isEmpty()).isPresent();
		if (hasParameters) {
			rq.setParameters(parameters);
		}

		String description = scenario.getDescription();
		if (isNotBlank(description)) {
			if (hasParameters) {
				rq.setDescription(
						String.format(MARKDOWN_DELIMITER_PATTERN,
								String.format(PARAMETERS_PATTERN, ParameterUtils.formatParametersAsTable(parameters)),
								description));
			} else {
				rq.setDescription(description);
			}
		} else if (hasParameters) {
			rq.setDescription(String.format(PARAMETERS_PATTERN, ParameterUtils.formatParametersAsTable(parameters)));
		}
		return rq;
	}

	/**
	 * Build ReportPortal request for start Background event.
	 *
	 * @param step     Karate's Step object instance
	 * @param scenario Karate's Scenario object instance
	 * @return request to ReportPortal
	 */
	@Nonnull
	@SuppressWarnings("unused")
	public static StartTestItemRQ buildStartBackgroundRq(@Nonnull Step step, @Nonnull Scenario scenario) {
		StartTestItemRQ rq = buildStartTestItemRq(Background.KEYWORD, Calendar.getInstance().getTime(), ItemType.STEP);
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
		StartTestItemRQ rq = buildStartTestItemRq(stepName, Calendar.getInstance().getTime(), ItemType.STEP);
		rq.setHasStats(false);
		if (step.isOutline()) {
			List<ParameterResource> parameters = scenario
					.getExampleData()
					.entrySet()
					.stream()
					.filter(e -> Pattern.compile(String.format(VARIABLE_PATTERN, e.getKey()))
							.matcher(step.getText()).find())
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
	public static ItemStatus getStepStatus(String status) {
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
}
