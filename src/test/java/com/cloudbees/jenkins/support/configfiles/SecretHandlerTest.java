package com.cloudbees.jenkins.support.configfiles;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cloudbees.plugins.credentials.SecretBytes;
import hudson.util.Secret;
import java.io.File;
import java.nio.charset.Charset;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class SecretHandlerTest {

    private String xml;

    @BeforeEach
    void setup() {
        Secret secret1 = Secret.fromString("this-is-a-secret");
        SecretBytes secret2 = SecretBytes.fromBytes("this-is-another-type-of-secret".getBytes());
        assertEquals("this-is-a-secret", secret1.getPlainText());
        assertEquals("this-is-another-type-of-secret", new String(secret2.getPlainData()));
        String encryptedSecret1 = secret1.getEncryptedValue();
        String encryptedSecret2 = secret2.toString();
        xml = "<com.cloudbees.plugins.credentials.SystemCredentialsProvider plugin=\"credentials@1.18\">\n"
                + "    <domainCredentialsMap class=\"hudson.util.CopyOnWriteMap$Hash\">\n"
                + "        <entry>\n"
                + "            <com.cloudbees.plugins.credentials.domains.Domain>\n"
                + "                <specifications/>\n"
                + "            </com.cloudbees.plugins.credentials.domains.Domain>\n"
                + "            <java.util.concurrent.CopyOnWriteArrayList>\n"
                + "                <com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                + "                    <scope>GLOBAL</scope>\n"
                + "                    <id>f9ebaa5c-a7fc-46e4-93ab-453699781181</id>\n"
                + "                    <description>Alice</description>\n"
                + "                    <username/>\n"
                + "                    <password>"
                + encryptedSecret1 + "</password>\n"
                + "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                + "                <com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                + "                    <scope>GLOBAL</scope>\n"
                + "                    <id>f9ebaa5c-a7fc-46e4-93ab-453699781182</id>\n"
                + "                    <description>Bobby&#0x;</description>\n"
                + "                    <username/>\n"
                + "                    <password>"
                + encryptedSecret2 + "</password>\n"
                + "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                + "            </java.util.concurrent.CopyOnWriteArrayList>\n"
                + "        </entry>\n"
                + "    </domainCredentialsMap>\n"
                + "</com.cloudbees.plugins.credentials.SystemCredentialsProvider>";
    }

    @Issue("JENKINS-41653")
    @Test
    void shouldPutAPlaceHolderInsteadOfSecret(JenkinsRule j) throws Exception {
        File file = File.createTempFile("test", ".xml");
        FileUtils.writeStringToFile(file, xml, Charset.defaultCharset());
        String patchedXml = SecretHandler.findSecrets(file);
        String expectedXml =
                "<com.cloudbees.plugins.credentials.SystemCredentialsProvider plugin=\"credentials@1.18\">\n"
                        + "    <domainCredentialsMap class=\"hudson.util.CopyOnWriteMap$Hash\">\n"
                        + "        <entry>\n"
                        + "            <com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "                <specifications/>\n"
                        + "            </com.cloudbees.plugins.credentials.domains.Domain>\n"
                        + "            <java.util.concurrent.CopyOnWriteArrayList>\n"
                        + "                <com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "                    <scope>GLOBAL</scope>\n"
                        + "                    <id>f9ebaa5c-a7fc-46e4-93ab-453699781181</id>\n"
                        + "                    <description>Alice</description>\n"
                        + "                    <username/>\n"
                        + "                    <password>"
                        + SecretHandler.SECRET_MARKER + "</password>\n"
                        + "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "                <com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "                    <scope>GLOBAL</scope>\n"
                        + "                    <id>f9ebaa5c-a7fc-46e4-93ab-453699781182</id>\n"
                        + "                    <description>Bobby&#0x;</description>\n"
                        + "                    <username/>\n"
                        + "                    <password>"
                        + SecretHandler.SECRET_MARKER + "</password>\n"
                        + "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                        + "            </java.util.concurrent.CopyOnWriteArrayList>\n"
                        + "        </entry>\n"
                        + "    </domainCredentialsMap>\n"
                        + "</com.cloudbees.plugins.credentials.SystemCredentialsProvider>";
        assertEquals(expectedXml, patchedXml);
    }

    @Test
    @Issue("JENKINS-50765")
    void shouldNotResolveExternalEntities(JenkinsRule j) throws Exception {
        String xxeXml =
                """
                <?xml version="1.0" encoding="utf-8"?>
                <!DOCTYPE test [\s
                    <!ENTITY xxeattack SYSTEM "file:///">\s
                ]>
                <xxx>&xxeattack;</xxx>""";
        File file = File.createTempFile("test", ".xml");
        FileUtils.writeStringToFile(file, xxeXml, Charset.defaultCharset());
        String redactedXxeXml = SecretHandler.findSecrets(file);
        // Either the XML library understands the XXE disabling features, and removes XXEs completely,
        // or our custom EntityResolver is used which replaces them with a placeholder.
        assertThat(
                redactedXxeXml,
                anyOf(containsString("<xxx/>"), containsString("<xxx>" + SecretHandler.XXE_MARKER + "</xxx>")));
    }
}
