package com.cloudbees.jenkins.support.actions;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.queue.QueueTaskFuture;
import junit.framework.AssertionFailedError;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

import java.util.Optional;
import java.util.zip.ZipFile;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * @author Allan Burdajewicz
 */
public class SupportAbstractItemActionTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Integration test that simulates the user action of clicking the button to generate the bundle from a Folder.
     */
    @Test
    public void generateFolderBundleDefaultsAndCheckContent() throws Exception {

        MockFolder folder = j.createFolder("testFolder");
        MockFolder subFolder = folder.createProject(MockFolder.class, "subFolder");
        subFolder.createProject(FreeStyleProject.class, "testFreestyle");

        // Check that the generation does not show any warnings
        ZipFile z = SupportTestUtils.generateBundleWithoutWarnings(folder.getUrl(),
                new SupportAbstractItemAction(j.jenkins.getItemByFullName("testFolder", MockFolder.class)),
                SupportPlugin.class,
                j.createWebClient());

        String itemEntryPrefix = "items/testFolder";
        assertNotNull(z.getEntry("manifest.md"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/config.xml"));
        assertNull("'**/jobs/**' should be excluded by default", z.getEntry(itemEntryPrefix + "/jobs/subFolder/jobs/testFreestyle/config.xml"));
        assertNull("'**/jobs/**' should be excluded by default", z.getEntry(itemEntryPrefix + "/jobs/subFolder/jobs/testFreestyle/config.xml"));
    }

    /**
     * Integration test that simulates the user action of clicking the button to generate the bundl from a Freesttle job.
     */
    @Test
    public void generateFreestyleBundleDefaultsAndCheckContent() throws Exception {

        MockFolder folder = j.createFolder("testFolder");
        FreeStyleProject p = folder.createProject(FreeStyleProject.class, "testFreestyle");
        QueueTaskFuture<FreeStyleBuild> freeStyleBuildQueueTaskFuture = p.scheduleBuild2(0);
        FreeStyleBuild fBuild = freeStyleBuildQueueTaskFuture.waitForStart();
        j.waitForCompletion(fBuild);

        // Check that the generation does not show any warnings
        ZipFile z = SupportTestUtils.generateBundleWithoutWarnings(p.getUrl(),
                new SupportAbstractItemAction(j.jenkins.getItemByFullName("testFolder/testFreestyle", MockFolder.class)),
                SupportPlugin.class,
                j.createWebClient());

        String itemEntryPrefix = "items/testFolder/testFreestyle";
        assertNotNull(z.getEntry("manifest.md"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/config.xml"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/nextBuildNumber"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/builds/1/build.xml"));
        assertNotNull(z.getEntry(itemEntryPrefix + "/builds/1/log"));
        
    }

    /**
     * Integration test that simulates the user action of clicking the button to generate the bundle from a Folder.
     */
    @Test
    public void generatePipelineBundleDefaultsAndCheckContent() throws Exception {

        WorkflowJob p = j.createProject(WorkflowJob.class, "testPipeline");
        p.setDefinition(new CpsFlowDefinition("node {semaphore 'wait'}", true));
        Optional<QueueTaskFuture<WorkflowRun>> optionalWorkflowRunQueueTaskFuture = Optional.ofNullable(p.scheduleBuild2(0));
        QueueTaskFuture<WorkflowRun> workflowRunQueueTaskFuture = optionalWorkflowRunQueueTaskFuture
                .orElseThrow(AssertionFailedError::new);
        WorkflowRun workflowRun = workflowRunQueueTaskFuture.waitForStart();
        SemaphoreStep.success("wait/1", workflowRun);

        // Check that the generation does not show any warnings
        ZipFile z = SupportTestUtils.generateBundleWithoutWarnings(p.getUrl(),
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
