package com.epam.reportportal.karate;

import com.intuit.karate.*;
import com.intuit.karate.core.*;
import com.intuit.karate.driver.DriverRunner;
import com.intuit.karate.http.HttpClientFactory;
import com.intuit.karate.job.JobManager;
import com.intuit.karate.report.ReportUtils;
import com.intuit.karate.report.SuiteReports;
import com.intuit.karate.resource.Resource;
import com.intuit.karate.resource.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

public class KarateReportPortalSuite extends Suite {
    private static final Logger logger = LoggerFactory.getLogger(Suite.class);

    public final long startTime;
    protected long endTime;
    protected int skippedCount;
    private final AtomicBoolean abort;
    public final String env;
    public final String tagSelector;
    public final boolean dryRun;
    public final boolean debugMode;
    public final File workingDir;
    public final String buildDir;
    public final String reportDir;
    public final ClassLoader classLoader;
    public final int threadCount;
    public final int timeoutMinutes;
    public final int featuresFound;
    public final List<FeatureCall> features;
    public final List<CompletableFuture<Boolean>> futures;
    public final Set<File> featureResultFiles;
    public final Collection<RuntimeHook> hooks;
    public final HttpClientFactory clientFactory;
    public final Map<String, String> systemProperties;
    public final boolean backupReportDir;
    public final SuiteReports suiteReports;
    public final boolean outputHtmlReport;
    public final boolean outputCucumberJson;
    public final boolean outputJunitXml;
    public final boolean parallel;
    public final ExecutorService scenarioExecutor;
    public final ExecutorService pendingTasks;
    public final JobManager<KarateReportPortalRunner> jobManager;
    public final String karateBase;
    public final String karateConfig;
    public final String karateConfigEnv;
    public final Map<String, Object> callSingleCache;
    public final Map<String, ScenarioCall.Result> callOnceCache;
    private final ReentrantLock progressFileLock;
    public final Map<String, DriverRunner<?>> drivers;

    public final ReportPortalPublisher reporter;

    private String read(String name) {
        try {
            Resource resource = ResourceUtils.getResource(this.workingDir, name);
            logger.debug("[config] {}", resource.getPrefixedPath());
            return FileUtils.toString(resource.getStream());
        } catch (Exception var3) {
            logger.trace("file not found: {} - {}", name, var3.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unused")
    public static KarateReportPortalSuite forTempUse(HttpClientFactory hcf, ReportPortalPublisher reporter) {
        return new KarateReportPortalSuite(KarateReportPortalRunner.builder().clientFactory(hcf).forTempUse(), reporter);
    }

    @SuppressWarnings("unused")
    public KarateReportPortalSuite(ReportPortalPublisher reporter) {
        this(KarateReportPortalRunner.builder(), reporter);
    }

    public KarateReportPortalSuite(KarateReportPortalRunner.Builder<?> rb, ReportPortalPublisher reporter) {
        this.abort = new AtomicBoolean(false);
        this.reporter = reporter;
        if (rb.forTempUse) {
            this.dryRun = false;
            this.debugMode = false;
            this.backupReportDir = false;
            this.outputHtmlReport = false;
            this.outputCucumberJson = false;
            this.outputJunitXml = false;
            this.classLoader = Thread.currentThread().getContextClassLoader();
            this.clientFactory = rb.clientFactory == null ? HttpClientFactory.DEFAULT : rb.clientFactory;
            this.startTime = -1L;
            this.env = rb.env;
            this.systemProperties = null;
            this.tagSelector = null;
            this.threadCount = -1;
            this.timeoutMinutes = -1;
            this.hooks = Collections.emptyList();
            this.features = null;
            this.featuresFound = -1;
            this.futures = null;
            this.featureResultFiles = null;
            this.workingDir = FileUtils.WORKING_DIR;
            this.buildDir = FileUtils.getBuildDir();
            this.reportDir = FileUtils.getBuildDir();
            this.karateBase = null;
            this.karateConfig = null;
            this.karateConfigEnv = null;
            this.parallel = false;
            this.scenarioExecutor = null;
            this.pendingTasks = null;
            this.callSingleCache = null;
            this.callOnceCache = null;
            this.suiteReports = null;
            this.jobManager = null;
            this.progressFileLock = null;
            this.drivers = null;
        } else {
            this.startTime = System.currentTimeMillis();
            rb.resolveAll();
            this.backupReportDir = rb.backupReportDir;
            this.outputHtmlReport = rb.outputHtmlReport;
            this.outputCucumberJson = rb.outputCucumberJson;
            this.outputJunitXml = rb.outputJunitXml;
            this.dryRun = rb.dryRun;
            this.debugMode = rb.debugMode;
            this.classLoader = rb.classLoader;
            this.clientFactory = rb.clientFactory;
            this.env = rb.env;
            this.systemProperties = rb.systemProperties;
            this.tagSelector = Tags.fromKarateOptionsTags(rb.tags);
            this.hooks = rb.hooks;
            this.features = rb.features;
            this.featuresFound = this.features.size();
            this.futures = new ArrayList<>(this.featuresFound);
            this.callSingleCache = rb.callSingleCache;
            this.callOnceCache = rb.callOnceCache;
            this.suiteReports = rb.suiteReports;
            this.featureResultFiles = new HashSet<>();
            this.workingDir = rb.workingDir;
            this.buildDir = rb.buildDir;
            this.reportDir = rb.reportDir;
            this.karateBase = this.read("classpath:karate-base.js");
            this.karateConfig = this.read(rb.configDir + "karate-config.js");
            if (this.env != null) {
                this.karateConfigEnv = this.read(rb.configDir + "karate-config-" + this.env + ".js");
            } else {
                this.karateConfigEnv = null;
            }

            if (rb.jobConfig != null) {
                this.jobManager = new JobManager<>(rb.jobConfig);
            } else {
                this.jobManager = null;
            }

            this.drivers = rb.drivers;
            this.threadCount = rb.threadCount;
            this.timeoutMinutes = rb.timeoutMinutes;
            this.parallel = this.threadCount > 1;
            if (this.parallel) {
                this.scenarioExecutor = Executors.newFixedThreadPool(this.threadCount);
                this.pendingTasks = Executors.newSingleThreadExecutor();
            } else {
                this.scenarioExecutor = SyncExecutorService.INSTANCE;
                this.pendingTasks = SyncExecutorService.INSTANCE;
            }

            this.progressFileLock = new ReentrantLock();
        }

    }

    @Override
    public void run() {
        try {
            if (this.backupReportDir) {
                this.backupReportDirIfExists();
            }

            this.hooks.forEach(h -> h.beforeSuite(this));
            int index = 0;

            for (FeatureCall feature : this.features) {
                ++index;
                FeatureRuntime fr = FeatureRuntime.of(this, feature);
                CompletableFuture<Boolean> future = new CompletableFuture<>();
                this.futures.add(future);
                int finalIndex = index;
                fr.setNext(() -> {
                    this.onFeatureDone(fr.result, finalIndex);
                    future.complete(Boolean.TRUE);
                });
                this.pendingTasks.submit(fr);
            }

            if (this.featuresFound > 1) {
                logger.debug("waiting for {} features to complete", this.featuresFound);
            }

            if (this.jobManager != null) {
                this.jobManager.start();
            }

            CompletableFuture<?>[] futuresArray = this.futures.toArray(new CompletableFuture[0]);
            if (this.timeoutMinutes > 0) {
                CompletableFuture.allOf(futuresArray).get(this.timeoutMinutes, TimeUnit.MINUTES);
            } else {
                CompletableFuture.allOf(futuresArray).join();
            }

            this.endTime = System.currentTimeMillis();
        } catch (Throwable var10) {
            logger.error("runner failed: " + var10);
        } finally {
            this.scenarioExecutor.shutdownNow();
            this.pendingTasks.shutdownNow();
            if (this.jobManager != null) {
                this.jobManager.server.stop();
            }

            this.hooks.forEach(h -> h.afterSuite(this));
        }

    }

    @Override
    public void abort() {
        this.abort.set(true);
    }

    @Override
    public boolean isAborted() {
        return this.abort.get();
    }

    @Override
    public void saveFeatureResults(FeatureResult fr) {
        File file = ReportUtils.saveKarateJson(this.reportDir, fr, null);
        synchronized (this.featureResultFiles) {
            this.featureResultFiles.add(file);
        }

        if (this.outputHtmlReport) {
            this.suiteReports.featureReport(this, fr).render();
        }

        if (this.outputCucumberJson) {
            ReportUtils.saveCucumberJson(this.reportDir, fr, null);
        }

        if (this.outputJunitXml) {
            ReportUtils.saveJunitXml(this.reportDir, fr, null);
        }

        fr.printStats();
    }


    private void onFeatureDone(FeatureResult fr, int index) {
        reporter.startFeature(fr);
        if (fr.getScenarioCount() > 0) {
            try {
                this.saveFeatureResults(fr);

                String status = fr.isFailed() ? "fail" : "pass";
                logger.info("<<{}>> feature {} of {} ({} remaining) {}", status, index, this.featuresFound, this.getFeaturesRemaining() - 1L, fr.getFeature());
            } catch (Throwable var4) {
                logger.error("<<error>> unable to write report file(s): {} - {}", fr.getFeature(), var4 + "");
                fr.printStats();
            }
        } else {
            ++this.skippedCount;
            if (logger.isTraceEnabled()) {
                logger.trace("<<skip>> feature {} of {}: {}", index, this.featuresFound, fr.getFeature());
            }
        }

        if (this.progressFileLock.tryLock()) {
            this.saveProgressJson();
            this.progressFileLock.unlock();
        }
        reporter.finishFeature(fr);
    }

    @Override
    public Stream<FeatureResult> getFeatureResults() {
        return this.featureResultFiles.stream().sorted().map(file -> FeatureResult.fromKarateJson(this.workingDir, Json.of(FileUtils.toString(file)).asMap()));
    }

    @Override
    public Stream<ScenarioResult> getScenarioResults() {
        return this.getFeatureResults().flatMap(fr -> fr.getScenarioResults().stream());
    }

    @Override
    public ScenarioResult retryScenario(Scenario scenario) {
        FeatureRuntime fr = FeatureRuntime.of(this, new FeatureCall(scenario.getFeature()));
        ScenarioRuntime runtime = new ScenarioRuntime(fr, scenario);
        runtime.run();
        return runtime.result;
    }

    @Override
    public Results updateResults(ScenarioResult sr) {
        Scenario scenario = sr.getScenario();
        File file = new File(this.reportDir + File.separator + scenario.getFeature().getKarateJsonFileName());
        FeatureResult fr;
        if (file.exists()) {
            String json = FileUtils.toString(file);
            fr = FeatureResult.fromKarateJson(this.workingDir, Json.of(json).asMap());
        } else {
            fr = new FeatureResult(scenario.getFeature());
        }

        List<ScenarioResult> scenarioResults = fr.getScenarioResults();
        int count = scenarioResults.size();
        int found = -1;

        for (int i = 0; i < count; ++i) {
            ScenarioResult temp = scenarioResults.get(i);
            if (temp.getScenario().isEqualTo(scenario)) {
                found = i;
                break;
            }
        }

        if (found != -1) {
            scenarioResults.set(found, sr);
        } else {
            scenarioResults.add(sr);
        }

        fr.sortScenarioResults();
        this.saveFeatureResults(fr);
        return this.buildResults();
    }

    private void backupReportDirIfExists() {
        File file = new File(this.reportDir);
        if (file.exists()) {
            File dest = new File(this.reportDir + "_" + System.currentTimeMillis());
            if (file.renameTo(dest)) {
                logger.info("backed up existing '{}' dir to: {}", this.reportDir, dest);
            } else {
                logger.warn("failed to backup existing dir: {}", file);
            }
        }
    }

    @Override
    public long getFeaturesRemaining() {
        return this.futures.stream().filter((f) -> !f.isDone()).count();
    }

    private void saveProgressJson() {
        long remaining = this.getFeaturesRemaining() - 1L;
        Map<String, Object> map = Collections.singletonMap("featuresRemaining", remaining);
        String json = JsonUtils.toJson(map);
        File file = new File(this.reportDir + File.separator + "karate-progress-json.txt");
        FileUtils.writeToFile(file, json);
    }

    public Results buildResults() {
        return Results.of(this);
    }
}

