package com.cloudbees.jenkins.support.util;

import hudson.model.Describable;

import javax.annotation.CheckForNull;

public class SupportUtils {
    public static String trimToEmpty(Object obj) {
        return (obj == null) ? "" : obj.toString();
    }

    public static String getDescriptorName(@CheckForNull Describable<?> d) {
        if (d == null) {
            return "(none)";
        }
        return "`" + d.getClass().getName() + "`";
    }
}
