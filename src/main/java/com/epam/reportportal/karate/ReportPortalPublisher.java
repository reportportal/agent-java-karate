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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;
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
    private Maybe<String> stepId;

    public ReportPortalPublisher() {
        this(createLaunchSupplier());
    }

    public ReportPortalPublisher(Supplier<Launch> launch) {
        this.launch = launch;
        this.featureIdMap = new ConcurrentHashMap<>();
        this.scenarioIdMap = new ConcurrentHashMap<>();
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
            sendStepResults(stepResults);
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

    private void startStep(StepResult stepResult) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setName(stepResult.getStep().getPrefix() + " " + stepResult.getStep().getText());
        rq.setStartTime(Calendar.getInstance().getTime());
        rq.setType("STEP");
        rq.setHasStats(false);
        stepId = launch.get().getStepReporter().startNestedStep(rq);
    }

    private void finishStep(StepResult stepResult) {
        if (stepId == null) {
            LOGGER.error("ERROR: Trying to finish unspecified step.");
            return;
        }

        FinishTestItemRQ rq = new FinishTestItemRQ();
        rq.setEndTime(Calendar.getInstance().getTime());
        rq.setStatus(getStepStatus(stepResult.getResult().getStatus()));
        launch.get().getStepReporter().finishNestedStep(rq);
        stepId = null;
    }

    private void sendStepResults(List<StepResult> stepResults) {
        for (StepResult stepResult : stepResults) {
            startStep(stepResult);
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

            finishStep(stepResult);
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
}
