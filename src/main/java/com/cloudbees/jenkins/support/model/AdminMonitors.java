package com.cloudbees.jenkins.support.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by stevenchristou on 2/3/17.
 */
@Data
public class AdminMonitors {
    List<AdminMonitor> monitorList = new ArrayList<>();

    public void addMonitor(AdminMonitor monitor) {
        monitorList.add(monitor);
    }

    @Data
    public static class OldDataMonitor extends AdminMonitor {
        List<OldData> olddata;

        public void addOldData(OldData data) {
            olddata.add(data);
        }

        @Data
        public static class OldData {
            String problematicObject;
            String range;
            String extra;
        }
    }

    @Data
    public static class AdminMonitor {
        String id;
        boolean active;
    }
}
