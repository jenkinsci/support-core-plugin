/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
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

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

import hudson.BulkChange;
import hudson.model.FreeStyleProject;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.StreamSupport;
import jenkins.model.Jenkins;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class ContentMappingsTest {

    private static String originalVersion;

    @BeforeAll
    static void storeVersion() {
        originalVersion = Jenkins.VERSION;
    }

    @AfterEach
    void resetVersion() {
        Jenkins.VERSION = originalVersion;
        ContentMappings.get().clear();
    }

    @Test
    void dynamicStopWordsAreAddedWhenReloading(JenkinsRule r) throws Exception {
        FreeStyleProject job = r.createFreeStyleProject();
        String[] expectedStopWords = {
            job.getPronoun().toLowerCase(Locale.ENGLISH), job.getTaskNoun().toLowerCase(Locale.ENGLISH)
        };
        ContentMappings mappings = ContentMappings.get();
        assertThat(mappings.getStopWords(), not(hasItems(expectedStopWords)));
        mappings.reload();
        assertThat(mappings.getStopWords(), hasItems(expectedStopWords));
    }

    @Test
    void contentMappingsOrderedByLengthDescending(JenkinsRule r) throws IOException {
        r.createFreeStyleProject("ShortName");
        r.createFreeStyleProject("LongerName");
        r.createFreeStyleProject("LongestNameHere");
        ContentFilter.ALL.reload();
        ContentMappings mappings = ContentMappings.get();
        List<String> originals = StreamSupport.stream(mappings.spliterator(), false)
                .map(ContentMapping::getOriginal)
                .toList();
        assertThat(originals, containsInRelativeOrder("LongestNameHere", "LongerName", "ShortName"));
    }

    @Test
    void contentMappingsSurviveSerializationRoundTrip(JenkinsRule r) throws Throwable {
        ContentMapping mapping = ContentMapping.of("test_original", "test_replacement");
        ContentMappings.get().getMappingOrCreate(mapping.getOriginal(), original -> mapping);
        assertThat(ContentMappings.get().getMappings(), hasEntry(mapping.getOriginal(), mapping.getReplacement()));

        r.restart();

        assertThat(ContentMappings.get().getMappings(), hasEntry(mapping.getOriginal(), mapping.getReplacement()));
    }

    @Test
    void removedItemMappingSurvivesGracePeriodThenEvicted(JenkinsRule r) throws Throwable {
        // To be final and reuse in the steps
        StringBuilder jobReplacement = new StringBuilder();

        r.createFreeStyleProject("ShortName");
        ContentFilter.reloadAndSaveMappings(ContentFilter.ALL);

        // Store their replacements
        ContentMappings mappings = ContentMappings.get();
        jobReplacement.append(mappings.getMappings().get("ShortName"));

        // Check if the mapping exists
        assertThat(mappings.getMappings(), hasEntry("ShortName", jobReplacement.toString()));

        r.restart();

        // Mapping persisted after restart and remove the project
        mappings = ContentMappings.get();

        // Check if the mapping exists after restart
        assertThat(mappings.getMappings(), hasEntry("ShortName", jobReplacement.toString()));

        // Remove the project
        r.jenkins.remove(r.jenkins.getItem("ShortName"));

        // A full reload-and-evict cycle must NOT drop the mapping immediately: it's still needed to
        // redact any stray reference to "ShortName" that may already exist in current content (e.g. build
        // logs, SCM URLs). Default retention is 90 days, so it survives.
        ContentFilter.reloadAndSaveMappings(ContentFilter.ALL);

        mappings = ContentMappings.get();
        assertThat(
                "The mapping of a removed project should survive its grace period",
                mappings.getMappings(),
                hasEntry("ShortName", jobReplacement.toString()));

        System.setProperty(ContentMappings.class.getName() + ".RETENTION", "PT15S");
        try {
            await().atMost(Duration.ofSeconds(20))
                    .pollInterval(Duration.ofSeconds(1))
                    .until(() -> {
                        ContentMappings cm = ContentMappings.get();
                        try (BulkChange change = new BulkChange(cm)) {
                            ContentFilter.reloadAndSaveMappings(ContentFilter.ALL);
                            cm.evictStale();
                            change.commit();
                        }
                        return !ContentMappings.get().getMappings().containsKey("ShortName");
                    });
        } finally {
            System.clearProperty(ContentMappings.class.getName() + ".RETENTION");
        }

        assertThat(
                "The mapping of a removed project should eventually be evicted once its grace period elapses",
                ContentMappings.get().getMappings(),
                not(hasEntry("ShortName", jobReplacement.toString())));
    }

    @Test
    void evictStaleRemovesMappingsPastRetentionWindow(JenkinsRule r) {
        ContentMappings mappings = ContentMappings.get();
        Instant now = Instant.now();

        ContentMapping stale = mappings.getMappingOrCreate("stale-entry", ContentMappingsTest::identityMapping);
        stale.touch(now.minus(Duration.ofDays(91)));

        ContentMapping fresh = mappings.getMappingOrCreate("fresh-entry", ContentMappingsTest::identityMapping);
        fresh.touch(now.minus(Duration.ofDays(1)));

        try (LogRecorder logger =
                new LogRecorder().record(ContentMappings.class, Level.FINE).capture(1)) {
            mappings.evictStale();

            assertTrue(
                    logger.getMessages().stream().anyMatch(msg -> msg.contains("Evicted 1 stale content mapping")),
                    "eviction should be logged at FINE level with count");
        }

        Map<String, String> remaining = mappings.getMappings();
        assertFalse(remaining.containsKey("stale-entry"), "mapping unused for over 90 days should be evicted");
        assertTrue(remaining.containsKey("fresh-entry"), "mapping used within the retention window should survive");
    }

    @Test
    @LocalData
    void migratedMappingDefaultsLastSeenToNowNotEpoch(JenkinsRule r) {
        ContentMappings mappings = ContentMappings.get();

        ContentMapping migrated = StreamSupport.stream(mappings.spliterator(), false)
                .filter(mapping -> mapping.getOriginal().equals("legacy-original"))
                .findFirst()
                .orElseThrow();

        Instant now = Instant.now();
        assertTrue(
                migrated.getLastSeen().isBefore(now) && migrated.getLastSeen().isAfter(now.minusSeconds(60)),
                "migrated mapping should default lastSeen to \"now\" (within last 60s), not the epoch (0)");
    }

    @Issue("JENKINS-53184")
    @Test
    @LocalData
    void jenkinsVersionIncludedAsStopWord(JenkinsRule r) {
        Jenkins.VERSION = "1.2.3.4";
        // With JCasC the ContentMappings is created before Jenkins.VERSION is called, so the previous instruction
        // doesn't take effect in the mappings. We have to force the mappings to reload after the version is set.
        ContentMappings.get().clear();
        ContentMappings mappings = ContentMappings.get();

        // Jenkins version added to stop words
        assertTrue(mappings.getStopWords().contains(Jenkins.VERSION));

        // Previous mappings with Jenkins version are ignored
        assertTrue(mappings.getMappings().isEmpty());
    }

    @Issue("JENKINS-54688")
    @Test
    @LocalData
    void operatingSystemIncludedAsStopWord(JenkinsRule r) {
        String os = "Linux";
        ContentMappings mappings = ContentMappings.get();

        // The Operating system is added to stop words
        assertTrue(mappings.getStopWords().contains(os.toLowerCase(Locale.ENGLISH)));

        // Previous mappings with the operating system are ignored
        assertTrue(mappings.getMappings().isEmpty());
    }

    @Issue("JENKINS-66023")
    @Test
    @LocalData
    void additionalStopWordsIncludedAsStopWord(JenkinsRule r) {
        String[] expectedStopWords = {
            "abc", "https://core.example.com", "john doe", "192.168.0.1", "<h1>", "  leadingspaces", "trailingspaces  "
        };
        ContentMappings mappings = ContentMappings.get();
        MatcherAssert.assertThat(mappings.getStopWords(), hasItems(expectedStopWords));
    }

    private static ContentMapping identityMapping(String original) {
        return ContentMapping.of(original, original);
    }

    @Test
    void clear(JenkinsRule r) {
        String ALT_VERSION = "alt-version";
        ContentMappings contentMappings = ContentMappings.get();
        int initialMappingsSize = contentMappings.getMappings().size();

        contentMappings.getMappingOrCreate("something", ContentMappingsTest::identityMapping);

        assertTrue(contentMappings.getMappings().size() > initialMappingsSize);

        Jenkins.VERSION = ALT_VERSION;
        contentMappings.clear();

        Set<String> stopWords = contentMappings.getStopWords();
        assertTrue(stopWords.contains(ALT_VERSION));
        assertFalse(stopWords.contains(originalVersion));
        assertEquals(contentMappings.getMappings().size(), initialMappingsSize);

        Jenkins.VERSION = originalVersion;
        contentMappings.clear();

        stopWords = contentMappings.getStopWords();
        assertFalse(stopWords.contains(ALT_VERSION));
        assertTrue(stopWords.contains(originalVersion));
    }

    @Test
    void staleIpMappingKeepsPseudonymWhenFilteredDuringBundle(JenkinsRule r) throws Exception {
        String testIp = "192.168.1.100";
        ContentMappings mappings = ContentMappings.get();

        String filtered = InetAddressContentFilter.get().filter(testIp);
        String originalPseudonym = mappings.getMappings().get(testIp);
        assertTrue(originalPseudonym.startsWith("ip_"), "IP should be filtered to ip_ pseudonym");

        StreamSupport.stream(mappings.spliterator(), false)
                .filter(mapping -> mapping.getOriginal().equals(testIp))
                .findFirst()
                .orElseThrow()
                .touch(Instant.now().minus(Duration.ofDays(91)));

        try (BulkChange change = new BulkChange(mappings)) {
            ContentFilter filter = ContentFilter.ALL;
            ContentFilter.reloadAndSaveMappings(filter);

            String testContent = "Server at " + testIp;
            filter.filter(testContent);

            mappings.evictStale();
            change.commit();
        }

        mappings = ContentMappings.get();
        assertTrue(
                mappings.getMappings().containsKey(testIp),
                "IP mapping backdated beyond retention window should survive if encountered during filtering");
        assertEquals(
                originalPseudonym,
                mappings.getMappings().get(testIp),
                "IP mapping should keep its original pseudonym, not be evicted and recreated");
    }

    @Test
    void staleMatchedMappingSurvivesAndRemainsFilteredOnNextCycle(JenkinsRule r) throws Exception {
        String testName = "stale-but-matched";
        ContentMappings mappings = ContentMappings.get();

        ContentMapping mapping = mappings.getMappingOrCreate(testName, ContentMappingsTest::identityMapping);
        String replacement = mapping.getReplacement();

        mapping.touch(Instant.now().minus(Duration.ofDays(91)));

        try (BulkChange change = new BulkChange(mappings)) {
            ContentFilter filter = ContentFilter.ALL;
            ContentFilter.reloadAndSaveMappings(filter);

            String testContent = "Reference to " + testName;
            filter.filter(testContent);

            mappings.evictStale();
            change.commit();
        }

        mappings = ContentMappings.get();
        assertTrue(
                mappings.getMappings().containsKey(testName),
                "stale mapping matched during filtering should survive eviction");

        try (BulkChange change = new BulkChange(mappings)) {
            ContentFilter filter = ContentFilter.ALL;
            ContentFilter.reloadAndSaveMappings(filter);

            String testContent = "Still referencing " + testName;
            filter.filter(testContent);

            mappings.evictStale();
            change.commit();
        }

        mappings = ContentMappings.get();
        assertTrue(
                mappings.getMappings().containsKey(testName), "previously-stale mapping should survive a second cycle");
        assertEquals(
                replacement,
                mappings.getMappings().get(testName),
                "mapping should keep the same replacement on second cycle");
    }
}
