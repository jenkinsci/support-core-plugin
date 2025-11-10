package com.cloudbees.jenkins.support.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

import com.cloudbees.jenkins.support.api.Component;
import java.util.stream.Collectors;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlInput;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Test for the {@link SupportAutomatedBundleConfiguration}
 * @author Allan Burdajewicz
 */
@WithJenkins
class SupportAutomatedBundleConfigurationTest {

    @Test
    void testDefaults(JenkinsRule j) {
        assertThat(
                "by default, support bundle generation should be enabled",
                SupportAutomatedBundleConfiguration.get().isEnabled(),
                is(true));
        assertThat(
                "by default, period should be 1",
                SupportAutomatedBundleConfiguration.get().getPeriod(),
                is(1));
        assertThat(
                "by default, default components should be added",
                SupportAutomatedBundleConfiguration.get().getComponentIds(),
                containsInAnyOrder(SupportAutomatedBundleConfiguration.getDefaultComponentIds()
                        .toArray()));
    }

    @Test
    void testRoundTrip(JenkinsRule j) throws Exception {
        HtmlForm cfg = j.createWebClient().goTo("supportCore").getFormByName("config");
        ((HtmlInput) cfg.getOneHtmlElementByAttribute("input", "name", "enabled")).setChecked(true);
        ((HtmlInput) cfg.getOneHtmlElementByAttribute("input", "name", "period")).setValue("2");
        for (HtmlElement element : cfg.getElementsByAttribute("div", "name", "components")) {
            ((HtmlInput) element.getOneHtmlElementByAttribute("input", "name", "selected")).setChecked(true);
        }
        j.submit(cfg);

        assertThat(
                "should be enabled", SupportAutomatedBundleConfiguration.get().isEnabled(), is(true));
        assertThat(
                "period should be 1", SupportAutomatedBundleConfiguration.get().getPeriod(), is(2));
        assertThat(
                "all applicable components should be saved",
                SupportAutomatedBundleConfiguration.get().getComponentIds(),
                containsInAnyOrder(SupportAutomatedBundleConfiguration.getApplicableComponents().stream()
                        .map(Component::getId)
                        .toArray()));
        assertThat(
                "all applicable components should be saved",
                SupportAutomatedBundleConfiguration.get().getComponents().stream()
                        .map(Component::getId)
                        .collect(Collectors.toList()),
                containsInAnyOrder(SupportAutomatedBundleConfiguration.getApplicableComponents().stream()
                        .map(Component::getId)
                        .toArray()));
        assertThat(
                "all saved components should be retrievable",
                SupportAutomatedBundleConfiguration.get().getComponents().stream()
                        .map(Component::getId)
                        .collect(Collectors.toList()),
                containsInAnyOrder(SupportAutomatedBundleConfiguration.getApplicableComponents().stream()
                        .map(Component::getId)
                        .toArray()));
    }

    @Test
    void testSetPeriod(JenkinsRule j) {
        // Test that setting the period to a value lower than -1 set it to minimum 1
        SupportAutomatedBundleConfiguration.get().setPeriod(-1);
        assertThat(SupportAutomatedBundleConfiguration.get().getPeriod(), is(1));

        // Test that setting the period to a value greater than 24 set it to maximum 24
        SupportAutomatedBundleConfiguration.get().setPeriod(25);
        assertThat(SupportAutomatedBundleConfiguration.get().getPeriod(), is(24));
    }
}
