/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Function;
import jenkins.security.HMACConfidentialKey;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.randname.Dictionary;

/**
 * Provides deterministic pseudonym generation for anonymization.
 *
 * <p>Derives stable pseudonyms from originals using HMAC-keyed word pairs and hex tails.
 * Same original always produces the same pseudonym within one installation (stable across
 * restarts and CloudBees CI HA replicas sharing JENKINS_HOME). Deliberately differs across installations
 * with different secret keys, preventing brute-force attacks on guessable originals.
 */
@Extension
@Restricted(NoExternalUse.class)
public class DataFaker implements ExtensionPoint, Function<Function<String, String>, Function<String, String>> {

    private static final HMACConfidentialKey PSEUDONYMS = new HMACConfidentialKey(DataFaker.class, "pseudonyms", 8);
    private static final Dictionary DICTIONARY = new Dictionary();

    /**
     * @return the singleton instance
     */
    public static DataFaker get() {
        return ExtensionList.lookupSingleton(DataFaker.class);
    }

    /**
     * Maps MAC-derived bits to a dictionary word, guaranteed never to produce a negative index.
     *
     * <p>Uses {@code Math.floorMod} rather than {@code %} because Java's {@code %} operator
     * takes the sign of the dividend, so a negative input yields a negative index.
     * {@code floorMod} takes the sign of the divisor, guaranteeing a non-negative result
     * for any input including {@code Long.MIN_VALUE}.
     *
     * @param macBits MAC-derived value to map to dictionary index
     * @return dictionary word at the computed index
     */
    static String wordFor(long macBits) {
        return DICTIONARY.word(Math.floorMod(macBits, DICTIONARY.size()));
    }

    /**
     * Applies the provided function to a deterministic name derived from the original and normalizes the result.
     *
     * @param nameTransformer function to apply to the generated word pair (e.g., adds prefix like "user_")
     * @return function that maps original strings to stable pseudonyms
     */
    @Override
    public Function<String, String> apply(@NonNull Function<String, String> nameTransformer) {
        return original -> {
            byte[] mac = PSEUDONYMS.mac(original.getBytes(StandardCharsets.UTF_8));
            // First 4 bytes for dictionary index, next 4 for the 8-hex-character tail
            long idx = ((long) (mac[0] & 0xFF) << 24)
                    | ((long) (mac[1] & 0xFF) << 16)
                    | ((long) (mac[2] & 0xFF) << 8)
                    | (mac[3] & 0xFF);
            String tail = String.format("%02x%02x%02x%02x", mac[4] & 0xFF, mac[5] & 0xFF, mac[6] & 0xFF, mac[7] & 0xFF);

            String name = nameTransformer
                    .apply(wordFor(idx))
                    .toLowerCase(Locale.ENGLISH)
                    .replace(' ', '_');
            return name + "_" + tail;
        };
    }
}
