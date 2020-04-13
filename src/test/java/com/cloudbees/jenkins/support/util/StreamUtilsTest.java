package com.cloudbees.jenkins.support.util;

import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class StreamUtilsTest {

    @Test
    public void nullCharacterShouldNotMarkFileAsBinary() {
        String withNullCharacter = "a" + '\0' + "b";
        assertFalse(StreamUtils.isNonWhitespaceControlCharacter(withNullCharacter.getBytes()));
    }
}
