package com.cloudbees.jenkins.support.timer;

import javax.servlet.http.HttpServletRequest;
import java.lang.management.ThreadInfo;

/**
* @author Kohsuke Kawaguchi
*/
class Tag {
    final Thread thread = Thread.currentThread();
    final long startTime;
    final String url;
    volatile boolean ended;

    Tag(HttpServletRequest req) {
        url = req.getRequestURI();
        startTime = System.currentTimeMillis();
    }

    public boolean is(ThreadInfo t) {
        return thread.getId()==t.getThreadId();
    }
}
