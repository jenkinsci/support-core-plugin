package com.cloudbees.jenkins.support.impl;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;

/**
 * Matches log files from winsw.
 *
 * @see <a href="https://github.com/winsw/winsw/blob/e4cf507bae5981363a9cdc0f7301c1aa892af401/src/WinSW.Core/LogAppenders.cs#L169-L170">LogAppenders.cs</a>
 * @see SlaveLogs
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
