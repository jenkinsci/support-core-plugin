package com.cloudbees.jenkins.support.config;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

/**
 * Test for the {@link SupportAutomatedBundleConfiguration}
 * @author Allan Burdajewicz
 */
public class SupportAutomatedBundleConfigurationSystemPropertiesTest {

    @Rule
    public RealJenkinsRule rr = new RealJenkinsRule();

    @Test
    public void enforcePeriod() throws Throwable {
        rr.javaOptions().javaOptions("-D" + SupportPlugin.class.getName()
            + ".AUTO_BUNDLE_PERIOD_HOURS=2").then(r -> {
            assertThat(r.getInstance().getExtensionList(SupportAutomatedBundleConfiguration.class).get(0).getPeriod(), is(2));
            assertThat(SupportAutomatedBundleConfiguration.get().isEnforcedPeriod(), is(true));
            assertThat(SupportAutomatedBundleConfiguration.get().isEnforcedDisabled(), is(false));
            assertThat(SupportAutomatedBundleConfiguration.get().isEnabled(), is(true));
            SupportAutomatedBundleConfiguration.get().setPeriod(1);
            SupportAutomatedBundleConfiguration.get().setEnabled(false);
            assertThat(SupportAutomatedBundleConfiguration.get().getPeriod(), is(2));
            assertThat(SupportAutomatedBundleConfiguration.get().isEnabled(), is(true));
        });
    }

    @Test
    public void enforceDisable() throws Throwable {
        rr.javaOptions().javaOptions("-D" + SupportPlugin.class.getName()
            + ".AUTO_BUNDLE_PERIOD_HOURS=0").then(r -> {
            assertThat(SupportAutomatedBundleConfiguration.get().getPeriod(), is(0));
            assertThat(SupportAutomatedBundleConfiguration.get().isEnforcedPeriod(), is(true));
            assertThat(SupportAutomatedBundleConfiguration.get().isEnforcedDisabled(), is(true));
            SupportAutomatedBundleConfiguration.get().setPeriod(1);
            SupportAutomatedBundleConfiguration.get().setEnabled(true);
            assertThat(SupportAutomatedBundleConfiguration.get().getPeriod(), is(0));
            assertThat(SupportAutomatedBundleConfiguration.get().isEnabled(), is(false));
        });
    }
}
