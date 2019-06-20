package com.cloudbees.jenkins.support.api;

import com.cloudbees.jenkins.support.AsyncResultCache;
import hudson.Functions;
import hudson.model.Node;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Common logic to CommandOutputContent and UnfilteredCommandOutputContent.
 */
@Restricted(NoExternalUse.class)
class BaseCommandOutputContent {
    private static final Logger LOGGER = Logger.getLogger(BaseCommandOutputContent.class.getName());

    // It was previously in CommandOutputContent, but the constructor is private and there isn't any method returning
    // an object of this class. As no external use is expected, it's been moved here.
    private static class CommandLauncher extends MasterToSlaveCallable<String, RuntimeException> {
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
                Functions.printStackTrace(e, pw);
            }
            pw.flush();
            return bos.toString();
        }
    }

    private static String getNodeName(Node node) {
        return node instanceof Jenkins ? "master" : node.getNodeName();
    }

    static String runOnNode(Node node, String... command) {
        String content = "Exception occurred while retrieving command content";

        VirtualChannel chan = node.getChannel();
        if (chan == null) {
            content = "No connection to node";
        } else {
            try {
                content = chan.call(new BaseCommandOutputContent.CommandLauncher(command));
            } catch (IOException | InterruptedException e) {
                final LogRecord lr = new LogRecord(Level.FINE, "Could not retrieve command content from {0}");
                lr.setParameters(new Object[]{getNodeName(node)});
                lr.setThrown(e);
                LOGGER.log(lr);
            }
        }

        return content;
    }

    static String runOnNodeAndCache(WeakHashMap<Node, String> cache, Node node, String... command) {
        String content = "Exception occurred while retrieving command content";

        try {
            content = AsyncResultCache.get(node, cache, new BaseCommandOutputContent.CommandLauncher(command), "sysctl info",
                    "N/A: Either no connection to node or no cached result");
        } catch (IOException e) {
            final LogRecord lr = new LogRecord(Level.FINE, "Could not retrieve sysctl content from {0}");
            lr.setParameters(new Object[]{getNodeName(node)});
            lr.setThrown(e);
            LOGGER.log(lr);
        }

        return content;
    }

}
