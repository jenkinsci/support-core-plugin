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

import com.cloudbees.jenkins.support.util.Anonymizer;
import org.apache.commons.io.FileUtils;
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

    @Test public void truncation() throws Exception {
        File f = tmp.newFile();
        FileUtils.writeStringToFile(f, "hello world\n");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new FileContent("-", f, false).writeTo(baos);
        assertEquals("hello world\n", baos.toString());
        baos.reset();
        new FileContent("-", f, false, 10).writeTo(baos);
        assertEquals("hello worl", baos.toString());
        baos.reset();
        new FileContent("-", f, false, 20).writeTo(baos);
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

        File file = tmp.newFile();
        FileUtils.writeStringToFile(file, "foo\nfoo/bar\nfoo/bar/baz\nfoo bar\nfoobar\n");

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        new FileContent("-", file, true).writeTo(out);
        assertEquals(foo + "\n" + foobar + "\n" + foobarbaz + "\n" + foobarSpace + "\nfoobar\n",
                out.toString());

        File file2 = tmp.newFile();
        FileUtils.writeStringToFile(file2, "foo foo/bar foo/bar/baz foo bar foobar\n");
        out.reset();
        new FileContent("-", file2, true).writeTo(out);
        assertEquals(foo + " " + foobar + " " + foobarbaz + " " + foobarSpace + " foobar\n", out.toString());
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
        new FileContent("-", file, false).writeTo(out);
        assertEquals("foo\nfoo/bar\nfoo/bar/baz\nfoo bar\nfoobar\n", out.toString());
    }

    @Test
    public void anonymizedTruncation() throws Exception {
        // TODO
    }
}
