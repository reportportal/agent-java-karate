package com.epam.reportportal.karate;

import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.ta.reportportal.ws.model.FinishExecutionRQ;
import com.epam.ta.reportportal.ws.model.FinishTestItemRQ;
import com.epam.ta.reportportal.ws.model.StartTestItemRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import com.intuit.karate.core.FeatureResult;
import com.intuit.karate.core.ScenarioResult;
import io.reactivex.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rp.com.google.common.base.Suppliers;

import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static rp.com.google.common.base.Strings.isNullOrEmpty;

class RPReporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RPReporter.class);
    private static final String INFO_LEVEL = "INFO";
    private static final String ERROR_LEVEL = "ERROR";
    private static final String PASSED = "passed";
    private static final String FAILED = "failed";
    private Supplier<Launch> launch;
    private ConcurrentHashMap<String, Maybe<String>> featureIdMap;

    private Maybe<String> scenarioId;

    RPReporter() {
        this(Suppliers.memoize(() -> {
            final ReportPortal reportPortal = ReportPortal.builder().build();
            ListenerParameters parameters = reportPortal.getParameters();
            StartLaunchRQ rq1 = new StartLaunchRQ();
            rq1.setName(parameters.getLaunchName());
            rq1.setStartTime(Calendar.getInstance().getTime());
            rq1.setMode(parameters.getLaunchRunningMode());
            if (!isNullOrEmpty(parameters.getDescription())) {
                rq1.setDescription(parameters.getDescription());
            }
            StartLaunchRQ rq = rq1;
            rq.setStartTime(Calendar.getInstance().getTime());
            return reportPortal.newLaunch(rq);
        }));
    }

    RPReporter(Supplier<Launch> launch) {
        this.launch = launch;
        featureIdMap = new ConcurrentHashMap<>();
    }

    void startLaunch() {
        this.launch.get().start();
    }

    void finishLaunch() {
        FinishExecutionRQ rq = new FinishExecutionRQ();
        rq.setEndTime(Calendar.getInstance().getTime());

        ListenerParameters parameters = launch.get().getParameters();
        LOGGER.info("LAUNCH URL: {}/ui/#{}/launches/all/{}", parameters.getBaseUrl(), parameters.getProjectName(),
                System.getProperty("rp.launch.id"));

        launch.get().finish(rq);
    }

    void startFeature(FeatureResult featureResult) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setName(String.valueOf(featureResult.toMap().get("name")));
        rq.setStartTime(Calendar.getInstance().getTime());
        rq.setType("STORY");
        Maybe<String> featureId = launch.get().startTestItem(rq);
        featureIdMap.put(featureResult.getCallName(), featureId);
    }

    void finishFeature(FeatureResult featureResult) {
        if (!featureIdMap.containsKey(featureResult.getCallName())) {
            LOGGER.error("BUG: Trying to finish unspecified feature.");
        }

        for (ScenarioResult scenarioResult : featureResult.getScenarioResults()) {
            startScenario(scenarioResult, featureResult);
            List<Map<String, Map>> stepResultsToMap = (List<Map<String, Map>>) scenarioResult.toMap().get("steps");
            for (Map<String, Map> step : stepResultsToMap) {
                Map stepResult = step.get("result");
                String logLevel = PASSED.equals(stepResult.get("status")) ? INFO_LEVEL : ERROR_LEVEL;
                if (step.get("doc_string") != null) {
                    sendLog("STEP: " + step.get("name") +
                            "\n-----------------DOC_STRING-----------------\n" + step.get("doc_string"), logLevel);
                } else {
                    sendLog("STEP: " + step.get("name"), logLevel);
                }
            }
            finishScenario(scenarioResult);
        }

        FinishTestItemRQ rq = new FinishTestItemRQ();
        rq.setEndTime(Calendar.getInstance().getTime());
        rq.setStatus(featureResult.isFailed() ? FAILED : PASSED);

        launch.get().finishTestItem(featureIdMap.remove(featureResult.getCallName()), rq);
    }

    private void startScenario(ScenarioResult scenarioResult, FeatureResult featureResult) {
        StartTestItemRQ rq = new StartTestItemRQ();
        rq.setName("SCENARIO: " + scenarioResult.toMap().get("name"));
        rq.setStartTime(Calendar.getInstance().getTime());
        rq.setType("SCENARIO");

        scenarioId = launch.get().startTestItem(featureIdMap.get(featureResult.getCallName()), rq);
    }

    private void finishScenario(ScenarioResult scenarioResult) {
        if (scenarioId == null) {
            LOGGER.error("BUG: Trying to finish unspecified scenario.");
            return;
        }

        FinishTestItemRQ rq = new FinishTestItemRQ();
        rq.setEndTime(Calendar.getInstance().getTime());
        rq.setStatus(scenarioResult.getFailureMessageForDisplay() == null ? PASSED : FAILED);
        launch.get().finishTestItem(scenarioId, rq);
        scenarioId = null;
    }

    private void sendLog(final String message, final String level) {
        ReportPortal.emitLog(itemId -> {
            SaveLogRQ rq = new SaveLogRQ();
            rq.setMessage(message);
            rq.setTestItemId(itemId);
            rq.setLevel(level);
            rq.setLogTime(Calendar.getInstance().getTime());
            return rq;
        });
    }

}
