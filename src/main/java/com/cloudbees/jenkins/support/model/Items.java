package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Created by schristou88 on 2/10/17.
 */

public class Items implements Serializable, MarkdownFile{
    Map<String, Integer> containerCounts = new TreeMap<>();
    Map<String, Stats> jobStats = new HashMap<>();
    Map<String, Stats> containerStats = new HashMap<>();
    Stats jobTotal = new Stats();

    public Map<String, Integer> getContainerCounts() {
        return containerCounts;
    }

    public void setContainerCounts(Map<String, Integer> containerCounts) {
        this.containerCounts = containerCounts;
    }

    public Map<String, Stats> getJobStats() {
        return jobStats;
    }

    public void setJobStats(Map<String, Stats> jobStats) {
        this.jobStats = jobStats;
    }

    public Map<String, Stats> getContainerStats() {
        return containerStats;
    }

    public void setContainerStats(Map<String, Stats> containerStats) {
        this.containerStats = containerStats;
    }

    public Stats getJobTotal() {
        return jobTotal;
    }

    public void setJobTotal(Stats jobTotal) {
        this.jobTotal = jobTotal;
    }

    public static class Stats implements Serializable {
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


    public static class Item implements Serializable {
        String name;
        String numberOfItems;
        String numofItemsOrBuilds;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getNumberOfItems() {
            return numberOfItems;
        }

        public void setNumberOfItems(String numberOfItems) {
            this.numberOfItems = numberOfItems;
        }

        public String getNumofItemsOrBuilds() {
            return numofItemsOrBuilds;
        }

        public void setNumofItemsOrBuilds(String numofItemsOrBuilds) {
            this.numofItemsOrBuilds = numofItemsOrBuilds;
        }
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("Item statistics");
        out.println("===============");
        out.println();
        for (Map.Entry<String,Integer> entry : containerCounts.entrySet()) {
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
}