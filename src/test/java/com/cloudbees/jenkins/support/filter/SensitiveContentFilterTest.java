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

import static org.assertj.core.api.Assertions.assertThat;

import hudson.model.FreeStyleProject;
import hudson.model.ListView;
import hudson.model.User;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SensitiveContentFilterTest {

    @Issue("JENKINS-21670")
    @Test
    void anonymizeAgentsAndLabels(JenkinsRule j) throws Exception {
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        // using foo, bar, jar and war could raise flaky test failures. It happened to me when
        // bar was changed by label_barrier :-O So we use stranger words to avoid this test to be flaky
        j.createSlave("foostrange", "barstrange", null);
        j.createSlave("jarstrange", "warstrange", null);
        filter.reload();

        String foo = filter.filter("foostrange");
        assertThat(foo).startsWith("computer_").doesNotContain("foostrange");

        String bar = filter.filter("barstrange");
        assertThat(bar).startsWith("label_").doesNotContain("barstrange");

        String jar = filter.filter("jarstrange");
        assertThat(jar).startsWith("computer_").doesNotContain("jarstrange");

        String war = filter.filter("warstrange");
        assertThat(war).startsWith("label_").doesNotContain("warstrange");
    }

    @Issue("JENKINS-21670")
    @Test
    void anonymizeItems(JenkinsRule j) throws IOException {
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        FreeStyleProject project = j.createFreeStyleProject();
        filter.reload();
        String name = project.getName();

        String actual = filter.filter(name);

        assertThat(actual).startsWith("item_").doesNotContain(name);
    }

    @Issue("JENKINS-21670")
    @Test
    void anonymizeViews(JenkinsRule j) throws IOException {
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        j.getInstance().addView(new ListView("foobar"));
        filter.reload();

        String foobar = filter.filter("foobar");

        assertThat(foobar).startsWith("view_").doesNotContain("foobar");
    }

    @Issue("JENKINS-21670")
    @Test
    void anonymizeUsers(JenkinsRule j) {
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        User.getOrCreateByIdOrFullName("gibson");
        filter.reload();

        String gibson = filter.filter("gibson");

        assertThat(gibson).startsWith("user_").doesNotContain("gibson");
    }

    @Issue("JENKINS-54688")
    @Test
    void shouldNotFilterOperatingSystem(JenkinsRule j) throws Exception {
        String os = "Linux";
        String label = "fake";
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        j.createSlave("foo", String.format("%s %s", os, label), null);
        filter.reload();
        assertThat(filter.filter(os)).isEqualTo(os);
        assertThat(filter.filter(label)).startsWith("label_").isNotEqualTo(label);
    }

    @Test
    void strayReferenceSurvivesGracePeriodAfterItemRemoval(JenkinsRule j) throws IOException {
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        FreeStyleProject project = j.createFreeStyleProject("strayproject");
        filter.reload();
        String name = project.getName();

        String replacement = filter.filter(name);
        assertThat(replacement).startsWith("item_").doesNotContain(name);

        j.jenkins.remove(project);
        filter.reload();

        // The item is gone, but the pre-fill loop in reload() still loads every persisted mapping, so a
        // stray reference to "strayproject" elsewhere in current content (build logs, SCM URLs, etc.) must
        // keep getting anonymized within the grace period.
        assertThat(filter.filter(name)).isEqualTo(replacement);
    }

    @Test
    void matchRefreshesLastSeenPreventingEviction(JenkinsRule j) throws IOException {
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        FreeStyleProject kept = j.createFreeStyleProject("keptproject");
        FreeStyleProject dropped = j.createFreeStyleProject("droppedproject");
        filter.reload();

        String keptName = kept.getName();
        String droppedName = dropped.getName();

        j.jenkins.remove(kept);
        j.jenkins.remove(dropped);
        filter.reload();

        ContentMappings mappings = ContentMappings.get();
        Instant staleTime = Instant.now().minus(Duration.ofDays(91));
        backdate(mappings, keptName, staleTime);
        backdate(mappings, droppedName, staleTime);

        // A real match on "keptproject" (e.g. because its name still appears in old build logs) should
        // refresh lastSeen back to "now" before the mapping is ever swept.
        filter.filter(keptName);

        mappings.evictStale();

        assertThat(mappings.getMappings()).containsKey(keptName).doesNotContainKey(droppedName);
    }

    private static void backdate(ContentMappings mappings, String original, Instant lastSeen) {
        StreamSupport.stream(mappings.spliterator(), false)
                .filter(mapping -> mapping.getOriginal().equals(original))
                .findFirst()
                .orElseThrow()
                .touch(lastSeen);
    }
}
