package com.cloudbees.jenkins.support.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.RealJenkinsExtension;

/**
 * Test for the {@link SupportAutomatedBundleConfiguration}
 *
 * @author Allan Burdajewicz
 */
class SupportAutomatedBundleConfigurationSystemPropertiesTest {
    @RegisterExtension
    private final RealJenkinsExtension extension = new RealJenkinsExtension();

    @Test
    void enforcePeriod() throws Throwable {
        extension
                .javaOptions()
                .javaOptions("-D" + SupportPlugin.class.getName() + ".AUTO_BUNDLE_PERIOD_HOURS=2")
                .then(SupportAutomatedBundleConfigurationSystemPropertiesTest::_enforcePeriod);
    }

    private static void _enforcePeriod(JenkinsRule r) throws Exception {
        assertThat(
                r.getInstance()
                        .getExtensionList(SupportAutomatedBundleConfiguration.class)
                        .get(0)
                        .getPeriod(),
                is(2));
        assertThat(SupportAutomatedBundleConfiguration.get().isEnforcedPeriod(), is(true));
        assertThat(SupportAutomatedBundleConfiguration.get().isEnforcedDisabled(), is(false));
        assertThat(SupportAutomatedBundleConfiguration.get().isEnabled(), is(true));
        SupportAutomatedBundleConfiguration.get().setPeriod(1);
        SupportAutomatedBundleConfiguration.get().setEnabled(false);
        assertThat(SupportAutomatedBundleConfiguration.get().getPeriod(), is(2));
        assertThat(SupportAutomatedBundleConfiguration.get().isEnabled(), is(true));

        try (JenkinsRule.WebClient wc = r.createWebClient()) {
            HtmlForm cfg = wc.goTo("supportCore").getFormByName("config");
            assertThat(
                    "should be checked",
                    ((HtmlInput) cfg.getOneHtmlElementByAttribute("input", "name", "enabled")).isChecked(),
                    is(true));
            assertThat(
                    "should not be able to disable",
                    ((HtmlInput) cfg.getOneHtmlElementByAttribute("input", "name", "enabled")).isCheckable(),
                    is(true));
            assertThat(
                    "should not even show an input for period",
                    cfg.getElementsByAttribute("input", "name", "period"),
                    hasSize(0));
            for (HtmlElement element : cfg.getElementsByAttribute("div", "name", "components")) {
                ((HtmlInput) element.getOneHtmlElementByAttribute("input", "name", "selected")).setChecked(true);
            }
            r.submit(cfg);

            assertThat(
                    "should be enabled",
                    SupportAutomatedBundleConfiguration.get().isEnabled(),
                    is(true));
            assertThat(
                    "period should be 2",
                    SupportAutomatedBundleConfiguration.get().getPeriod(),
                    is(2));
            assertThat(
                    "all applicable components should be saved",
                    SupportAutomatedBundleConfiguration.get().getComponentIds(),
                    containsInAnyOrder(SupportAutomatedBundleConfiguration.getApplicableComponents().stream()
                            .map(Component::getId)
                            .toArray()));
        }
    }

    @Test
    void enforceDisable() throws Throwable {
        extension
                .javaOptions()
                .javaOptions("-D" + SupportPlugin.class.getName() + ".AUTO_BUNDLE_PERIOD_HOURS=0")
                .then(SupportAutomatedBundleConfigurationSystemPropertiesTest::_enforceDisabled);
    }

    private static void _enforceDisabled(JenkinsRule r) throws Exception {
        assertThat(SupportAutomatedBundleConfiguration.get().getPeriod(), is(0));
        assertThat(SupportAutomatedBundleConfiguration.get().isEnforcedPeriod(), is(true));
        assertThat(SupportAutomatedBundleConfiguration.get().isEnforcedDisabled(), is(true));
        SupportAutomatedBundleConfiguration.get().setPeriod(1);
        SupportAutomatedBundleConfiguration.get().setEnabled(true);
        assertThat(SupportAutomatedBundleConfiguration.get().getPeriod(), is(0));
        assertThat(SupportAutomatedBundleConfiguration.get().isEnabled(), is(false));

        try (JenkinsRule.WebClient wc = r.createWebClient()) {
            HtmlForm cfg = wc.goTo("supportCore").getFormByName("config");
            assertThat(
                    "should not be checked",
                    ((HtmlInput) cfg.getOneHtmlElementByAttribute("input", "name", "enabled")).isChecked(),
                    is(false));
            assertThat(
                    "should not be able to enable",
                    ((HtmlInput) cfg.getOneHtmlElementByAttribute("input", "name", "enabled")).isCheckable(),
                    is(true));
            assertThat(
                    "should not even show period", cfg.getElementsByAttribute("input", "name", "period"), hasSize(0));
            assertThat(
                    "should not even show components",
                    cfg.getElementsByAttribute("div", "name", "components"),
                    hasSize(0));
            r.submit(cfg);

            assertThat(
                    "should be disabled",
                    SupportAutomatedBundleConfiguration.get().isEnabled(),
                    is(false));
            assertThat(
                    "period should be 0",
                    SupportAutomatedBundleConfiguration.get().getPeriod(),
                    is(0));
        }
    }
}
