/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.support.startup;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import hudson.init.InitMilestone;
import java.io.IOException;
import jenkins.model.Jenkins;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public class StartupReportTest {
    @Rule
    public RealJenkinsRule rr = new RealJenkinsRule()
            .javaOptions("-Dcom.cloudbees.jenkins.support.startup.StartupReport.INITIAL_DELAY_SECONDS=0")
            .javaOptions("-Dcom.cloudbees.jenkins.support.startup.StartupReport.RECURRENCE_PERIOD_SECONDS=1");

    @Test
    public void startupReport() throws Throwable {
        rr.then(StartupReportTest::assertStartupReportExists);
        System.out.println("Finished");
    }

    private static void assertStartupReportExists(JenkinsRule r) throws IOException {
        var startupReport = StartupReport.get();
        // We've got some thread dumps already
        assertThat(startupReport.getLogs().getSize(), greaterThan(0));
        // Our SaveableListener works
        assertThat(startupReport.getTimesPerMilestone().keySet(), hasItem(equalTo(InitMilestone.COMPLETED)));
        // Other milestones got recorded. Don't want to hardcode them in case it changes.
        assertThat(startupReport.getTimesPerMilestone().keySet(), hasSize(greaterThan(1)));
        var l = startupReport.getTimesPerMilestone().get(InitMilestone.COMPLETED);
        assertThat(l.toEpochMilli(), greaterThan(0L));
        Jenkins.get().save();
        assertThat(startupReport.getTimesPerMilestone().get(InitMilestone.COMPLETED), equalTo(l));
    }
}
