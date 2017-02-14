package com.cloudbees.jenkins.support.model;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.SupportProvider;
import com.cloudbees.jenkins.support.impl.AboutJenkins;
import com.cloudbees.jenkins.support.util.Helper;
import hudson.PluginManager;
import hudson.PluginWrapper;
import hudson.lifecycle.Lifecycle;
import jenkins.model.Jenkins;
import jenkins.model.JenkinsLocationConfiguration;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.kohsuke.stapler.Stapler;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

/**
 * Created by schristou88 on 2/9/17.
 */
@Data
public class About implements Serializable, MarkdownFile {
    VersionDetails versionDetails = new VersionDetails();
    ImportantConfiguration importantConfiguration = new ImportantConfiguration();
    ActivePlugins activePlugins = new ActivePlugins();
    PackagingDetails packagingDetails = new PackagingDetails();

    @Data
    public static class VersionDetails {
        String version;
        String mode;
        String url;
        ServletContainer container = new ServletContainer();
        Java java = new Java();
        JavaRuntimeSpecification javaRuntimeSpecification = new JavaRuntimeSpecification();
        JVMSpecification jvmSpecification = new JVMSpecification();
        JVMImplementation jvmImplementation = new JVMImplementation();
        OperatingSystem operatingSystem = new OperatingSystem();
        int processID;
        Date process_started;
        String process_uptime;
        JVMStartupParameters jvmStartupParameters = new JVMStartupParameters();
        String distribution;
        String LSB_modules;

        @Data
        public static class ServletContainer {
            String specification;
            String name;
        }

        @Data
        public static class Java {
            String home;
            String vendor;
            String version;
            String maximum_memory;
            String allocated_memory;
            String free_memory;
            String in_use_memory;
            String gc_strategy;
            String permgen_used;
            String permgen_max;
        }

        @Data
        public static class JavaRuntimeSpecification {
            String name;
            String vendor;
            String version;
        }

        @Data
        public static class JVMSpecification {
            String name;
            String vendor;
            String version;
        }

        @Data
        public static class JVMImplementation {
            String name;
            String vendor;
            String version;
        }

        @Data
        public static class OperatingSystem {
            String name;
            String Architecture;
            String version;
        }

        @Data
        public static class JVMStartupParameters {
            String boot_classpath;
            String classPath;
            String libraryClasspath;
            List<String> args = new ArrayList<>();

            public JVMStartupParameters addArg(String arg) {
                this.args.add(arg);
                return this;
            }

        }
    }

    @Data
    public static class ImportantConfiguration {
        String securityRealm;
        String authorizationStrategy;
        boolean CSRF_protection;
        String initializationMilestone;
    }

    @Data
    public static class ActivePlugins {
        List<Plugin> activePlugins = new ArrayList<>();

        public ActivePlugins addPlugin(Plugin plugin) {
            this.activePlugins.add(plugin);
            return this;
        }

        @Data
        public static class Plugin {
            String name;
            String version;
            boolean updates_available;
            String description;
        }
    }

    @Data
    public static class PackagingDetails {
        String details;
    }

    @Override
    public void toMarkdown(PrintWriter out) {
        out.println("Jenkins");
        out.println("=======");
        out.println();
        out.println("Version details");
        out.println("---------------");
        out.println();
        out.println("  * Version: `" + versionDetails.getVersion().replaceAll("`", "&#96;") + "`");
        out.println("  * Mode:    " + versionDetails.getMode());

        out.println("  * Url:     " + (versionDetails.getUrl() != null ? versionDetails.getUrl() : "No JenkinsLocationConfiguration available"));
        try {
            out.println("  * Servlet container");
            out.println("      - Specification: " + versionDetails.getContainer().getSpecification());
            out.println(
                    "      - Name:          `" + versionDetails.getContainer().getName().replaceAll("`", "&#96;") + "`");
        } catch (NullPointerException e) {
            // pity Stapler.getCurrent() throws an NPE when outside of a request
        }
        out.print(getVersionDetails());
        out.println();
        out.println("Important configuration");
        out.println("---------------");
        out.println();
        out.println("  * Security realm: " + importantConfiguration.getSecurityRealm());
        out.println("  * Authorization strategy: " + importantConfiguration.getAuthorizationStrategy());
        out.println("  * CSRF Protection: " + importantConfiguration.isCSRF_protection());
        out.println("  * Initialization Milestone: " + importantConfiguration.getInitializationMilestone());
        out.println();
        out.println("Active Plugins");
        out.println("--------------");
        out.println();
        for (ActivePlugins.Plugin w : activePlugins.activePlugins) {
            out.println("  * " + w.getName() + ":" + w.getVersion() + (w.isUpdates_available()
                    ? " *(update available)*"
                    : "") + " '" + w.getDescription() + "'");
        }
    }
}