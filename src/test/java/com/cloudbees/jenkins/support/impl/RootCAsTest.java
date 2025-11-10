package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.startsWith;

import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class RootCAsTest {

    @Test
    void getRootCAList() {
        StringWriter certsWriter = new StringWriter();
        RootCAs.getRootCAList(certsWriter);
        String rootCAs = certsWriter.toString();

        assertThat("output doesn't start with the Exception", rootCAs, startsWith("===== Trust Manager 0 =====\n"));
    }
}
