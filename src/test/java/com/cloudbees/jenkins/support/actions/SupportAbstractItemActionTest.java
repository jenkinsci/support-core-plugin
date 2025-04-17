package com.cloudbees.jenkins.support.actions;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cloudbees.hudson.plugins.folder.Folder;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.queue.QueueTaskFuture;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import jenkins.model.Jenkins;
import junit.framework.AssertionFailedError;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Allan Burdajewicz
 */
@WithJenkins
class SupportAbstractItemActionTest {

    @Test
    void onlyAdminCanSeeAction(JenkinsRule j) throws Exception {
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
    void generateFolderBundleDefaultsAndCheckContent(JenkinsRule j) throws Exception {
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
                z.getEntry(itemEntryPrefix + "/jobs/subFolder/jobs/testFreestyle/config.xml"),
                "'**/jobs/**' should be excluded by default");
    }

    /*
     * Integration test that simulates the user action of clicking the button to generate the bundle from a Freestyle job.
     */
    @Test
    void generateFreestyleBundleDefaultsAndCheckContent(JenkinsRule j) throws Exception {
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
    void generatePipelineBundleDefaultsAndCheckContent(JenkinsRule j) throws Exception {
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
        assertThat(
                Collections.list(z.entries()).stream().map(ZipEntry::getName).collect(Collectors.toList()),
                hasItem(startsWith(itemEntryPrefix + "/builds/1/workflow")));
    }
}
