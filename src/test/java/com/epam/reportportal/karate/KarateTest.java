package com.epam.reportportal.karate;

import com.intuit.karate.Results;
import org.junit.Assert;
import org.junit.Test;

public class KarateTest {
    @Test
    public void testParallel() {
        Results results = KarateReportPortalRunner
                .path("classpath:feature/Test.feature")
                .outputCucumberJson(true)
                .tags("~@ignore")
                .parallel(1);
        Assert.assertEquals(0, results.getFailCount());
        Assert.assertEquals(0, results.getErrors().size());
    }
}