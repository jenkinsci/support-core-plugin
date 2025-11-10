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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cloudbees.jenkins.support.filter.ContentFilter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.jvnet.hudson.test.Issue;

class FileContentTest {

    @TempDir
    private File tmp;

    @Test
    void truncation() throws Exception {
        File f = File.createTempFile("junit", null, tmp);
        FileUtils.writeStringToFile(f, "hello world\n", Charset.defaultCharset());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new FileContent("-", f).writeTo(baos);
        assertEquals("hello world\n", baos.toString());

        baos.reset();
        new FileContent("-", f, 10).writeTo(baos);
        assertEquals("hello worl", baos.toString());

        baos.reset();
        new FileContent("-", f, 20).writeTo(baos);
        assertEquals("hello world\n", baos.toString());
    }

    @Test
    @Issue("JENKINS-71466")
    void encoding() throws Exception {
        File f = new File(getClass().getResource("non-utf8.txt").getFile());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new FileContent("-", f).writeTo(baos);
        assertTrue(baos.toString().contains("Checking folder"));
        baos.reset();
        new FileContent("-", f).writeTo(baos, input -> input);
        assertTrue(baos.toString().contains("Checking folder"));
    }

    @Test
    void normalizesLineEndingsWhenFiltering() throws IOException {
        File f = File.createTempFile("junit", null, tmp);
        FileUtils.writeStringToFile(f, "Before\r\nlines\n", StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new FileContent("-", f).writeTo(baos, s -> s.replace("Before", "After"));
        // Note that besides the filtering, \r has been dropped!
        assertEquals("After\nlines\n", baos.toString());
    }

    @Test
    void preservesLineEndingsWithContentFilterNone() throws IOException {
        File f = File.createTempFile("junit", null, tmp);
        FileUtils.writeStringToFile(f, "Cool\r\nlines\n", StandardCharsets.UTF_8);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        new FileContent("-", f).writeTo(baos, ContentFilter.NONE);
        assertEquals("Cool\r\nlines\n", baos.toString());
    }
}
