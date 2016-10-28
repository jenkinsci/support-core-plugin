/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
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

package com.cloudbees.jenkins.support;

/***********************************************
 * DO NOT INCLUDE ANY NON JDK CLASSES IN HERE.
 * IT CAN DEADLOCK REMOTING - SEE JENKINS-32622
 ***********************************************/
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;
/***********************************************
 * DO NOT INCLUDE ANY NON JDK CLASSES IN HERE.
 * IT CAN DEADLOCK REMOTING - SEE JENKINS-32622
 ***********************************************/

/**
 * Format log files in a nicer format that is easier to read and search.
 *
 * @author Stephen Connolly
 */
public class SupportLogFormatter extends Formatter {
    
    private final static ThreadLocal<SimpleDateFormat> threadLocalDateFormat = new ThreadLocal<SimpleDateFormat>() {
        @Override
        protected SimpleDateFormat initialValue() {
            return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
        }
    };

    private final Object[] args = new Object[6];

    @Override
    @SuppressFBWarnings(
            value = {"DE_MIGHT_IGNORE"},
            justification = "The exception wasn't thrown on our stack frame"
    )
    public String format(LogRecord record) {
        StringBuilder builder = new StringBuilder();
        builder.append(threadLocalDateFormat.get().format(new Date(record.getMillis())));
        builder.append(" [id=").append(record.getThreadID()).append("]");

        builder.append("\t").append(record.getLevel().getName()).append("\t");

        if (record.getSourceMethodName() != null) {
            String sourceClass;
            if (record.getSourceClassName() == null) {
                sourceClass = record.getLoggerName();
            } else {
                sourceClass = record.getSourceClassName();
            }

            builder.append(abbreviateClassName(sourceClass, 32)).append("#").append(record.getSourceMethodName());
        } else {
            String sourceClass;
            if (record.getSourceClassName() == null) {
                sourceClass = record.getLoggerName();
            } else {
                sourceClass = record.getSourceClassName();
            }
            builder.append(abbreviateClassName(sourceClass, 40));
        }

        String message = formatMessage(record);
        if (message != null) {
            builder.append(": ").append(message);
        }

        builder.append("\n");

        if (record.getThrown() != null) {
            try {
                StringWriter writer = new StringWriter();
                PrintWriter out = new PrintWriter(writer);
                record.getThrown().printStackTrace(out);
                out.close();
                builder.append(writer.toString());
            } catch (Exception e) {
                // ignore
            }
        }

        return builder.toString();
    }

    public String abbreviateClassName(String fqcn, int targetLength) {
        if (fqcn == null) {
            return "-";
        }
        int fqcnLength = fqcn.length();
        if (fqcnLength < targetLength) {
            return fqcn;
        }
        int[] indexes = new int[16];
        int[] lengths = new int[17];
        int count = 0;
        for (int i = fqcn.indexOf('.'); i != -1 && count < indexes.length; i = fqcn.indexOf('.', i + 1)) {
            indexes[count++] = i;
        }
        if (count == 0) {
            return fqcn;
        }
        StringBuilder buf = new StringBuilder(targetLength);
        int requiredSavings = fqcnLength - targetLength;
        for (int i = 0; i < count; i++) {
            int previous = i > 0 ? indexes[i - 1] : -1;
            int available = indexes[i] - previous - 1;
            int length = requiredSavings > 0 ? (available < 1) ? available : 1 : available;
            requiredSavings -= (available - length);
            lengths[i] = length + 1;
        }
        lengths[count] = fqcnLength - indexes[count - 1];
        for (int i = 0; i <= count; i++) {
            if (i == 0) {
                buf.append(fqcn.substring(0, lengths[i] - 1));
            } else {
                buf.append(fqcn.substring(indexes[i - 1], indexes[i - 1] + lengths[i]));
            }
        }
        return buf.toString();
    }
}
