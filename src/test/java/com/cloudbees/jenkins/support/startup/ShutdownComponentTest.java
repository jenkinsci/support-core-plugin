package com.cloudbees.jenkins.support.startup;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public class ShutdownComponentTest {
    @Rule
    public RealJenkinsRule rr = new RealJenkinsRule()
            .javaOptions("-Dcom.cloudbees.jenkins.support.startup.ShutdownComponent.INITIAL_DELAY_SECONDS=0");

    @Test
    public void checkThreadDumpsAreCreated() throws Throwable {
        rr.startJenkins();
        rr.stopJenkins();
        rr.then(ShutdownComponentTest::assertThreadDumpsAreCreated);
    }

    private static void assertThreadDumpsAreCreated(JenkinsRule r) {
        assertThat(ShutdownComponent.get().getLogs().getSize(), greaterThan(0));
    }
}
