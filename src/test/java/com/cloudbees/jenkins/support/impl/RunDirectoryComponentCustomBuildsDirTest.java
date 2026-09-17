/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.jenkins.support.SupportTestUtils;
import java.util.Optional;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

final class RunDirectoryComponentCustomBuildsDirTest {

    private static final String JOB_NAME = "job-name";

    @RegisterExtension
    private final RealJenkinsExtension rje = new RealJenkinsExtension();

    @Test
    void itemFullName() throws Throwable {
        rje.javaOptions("-Djenkins.model.Jenkins.buildsDir=${JENKINS_HOME}/buildsRoot/${ITEM_FULL_NAME}/builds");
        rje.then(j -> {
            var folder = j.createProject(Folder.class, "topFolder");
            var subFolder = folder.createProject(Folder.class, "subFolder");
            var p = subFolder.createProject(WorkflowJob.class, JOB_NAME);
            p.setDefinition(new CpsFlowDefinition(
                    "node {writeFile file: 'test.txt', text: ''; archiveArtifacts '*.txt'}", true));
            var workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                    .orElseThrow(AssertionError::new)
                    .waitForStart();
            j.waitForCompletion(workflowRun);
            j.waitUntilNoActivity();

            var output = SupportTestUtils.invokeComponentToMap(new RunDirectoryComponent(), workflowRun);

            var prefix = "items/" + p.getFullName() + "/builds/" + workflowRun.number;
            assertTrue(output.containsKey(prefix + "/build.xml"));
            assertTrue(output.containsKey(prefix + "/log"));
            assertThat(output.keySet(), hasItem(startsWith(prefix + "/workflow")));
            assertThat(output.get(prefix + "/build.xml"), Matchers.containsString("<flow-build"));
            assertThat(output.get(prefix + "/log"), Matchers.containsString("[Pipeline] node"));
            assertThat(output.keySet(), not(hasItem(Matchers.containsString("test.txt"))));
        });
    }

    @Test
    void itemRootdir() throws Throwable {
        rje.javaOptions("-Djenkins.model.Jenkins.buildsDir=${ITEM_ROOTDIR}/runs");
        rje.then(j -> {
            var folder = j.createProject(Folder.class, "topFolder2");
            var subFolder = folder.createProject(Folder.class, "subFolder2");
            var p = subFolder.createProject(WorkflowJob.class, "testWorkflow2");
            p.setDefinition(new CpsFlowDefinition(
                    "node {writeFile file: 'test.txt', text: ''; archiveArtifacts '*.txt'}", true));
            var workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                    .orElseThrow(AssertionError::new)
                    .waitForStart();
            j.waitForCompletion(workflowRun);
            j.waitUntilNoActivity();

            var output = SupportTestUtils.invokeComponentToMap(new RunDirectoryComponent(), workflowRun);

            var prefix = "items/" + p.getFullName() + "/builds/" + workflowRun.number;
            assertTrue(output.containsKey(prefix + "/build.xml"));
            assertTrue(output.containsKey(prefix + "/log"));
            assertThat(output.keySet(), hasItem(startsWith(prefix + "/workflow")));
            assertThat(output.get(prefix + "/build.xml"), Matchers.containsString("<flow-build"));
            assertThat(output.get(prefix + "/log"), Matchers.containsString("[Pipeline] node"));
            assertThat(output.keySet(), not(hasItem(Matchers.containsString("test.txt"))));
        });
    }
}
