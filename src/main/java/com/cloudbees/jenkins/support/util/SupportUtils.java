package com.cloudbees.jenkins.support.util;

import hudson.model.Describable;

import javax.annotation.CheckForNull;

/**
 * Created by schristou88 on 2/21/17.
 */
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
