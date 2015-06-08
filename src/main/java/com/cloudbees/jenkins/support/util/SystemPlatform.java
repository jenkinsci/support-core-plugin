package com.cloudbees.jenkins.support.util;

import hudson.remoting.Callable;

import java.util.Locale;

/**
 * System platform
 */
public enum SystemPlatform {
    LINUX, SOLARIS, WINDOWS, MACOSX, UNKNOWN;

    public static SystemPlatform current()  {
        String arch = System.getProperty("os.name").toLowerCase(Locale.ENGLISH);
        if(arch.contains("linux")) {
            return LINUX;
        } else if(arch.contains("windows")) {
            return WINDOWS;
        } else if(arch.contains("sun") || arch.contains("solaris"))  {
            return SOLARIS;
        } else if(arch.contains("mac")) {
            return MACOSX;
        } else {
            return UNKNOWN;
        }
    }

    static public class GetCurrentPlatform implements Callable<SystemPlatform, Exception> {
        private static final long serialVersionUID = 1L;
        public SystemPlatform call() {
            return current();
        }
    }
}
