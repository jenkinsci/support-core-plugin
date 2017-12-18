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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import jenkins.model.Jenkins;

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
        statistics.forEach((name, stats) -> container.add(new PrintedContent("nodes/slave/" + name + "/command-stats.md") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                stats.print(out);
            }
        }));
    }

    private static final class Statistics extends Channel.Listener {

        private final Map<String, LongAdder> writeInvocations = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> writeBytes = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> readInvocations = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> readBytes = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> responseInvocations = new ConcurrentHashMap<>();
        private final Map<String, LongAdder> responseNanoseconds = new ConcurrentHashMap<>();

        @Override
        public void onWrite(Channel channel, Command cmd, long blockSize) {
            String type = classify(cmd);
            writeInvocations.computeIfAbsent(type, k -> new LongAdder()).increment();
            writeBytes.computeIfAbsent(type, k -> new LongAdder()).add(blockSize);
        }

        @Override
        public void onRead(Channel channel, Command cmd, long blockSize) {
            String type = classify(cmd);
            readInvocations.computeIfAbsent(type, k -> new LongAdder()).increment();
            readBytes.computeIfAbsent(type, k -> new LongAdder()).add(blockSize);
        }

        @Override
        public void onResponse(Channel channel, Request<?, ?> req, Response<?, ?> rsp, long totalTime) {
            String type = classify(req);
            responseInvocations.computeIfAbsent(type, k -> new LongAdder()).increment();
            responseNanoseconds.computeIfAbsent(type, k -> new LongAdder()).add(totalTime);
        }

        private static final Pattern IRRELEVANT = Pattern.compile("(@[a-f0-9]+|[(][^)]+[)])+$");
        private static String classify(Command cmd) {
            return IRRELEVANT.matcher(cmd.toString()).replaceFirst("");
        }

        @SuppressFBWarnings(value="UC_USELESS_OBJECT_STACK", justification="Maybe FindBugs is just confused? The TreeMap _is_ being used.")
        private void print(PrintWriter out) {
            out.println("# Totals");
            out.printf("* Writes: %d%n** sent %.1fMb%n", writeInvocations.values().stream().mapToLong(LongAdder::sum).sum(), writeBytes.values().stream().mapToLong(LongAdder::sum).sum() / 1_000_000.0);
            out.printf("* Reads: %d%n** received %.1fMb%n", readInvocations.values().stream().mapToLong(LongAdder::sum).sum(), readBytes.values().stream().mapToLong(LongAdder::sum).sum() / 1_000_000.0);
            out.printf("* Responses: %d%n** waited %s%n", responseInvocations.values().stream().mapToLong(LongAdder::sum).sum(), Util.getTimeSpanString(responseNanoseconds.values().stream().mapToLong(LongAdder::sum).sum() / 1_000_000));
            out.println();
            out.println("# Commands sent");
            // TODO perhaps sort by invocations descending?
            new TreeMap<>(writeInvocations).forEach((type, tot) -> out.printf("* `%s`: %d%n** sent %.1fMb%n", type, tot.sum(), writeBytes.get(type).sum() / 1_000_000.0));
            out.println();
            out.println("# Commands received");
            new TreeMap<>(readInvocations).forEach((type, tot) -> out.printf("* `%s`: %d%n** received %.1fMb%n", type, tot.sum(), readBytes.get(type).sum() / 1_000_000.0));
            out.println();
            out.println("# Responses received");
            new TreeMap<>(responseInvocations).forEach((type, tot) -> out.printf("* `%s`: %d%n** waited %s%n", type, tot.sum(), Util.getTimeSpanString(responseNanoseconds.get(type).sum() / 1_000_000)));
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
