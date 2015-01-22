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

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueTaskFuture;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertThat;

/**
 * @author schristou88
 */
public class BuildQueueTest {
  @Rule public JenkinsRule j = new JenkinsRule();

  @Test
  public void verifyMinimumBuildQueue() throws Exception {
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
    List<String> output = new ArrayList<String>(Arrays.asList(baos.toString().split("\n")));


    assertContains(output, "1 item");
    assertContains(output, p.getDisplayName());
    assertContains(output, "Waiting for next available executor");
  }

  public void assertContains(List<String> list, String search) {
    assertThat(list, hasItems(containsString(search)));
  }

  public Container createContainer(final OutputStream os) {
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