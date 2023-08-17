package com.cloudbees.jenkins.support.impl;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;

/**
 * Matches log files from winsw.
 *
 * @see <a href="https://github.com/kohsuke/winsw/blob/master/LogAppenders.cs">LogAppenders.cs</a>
 * @author Kohsuke Kawaguchi
 */
class WinswLogfileFilter implements FilenameFilter, Serializable {
    public boolean accept(File dir, String name) {
        // RollingLogAppender uses *.out.log.old, so instead of the endsWith() method,
        // we use the contains() method.
        return name.contains(".out.log") || name.contains(".err.log");
    }

    private static final long serialVersionUID = 1L;
}
