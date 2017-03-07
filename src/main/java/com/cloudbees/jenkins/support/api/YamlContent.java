package com.cloudbees.jenkins.support.api;

import com.cloudbees.jenkins.support.model.MarkdownFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.UTF8Writer;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Created by schristou88 on 2/9/17.
 */
public class YamlContent extends Content {

    MarkdownFile yamlFile;

    public YamlContent(String name, MarkdownFile file) {
        super(name);
        this.yamlFile = file;
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))){
            YAMLFactory yf = new YAMLFactory();
            ObjectMapper om = new ObjectMapper(yf);
            pw.println(om.writerWithDefaultPrettyPrinter().writeValueAsString(yamlFile));
            pw.flush();
        }
    }
}
