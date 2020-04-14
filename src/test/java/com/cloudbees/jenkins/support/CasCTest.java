package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.filter.ContentFilters;
import io.jenkins.plugins.casc.misc.RoundTripAbstractTest;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import static org.junit.Assert.assertTrue;

public class CasCTest extends RoundTripAbstractTest {

    @Override
    protected void assertConfiguredAsExpected(RestartableJenkinsRule restartableJenkinsRule, String s) {
        assertTrue("JCasC should have configured support core to anonymize contents, but it didn't", ContentFilters.get().isEnabled());
    }

    @Override
    protected String stringInLogExpected() {
        return ".enabled = true";
    }
}
