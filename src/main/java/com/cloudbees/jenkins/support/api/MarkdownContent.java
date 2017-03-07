package com.cloudbees.jenkins.support.api;

import com.cloudbees.jenkins.support.model.MarkdownFile;
import org.jfree.io.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * Created by schristou88 on 2/9/17.
 */
public class MarkdownContent extends Content {

    MarkdownFile mdFile;

    public MarkdownContent(String location, MarkdownFile mdFile) {
        super(location);
        this.mdFile = mdFile;
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))){
            mdFile.toMarkdown(pw);
            pw.flush();
        }
    }
}
