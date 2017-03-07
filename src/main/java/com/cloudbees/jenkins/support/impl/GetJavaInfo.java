package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.model.About;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.Util;
import hudson.util.IOUtils;
import jenkins.security.MasterToSlaveCallable;

import java.io.File;
import java.io.IOException;
import java.lang.management.*;
import java.text.DecimalFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Created by schristou88 on 2/10/17.
 */
class GetJavaInfo extends MasterToSlaveCallable<About.VersionDetails, RuntimeException> {
    private static final long serialVersionUID = 1L;
    GetJavaInfo() {}

    @SuppressWarnings("DMI_HARDCODED_ABSOLUTE_FILENAME")
    public About.VersionDetails call() throws RuntimeException {
        About.VersionDetails vd = new About.VersionDetails();


        Runtime runtime = Runtime.getRuntime();
        vd.getJava().setHome(System.getProperty("java.home"));
        vd.getJava().setVendor(System.getProperty("java.vendor"));
        vd.getJava().setVersion(System.getProperty("java.version"));

        long maxMem = runtime.maxMemory();
        long allocMem = runtime.totalMemory();
        long freeMem = runtime.freeMemory();

        vd.getJava().setMaximum_memory(maxMem);
        vd.getJava().setAllocated_memory(allocMem);
        vd.getJava().setFree_memory(freeMem);
        vd.getJava().setIn_use_memory(allocMem - freeMem);

        for(MemoryPoolMXBean bean : ManagementFactory.getMemoryPoolMXBeans()) {
            if (bean.getName().toLowerCase().contains("perm gen")) {
                MemoryUsage currentUsage = bean.getUsage();
                vd.getJava().setPermgen_used(currentUsage.getUsed());
                vd.getJava().setPermgen_max(currentUsage.getMax());
                break;
            }
        }

        for(MemoryManagerMXBean bean : ManagementFactory.getMemoryManagerMXBeans()) {
            if (bean.getName().contains("MarkSweepCompact")) {
                vd.getJava().setGc_strategy("SerialGC");
                break;
            }
            if (bean.getName().contains("ConcurrentMarkSweep")) {
                vd.getJava().setGc_strategy("ConcMarkSweepGC");
                break;
            }
            if (bean.getName().contains("PS")) {
                vd.getJava().setGc_strategy("ParallelGC");
                break;
            }
            if (bean.getName().contains("G1")) {
                vd.getJava().setGc_strategy("G1");
                break;
            }
        }

        vd.getJavaRuntimeSpecification().setName(System.getProperty("java.specification.name"));
        vd.getJavaRuntimeSpecification().setVendor(System.getProperty("java.specification.vendor"));
        vd.getJavaRuntimeSpecification().setVersion(System.getProperty("java.specification.version"));

        vd.getJvmSpecification().setName(System.getProperty("java.vm.specification.name"));
        vd.getJvmSpecification().setVendor(System.getProperty("java.vm.specification.vendor"));
        vd.getJvmSpecification().setVersion(System.getProperty("java.vm.specification.version"));

        vd.getJvmImplementation().setName(System.getProperty("java.vm.name"));
        vd.getJvmImplementation().setVendor(System.getProperty("java.vm.vendor"));
        vd.getJvmImplementation().setVendor(System.getProperty("java.vm.version"));

        vd.getOperatingSystem().setName(System.getProperty("os.name"));
        vd.getOperatingSystem().setArchitecture(System.getProperty("os.arch"));
        vd.getOperatingSystem().setVersion(System.getProperty("os.version"));

        File lsb_release = new File("/usr/bin/lsb_release");
        if (lsb_release.canExecute()) {
            try {
                Process proc = new ProcessBuilder().command(lsb_release.getAbsolutePath(), "--description", "--short").start();
                String distro = IOUtils.readFirstLine(proc.getInputStream(), "UTF-8");
                if (proc.waitFor() == 0) {
                    vd.setDistribution(distro);
                } else {
                    LOGGER.fine("lsb_release had a nonzero exit status");
                }
                proc = new ProcessBuilder().command(lsb_release.getAbsolutePath(), "--version", "--short").start();
                String modules = IOUtils.readFirstLine(proc.getInputStream(), "UTF-8");
                if (proc.waitFor() == 0 && modules != null) {
                    vd.setLSB_modules(modules);
                } else {
                    LOGGER.fine("lsb_release had a nonzero exit status");
                }
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, "lsb_release exists but could not run it", x);
            } catch (InterruptedException x) {
                LOGGER.log(Level.WARNING, "lsb_release hung", x);
            }
        }

        RuntimeMXBean mBean = ManagementFactory.getRuntimeMXBean();
        String process = mBean.getName();
        Matcher processMatcher = Pattern.compile("^(-?[0-9]+)@.*$").matcher(process);
        if (processMatcher.matches()) {
            int processId = Integer.parseInt(processMatcher.group(1));
            vd.setProcessID(processId);
        }


        vd.setProcess_started(new Date(mBean.getStartTime()));
        vd.setProcess_uptime(Util.getTimeSpanString(mBean.getUptime()));


        if (mBean.isBootClassPathSupported()) {
            vd.getJvmStartupParameters().setBoot_classpath(mBean.getBootClassPath());
        }

        vd.getJvmStartupParameters().setClassPath(mBean.getClassPath());
        vd.getJvmStartupParameters().setLibraryClasspath(mBean.getLibraryPath());

        for (String arg : mBean.getInputArguments()) {
            vd.getJvmStartupParameters().addArg(arg);
        }
        return vd;
    }

    private static final Logger LOGGER = Logger.getLogger(GetJavaInfo.class.getCanonicalName());
}