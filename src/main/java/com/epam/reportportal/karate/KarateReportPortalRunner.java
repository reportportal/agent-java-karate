
package com.epam.reportportal.karate;

import com.intuit.karate.*;
import com.intuit.karate.core.Feature;
import com.intuit.karate.core.FeatureCall;
import com.intuit.karate.core.RuntimeHookFactory;
import com.intuit.karate.core.ScenarioCall;
import com.intuit.karate.driver.DriverOptions;
import com.intuit.karate.driver.DriverRunner;
import com.intuit.karate.http.HttpClientFactory;
import com.intuit.karate.job.JobConfig;
import com.intuit.karate.report.SuiteReports;
import com.intuit.karate.resource.ResourceUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public class KarateReportPortalRunner {

    private static final String CLASSPATH = "classpath:";
    private static final String PATH_DELIMITER = "/";
    private static final Logger LOGGER = LoggerFactory.getLogger(KarateReportPortalRunner.class);

    public KarateReportPortalRunner() {
    }

    public static <T extends Builder<T>> Builder<T> path(String... paths) {
        Builder<T> builder = new Builder<>();
        return builder.path(paths);
    }

    @SuppressWarnings({"unused"})
    public static <T extends Builder<T>> Builder<T> path(List<String> paths) {
        Builder<T> builder = new Builder<>();
        return builder.path(paths);
    }

    public static <T extends Builder<T>> Builder<T> builder() {
        return new Builder<>();
    }

    @SuppressWarnings("unchecked")
    public static class Builder<T extends Builder<T>> extends Runner.Builder<T> {
        ReportPortalPublisher reporter = new ReportPortalPublisher();
        ClassLoader classLoader;
        Class<?> optionsClass;
        String env;
        File workingDir;
        String buildDir;
        String configDir;
        int threadCount;
        int timeoutMinutes;
        String reportDir;
        String scenarioName;
        List<String> tags;
        List<String> paths;
        List<FeatureCall> features;
        String relativeTo;
        final Collection<RuntimeHook> hooks = new ArrayList<>();
        RuntimeHookFactory hookFactory;
        HttpClientFactory clientFactory;
        boolean forTempUse;
        boolean backupReportDir = true;
        boolean outputHtmlReport = true;
        boolean outputJunitXml;
        boolean outputCucumberJson;
        boolean dryRun;
        boolean debugMode;
        Map<String, String> systemProperties;
        Map<String, Object> callSingleCache;
        Map<String, ScenarioCall.Result> callOnceCache;
        SuiteReports suiteReports;
        JobConfig<T> jobConfig;
        Map<String, DriverRunner<?>> drivers;


        public Builder() {
        }

        @Override
        public synchronized Builder<T> copy() {
            Builder<T> b = new Builder<>();
            b.classLoader = this.classLoader;
            b.optionsClass = this.optionsClass;
            b.env = this.env;
            b.workingDir = this.workingDir;
            b.buildDir = this.buildDir;
            b.configDir = this.configDir;
            b.threadCount = this.threadCount;
            b.timeoutMinutes = this.timeoutMinutes;
            b.reportDir = this.reportDir;
            b.scenarioName = this.scenarioName;
            b.tags = this.tags;
            b.paths = this.paths;
            b.features = this.features;
            b.relativeTo = this.relativeTo;
            b.hooks.addAll(this.hooks);
            b.hookFactory = this.hookFactory;
            b.clientFactory = this.clientFactory;
            b.forTempUse = this.forTempUse;
            b.backupReportDir = this.backupReportDir;
            b.outputHtmlReport = this.outputHtmlReport;
            b.outputJunitXml = this.outputJunitXml;
            b.outputCucumberJson = this.outputCucumberJson;
            b.dryRun = this.dryRun;
            b.debugMode = this.debugMode;
            b.systemProperties = this.systemProperties;
            b.callSingleCache = this.callSingleCache;
            b.callOnceCache = this.callOnceCache;
            b.suiteReports = this.suiteReports;
            b.jobConfig = this.jobConfig;
            b.drivers = this.drivers;
            return b;
        }

        @Override
        public List<FeatureCall> resolveAll() {
            if (this.classLoader == null) {
                this.classLoader = Thread.currentThread().getContextClassLoader();
            }

            if (this.clientFactory == null) {
                this.clientFactory = HttpClientFactory.DEFAULT;
            }

            HashMap<String, String> propertiesMap = new HashMap<>();
            System.getProperties().forEach((key, value) -> propertiesMap.put((String) key, (String) value));
            if (this.systemProperties == null) {
                this.systemProperties = new HashMap<>();
                this.systemProperties.putAll(propertiesMap);
            } else {
                this.systemProperties.putAll(propertiesMap);
            }

            String tempOptions = StringUtils.trimToNull(this.systemProperties.get("karate.options"));
            if (tempOptions != null) {
                LOGGER.info("using system property '{}': {}", "karate.options", tempOptions);
                Main ko = Main.parseKarateOptions(tempOptions);
                if (ko.getTags() != null) {
                    this.tags = ko.getTags();
                }

                if (ko.getPaths() != null) {
                    this.paths = ko.getPaths();
                }
            }

            String tempEnv = StringUtils.trimToNull(this.systemProperties.get("karate.env"));
            if (tempEnv != null) {
                LOGGER.info("Using system property '{}': {}", "karate.env", tempEnv);
                this.env = tempEnv;
            } else if (this.env != null) {
                LOGGER.info("karate.env is: '{}'", this.env);
            }

            String tempConfig = StringUtils.trimToNull(this.systemProperties.get("karate.config.dir"));
            if (tempConfig != null) {
                LOGGER.info("using system property '{}': {}", "karate.config.dir", tempConfig);
                this.configDir = tempConfig;
            }

            if (this.workingDir == null) {
                this.workingDir = FileUtils.WORKING_DIR;
            }

            if (this.configDir == null) {
                try {
                    ResourceUtils.getResource(this.workingDir, CLASSPATH + "karate-config.js");
                    this.configDir = CLASSPATH;
                } catch (Exception var5) {
                    this.configDir = this.workingDir.getPath();
                }
            }

            if (!this.configDir.startsWith("file:") && !this.configDir.startsWith(CLASSPATH)) {
                this.configDir = "file:" + this.configDir;
            }

            if (!this.configDir.endsWith(":") && !this.configDir.endsWith("/") && !this.configDir.endsWith("\\")) {
                this.configDir = this.configDir + File.separator;
            }

            if (this.buildDir == null) {
                this.buildDir = FileUtils.getBuildDir();
            }

            if (this.reportDir == null) {
                this.reportDir = this.buildDir + File.separator + "karate-reports";
            }

            if (this.hookFactory != null) {
                this.hook(this.hookFactory.create());
            }

            if (this.features == null) {
                if (this.paths != null && !this.paths.isEmpty()) {
                    if (this.relativeTo != null) {
                        this.paths = this.paths.stream().map(p -> {
                            if (p.startsWith(CLASSPATH)) {
                                return p;
                            } else {
                                if (!p.endsWith(".feature")) {
                                    p = p + ".feature";
                                }

                                return this.relativeTo + PATH_DELIMITER + p;
                            }
                        }).collect(Collectors.toList());
                    }
                } else if (this.relativeTo != null) {
                    this.paths = new ArrayList<>();
                    this.paths.add(this.relativeTo);
                }

                this.features = ResourceUtils.findFeatureFiles(this.workingDir, this.paths, this.scenarioName);
            }

            if (this.callSingleCache == null) {
                this.callSingleCache = new HashMap<>();
            }

            if (this.callOnceCache == null) {
                this.callOnceCache = new HashMap<>();
            }

            if (this.suiteReports == null) {
                this.suiteReports = SuiteReports.DEFAULT;
            }

            if (this.drivers != null) {
                Map<String, DriverRunner<?>> customDrivers = this.drivers;
                this.drivers = (Map<String, DriverRunner<?>>) (Map<?, ?>) DriverOptions.driverRunners();
                this.drivers.putAll(customDrivers);
            } else {
                this.drivers = (Map<String, DriverRunner<?>>) (Map<?, ?>) DriverOptions.driverRunners();
            }

            if (this.jobConfig != null) {
                this.reportDir = this.jobConfig.getExecutorDir();
                if (this.threadCount < 1) {
                    this.threadCount = this.jobConfig.getExecutorCount();
                }

                this.timeoutMinutes = this.jobConfig.getTimeoutMinutes();
            }

            if (this.threadCount < 1) {
                this.threadCount = 1;
            }

            return this.features;
        }

        @Override
        protected T forTempUse() {
            this.forTempUse = true;
            return (T) this;
        }

        @Override
        public T configDir(String dir) {
            this.configDir = dir;
            return (T) this;
        }

        @Override
        public T karateEnv(String env) {
            this.env = env;
            return (T) this;
        }

        @Override
        public T systemProperty(String key, String value) {
            if (this.systemProperties == null) {
                this.systemProperties = new HashMap<>();
            }

            this.systemProperties.put(key, value);
            return (T) this;
        }

        @Override
        public T workingDir(File value) {
            if (value != null) {
                this.workingDir = value;
            }

            return (T) this;
        }

        @Override
        public T buildDir(String value) {
            if (value != null) {
                this.buildDir = value;
            }

            return (T) this;
        }

        @Override
        public T classLoader(ClassLoader value) {
            this.classLoader = value;
            return (T) this;
        }

        @Override
        public T relativeTo(Class clazz) {
            this.relativeTo = CLASSPATH + ResourceUtils.toPathFromClassPathRoot(clazz);
            return (T) this;
        }

        @Override
        public T path(String... value) {
            this.path(Arrays.asList(value));
            return (T) this;
        }

        @Override
        public T path(List<String> value) {
            if (value != null) {
                if (this.paths == null) {
                    this.paths = new ArrayList<>();
                }

                this.paths.addAll(value);
            }

            return (T) this;
        }

        @Override
        public T tags(List<String> value) {
            if (value != null) {
                if (this.tags == null) {
                    this.tags = new ArrayList<>();
                }

                this.tags.addAll(value);
            }

            return (T) this;
        }

        @Override
        public T tags(String... tags) {
            this.tags(Arrays.asList(tags));
            return (T) this;
        }

        @Override
        public T features(Collection<Feature> value) {
            if (value != null) {
                if (this.features == null) {
                    this.features = new ArrayList<>();
                }

                this.features.addAll(value.stream().map(FeatureCall::new).collect(Collectors.toList()));
            }

            return (T) this;
        }

        @Override
        public T features(Feature... value) {
            return this.features(Arrays.asList(value));
        }

        @Override
        public T reportDir(String value) {
            if (value != null) {
                this.reportDir = value;
            }

            return (T) this;
        }

        @Override
        public T scenarioName(String name) {
            this.scenarioName = name;
            return (T) this;
        }

        @Override
        public T timeoutMinutes(int timeoutMinutes) {
            this.timeoutMinutes = timeoutMinutes;
            return (T) this;
        }

        @Override
        public T hook(RuntimeHook hook) {
            if (hook != null) {
                this.hooks.add(hook);
            }

            return (T) this;
        }

        @Override
        public T hooks(Collection<RuntimeHook> hooks) {
            if (hooks != null) {
                this.hooks.addAll(hooks);
            }

            return (T) this;
        }

        @Override
        public T hookFactory(RuntimeHookFactory hookFactory) {
            this.hookFactory = hookFactory;
            return (T) this;
        }

        @Override
        public T clientFactory(HttpClientFactory clientFactory) {
            this.clientFactory = clientFactory;
            return (T) this;
        }

        @Override
        public Builder<T> threads(int value) {
            this.threadCount = value;
            return this;
        }

        @Override
        public T outputHtmlReport(boolean value) {
            this.outputHtmlReport = value;
            return (T) this;
        }

        @Override
        public T backupReportDir(boolean value) {
            this.backupReportDir = value;
            return (T) this;
        }

        @Override
        public T outputCucumberJson(boolean value) {
            this.outputCucumberJson = value;
            return (T) this;
        }

        @Override
        public T outputJunitXml(boolean value) {
            this.outputJunitXml = value;
            return (T) this;
        }

        @Override
        public T dryRun(boolean value) {
            this.dryRun = value;
            return (T) this;
        }

        @Override
        public T debugMode(boolean value) {
            this.debugMode = value;
            return (T) this;
        }

        @Override
        public T callSingleCache(Map<String, Object> value) {
            this.callSingleCache = value;
            return (T) this;
        }

        @Override
        public T callOnceCache(Map<String, ScenarioCall.Result> value) {
            this.callOnceCache = value;
            return (T) this;
        }

        @Override
        public T suiteReports(SuiteReports value) {
            this.suiteReports = value;
            return (T) this;
        }

        @Override
        public T customDrivers(Map<String, DriverRunner> customDrivers) {
            this.drivers = (Map<String, DriverRunner<?>>) (Map<?, ?>) customDrivers;
            return (T) this;
        }

        @Override
        public Results jobManager(JobConfig value) {
            this.jobConfig = value;
            KarateReportPortalSuite suite = new KarateReportPortalSuite(this, reporter);
            suite.run();
            return suite.buildResults();
        }

        @Override
        public Results parallel(int threadCount) {
            reporter.startLaunch();
            this.threads(threadCount);
            KarateReportPortalSuite suite = new KarateReportPortalSuite(this, reporter);
            suite.run();
            Results results = suite.buildResults();
            reporter.finishLaunch();
            return results;
        }

        @Override
        public String toString() {
            return this.paths + "";
        }
    }
}
