/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.google.common.collect.Iterators;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.acegisecurity.Authentication;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Items content
 */
@Extension
public class ItemsContent extends Component {

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Items Content (Computationally expensive)";
    }

    @Override
    public void addContents(@NonNull Container result) {
        final Authentication authentication = SupportPlugin.getRequesterAuthentication();
        if (authentication != null) {
            result.add(new PrintedContent("items.md") {
                @Override
                protected void printTo(PrintWriter out) throws IOException {
                    final Jenkins jenkins = Jenkins.getInstanceOrNull();
                    Map<String,Integer> containerCounts = new TreeMap<>();
                    Map<String,ItemsContent.Stats> jobStats = new HashMap<>();
                    ItemsContent.Stats jobTotal = new ItemsContent.Stats();
                    Map<String,ItemsContent.Stats> containerStats = new HashMap<String,ItemsContent.Stats>();

                    long startTime = System.currentTimeMillis();
                    for (Item i : jenkins.getAllItems()) {
                        String key = i.getClass().getName();
                        Integer cnt = containerCounts.get(key);
                        containerCounts.put(key, cnt == null ? 1 : cnt + 1);
                        if (i instanceof Job) {
                            Job<?,?> j = (Job) i;
                            int builds = 0;
                            Iterator buildsIterator = j.getBuilds().iterator();
                            builds = Iterators.size(buildsIterator);
                            jobTotal.add(builds);
                            ItemsContent.Stats s = jobStats.get(key);
                            if (s == null) {
                                jobStats.put(key, s = new ItemsContent.Stats());
                            }
                            s.add(builds);
                        }
                        if (i instanceof ItemGroup) {
                            ItemsContent.Stats s = containerStats.get(key);
                            if (s == null) {
                                containerStats.put(key, s = new ItemsContent.Stats());
                            }
                            s.add(((ItemGroup) i).getItems().size());
                        }
                    }
                    long endTime = System.currentTimeMillis();
                    LOGGER.log(Level.FINE, "Time to compute all the build is {0}", endTime - startTime);
                    out.println("Item statistics");
                    out.println("===============");
                    out.println();
                    for (Map.Entry<String,Integer> entry : containerCounts.entrySet()) {
                        String key = entry.getKey();
                        out.println("  * `" + key + "`");
                        out.println("    - Number of items: " + entry.getValue());
                        ItemsContent.Stats s = jobStats.get(key);
                        if (s != null) {
                            out.println("    - Number of builds per job: " + s);
                        }
                        s = containerStats.get(key);
                        if (s != null) {
                            out.println("    - Number of items per container: " + s);
                        }
                    }
                    out.println();
                    out.println("Total job statistics");
                    out.println("======================");
                    out.println();
                    out.println("  * Number of jobs: " + jobTotal.n());
                    out.println("  * Number of builds per job: " + jobTotal);
                }
            });
        }
    }

    private static class Stats {
        private int s0 = 0;
        private long s1 = 0;
        private long s2 = 0;

        public synchronized void add(int x) {
            s0++;
            s1 += x;
            s2 += x * (long) x;
        }

        public synchronized double x() {
            return s1 / (double) s0;
        }

        private static double roundToSigFig(double num, int sigFig) {
            if (num == 0) {
                return 0;
            }
            final double d = Math.ceil(Math.log10(num < 0 ? -num : num));
            final int pow = sigFig - (int) d;
            final double mag = Math.pow(10, pow);
            final long shifted = Math.round(num * mag);
            return shifted / mag;
        }

        public synchronized double s() {
            if (s0 >= 2) {
                double v = Math.sqrt((s0 * (double) s2 - s1 * (double) s1) / s0 / (s0 - 1));
                if (s0 <= 100) {
                    return roundToSigFig(v, 1); // 0.88*SD to 1.16*SD
                }
                if (s0 <= 1000) {
                    return roundToSigFig(v, 2); // 0.96*SD to 1.05*SD
                }
                return v;
            } else {
                return Double.NaN;
            }
        }

        public synchronized String toString() {
            if (s0 == 0) {
                return "N/A";
            }
            if (s0 == 1) {
                return Long.toString(s1) + " [n=" + s0 + "]";
            }
            return Double.toString(x()) + " [n=" + s0 + ", s=" + s() + "]";
        }

        public synchronized int n() {
            return s0;
        }
    }

    @Override
    public boolean isSelectedByDefault() {
        return false;
    }

    private static final Logger LOGGER = Logger.getLogger(ItemsContent.class.getName());
}
