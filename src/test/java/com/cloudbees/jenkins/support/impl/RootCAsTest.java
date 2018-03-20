package com.cloudbees.jenkins.support.impl;

import org.junit.Test;

import java.io.StringWriter;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;

public class RootCAsTest {

    @Test
    public void getRootCAList() {
        final StringWriter certsWriter = new StringWriter();
        RootCAs.getRootCAList(certsWriter);
        final String rootCAs = certsWriter.toString();

        assertThat("output doesn't start with the Exception",
                rootCAs, startsWith("===== Trust Manager 0 =====\n"));
    }
}
