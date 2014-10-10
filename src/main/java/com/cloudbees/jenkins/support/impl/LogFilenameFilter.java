package com.cloudbees.jenkins.support.impl;

import java.io.File;
import java.io.FilenameFilter;

/**
 * Matches log files.
 */
class LogFilenameFilter implements FilenameFilter {
    public boolean accept(File dir, String name) {
        return name.endsWith(".log");
    }
}
