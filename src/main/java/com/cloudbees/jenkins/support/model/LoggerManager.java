package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by schristou88 on 2/21/17.
 */

public class LoggerManager implements Serializable, MarkdownFile {
    List<Logger> loggerList = new ArrayList<>();

    public void addLogger(Logger logger) {
        loggerList.add(logger);
    }

    public List<Logger> getLoggerList() {
        return loggerList;
    }

    public void setLoggerList(List<Logger> loggerList) {
        this.loggerList = loggerList;
    }

    public static class Logger implements Serializable {
        String name;
        String level;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getLevel() {
            return level;
        }

        public void setLevel(String level) {
            this.level = level;
        }
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("Loggers currently enabled");
        out.println("=========================");

        for (Logger logger : loggerList) {
            out.println(logger.getName() + " - " + logger.getLevel());
        }
    }
}
