/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc
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

package com.cloudbees.jenkins.support.threaddump;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.timer.UnfilteredFileListCapComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;

/**
 * HighLoadComponent component
 */
@Extension
public class HighLoadComponent extends UnfilteredFileListCapComponent {

    public String getDisplayName() {
        return Messages.ThreadDumpHighCPU_DisplayName();
    }

    @Override
    public void addContents(@NonNull Container container) {
        HighLoadCpuChecker checker =
                ExtensionList.lookup(HighLoadCpuChecker.class).get(HighLoadCpuChecker.class);
        if (checker != null && checker.logs.getSize() > 0) {
            super.addContents(container, checker.logs);
        }
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.PLATFORM;
    }
}
