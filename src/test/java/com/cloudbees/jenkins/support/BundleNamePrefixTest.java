package com.cloudbees.jenkins.support;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.text.SimpleDateFormat;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class BundleNamePrefixTest {

    private static final String CURRENT_YEAR = new SimpleDateFormat("yyyy").format(new Date());

    @Test
    void checkOriginalBehaviour(JenkinsRule j) {
        assertThat(BundleFileName.generate(), startsWith("support_" + CURRENT_YEAR));
    }

    @Test
    void checkWithOneProvider(JenkinsRule j) {
        assertThat(BundleFileName.generate(), startsWith("support_pouet_" + CURRENT_YEAR));
    }

    @Test
    void tooManyProviders(JenkinsRule j) {
        assertThat(BundleFileName.generate(), startsWith("support_Zis_" + CURRENT_YEAR));
    }

    @Test
    void withSysProp(JenkinsRule j) {
        System.setProperty(BundleNameInstanceTypeProvider.SUPPORT_BUNDLE_NAMING_INSTANCE_SPEC_PROPERTY, "paf");
        assertThat(BundleFileName.generate(), startsWith("support_paf_" + CURRENT_YEAR));
        System.getProperties().remove(BundleNameInstanceTypeProvider.SUPPORT_BUNDLE_NAMING_INSTANCE_SPEC_PROPERTY);
    }

    @TestExtension("checkWithOneProvider")
    public static class TestProvider extends BundleNameInstanceTypeProvider {
        @NonNull
        @Override
        public String getInstanceType() {
            return "pouet";
        }
    }

    @TestExtension("tooManyProviders")
    public static class SpuriousProvider1 extends BundleNameInstanceTypeProvider {
        @NonNull
        @Override
        public String getInstanceType() {
            return "Zis";
        }
    }

    @TestExtension("tooManyProviders")
    public static class SpuriousProvider2 extends BundleNameInstanceTypeProvider {
        @NonNull
        @Override
        public String getInstanceType() {
            return "Zat";
        }
    }
}
