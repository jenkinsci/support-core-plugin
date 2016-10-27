package com.cloudbees.jenkins.support.impl;

import hudson.Extension;
import hudson.model.UnprotectedRootAction;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerProxy;
import org.kohsuke.stapler.StaplerRequest;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * Created by michaelneale on 24/10/16.
 */
@Extension
public class BrowserErrorCollector implements UnprotectedRootAction, StaplerProxy {

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
            String msg = IOUtils.toString(request.getInputStream());
            System.err.println(msg);
            LOGGER.info(msg);

        } catch (IOException e) {
            LOGGER.severe("Unable to read stream");
        }

        try {
            Stapler.getCurrentResponse().sendRedirect("/");
        } catch (IOException e) {

        }


        return null;
    }


}
