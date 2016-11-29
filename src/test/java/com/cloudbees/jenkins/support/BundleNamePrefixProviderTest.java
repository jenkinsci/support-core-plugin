package com.cloudbees.jenkins.support;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import javax.annotation.Nonnull;

import static org.junit.Assert.assertEquals;

public class BundleNamePrefixProviderTest {

    @Rule
    public JenkinsRule rule = new JenkinsRule();

    @Test
    public void checkOriginalBehaviour() throws Exception {
        assertEquals("support", BundleNamePrefixProvider.getInstance().getPrefix());
    }

    @Test
    public void checkWithOneProvider() throws Exception {
        assertEquals("pouet", BundleNamePrefixProvider.getInstance().getPrefix());
    }

    @Test
    public void tooManyProviders() throws Exception {
        assertEquals("this", BundleNamePrefixProvider.getInstance().getPrefix());
    }

    @Test
    public void withSysProp() throws Exception {
        System.setProperty(BundleNamePrefixProvider.SUPPORT_BUNDLE_NAMING_PREFIX_PROPERTY, "paf");
        assertEquals("paf", BundleNamePrefixProvider.getInstance().getPrefix());
        System.getProperties().remove(BundleNamePrefixProvider.SUPPORT_BUNDLE_NAMING_PREFIX_PROPERTY);
    }

    @TestExtension("checkWithOneProvider")
    public static class TestProvider extends BundleNamePrefixProvider {

        @Nonnull
        @Override
        public String getPrefix() {
            return "pouet";
        }
    }

    @TestExtension("tooManyProviders")
    public static class SpuriousProvider extends BundleNamePrefixProvider {
        @Nonnull
        @Override
        public String getPrefix() {
            return "this";
        }
    }

    @TestExtension("tooManyProviders")
    public static class SpuriousProvider2 extends BundleNamePrefixProvider {
        @Nonnull
        @Override
        public String getPrefix() {
            return "that";
        }
    }
}