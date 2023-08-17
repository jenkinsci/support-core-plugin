package com.cloudbees.jenkins.support.actions;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.queue.QueueTaskFuture;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import jenkins.model.Jenkins;
import junit.framework.AssertionFailedError;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Allan Burdajewicz
 */
public class SupportAbstractItemActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void onlyAdminCanSeeAction() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "testPipeline");
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        SupportAbstractItemAction pAction =
                new SupportAbstractItemAction(j.jenkins.getItemByFullName("testPipeline", WorkflowJob.class));

        SupportTestUtils.testPermissionToSeeAction(
                j,
                p.getUrl(),
                pAction,
                Stream.of(Jenkins.ADMINISTER).collect(Collectors.toSet()),
                Stream.of(Jenkins.READ, Item.READ, SupportPlugin.CREATE_BUNDLE).collect(Collectors.toSet()));

        SupportTestUtils.testPermissionToDisplayAction(
                j,
                p.getUrl(),
                pAction,
                Stream.of(Jenkins.ADMINISTER).collect(Collectors.toSet()),
                Stream.of(Jenkins.READ, Item.READ, SupportPlugin.CREATE_BUNDLE).collect(Collectors.toSet()));
    }

    /*
     * Integration test that simulates the user action of clicking the button to generate the bundle from a Folder.
     */
    @Test
    public void generateFolderBundleDefaultsAndCheckContent() throws Exception {
        Folder folder = j.createProject(Folder.class, "testFolder");
        Folder subFolder = folder.createProject(Folder.class, "subFolder");
        subFolder.createProject(FreeStyleProject.class, "testFreestyle");

        // Check that the generation does not show any warnings
        ZipFile z = SupportTestUtils.generateBundleWithoutWarnings(
                folder.getUrl(),
                new SupportAbstractItemAction(j.jenkins.getItemByFullName("testFolder", Folder.class)),
                SupportPlugin.class,
                j.createWebClient());

        String itemEntryPrefix = "items/testFolder";
        assertNotNull(z.getEntry("manifest.md"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/config.xml"));
        assertNull(
                "'**/jobs/**' should be excluded by default",
                z.getEntry(itemEntryPrefix + "/jobs/subFolder/jobs/testFreestyle/config.xml"));
    }

    /*
     * Integration test that simulates the user action of clicking the button to generate the bundle from a Freestyle job.
     */
    @Test
    public void generateFreestyleBundleDefaultsAndCheckContent() throws Exception {
        Folder folder = j.createProject(Folder.class, "testFolder");
        FreeStyleProject p = folder.createProject(FreeStyleProject.class, "testFreestyle");
        QueueTaskFuture<FreeStyleBuild> freeStyleBuildQueueTaskFuture = p.scheduleBuild2(0);
        FreeStyleBuild fBuild = freeStyleBuildQueueTaskFuture.waitForStart();
        j.waitForCompletion(fBuild);

        // Check that the generation does not show any warnings
        ZipFile z = SupportTestUtils.generateBundleWithoutWarnings(
                p.getUrl(),
                new SupportAbstractItemAction(
                        j.jenkins.getItemByFullName("testFolder/testFreestyle", FreeStyleProject.class)),
                SupportPlugin.class,
                j.createWebClient());

        String itemEntryPrefix = "items/testFolder/jobs/testFreestyle";
        assertNotNull(z.getEntry("manifest.md"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/config.xml"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/nextBuildNumber"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/builds/1/build.xml"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/builds/1/log"));
    }

    /*
     * Integration test that simulates the user action of clicking the button to generate the bundle from a Pipeline job.
     */
    @Test
    public void generatePipelineBundleDefaultsAndCheckContent() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "testPipeline");
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        WorkflowRun workflowRun = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        j.waitForCompletion(workflowRun);

        // Check that the generation does not show any warnings
        ZipFile z = SupportTestUtils.generateBundleWithoutWarnings(
                p.getUrl(),
                new SupportAbstractItemAction(j.jenkins.getItemByFullName("testPipeline", WorkflowJob.class)),
                SupportPlugin.class,
                j.createWebClient());

        String itemEntryPrefix = "items/testPipeline";
        assertNotNull(z.getEntry("manifest.md"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/config.xml"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/builds/1/build.xml"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/builds/1/workflow/2.xml"));
    }
}
