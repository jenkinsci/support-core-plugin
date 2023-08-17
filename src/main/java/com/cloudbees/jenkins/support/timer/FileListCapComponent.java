package com.cloudbees.jenkins.support.timer;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.Permission;
import java.io.File;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;

/**
 * {@link Component} that attaches files inside {@link FileListCap} into a support bundle.
 *
 * @author stevenchristou
 */
public abstract class FileListCapComponent extends Component {

    /** Maximum file size to pack is 2Mb. */
    public static final int MAX_FILE_SIZE = 2 * 1000000;

    /**
     * Maximum age (in milliseconds) of log files we would bother including in a bundle.
     * Anything older is likely no longer relevant.
     */
    public static final long MAX_LOG_FILE_AGE_MS = TimeUnit.DAYS.toMillis(90);

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public ComponentCategory getCategory() {
        return ComponentCategory.PLATFORM;
    }

    public void addContents(@NonNull Container container, FileListCap fileListCap) {
        synchronized (fileListCap) {
            // while we read and put the reports into the support bundle, we don't want
            // the FileListCap to delete files. So we lock it.

            final Collection<File> files = FileUtils.listFiles(fileListCap.getFolder(), new String[] {"txt"}, false);
            long recently = System.currentTimeMillis() - MAX_LOG_FILE_AGE_MS;
            for (File f : files) {
                if (f.lastModified() > recently) {
                    container.add(new FileContent(
                            "{0}/{1}",
                            new String[] {fileListCap.getFolder().getName(), f.getName()}, f, MAX_FILE_SIZE));
                }
            }
        }
    }
}
