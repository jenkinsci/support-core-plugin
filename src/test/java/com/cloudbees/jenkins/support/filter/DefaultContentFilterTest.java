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
import hudson.model.ListView;
import hudson.model.User;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;

import static org.assertj.core.api.Assertions.*;

public class DefaultContentFilterTest {

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Issue("JENKINS-21670")
    @Test
    public void anonymizeSlavesAndLabels() throws Exception {
        DefaultContentFilter filter = DefaultContentFilter.get();
        jenkins.createSlave("foo", "bar", null);
        jenkins.createSlave("jar", "war", null);

        filter.setEnabled(false);

        String foo = filter.filter("foo");
        assertThat(foo).isEqualTo("foo");
        String bar = filter.filter("bar");
        assertThat(bar).isEqualTo("bar");
        String jar = filter.filter("jar");
        assertThat(jar).isEqualTo("jar");
        String war = filter.filter("war");
        assertThat(war).isEqualTo("war");

        filter.setEnabled(true);

        foo = filter.filter("foo");
        assertThat(foo).startsWith("computer_").doesNotContain("foo");
        bar = filter.filter("bar");
        assertThat(bar).startsWith("label_").doesNotContain("bar");
        jar = filter.filter("jar");
        assertThat(jar).startsWith("computer_").doesNotContain("jar");
        war = filter.filter("war");
        assertThat(war).startsWith("label_").doesNotContain("war");
    }

    @Issue("JENKINS-21670")
    @Test
    public void anonymizeItems() throws IOException {
        DefaultContentFilter filter = DefaultContentFilter.get();
        FreeStyleProject project = jenkins.createFreeStyleProject();
        String name = project.getName();

        filter.setEnabled(false);

        String actual = filter.filter(name);
        assertThat(actual).isEqualTo(name);

        filter.setEnabled(true);

        actual = filter.filter(name);
        assertThat(actual).startsWith("item_").doesNotContain(name);
    }

    @Issue("JENKINS-21670")
    @Test
    public void anonymizeViews() throws IOException {
        DefaultContentFilter filter = DefaultContentFilter.get();
        jenkins.getInstance().addView(new ListView("foobar"));

        filter.setEnabled(false);

        String foobar = filter.filter("foobar");
        assertThat(foobar).isEqualTo("foobar");

        filter.setEnabled(true);

        foobar = filter.filter("foobar");
        assertThat(foobar).startsWith("view_").doesNotContain("foobar");
    }

    @Issue("JENKINS-21670")
    @Test
    public void anonymizeUsers() {
        DefaultContentFilter filter = DefaultContentFilter.get();
        User.getOrCreateByIdOrFullName("gibson");

        filter.setEnabled(false);

        String gibson = filter.filter("gibson");
        assertThat(gibson).isEqualTo("gibson");

        filter.setEnabled(true);

        gibson = filter.filter("gibson");
        assertThat(gibson).startsWith("user_").doesNotContain("gibson");
    }
}
