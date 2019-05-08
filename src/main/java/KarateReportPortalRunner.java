package test.java.utilities;

        import com.intuit.karate.CallContext;
        import com.intuit.karate.ScriptContext;
        import com.intuit.karate.cucumber.DummyFormatter;
        import com.intuit.karate.cucumber.DummyReporter;
        import com.intuit.karate.cucumber.KarateFeature;
        import com.intuit.karate.cucumber.KarateHtmlReporter;
        import com.intuit.karate.cucumber.KarateRuntime;
        import com.intuit.karate.cucumber.KarateRuntimeOptions;
        import com.intuit.karate.junit4.Karate;
        import com.intuit.karate.junit4.KarateFeatureRunner;
        import cucumber.runtime.RuntimeOptions;
        import cucumber.runtime.RuntimeOptionsFactory;
        import cucumber.runtime.junit.FeatureRunner;
        import cucumber.runtime.junit.JUnitOptions;
        import cucumber.runtime.junit.JUnitReporter;
        import gherkin.formatter.model.Match;
        import gherkin.formatter.model.Result;
        import gherkin.formatter.model.Scenario;
        import gherkin.formatter.model.Step;
        import java.io.IOException;
        import java.util.ArrayList;
        import java.util.HashMap;
        import java.util.Iterator;import java.util.List;
        import java.util.Map;
        import org.junit.Test;
        import org.junit.runner.Description;
        import org.junit.runner.notification.RunNotifier;
        import org.junit.runners.model.FrameworkMethod;
        import org.junit.runners.model.InitializationError;
        import org.slf4j.Logger;
        import org.slf4j.LoggerFactory;

        public class KarateReportPortalRunner extends Karate {

        private static final Logger logger = LoggerFactory.getLogger(Karate.class);
        private final List<FeatureRunner> children;
    private final JUnitReporter reporter;
    private final KarateHtmlReporter htmlReporter;
    private final Map<Integer, KarateFeatureRunner> featureMap;
    private final JUnitReporter jUnitReporter;

    public KarateReportPortalRunner(Class clazz) throws InitializationError, IOException {
    super(clazz);
    ClassLoader classLoader = clazz.getClassLoader();

    List<FrameworkMethod> testMethods = this.getTestClass().getAnnotatedMethods(Test.class);
        if (!testMethods.isEmpty()) {
        logger.warn("WARNING: there are methods annotated with '@Test', they will NOT be run when using '@RunWith(Karate.class)'");
        }

        RuntimeOptionsFactory runtimeOptionsFactory = new RuntimeOptionsFactory(clazz);
        RuntimeOptions runtimeOptions = runtimeOptionsFactory.create();
        JUnitOptions junitOptions = new JUnitOptions(runtimeOptions.getJunitOptions());
        this.jUnitReporter = new JUnitReporter(runtimeOptions.reporter(classLoader), runtimeOptions.formatter(classLoader), runtimeOptions.isStrict(), junitOptions);

        KarateRuntimeOptions kro = new KarateRuntimeOptions(clazz);
        RuntimeOptions ro = kro.getRuntimeOptions();
        // JUnitOptions junitOptions = new JUnitOptions(ro.getJunitOptions());
        this.htmlReporter = new KarateHtmlReporter(new DummyReporter(), new DummyFormatter());
        this.reporter = new JUnitReporter(this.htmlReporter, this.htmlReporter, ro.isStrict(), junitOptions) {
        final List<Step> steps = new ArrayList();
            final List<Match> matches = new ArrayList();

                public void startOfScenarioLifeCycle(Scenario scenario) {
                this.steps.clear();
                this.matches.clear();
                super.startOfScenarioLifeCycle(scenario);
                }

                public void step(Step step) {
                this.steps.add(step);
                }

                public void match(Match match) {
                this.matches.add(match);
                }

                public void result(Result result) {
                Step step = (Step)this.steps.remove(0);
                Match match = (Match)this.matches.remove(0);
                CallContext callContext = new CallContext((ScriptContext)null, 0, (Map)null, -1, false, false, (String)null);
                KarateReportPortalRunner.this.htmlReporter.karateStep(step, match, result, callContext);
                super.step(step);
                super.match(match);
                super.result(result);
                }

                public void eof() {
                try {
                super.eof();
                } catch (Exception var2) {
                KarateReportPortalRunner.logger.warn("WARNING: cucumber native plugin / formatter failed: " + var2.getMessage());
                }

                }
                };
                List<KarateFeature> list = KarateFeature.loadFeatures(kro);
                    this.children = new ArrayList(list.size());
                    this.featureMap = new HashMap(list.size());
                    Iterator var7 = list.iterator();

                    while(var7.hasNext()) {
                    KarateFeature kf = (KarateFeature)var7.next();
                    KarateRuntime kr = kf.getRuntime(this.htmlReporter);
                    FeatureRunner runner = new FeatureRunner(kf.getFeature(), kr, this.jUnitReporter);
                    this.children.add(runner);
                    this.featureMap.put(runner.hashCode(), new KarateFeatureRunner(kf, kr));

                    }

                    }

                    public List<FeatureRunner> getChildren() {
                        return this.children;
                        }

                        protected Description describeChild(FeatureRunner child) {
                        return child.getDescription();
                        }
                        protected void runChild(FeatureRunner child, RunNotifier notifier) {
                        child.run(notifier);
                        }



                        public void run(RunNotifier notifier) {
                        super.run(notifier);
                        this.jUnitReporter.done();
                        this.jUnitReporter.close() ;
                        if (this.reporter != null) {
                        this.reporter.done();
                        this.reporter.close();
                        }

                        }


                        }
