package com.cloudbees.jenkins.support.api;

import com.cloudbees.jenkins.support.model.MarkdownFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;

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
        try (PrintWriter pw = new PrintWriter(os)){
            YAMLFactory yf = new YAMLFactory();
            ObjectMapper om = new ObjectMapper(yf);
            pw.println(om.writerWithDefaultPrettyPrinter().writeValueAsString(yamlFile));
            pw.flush();
        }
    }
}
