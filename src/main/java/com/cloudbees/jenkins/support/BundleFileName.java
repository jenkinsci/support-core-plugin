package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.api.SupportProvider;
import org.apache.commons.lang.StringUtils;

import javax.annotation.Nonnull;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * <p>Generate the support bundle names.</p>
 *
 * <p>Format: {@code "${support-provider-name}[_${qualifier}][_${instance_type}]_${date_time}}</p>
 * <dl>
 *     <dt>support-provider-name</dt>
 *     <dd>"support" or the value of {@link SupportProvider#getName()}</dd>
 *     <dt>qualifier</dt>
 *     <dd>arbitrary string to help distinguish between potentially different support bundles</dd>
 *     <dt>instance_type</dt>
 *     <dd>See {@link BundleNameInstanceTypeProvider}</dd>
 *     <dt>date_time</dt>
 *     <dd>Date Time of the generation of the support bundle in UTC.</dd>
 * </dl>
 */
public final class BundleFileName {
    private BundleFileName() {
        throw new UnsupportedOperationException();
    }

    private static final Clock DEFAULT_CLOCK = Clock.systemUTC();
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss");

    /**
     * @return the bundle name without qualifier.
     */
    @Nonnull
    public static String generate() {
        return generate(DEFAULT_CLOCK, null);
    }

    /**
     * @return the bundle name with qualifier.
     */
    @Nonnull
    public static String generate(String qualifier) {
        return generate(DEFAULT_CLOCK, qualifier);
    }

    static String generate(Clock clock, String qualifier) {
        Objects.requireNonNull(clock);

        StringBuilder filename = new StringBuilder();
        filename.append(getSupportProviderName());
        if (StringUtils.isNotBlank(qualifier)) {
            filename.append('_').append(qualifier.trim());
        }
        final String instanceType = BundleNameInstanceTypeProvider.getInstance().getInstanceType();
        if (StringUtils.isNotBlank(instanceType)) {
            filename.append("_").append(instanceType);
        }
        filename.append("_").append(LocalDateTime.now(clock).format(DATE_TIME_FORMATTER));
        filename.append(".zip");
        return filename.toString();
    }

    /**
     * Returns the prefix of the bundle name.
     *
     * @return the prefix of the bundle name.
     */
    private static String getSupportProviderName() {
        String filename = "support"; // default bundle filename
        final SupportPlugin instance = SupportPlugin.getInstance();
        if (instance != null) {
            SupportProvider supportProvider = instance.getSupportProvider();
            if (supportProvider != null) {
                // let the provider name it
                filename = supportProvider.getName();
            }
        }
        return filename;
    }
}
