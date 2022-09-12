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

import hudson.model.FreeStyleProject;
import jenkins.model.Jenkins;
import org.hamcrest.MatcherAssert;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ContentMappingsTest {

    private static String originalVersion;

    @BeforeClass
    public static void storeVersion() {
        originalVersion = Jenkins.VERSION;
    }

    @After
    public void resetVersion() {
        Jenkins.VERSION = originalVersion;
        rr.then(r -> {
            ContentMappings.get().clear();
        });
    }

    @Rule
    public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Test
    public void dynamicStopWordsAreAddedWhenReloading() throws Exception {
        rr.then(r -> {
            FreeStyleProject job = r.createFreeStyleProject();
            String[] expectedStopWords = {
                job.getPronoun().toLowerCase(Locale.ENGLISH),
                job.getTaskNoun().toLowerCase(Locale.ENGLISH)
            };
            ContentMappings mappings = ContentMappings.get();
            assertThat(mappings.getStopWords(), not(hasItems(expectedStopWords)));
            mappings.reload();
            assertThat(mappings.getStopWords(), hasItems(expectedStopWords));
        });
    }

    @Test
    public void contentMappingsOrderedByLengthDescending() throws IOException {
        rr.then(r -> {
            r.createFreeStyleProject("ShortName");
            r.createFreeStyleProject("LongerName");
            r.createFreeStyleProject("LongestNameHere");
            ContentFilter.ALL.reload();
            ContentMappings mappings = ContentMappings.get();
            List<String> originals = StreamSupport.stream(mappings.spliterator(), false)
                    .map(ContentMapping::getOriginal)
                    .collect(toList());
            assertThat(originals, containsInRelativeOrder("LongestNameHere", "LongerName", "ShortName"));
        });
    }

    @Test
    public void contentMappingsSurviveSerializationRoundTrip() {
        ContentMapping mapping = ContentMapping.of("test_original", "test_replacement");
        rr.then(r -> {
            ContentMappings.get().getMappingOrCreate(mapping.getOriginal(), original -> mapping);
            assertThat(ContentMappings.get().getMappings(), hasEntry(mapping.getOriginal(), mapping.getReplacement()));
        });
        rr.then(r -> {
            assertThat(ContentMappings.get().getMappings(), hasEntry(mapping.getOriginal(), mapping.getReplacement()));
        });
    }

    @Test
    @Ignore("Bug to be resolved. Elements removed aren't removed from the persisted mapping (ContentMappings.xml")
    public void contentMappingsRemovedSerialized() {
        //To be final and reuse in the steps
        StringBuilder jobReplacement = new StringBuilder();

        // Create a project and check its mapping
        rr.then(r -> {
            //Create a project
            r.createFreeStyleProject("ShortName");
            ContentFilter.ALL.reload();

            //Store their replacements
            ContentMappings mappings = ContentMappings.get();
            jobReplacement.append(mappings.getMappings().get("ShortName"));

            //Check if the mapping exists
            assertThat(mappings.getMappings(), hasEntry("ShortName", jobReplacement.toString()));
        });

        // Mapping persisted after restart and remove the project
        rr.then(r -> {
            ContentMappings mappings = ContentMappings.get();

            //Check if the mapping exists after restart
            assertThat(mappings.getMappings(), hasEntry("ShortName", jobReplacement.toString()));

            //Remove the project
            r.jenkins.remove(r.jenkins.getItem("ShortName"));

            //Run the getMappingOrCreate of every mapping
            SensitiveContentFilter.get().reload();
        });

        // Mapping removed after restart
        rr.then(r -> {
            ContentMappings mappings = ContentMappings.get();

            //Check if the mapping exists after restart
            assertThat("The mapping of a removed project shouldn't persist", mappings.getMappings(), not(hasEntry("ShortName", jobReplacement.toString())));
        });
    }

    @Issue("JENKINS-53184")
    @Test
    @LocalData
    public void jenkinsVersionIncludedAsStopWord() {
        rr.then(r -> {
            Jenkins.VERSION = "1.2.3.4";
            // With JCasC the ContentMappings is created before Jenkins.VERSION is called, so the previous instruction
            // doesn't take effect in the mappings. We have to force the mappings to reload after the version is set.
            ContentMappings.get().clear();
            ContentMappings mappings = ContentMappings.get();

            // Jenkins version added to stop words
            assertTrue(mappings.getStopWords().contains(Jenkins.VERSION));

            // Previous mappings with Jenkins version are ignored
            assertTrue(mappings.getMappings().isEmpty());
        });
    }

    @Issue("JENKINS-54688")
    @Test
    @LocalData
    public void operatingSystemIncludedAsStopWord() {
        rr.then(r -> {
            String os = "Linux";
            ContentMappings mappings = ContentMappings.get();

            // The Operating system is added to stop words
            assertTrue(mappings.getStopWords().contains(os.toLowerCase(Locale.ENGLISH)));

            // Previous mappings with the operating system are ignored
            assertTrue(mappings.getMappings().isEmpty());
        });
    }

    @Issue("JENKINS-66023")
    @Test
    @LocalData
    public void additionalStopWordsIncludedAsStopWord() {
        String[] expectedStopWords = {
            "abc", 
            "https://core.example.com", 
            "john doe", 
            "192.168.0.1", 
            "<h1>",
            "  leadingspaces", 
            "trailingspaces  "
        };
        rr.then(r -> {
            ContentMappings mappings = ContentMappings.get();
            MatcherAssert.assertThat(mappings.getStopWords(), hasItems(expectedStopWords));
        });
    }

    private static ContentMapping identityMapping(String original) {
        return ContentMapping.of(original, original);
    }

    @Test
    public void clear() {
        rr.then(r -> {
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
        });
    }
}
