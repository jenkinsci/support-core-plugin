package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.plugins.credentials.SecretBytes;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class OtherConfigFilesComponentTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule().recordPackage(OtherConfigFilesComponent.class, Level.WARNING).capture(100);

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
            "                    <description>Bobby</description>\n" +
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
                "                    <description>Bobby</description>\n" +
                "                    <username/>\n" +
                "                    <password>" + encrypted_secret2 + "</password>\n" +
                "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n" +
                "            </java.util.concurrent.CopyOnWriteArrayList>\n" +
                "        </entry>\n" +
                "    </domainCredentialsMap>\n" +
                "</com.cloudbees.plugins.credentials.SystemCredentialsProvider>";
    }

    @Test
    public void shouldPutAPlaceHolderInsteadOfSecret() throws Exception {
        File file = File.createTempFile("test", ".xml");
        FileUtils.writeStringToFile(file, xml);
        String patchedXml = SecretHandler.findSecrets(file);
        assertThat(patchedXml, equalToCompressingWhiteSpace(expectedXml));
    }

    @Test
    public void missingFile() throws Exception {
        File file = new File(j.jenkins.root, "x.xml");
        FileUtils.writeStringToFile(file, xml);
        Map<String, Content> contents = new HashMap<>();
        new OtherConfigFilesComponent().addContents(new Container() {
            @Override
            public void add(Content content) {
                contents.put(MessageFormat.format(content.getName(), (Object[]) content.getFilterableParameters()), content);
            }
        });
        Files.delete(file.toPath());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Content content = contents.get("jenkins-root-configuration-files/x.xml");
        assertNotNull(content);
        content.writeTo(baos);
        assertThat("routine build should not issue warnings",
            logging.getRecords().stream().
                filter(lr -> lr.getLevel().intValue() >= Level.WARNING.intValue()). // TODO .record(…, WARNING) does not accomplish this
                map(lr -> lr.getSourceClassName() + "." + lr.getSourceMethodName() + ": " + lr.getMessage()).collect(Collectors.toList()), // LogRecord does not override toString
            emptyIterable());
        assertThat(baos.toString(), allOf(containsString("FileNotFoundException"), containsString(file.getAbsolutePath())));
    }

}
