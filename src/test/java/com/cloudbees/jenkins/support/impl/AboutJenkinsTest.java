/*
 * Copyright © 2013 CloudBees, Inc.
 */
package com.cloudbees.jenkins.support.impl;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Stephen Connolly
 */
public class AboutJenkinsTest {

    @Test
    public void mayBeDateSmokes() throws Exception {
        assertThat("null not a date", AboutJenkins.mayBeDate(null), is(false));
        assertThat("empty not a date", AboutJenkins.mayBeDate(""), is(false));
        assertThat("number not a date", AboutJenkins.mayBeDate("1"), is(false));
        assertThat("yyyy-mm-dd_hh-mm-ss not a date", AboutJenkins.mayBeDate("yyyy-mm-dd_hh-mm-ss"), is(false));
        assertThat("valid", AboutJenkins.mayBeDate("2000-01-01_00-00-00"), is(true));
        assertThat("in the year 3000", AboutJenkins.mayBeDate("3000-01-01_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2-00-01-01_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("20-0-01-01_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("200--01-01_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000001-01_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-21-01_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000--1-01_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-0--01_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01_01_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01001_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01--1_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-41_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-0-_00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-00-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01000-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01--0-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-30-00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-0--00-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-00000-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-00--0-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-00-60-00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-00-0--00"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-00-00000"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-00-00--0"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-00-00-60"), is(false));
        assertThat("malformatted", AboutJenkins.mayBeDate("2000-01-01-00-00-0-"), is(false));
        assertThat("valid", AboutJenkins.mayBeDate("2014-03-24_12-48-41"), is(true));
    }
}
