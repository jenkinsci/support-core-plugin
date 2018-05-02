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
import org.apache.commons.io.input.CharSequenceInputStream;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

public class FilteredOutputStreamTest {

    private final ByteArrayOutputStream testOutput = new ByteArrayOutputStream();

    @Issue("JENKINS-21670")
    @Test
    public void shouldModifyStream() throws IOException {
        final int nrLines = FilteredOutputStream.DEFAULT_DECODER_CAPACITY;
        String inputContents = IntStream.range(0, nrLines).mapToObj(i -> "Line " + i).collect(Collectors.joining(System.lineSeparator()));
        CharSequenceInputStream input = new CharSequenceInputStream(inputContents, UTF_8);
        ContentFilter filter = s -> s.replace("Line", "Network");
        FilteredOutputStream output = new FilteredOutputStream(testOutput, filter);

        IOUtils.copy(input, output);
        String outputContents = new String(testOutput.toByteArray(), UTF_8);

        assertNotEquals(0, outputContents.length());
        String[] lines = FilteredOutputStream.EOL.split(outputContents);
        for (int i = 0; i < lines.length; i++) {
            assertEquals(String.format("Network %d", i), lines[i]);
        }
    }

    @Issue("JENKINS-21670")
    @Test
    public void shouldSupportLinesLargerThanDefaultBufferSize() throws IOException {
        CharBuffer input = CharBuffer.allocate(FilteredOutputStream.DEFAULT_DECODER_CAPACITY * 10);
        for (int i = 0; i < input.capacity(); i++) {
            input.put('*');
        }
        input.flip();
        InputStream in = new CharSequenceInputStream(input, UTF_8);
        FilteredOutputStream out = new FilteredOutputStream(testOutput, s -> s.replace('*', 'a'));

        IOUtils.copy(in, out);
        String contents = new String(testOutput.toByteArray(), UTF_8);

        assertEquals("Buffer overflowed prematurely", 0, contents.length());

        out.flush();
        contents = new String(testOutput.toByteArray(), UTF_8);

        assertNotEquals("No contents written", 0, contents.length());
        assertTrue(contents.matches("^a+$"));
    }
}
