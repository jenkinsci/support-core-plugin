package com.cloudbees.jenkins.support.util;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

public class StreamUtilsTest {

    @Test
    public void nullCharacterShouldNotMarkFileAsBinary() {
        String withNullCharacter = "a" + '\0' + "b";
        assertFalse(StreamUtils.isNonWhitespaceControlCharacter(withNullCharacter.getBytes()));
    }
}
