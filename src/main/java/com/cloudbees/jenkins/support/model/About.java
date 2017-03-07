package com.cloudbees.jenkins.support.model;

import java.io.PrintWriter;
import java.io.Serializable;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Created by schristou88 on 2/9/17.
 */
public class About implements Serializable, MarkdownFile {
    VersionDetails versionDetails = new VersionDetails();
    ImportantConfiguration importantConfiguration = new ImportantConfiguration();
    ActivePlugins activePlugins = new ActivePlugins();
    PackagingDetails packagingDetails = new PackagingDetails();

    public VersionDetails getVersionDetails() {
        return versionDetails;
    }

    public void setVersionDetails(VersionDetails versionDetails) {
        this.versionDetails = versionDetails;
    }

    public ImportantConfiguration getImportantConfiguration() {
        return importantConfiguration;
    }

    public void setImportantConfiguration(ImportantConfiguration importantConfiguration) {
        this.importantConfiguration = importantConfiguration;
    }

    public ActivePlugins getActivePlugins() {
        return activePlugins;
    }

    public void setActivePlugins(ActivePlugins activePlugins) {
        this.activePlugins = activePlugins;
    }

    public PackagingDetails getPackagingDetails() {
        return packagingDetails;
    }

    public void setPackagingDetails(PackagingDetails packagingDetails) {
        this.packagingDetails = packagingDetails;
    }

    public static class VersionDetails implements Serializable{
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

        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public ServletContainer getContainer() {
            return container;
        }

        public void setContainer(ServletContainer container) {
            this.container = container;
        }

        public Java getJava() {
            return java;
        }

        public void setJava(Java java) {
            this.java = java;
        }

        public JavaRuntimeSpecification getJavaRuntimeSpecification() {
            return javaRuntimeSpecification;
        }

        public void setJavaRuntimeSpecification(JavaRuntimeSpecification javaRuntimeSpecification) {
            this.javaRuntimeSpecification = javaRuntimeSpecification;
        }

        public JVMSpecification getJvmSpecification() {
            return jvmSpecification;
        }

        public void setJvmSpecification(JVMSpecification jvmSpecification) {
            this.jvmSpecification = jvmSpecification;
        }

        public JVMImplementation getJvmImplementation() {
            return jvmImplementation;
        }

        public void setJvmImplementation(JVMImplementation jvmImplementation) {
            this.jvmImplementation = jvmImplementation;
        }

        public OperatingSystem getOperatingSystem() {
            return operatingSystem;
        }

        public void setOperatingSystem(OperatingSystem operatingSystem) {
            this.operatingSystem = operatingSystem;
        }

        public int getProcessID() {
            return processID;
        }

        public void setProcessID(int processID) {
            this.processID = processID;
        }

        public Date getProcess_started() {
            return new Date(process_started.getTime());
        }

        public void setProcess_started(Date process_started) {
            this.process_started = new Date(process_started.getTime());
        }

        public String getProcess_uptime() {
            return process_uptime;
        }

        public void setProcess_uptime(String process_uptime) {
            this.process_uptime = process_uptime;
        }

        public JVMStartupParameters getJvmStartupParameters() {
            return jvmStartupParameters;
        }

        public void setJvmStartupParameters(JVMStartupParameters jvmStartupParameters) {
            this.jvmStartupParameters = jvmStartupParameters;
        }

        public String getDistribution() {
            return distribution;
        }

        public void setDistribution(String distribution) {
            this.distribution = distribution;
        }

        public String getLSB_modules() {
            return LSB_modules;
        }

        public void setLSB_modules(String LSB_modules) {
            this.LSB_modules = LSB_modules;
        }

        public static class ServletContainer implements Serializable {
            String specification;
            String name;

            public String getSpecification() {
                return specification;
            }

            public void setSpecification(String specification) {
                this.specification = specification;
            }

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }
        }


        public static class Java implements Serializable{
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

            public String getHome() {
                return home;
            }

            public void setHome(String home) {
                this.home = home;
            }

            public String getVendor() {
                return vendor;
            }

            public void setVendor(String vendor) {
                this.vendor = vendor;
            }

            public String getVersion() {
                return version;
            }

            public void setVersion(String version) {
                this.version = version;
            }

            public long getMaximum_memory() {
                return maximum_memory;
            }

            public void setMaximum_memory(long maximum_memory) {
                this.maximum_memory = maximum_memory;
            }

            public long getAllocated_memory() {
                return allocated_memory;
            }

            public void setAllocated_memory(long allocated_memory) {
                this.allocated_memory = allocated_memory;
            }

            public long getFree_memory() {
                return free_memory;
            }

            public void setFree_memory(long free_memory) {
                this.free_memory = free_memory;
            }

            public long getIn_use_memory() {
                return in_use_memory;
            }

            public void setIn_use_memory(long in_use_memory) {
                this.in_use_memory = in_use_memory;
            }

            public String getGc_strategy() {
                return gc_strategy;
            }

            public void setGc_strategy(String gc_strategy) {
                this.gc_strategy = gc_strategy;
            }

            public long getPermgen_used() {
                return permgen_used;
            }

            public void setPermgen_used(long permgen_used) {
                this.permgen_used = permgen_used;
            }

            public long getPermgen_max() {
                return permgen_max;
            }

            public void setPermgen_max(long permgen_max) {
                this.permgen_max = permgen_max;
            }
        }


        public static class JavaRuntimeSpecification implements Serializable {
            String name;
            String vendor;
            String version;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getVendor() {
                return vendor;
            }

            public void setVendor(String vendor) {
                this.vendor = vendor;
            }

            public String getVersion() {
                return version;
            }

            public void setVersion(String version) {
                this.version = version;
            }
        }


        public static class JVMSpecification implements Serializable {
            String name;
            String vendor;
            String version;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getVendor() {
                return vendor;
            }

            public void setVendor(String vendor) {
                this.vendor = vendor;
            }

            public String getVersion() {
                return version;
            }

            public void setVersion(String version) {
                this.version = version;
            }
        }


        public static class JVMImplementation implements Serializable {
            String name;
            String vendor;
            String version;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getVendor() {
                return vendor;
            }

            public void setVendor(String vendor) {
                this.vendor = vendor;
            }

            public String getVersion() {
                return version;
            }

            public void setVersion(String version) {
                this.version = version;
            }
        }


        public static class OperatingSystem implements Serializable {
            String name;
            String architecture;
            String version;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getArchitecture() {
                return architecture;
            }

            public void setArchitecture(String architecture) {
                this.architecture = architecture;
            }

            public String getVersion() {
                return version;
            }

            public void setVersion(String version) {
                this.version = version;
            }
        }


        public static class JVMStartupParameters implements Serializable {
            String boot_classpath;
            String classPath;
            String libraryClasspath;
            List<String> args = new ArrayList<>();

            public JVMStartupParameters addArg(String arg) {
                this.args.add(arg);
                return this;
            }

            public String getBoot_classpath() {
                return boot_classpath;
            }

            public void setBoot_classpath(String boot_classpath) {
                this.boot_classpath = boot_classpath;
            }

            public String getClassPath() {
                return classPath;
            }

            public void setClassPath(String classPath) {
                this.classPath = classPath;
            }

            public String getLibraryClasspath() {
                return libraryClasspath;
            }

            public void setLibraryClasspath(String libraryClasspath) {
                this.libraryClasspath = libraryClasspath;
            }

            public List<String> getArgs() {
                return args;
            }

            public void setArgs(List<String> args) {
                this.args = args;
            }
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
            result.append(min).append(" Name:         ").append(operatingSystem.name).append("\n");
            result.append(min).append(" Architecture: ").append(operatingSystem.architecture).append("\n");
            result.append(min).append(" Version:      ").append(operatingSystem.version).append("\n");

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
    }


    public static class ImportantConfiguration implements Serializable {
        String securityRealm;
        String authorizationStrategy;
        boolean CSRF_protection;
        String initializationMilestone;

        public String getSecurityRealm() {
            return securityRealm;
        }

        public void setSecurityRealm(String securityRealm) {
            this.securityRealm = securityRealm;
        }

        public String getAuthorizationStrategy() {
            return authorizationStrategy;
        }

        public void setAuthorizationStrategy(String authorizationStrategy) {
            this.authorizationStrategy = authorizationStrategy;
        }

        public boolean isCSRF_protection() {
            return CSRF_protection;
        }

        public void setCSRF_protection(boolean CSRF_protection) {
            this.CSRF_protection = CSRF_protection;
        }

        public String getInitializationMilestone() {
            return initializationMilestone;
        }

        public void setInitializationMilestone(String initializationMilestone) {
            this.initializationMilestone = initializationMilestone;
        }
    }


    public static class ActivePlugins implements Serializable {
        List<Plugin> activePlugins = new ArrayList<>();

        public ActivePlugins addPlugin(Plugin plugin) {
            this.activePlugins.add(plugin);
            return this;
        }

        public List<Plugin> getActivePlugins() {
            return activePlugins;
        }

        public void setActivePlugins(List<Plugin> activePlugins) {
            this.activePlugins = activePlugins;
        }

        public static class Plugin implements Serializable {
            String name;
            String version;
            boolean updates_available;
            String description;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public String getVersion() {
                return version;
            }

            public void setVersion(String version) {
                this.version = version;
            }

            public boolean isUpdates_available() {
                return updates_available;
            }

            public void setUpdates_available(boolean updates_available) {
                this.updates_available = updates_available;
            }

            public String getDescription() {
                return description;
            }

            public void setDescription(String description) {
                this.description = description;
            }
        }
    }


    public static class PackagingDetails implements Serializable {
        String details;

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }
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