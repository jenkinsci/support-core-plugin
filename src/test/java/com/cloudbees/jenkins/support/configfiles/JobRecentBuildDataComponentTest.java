/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.support.configfiles;

import com.cloudbees.jenkins.support.api.ItemComponentDescriptor;
import com.cloudbees.jenkins.support.util.TestContainer;
import hudson.Functions;
import hudson.model.AbstractItem;
import hudson.model.FreeStyleProject;
import hudson.model.TopLevelItem;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertThat;

public class JobRecentBuildDataComponentTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void generateForJob() throws Exception {
        FreeStyleProject job = r.createFreeStyleProject("job1");
        job.getBuildersList().add(Functions.isWindows()
                ? new BatchFile("echo \"Running build #%BUILD_NUMBER%\"")
                : new Shell("echo \"Running build #$BUILD_NUMBER\""));
        r.buildAndAssertSuccess(job);
        r.buildAndAssertSuccess(job);
        assertThat(generateContentsForItem(job, 2), allOf(
                hasItem(containsString("Running build #1")),
                hasItem(containsString("Running build #2"))));
    }

    private <T extends AbstractItem & TopLevelItem> List<String> generateContentsForItem(T item, int buildsToInclude) {
        assertThat("Descriptor should be applicable to the item", ItemComponentDescriptor.getDescriptors(item),
                hasItem(instanceOf(JobRecentBuildDataComponent.DescriptorImpl.class)));
        JobRecentBuildDataComponent component = new JobRecentBuildDataComponent();
        component.setItem(item);
        component.setRecentBuildsToInclude(buildsToInclude);
        TestContainer container = new TestContainer();
        component.addContents(container);
        return container.getContents();
    }

}
