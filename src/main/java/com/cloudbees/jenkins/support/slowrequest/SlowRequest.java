package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.timer.FileListCap;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

import java.io.IOException;
import java.util.List;

public abstract class SlowRequest implements ExtensionPoint {
    abstract void doRun(InflightRequest req, long totalTime, FileListCap logs) throws IOException;

    public static List<SlowRequest> all() {
        return ExtensionList.lookup(SlowRequest.class);
    }
}
