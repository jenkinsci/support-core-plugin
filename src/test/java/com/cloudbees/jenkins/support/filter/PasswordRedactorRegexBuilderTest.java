package com.cloudbees.jenkins.support.filter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

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
