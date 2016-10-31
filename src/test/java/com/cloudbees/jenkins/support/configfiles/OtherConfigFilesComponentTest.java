package com.cloudbees.jenkins.support.configfiles;

import hudson.util.Secret;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.io.File;

import static org.junit.Assert.assertEquals;

/**
 * Created by valentina on 28/10/16.
 */
public class OtherConfigFilesComponentTest {

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
            "                    <description>vale</description>\n" +
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
        assertEquals("this-is-a-secret", secret.getPlainText());
        String encrypted_secret = secret.getEncryptedValue();
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
                "                    <description>vale</description>\n" +
                "                    <username/>\n" +
                "                    <password>" + encrypted_secret + "</password>\n" +
                "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
                "            </java.util.concurrent.CopyOnWriteArrayList>\n" +
                "        </entry>\n" +
                "    </domainCredentialsMap>\n" +
                "</com.cloudbees.plugins.credentials.SystemCredentialsProvider>";
    }

    @Test
    public void shouldPutAPlaceHolderInsteadOfSecret() throws Exception {
        //File file = new File(this.getClass().getResource("credentials.xml").getFile());
        File file = File.createTempFile("test", ".xml");
        FileUtils.writeStringToFile(file, xml);
        File patchedFile = SecretHandler.findSecrets(file);
        String patchedXml = FileUtils.readFileToString(patchedFile);
        assertEquals(expectedXml, patchedXml);
    }
}


