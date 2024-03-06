/*
 * Copyright 2024 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.epam.reportportal.karate;

import com.epam.reportportal.service.ReportPortal;
import com.intuit.karate.Results;
import com.intuit.karate.Runner;

/**
 * Karate runner with ReportPortal integration
 */
public class KarateReportPortalRunner {

	/**
	 * Create a new builder for the Karate runner with ReportPortal integration
	 *
	 * @param paths paths to feature files
	 * @param <T>   type of the builder
	 * @return a new builder
	 */
	public static <T extends Builder<T>> Builder<T> path(String... paths) {
		Builder<T> builder = new Builder<>();
		return builder.path(paths);
	}

	/**
	 * Builder for the Karate runner with ReportPortal integration
	 *
	 * @param <T> type of the builder
	 */
	public static class Builder<T extends Builder<T>> extends Runner.Builder<T> {
		private ReportPortal rp;

		/**
		 * Create a new builder
		 */
		public Builder() {
			super();
		}

		/**
		 * Set the ReportPortal instance to use
		 *
		 * @param reportPortal the ReportPortal instance
		 * @return the builder
		 */
		public Builder<T> withReportPortal(ReportPortal reportPortal) {
			rp = reportPortal;
			return this;
		}

		/**
		 * Run the tests in parallel
		 *
		 * @param threadCount number of threads to use
		 * @return the results of the tests
		 */
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
