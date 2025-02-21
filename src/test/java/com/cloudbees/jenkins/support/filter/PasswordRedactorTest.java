package com.cloudbees.jenkins.support.filter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PasswordRedactorTest {

    @Test
    void redactWhenSimplePairKeyValueThenPasswordRedacted(JenkinsRule r) {
        assertThat(PasswordRedactor.get().redact("key=a"), is("key=REDACTED"));
    }

    @Test
    void redactWhenPasswordContainsRegexReservedCharThenPasswordRedacted(JenkinsRule r) {
        assertThat(PasswordRedactor.get().redact("key=     *REDACTED*"), is("key=REDACTED"));
    }

    @Test
    void redactWhenMoreThanOneSecretsInInputSpaceSeparatedThenAllRedacted(JenkinsRule r) {
        assertThat(
                PasswordRedactor.get()
                        .redact("sun.java.command=jenkins.war password=word -Dcasc.reload.token=any_value_here"),
                is("sun.java.command=jenkins.war password=REDACTED -Dcasc.reload.token=REDACTED"));
    }

    @Test
    void redactWhenMoreThenOneSecretsInInputCommaSeparatedThenAllRedacted(JenkinsRule r) {
        assertThat(
                PasswordRedactor.get()
                        .redact(
                                "-Djavax.net.ssl.trustStorePassword.ddd=mySecret,Dcasc.reload.token=any_value_here,password=ip_plain"),
                is("-Djavax.net.ssl.trustStorePassword.ddd=REDACTED,Dcasc.reload.token=REDACTED,password=REDACTED"));
    }

    @Test
    void redactWhenSecretsContainKeyWordsThenAllRedacted(JenkinsRule r) {
        assertThat(
                PasswordRedactor.get()
                        .redact("Djavax.net.ssl.private_password=mySecret, passwd=password key.damp=keypass"),
                is("Djavax.net.ssl.private_password=REDACTED, passwd=REDACTED key.damp=REDACTED"));
    }

    @Test
    void redactWhenKeyAndSecretSeparatedBySpaceThenRedacted(JenkinsRule r) {
        assertThat(
                PasswordRedactor.get().redact("--argumentsRealm.passwd.<user> = pass "),
                is("--argumentsRealm.passwd.<user> =REDACTED "));
    }

    @Test
    void redactWhenSeparatorIsNulThenRedacted(JenkinsRule r) {
        assertThat(
                PasswordRedactor.get()
                        .redact(
                                "PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/binHOSTNAME=b4c2f6bb1c8e\0TERM=xterm\0PASSWORD=dockerdev\0DOCKER_HOST=tcp://docker:2376"),
                is(
                        "PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/binHOSTNAME=b4c2f6bb1c8e\0TERM=xterm\0PASSWORD=REDACTED\0DOCKER_HOST=tcp://docker:2376"));
    }

    @Test
    void redactWhenPropertiesContainSecretThenAllRedacted(JenkinsRule r) {
        Map<String, String> variables = new HashMap<>();
        variables.put("SECRET_AWS", "gdfdfdddd");
        variables.put(
                "sun.java.command",
                "org.codehaus.classworlds.Launcher -Didea.version\\=2021.2.3 hpi\\:run -Djavax.net.ssl.key\\=password -Djavax.net.passwd\\=secret");

        Map<String, String> expected = new HashMap<>();
        expected.put("SECRET_AWS", "REDACTED");
        expected.put(
                "sun.java.command",
                "org.codehaus.classworlds.Launcher -Didea.version\\=2021.2.3 hpi\\:run -Djavax.net.ssl.key\\=REDACTED -Djavax.net.passwd\\=REDACTED");

        Map<String, String> redacted = PasswordRedactor.get().redact(variables);

        assertThat(redacted, is(expected));
    }

    @Test
    void redactWhenFileIsEmptyShouldReturnInput(JenkinsRule r) {
        assertThat(new PasswordRedactor(null, null).redact("secret=passwd"), is("secret=passwd"));
    }

    @Test
    void matchWhenFileIsEmptyShouldReturnFalse(JenkinsRule r) {
        assertFalse(new PasswordRedactor(null, null).match("secret"));
    }

    @Test
    void redactWhenFileIsEmptyShouldReturnInputProperties(JenkinsRule r) {
        Map<String, String> variables = new HashMap<>();
        variables.put("secret", "gdfdfdddd");
        assertThat(new PasswordRedactor(null, null).redact(variables), is(variables));
    }
}
