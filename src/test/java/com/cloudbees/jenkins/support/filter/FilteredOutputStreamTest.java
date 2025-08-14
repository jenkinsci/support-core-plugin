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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.CharBuffer;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.CharSequenceInputStream;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;

public class FilteredOutputStreamTest {

    public static final String FAKE_TEXT =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor "
                    + "incididunt ut labore et dolore magna aliqua. Ut enim ad minim veniam, quis nostrud "
                    + "exercitation ullamco laboris nisi ut aliquip ex ea commodo consequat. Duis aute "
                    + "irure dolor in reprehenderit in voluptate velit esse cillum dolore eu fugiat nulla "
                    + "pariatur. Excepteur sint occaecat cupidatat non proident, sunt in culpa qui officia "
                    + "deserunt mollit anim id est laborum.";

    private final ByteArrayOutputStream testOutput = new ByteArrayOutputStream();

    @Issue("JENKINS-21670")
    @Test
    void shouldModifyStream() throws IOException {
        int nrLines = FilteredConstants.DEFAULT_DECODER_CAPACITY;
        String inputContents =
                IntStream.range(0, nrLines).mapToObj(i -> "Line " + i).collect(joining(System.lineSeparator()));
        CharSequenceInputStream input = CharSequenceInputStream.builder()
                .setCharSequence(inputContents)
                .setCharset(UTF_8)
                .get();
        ContentFilter filter = s -> s.replace("Line", "Network");
        FilteredOutputStream output = new FilteredOutputStream(testOutput, filter);

        IOUtils.copy(input, output);
        output.flush();
        String outputContents = testOutput.toString(UTF_8);

        assertThat(outputContents).isNotEmpty();
        String[] lines = FilteredConstants.EOL.split(outputContents);
        assertThat(lines)
                .allMatch(line -> !line.contains("Line") && line.startsWith("Network"))
                .hasSize(nrLines);
    }

    @Issue("JENKINS-21670")
    @Test
    void shouldSupportLinesLargerThanDefaultBufferSize() throws IOException {
        CharBuffer input = CharBuffer.allocate(FilteredConstants.DEFAULT_DECODER_CAPACITY * 10);
        for (int i = 0; i < input.capacity(); i++) {
            input.put('*');
        }
        input.flip();
        InputStream in = CharSequenceInputStream.builder()
                .setCharSequence(input)
                .setCharset(UTF_8)
                .get();
        FilteredOutputStream out = new FilteredOutputStream(testOutput, s -> s.replace('*', 'a'));

        IOUtils.copy(in, out);
        String contents = testOutput.toString(UTF_8);

        assertThat(contents).isEmpty();

        out.flush();
        contents = testOutput.toString(UTF_8);

        assertThat(contents).isNotEmpty().matches("^a+$");
    }

    @Test
    void shouldNotAllowOperationsAfterClose() throws IOException {
        FilteredOutputStream out = new FilteredOutputStream(testOutput, s -> s);
        out.close();
        assertThatIllegalStateException().isThrownBy(() -> out.write(0));
        assertThatIllegalStateException().isThrownBy(() -> out.write(new byte[0]));
        assertThatIllegalStateException().isThrownBy(() -> out.write(new byte[] {1, 2, 3, 4}, 0, 4));
        assertThatIllegalStateException().isThrownBy(out::flush);
        assertThatIllegalStateException().isThrownBy(out::close);
        assertThatIllegalStateException().isThrownBy(out::reset);
        assertThatIllegalStateException().isThrownBy(out::asWriter);
    }

    @Test
    void shouldSupportMultipleUsesWithReset() throws IOException {
        FilteredOutputStream out = new FilteredOutputStream(testOutput, s -> s);
        List<String> paragraphs = Stream.generate(() -> FAKE_TEXT).limit(10).toList();
        StringBuilder b = new StringBuilder();
        for (String paragraph : paragraphs) {
            out.write(paragraph.getBytes(UTF_8));
            out.write('\n');
            b.append(paragraph).append('\n');
            out.reset();
        }
        String expected = b.toString();
        assertThat(testOutput.toString(UTF_8)).isNotEmpty().isEqualTo(expected);
    }

    @Test
    void shouldSupportWriterView() throws IOException {
        FilteredOutputStream out = new FilteredOutputStream(testOutput, s -> s.toUpperCase(Locale.ENGLISH));
        String original = FAKE_TEXT;
        try (FilteredWriter writer = out.asWriter()) {
            writer.write(original);
        }
        assertThat(testOutput.toString(UTF_8)).isNotEmpty().isEqualTo(original.toUpperCase(Locale.ENGLISH));
    }
}
