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
    private static final String INFO_LEVEL = "INFO";
    private static final String ERROR_LEVEL = "ERROR";
    private static final String PASSED = "PASSED";
    private static final String FAILED = "FAILED";
    private final Supplier<Launch> launch;
    private final ConcurrentHashMap<String, Maybe<String>> featureIdMap;
    private final ConcurrentHashMap<String, Maybe<String>> scenarioIdMap;
    private Maybe<String> scenarioId;

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
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setName(scenarioResult.getScenario().getName());
        rq.setStartTime(Calendar.getInstance().getTime());
        rq.setType("STEP");
        rq.setHasStats(false);

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
        launch.get().finishTestItem(scenarioId, rq);
        launch.get().finishTestItem(scenarioIdMap.remove(scenarioResult.getScenario().getName()), rq);
    }

    private void sendStepResults(List<StepResult> stepResults) {
        for (StepResult stepResult : stepResults) {
            Result result = stepResult.getResult();
            String logLevel = PASSED.equalsIgnoreCase(result.getStatus()) ? INFO_LEVEL : ERROR_LEVEL;
            Step step = stepResult.getStep();

            if (step.getDocString() != null) {
                sendLog("\n-----------------DOC_STRING-----------------\n" + step.getDocString(), logLevel);
            }

            sendLog(step.getPrefix() + " " + step.getText() + "\n\n" + stepResult.getStepLog(), logLevel);
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
}
