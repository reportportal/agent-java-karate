package com.epam.reportportal.karate.utils;

import com.epam.reportportal.karate.KarateReportPortalRunner;
import com.intuit.karate.Results;

public class TestUtils {
	private TestUtils() {
	}

	public static Results run(String... paths) {
		return KarateReportPortalRunner
				.path(paths)
				.outputCucumberJson(false)
				.parallel(1);
	}
}
