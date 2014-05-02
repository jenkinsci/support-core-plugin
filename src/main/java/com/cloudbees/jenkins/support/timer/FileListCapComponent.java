package com.cloudbees.jenkins.support.timer;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import hudson.security.Permission;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * {@link Component} that writes out fields inside {@link FileListCap}.
 *
 * @author stevenchristou
 */
public abstract class FileListCapComponent extends Component {
    final int MAX_FILE_SIZE = 2 * 1000000; // 2 Megabytes max file size.

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    public void addContents(@NonNull Container container, FileListCap fileListCap) {
        synchronized (fileListCap) {
            // while we read and put the reports into the support bundle, we don't want
            // the FileListCap to delete files. So we lock it.

            File[] files = fileListCap.getFolder().listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".txt");
                }
            });
            for (File f : Util.fixNull(Arrays.asList(files))) {
                if (f.length() < MAX_FILE_SIZE)
                    container.add(new FileContent(fileListCap.getFolder().getName() + "/" + f.getName(), f));
                else
                    LOGGER.log(Level.INFO, "File : " + f.getAbsolutePath()
                            + " is too large. Max file size is " + MAX_FILE_SIZE + " bytes.");
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(FileListCapComponent.class.getName());
}
