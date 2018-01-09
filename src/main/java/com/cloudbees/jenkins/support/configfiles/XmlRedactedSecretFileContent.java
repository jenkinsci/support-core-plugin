package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.FileContent;
import org.xml.sax.SAXException;

import javax.xml.transform.TransformerException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

class XmlRedactedSecretFileContent extends FileContent {

    public XmlRedactedSecretFileContent(String name, File file) {
        this(name, file, false);
    }

    public XmlRedactedSecretFileContent(String name, File file, boolean shouldAnonymize) {
        super(name, file, shouldAnonymize);
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        try {
            return new ByteArrayInputStream(SecretHandler.findSecrets(file).getBytes(SecretHandler.OUTPUT_ENCODING));
        } catch (SAXException | TransformerException e) {
            throw new IOException(e);
        }
    }
}
