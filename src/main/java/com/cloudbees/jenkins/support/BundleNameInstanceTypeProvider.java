package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.SupportProvider;
import com.google.common.annotations.VisibleForTesting;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Extension point allowing to customize the support bundle naming strategy.</p>
 * <p>
 * It will work the following way:
 * </p>
 * <ol>
 * <li>If an implementation of {@link BundleNameInstanceTypeProvider} is found, it will be used.<br>
 * <strong>WARNING: </strong>if many are found, then a warning will be issued, and the first extension found will
 * be used.</li>
 * <li>If not, then it will check for the presence of the {@link #SUPPORT_BUNDLE_NAMING_INSTANCE_SPEC_PROPERTY}
 * system property, and will use its value if provided.</li>
 * <li>If not, then will fallback to the original behaviour, which is simply an empty String</li>
 * </ol>
 *
 * @see SupportProvider#getName() for prefixing.
 */
public abstract class BundleNameInstanceTypeProvider implements ExtensionPoint {

    @VisibleForTesting
    static final String SUPPORT_BUNDLE_NAMING_INSTANCE_SPEC_PROPERTY = SupportPlugin.class.getName() + ".instanceType";

    private static final Logger LOGGER = Logger.getLogger(BundleNameInstanceTypeProvider.class.getName());

    private static final BundleNameInstanceTypeProvider DEFAULT_STRATEGY = new BundleNameInstanceTypeProvider() {

        @Override
        public String getInstanceType() {
            return System.getProperty(SUPPORT_BUNDLE_NAMING_INSTANCE_SPEC_PROPERTY, "");
        }
    };

    /* package */
    @Nonnull
    static BundleNameInstanceTypeProvider getInstance() {
        final ExtensionList<BundleNameInstanceTypeProvider> all = ExtensionList.lookup(BundleNameInstanceTypeProvider.class);
        final int extensionCount = all.size();
        if (extensionCount < 1) {
            LOGGER.fine("No alternative strategy provided for support bundle prefixing.");
            return DEFAULT_STRATEGY;
        }

        if (all.size() > 1) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning("{0} implementations found for support bundle prefix naming strategy. " +
                        "Can be only 0 or 1. Choosing the first found.");
                for (BundleNameInstanceTypeProvider bundlePrefixProvider : all) {
                    LOGGER.log(Level.WARNING, "class '{0}' found", bundlePrefixProvider.getClass().getName());
                }
            }
        }
        return all.get(0);
    }

    /**
     * Returns the <strong>non-null</strong> instance type to be used for generated support bundle names.
     * Aims to provide informational data about the generated bundles.
     *
     * <p>
     * <p><b>Will be used for file name generation, so avoid funky characters.
     * Please stay in <code>[a-zA-Z-_]</code></b>. Also consider the file name length, you probably want to be defensive
     * and not return crazily long strings. Something below 20 characters or so might sound reasonable.</p>
     *
     * @return the instance type specification to be used for generated support bundles.
     */
    @Nonnull
    public abstract String getInstanceType();
}
