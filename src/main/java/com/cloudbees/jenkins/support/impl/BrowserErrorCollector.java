package com.cloudbees.jenkins.support.impl;

import hudson.Extension;
import hudson.model.RootAction;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * A simple endpoint to collect unhandled browser JS errors.
 *
 * To make use of this, install a window.onerror handler,
 * using a script tag similar to the following in client side javascript code:
 *  <code>
 *
 *            window.onerror = function (msg, url, _lineNo, _columnNo, error) {
 *                var http = new XMLHttpRequest();
 *                http.open('POST', "/errorCollector", true);
 *                http.send(msg + '\n' + url + '\n' + error.stack);
 *                return false;
 *            }
 *
 *
 *  </code>
 *
 *         This will attempt to log the error with a maybe helpful JS stacktrace.
 *
 */
@Extension
public class BrowserErrorCollector implements RootAction, StaplerProxy {

    private static final Logger LOGGER = Logger.getLogger(BrowserErrorCollector.class.getName());

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return null;
    }

    @Override
    public String getUrlName() {
        return "errorCollector";
    }

    @Override
    public Object getTarget() {
        StaplerRequest request = Stapler.getCurrentRequest();
        try {
            javaScriptError(request);
        } catch (IOException e) {
            LOGGER.severe("Unable to log javascript error report. " + e.toString());
        }
        return null;
    }

    private void javaScriptError(StaplerRequest request) throws IOException {
        LOGGER.warning("Javascript error. User-agent: " +
                        request.getHeader("User-Agent") +
                        " -- Error:" +
                        IOUtils.toString(request.getInputStream()));
    }


}
