package com.cloudbees.jenkins.support.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.cloudbees.jenkins.support.SupportTestUtils;
import com.cloudbees.jenkins.support.api.Component;
import hudson.ExtensionList;
import hudson.model.FreeStyleProject;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * @author Allan Burdajewicz
 */
@WithJenkins
class ItemsContentTest {

    @Test
    void testItemsContent(JenkinsRule j) throws Exception {
        MockFolder testFolder1 = j.createFolder("testFolder1");
        FreeStyleProject project11 = testFolder1.createProject(FreeStyleProject.class, "testProject12");
        project11.scheduleBuild2(0);
        j.waitUntilNoActivity();
        project11.scheduleBuild2(0);
        j.waitUntilNoActivity();

        testFolder1.createProject(FreeStyleProject.class, "testProject21");

        FreeStyleProject project21 =
                j.createFolder("testFolder2").createProject(FreeStyleProject.class, "testProject21");
        project21.scheduleBuild2(0);
        j.waitUntilNoActivity();

        String itemsContentToString = SupportTestUtils.invokeComponentToString(
                ExtensionList.lookup(Component.class).get(ItemsContent.class));

        assertThat(itemsContentToString, containsString("  * `hudson.model.FreeStyleProject`"));
        assertThat(itemsContentToString, containsString("    - Number of items: 3"));
        assertThat(itemsContentToString, containsString("    - Number of builds per job: 1.0 [n=3, s=1.0]"));
        assertThat(itemsContentToString, containsString("  * `org.jvnet.hudson.test.MockFolder`"));
        assertThat(itemsContentToString, containsString("    - Number of items: 2"));
        assertThat(itemsContentToString, containsString("    - Number of items per container: 1.5 [n=2, s=0.7]"));
        assertThat(itemsContentToString, containsString("  * Number of jobs: 3"));
        assertThat(itemsContentToString, containsString("  * Number of builds per job: 1.0 [n=3, s=1.0]"));
    }
}
