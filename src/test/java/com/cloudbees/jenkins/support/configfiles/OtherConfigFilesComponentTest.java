package com.cloudbees.jenkins.support.configfiles;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyIterable;
import static org.hamcrest.Matchers.equalToCompressingWhiteSpace;
import static org.junit.jupiter.api.Assertions.*;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.filter.ContentMappings;
import com.cloudbees.plugins.credentials.SecretBytes;
import hudson.util.Secret;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LogRecorder;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class OtherConfigFilesComponentTest {

    private static final LogRecorder logging = new LogRecorder()
            .recordPackage(OtherConfigFilesComponent.class, Level.WARNING)
            .capture(100);

    private String xml;

    private final String expectedXml =
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
                    + "                    <description>Bobby</description>\n"
                    + "                    <username/>\n"
                    + "                    <password>"
                    + SecretHandler.SECRET_MARKER + "</password>\n"
                    + "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                    + "            </java.util.concurrent.CopyOnWriteArrayList>\n"
                    + "        </entry>\n"
                    + "    </domainCredentialsMap>\n"
                    + "</com.cloudbees.plugins.credentials.SystemCredentialsProvider>";

    @BeforeEach
    void setup() {
        Secret secret = Secret.fromString("this-is-a-secret");
        SecretBytes secret2 = SecretBytes.fromBytes("this-is-another-type-of-secret".getBytes());
        assertEquals("this-is-a-secret", secret.getPlainText());
        assertEquals("this-is-another-type-of-secret", new String(secret2.getPlainData()));
        String encrypted_secret = secret.getEncryptedValue();
        String encrypted_secret2 = secret2.toString();
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
                + encrypted_secret + "</password>\n"
                + "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                + "                <com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                + "                    <scope>GLOBAL</scope>\n"
                + "                    <id>f9ebaa5c-a7fc-46e4-93ab-453699781182</id>\n"
                + "                    <description>Bobby</description>\n"
                + "                    <username/>\n"
                + "                    <password>"
                + encrypted_secret2 + "</password>\n"
                + "                </com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl>\n"
                + "            </java.util.concurrent.CopyOnWriteArrayList>\n"
                + "        </entry>\n"
                + "    </domainCredentialsMap>\n"
                + "</com.cloudbees.plugins.credentials.SystemCredentialsProvider>";
    }

    @Test
    void shouldPutAPlaceHolderInsteadOfSecret(JenkinsRule j) throws Exception {
        File file = File.createTempFile("test", ".xml");
        FileUtils.writeStringToFile(file, xml, Charset.defaultCharset());
        String patchedXml = SecretHandler.findSecrets(file);
        assertThat(patchedXml, equalToCompressingWhiteSpace(expectedXml));
    }

    @Test
    void missingFile(JenkinsRule j) throws Exception {
        File file = new File(j.jenkins.root, "x.xml");
        FileUtils.writeStringToFile(file, xml, Charset.defaultCharset());
        Map<String, Content> contents = new HashMap<>();
        new OtherConfigFilesComponent().addContents(new Container() {
            @Override
            public void add(Content content) {
                contents.put(
                        MessageFormat.format(content.getName(), (Object[]) content.getFilterableParameters()), content);
            }
        });
        Files.delete(file.toPath());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Content content = contents.get("jenkins-root-configuration-files/x.xml");
        assertNotNull(content);
        content.writeTo(baos);
        assertThat(
                "routine build should not issue warnings",
                logging.getRecords().stream()
                        .filter(lr -> lr.getLevel().intValue() >= Level.WARNING.intValue())
                        . // TODO .record(…, WARNING) does not accomplish this
                        map(lr -> lr.getSourceClassName() + "." + lr.getSourceMethodName() + ": " + lr.getMessage())
                        .collect(Collectors.toList()), // LogRecord does not override toString
                emptyIterable());
        assertThat(
                baos.toString(),
                allOf(
                        anyOf(containsString("FileNotFoundException"), containsString("NoSuchFileException")),
                        containsString(file.getAbsolutePath())));
    }

    @Test
    void regexpFromFileFilter(JenkinsRule j) throws Exception {
        List<String> filesToExclude = List.of(
                "test-abc.xml",
                "test-efgh.xml",
                "toexclude.xml",
                "credentials.xml",
                ContentMappings.class.getName() + ".xml");
        List<String> filesNotToExclude = List.of("test-bcd.xml");

        for (String fileToExclude : filesToExclude) {
            FileUtils.writeStringToFile(new File(j.jenkins.root, fileToExclude), xml, Charset.defaultCharset());
        }
        for (String fileNotToExclude : filesNotToExclude) {
            FileUtils.writeStringToFile(new File(j.jenkins.root, fileNotToExclude), xml, Charset.defaultCharset());
        }

        Map<String, Content> contents = new HashMap<>();
        new OtherConfigFilesComponent().addContents(new Container() {
            @Override
            public void add(Content content) {
                contents.put(
                        MessageFormat.format(content.getName(), (Object[]) content.getFilterableParameters()), content);
            }
        });
        for (String fileToExclude : filesToExclude) {
            Files.delete(Path.of(j.jenkins.root.getPath(), fileToExclude));
        }
        for (String fileNotToExclude : filesNotToExclude) {
            Files.delete(Path.of(j.jenkins.root.getPath(), fileNotToExclude));
        }
        filesToExclude.forEach(
                s -> assertNull(contents.get("jenkins-root-configuration-files/" + s), s + " should not be included"));
        filesNotToExclude.forEach(
                s -> assertNotNull(contents.get("jenkins-root-configuration-files/" + s), s + " should be included"));
    }

    @TestExtension
    public static class TestConfigFilesFilter implements OtherConfigFilesComponent.ConfigFilesFilter {

        @Override
        public boolean include(@NotNull File file) {
            return !List.of("test-abc.xml", "test-efgh.xml", "toexclude.xml").contains(file.getName());
        }
    }
}
