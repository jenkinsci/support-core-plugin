package com.cloudbees.jenkins.support.threaddump;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.timer.UnfilteredFileListCapComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

/**
 * Component which automatically generates thread dumps on high CPU
 *
 */

@Extension
public class ThreadDumpHighCPU extends UnfilteredFileListCapComponent {

    @NonNull
    @Override
    public String getDisplayName() {
        return Messages.ThreadDumpHighCPU_DisplayName();
    }

    @Override
    public void addContents(@NonNull Container container) {

    }
}
