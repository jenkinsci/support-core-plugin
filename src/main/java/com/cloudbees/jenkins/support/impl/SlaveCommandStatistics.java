/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Computer;
import hudson.model.TaskListener;
import hudson.remoting.Channel;
import hudson.remoting.Command;
import hudson.remoting.Request;
import hudson.remoting.Response;
import hudson.security.Permission;
import hudson.slaves.ComputerListener;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

@Extension
public final class SlaveCommandStatistics extends Component {

    private final Map<String, Statistics> statistics = Collections.synchronizedSortedMap(new TreeMap<>());

    @Override
    public String getDisplayName() {
        return "Agent Command Statistics";
    }

    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @Override
    public void addContents(Container container) {
        statistics.forEach((name, stats) -> container.add(new PrintedContent("nodes/slave/{0}/command-stats.md", name) {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                stats.print(out);
            }

            @Override
            public boolean shouldBeFiltered() {
                // The information of this content is not sensible, so it doesn't need to be filtered.
                return false;
            }
        }));
    }

    private static final class Statistics extends Channel.Listener {

        /** Represents a tally of both the number of times some event occurred, and some integral metric associated with each event which should be summed. */
        private static final class CountSum {
            long count;
            long sum;
            void tally(long value) {
                count++;
                sum += value;
            }
            long count() {
                return count;
            }
            long sum() {
                return sum;
            }
        }
        private final Map<String, CountSum> writes = new HashMap<>();
        private final Map<String, CountSum> reads = new HashMap<>();
        private final Map<String, CountSum> responses = new HashMap<>();

        private final Set<File> jars = new LinkedHashSet<>();

        @Override
        public void onWrite(Channel channel, Command cmd, long blockSize) {
            String type = classify(cmd);
            // Synchronization probably unnecessary for tallying (since each channel processes commands sequentially), but printing could happen at any time anyway.
            synchronized (writes) {
                writes.computeIfAbsent(type, k -> new CountSum()).tally(blockSize);
            }
        }

        @Override
        public void onRead(Channel channel, Command cmd, long blockSize) {
            String type = classify(cmd);
            synchronized (reads) {
                reads.computeIfAbsent(type, k -> new CountSum()).tally(blockSize);
            }
        }

        @Override
        public void onResponse(Channel channel, Request<?, ?> req, Response<?, ?> rsp, long totalTime) {
            String type = classify(req);
            synchronized (responses) {
                responses.computeIfAbsent(type, k -> new CountSum()).tally(totalTime);
            }
        }

        @Override
        public void onJar(Channel channel, File jar) {
            synchronized (jars) {
                jars.add(jar);
            }
        }

        private static final Pattern IRRELEVANT = Pattern.compile("(@[a-f0-9]+|[(][^)]+[)])+$");
        private static String classify(Command cmd) {
            return IRRELEVANT.matcher(cmd.toString()).replaceFirst("");
        }

        @SuppressFBWarnings(value="UC_USELESS_OBJECT_STACK", justification="Maybe FindBugs is just confused? The TreeMap _is_ being used.")
        private void print(PrintWriter out) {
            out.println("# Totals");
            out.printf("* Writes: %d%n  * sent %.1fMb%n", writes.values().stream().mapToLong(CountSum::count).sum(), writes.values().stream().mapToLong(CountSum::sum).sum() / 1_000_000.0);
            out.printf("* Reads: %d%n  * received %.1fMb%n", reads.values().stream().mapToLong(CountSum::count).sum(), reads.values().stream().mapToLong(CountSum::sum).sum() / 1_000_000.0);
            out.printf("* Responses: %d%n  * waited %s%n", responses.values().stream().mapToLong(CountSum::count).sum(), Util.getTimeSpanString(responses.values().stream().mapToLong(CountSum::sum).sum() / 1_000_000));
            out.println();
            out.println("# Commands sent");
            // TODO perhaps sort by count descending?
            new TreeMap<>(writes).forEach((type, cs) -> out.printf("* `%s`: %d%n  * sent %.1fMb%n", type, cs.count, cs.sum / 1_000_000.0));
            out.println();
            out.println("# Commands received");
            new TreeMap<>(reads).forEach((type, cs) -> out.printf("* `%s`: %d%n  * received %.1fMb%n", type, cs.count, cs.sum / 1_000_000.0));
            out.println();
            out.println("# Responses received");
            new TreeMap<>(responses).forEach((type, cs) -> out.printf("* `%s`: %d%n  * waited %s%n", type, cs.count, Util.getTimeSpanString(cs.sum / 1_000_000)));
            out.println();
            out.println("# JARs sent");
            jars.forEach(jar -> out.printf("* `%s`: %db%n", jar.getName(), jar.length()));
        }

    }

    @Extension
    public static final class ComputerListenerImpl extends ComputerListener {

        @Override
        public void preOnline(Computer c, Channel channel, FilePath root, TaskListener listener) throws IOException, InterruptedException {
            channel.addListener(ExtensionList.lookupSingleton(SlaveCommandStatistics.class).statistics.computeIfAbsent(c.getName(), k -> new Statistics()));
        }

    }

}
