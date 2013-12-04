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

import hudson.util.IOUtils;
import net.jcip.annotations.GuardedBy;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * A log handler that rotates files.
 *
 * @author Stephen Connolly
 */
public class SupportLogHandler extends Handler {

    private final Lock outputLock = new ReentrantLock();
    private final int fileSize;
    @GuardedBy("outputLock")
    private final LogRecord[] records;
    @GuardedBy("outputLock")
    private int position, count, fileCount;
    @GuardedBy("outputLock")
    private Writer writer;
    @GuardedBy("outputLock")
    private File logDirectry;
    private String logFilePrefix;
    private final SimpleDateFormat dateFormat;
    private final int maxFiles;

    public SupportLogHandler(int size, int fileSize, int maxFiles) {
        this.maxFiles = maxFiles;
        records = new LogRecord[size];
        position = 0;
        count = 0;
        fileCount = 0;
        this.fileSize = fileSize;
        setFormatter(new SupportLogFormatter());
        dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
    }

    public void setDirectory(File directory, String namePrefix) {
        outputLock.lock();
        try {
            logDirectry = directory;
            logFilePrefix = namePrefix;
            rollOver();
        } finally {
            outputLock.unlock();
        }
    }

    @Override
    public void publish(LogRecord record) {
        String formatted;
        try {
            formatted = isLoggable(record) ? getFormatter().format(record) : null;
        } catch (Exception e) {
            formatted = null;
        }
        outputLock.lock();
        try {
            int maxCount = records.length;
            records[(position + count) % maxCount] = record;
            if (count == maxCount) {
                position = (position + 1) % maxCount;
            } else {
                count++;
            }
            if (formatted != null) {
                if (writer != null) {
                    if (fileCount > fileSize) {
                        rollOver();
                    }
                    if (writer != null) {
                        try {
                            fileCount++;
                            writer.write(formatted);
                            flush();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        } finally {
            outputLock.unlock();
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(
            value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE"},
            justification = "Best effort"
    )
    private void rollOver() {
        outputLock.lock();
        try {
            setFile(null);
            if (logDirectry != null) {
                setFile(new File(logDirectry, logFilePrefix + "_" + dateFormat.format(new Date()) + ".log"));
                File[] files = logDirectry.listFiles(new FilenameFilter() {
                    public boolean accept(File dir, String name) {
                        return name.startsWith(logFilePrefix) && name.endsWith(".log");
                    }
                });
                if (files.length > maxFiles) {
                    Arrays.sort(files, new Comparator<File>() {
                        public int compare(File o1, File o2) {
                            long lm1 = o1.lastModified();
                            long lm2 = o2.lastModified();
                            return lm1 < lm2 ? -1 : lm1 == lm2 ? 0 : +1;
                        }
                    });
                    for (int i = 0; i < files.length - maxFiles; i++) {
                        files[i].delete();
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // ignore
        } finally {
            outputLock.unlock();
        }
    }

    @Override
    public void flush() {
        outputLock.lock();
        try {
            if (writer != null) {
                try {
                    writer.flush();
                } catch (IOException e) {
                    // ignore
                }
            }
        } finally {
            outputLock.unlock();
        }
    }

    @Override
    public void close() throws SecurityException {
        outputLock.lock();
        try {
            if (writer != null) {
                IOUtils.closeQuietly(writer);
                writer = null;
            }
        } finally {
            outputLock.unlock();
        }
    }

    public List<LogRecord> getRecent() {
        outputLock.lock();
        try {
            List<LogRecord> result = new ArrayList<LogRecord>(count);
            for (int i = 0; i < count; i++) {
                result.add(i, records[(position + i) % records.length]);
            }
            return result;
        } finally {
            outputLock.unlock();
        }
    }

    private void setWriter(Writer writer) {
        Writer oldWriter = null;
        boolean success = false;
        outputLock.lock();
        try {
            oldWriter = this.writer;
            if (oldWriter != null) {
                try {
                    this.writer.flush();
                } catch (IOException e) {
                    // ignore
                }
            }
            this.writer = writer;
        } finally {
            outputLock.unlock();
            IOUtils.closeQuietly(oldWriter);
        }
    }

    @edu.umd.cs.findbugs.annotations.SuppressWarnings(
            value = {"RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", "DM_DEFAULT_ENCODING"},
            justification = "Best effort"
    )
    private void setFile(File file) throws FileNotFoundException {
        outputLock.lock();
        try {
            if (file == null) {
                setWriter(null);
                return;
            }
            final File parentFile = file.getParentFile();
            if (parentFile != null) {
                parentFile.mkdirs();
            }
            boolean success = false;
            FileOutputStream fos = null;
            BufferedOutputStream bos = null;
            OutputStreamWriter writer = null;
            try {
                fos = new FileOutputStream(file);
                bos = new BufferedOutputStream(fos);
                try {
                    writer = new OutputStreamWriter(bos, "utf-8");
                } catch (UnsupportedEncodingException e) {
                    writer = new OutputStreamWriter(bos); // fall back to something sensible
                }
                setWriter(writer);
                fileCount = 0;
                success = true;
            } finally {
                if (!success) {
                    IOUtils.closeQuietly(writer);
                    IOUtils.closeQuietly(bos);
                    IOUtils.closeQuietly(fos);
                }
            }
        } finally {
            outputLock.unlock();
        }
    }
}
