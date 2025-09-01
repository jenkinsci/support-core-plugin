/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support.api;

import com.cloudbees.jenkins.support.Messages;
import com.cloudbees.jenkins.support.configfiles.AgentsConfigFile;
import com.cloudbees.jenkins.support.configfiles.ConfigFileComponent;
import com.cloudbees.jenkins.support.configfiles.OtherConfigFilesComponent;
import com.cloudbees.jenkins.support.impl.*;
import com.cloudbees.jenkins.support.slowrequest.SlowRequestComponent;
import com.cloudbees.jenkins.support.slowrequest.SlowRequestThreadDumpsComponent;
import com.cloudbees.jenkins.support.startup.ShutdownComponent;
import com.cloudbees.jenkins.support.startup.StartupComponent;
import com.cloudbees.jenkins.support.threaddump.HighLoadComponent;
import com.cloudbees.jenkins.support.timer.DeadlockRequestComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import hudson.model.AbstractModelObject;
import hudson.security.ACL;
import hudson.security.Permission;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jenkins.model.Jenkins;
import org.jvnet.localizer.Localizable;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;
import org.springframework.security.core.Authentication;

/**
 * Represents a component of a support bundle.
 *
 * <p>
 * This is the unit of user consent; when creating a support bundle, the user would enable/disable
 * individual components.
 *
 * @author Stephen Connolly
 */
@ExportedBean
public abstract class Component implements ExtensionPoint {

    /**
     * Returns the (possibly empty, never null) list of permissions that are required for the user to include this
     * in a bundle. An empty list indicates that any user can include this bundle.
     *
     * @return the (possibly empty, never null) list of permissions that are required for the user to include this
     *         in a bundle.
     */
    @NonNull
    public abstract Set<Permission> getRequiredPermissions();

    private Set<Permission> _getRequiredPermissions() {
        try {
            return getRequiredPermissions();
        } catch (AbstractMethodError x) {
            return Collections.emptySet();
        }
    }

    public String getDisplayPermissions() {
        StringBuilder buf = new StringBuilder();
        boolean first = true;
        for (Permission p : _getRequiredPermissions()) {
            if (first) {
                first = false;
            } else {
                buf.append(", ");
            }
            buf.append(p.group.title.toString());
            buf.append('/');
            buf.append(p.name);
        }
        return buf.toString();
    }

    /**
     * Returns {@code true} if the current authentication can include this component in a bundle.
     *
     * @return {@code true} if the current authentication can include this component in a bundle.
     */
    @Exported
    public boolean isEnabled() {
        ACL acl = Jenkins.get().getAuthorizationStrategy().getRootACL();
        Authentication authentication = Jenkins.getAuthentication2();
        for (Permission p : _getRequiredPermissions()) {
            if (!acl.hasPermission2(authentication, p)) {
                return false;
            }
        }
        return true;
    }

    @Exported
    public boolean isSelectedByDefault() {
        return true;
    }

    /**
     * This method will indicate if the component can be generated asynchronously.
     * This is useful for components that need request context info that only be available in a request thread.
     * By default, it will return true.
     * @return
     */
    public boolean canBeGeneratedAsync() {
        return true;
    }

    /**
     * Return if this component is applicable to a specific class of item.
     *
     * @param clazz the class
     * @param <C> Object that extends {@link AbstractModelObject}
     * @return {@code true} if applicable to this class
     */
    public <C extends AbstractModelObject> boolean isApplicable(Class<C> clazz) {
        return Jenkins.class.isAssignableFrom(clazz);
    }

    @NonNull
    @Exported
    public abstract String getDisplayName();

    /**
     * Add contents to a container
     * @param container a {@link Container}
     */
    public abstract void addContents(@NonNull Container container);

    /**
     * Returns the component id.
     *
     * @return by default, the {@link Class#getSimpleName} of the component implementation.
     */
    @Exported
    @NonNull
    public String getId() {
        return getClass().getSimpleName();
    }

    /**
     * Used for getting a hash code with the options selected by the user.
     * Each component (class that extends the Component class) should have a unique value.
     * The UI will ignore any component with a "-1" value in the id (extracted from this getHash method)
     * in order to generate the final hash value for the selected options.
     */
    public int getHash() {
        return ComponentCategory.HASHES.getOrDefault(this.getClass().getName(), -1);
    }

    @Deprecated
    public void start(@NonNull SupportContext context) {}

    /**
     * Specify in which {@link ComponentCategory} the current component is related.
     *
     * @return An enum value of {@link ComponentCategory}.
     * @since TODO
     */
    @Exported
    @NonNull
    public ComponentCategory getCategory() {
        return ComponentCategory.UNCATEGORIZED;
    }

    /**
     * Returns true if a component is superseded by this component.
     * useful if we write a component that makes another one obsolete.
     */
    public boolean supersedes(Component component) {
        return false;
    }

    /**
     * Categories supported by this version of support-core
     *
     * @since TODO
     */
    public enum ComponentCategory {
        /**
         * For components related to the agents, their configuration and other bits.
         */
        AGENT(Messages._SupportPlugin_Category_Agent()),
        /**
         * For components related to queued, running, or historical builds.
         */
        BUILDS(Messages._SupportPlugin_Category_Builds()),
        /**
         * For components related to the controller, its configuration and other bits.
         */
        CONTROLLER(Messages._SupportPlugin_Category_Controller()),
        /**
         * For components related to the logging system or the logs themself.
         */
        LOGS(Messages._SupportPlugin_Category_Logs()),
        /**
         * Anything that doesn't fit any other categories.
         */
        MISC(Messages._SupportPlugin_Category_Misc()),
        /**
         * For components related to how the controller is running and where it is running.
         */
        PLATFORM(Messages._SupportPlugin_Category_Platform()),
        /**
         * The default category. This shouldn't be explicitly used.
         */
        UNCATEGORIZED(Messages._SupportPlugin_Category_Uncategorized());

        private final Localizable label;

        ComponentCategory(Localizable label) {
            this.label = label;
        }

        public @NonNull String getLabel() {
            return label.toString();
        }

        public static Map<String, Integer> HASHES = new HashMap<String, Integer>();
        static {
            HASHES.put(AboutBrowser.class.getName(),0);
            HASHES.put(AboutJenkins.class.getName(),1);
            HASHES.put(AboutUser.class.getName(),2);
            HASHES.put(AbstractItemDirectoryComponent.class.getName(),3);
            HASHES.put(AdministrativeMonitors.class.getName(), 4);
            HASHES.put(AgentsConfigFile.class.getName(), 5);
            HASHES.put(BuildQueue.class.getName(), 6);
            HASHES.put(ConfigFileComponent.class.getName(), 7);
            HASHES.put(CustomLogs.class.getName(), 8);
            HASHES.put(DeadlockRequestComponent.class.getName(), 9);
            HASHES.put(DumpExportTable.class.getName(), 10);
            HASHES.put(EnvironmentVariables.class.getName(), 11);
            HASHES.put(FileDescriptorLimit.class.getName(), 12);
            HASHES.put(GCLogs.class.getName(), 13);
            HASHES.put(HeapUsageHistogram.class.getName(), 14);
            HASHES.put(HighLoadComponent.class.getName(), 15);
            HASHES.put(ItemsContent.class.getName(), 16);
            HASHES.put(JenkinsLogs.class.getName(), 17);
            HASHES.put(JVMProcessSystemMetricsContents.Master.class.getName(), 18);
            HASHES.put(JVMProcessSystemMetricsContents.Agents.class.getName(), 19);
            HASHES.put(LoadStats.class.getName(), 20);
            HASHES.put(LoggerManager.class.getName(), 21);
            HASHES.put(Metrics.class.getName(), 22);
            HASHES.put(NetworkInterfaces.class.getName(), 23);
            HASHES.put(NodeExecutors.class.getName(), 24);
            HASHES.put(NodeMonitors.class.getName(), 25);
            HASHES.put(NodeRemoteDirectoryComponent.class.getName(), 26);
            HASHES.put(OtherConfigFilesComponent.class.getName(), 27);
            HASHES.put(OtherLogs.class.getName(), 28);
            HASHES.put(ProxyConfiguration.class.getName(), 29);
            HASHES.put(RemotingDiagnostics.class.getName(), 30);
            HASHES.put(ReverseProxy.class.getName(), 31);
            HASHES.put(RootCAs.class.getName(), 32);
            HASHES.put(RunDirectoryComponent.class.getName(), 33);
            HASHES.put(RunningBuilds.class.getName(), 34);
            HASHES.put(ShutdownComponent.class.getName(), 35);
            HASHES.put(SlaveCommandStatistics.class.getName(), 36);
            HASHES.put(SlaveLaunchLogs.class.getName(), 37);
            HASHES.put(SlaveLogs.class.getName(), 38);
            HASHES.put(SlowRequestComponent.class.getName(), 39);
            HASHES.put(SlowRequestThreadDumpsComponent.class.getName(), 40);
            HASHES.put(StartupComponent.class.getName(), 41);
            HASHES.put(SystemConfiguration.Master.class.getName(), 42);
            HASHES.put(SystemConfiguration.Agents.class.getName(), 43);
            HASHES.put(SystemProperties.class.getName(), 44);
            HASHES.put(TaskLogs.class.getName(), 45);
            HASHES.put(ThreadDumps.class.getName(), 46);
            HASHES.put(UpdateCenter.class.getName(), 47);
            HASHES.put(UserCount.class.getName(), 48);
            HASHES.put("org.jenkinsci.plugins.workflow.cps.CpsFlowExecution$PipelineInternalCalls", 49);
            HASHES.put("org.jenkinsci.plugins.workflow.cps.CpsFlowExecution$PipelineTimings", 50);
            HASHES.put("org.jenkinsci.plugins.workflow.cps.CpsThreadDumpAction$PipelineThreadDump", 51);
        };
    }
}
