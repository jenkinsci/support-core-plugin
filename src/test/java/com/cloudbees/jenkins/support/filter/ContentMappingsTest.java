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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;

import hudson.model.FreeStyleProject;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;
import jenkins.model.Jenkins;
import org.hamcrest.MatcherAssert;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
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
    @Disabled("Bug to be resolved. Elements removed aren't removed from the persisted mapping (ContentMappings.xml")
    void contentMappingsRemovedSerialized(JenkinsRule r) throws Throwable {
        // To be final and reuse in the steps
        StringBuilder jobReplacement = new StringBuilder();

        // Create a project and check its mapping
        // Create a project
        r.createFreeStyleProject("ShortName");
        ContentFilter.ALL.reload();

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

        // Run the getMappingOrCreate of every mapping
        SensitiveContentFilter.get().reload();

        r.restart();

        // Mapping removed after restart
        mappings = ContentMappings.get();

        // Check if the mapping exists after restart
        assertThat(
                "The mapping of a removed project shouldn't persist",
                mappings.getMappings(),
                not(hasEntry("ShortName", jobReplacement.toString())));
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
}
