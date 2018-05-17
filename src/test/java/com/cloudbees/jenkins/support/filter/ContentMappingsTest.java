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
import org.assertj.core.api.Assertions;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;

public class ContentMappingsTest { 

    @Rule
    public JenkinsRule r = new JenkinsRule();
    
    @Test
    public void dynamicStopWordsAreAddedWhenReloading() throws Exception {
        FreeStyleProject job = r.createFreeStyleProject();
        String[] expectedStopWords = {
            job.getPronoun().toLowerCase(Locale.ENGLISH), 
            job.getTaskNoun().toLowerCase(Locale.ENGLISH) 
        };
        ContentMappings mappings = ContentMappings.get();
        assertThat(mappings.getStopWords(), not(hasItems(expectedStopWords)));
        mappings.reload();
        assertThat(mappings.getStopWords(), hasItems(expectedStopWords));
    }

    @Test
    public void contentMappingsOrderedByLengthDescending() throws IOException {
        r.createFreeStyleProject("ShortName");
        r.createFreeStyleProject("LongerName");
        r.createFreeStyleProject("LongestNameHere");
        ContentFilter.ALL.reload();
        ContentMappings mappings = ContentMappings.get();
        List<String> originals = StreamSupport.stream(mappings.spliterator(), false)
                .map(ContentMapping::getOriginal)
                .collect(toList());
        Assertions.assertThat(originals).containsExactly("LongestNameHere", "LongerName", "ShortName");
    }
}
