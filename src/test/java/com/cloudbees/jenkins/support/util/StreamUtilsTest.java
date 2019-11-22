package com.cloudbees.jenkins.support.util;

import org.junit.Assert;
import org.junit.Test;

public class StreamUtilsTest {

    @Test
    public void nullCharacterShouldNotMarkFileAsBinary() {
        String withNullCharacter = "a" + '\0' + "b";
        Assert.assertFalse(StreamUtils.isNonWhitespaceControlCharacter(withNullCharacter.getBytes()));
    }
}
