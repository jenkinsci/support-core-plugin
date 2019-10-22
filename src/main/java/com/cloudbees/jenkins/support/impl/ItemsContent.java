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

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.model.Job;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

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
    
    private final DateFormat BUILD_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    @Override
    public void addContents(@NonNull Container result) {
        result.add(new PrintedContent("items.md") {

            @Override
            protected void printTo(PrintWriter out) {
                final Jenkins jenkins = Jenkins.getInstanceOrNull();
                if (jenkins == null) {
                    return;
                }
                Map<String, Integer> containerCounts = new TreeMap<>();
                Map<String, Stats> jobStats = new HashMap<>();
                Stats jobTotal = new Stats();
                Map<String, Stats> containerStats = new HashMap<>();
                jenkins.allItems().forEach(item -> {
                    String key = item.getClass().getName();
                    Integer cnt = containerCounts.get(key);
                    containerCounts.put(key, cnt == null ? 1 : cnt + 1);
                    if (item instanceof Job) {
                        Job<?, ?> j = (Job) item;
                        // too expensive: int builds = j.getBuilds().size();
                        int builds = 0;
                        File buildDir = jenkins.getBuildDirFor(j);
                        if(new File(buildDir, "legacyIds").isFile()) {
                            try (DirectoryStream<Path> stream = Files.newDirectoryStream(buildDir.toPath())) {
                                for (Path path : stream) {
                                    if (Files.isDirectory(path) && parseInt(path.toFile().getName()).isPresent()) {
                                        builds++;
                                    }
                                }
                            } catch (IOException e) {
                                // ignore
                            }
                        } else {
                            try (DirectoryStream<Path> stream = Files.newDirectoryStream(buildDir.toPath())) {
                                for (Path path : stream) {
                                    if (!Files.isDirectory(path) && parseDate(path.toFile().getName()).isPresent()) {
                                        builds++;
                                    }
                                }
                            } catch (IOException e) {
                                // ignore
                            }
                        }
                        jobTotal.add(builds);
                        Stats s = jobStats.get(key);
                        if (s == null) {
                            jobStats.put(key, s = new Stats());
                        }
                        s.add(builds);
                    }
                    if (item instanceof ItemGroup) {
                        Stats s = containerStats.get(key);
                        if (s == null) {
                            containerStats.put(key, s = new Stats());
                        }
                        s.add(((ItemGroup) item).getItems().size());
                    }
                });
                out.println("Item statistics");
                out.println("===============");
                out.println();
                for (Map.Entry<String, Integer> entry : containerCounts.entrySet()) {
                    String key = entry.getKey();
                    out.println("  * `" + key + "`");
                    out.println("    - Number of items: " + entry.getValue());
                    Stats s = jobStats.get(key);
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

            @Override
            public boolean shouldBeFiltered() {
                return false;
            }
        });
    } 
    
    private Optional<Integer> parseInt(String fileName) {
        try {
            return Optional.of(Integer.parseInt(fileName));
        } catch (NumberFormatException x) {
            return Optional.empty();
        }
    }

    private Optional<Date> parseDate(String fileName) {
        try {
            return Optional.of(BUILD_FORMAT.parse(fileName));
        } catch (ParseException x) {
            return Optional.empty();
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
                return s1 + " [n=" + s0 + "]";
            }
            return x() + " [n=" + s0 + ", s=" + s() + "]";
        }

        public synchronized int n() {
            return s0;
        }
    }

    @Override
    public boolean isSelectedByDefault() {
        return true;
    }
}
