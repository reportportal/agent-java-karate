package com.epam.reportportal.karate;

import com.intuit.karate.Results;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class KarateTest {
    @Test
    public void testParallel() {
        Results results = KarateReportPortalRunner
                .path("classpath:feature")
                .outputCucumberJson(true)
                .tags("~@ignore")
                .parallel(1);
        assertEquals(0, results.getFailCount());
        assertEquals(0, results.getErrors().size());
    }
}