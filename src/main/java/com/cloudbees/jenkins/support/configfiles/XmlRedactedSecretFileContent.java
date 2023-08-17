package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.filter.PasswordRedactor;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.transform.TransformerException;
import org.xml.sax.SAXException;

class XmlRedactedSecretFileContent extends FileContent {

    private static final String STRING_TAG = "<string>";
    private static final String CLOSE_STRING_TAG = "</string>";

    private String previousStringTagValue;

    public XmlRedactedSecretFileContent(String name, File file) {
        super(name, file);
    }

    public XmlRedactedSecretFileContent(String name, String[] filterableParameters, File file) {
        super(name, filterableParameters, file);
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        try {
            return new ByteArrayInputStream(SecretHandler.findSecrets(file).getBytes(SecretHandler.OUTPUT_ENCODING));
        } catch (SAXException | TransformerException e) {
            throw new IOException(e);
        }
    }

    @Override
    protected String getSimpleValueOrRedactedPassword(String value) {
        if (value.contains(STRING_TAG)) {
            return redactStringTagIfNeeded(value);
        }
        return PasswordRedactor.get().redact(value);
    }

    private String redactStringTagIfNeeded(String value) {
        if (previousStringTagValue != null) {
            if (previousStringTagValue.contains(STRING_TAG)
                    && PasswordRedactor.get().match(previousStringTagValue)) {
                previousStringTagValue = null;
                return value.substring(0, value.indexOf(STRING_TAG))
                        + STRING_TAG
                        + PasswordRedactor.REDACTED
                        + CLOSE_STRING_TAG;
            }
            previousStringTagValue = null;
            return value;
        }
        previousStringTagValue = value;
        return value;
    }
}
