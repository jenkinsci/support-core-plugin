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

    @Test
    void longSMatches(JenkinsRule j) throws IOException {
        // Long s (U+017F) normalizes to 's' via Character.toLowerCase(Character.toUpperCase(cp)),
        // matching what Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE does. But "ſap".toLowerCase(ENGLISH)
        // is unchanged (ſ is already lowercase), so the old code crashed with NPE on a null replacement.
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        ContentMappings mappings = ContentMappings.get();
        mappings.getMappingOrCreate("sap", original -> ContentMapping.of(original, "replacement_sap"));
        filter.reload();

        String result = filter.filter("the ſap server");
        assertThat(result).isEqualTo("the replacement_sap server");
    }

    @Test
    void turkishCapitalIDotAbovePreservesLength(JenkinsRule j) throws IOException {
        // Turkish İ (U+0130) grows from 7 to 8 chars when lowercased with toLowerCase(ENGLISH), corrupting
        // trie literals and offsets. normalizeCase preserves length, so the name is redacted correctly.
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        ContentMappings mappings = ContentMappings.get();
        mappings.getMappingOrCreate("İdalium", original -> ContentMapping.of(original, "replacement_idalium"));
        filter.reload();

        String result = filter.filter("İdalium");
        assertThat(result).isEqualTo("replacement_idalium");
    }

    @Test
    void caseVariantMatching(JenkinsRule j) throws IOException {
        // Case-insensitive matching should still work: JDoe, jdoe, JDOE should all match key "jdoe".
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        FreeStyleProject project = j.createFreeStyleProject("JDoe");
        filter.reload();

        String replacement = filter.filter("JDoe");
        assertThat(replacement).startsWith("item_").doesNotContain("JDoe");

        assertThat(filter.filter("jdoe")).isEqualTo(replacement);
        assertThat(filter.filter("JDOE")).isEqualTo(replacement);
    }

    @Test
    void identityFastPathReturnsSameObject() {
        // When normalizeCase encounters an already-normalized string, it must return the input object itself
        // (not a copy) to avoid duplicating the char array for every key.
        String alreadyLowercase = "alreadylowercase";
        String normalized = SensitiveContentFilter.normalizeCase(alreadyLowercase);
        assertThat(normalized).isSameAs(alreadyLowercase);

        String mixed = "MixedCase";
        String normalizedMixed = SensitiveContentFilter.normalizeCase(mixed);
        assertThat(normalizedMixed).isNotSameAs(mixed);
    }

    @Test
    void wordBoundarySemantics(JenkinsRule j) throws IOException {
        // Java's \w is ASCII-only by default. normalizeCase on "ſſap" -> "ssap" where the lookbehind now sees
        // a word character 's' and rejects a match that the old regex (with UNICODE_CASE) would have accepted
        // if ſ were considered non-\w. Pin the intended behavior.
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        ContentMappings mappings = ContentMappings.get();
        mappings.getMappingOrCreate("sap", original -> ContentMapping.of(original, "replacement_sap"));
        filter.reload();

        // "ſſap" normalizes to "ssap", and "ssap" should NOT match due to the lookbehind (?<!\w) seeing 's'.
        String result = filter.filter("ſſap");
        assertThat(result).isEqualTo("ſſap"); // no replacement

        // " sap" should match (space is not \w).
        String result2 = filter.filter(" sap");
        assertThat(result2).isEqualTo(" replacement_sap");
    }
}
