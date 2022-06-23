package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.PrintedContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * Output the current list of loggers for the controller and display the
 * logging level. This is to diagnose if a specific logger is causing
 * some performance issues by logging too much data.
 *
 * @since 2.30
 */
@Extension
public class LoggerManager extends Component {
    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "All loggers currently enabled";
    }

    @Override
    public void addContents(@NonNull Container container) {
        container.add(new PrintedContent("loggers.md") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                out.println("Loggers currently enabled");
                out.println("=========================");
                LogManager logManager = LogManager.getLogManager();
                Enumeration<String> loggerNames = logManager.getLoggerNames();
                while (loggerNames.hasMoreElements()) {
                    String loggerName =  loggerNames.nextElement();
                    Logger loggerByName = logManager.getLogger(loggerName);
                    if (loggerByName != null) {
                        Level level = loggerByName.getLevel();
                        if (level != null) {
                            out.println(loggerName + " - " + level);
                        }
                    }
                }
            }
        });
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.LOGS;
    }
}
