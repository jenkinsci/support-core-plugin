package com.cloudbees.jenkins.support.filter;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import java.util.Collections;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

public class PasswordRedactorRegexBuilderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void getPasswordPatternWhenWordsIsEmptyThenReturnNull() {
        assertThat(PasswordRedactorRegexBuilder.getPasswordPattern(Collections.emptySet()), is(nullValue()));
    }

    @Test
    public void getSecretMatcherWhenWordsIsEmptyThenReturnNull() {
        assertThat(PasswordRedactorRegexBuilder.getSecretMatcher(Collections.emptySet()), is(nullValue()));
    }
}
