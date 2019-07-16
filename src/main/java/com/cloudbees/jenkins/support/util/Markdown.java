package com.cloudbees.jenkins.support.util;

import javax.annotation.CheckForNull;

public final class Markdown {
    public static final String NONE_STRING = "(none)";

    /** Not instantiable. */
    private Markdown() {
        throw new AssertionError("Not instantiable");
    }

    public static String escapeUnderscore(@CheckForNull String raw) {
        return (raw == null) ? null : raw.replaceAll("_", "&#95;");
    }
    public static String escapeBacktick(@CheckForNull String raw) {
        return (raw == null) ? null : raw.replaceAll("`", "&#96;");
    }

    public static String prettyNone(String raw) { return raw != null && !raw.isEmpty() ? raw : NONE_STRING; }
}
