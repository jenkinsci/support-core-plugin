/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;

import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.ExtensionList;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsSessionRule;

public final class SlaveLaunchLogsRestartTest {

    @Rule
    public JenkinsSessionRule rr = new JenkinsSessionRule();

    @Test
    public void twoAgents() throws Throwable {
        rr.then(r -> {
            var s = r.createSlave("agent1", null, null);
            r.waitOnline(s);
            assertThat(
                    "reflects launch of agent1",
                    SupportTestUtils.invokeComponentToString(ExtensionList.lookupSingleton(SlaveLaunchLogs.class)),
                    containsString("Z agent1] Remoting version: "));
            r.jenkins.removeNode(s);
        });
        rr.then(r -> {
            var s = r.createSlave("agent2", null, null);
            r.waitOnline(s);
            assertThat(
                    "reflects launch of both agent1 & agent2",
                    SupportTestUtils.invokeComponentToString(ExtensionList.lookupSingleton(SlaveLaunchLogs.class)),
                    allOf(
                            containsString("Z agent1] Remoting version: "),
                            containsString("Z agent2] Remoting version: ")));
        });
    }
}
