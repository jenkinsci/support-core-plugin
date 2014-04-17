package com.cloudbees.jenkins.support.slowrequest;

import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

import java.io.File;
import java.io.FilenameFilter;
import foo;

/**
 * @author Kohsuke Kawaguchi
 */
@Extension
public class SlowRequestComponent extends Component {
    @NonNull
    @Override
    public String getDisplayName() {
        return "Slow Request Records";
    }

    @Override
    public void addContents(@NonNull Container container) {
        result.add(new FileContent("other-logs/" + f.getName(), f));


        File f;
        f.list(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                return false;
            }
        })
    }
}
