package com.epam.reportportal.karate;

import com.epam.reportportal.karate.enums.ItemLogLevelEnum;
import com.epam.reportportal.karate.enums.ItemStatusEnum;
import com.epam.reportportal.listeners.ListenerParameters;
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
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static com.google.common.base.Strings.isNullOrEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

public class ReportPortalPublisher {
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
		launch.get().start();
	}

	/**
	 * Finish launch
	 */
	public void finishLaunch() {
		FinishExecutionRQ rq = new FinishExecutionRQ();
		rq.setEndTime(Calendar.getInstance().getTime());
		ListenerParameters parameters = launch.get().getParameters();
		LOGGER.info("LAUNCH URL: {}/ui/#{}/launches/all/{}", parameters.getBaseUrl(), parameters.getProjectName(),
				System.getProperty("rp.launch.id"));
		launch.get().finish(rq);
		Runtime.getRuntime().removeShutdownHook(shutDownHook);
	}

	/**
	 * Customize start test item event/request
	 *
	 * @param name      item's name
	 * @param startTime item's start time in Date format
	 * @param type      item's type (e.g. feature, scenario, step, etc.)
	 * @return request to ReportPortal
	 */
	protected StartTestItemRQ buildStartTestItemRq(@Nonnull String name,
	                                               @Nonnull Date startTime,
	                                               @Nonnull String type) {
		StartTestItemRQ rq = new StartTestItemRQ();
		rq.setName(name);
		rq.setStartTime(startTime);
		rq.setType(type);
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
	@SuppressWarnings("SameParameterValue")
	protected StartTestItemRQ buildStartTestItemRq(@Nonnull String name,
	                                               @Nonnull Date startTime,
	                                               @Nonnull String type,
	                                               boolean hasStats) {
		StartTestItemRQ rq = buildStartTestItemRq(name, startTime, type);
		rq.setHasStats(hasStats);
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
	 * Start sending feature data to ReportPortal
	 *
	 * @param featureResult feature result
	 */
	public void startFeature(FeatureResult featureResult) {
		StartTestItemRQ rq = buildStartTestItemRq(String.valueOf(featureResult.toCucumberJson().get("name")),
				Calendar.getInstance().getTime(),
				"STORY");
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
		launch.get().finishTestItem(featureIdMap.remove(featureResult.getCallNameForReport()), rq);
	}

	/**
	 * Start sending scenario data to ReportPortal
	 *
	 * @param scenarioResult scenario result
	 * @param featureResult  feature result
	 */
	public void startScenario(ScenarioResult scenarioResult, FeatureResult featureResult) {
		StartTestItemRQ rq = buildStartTestItemRq(scenarioResult.getScenario().getName(),
				Calendar.getInstance().getTime(),
				"STEP");
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
		StartTestItemRQ rq = buildStartTestItemRq(stepName, getStepStartTime(stepStartTimeMap, stepId), "STEP", false);
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
