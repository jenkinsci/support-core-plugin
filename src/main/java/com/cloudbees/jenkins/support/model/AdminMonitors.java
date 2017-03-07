package com.cloudbees.jenkins.support.model;

import org.apache.commons.lang.StringUtils;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by stevenchristou on 2/3/17.
 */

public class AdminMonitors implements Serializable, MarkdownFile {
    List<AdminMonitor> monitorList = new ArrayList<>();

    public void addMonitor(AdminMonitor monitor) {
        monitorList.add(monitor);
    }

    public List<AdminMonitor> getMonitorList() {
        return monitorList;
    }

    public void setMonitorList(List<AdminMonitor> monitorList) {
        this.monitorList = monitorList;
    }

    public static class OldDataMonitor extends AdminMonitor implements Serializable {
        List<OldData> olddata;

        public void addOldData(OldData data) {
            olddata.add(data);
        }

        public List<OldData> getOlddata() {
            return olddata;
        }

        public void setOlddata(List<OldData> olddata) {
            this.olddata = olddata;
        }

        public static class OldData implements Serializable {
            String problematicObject;
            String range;
            String extra;

            public String getProblematicObject() {
                return problematicObject;
            }

            public void setProblematicObject(String problematicObject) {
                this.problematicObject = problematicObject;
            }

            public String getRange() {
                return range;
            }

            public void setRange(String range) {
                this.range = range;
            }

            public String getExtra() {
                return extra;
            }

            public void setExtra(String extra) {
                this.extra = extra;
            }
        }
    }


    public static class AdminMonitor implements Serializable {
        String id;
        boolean active;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }
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
