package com.epam.reportportal.karate;

import com.epam.reportportal.service.ReportPortal;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;

public class KarateReportPortalRunner {

	public static <T extends Builder<T>> Builder<T> path(String... paths) {
		Builder<T> builder = new Builder<>();
		return builder.path(paths);
	}

	public static class Builder<T extends Builder<T>> extends Runner.Builder<T> {
		private ReportPortal rp;

		public Builder() {
			super();
		}

		public Builder<T> withReportPortal(ReportPortal reportPortal) {
			rp = reportPortal;
			return this;
		}

		@Override
		public Results parallel(int threadCount) {
			if (rp == null) {
				rp = ReportPortal.builder().build();
			}
			ReportPortalPublisher reporter = new ReportPortalPublisher(rp);
			reporter.startLaunch();
			Results results = super.parallel(threadCount);
			results.getFeatureResults().forEach(f -> {
				reporter.startFeature(f);
				reporter.finishFeature(f);
			});
			reporter.finishLaunch();
			return results;
		}
	}
}
