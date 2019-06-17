package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.configfiles.AgentsConfigFile;
import com.cloudbees.jenkins.support.configfiles.ConfigFileComponent;
import com.cloudbees.jenkins.support.filter.ContentFilter;
import com.cloudbees.jenkins.support.filter.ContentFilters;
import com.cloudbees.jenkins.support.impl.AboutJenkins;
import com.cloudbees.jenkins.support.impl.AboutUser;
import com.cloudbees.jenkins.support.impl.BuildQueue;
import com.cloudbees.jenkins.support.impl.DumpExportTable;
import com.cloudbees.jenkins.support.impl.EnvironmentVariables;
import com.cloudbees.jenkins.support.impl.JVMProcessSystemMetricsContents;
import com.cloudbees.jenkins.support.impl.NetworkInterfaces;
import com.cloudbees.jenkins.support.impl.NodeMonitors;
import com.cloudbees.jenkins.support.impl.SystemConfiguration;
import com.cloudbees.jenkins.support.impl.SystemProperties;
import com.cloudbees.jenkins.support.impl.ThreadDumps;
import com.cloudbees.jenkins.support.impl.UpdateCenter;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.User;
import hudson.model.labels.LabelAtom;
import hudson.model.queue.QueueTaskFuture;
import hudson.tasks.Builder;
import jenkins.model.Jenkins;
import jenkins.tasks.SimpleBuildStep;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.Nonnull;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;

public class CheckFilterTest {
    private final static Logger LOGGER = Logger.getLogger(CheckFilterTest.class.getName());

    private final static String JOB_NAME = "thejob";
    private final static String SLAVE_NAME = "slave0"; //it's the name used by createOnlineSlave
    private final static String VIEW_ALL_NEW_NAME = "all-view";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void checkFilterTest() throws Exception {
        // Create the files to check
        FileChecker checker = new FileChecker(jenkins.jenkins);
        // Create the objects needed for some contents to be included
        QueueTaskFuture<FreeStyleBuild> build = createObjectsWithNames();

        // Reload the mappings, after the objects were created
        ContentFilters.get().setEnabled(true);
        ContentFilter.ALL.reload();
        // Generate the filtered words
        checker.generateFilteredWords();

        // Check the components are filtered correctly
        assertComponent(Arrays.asList(  AboutJenkins.class,
                                        AboutUser.class,
                                        AgentsConfigFile.class,
                                        BuildQueue.class,
                                        ConfigFileComponent.class,
                                        DumpExportTable.class,
                                        EnvironmentVariables.class,
                                        JVMProcessSystemMetricsContents.Agents.class,
                                        JVMProcessSystemMetricsContents.Master.class,
                                        SystemConfiguration.class,
                                        NetworkInterfaces.class,
                                        NodeMonitors.class,
                                        UpdateCenter.class,
                                        SystemProperties.class,
                                        ThreadDumps.class
                                    ), checker);

        // Cancel the job running
        build.cancel(true);
    }

    private QueueTaskFuture<FreeStyleBuild> createObjectsWithNames() throws Exception {
        // For an environment variable
        User.getOrCreateByIdOrFullName("path");

        // For JVMProcessSytemMetricsContents
        User.getOrCreateByIdOrFullName("kb");
        User.getOrCreateByIdOrFullName("mb");
        User.getOrCreateByIdOrFullName("max");
        User.getOrCreateByIdOrFullName("mounted");

        // ThreadDumps
        User.getOrCreateByIdOrFullName("runnable");

        // For SystemProperties
        User.getOrCreateByIdOrFullName("encoding");

        // For ConfigFileComponent --> config.xml
        jenkins.jenkins.getView("all").rename(VIEW_ALL_NEW_NAME);
        jenkins.jenkins.save();

        // Create the slave slave0 for some components
        jenkins.createOnlineSlave();

        // Create a job to have something pending in the queue for the BuilQueue component
        FreeStyleProject project = jenkins.createFreeStyleProject(JOB_NAME);
        project.setAssignedLabel(new LabelAtom("foo")); //it's mandatory

        //project.getBuildersList().add(new SimpleBuilder());
        QueueTaskFuture<FreeStyleBuild> buildQueued = project.scheduleBuild2(0);
        return buildQueued;
    }

    private void assertComponent(List<Class<? extends Component>> componentClasses, FileChecker checker) throws IOException {
        File fileZip = new File(temp.getRoot(), "filteredBundle.zip");
        Files.deleteIfExists(fileZip.toPath());

        try (FileOutputStream zipOutputStream = new FileOutputStream(fileZip)) {
            ExtensionList<Component> existingComponents = ExtensionList.lookup(Component.class);
            List<Component> componentsRequested = existingComponents.stream().filter(existingComponent -> componentClasses.contains(existingComponent.getClass())).collect(Collectors.toList());
            SupportPlugin.writeBundle(zipOutputStream, componentsRequested);

            //ZipArchiveInputStream zip = new ZipArchiveInputStream(new FileInputStream(fileZip));
            try(ZipFile zip = new ZipFile(fileZip)) {
                Enumeration<ZipArchiveEntry> zipFileEntries = zip.getEntries();

                while (zipFileEntries.hasMoreElements()) {
                    ZipArchiveEntry entry = zipFileEntries.nextElement();
                    if (entry.isDirectory()) {
                        break;
                    }
                    if (entry.getSize() > 2048 * 1024) {
                        LOGGER.log(Level.WARNING, "Cannot check file '%s' because its content is bigger than 2Mb", entry.getName());
                        break;
                    }

                    String content = getContentFromEntry(zip, entry);
                    checker.check(entry.getName(), content);
                }
            }
        }
    }

    private String getContentFromEntry(ZipFile zip, ZipArchiveEntry entry) throws IOException {
        if (entry.getSize() == 0) {
            return "";
        }

        String content;
        try(ByteArrayOutputStream out = new ByteArrayOutputStream();
            BufferedInputStream is = new BufferedInputStream(zip.getInputStream(entry))) {

            int currentByte;
            // buffer of same size as the entry
            byte data[] = new byte[(int) entry.getSize()];

            // read and write until last byte is encountered
            while ((currentByte = is.read(data, 0, (int) entry.getSize())) != -1) {
                out.write(data, 0, currentByte);
            }
            out.flush();
            content = new String(out.toByteArray(), Charset.forName("UTF-8"));
        }
        return content;
    }

    private static class FileChecker {
        private Set<FileToCheck> fileSet = new HashSet<>();
        private Set<String> words = new HashSet<>();


        private FileChecker(Jenkins jenkins) throws UnknownHostException {
            fileSet.add(of("manifest.md", "about", false));
            fileSet.add(of("about.md", "b", false));
            fileSet.add(of("items.md", "jobs", false));
            //checksum.md5 is not generated in tests

            //AboutJenkins -> nodes.md
            //The slave node name should be filtered
            fileSet.add(of("nodes.md", SLAVE_NAME, true));
            //AboutUser
            fileSet.add(of("user.md", "anonymous", true));

            fileSet.add(of("node-monitors.md", SLAVE_NAME, true));

            fileSet.add(of("admin-monitors.md", "diagnostics", false));
            fileSet.add(of("admin-monitors.md", "Family", false));


            fileSet.add(of("nodes/slave/*/config.xml", SLAVE_NAME, true));

            //BuildQueue -> Name of item: ...
            fileSet.add(of("buildqueue.md", JOB_NAME, true));

            //ConfigFileComponent --> jenkins-root-configuration-files/config.xml
            fileSet.add(of("jenkins-root-configuration-files/config.xml", "all-view", true));

            // DumpExportTable --> nodes/slave/*/exportTable.txt
            fileSet.add(of("nodes/slave/*/exportTable.txt", SLAVE_NAME, true));

            // EnvironmentVariables --> nodes/master/environment.txt and nodes/slave/*/environment.txt. A well known
            // and also existing in all OS environment variable
            fileSet.add(of("nodes/master/environment.txt", "path", true));
            fileSet.add(of("nodes/slave/*/environment.txt", "path", true));

//            JVMProcessSystemMetricsContents -->
//            nodes/master/proc/meminfo.txt
//            nodes/slave/*/proc/meminfo.txt
//            nodes/*/self/status.txt
//            nodes/*/self/cmdline
//            nodes/*/self/environ
//            nodes/*/self/limits.txt
//            nodes/*/self/mountstats.txt


            fileSet.add(of("nodes/master/proc/meminfo.txt", "kb", false));
            fileSet.add(of("nodes/slave/*/proc/meminfo.txt", "kb", false));

            fileSet.add(of("nodes/master/proc/self/status.txt", "kb", false));
            fileSet.add(of("nodes/slave/*/proc/self/status.txt", "kb", false));

            fileSet.add(of("nodes/master/proc/self/cmdline", "java", false));
            fileSet.add(of("nodes/slave/*/proc/self/cmdline", "java", false));

            fileSet.add(of("nodes/master/proc/self/environ", "path", false));
            fileSet.add(of("nodes/slave/*/proc/self/environ", "path", false));

            fileSet.add(of("nodes/master/proc/self/limits.txt", "max", false));
            fileSet.add(of("nodes/slave/*/proc/self/limits.txt", "max", false));

            fileSet.add(of("nodes/master/proc/self/mountstats.txt", "mounted", false));
            fileSet.add(of("nodes/slave/*/proc/self/mountstats.txt", "mounted", false));

            // SystemConfiguration -->
            // nodes/master/proc/ or nodes/slave/*/ ...
            // swaps.txt
            // cpuinfo.txt
            // mounts.txt
            // system-uptime.txt
            // net/rpc/nfs.txt
            // net/rpc/nfsd.txt
            fileSet.add(of("nodes/master/proc/swaps.txt", "cpu", false));
            fileSet.add(of("nodes/slave/*/proc/swaps.txt", "cpu", false));

            fileSet.add(of("nodes/master/proc/cpuinfo.txt", "cpu", false));
            fileSet.add(of("nodes/slave/*/proc/cpuinfo.txt", "cpu", false));

            fileSet.add(of("nodes/master/proc/mounts.txt", "cpu", false));
            fileSet.add(of("nodes/slave/*/proc/mounts.txt", "cpu", false));

            fileSet.add(of("nodes/master/proc/system-uptime.txt", "cpu", false));
            fileSet.add(of("nodes/slave/*/proc/system-uptime.txt", "cpu", false));

            fileSet.add(of("nodes/master/proc/net/rpc/nfs.txt", "cpu", false));
            fileSet.add(of("nodes/slave/*/proc/net/rpc/nfs.txt", "cpu", false));

            fileSet.add(of("nodes/master/proc/net/rpc/nfsd.txt", "cpu", false));
            fileSet.add(of("nodes/slave/*/proc/net/rpc/nfsd.txt", "cpu", false));

            //NetworkInterfaces --> nodes/master/networkInterface.md, nodes/slave/*/networkInterface.md
            String anIP = getInetAddress();
            if(anIP != null) {
                fileSet.add(of("nodes/master/networkInterface.md", anIP, true));
                fileSet.add(of("nodes/slave/*/networkInterface.md", anIP, true));
            }

            //NodeMonitors --> node-monitors.md
            fileSet.add(of("node-monitors.md", SLAVE_NAME, true));

            //UpdateCenter --> update-center.md
            if(getUpdateCenterURL(jenkins) != null) {
                fileSet.add(of("update-center.md", getUpdateCenterURL(jenkins), true));
            }

            //SystemProperties --> nodes/master/system.properties, nodes/slave/*/system.properties
            fileSet.add(of("nodes/master/system.properties", "encoding", true));
            fileSet.add(of("nodes/slave/*/system.properties", "encoding", true));

            //ThreadDumps --> nodes/slave/*/thread-dump.txt, nodes/master/thread-dump.txt
            fileSet.add(of("nodes/master/thread-dump.txt", "runnable", true));
            fileSet.add(of("nodes/slave/*/thread-dump.txt", "runnable", true));
        }

        private String getUpdateCenterURL(Jenkins jenkins) {
            if (jenkins.getUpdateCenter().getSiteList() != null && jenkins.getUpdateCenter().getSiteList().get(0) != null) {
                return jenkins.getUpdateCenter().getSiteList().get(0).getUrl();
            }
            return null;
        }

        private String getInetAddress() {
            try {
                Enumeration<NetworkInterface> networkInterfaces = null;
                networkInterfaces = NetworkInterface.getNetworkInterfaces();
                if (networkInterfaces.hasMoreElements()) {
                    NetworkInterface ni = networkInterfaces.nextElement();
                    Enumeration<InetAddress> inetAddresses = ni.getInetAddresses();
                    if (inetAddresses != null && inetAddresses.hasMoreElements()) {
                        return inetAddresses.nextElement().toString();
                    }
                }
            } catch (SocketException ignored) {
                return null;
            }
            return null;
        }

        /**
         * After creating all the contents and
         */
        private void generateFilteredWords() {
            ContentFilter filter = SupportPlugin.getContentFilter().orElse(null);
            for(FileToCheck file : fileSet) {
                file.wordFiltered = (filter != null && file.fileIsFiltered) ? filter.filter(file.word) : file.word;
            }
        }

        /**
         * A filePattern pattern. Only * is allowed between to indicate subfolders.
         * @param filePattern pattern of the file
         * @param word the word that should be in the content, filtered or not.
         * @param fileIsFiltered if this file is filtered
         * @return an object to check the content of files in the bundle that matches the pattern.
         */
        private FileToCheck of(String filePattern, String word, boolean fileIsFiltered) {
            words.add(word);
            return new FileToCheck(filePattern, word, fileIsFiltered);
        }

        /**
         * Look for a {@link FileToCheck} in the list and if one is found that matches the filePattern passed, check the
         * content. The content should have the filtered word if filtered, in other case, the plain word.
         * @param file filePattern path in the bundle
         * @param content content of this filePattern
         */
        private void check(String file, String content) {
            final int MAX_CONTENT_LENGTH = Integer.MAX_VALUE;
            for (FileToCheck value : fileSet) {
                // If there is a filePattern to check that matches this entry of the bundle, we check
                if(value.match(file)) {
                    if (content == null) {
                        fail(String.format("Error checking the file %s because its content was null", file));
                    } else {
                        Assert.assertTrue(String.format("The file '%s' should have the word '%s'. File content:\n\n----------\n%s\n%s----------\n\n", file, value.wordFiltered, content.substring(0, Math.min(MAX_CONTENT_LENGTH, content.length())), content.length() > MAX_CONTENT_LENGTH ? "...\n(content cut off)\n" : ""), content.toLowerCase(Locale.ENGLISH).contains(value.wordFiltered.toLowerCase(Locale.ENGLISH)));
                    }
                    return;
                }
            }
            // No match in the list of patterns for this file in the bundle
        }
    }

    private static class FileToCheck {
        private String filePattern;
        private String word;
        private boolean fileIsFiltered;
        private String wordFiltered;

        private FileToCheck(String filePattern, String word, boolean fileIsFiltered) {
            this.filePattern = filePattern;
            this.word = word;
            this.fileIsFiltered = fileIsFiltered;
        }

        private boolean match(String s) {
            String pattern = filePattern.replace(".", "\\.").replace("*", "[^/]+");
            return Pattern.matches(pattern, s);
        }

    }

    private static class SimpleBuilder extends Builder implements SimpleBuildStep, Serializable {
        @Override
        public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath filePath, @Nonnull Launcher launcher, @Nonnull TaskListener taskListener) throws InterruptedException, IOException {
            taskListener.getLogger().println("Doing my job, sleeping...");
            Thread.sleep(Long.MAX_VALUE);
        }
    }
}
