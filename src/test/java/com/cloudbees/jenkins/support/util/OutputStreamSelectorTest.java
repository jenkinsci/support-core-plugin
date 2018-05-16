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

package com.cloudbees.jenkins.support.util;

import com.github.javafaker.Faker;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

public class OutputStreamSelectorTest {

    private ByteArrayOutputStream binaryOut = spy(new ByteArrayOutputStream());
    private ByteArrayOutputStream textOut = spy(new ByteArrayOutputStream());
    private OutputStreamSelector selector = new OutputStreamSelector(() -> binaryOut, () -> textOut);

    @Test
    public void shouldNotAllowWritesAfterClose() throws IOException {
        selector.close();
        assertThatIllegalStateException()
                .isThrownBy(() -> selector.write(0))
                .withMessageContaining("closed");
        assertThatIllegalStateException()
                .isThrownBy(() -> selector.write(new byte[]{0, 1, 2, 3, 4, 5, 6, 7}))
                .withMessageContaining("closed");
        assertThatIllegalStateException()
                .isThrownBy(() -> selector.write(new byte[]{0, 1, 2, 3, 4, 5, 6, 7}, 4, 4))
                .withMessageContaining("closed");
        assertThatIllegalStateException()
                .isThrownBy(() -> selector.flush());
        assertThatIllegalStateException()
                .isThrownBy(() -> selector.getUnderlyingStream());
    }

    @Test
    public void shouldNotAllowInvalidLengths() throws IOException {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> selector.write(new byte[0], 0, -1));
    }

    @Test
    public void shouldIgnoreEmptyWritesWhereValid() throws IOException {
        selector.write(new byte[0]);
        selector.write(new byte[0], 0, 0);
        selector.close();

        assertThatIllegalStateException()
                .isThrownBy(() -> selector.write(new byte[0]));
        assertThat(binaryOut.toByteArray()).isEmpty();
        assertThat(textOut.toByteArray()).isEmpty();
    }

    @Test
    public void shouldUseBinaryStreamWhenNonPrintableCharactersFoundEarly() throws IOException {
        byte[] bytes = new byte[]{0x41, 0x42, 0x43, 0x44, 0x4, 0x46, 0x47, 0x48, 0x49, 0x50}; // 0x4 is EOT

        selector.write(bytes);
        selector.close();

        assertThat(binaryOut.toByteArray()).isNotEmpty().isEqualTo(bytes);
    }

    @Test
    public void shouldUseTextStreamWhenAllPrintable() throws IOException {
        Faker faker = new Faker();
        String contents = faker.lorem().paragraph();

        selector.write(contents.getBytes(UTF_8));
        selector.close();

        assertThat(binaryOut.toByteArray()).isEmpty();
        byte[] bytes = textOut.toByteArray();
        assertThat(bytes).isNotEmpty();
        String actual = new String(bytes, UTF_8);
        assertThat(actual).isEqualTo(contents);
    }

    @Test
    public void shouldDeferFlushUntilStreamChosen() throws IOException {
        selector.write(0x42);
        selector.flush();
        verify(binaryOut, never()).flush();
        verify(textOut, never()).flush();
        selector.close();
        verify(textOut).flush();
        verifyNoMoreInteractions(binaryOut);
    }

    @Test
    public void shouldPassAlongFlushAfterStreamChosenPreviously() throws IOException {
        for (int i = 0; i < 1024; i++) {
            selector.write(0);
        }
        verify(binaryOut, never()).flush();
        selector.flush();
        verify(binaryOut).flush();
        verifyNoMoreInteractions(textOut);
    }

    @Test
    public void shouldUseBinaryStreamForPNG() throws IOException {
        Path pngFile = Paths.get("src/main/webapp/images/16x16/support.png");
        ByteArrayOutputStream expectedStreamContents = new ByteArrayOutputStream();
        Files.copy(pngFile, expectedStreamContents);
        Files.copy(pngFile, selector);
        byte[] expected = expectedStreamContents.toByteArray();
        assertThat(textOut.toByteArray()).isEmpty();
        assertThat(binaryOut.toByteArray()).isNotEmpty().isEqualTo(expected);
    }
}
