package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;

public interface MarkdownFile {
    public void toMarkdown(PrintWriter out);
}
