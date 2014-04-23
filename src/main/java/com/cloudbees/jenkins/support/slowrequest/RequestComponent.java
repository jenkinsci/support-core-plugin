package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.StringContent;
import com.cloudbees.jenkins.support.timer.FileListCap;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Util;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author stevenchristou
 *         Date: 4/23/14
 *         Time: 4:27 PM
 */
public abstract class RequestComponent extends Component {

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
                try {
                    container.add(new StringContent(fileListCap.getFolder().getName() + "/" + f.getName(),
                            FileUtils.readFileToString(f)));
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to read "+f, e);
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(RequestComponent.class.getName());
}
