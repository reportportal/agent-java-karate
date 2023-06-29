package com.epam.reportportal.karate;

import com.intuit.karate.Results;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KarateTest {
    @Test
    void testParallel() {
        Results results = KarateReportPortalRunner
                .path("classpath:feature")
                .outputCucumberJson(true)
                .tags("~@ignore")
                .parallel(1);
        assertEquals(0, results.getFailCount());
        assertEquals(0, results.getErrors().size());
    }
}