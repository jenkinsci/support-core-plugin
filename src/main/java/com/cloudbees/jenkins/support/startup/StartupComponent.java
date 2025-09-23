/*
 * The MIT License
 *
 * Copyright 2025 CloudBees, Inc
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
package com.cloudbees.jenkins.support.startup;

import com.cloudbees.jenkins.support.SupportAction;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrefilteredPrintedContent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.timer.UnfilteredFileListCapComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import hudson.init.InitMilestone;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Component that contributes the startup report into the support bundle.
 */
@Extension
public class StartupComponent extends UnfilteredFileListCapComponent {
    @NonNull
    @Override
    public String getDisplayName() {
        return "Startup Report";
    }

    @Override
    public void addContents(@NonNull Container container) {
        super.addContents(container, StartupReport.get().getLogs());
        container.add(new StartupContent(StartupReport.get().getTimesPerMilestone()));
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }

    private static class StartupContent extends PrefilteredPrintedContent {
        public static final String MILESTONE_HEADER = "Milestone";
        public static final String TIME_HEADER = "Time";
        private final Map<InitMilestone, Instant> timePerMilestone;

        StartupContent(Map<InitMilestone, Instant> timePerMilestone) {
            super("startup-timings.md");
            this.timePerMilestone = timePerMilestone;
        }

        @Override
        protected void printTo(PrintWriter out, @NonNull ContentFilter filter) {
            var startTime =
                    Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime());
            Instant lastMilestoneTimestamp = startTime;

            InitMilestone previousMilestone = null;
            List<TimingEntry> timingEntries = new ArrayList<>();

            var maxMilestoneLength = MILESTONE_HEADER.length();
            var maxTimingLength = TIME_HEADER.length();
            for (var milestone : InitMilestone.values()) {
                var currentMilestoneTimestamp = timePerMilestone.get(milestone);
                if (previousMilestone != null) {
                    var milestoneString = previousMilestone.toString();
                    String timeString;
                    if (currentMilestoneTimestamp != null) {
                        timeString = Util.getTimeSpanString(
                                Duration.between(lastMilestoneTimestamp, currentMilestoneTimestamp)
                                        .toMillis());
                        lastMilestoneTimestamp = currentMilestoneTimestamp;
                    } else {
                        timeString = "(No data)";
                    }
                    maxMilestoneLength = Math.max(maxMilestoneLength, milestoneString.length());
                    maxTimingLength = Math.max(maxTimingLength, timeString.length());
                    timingEntries.add(new TimingEntry(milestoneString, timeString));
                }
                previousMilestone = milestone;
            }
            var totalStartupTime = Duration.between(startTime, lastMilestoneTimestamp);
            out.println("| " + pad(MILESTONE_HEADER, maxMilestoneLength) + " | " + pad(TIME_HEADER, maxTimingLength)
                    + " |");
            out.println("|" + "-".repeat(maxMilestoneLength + 2) + "|" + "-".repeat(maxTimingLength + 2) + "|");
            for (var entry : timingEntries) {
                out.println("| " + pad(entry.milestone, maxMilestoneLength) + " | "
                        + pad(entry.timeString, maxTimingLength) + " |");
            }
            out.println();
            out.println("Total startup time : " + Util.getTimeSpanString(totalStartupTime.toMillis()));
        }

        private static String pad(@NonNull Object o, int size) {
            var string = o.toString();
            return string + " ".repeat(Math.max(0, size - string.length()));
        }

        private record TimingEntry(String milestone, String timeString) {}
    }

}
