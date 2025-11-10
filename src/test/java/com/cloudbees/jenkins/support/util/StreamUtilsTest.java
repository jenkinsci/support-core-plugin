package com.cloudbees.jenkins.support.util;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class StreamUtilsTest {

    @Test
    void nullCharacterShouldNotMarkFileAsBinary() {
        String withNullCharacter = "a" + '\0' + "b";
        assertFalse(StreamUtils.isNonWhitespaceControlCharacter(withNullCharacter.getBytes()));
    }
}
