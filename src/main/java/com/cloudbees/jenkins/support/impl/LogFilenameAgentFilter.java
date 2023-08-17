/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support.impl;

import java.io.File;
import java.io.FilenameFilter;
import java.io.Serializable;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Matches agent log files in an interval of time
 */
class LogFilenameAgentFilter implements FilenameFilter, Serializable {

    public static final long MAX_TIME_AGENT_LOG_RETRIEVAL = Long.getLong(
            System.getProperty(LogFilenameAgentFilter.class.getName() + ".maxTimeAgentLogRetrieval"),
            TimeUnit.DAYS.toMillis(7));

    public boolean accept(File dir, String name) {
        // We should avoid taking agent files which are very old
        // as they are not usually very helpful to troubleshoot
        // 1 week should be enough in most of the cases
        if (name.endsWith(".log") && new Date().getTime() - dir.lastModified() < MAX_TIME_AGENT_LOG_RETRIEVAL) {
            return true;
        }
        return false;
    }

    private static final long serialVersionUID = 1L;
}
