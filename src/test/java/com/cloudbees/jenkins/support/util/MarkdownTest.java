package com.cloudbees.jenkins.support.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class MarkdownTest {

    @Test
    public void escapeUnderscore() {
        assertEquals("a&#95;b", Markdown.escapeUnderscore("a_b"));
        assertEquals("a`b", Markdown.escapeUnderscore("a`b"));
    }

    @Test
    public void escapeBacktick() {
        assertEquals("a&#96;b", Markdown.escapeBacktick("a`b"));
        assertEquals("a_b", Markdown.escapeBacktick("a_b"));
    }
}
