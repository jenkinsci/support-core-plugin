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

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;

import static org.junit.Assert.assertEquals;

public class FileContentTest {

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test public void truncation() throws Exception {
        File f = tmp.newFile();
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
}
