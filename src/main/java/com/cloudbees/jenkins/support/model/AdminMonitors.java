package com.cloudbees.jenkins.support.model;

import lombok.Data;
import org.apache.commons.lang.StringUtils;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by stevenchristou on 2/3/17.
 */
@Data
public class AdminMonitors implements Serializable, MarkdownFile {
    List<AdminMonitor> monitorList = new ArrayList<>();

    public void addMonitor(AdminMonitor monitor) {
        monitorList.add(monitor);
    }

    @Data
    public static class OldDataMonitor extends AdminMonitor implements Serializable {
        List<OldData> olddata;

        public void addOldData(OldData data) {
            olddata.add(data);
        }

        @Data
        public static class OldData implements Serializable {
            String problematicObject;
            String range;
            String extra;
        }
    }

    @Data
    public static class AdminMonitor implements Serializable {
        String id;
        boolean active;
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("Monitors");
        out.println("========");
        for (AdminMonitor monitor : monitorList) {
            out.println();
            out.println("`" + monitor.id + "`");
            out.println("--------------");
            if (monitor instanceof OldDataMonitor) {
                OldDataMonitor odm = (OldDataMonitor) monitor;
                for (OldDataMonitor.OldData entry : odm.getOlddata()) {
                    out.println("  * Problematic object: `" + entry.getProblematicObject() + "`");
                    String range = entry.getRange();

                    if (!range.isEmpty()) {
                        out.println("    - " + range);
                    }

                    String extra = entry.getRange();

                    if (!StringUtils.isBlank(extra)) {
                        out.println("    - " + extra); // TODO could be a multiline stack trace, quote it
                    }
                }
            } else {
                // No specific content we can show; message.jelly is for HTML only.
                out.println("(active and enabled)");
            }
        }

    }
}
