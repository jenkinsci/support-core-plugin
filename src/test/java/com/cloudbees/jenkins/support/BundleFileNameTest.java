package com.cloudbees.jenkins.support;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.cloudbees.jenkins.support.api.SupportProvider;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.PrintWriter;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.localizer.Localizable;

@WithJenkins
class BundleFileNameTest {

    private static final Clock TEST_CLOCK = Clock.fixed(Instant.parse("2020-10-01T10:20:30Z"), ZoneOffset.UTC);

    @Test
    void testGenerate_Default(JenkinsRule j) {
        assertThat(BundleFileName.generate(TEST_CLOCK, null), equalTo("support_2020-10-01_10.20.30.zip"));
    }

    @Disabled("Relies on SupportPlugin object which is not instantiated by TestPluginManager")
    @Test
    void testGenerate_WithSupportProvider(JenkinsRule j) {
        assertThat(BundleFileName.generate(TEST_CLOCK, null), equalTo("amazing-support_2020-10-01_10.20.30.zip"));
    }

    @TestExtension("testGenerate_WithSupportProvider")
    public static class AmazingSupportProvider extends SupportProvider {
        @Override
        public String getName() {
            return "amazing-support";
        }

        @Override
        public String getDisplayName() {
            return "Amazing Support";
        }

        @Override
        public Localizable getActionTitle() {
            return null;
        }

        @Override
        public Localizable getActionBlurb() {
            return null;
        }

        @Override
        public void printAboutJenkins(PrintWriter out) {}
    }

    @Test
    void testGenerate_WithQualifier(JenkinsRule j) {
        assertThat(
                BundleFileName.generate(TEST_CLOCK, "qualifier"), equalTo("support_qualifier_2020-10-01_10.20.30.zip"));
    }

    @Test
    void testGenerate_WithQualifierAndInstanceType(JenkinsRule j) {
        assertThat(
                BundleFileName.generate(TEST_CLOCK, "qualifier"),
                equalTo("support_qualifier_instance_type_2020-10-01_10.20.30.zip"));
    }

    @TestExtension("testGenerate_WithQualifierAndInstanceType")
    public static class TestBundleNameInstanceTypeProvider extends BundleNameInstanceTypeProvider {
        @Override
        @NonNull
        public String getInstanceType() {
            return "instance_type";
        }
    }
}
