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

package com.cloudbees.jenkins.support.api;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.util.Anonymizer;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.io.File;

import static org.junit.Assert.assertEquals;

public class FileContentTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    @Rule public JenkinsRule jenkins = new JenkinsRule();

    @Before
    public void setUp() {
        SupportPlugin.AnonymizationSettings settings = SupportPlugin.getInstance().getAnonymizationSettings();
        settings.setAnonymizeComputers(true);
        settings.setAnonymizeItems(true);
        settings.setAnonymizeLabels(true);
        settings.setAnonymizeNodes(true);
        settings.setAnonymizeUsers(true);
        settings.setAnonymizeViews(true);
    }

    @Test
    public void unanonymized() throws Exception {
        jenkins.createFreeStyleProject("foo");
        jenkins.createFreeStyleProject("foo/bar");
        jenkins.createFreeStyleProject("foo/bar/baz");
        jenkins.createFreeStyleProject("foo bar");

        File file = tmp.newFile();
        FileUtils.writeStringToFile(file, "foo\nfoo/bar\nfoo/bar/baz\nfoo bar\nfoobar\n");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new FileContent(new ContentData("-", false), file).writeTo(out);
        assertEquals("foo\nfoo/bar\nfoo/bar/baz\nfoo bar\nfoobar\n", out.toString());
    }

    @Test public void truncation() throws Exception {
        File f = tmp.newFile();
        FileUtils.writeStringToFile(f, "hello world\n");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new FileContent(new ContentData("-", false), f).writeTo(baos);
        assertEquals("hello world\n", baos.toString());
        baos.reset();
        new FileContent(new ContentData("-", false), f, 10).writeTo(baos);
        assertEquals("hello worl", baos.toString());
        baos.reset();
        new FileContent(new ContentData("-", false), f, 20).writeTo(baos);
        assertEquals("hello world\n", baos.toString());
    }

    @Test
    public void anonymization() throws Exception {
        jenkins.createFreeStyleProject("foo");
        jenkins.createFreeStyleProject("foo/bar");
        jenkins.createFreeStyleProject("foo/bar/baz");
        jenkins.createFreeStyleProject("foo bar");
        Anonymizer.refresh();
        String foo = Anonymizer.anonymize("foo");
        String foobar = Anonymizer.anonymize("foo/bar");
        String foobarbaz = Anonymizer.anonymize("foo/bar/baz");
        String foobarSpace = Anonymizer.anonymize("foo bar");

        // Experienced some problems with Windows for this test - using System.lineSeparator() for all new lines
        String ls = System.lineSeparator();
        File file = tmp.newFile();
        FileUtils.writeStringToFile(file, "foo" + ls + "foo/bar" + ls + "foo/bar/baz" + ls + "foo bar" + ls + "foobar" + ls);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new FileContent(new ContentData("-", true), file).writeTo(out);
        assertEquals(foo + ls + foobar + ls + foobarbaz + ls + foobarSpace + ls + "foobar" + ls,
                out.toString());

        File file2 = tmp.newFile();
        FileUtils.writeStringToFile(file2, "foo foo/bar foo/bar/baz foo bar foobar" + ls);
        out.reset();
        new FileContent(new ContentData("-", true), file2).writeTo(out);
        assertEquals(foo + " " + foobar + " " + foobarbaz + " " + foobarSpace + " foobar" + ls, out.toString());
    }

    @Test
    public void anonymizedTruncation() throws Exception {
        jenkins.createFreeStyleProject("hello");
        Anonymizer.refresh();
        String hello = Anonymizer.anonymize("hello");

        File f = tmp.newFile();
        FileUtils.writeStringToFile(f, "hello world" + System.lineSeparator());

        String fullContent = hello + " world" + System.lineSeparator();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new FileContent(new ContentData("-", true), f).writeTo(baos);
        assertEquals(fullContent, baos.toString());
        baos.reset();
        new FileContent(new ContentData("-", true), f, 10).writeTo(baos);
        assertEquals(fullContent.substring(0, 10), baos.toString());
        baos.reset();
        new FileContent(new ContentData("-", true), f, 20).writeTo(baos);
        assertEquals(fullContent.substring(0, 20), baos.toString());
        baos.reset();
        new FileContent(new ContentData("-", true), f, 100).writeTo(baos);
        assertEquals(fullContent, baos.toString());
    }
}
