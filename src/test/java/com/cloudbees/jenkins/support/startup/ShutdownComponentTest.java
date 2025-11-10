package com.cloudbees.jenkins.support.startup;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

class ShutdownComponentTest {
    @RegisterExtension
    public RealJenkinsExtension extension = new RealJenkinsExtension()
            .javaOptions("-Dcom.cloudbees.jenkins.support.startup.ShutdownComponent.INITIAL_DELAY_SECONDS=0");

    @Test
    void checkThreadDumpsAreCreated() throws Throwable {
        extension.startJenkins();
        extension.stopJenkins();
        extension.then(ShutdownComponentTest::assertThreadDumpsAreCreated);
    }

    private static void assertThreadDumpsAreCreated(JenkinsRule r) {
        assertThat(ShutdownComponent.get().getLogs().getSize(), greaterThan(0));
    }
}
