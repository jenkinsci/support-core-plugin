package com.cloudbees.jenkins.support.filter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PasswordRedactorRegexBuilderTest {

    @Test
    void getPasswordPatternWhenWordsIsEmptyThenReturnNull(JenkinsRule r) {
        assertThat(PasswordRedactorRegexBuilder.getPasswordPattern(Collections.emptySet()), is(nullValue()));
    }

    @Test
    void getSecretMatcherWhenWordsIsEmptyThenReturnNull(JenkinsRule r) {
        assertThat(PasswordRedactorRegexBuilder.getSecretMatcher(Collections.emptySet()), is(nullValue()));
    }
}
