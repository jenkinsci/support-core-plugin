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

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    public void addContents(@NonNull Container container, FileListCap fileListCap) {
        synchronized (fileListCap) {
            // while we read and put the reports into the support bundle, we don't want
            // the FileListCap to delete files. So we lock it.

            final Collection<File> files = FileUtils.listFiles(
                    fileListCap.getFolder(), new String[] {"txt"}, false);
            for (File f : files) {
                container.add(new FileContent(fileListCap.getFolder().getName() + "/" + f.getName(), f, MAX_FILE_SIZE));
            }
        }
    }

}
