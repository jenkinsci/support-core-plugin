package com.cloudbees.jenkins.support.util;

/**
 * Created by schristou88 on 2/21/17.
 */
public class SupportUtils {
    public static String trimToEmpty(Object obj) {
        return (obj == null) ? "" : obj.toString();
    }
}
