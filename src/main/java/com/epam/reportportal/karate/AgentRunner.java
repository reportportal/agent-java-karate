package com.epam.reportportal.karate;

import com.intuit.karate.*;
import com.intuit.karate.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class AgentRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentRunner.class);
    public static Results parallel(Class<?> clazz, int threadCount) {
        return parallel(clazz, threadCount, null);
    }

    public static Results parallel(Class<?> clazz, int threadCount, String reportDir) {
        RunnerOptions options = RunnerOptions.fromAnnotationAndSystemProperties(clazz);
        return parallel(options.getTags(), options.getFeatures(), options.getName(), null, threadCount, reportDir);
    }

    public static Results parallel(List<String> tags, List<String> paths, int threadCount, String reportDir) {
        return parallel(tags, paths, null, null, threadCount, reportDir);
    }

    public static Results parallel(List<String> tags, List<String> paths, String scenarioName,
                                   Collection<ExecutionHook> hooks, int threadCount, String reportDir) {
        String tagSelector = tags == null ? null : Tags.fromKarateOptionsTags(tags);
        List<Resource> files = FileUtils.scanForFeatureFiles(paths, Thread.currentThread().getContextClassLoader());
        return parallel(tagSelector, files, scenarioName, hooks, threadCount, reportDir);
    }

    public static Results parallel(String tagSelector, List<Resource> resources, int threadCount, String reportDir) {
        return parallel(tagSelector, resources, null, null, threadCount, reportDir);
    }

    public static Results parallel(String tagSelector, List<Resource> resources, String scenarioName,
                                   Collection<ExecutionHook> hooks, int threadCount, String reportDir) {
        if (threadCount < 1) {
            threadCount = 1;
        }
        if (reportDir == null) {
            reportDir = FileUtils.getBuildDir() + File.separator + "surefire-reports";
            new File(reportDir).mkdirs();
        }

        RPReporter reporter = new RPReporter();
        reporter.startLaunch();

        final String finalReportDir = reportDir;
        Results results = Results.startTimer(threadCount);
        ExecutorService featureExecutor = Executors.newFixedThreadPool(threadCount);
        ExecutorService scenarioExecutor = Executors.newWorkStealingPool(threadCount);
        int executedFeatureCount = 0;
        try {
            int count = resources.size();
            CountDownLatch latch = new CountDownLatch(count);
            List<FeatureResult> featureResults = new ArrayList(count);
            for (int i = 0; i < count; i++) {
                Resource resource = resources.get(i);
                int index = i + 1;

                Feature feature = FeatureParser.parse(resource);
                feature.setCallName(scenarioName);
                feature.setCallLine(resource.getLine());
                FeatureContext featureContext = new FeatureContext(null, feature, tagSelector);
                CallContext callContext = CallContext.forAsync(feature, hooks, null, false);


                ExecutionContext execContext = new ExecutionContext(results.getStartTime(), featureContext, callContext, reportDir,
                        r -> featureExecutor.submit(r), scenarioExecutor);
                featureResults.add(execContext.result);
                FeatureExecutionUnit unit = new FeatureExecutionUnit(execContext);
                unit.setNext(() -> {
                    FeatureResult result = execContext.result;
                    if (result.getScenarioCount() > 0) {

                        reporter.startFeature(result);

                        File file = Engine.saveResultJson(finalReportDir, result, null);
                        if (result.getScenarioCount() < 500) {
                            Engine.saveResultXml(finalReportDir, result, null);
                        }
                        String status = result.isFailed() ? "fail" : "pass";
                        LOGGER.info("<<{}>> feature {} of {}: {}", status, index, count, feature.getRelativePath());
                        result.printStats(file.getPath());

                        reporter.finishFeature(result);
                    } else {
                        results.addToSkipCount(1);
                        if (LOGGER.isTraceEnabled()) {
                            LOGGER.trace("<<skip>> feature {} of {}: {}", index, count, feature.getRelativePath());
                        }
                    }
                    latch.countDown();

                });
                featureExecutor.submit(unit);

            }
            latch.await();
            results.stopTimer();
            for (FeatureResult result : featureResults) {
                int scenarioCount = result.getScenarioCount();
                results.addToScenarioCount(scenarioCount);
                if (scenarioCount != 0) {
                    executedFeatureCount++;
                }
                results.addToFailCount(result.getFailedCount());
                results.addToTimeTaken(result.getDurationMillis());
                if (result.isFailed()) {
                    results.addToFailedList(result.getPackageQualifiedName(), result.getErrorMessages());
                }
                results.addScenarioResults(result.getScenarioResults());
            }

        } catch (Exception e) {
            LOGGER.error("karate parallel runner failed: {}", e.getMessage());
            results.setFailureReason(e);
        } finally {
            featureExecutor.shutdownNow();
            scenarioExecutor.shutdownNow();
        }
        results.setFeatureCount(executedFeatureCount);
        results.printStats(threadCount);
        Engine.saveStatsJson(reportDir, results, null);
        Engine.saveTimelineHtml(reportDir, results, null);
        results.setReportDir(reportDir);

        reporter.finishLaunch();

        return results;
    }

    public static Map<String, Object> runFeature(Feature feature, Map<String, Object> vars, boolean evalKarateConfig) {
        CallContext callContext = new CallContext(vars, evalKarateConfig);
        FeatureResult result = Engine.executeFeatureSync(null, feature, null, callContext);
        if (result.isFailed()) {
            throw result.getErrorsCombined();
        }
        return result.getResultAsPrimitiveMap();
    }

    public static Map<String, Object> runFeature(File file, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = FeatureParser.parse(file);
        return runFeature(feature, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(Class relativeTo, String path, Map<String, Object> vars, boolean evalKarateConfig) {
        File file = FileUtils.getFileRelativeTo(relativeTo, path);
        return runFeature(file, vars, evalKarateConfig);
    }

    public static Map<String, Object> runFeature(String path, Map<String, Object> vars, boolean evalKarateConfig) {
        Feature feature = FeatureParser.parse(path);
        return runFeature(feature, vars, evalKarateConfig);
    }

    public static void callAsync(String path, Map<String, Object> arg, ExecutionHook hook, Consumer<Runnable> system, Runnable next) {
        Feature feature = FileUtils.parseFeatureAndCallTag(path);
        FeatureContext featureContext = new FeatureContext(null, feature, null);
        CallContext callContext = CallContext.forAsync(feature, Collections.singletonList(hook), arg, true);
        ExecutionContext executionContext = new ExecutionContext(System.currentTimeMillis(), featureContext, callContext, null, system, null);
        FeatureExecutionUnit exec = new FeatureExecutionUnit(executionContext);
        exec.setNext(next);
        system.accept(exec);
    }

}

