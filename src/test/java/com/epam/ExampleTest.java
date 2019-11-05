package com.epam;

import com.intuit.karate.KarateOptions;
import com.intuit.karate.Results;
import org.junit.Assert;
import org.junit.Test;

@KarateOptions(features = {"classpath:feature/test.feature"})
public class ExampleTest {

    @Test
    public void testParallel() {
        Results results = AgentRunner.parallel(getClass(), 1);
        Assert.assertEquals(0, results.getFailCount());
    }

}