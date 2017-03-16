package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.timer.FileListCap;
import com.cloudbees.jenkins.support.timer.FileListCapComponent;
import hudson.Extension;
import hudson.util.IOUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Created by stevenchristou on 3/14/17.
 */
@Extension
public class ThreadDumpSlowRequest extends SlowRequest {

    private final SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss.SSS");
    {
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void doRun(InflightRequest req, long totalTime, FileListCap logs) throws IOException {
        long iota = System.currentTimeMillis();

        PrintWriter w = null;
        try {
            if (req.record==null) {
                req.record = logs.file(format.format(new Date(iota++)) + ".txt");
                logs.add(req.record);

                w = new PrintWriter(req.record,"UTF-8");
                req.writeHeader(w);
            } else {
                w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(req.record,true),"UTF-8"));
                logs.touch(req.record);
            }

            if (req.record.length() >= FileListCapComponent.MAX_FILE_SIZE)
                return;
            ThreadInfo lockedThread = ManagementFactory.getThreadMXBean().getThreadInfo(req.thread.getId(), Integer.MAX_VALUE);
            if (lockedThread != null ) {
                w.println(lockedThread);
                w.println(totalTime + "msec elapsed in " + lockedThread.getThreadName());
                printThreadStackElements(lockedThread, w);

                long lockOwnerId = lockedThread.getLockOwnerId();
                if (lockOwnerId != -1) // If the thread is not locked, then getLockOwnerId returns -1.
                {
                    ThreadInfo threadInfo = ManagementFactory.getThreadMXBean().getThreadInfo(lockOwnerId, Integer.MAX_VALUE);
                    w.println(threadInfo);
                    if (threadInfo != null) {
                        printThreadStackElements(threadInfo, w);
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(w);
        }
    }

    private void printThreadStackElements(ThreadInfo threadinfo, PrintWriter writer) {
        for (StackTraceElement element : threadinfo.getStackTrace()) {
            writer.println("    " + element);
        }
    }
}
