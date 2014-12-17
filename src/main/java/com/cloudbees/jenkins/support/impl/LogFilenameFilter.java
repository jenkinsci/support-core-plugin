package com.cloudbees.jenkins.support.impl;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;

/**
 * Matches log files.
 */
class LogFilenameFilter implements FilenameFilter, Serializable {
    public boolean accept(File dir, String name) {
        return name.endsWith(".log");
    }
    private static final long serialVersionUID = 1L;
}
