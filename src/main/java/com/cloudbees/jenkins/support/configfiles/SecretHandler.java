package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.plugins.credentials.SecretBytes;
import hudson.util.Secret;
import org.apache.commons.io.FileUtils;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;

import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.XMLReaderFactory;

/**
 * Secret Handler for xml files to add to the support bundle.
 * We want to use a placeholder instead of Secrets.
 */
public class SecretHandler {

    /**
     * our placeholder
     */
    protected static final String SECRET_MARKER = "#secret#";

    /**
     * find the secret in the xml file and replace it with the place holder
     * @param xmlFile we want to parse
     * @return the patched xml files without secrets
     * @throws SAXException if some XML parsing issue occurs.
     * @throws IOException if some issue occurs while reading the providing file.
     * @throws TransformerException if an issue occurs while writing the result.
     */
    public static File findSecrets(File xmlFile) throws SAXException, IOException, TransformerException {

        XMLReader xr = new XMLFilterImpl(XMLReaderFactory.createXMLReader()) {
            private String tagName = "";

            @Override
            public void startElement(String uri, String localName, String qName, Attributes atts)
                    throws SAXException {
                tagName = qName;
                super.startElement(uri, localName, qName, atts);
            }

            public void endElement(String uri, String localName, String qName) throws SAXException {
                tagName = "";
                super.endElement(uri, localName, qName);
            }

            @Override
            public void characters(char[] ch, int start, int length) throws SAXException {
                if (!"".equals(tagName)) {
                    String value = new String(ch, start, length).trim();
                    //if it's a secret, then use a place holder
                    if (!"".equals(value)) {
                        if ((Secret.decrypt(value)) != null || SecretBytes.isSecretBytes(value)) {
                            ch = SECRET_MARKER.toCharArray();
                            start = 0;
                            length = ch.length;
                        }
                    }
                }
                super.characters(ch, start, length);
            }
        };
        Source src = new SAXSource(xr, new InputSource(new StringReader(FileUtils.readFileToString(xmlFile))));
        File patchedFile = File.createTempFile("patched", ".xml", SupportPlugin.getRootDirectory());
        Result res = new StreamResult(new FileOutputStream(patchedFile));
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        //omit xml declaration because of https://bugs.openjdk.java.net/browse/JDK-8035437
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.transform(src, res);

        return patchedFile;
    }
}