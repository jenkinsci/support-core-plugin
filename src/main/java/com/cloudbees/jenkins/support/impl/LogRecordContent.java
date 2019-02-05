package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportLogFormatter;
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.PrefilteredContent;
import com.google.common.collect.Lists;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

/**
 * {@link Content} that formats {@link LogRecord}s.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class LogRecordContent extends PrefilteredContent {
    public LogRecordContent(String name) {
        super(name);
    }

    public LogRecordContent(String name, String... filterableParameters) {
        super(name, filterableParameters);
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
    public final void writeTo(OutputStream os) throws IOException {
        writeTo(os, null);
    }

    @Override
    public final void writeTo(OutputStream os, ContentFilter filter) throws IOException {
        final PrintWriter writer = getWriter(os);
        try {
            printTo(writer, filter);
        } finally {
            writer.flush();
        }
    }

    protected void printTo(PrintWriter out) throws IOException {
        printTo(out, null);
    }

    protected void printTo(PrintWriter out, ContentFilter filter) throws IOException {
        for (LogRecord logRecord : getLogRecords()) {
            String filtered = LOG_FORMATTER.format(logRecord);
            if (filter != null) {
                filtered = filter.filter(filtered);
            }
            out.print(filtered);
        }

        out.flush();
    }

    private PrintWriter getWriter(OutputStream os) {
        return new PrintWriter(new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8)));
    }

    private static final Formatter LOG_FORMATTER = new SupportLogFormatter();
}
