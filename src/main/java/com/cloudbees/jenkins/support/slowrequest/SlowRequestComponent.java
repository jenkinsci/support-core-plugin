package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.StringContent;
import com.google.inject.Inject;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.Util;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Contributes slow request reports into the support bundle.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestComponent extends Component {
    @Inject
    SlowRequestChecker checker;

    @NonNull
    @Override
    public String getDisplayName() {
        return "Slow Request Records";
    }

    @Override
    public void addContents(@NonNull Container container) {
        synchronized (checker.logs) {
            // while we read and put the reports into the support bundle, we don't want
            // the FileListCap to delete files. So we lock it.

            File[] files = checker.logs.getFolder().listFiles(new FilenameFilter() {
                public boolean accept(File dir, String name) {
                    return name.endsWith(".txt");
                }
            });
            for (File f : Util.fixNull(Arrays.asList(files))) {
                try {
                    container.add(new StringContent("slow-requests/" + f.getName(),
                            FileUtils.readFileToString(f)));
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to read "+f, e);
                }
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SlowRequestComponent.class.getName());
}
