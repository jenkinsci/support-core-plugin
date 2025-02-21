package com.cloudbees.jenkins.support.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class FilteredInputStreamTest {

    @Test
    void testReadSingleLine() throws IOException {
        String input = "foo bar";
        String output;
        try (FilteredInputStream fis = new FilteredInputStream(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8,
                String::toUpperCase)) {
            output = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals("FOO BAR" + System.lineSeparator(), output);
    }

    @Test
    void testReadMultipleLines() throws IOException {
        String input = "Line 1\nLine 2\nLine 3";
        String output;
        try (FilteredInputStream fis = new FilteredInputStream(
                new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8,
                String::toUpperCase)) {
            output = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals(
                "LINE 1" + System.lineSeparator() + "LINE 2" + System.lineSeparator() + "LINE 3"
                        + System.lineSeparator(),
                output);
    }

    @Test
    void testReadEmptyStream() throws IOException {
        byte[] input = new byte[0];
        String output;
        try (FilteredInputStream fis =
                new FilteredInputStream(new ByteArrayInputStream(input), StandardCharsets.UTF_8, String::toUpperCase)) {
            output = new String(fis.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertEquals("", output);
    }
}
