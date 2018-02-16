package com.cloudbees.jenkins.support.api;

import com.cloudbees.jenkins.support.AsyncResultCache;
import com.cloudbees.jenkins.support.SupportLogFormatter;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Content of a command output. You can only instantiate this content with
 * a builder method which launch the command immediately.
 */
public class CommandOutputContent extends StringContent {

    private static final Logger LOGGER = Logger.getLogger(CommandOutputContent.class.getName());

    private CommandOutputContent(ContentData contentData, String value) {
        super(contentData, value);
    }

    public static class CommandLauncher extends MasterToSlaveCallable<String, RuntimeException> {
        final String[] command;

        private CommandLauncher(String... command) {
            this.command = command;
        }

        public String call() {
            StringWriter bos = new StringWriter();
            PrintWriter pw = new PrintWriter(bos);
            try {
                Process proc = new ProcessBuilder().command(command).redirectErrorStream(true).start();
                IOUtils.copy(proc.getInputStream(), pw);
            } catch (Exception e) {
                SupportLogFormatter.printStackTrace(e, pw);
            }
            pw.flush();
            return bos.toString();
        }
    }

    public static CommandOutputContent runOnNode(Node node, String name, String... command) {
        return runOnNode(node, false, name, command);
    }

    public static CommandOutputContent runOnNode(Node node, boolean shouldAnonymize, String name, String... command) {
        String content = "Exception occurred while retrieving command content";

        VirtualChannel chan = node.getChannel();
        if (chan == null) {
            content = "No connection to node";
        } else {
            try {
                content = chan.call(new CommandLauncher(command));
            } catch (IOException | InterruptedException e) {
                final LogRecord lr = new LogRecord(Level.FINE, "Could not retrieve command content from {0}");
                lr.setParameters(new Object[]{getNodeName(node, shouldAnonymize)});
                lr.setThrown(e);
                LOGGER.log(lr);
            }
        }

        return new CommandOutputContent(new ContentData(name, shouldAnonymize), content);
    }

    public static CommandOutputContent runOnNodeAndCache(WeakHashMap<Node, String> cache, Node node, String name, String... command) {
        return runOnNodeAndCache(cache, node, false, name, command);
    }

    public static CommandOutputContent runOnNodeAndCache(WeakHashMap<Node, String> cache, Node node, boolean shouldAnonymize, String name, String... command) {
        String content = "Exception occurred while retrieving command content";

        try {
            content = AsyncResultCache.get(node, cache, shouldAnonymize, new CommandLauncher(command), "sysctl info",
                    "N/A: Either no connection to node or no cached result");
        } catch (IOException e) {
            final LogRecord lr = new LogRecord(Level.FINE, "Could not retrieve sysctl content from {0}");
            lr.setParameters(new Object[]{getNodeName(node, shouldAnonymize)});
            lr.setThrown(e);
            LOGGER.log(lr);
        }

        return new CommandOutputContent(new ContentData(name, shouldAnonymize), content);
    }
}
