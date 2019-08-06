/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.ExtensionList;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleProject;
import hudson.remoting.VirtualChannel;
import hudson.slaves.DumbSlave;
import hudson.slaves.SlaveComputer;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import jenkins.MasterToSlaveFileCallable;
import static org.hamcrest.Matchers.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestBuilder;

public class SlaveCommandStatisticsTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().record(SlaveComputer.class, Level.FINEST);

    @Test
    public void smokes() throws Exception {
        DumbSlave s = r.createSlave();
        FreeStyleProject p = r.createFreeStyleProject();
        p.setAssignedNode(s);
        p.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
                build.getWorkspace().act(new SampleCallable());
                return true;
            }
        });
        r.buildAndAssertSuccess(p);
        String dump = SupportTestUtils.invokeComponentToString(ExtensionList.lookupSingleton(SlaveCommandStatistics.class));
        System.out.println(dump);
        assertThat(dump, containsString(SampleCallable.class.getName()));
    }

    private static class SampleCallable extends MasterToSlaveFileCallable<Void> {
        @Override
        public Void invoke(File f, VirtualChannel channel) throws IOException, InterruptedException {
            return null;
        }
    }

    @Test @Issue("JENKINS-58528")
    public void statisticsAreRotatedWithComputers() throws Exception {
        SlaveCommandStatistics scs = ExtensionList.lookupSingleton(SlaveCommandStatistics.class);

        SlaveCommandStatistics.MAX_STATS_SIZE = 0;
        assertThat(scs.getStatistics().size(), equalTo(0));
        DumbSlave s0 = r.createOnlineSlave();
        assertThat(scs.getStatistics().size(), equalTo(1));
        r.jenkins.removeNode(s0);
        assertThat(scs.getStatistics().size(), equalTo(0));

        SlaveCommandStatistics.MAX_STATS_SIZE = 1;
        DumbSlave s1 = r.createOnlineSlave();
        DumbSlave s2 = r.createOnlineSlave();
        DumbSlave s3 = r.createOnlineSlave();
        assertThat(scs.getStatistics().size(), equalTo(3));
        r.jenkins.removeNode(s1);
        assertThat(scs.getStatistics().size(), equalTo(3));
        r.jenkins.removeNode(s2);
        assertThat(scs.getStatistics().size(), equalTo(2));
        r.jenkins.removeNode(s3);
        assertThat(scs.getStatistics().size(), equalTo(1));
        assertThat("Latest preserved", scs.getStatistics().keySet(), contains(s3.getNodeName()));

        SlaveCommandStatistics.MAX_STATS_SIZE = 3;
        s0 = r.createOnlineSlave();
        s1 = r.createOnlineSlave();
        s2 = r.createOnlineSlave();
        s3 = r.createOnlineSlave();
        assertThat(scs.getStatistics().size(), equalTo(4));
        r.jenkins.removeNode(s0);
        assertThat(scs.getStatistics().size(), equalTo(4));
        r.jenkins.removeNode(s1);
        assertThat(scs.getStatistics().size(), equalTo(4));
        r.jenkins.removeNode(s2);
        assertThat(scs.getStatistics().size(), equalTo(4));
        r.jenkins.removeNode(s3);
        assertThat(scs.getStatistics().size(), equalTo(3));
        assertThat("Latest preserved", scs.getStatistics().keySet(), containsInAnyOrder(
                s3.getNodeName(), s2.getNodeName(), s1.getNodeName()
        ));
    }
}
