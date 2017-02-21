package com.cloudbees.jenkins.support.model;

import lombok.Data;

import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by schristou88 on 2/21/17.
 */
@Data
public class LoggerManager implements Serializable, MarkdownFile {
    List<Logger> loggerList = new ArrayList<>();

    public void addLogger(Logger logger) {
        loggerList.add(logger);
    }

    @Data
    public static class Logger {
        String name;
        String level;
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
