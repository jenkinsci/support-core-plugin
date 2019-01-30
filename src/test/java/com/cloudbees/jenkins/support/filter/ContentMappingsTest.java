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
import org.apache.commons.io.IOUtils;
import org.assertj.core.api.Assertions;
import org.junit.*;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.recipes.LocalData;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.StreamSupport;
import java.util.zip.ZipFile;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

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
        Assertions.assertThat(originals).containsExactly("LongestNameHere", "LongerName", "ShortName");
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

    @Issue("JENKINS-53184")
    @Test
    @LocalData
    public void jenkinsVersionIncludedAsStopWord() {
        rr.then(r -> {
            Jenkins.VERSION = "1.2.3.4";
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
            assertTrue(contentMappings.getMappings().size() == initialMappingsSize);

            Jenkins.VERSION = originalVersion;
            contentMappings.clear();

            stopWords = contentMappings.getStopWords();
            assertFalse(stopWords.contains(ALT_VERSION));
            assertTrue(stopWords.contains(originalVersion));
        });
    }
}
