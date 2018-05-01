/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

import org.apache.commons.io.IOUtils;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

public class FilteredOutputStreamTest {

    private final InputStream testInput = getClass().getResourceAsStream("/test-input.txt");
    private final ByteArrayOutputStream testOutput = new ByteArrayOutputStream();

    @Test
    public void shouldModifyStream() throws IOException {
        assertNotNull(testInput);
        ContentFilter filter = s -> s.replace("Line", "Network");
        FilteredOutputStream out = new FilteredOutputStream(testOutput, filter);

        IOUtils.copy(testInput, out);
        String contents = new String(testOutput.toByteArray(), UTF_8);

        assertNotEquals(0, contents.length());
        String[] lines = contents.split("\n");
        for (int i = 0; i < lines.length; i++) {
            assertEquals(String.format("Network %d", i), lines[i]);
        }
    }

    @Test
    public void shouldSupportLongLines() throws IOException {
        char[] input = new char[4096];
        Arrays.fill(input, '*');
        InputStream in = new ByteArrayInputStream(new String(input).getBytes(UTF_8));
        FilteredOutputStream out = new FilteredOutputStream(testOutput, s -> s.replace('*', 'a'));

        IOUtils.copy(in, out);
        String contents = new String(testOutput.toByteArray(), UTF_8);

        assertEquals("Buffer overflowed prematurely", 0, contents.length());

        out.flush();
        contents = new String(testOutput.toByteArray(), UTF_8);

        assertNotEquals("No contents written", 0, contents.length());
        assertTrue(contents.matches("^a{4096}$"));
    }
}