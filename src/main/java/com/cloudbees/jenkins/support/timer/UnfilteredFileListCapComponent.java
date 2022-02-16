package com.cloudbees.jenkins.support.timer;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.UnfilteredFileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * {@link Component} that attaches files inside {@link FileListCap} into a support bundle without filtering the
 * content of the files.
 *
 * @author stevenchristou
 */
public abstract class UnfilteredFileListCapComponent extends Component {

    /** Maximum file size to pack is 2Mb. */
    public static final int MAX_FILE_SIZE = 2 * 1000000;

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    public void addContents(@NonNull Container container, FileListCap fileListCap) {
        synchronized (fileListCap) {
            // while we read and put the reports into the support bundle, we don't want
            // the FileListCap to delete files. So we lock it.

            final List<File> files = new ArrayList<>(FileUtils.listFiles(
                    fileListCap.getFolder(), new String[] {"txt"}, false));
            Collections.sort(files);
            long recently = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90);
            for (File f : files) {
                if (f.lastModified() > recently) {
                    container.add(new UnfilteredFileContent("{0}/{1}", new String[]{fileListCap.getFolder().getName(), f.getName()}, f, MAX_FILE_SIZE));
                }
            }
        }
    }

}
