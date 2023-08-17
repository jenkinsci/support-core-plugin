package com.cloudbees.jenkins.support.filter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;

import java.util.HashMap;
import java.util.Map;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class PasswordRedactorTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Test
    public void redactWhenSimplePairKeyValueThenPasswordRedacted() {
        assertThat(PasswordRedactor.get().redact("key=a"), is("key=REDACTED"));
    }

    @Test
    public void redactWhenPasswordContainsRegexReservedCharThenPasswordRedacted() {
        assertThat(PasswordRedactor.get().redact("key=     *REDACTED*"), is("key=REDACTED"));
    }

    @Test
    public void redactWhenMoreThanOneSecretsInInputSpaceSeparatedThenAllRedacted() {
        assertThat(
                PasswordRedactor.get()
                        .redact("sun.java.command=jenkins.war password=word -Dcasc.reload.token=any_value_here"),
                is("sun.java.command=jenkins.war password=REDACTED -Dcasc.reload.token=REDACTED"));
    }

    @Test
    public void redactWhenMoreThenOneSecretsInInputCommaSeparatedThenAllRedacted() {
        assertThat(
                PasswordRedactor.get()
                        .redact(
                                "-Djavax.net.ssl.trustStorePassword.ddd=mySecret,Dcasc.reload.token=any_value_here,password=ip_plain"),
                is("-Djavax.net.ssl.trustStorePassword.ddd=REDACTED,Dcasc.reload.token=REDACTED,password=REDACTED"));
    }

    @Test
    public void redactWhenSecretsContainKeyWordsThenAllRedacted() {
        assertThat(
                PasswordRedactor.get()
                        .redact("Djavax.net.ssl.private_password=mySecret, passwd=password key.damp=keypass"),
                is("Djavax.net.ssl.private_password=REDACTED, passwd=REDACTED key.damp=REDACTED"));
    }

    @Test
    public void redactWhenKeyAndSecretSeparatedBySpaceThenRedacted() {
        assertThat(
                PasswordRedactor.get().redact("--argumentsRealm.passwd.<user> = pass "),
                is("--argumentsRealm.passwd.<user> =REDACTED "));
    }

    @Test
    public void redactWhenSeparatorIsNulThenRedacted() {
        assertThat(
                PasswordRedactor.get()
                        .redact(
                                "PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/binHOSTNAME=b4c2f6bb1c8e\0TERM=xterm\0PASSWORD=dockerdev\0DOCKER_HOST=tcp://docker:2376"),
                is(
                        "PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/binHOSTNAME=b4c2f6bb1c8e\0TERM=xterm\0PASSWORD=REDACTED\0DOCKER_HOST=tcp://docker:2376"));
    }

    @Test
    public void redactWhenPropertiesContainSecretThenAllRedacted() {
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
    public void redactWhenFileIsEmptyShouldReturnInput() {
        assertThat(new PasswordRedactor(null, null).redact("secret=passwd"), is("secret=passwd"));
    }

    @Test
    public void matchWhenFileIsEmptyShouldReturnFalse() {
        assertFalse(new PasswordRedactor(null, null).match("secret"));
    }

    @Test
    public void redactWhenFileIsEmptyShouldReturnInputProperties() {
        Map<String, String> variables = new HashMap<>();
        variables.put("secret", "gdfdfdddd");
        assertThat(new PasswordRedactor(null, null).redact(variables), is(variables));
    }
}
