package com.cloudbees.jenkins.support.model;

import com.cloudbees.jenkins.support.impl.AboutJenkins;
import lombok.Data;
import org.apache.commons.jexl.context.HashMapContext;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.*;

/**
 * Created by schristou88 on 2/10/17.
 */
@Data
public class Items implements Serializable, MarkdownFile{
    Map<String, Integer> containerCounts = new TreeMap<>();
    Map<String, Stats> jobStats = new HashMap<>();
    Map<String, Stats> containerStats = new HashMap<>();
    Stats jobTotal = new Stats();

    @Data
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

    @Data
    public static class Item implements Serializable {
        String name;
        String numberOfItems;
        String numofItemsOrBuilds;
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