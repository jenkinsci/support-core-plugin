package com.cloudbees.jenkins.support.util;

public final class Markdown {
    /** Not instantiable. */
    private Markdown() {
        throw new AssertionError("Not instantiable");
    }

    public static String escapeUnderscore(String raw) {
        return raw.replaceAll("_", "&#95;");
    }
    public static String escapeBacktick(String raw) {
        return raw.replaceAll("`", "&#96;");
    }
}
