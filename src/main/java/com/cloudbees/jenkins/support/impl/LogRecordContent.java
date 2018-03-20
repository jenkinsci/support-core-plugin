package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportLogFormatter;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.ContentData;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.google.common.collect.Lists;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * {@link Content} that formats {@link LogRecord}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class LogRecordContent extends PrintedContent {
    public LogRecordContent(String name) {
        this(new ContentData(name, false));
    }

    public LogRecordContent(ContentData contentData) {
        super(contentData);
    }

    /**
     * Iterates {@link LogRecord}s to be printed as this content.
     *
     * @return the {@link LogRecord}s to be printed as this content.
     * @see Lists#reverse(List)
     * @throws IOException if an error occurs while performing the operation.
     */
    public abstract Iterable<LogRecord> getLogRecords() throws IOException;

    @Override
    protected void printTo(PrintWriter out) throws IOException {
        for (LogRecord logRecord : getLogRecords()) {
            out.print(LOG_FORMATTER.format(logRecord));
        }

        out.flush();
    }

    private static final Formatter LOG_FORMATTER = new SupportLogFormatter();
}
