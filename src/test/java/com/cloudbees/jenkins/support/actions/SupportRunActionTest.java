package com.cloudbees.jenkins.support.actions;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.SupportTestUtils;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Item;
import hudson.model.Job;
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
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Allan Burdajewicz
 */
@WithJenkins
class SupportRunActionTest {

    @Test
    void onlyAdminCanSeeAction(JenkinsRule j) throws Exception {

        WorkflowJob p = j.createProject(WorkflowJob.class, "testPipeline");
        p.setDefinition(new CpsFlowDefinition("node { echo 'test' }", true));
        WorkflowRun r = Optional.ofNullable(p.scheduleBuild2(0))
                .orElseThrow(AssertionFailedError::new)
                .waitForStart();
        j.waitForCompletion(r);
        SupportRunAction rAction = new SupportRunAction(r);

        SupportTestUtils.testPermissionToSeeAction(
                j,
                r.getUrl(),
                rAction,
                Stream.of(Jenkins.ADMINISTER).collect(Collectors.toSet()),
                Stream.of(Jenkins.READ, Item.READ, SupportPlugin.CREATE_BUNDLE).collect(Collectors.toSet()));

        SupportTestUtils.testPermissionToDisplayAction(
                j,
                r.getUrl(),
                rAction,
                Stream.of(Jenkins.ADMINISTER).collect(Collectors.toSet()),
                Stream.of(Jenkins.READ, Item.READ, SupportPlugin.CREATE_BUNDLE).collect(Collectors.toSet()));
    }

    /*
     * Integration test that simulates the user action of clicking the button to generate the bundle.
     */
    @Test
    void generateBundleDefaultsAndCheckContent(JenkinsRule j) throws Exception {
        MockFolder folder = j.createFolder("testFolder");
        FreeStyleProject p = folder.createProject(FreeStyleProject.class, "testFreestyle");
        QueueTaskFuture<FreeStyleBuild> freeStyleBuildQueueTaskFuture = p.scheduleBuild2(0);
        FreeStyleBuild fBuild = freeStyleBuildQueueTaskFuture.waitForStart();
        j.waitForCompletion(fBuild);

        // Check that the generation does not show any warnings
        ZipFile z = SupportTestUtils.generateBundleWithoutWarnings(
                fBuild.getUrl(),
                new SupportRunAction(j.jenkins
                        .getItemByFullName("testFolder/testFreestyle", Job.class)
                        .getBuildByNumber(fBuild.getNumber())),
                SupportPlugin.class,
                j.createWebClient());

        String buildsEntryPrefix = "items/testFolder/jobs/testFreestyle/builds/" + fBuild.getNumber();
        assertNotNull(z.getEntry("manifest.md"));
        assertNotNull(z.getEntry(buildsEntryPrefix + "/build.xml"));
    }
}
