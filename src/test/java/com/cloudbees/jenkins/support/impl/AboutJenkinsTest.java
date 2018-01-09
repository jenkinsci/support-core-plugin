/*
 * Copyright © 2013 CloudBees, Inc.
 * This is proprietary code. All rights reserved.
 */
package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.util.Anonymizer;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * @author Stephen Connolly
 */
public class AboutJenkinsTest {
    @Rule public JenkinsRule jenkins = new JenkinsRule();

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

    @Test
    public void anonymized() throws Exception {
        jenkins.createSlave("slave1", "test", null);
        jenkins.createSlave("slave2", "test", null);
        Anonymizer.refresh();

        List<Content> contents = new ArrayList<>();
        Component aboutJenkins = new AboutJenkins();
        aboutJenkins.addContents(new Container() {
            @Override
            public void add(Content content) {
                if (content != null) {
                    contents.add(content);
                }
            }
        }, true);

        for (Content content : contents) {
            if (content.getName().equals("nodes.md")) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                content.writeTo(baos);
                String toString = baos.toString();
                assertThat(toString, not(containsString("slave1")));
                assertThat(toString, not(containsString("slave2")));
            }
        }
    }
}
