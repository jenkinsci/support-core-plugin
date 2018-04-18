package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.plugins.credentials.SecretBytes;
import hudson.util.Secret;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

public class SecretHandlerTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    private String xml;

    private final String expectedXml = "<com.cloudbees.plugins.credentials.SystemCredentialsProvider plugin=\"credentials@1.18\">\n" +
            "    <domainCredentialsMap class=\"hudson.util.CopyOnWriteMap$Hash\">\n" +
            "        <entry>\n" +
            "            <com.cloudbees.plugins.credentials.domains.Domain>\n" +
            "                <specifications/>\n" +
            "            </com.cloudbees.plugins.credentials.domains.Domain>\n" +
            "            <java.util.concurrent.CopyOnWriteArrayList>\n" +
            "                <com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
            "                    <scope>GLOBAL</scope>\n" +
            "                    <id>f9ebaa5c-a7fc-46e4-93ab-453699781181</id>\n" +
            "                    <description>Alice</description>\n" +
            "                    <username/>\n" +
            "                    <password>" + SecretHandler.SECRET_MARKER + "</password>\n" +
            "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
            "                <com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
            "                    <scope>GLOBAL</scope>\n" +
            "                    <id>f9ebaa5c-a7fc-46e4-93ab-453699781182</id>\n" +
            "                    <description>Bobby&#0x;</description>\n" +
            "                    <username/>\n" +
            "                    <password>" + SecretHandler.SECRET_MARKER + "</password>\n" +
            "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
            "            </java.util.concurrent.CopyOnWriteArrayList>\n" +
            "        </entry>\n" +
            "    </domainCredentialsMap>\n" +
            "</com.cloudbees.plugins.credentials.SystemCredentialsProvider>";

    @Before
    public void setup() {
        Secret secret = Secret.fromString("this-is-a-secret");
        SecretBytes secret2 = SecretBytes.fromBytes("this-is-another-type-of-secret".getBytes());
        assertEquals("this-is-a-secret", secret.getPlainText());
        assertEquals("this-is-another-type-of-secret", new String(secret2.getPlainData()));
        String encrypted_secret = secret.getEncryptedValue();
        String encrypted_secret2 = secret2.toString();
        xml = "<com.cloudbees.plugins.credentials.SystemCredentialsProvider plugin=\"credentials@1.18\">\n" +
                "    <domainCredentialsMap class=\"hudson.util.CopyOnWriteMap$Hash\">\n" +
                "        <entry>\n" +
                "            <com.cloudbees.plugins.credentials.domains.Domain>\n" +
                "                <specifications/>\n" +
                "            </com.cloudbees.plugins.credentials.domains.Domain>\n" +
                "            <java.util.concurrent.CopyOnWriteArrayList>\n" +
                "                <com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
                "                    <scope>GLOBAL</scope>\n" +
                "                    <id>f9ebaa5c-a7fc-46e4-93ab-453699781181</id>\n" +
                "                    <description>Alice</description>\n" +
                "                    <username/>\n" +
                "                    <password>" + encrypted_secret + "</password>\n" +
                "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
                "                <com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
                "                    <scope>GLOBAL</scope>\n" +
                "                    <id>f9ebaa5c-a7fc-46e4-93ab-453699781182</id>\n" +
                "                    <description>Bobby&#0x;</description>\n" +
                "                    <username/>\n" +
                "                    <password>" + encrypted_secret2 + "</password>\n" +
                "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
                "            </java.util.concurrent.CopyOnWriteArrayList>\n" +
                "        </entry>\n" +
                "    </domainCredentialsMap>\n" +
                "</com.cloudbees.plugins.credentials.SystemCredentialsProvider>";
    }

    @Issue("JENKINS-41653")
    @Test
    public void shouldPutAPlaceHolderInsteadOfSecret() throws Exception {
        File file = File.createTempFile("test", ".xml");
        FileUtils.writeStringToFile(file, xml);
        String patchedXml = SecretHandler.findSecrets(file);
        assertEquals(expectedXml, patchedXml);
    }

    @Test
    @Issue("JENKINS-50765")
    public void shouldNotResolveExternalEntities() throws Exception {
        final String xxeXml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<!DOCTYPE test [ \n" +
                "    <!ENTITY xxeattack SYSTEM \"file:///\"> \n" +
                "]>\n" +
                "<xxx>&xxeattack;</xxx>";
        File file = File.createTempFile("test", ".xml");
        FileUtils.writeStringToFile(file, xxeXml);
        String redactedXxeXml = SecretHandler.findSecrets(file);
        // Either the XML library understands the XXE disabling features, and removes XXEs completely,
        // or our custom EntityResolver is used which replaces them with a placeholder.
        assertThat(redactedXxeXml, anyOf(
                containsString("<xxx/>"),
                containsString("<xxx>" + SecretHandler.XXE_MARKER + "</xxx>")));
    }
}
