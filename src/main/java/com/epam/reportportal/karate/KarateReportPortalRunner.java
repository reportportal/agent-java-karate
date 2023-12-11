
package com.epam.reportportal.karate;

import com.intuit.karate.*;
import com.intuit.karate.core.*;

import java.util.*;
import java.util.stream.Collectors;

public class KarateReportPortalRunner {

    public static <T extends Builder<T>> Builder<T> path(String... paths) {
        Builder<T> builder = new Builder<>();
        return builder.path(paths);
    }

    public static class Builder<T extends Builder<T>> extends Runner.Builder<T> {
        ReportPortalPublisher reporter = new ReportPortalPublisher();

        public Builder() {
            super();
        }

        @Override
        public Results parallel(int threadCount) {
            reporter.startLaunch();
            Results results = super.parallel(threadCount);
            List<FeatureResult> featureResults = results.getFeatureResults().collect(Collectors.toList());
            featureResults.forEach(f -> reporter.startFeature(f));
            featureResults.forEach(f -> reporter.finishFeature(f));
            reporter.finishLaunch();
            return results;
        }
    }
}
