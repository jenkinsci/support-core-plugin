package com.cloudbees.jenkins.support.actions;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Job;
import hudson.model.queue.QueueTaskFuture;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

import java.util.zip.ZipFile;

import static org.junit.Assert.assertNotNull;

/**
 * @author Allan Burdajewicz
 */
public class SupportRunActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Integration test that simulates the user action of clicking the button to generate the bundle.
     */
    @Test
    public void generateBundleDefaultsAndCheckContent() throws Exception {

        MockFolder folder = j.createFolder("testFolder");
        FreeStyleProject p = folder.createProject(FreeStyleProject.class, "testFreestyle");
        QueueTaskFuture<FreeStyleBuild> freeStyleBuildQueueTaskFuture = p.scheduleBuild2(0);
        FreeStyleBuild fBuild = freeStyleBuildQueueTaskFuture.waitForStart();
        j.waitForCompletion(fBuild);

        // Check that the generation does not show any warnings
        ZipFile z = SupportTestUtils.generateBundleWithoutWarnings(
                fBuild.getUrl(),
                new SupportRunAction(j.jenkins.getItemByFullName("testFolder/testFreestyle", Job.class)
                        .getBuildByNumber(fBuild.number)),
                SupportPlugin.class,
                j.createWebClient());
        
        String buildsEntryPrefix = "items/testFolder/testFreestyle/builds/" + fBuild.number;
        assertNotNull(z.getEntry("manifest.md"));
        assertNotNull(z.getEntry(buildsEntryPrefix + "/build.xml"));
    }
}
