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
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
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
            long maximum_memory;
            long allocated_memory;
            long free_memory;
            long in_use_memory;
            String gc_strategy;
            long permgen_used;
            long permgen_max;
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

        private static String humanReadableSize(long size) {
            String measure = "B";
            if (size < 1024) {
                return size + " " + measure;
            }
            double number = size;
            if (number >= 1024) {
                number = number / 1024;
                measure = "KB";
                if (number >= 1024) {
                    number = number / 1024;
                    measure = "MB";
                    if (number >= 1024) {
                        number = number / 1024;
                        measure = "GB";
                    }
                }
            }
            DecimalFormat format = new DecimalFormat("#0.00");
            return format.format(number) + " " + measure + " (" + size + ")";
        }


        public String toMarkdown(String maj, String min) {
            StringBuilder result = new StringBuilder();
            result.append(maj).append(" Java\n");
            result.append(min).append(" Home:           `").append(java.home.replaceAll("`", "&#96;")).append("`\n");
            result.append(min).append(" Vendor:           ").append(java.vendor).append("\n");
            result.append(min).append(" Version:          ").append(java.version).append("\n");
            result.append(min).append(" Maximum memory:   ").append(humanReadableSize(java.maximum_memory)).append("\n");
            result.append(min).append(" Allocated memory: ").append(humanReadableSize(java.allocated_memory)).append("\n");
            result.append(min).append(" Free memory:      ").append(humanReadableSize(java.free_memory)).append("\n");
            result.append(min).append(" In-use memory:    ").append(java.allocated_memory - java.free_memory).append("\n");

            result.append(min).append(" PermGen used:     ").append(humanReadableSize(java.permgen_used)).append("\n");
            result.append(min).append(" PermGen max:      ").append(humanReadableSize(java.permgen_max)).append("\n");

            result.append(min).append(" GC strategy:      ").append(java.gc_strategy).append("\n");
            result.append(maj).append(" Java Runtime Specification\n");
            result.append(min).append(" Name:    ").append(javaRuntimeSpecification.name).append("\n");
            result.append(min).append(" Vendor:  ").append(javaRuntimeSpecification.vendor).append("\n");
            result.append(min).append(" Version: ").append(javaRuntimeSpecification.version).append("\n");
            result.append(maj).append(" JVM Specification\n");
            result.append(min).append(" Name:    ").append(jvmSpecification.name).append("\n");
            result.append(min).append(" Vendor:  ").append(jvmSpecification.vendor).append("\n");
            result.append(min).append(" Version: ").append(jvmSpecification.version).append("\n");
            result.append(maj).append(" JVM Implementation\n");
            result.append(min).append(" Name:    ").append(jvmImplementation.name).append("\n");
            result.append(min).append(" Vendor:  ").append(jvmImplementation.vendor).append("\n");
            result.append(min).append(" Version: ").append(jvmImplementation.version).append("\n");
            result.append(maj).append(" Operating system\n");
            result.append(min).append(" Name:         ").append(System.getProperty("os.name")).append("\n");
            result.append(min).append(" Architecture: ").append(System.getProperty("os.arch")).append("\n");
            result.append(min).append(" Version:      ").append(System.getProperty("os.version")).append("\n");

            result.append(min).append(" Distribution: ").append(distribution).append("\n");
            result.append(min).append(" LSB Modules:  `").append(LSB_modules).append("`\n");
            result.append(maj).append(" Process ID: ").append(processID).append(" (0x").append(Integer.toHexString(processID)).append(")\n");
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ");
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            result.append(maj).append(" Process started: ").append(f.format(process_started)).append('\n');
            result.append(maj).append(" Process uptime: ").append(process_uptime).append('\n');
            result.append(maj).append(" JVM startup parameters:\n");
            result.append(min).append(" Boot classpath: `").append(jvmStartupParameters.boot_classpath.replaceAll("`", "&#96;")).append("`\n");
            result.append(min).append(" Classpath: `").append(jvmStartupParameters.classPath.replaceAll("`", "&#96;")).append("`\n");
            result.append(min).append(" Library path: `").append(jvmStartupParameters.libraryClasspath.replaceAll("`", "&#96;")).append("`\n");
            int count = 0;
            for (String arg : jvmStartupParameters.args) {
                result.append(min).append(" arg[").append(count++).append("]: `").append(arg.replaceAll("`", "&#96;")).append("`\n");
            }
            return result.toString();
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
        out.println("  * Servlet container");
        out.println("      - Specification: " + versionDetails.getContainer().getSpecification());
        out.println("      - Name:          `" + versionDetails.getContainer().getName().replaceAll("`", "&#96;") + "`");
        out.print(versionDetails.toMarkdown("  *", "      -"));
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