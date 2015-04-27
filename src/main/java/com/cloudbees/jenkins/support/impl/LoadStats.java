/*
 * The MIT License
 *
 * Copyright (c) 2015, CloudBees, Inc.
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
import com.cloudbees.jenkins.support.api.Content;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Label;
import hudson.model.LoadStatistics;
import hudson.model.MultiStageTimeSeries;
import hudson.model.TimeSeries;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

/**
 * This component captures the Jenkins {@link LoadStatistics} for overall load, jobs not tied to a label and each
 * label's own load.
 *
 * @since 2.21
 */
@Extension
public class LoadStats extends Component {
    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.emptySet();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getDisplayName() {
        return "Load Statistics";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addContents(@NonNull Container container) {
        Jenkins jenkins = Jenkins.getInstance();
        add(container, "no-label", jenkins.unlabeledLoad);
        add(container, "overall", jenkins.overallLoad);
        for (Label l : jenkins.getLabels()) {
            try {
                add(container, String.format("label/%s", URLEncoder.encode(l.getName(), "UTF-8")), l.loadStatistics);
            } catch (UnsupportedEncodingException e) {
                // ignore UTF-8 is required by JLS specification
            }
        }
    }

    private void add(@NonNull Container container, String name, LoadStatistics stats) {
        // A headless environment may be missing the fonts required for these graphs, so even though
        // we should be able to generate graphs from a headless environment we will skip the graphs
        // if headless
        boolean headless = GraphicsEnvironment.isHeadless();
        for (MultiStageTimeSeries.TimeScale scale : MultiStageTimeSeries.TimeScale.values()) {
            String scaleName = scale.name().toLowerCase(Locale.ENGLISH);
            if (!headless) {
                BufferedImage image = stats.createTrendChart(scale).createChart().createBufferedImage(500, 400);
                container.add(new ImageContent(String.format("load-stats/%s/%s.png", name, scaleName), image));
            }
            container.add(new CsvContent(String.format("load-stats/%s/%s.csv", name, scaleName), stats, scale));
        }
        // on the other hand, if headless we should give an easy way to generate the graphs
        if (headless) {
            container.add(new GnuPlotScript(String.format("load-stats/%s/gnuplot", name)));
        }
    }

    private static class ImageContent extends Content {
        private final BufferedImage image;

        public ImageContent(String name, BufferedImage image) {
            super(name);
            this.image = image;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeTo(OutputStream os) throws IOException {
            ImageIO.write(image, "png", os);
        }
    }

    private static final List<Field> FIELDS = findFields();

    /**
     * The fields that a {@link LoadStatistics} has change as you move from pre-1.607 to post 1.607, so better to
     * just look and see what there is rather than hard-code.
     *
     * @return the fields that correspond to {@link MultiStageTimeSeries}
     */
    private static List<Field> findFields() {
        List<Field> result = new ArrayList<Field>();
        for (Field f : LoadStatistics.class.getFields()) {
            if (Modifier.isPublic(f.getModifiers()) && MultiStageTimeSeries.class.isAssignableFrom(f.getType())
                    && f.getAnnotation(Deprecated.class) == null) {
                result.add(f);
            }
        }
        return result;
    }

    private static class CsvContent extends PrintedContent {

        private final Map<String, float[]> data;
        private final long time;
        private final long clock;

        public CsvContent(String name, LoadStatistics stats,
                          MultiStageTimeSeries.TimeScale scale) {
            super(name);
            time = System.currentTimeMillis();
            clock = scale.tick;
            data = new TreeMap<String, float[]>();
            for (Field f : FIELDS) {
                try {
                    MultiStageTimeSeries ts = (MultiStageTimeSeries) f.get(stats);
                    if (ts != null) {
                        TimeSeries series = ts.pick(scale);
                        if (series != null) {
                            data.put(camelCaseToSentenceCase(f.getName()), series.getHistory());
                        }
                    }
                } catch (IllegalAccessException e) {
                    continue;
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void printTo(PrintWriter out) throws IOException {
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            dateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));

            out.print("time");
            int maxLen = 0;
            for (Map.Entry<String, float[]> entry : data.entrySet()) {
                out.print(',');
                out.print(entry.getKey());
                maxLen = Math.max(maxLen, entry.getValue().length);
            }
            out.println();
            for (int row = maxLen - 1, offset = 1; row >= 0; row--, offset++) {
                out.print(dateFormat.format(new Date(time - clock * (maxLen - offset))));
                for (float[] h : data.values()) {
                    out.print(',');
                    if (h.length > row) {
                        out.print(h[row]);
                    }
                }
                out.println();
            }
        }
    }

    private static class GnuPlotScript extends PrintedContent {

        public GnuPlotScript(String name) {
            super(name);
        }

        @Override
        protected void printTo(PrintWriter out) throws IOException {
            // the following is a reasonable approximation of the JFreeChart graphs
            out.println("#!/usr/bin/env gnuplot");
            out.println("set style line 101 lc rgb '#000000' lt 1 lw 1;");
            out.println("set border 3 front ls 101;");
            out.println("set tics nomirror out;");
            out.println("set format '%g';");
            out.println("set key box linestyle 101;");
            out.println("set key outside;");
            out.println("set key center bottom;");
            out.println("set key horizontal maxcols 3 spacing 1;");
            out.println("set timefmt \"%Y/%m/%d %H:%M:%S\";");
            out.println("set xdata time; ");
            out.println("set xtics rotate; ");
            out.println("set datafile sep \",\";");
            out.println("set term png font \"arial\" 9 size 500,400;");
            for (MultiStageTimeSeries.TimeScale scale : MultiStageTimeSeries.TimeScale.values()) {
                String scaleName = scale.name().toLowerCase(Locale.ENGLISH);
                out.printf("set output \"%s.png\";%n", scaleName);
                int col = 2;
                List<String> names = new ArrayList<String>();
                for (Field f : FIELDS) {
                    names.add(camelCaseToSentenceCase(f.getName()));
                }
                Collections.sort(names);
                for (String name : names) {
                    out.printf("%s \"%s.csv\" using 1:%d with lines title \"%s\"", col == 2 ? "plot" : ",", scaleName,
                            col, name);
                    col++;
                }
                out.println(";");
            }
        }
    }

    /**
     * Converts "aCamelCaseString" into "A sentence case string".
     *
     * @param camelCase camelCase.
     * @return Sentence case.
     */
    private static String camelCaseToSentenceCase(String camelCase) {
        String name = StringUtils.join(StringUtils.splitByCharacterTypeCamelCase(camelCase), " ");
        return name.substring(0, 1).toUpperCase(Locale.ENGLISH) + name.substring(1)
                .toLowerCase(Locale.ENGLISH);
    }
}
