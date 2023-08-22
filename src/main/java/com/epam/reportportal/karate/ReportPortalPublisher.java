package com.epam.reportportal.karate;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.intuit.karate.core.*;
import io.reactivex.Maybe;
import org.apache.commons.lang3.NotImplementedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final String INFO_LOG_LEVEL = "INFO";
    private static final String ERROR_LOG_LEVEL = "ERROR";
    private static final String WARN_LOG_LEVEL = "WARN";
    private static final String PASSED = "PASSED";
    private static final String FAILED = "FAILED";
    private static final String SKIPPED = "SKIPPED";
    private static final String STOPPED = "STOPPED";
    private static final String RESETED = "RESETED";
    private static final String CANCELLED = "CANCELLED";

    private final Supplier<Launch> launch;
    private final ConcurrentHashMap<String, Maybe<String>> featureIdMap;
    private final ConcurrentHashMap<String, Maybe<String>> scenarioIdMap;
    private final ConcurrentHashMap<Maybe<String>, Long> stepStartTimeMap;
    private Maybe<String> stepId;

    public ReportPortalPublisher() {
        this(createLaunchSupplier());
    }

    public ReportPortalPublisher(Supplier<Launch> launch) {
        this.launch = launch;
        this.featureIdMap = new ConcurrentHashMap<>();
        this.scenarioIdMap = new ConcurrentHashMap<>();
        this.stepStartTimeMap = new ConcurrentHashMap<>();
    }

    private static Supplier<Launch> createLaunchSupplier() {
        return new Supplier<>() {
            private final Launch launch = createLaunch();

            @Override
            public Launch get() {
                return launch;
            }
        };
    }

    private static Launch createLaunch() {
        final ReportPortal reportPortal = ReportPortal.builder().build();
        ListenerParameters parameters = reportPortal.getParameters();
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
        return reportPortal.newLaunch(rq);
    }

    public void startLaunch() {
        this.launch.get().start();
    }

    public void finishLaunch() {
        FinishExecutionRQ rq = new FinishExecutionRQ();
        rq.setEndTime(Calendar.getInstance().getTime());
        ListenerParameters parameters = launch.get().getParameters();
        LOGGER.info("LAUNCH URL: {}/ui/#{}/launches/all/{}", parameters.getBaseUrl(), parameters.getProjectName(),
                System.getProperty("rp.launch.id"));
        launch.get().finish(rq);
    }

    public void startFeature(FeatureResult featureResult) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setName(String.valueOf(featureResult.toCucumberJson().get("name")));
        rq.setStartTime(Calendar.getInstance().getTime());
        rq.setType("STORY");
        Maybe<String> featureId = launch.get().startTestItem(rq);
        featureIdMap.put(featureResult.getCallNameForReport(), featureId);
    }

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

        FinishTestItemRQ rq = new FinishTestItemRQ();
        rq.setEndTime(Calendar.getInstance().getTime());
        rq.setStatus(featureResult.isFailed() ? FAILED : PASSED);
        launch.get().finishTestItem(featureIdMap.remove(featureResult.getCallNameForReport()), rq);
    }

    private void startScenario(ScenarioResult scenarioResult, FeatureResult featureResult) {
        Maybe<String> scenarioId;
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setName(scenarioResult.getScenario().getName());
        rq.setStartTime(Calendar.getInstance().getTime());
        rq.setType("STEP");
        scenarioId = launch.get().startTestItem(featureIdMap.get(featureResult.getCallNameForReport()), rq);
        scenarioIdMap.put(scenarioResult.getScenario().getName(), scenarioId);
    }

    private void finishScenario(ScenarioResult scenarioResult) {
        if (!scenarioIdMap.containsKey(scenarioResult.getScenario().getName())) {
            LOGGER.error("ERROR: Trying to finish unspecified scenario.");
        }

        FinishTestItemRQ rq = new FinishTestItemRQ();
        rq.setEndTime(Calendar.getInstance().getTime());
        rq.setStatus(scenarioResult.getFailureMessageForDisplay() == null ? PASSED : FAILED);
        Maybe<String> removedScenarioId = scenarioIdMap.remove(scenarioResult.getScenario().getName());
        launch.get().finishTestItem(removedScenarioId, rq);
    }

    private void startStep(StepResult stepResult, ScenarioResult scenarioResult) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setName(stepResult.getStep().getPrefix() + " " + stepResult.getStep().getText());
        Date startTime;

        try {
            startTime = getStepStartTime(stepResult);
        } catch (NotImplementedException e) {
            startTime = getStepStartTime(stepStartTimeMap, stepId);
        }

        rq.setStartTime(startTime);
        rq.setType("STEP");
        rq.setHasStats(false);
        stepId = launch.get().startTestItem(scenarioIdMap.get(scenarioResult.getScenario().getName()), rq);
        stepStartTimeMap.put(stepId, rq.getStartTime().getTime());
    }

    private void finishStep(StepResult stepResult) {
        if (stepId == null) {
            LOGGER.error("ERROR: Trying to finish unspecified step.");
            return;
        }

        FinishTestItemRQ rq = new FinishTestItemRQ();
        rq.setEndTime(Calendar.getInstance().getTime());
        rq.setStatus(getStepStatus(stepResult.getResult().getStatus()));
        launch.get().finishTestItem(stepId, rq);
    }

    private void sendStepResults(StepResult stepResult) {
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

    private void sendLog(final String message, final String level) {
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
                return FAILED;
            case "passed":
                return PASSED;
            case "skipped":
                return SKIPPED;
            case "stopped":
                return STOPPED;
            case "interrupted":
                return RESETED;
            case "cancelled":
                return CANCELLED;
            default:
                LOGGER.warn("Unknown step status received! Set it as SKIPPED");
                return SKIPPED;
        }
    }

    private String getLogLevel(String status) {
        switch (status) {
            case "failed":
                return ERROR_LOG_LEVEL;
            case "stopped":
            case "interrupted":
            case "cancelled":
                return WARN_LOG_LEVEL;
            default:
                return INFO_LOG_LEVEL;
        }
    }

    /**
     * Get step start time to keep the steps order
     * in case previous step startTime == current step startTime or previous step startTime > current step startTime.
     * @param stepStartTimeMap  ConcurrentHashMap of steps within a scenario.
     * @param stepId step ID.
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

    /**
     * Get step start time from Karate
     * IMPORTANT: Implement getting step startTime from Karate v1.4.1. E.g. return new Date(stepResult.getStartTime());
     * Will be fixed in <a href="https://github.com/karatelabs/karate/issues/2383">Karate 1.4.1</a>
     * @param stepResult StepResult object
     * @return step startTime provided by Karate in Date format.
     */

    @SuppressWarnings("unused")
    private Date getStepStartTime(StepResult stepResult) {
        throw new NotImplementedException("TODO: Implement getting step startTime from Karate v1.4.1. E.g. return new Date(stepResult.getStartTime());");
    }
}
