package com.cloudbees.jenkins.support.util;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class Chrono {
    private Map<String, Point> points = new LinkedHashMap<>();
    private String initName;
    private Long initTime;
    private String lastPoint;

    public Chrono(String startName) {
        restart(startName);
    }

    /**
     * Add a point at this precise time to measure time since the indicated points.
     * @param pointName the point name to be used when printing the info or requesting the information with any of the
     *                  getMeasures methods.
     * @param fromPointNames all the points to get measure from. If it's null, the start point is considered.
     */
    public void mark(String pointName, String... fromPointNames) {
        mark(pointName, System.currentTimeMillis(), fromPointNames);
    }

    /**
     * Add a point to measure time since the start point of this chrono.
     * @param pointName the point name to be used when printing the info or requesting the information with any of the
     *                  getMeasures methods.
     */
    public void mark(String pointName) {
        mark(pointName, (String[]) null);
    }

    public void markFromPrevious(String pointName, Long time) {
        mark(pointName, time, lastPoint);
    }

    public void markFromPrevious(String pointName) {
        mark(pointName, lastPoint);
    }

    /**
     * Add a point to measure time since the start point of this chrono.
     * @param pointName the point name to be used when printing the info or requesting the information with any of the
     *                  getMeasures methods.
     * @param time time when this point happens.
     */
    public void mark(String pointName, Long time) {
        mark(pointName, time, (String[]) null);
    }

    /**
     * Add a point at the specified time to measure time since the indicated points
     * @param pointName the point name to be used when printing the info or requesting the information with any of the
     *                  getMeasures methods.
     * @param time time when this point happens
     * @param fromPointNames all the points to get measure from. If it's null, the start point is considered.
     */
    public void mark(String pointName, Long time, String... fromPointNames) {
        if (fromPointNames == null) {
            fromPointNames = new String[] {initName};
        }
        points.put(pointName, new Point(pointName, time, Arrays.asList(fromPointNames)));
        lastPoint = pointName;
    }

    /**
     * Restart the chrono defining a new start time and cleaning the points previously defined.
     * @param startName name of the start point of this chrono.
     */
    public void restart(String startName) {
        initName = startName;
        initTime = System.currentTimeMillis();
        points = new LinkedHashMap<>();
        lastPoint = null;
        mark(initName, initTime);
    }

    public Map<String, Long> getMeasures(String pointName) {
        Point point = points.get(pointName);
        Map<String, Long> measures = new LinkedHashMap<>();

        if (point != null) {
            if (point.fromPoints != null) {
                for (String fromName : point.fromPoints) {
                    Point from = points.get(fromName);
                    if (from != null) {
                        measures.put(fromName, point.time - from.time);
                    }
                }
            }
        }
        return measures;
    }

    public Map<String, Map<String, Long>> getMeasures() {
        Set<String> keys = points.keySet();
        Map<String, Map<String, Long>> measures = new LinkedHashMap<>(points.size());

        for (String key : keys) {
            measures.put(key, getMeasures(key));
        }

        return measures;
    }

    public String printMeasures() {
        StringBuilder sb = new StringBuilder();
        sb.append("All measures of ");
        sb.append(initName);
        sb.append(" started at ");
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(initTime));
        sb.append("\n");
        for (String key : getMeasures().keySet()) {
            sb.append(printMeasures(key));
        }

        return sb.toString();
    }

    public String printMeasures(String pointName) {
        StringBuilder sb = new StringBuilder();

        if (!pointName.equals(initName)) {
            Map<String, Long> measures = getMeasures(pointName);

            sb.append("Measures for point '");
            sb.append(pointName);
            sb.append("'");
            if (points.get(pointName) != null) {
                sb.append(" (");
                sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS").format(points.get(pointName).time));
                sb.append(")");

                sb.append("\n");

                if (!measures.isEmpty()) {
                    for (Map.Entry<String, Long> e : measures.entrySet()) {
                        sb.append("\t - From point '");
                        sb.append(e.getKey());
                        sb.append("': ");
                        sb.append(timeToString(e.getValue()));
                        sb.append("\n");
                    }
                } else {
                    sb.append("\t - Not defined previous points\n");
                }
            } else {
                sb.append("\t - This point was not defined\n");
            }
        }
        return sb.toString();
    }

    public String printMeasure(String pointName) {
        Long diff = getMeasure(pointName);
        StringBuilder sb = new StringBuilder();
        sb.append("Point '");
        sb.append(pointName);
        sb.append("': ");
        if (diff == null) {
            sb.append("No previous points found\n");
        } else {
            sb.append(timeToString(diff));
            sb.append(" since last point\n");
        }
        return sb.toString();
    }

    /**
     * We don't care about point names, we just want the first measure. If there are more than one fromPoint defined for
     * this pointName, it returns the difference with the previous one.
     * @param pointName
     * @return
     */
    public Long getMeasure(String pointName) {
        Map<String, Long> measures = getMeasures(pointName);

        Map.Entry<String, Long> lastPoint = null;
        // Go to the last element
        for (Map.Entry<String, Long> entry : measures.entrySet()) {
            lastPoint = entry;
        }
        return lastPoint != null ? lastPoint.getValue() : null;
    }

    public static String timeToString(long millis) {
        Optional<TimeUnit> first = Stream.of(DAYS, HOURS, MINUTES, SECONDS, MILLISECONDS)
                .filter(u -> u.convert(millis, MILLISECONDS) > 0)
                .findFirst();
        TimeUnit unit = first.isPresent() ? first.get() : MILLISECONDS;
        double value = (double) millis / MILLISECONDS.convert(1, unit);
        return String.format("%.4g %s", value, unit.name().toLowerCase());
    }

    /**
     * Private class to manage the points to measure time.
     */
    private static class Point {
        final String name;
        final Long time;
        final List<String> fromPoints;

        private Point(String name, Long moment, List<String> fromPoints) {
            this.name = name;
            this.time = moment;
            this.fromPoints = fromPoints;
        }
    }
}
