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

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringWriter;
import java.nio.CharBuffer;
import java.util.stream.IntStream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CharSequenceReader;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

class FilteredWriterTest {

    @Issue("JENKINS-21670")
    @Test
    void shouldModifyStream() throws Exception {
        int nrLines = FilteredConstants.DEFAULT_DECODER_CAPACITY;
        String inputContents =
                IntStream.range(0, nrLines).mapToObj(i -> "ManagedNode" + i).collect(joining(System.lineSeparator()));
        CharSequenceReader reader = new CharSequenceReader(inputContents);
        ContentFilter filter = s -> s.replace("ManagedNode", "Anonymous_");
        StringWriter output = new StringWriter();
        FilteredWriter writer = new FilteredWriter(output, filter);

        IOUtils.copy(reader, writer);
        writer.flush();
        String outputContents = output.toString();

        assertThat(outputContents).isNotEmpty();
        String[] lines = FilteredConstants.EOL.split(outputContents);
        assertThat(lines)
                .allMatch(line -> !line.contains("ManagedNode") && line.startsWith("Anonymous_"))
                .hasSize(nrLines);
    }

    @Issue("JENKINS-21670")
    @Test
    void shouldSupportLinesLongerThanDefaultBufferSize() throws Exception {
        CharBuffer input = CharBuffer.allocate(FilteredConstants.DEFAULT_DECODER_CAPACITY * 10);
        for (int i = 0; i < input.capacity(); i++) {
            input.put('+');
        }
        input.flip();
        CharSequenceReader reader = new CharSequenceReader(input);
        ContentFilter filter = s -> s.replace('+', '-');
        StringWriter output = new StringWriter();
        FilteredWriter writer = new FilteredWriter(output, filter);

        IOUtils.copy(reader, writer);
        assertThat(output.toString()).isEmpty();

        writer.flush();

        assertThat(output.toString()).isNotEmpty().matches("^-+$");
    }
}
