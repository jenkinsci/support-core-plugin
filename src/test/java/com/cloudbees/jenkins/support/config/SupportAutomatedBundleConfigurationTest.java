package com.cloudbees.jenkins.support.config;

import com.cloudbees.jenkins.support.api.Component;
import com.gargoylesoftware.htmlunit.html.HtmlElement;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import hudson.lifecycle.RestartNotSupportedException;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

/**
 * Test for the {@link SupportAutomatedBundleConfiguration}
 * @author Allan Burdajewicz
 */
public class SupportAutomatedBundleConfigurationTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void testDefaults() {
        assertThat("by default, support bundle generation should be enabled",
            SupportAutomatedBundleConfiguration.get().isEnabled(),
            is(true));
        assertThat("by default, period should be 1",
            SupportAutomatedBundleConfiguration.get().getPeriod(),
            is(1));
        assertThat("by default, default components should be added",
            SupportAutomatedBundleConfiguration.get().getComponentIds(),
            containsInAnyOrder(SupportAutomatedBundleConfiguration.getDefaultComponentIds().toArray())
        );
    }

    @Test
    public void testRoundTrip() throws Exception {
        HtmlForm cfg = j.createWebClient().goTo("supportCore").getFormByName("config");
        ((HtmlInput) cfg.getOneHtmlElementByAttribute("input", "name", "enabled")).setChecked(true);
        ((HtmlInput) cfg.getOneHtmlElementByAttribute("input", "name", "period")).setValueAttribute("2");
        for (HtmlElement element : cfg.getElementsByAttribute("div", "name", "components")) {
            ((HtmlInput) element.getOneHtmlElementByAttribute("input", "name", "selected")).setChecked(true);
        }
        j.submit(cfg);

        assertThat("should be enabled",
            SupportAutomatedBundleConfiguration.get().isEnabled(),
            is(true));
        assertThat("period should be 1",
            SupportAutomatedBundleConfiguration.get().getPeriod(),
            is(2));
        assertThat("all applicable components should be saved",
            SupportAutomatedBundleConfiguration.get().getComponentIds(),
            containsInAnyOrder(SupportAutomatedBundleConfiguration.getApplicableComponents().stream()
                .map(Component::getId).toArray())
        );
        assertThat("all applicable components should be saved",
            SupportAutomatedBundleConfiguration.get().getComponents().stream()
                .map(Component::getId).collect(Collectors.toList()),
            containsInAnyOrder(SupportAutomatedBundleConfiguration.getApplicableComponents().stream()
                .map(Component::getId).toArray())
        );
        assertThat("all saved components should be retrievable",
            SupportAutomatedBundleConfiguration.get().getComponents().stream()
                .map(Component::getId).collect(Collectors.toList()),
            containsInAnyOrder(SupportAutomatedBundleConfiguration.getApplicableComponents().stream()
                .map(Component::getId).toArray())
        );
    }

    @Test
    public void testSetPeriod() throws RestartNotSupportedException {
        // Test that setting the period to a value lower than -1 set it to minimum 1
        SupportAutomatedBundleConfiguration.get().setPeriod(-1);
        assertThat(SupportAutomatedBundleConfiguration.get().getPeriod(), is(1));

        // Test that setting the period to a value greater than 24 set it to maximum 24
        SupportAutomatedBundleConfiguration.get().setPeriod(25);
        assertThat(SupportAutomatedBundleConfiguration.get().getPeriod(), is(24));
    }
}
