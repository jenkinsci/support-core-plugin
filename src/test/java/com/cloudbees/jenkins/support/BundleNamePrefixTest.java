package com.cloudbees.jenkins.support;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import javax.annotation.Nonnull;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;

public class BundleNamePrefixTest {

    private static final String CURRENT_YEAR = new SimpleDateFormat("yyyy").format(new Date());

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void checkOriginalBehaviour() {
        assertThat(BundleFileName.generate(), startsWith("support_" + CURRENT_YEAR));
    }

    @Test
    public void checkWithOneProvider() {
        assertThat(BundleFileName.generate(), startsWith("support_pouet_" + CURRENT_YEAR));
    }

    @Test
    public void tooManyProviders() {
        assertThat(BundleFileName.generate(), startsWith("support_Zis_" + CURRENT_YEAR));
    }

    @Test
    public void withSysProp() {
        System.setProperty(BundleNameInstanceTypeProvider.SUPPORT_BUNDLE_NAMING_INSTANCE_SPEC_PROPERTY, "paf");
        assertThat(BundleFileName.generate(), startsWith("support_paf_" + CURRENT_YEAR));
        System.getProperties().remove(BundleNameInstanceTypeProvider.SUPPORT_BUNDLE_NAMING_INSTANCE_SPEC_PROPERTY);
    }

    @TestExtension("checkWithOneProvider")
    public static class TestProvider extends BundleNameInstanceTypeProvider {
        @Nonnull
        @Override
        public String getInstanceType() {
            return "pouet";
        }
    }

    @TestExtension("tooManyProviders")
    public static class SpuriousProvider1 extends BundleNameInstanceTypeProvider {
        @Nonnull
        @Override
        public String getInstanceType() {
            return "Zis";
        }
    }

    @TestExtension("tooManyProviders")
    public static class SpuriousProvider2 extends BundleNameInstanceTypeProvider {
        @Nonnull
        @Override
        public String getInstanceType() {
            return "Zat";
        }
    }
}
