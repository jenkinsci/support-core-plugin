package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;

/**
 * Created by schristou88 on 2/9/17.
 */
public interface MarkdownFile {
    public void toMarkdown(PrintWriter out);
}
