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

import javax.annotation.concurrent.GuardedBy;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

import static java.util.stream.Collectors.toList;

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
    private Path logDirectory;
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
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    public void setDirectory(File directory, String namePrefix) {
        outputLock.lock();
        try {
            logDirectory = directory.toPath();
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

    private void rollOver() {
        outputLock.lock();
        try {
            setPath(null);
            if (logDirectory != null) {
                setPath(logDirectory.resolve(logFilePrefix + '_' + dateFormat.format(new Date()) + ".log"));
                List<Path> logFiles = Files.list(logDirectory)
                        .filter(path -> {
                            Path name = path.getFileName();
                            return name.startsWith(logFilePrefix) && name.endsWith(".log");
                        })
                        .sorted(Comparator.comparingLong(SupportLogHandler::getLastModifiedTime))
                        .collect(toList());
                if (logFiles.size() > maxFiles) {
                    logFiles.stream()
                            .limit(logFiles.size() - maxFiles)
                            .forEach(SupportLogHandler::delete);
                }
            }
        } catch (IOException ignored) {
            // ignore
        } finally {
            outputLock.unlock();
        }
    }

    private static long getLastModifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException ignored) {
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
        outputLock.lock();
        try {
            if (this.writer != null) {
                try (Writer previous = this.writer) {
                    previous.flush();
                } catch (IOException ignored) {
                }
            }
            this.writer = writer;
        } finally {
            outputLock.unlock();
        }
    }

    private void setPath(Path path) throws IOException {
        outputLock.lock();
        try {
            if (path == null) {
                setWriter(null);
                return;
            }
            Path parentDirectory = path.getParent();
            if (Files.notExists(parentDirectory)) {
                Files.createDirectories(parentDirectory);
            }
            BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            setWriter(writer);
            fileCount = 0;
        } finally {
            outputLock.unlock();
        }
    }

}
