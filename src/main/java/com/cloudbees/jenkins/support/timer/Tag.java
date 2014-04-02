package com.cloudbees.jenkins.support.timer;

import javax.servlet.http.HttpServletRequest;

/**
* @author Kohsuke Kawaguchi
*/
class Tag {
    final long startTime;
    final String url;

    Tag(HttpServletRequest req) {
        url = req.getRequestURI();
        startTime = System.currentTimeMillis();
    }
}
