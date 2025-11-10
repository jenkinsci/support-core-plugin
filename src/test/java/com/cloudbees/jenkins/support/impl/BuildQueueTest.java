/*
 * The MIT License
 *
 * Copyright (c) 2015 schristou88
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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueTaskFuture;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author schristou88
 */
@WithJenkins
class BuildQueueTest {

    @Disabled("Unit test fails when performing a release. The queue has a race condition"
            + "which is resolved in 1.607+ (TODO).")
    @Test
    void verifyMinimumBuildQueue(JenkinsRule j) throws Exception {
        // Given
        QueueTaskFuture<FreeStyleBuild> build;
        String assignedLabel = "foo";
        FreeStyleProject p = j.createFreeStyleProject("bar");
        p.setAssignedLabel(new LabelAtom(assignedLabel));
        BuildQueue queue = new BuildQueue();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // When
        build = p.scheduleBuild2(0);
        queue.addContents(createContainer(baos));

        // Then
        build.cancel(true);
        List<String> output = new ArrayList<>(Arrays.asList(baos.toString().split("\n")));

        assertContains(output, "1 item");
        assertContains(output, p.getDisplayName());
        assertContains(output, "Waiting for next available executor");
    }

    private static void assertContains(List<String> list, String search) {
        assertThat(list, hasItems(containsString(search)));
    }

    private static Container createContainer(final OutputStream os) {
        return new Container() {
            @Override
            public void add(@CheckForNull Content content) {
                try {
                    content.writeTo(os);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        };
    }
}
