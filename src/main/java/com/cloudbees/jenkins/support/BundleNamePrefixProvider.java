package com.cloudbees.jenkins.support;

import com.google.common.annotations.VisibleForTesting;
import hudson.ExtensionList;
import hudson.ExtensionPoint;

import javax.annotation.Nonnull;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * <p>Extension point allowing to customize the support bundle name prefixing strategy.</p>
 * It will work the following way:
 * <ol>
 * <li>If an implementation of {@link BundleNamePrefixProvider} is found, it will be used.<br>
 * <strong>WARNING: </strong>if many are found, then a warning will be issued, and the first extension found will
 * be used.</li>
 * <li>If not, then it will check for the presence of the {@link #SUPPORT_BUNDLE_NAMING_PREFIX_PROPERTY}
 * system property, and will use its value if provided.</li>
 * <li>If not, then will fallback to the original behaviour, simply using <em>support</em> as a prefix</li>
 * </ol>
 */
public abstract class BundleNamePrefixProvider implements ExtensionPoint {

    private static final Logger LOGGER = Logger.getLogger(BundleNamePrefixProvider.class.getName());

    @VisibleForTesting
    static final String SUPPORT_BUNDLE_NAMING_PREFIX_PROPERTY = SupportPlugin.class.getName() + ".bundlePrefix";

    private static final BundleNamePrefixProvider DEFAULT_STRATEGY = new BundleNamePrefixProvider() {

        @Override
        public String getPrefix() {
            return System.getProperty(SUPPORT_BUNDLE_NAMING_PREFIX_PROPERTY, "support");
        }
    };

    /* package */
    static BundleNamePrefixProvider getInstance() {
        final ExtensionList<BundleNamePrefixProvider> all = ExtensionList.lookup(BundleNamePrefixProvider.class);
        final int extensionCount = all.size();
        System.out.println(extensionCount);
        if (extensionCount < 1) {
            LOGGER.fine("No alternative strategy provided for support bundle prefixing.");
            return DEFAULT_STRATEGY;
        }

        if (all.size() > 1) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.warning("{0} implementations found for support bundle prefix naming strategy. " +
                        "Can be only 0 or 1. Choosing the first found.");
                for (BundleNamePrefixProvider bundlePrefixProvider : all) {
                    LOGGER.log(Level.WARNING, "class '{0}' found", bundlePrefixProvider.getClass().getName());
                }
            }
        }
        return all.get(0);
    }

    /**
     * Returns the <strong>non-null</strong> prefix to be used for generated support bundles.
     *
     * @return the prefix to be used for generated support bundles.
     */
    @Nonnull
    public abstract String getPrefix();
}
