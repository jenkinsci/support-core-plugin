/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package com.cloudbees.jenkins.support.util;

import hudson.model.FreeStyleProject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class AnonymizerTest {
    @Rule public JenkinsRule jenkins = new JenkinsRule();

    private Set<String> anonymizedNames = new HashSet<>();

    @Before
    public void setUp() {
        Anonymizer.updateFile();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void pathsAnonymized() throws IOException, ClassNotFoundException {
        MockFolder folderA = jenkins.createFolder("FA");
        FreeStyleProject jobA1 = folderA.createProject(FreeStyleProject.class, "A1");
        FreeStyleProject jobA2 = folderA.createProject(FreeStyleProject.class, "A2");
        MockFolder folderAA = folderA.createProject(MockFolder.class, "AA");
        FreeStyleProject jobAA1 = folderAA.createProject(FreeStyleProject.class, "AA1");
        MockFolder folderB = jenkins.createFolder("B");
        FreeStyleProject jobB1 = folderB.createProject(FreeStyleProject.class, "B 1");
        FreeStyleProject jobB2 = folderB.createProject(FreeStyleProject.class, "B2/");
        FreeStyleProject jobB3 = folderB.createProject(FreeStyleProject.class, "B.3.");
        Anonymizer.refresh();

        String anonFolderA = assertCorrectLabel(folderA.getFullName());
        String anonJobA1 = assertCorrectLabel(jobA1.getFullName());
        assertThat(anonJobA1, startsWith(anonFolderA + "/"));
        assertThat(anonJobA1.substring(anonFolderA.length()), containsString("_"));

        String anonJobA2 = assertCorrectLabel(jobA2.getFullName());
        assertThat(anonJobA2, startsWith(anonFolderA + "/"));
        assertThat(anonJobA2.substring(anonFolderA.length()), containsString("_"));

        String anonFolderAA = assertCorrectLabel(folderAA.getFullName());
        assertThat(anonFolderAA, startsWith(anonFolderA));
        assertThat(anonFolderAA.substring(anonFolderA.length()), containsString("_"));

        String anonJobAA1 = assertCorrectLabel(jobAA1.getFullName());
        assertThat(anonJobAA1, startsWith(anonFolderAA + "/"));
        assertThat(anonJobAA1.substring(anonFolderAA.length()), containsString("_"));

        String anonFolderB = assertCorrectLabel(folderB.getFullName());
        String anonJobB1 = assertCorrectLabel(jobB1.getFullName());
        assertThat(anonJobB1, startsWith(anonFolderB + "/"));
        assertThat(anonJobB1.substring(anonFolderB.length()), containsString("_"));

        String anonJobB2 = assertCorrectLabel(jobB2.getFullName());
        assertThat(anonJobB2, startsWith(anonFolderB + "/"));
        assertThat(anonJobB2.substring(anonFolderB.length()), containsString("_"));
        assertThat(anonJobB2, endsWith("/"));

        String anonJobB3 = assertCorrectLabel(jobB3.getFullName());
        assertThat(anonJobB3, startsWith(anonFolderB + "/"));
        assertThat(anonJobB3.substring(anonFolderB.length()), containsString("_"));

        File anonymizedNamesFile = new File(jenkins.getInstance().getRootDir(), "secrets/anonymized-names");
        assertTrue(anonymizedNamesFile.exists());
        assertEquals(Anonymizer.getAnonMap(),
                (Map<String, String>) new ObjectInputStream(new FileInputStream(anonymizedNamesFile)).readObject());
    }

    @Test
    public void excludedWords() throws IOException {
        jenkins.createFreeStyleProject("A");
        MockFolder folder = jenkins.createFolder("master");
        folder.createProject(FreeStyleProject.class, "fakename");
        Anonymizer.refresh();

        assertEquals("A", Anonymizer.anonymize("A"));
        assertEquals("master", Anonymizer.anonymize("master"));
        assertThat(Anonymizer.anonymize("master/fakename"), startsWith("master"));
    }

    private String assertCorrectLabel(String actual) {
        String anon = Anonymizer.anonymize(actual);
        assertTrue(anon.startsWith("item_"));
        assertNotEquals(actual, anon);
        String anon2 = Anonymizer.anonymize(actual);
        assertEquals(anon, anon2);
        assertTrue(anonymizedNames.add(anon));
        return anon.substring("item_".length());
    }
}
