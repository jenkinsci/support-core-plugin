package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.ObjectComponent;
import com.cloudbees.jenkins.support.api.ObjectComponentDescriptor;
import com.cloudbees.jenkins.support.api.TruncatedContent;
import com.cloudbees.jenkins.support.api.TruncationException;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.AbstractModelObject;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.remoting.Channel;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Dump export table of nodes to detect potential
 * memory leaks.
 *
 * @since 2.23
 * <p>
 * User: schristou88
 * Date: 1/8/15
 * Time: 3:54 PM
 */
@Extension
public class DumpExportTable extends ObjectComponent<Computer> {

    private static final long serialVersionUID = 1L;

    private final Logger logger = Logger.getLogger(DumpExportTable.class.getName());

    @DataBoundConstructor
    public DumpExportTable() {}

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Dump agent export tables (could reveal some memory leaks)";
    }

    @Override
    public void addContents(@NonNull Container result) {
        Jenkins.get().getNodes().stream().map(Node::toComputer).forEach(computer -> Optional.ofNullable(computer)
                .ifPresent(comp -> addContents(result, comp)));
    }

    @Override
    public void addContents(@NonNull Container container, @NonNull Computer item) {
        container.add(new TruncatedContent("nodes/slave/{0}/exportTable.txt", item.getName()) {
            @Override
            protected void printTo(PrintWriter out) {
                try {

                    VirtualChannel channel = item.getChannel();
                    if (channel instanceof Channel) // Should never be false but just in case.
                    {
                        ((Channel) channel).dumpExportTable(out);
                    }
                } catch (TruncationException e) {
                    logger.log(Level.WARNING, "Truncated the output of export table for node: " + item.getName(), e);
                } catch (IOException e) {
                    logger.log(Level.WARNING, "Could not record environment of node " + item.getName(), e);
                }
            }
        });
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.AGENT;
    }

    @Override
    public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
        return Jenkins.class.isAssignableFrom(clazz) || Computer.class.isAssignableFrom(clazz);
    }

    @Override
    protected boolean isApplicable(Computer item) {
        return item != Jenkins.get().toComputer();
    }

    @Override
    public DescriptorImpl getDescriptor() {
        return Jenkins.get().getDescriptorByType(DescriptorImpl.class);
    }

    @Extension
    @Symbol("agentDumpExportTableComponent")
    public static class DescriptorImpl extends ObjectComponentDescriptor<Computer> {
        /**
         * {@inheritDoc}
         */
        @NonNull
        @Override
        public String getDisplayName() {
            return "Dump agent export tables (could reveal some memory leaks)";
        }
    }
}
