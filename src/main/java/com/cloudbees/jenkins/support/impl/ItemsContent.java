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
import java.util.function.Function;

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
                            builds += countBuilds(buildDir.toPath(), this::parseInt);
                        } else {
                            builds += countBuilds(buildDir.toPath(), this::parseDate);
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

            private Integer countBuilds(Path buildDirPath, Function<String, Optional<? extends Comparable>> parseMethod) {
                int builds = 0;
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(buildDirPath)) {
                    for (Path path : stream) {
                        if (Files.isDirectory(path) && parseMethod.apply(path.toFile().getName()).isPresent()) {
                            builds++;
                        }
                    }
                } catch (IOException e) {
                    // ignore
                }
                return builds;
            }

            @Override
            public boolean shouldBeFiltered() {
                return false;
            }
        });
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.CONTROLLER;
    }

    private static class Stats {
        private int count = 0;
        private long sumOfValues = 0;
        private long sumOfSquaredValues = 0;

        public synchronized void add(int x) {
            count++;
            sumOfValues += x;
            sumOfSquaredValues += x * (long) x;
        }

        /**
         * Compute the mean
         * @return the mean
         */
        public synchronized double mean() {
            return sumOfValues / (double) count;
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

        /**
         * Compute the Standard Deviation (or Variance) as a measure of dispersion
         * @return the standard deviation 
         */
        public synchronized double standardDeviation() {
            if (count >= 2) {
                double v = Math.sqrt((count * (double) sumOfSquaredValues - sumOfValues * (double) sumOfValues) / count / (count - 1));
                if (count <= 100) {
                    return roundToSigFig(v, 1); // 0.88*SD to 1.16*SD
                }
                if (count <= 1000) {
                    return roundToSigFig(v, 2); // 0.96*SD to 1.05*SD
                }
                return v;
            } else {
                return Double.NaN;
            }
        }

        public synchronized String toString() {
            if (count == 0) {
                return "N/A";
            }
            if (count == 1) {
                return sumOfValues + " [n=" + count + "]";
            }
            return mean() + " [n=" + count + ", s=" + standardDeviation() + "]";
        }

        public synchronized int n() {
            return count;
        }
    }

    @Override
    public boolean isSelectedByDefault() {
        return true;
    }
}
