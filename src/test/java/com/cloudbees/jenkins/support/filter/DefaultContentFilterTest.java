/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package com.cloudbees.jenkins.support.filter;

import hudson.model.FreeStyleProject;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class DefaultContentFilterTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void anonymizeNodesAndLabels() throws Exception {
        DefaultContentFilter filter = DefaultContentFilter.get();
        jenkins.createSlave("foo", "bar", null);
        jenkins.createSlave("jar", "war", null);

        filter.setEnabled(false);

        String foo = filter.filter("foo");
        assertEquals("foo", foo);
        String bar = filter.filter("bar");
        assertEquals("bar", bar);
        String jar = filter.filter("jar");
        assertEquals("jar", jar);
        String war = filter.filter("war");
        assertEquals("war", war);

        filter.setEnabled(true);

        foo = filter.filter("foo");
        assertNotEquals("foo", foo);
        bar = filter.filter("bar");
        assertNotEquals("bar", bar);
        jar = filter.filter("jar");
        assertNotEquals("jar", jar);
        war = filter.filter("war");
        assertNotEquals("war", war);
    }

    @Test
    public void anonymizeItems() throws IOException {
        DefaultContentFilter filter = DefaultContentFilter.get();
        FreeStyleProject project = jenkins.createFreeStyleProject();
        String name = project.getName();

        filter.setEnabled(false);

        String actual = filter.filter(name);
        assertEquals(name, actual);

        filter.setEnabled(true);

        actual = filter.filter(name);
        assertNotEquals(name, actual);
    }
}