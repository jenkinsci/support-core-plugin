package com.cloudbees.jenkins.support.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MarkdownTest {

    @Test
    void escapeUnderscore() {
        assertEquals("a&#95;b", Markdown.escapeUnderscore("a_b"));
        assertEquals("a`b", Markdown.escapeUnderscore("a`b"));
    }

    @Test
    void escapeBacktick() {
        assertEquals("a&#96;b", Markdown.escapeBacktick("a`b"));
        assertEquals("a_b", Markdown.escapeBacktick("a_b"));
    }

    @Test
    void prettyNone() {
        assertEquals(Markdown.NONE_STRING, Markdown.prettyNone(null));
        assertEquals(Markdown.NONE_STRING, Markdown.prettyNone(""));
        assertEquals("a", Markdown.prettyNone("a"));
    }
}
