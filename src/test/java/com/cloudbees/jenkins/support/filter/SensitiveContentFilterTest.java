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

    @Test
    void strayReferenceSurvivesEvictionByAConcurrentBundle(JenkinsRule j) throws IOException {
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        FreeStyleProject project = j.createFreeStyleProject("concurrentproject");
        filter.reload();
        String name = project.getName();
        String replacement = filter.filter(name);

        j.jenkins.remove(project);
        // This bundle's snapshot still holds the mapping: reload() pre-fills from the persisted table.
        filter.reload();

        ContentMappings mappings = ContentMappings.get();
        backdate(mappings, name, Instant.now().minus(Duration.ofDays(91)));

        // Stand in for a second bundle generated at the same time, sweeping the mapping out from under us.
        mappings.evictStale();
        assertThat(mappings.getMappings()).doesNotContainKey(name);

        // Matching a stray reference has to put the mapping back. Nothing else will: the pre-fill loop reads
        // only the table, and no NameProvider reports a deleted item, so the name would leak from here on.
        assertThat(filter.filter(name)).isEqualTo(replacement);
        assertThat(mappings.getMappings()).containsEntry(name, replacement);
    }

    private static void backdate(ContentMappings mappings, String original, Instant lastSeen) {
        StreamSupport.stream(mappings.spliterator(), false)
                .filter(mapping -> mapping.getOriginal().equals(original))
                .findFirst()
                .orElseThrow()
                .touch(lastSeen);
    }

    @Test
    void longSIsNotFoldedOntoPlainS(JenkinsRule j) throws IOException {
        // normalizeCase lowercases per code point and deliberately does not case-fold, so the long s
        // (U+017F, already lowercase) stays itself rather than becoming 's'. "ſap" is therefore a
        // different key from "sap" and is left alone. Folding them together would be worse than a missed
        // match: distinct originals sharing a key means one takes the other's pseudonym.
        // This also pins the absence of the old NPE, which came from the pattern folding ſ onto s while
        // the key map did not, leaving the match with no replacement to resolve to.
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        ContentMappings mappings = ContentMappings.get();
        mappings.getMappingOrCreate("sap", original -> ContentMapping.of(original, "replacement_sap"));
        filter.reload();

        assertThat(filter.filter("the ſap server")).isEqualTo("the ſap server");
        assertThat(filter.filter("the sap server")).isEqualTo("the replacement_sap server");
    }

    @Test
    void stopWordsGateCaseVariants(JenkinsRule j) throws IOException {
        // The gate compares the normalized key, not the raw original, so a case variant of a stop word is
        // still gated. Comparing raw originals would let "JENKINS" through while "jenkins" was suppressed.
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        ContentMappings mappings = ContentMappings.get();
        mappings.getMappingOrCreate("JENKINS", original -> ContentMapping.of(original, "replacement_jenkins"));
        filter.reload();

        assertThat(filter.filter("the JENKINS server")).isEqualTo("the JENKINS server");
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
    void normalizeCasePreservesUtf16WidthForEveryCodePoint() {
        // Offset splicing (replaceWordsByOffset) assumes folding a code point never changes how many
        // UTF-16 code units it needs; otherwise offsets between the original and normalized strings
        // desync and splicing corrupts or misaligns content. The targeted tests above (Turkish İ, long
        // s) pin known cases; this exhaustively checks every code point through normalizeCase itself
        // (not a reimplementation of its fold) so a future JDK Unicode table change, or a change to
        // normalizeCase's own logic, can't silently break the invariant unnoticed.
        for (int cp = Character.MIN_CODE_POINT; cp <= Character.MAX_CODE_POINT; cp++) {
            String single = new String(Character.toChars(cp));
            assertThat(SensitiveContentFilter.normalizeCase(single).length())
                    .as("U+%04X normalizes to a different UTF-16 width", cp)
                    .isEqualTo(Character.charCount(cp));
        }
    }

    @Test
    void wordBoundarySemantics(JenkinsRule j) throws IOException {
        // The pattern is wrapped in (?<!\w) ... (?!\w), so a name only matches as a whole word. Java's \w is
        // ASCII-only by default, which is what decides whether a neighbouring character blocks a match.
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        ContentMappings mappings = ContentMappings.get();
        mappings.getMappingOrCreate("sap", original -> ContentMapping.of(original, "replacement_sap"));
        filter.reload();

        // "ssap" must NOT match: the lookbehind sees the preceding 's', an ASCII word character.
        assertThat(filter.filter("ssap")).isEqualTo("ssap");
        // Trailing word characters block it too.
        assertThat(filter.filter("sapling")).isEqualTo("sapling");

        // " sap" should match (space is not \w).
        String result2 = filter.filter(" sap");
        assertThat(result2).isEqualTo(" replacement_sap");
    }

    @Test
    void collidingOriginalsResolveToTheSameMappingAcrossReloads(JenkinsRule j) throws IOException {
        // "sap" and "SAP" are distinct originals that normalize to the same key. The pre-fill loop and the
        // provider loop must agree on one winner and keep agreeing on every later reload -- otherwise the
        // same input resolves to a different pseudonym, and a different mapping gets touched/kept alive,
        // depending on when the colliding mapping happened to get persisted relative to the reload.
        SensitiveContentFilter filter = SensitiveContentFilter.get();
        ContentMappings mappings = ContentMappings.get();

        // "SAP" is the expected winner because the pre-fill loop iterates ContentMappings in its own
        // comparator order (length descending, then natural order) and takes the first write, and "SAP"
        // sorts before "sap". Picking the collider that sorts first is what makes the winner stable here;
        // the policy keys off sort order rather than which original was persisted first.
        mappings.getMappingOrCreate("SAP", original -> ContentMapping.of(original, "replacement_sap"));
        filter.reload();
        String replacement = filter.filter("sap");
        assertThat(replacement).isEqualTo("replacement_sap");

        // The second collider arrives through a NameProvider (an item named "sap"), persisted during this
        // reload's provider loop -- too late to affect this reload's winner, but present for the next one.
        j.createFreeStyleProject("sap");
        filter.reload();
        assertThat(filter.filter("sap")).isEqualTo(replacement);

        // Third reload: the pre-fill loop now sees both persisted mappings. The winner must still be "SAP".
        filter.reload();
        assertThat(filter.filter("sap")).isEqualTo(replacement);

        // The match must still be attributed to the "SAP" mapping, not the item's: back-date both, match
        // once, then evict -- only the mapping actually matched should survive.
        Instant staleTime = Instant.now().minus(Duration.ofDays(91));
        backdate(mappings, "SAP", staleTime);
        backdate(mappings, "sap", staleTime);

        filter.filter("sap");
        mappings.evictStale();

        assertThat(mappings.getMappings()).containsKey("SAP").doesNotContainKey("sap");
    }
}
