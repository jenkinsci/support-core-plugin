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

import com.cloudbees.jenkins.support.util.WordReplacer;
import hudson.Extension;
import hudson.ExtensionList;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Filters contents by mapping all found IPv4 and IPv6 addresses to generated names.
 *
 * @see ContentMappings
 * @since TODO
 */
@Extension
@Restricted(NoExternalUse.class)
public class InetAddressContentFilter implements ContentFilter {

    /**
     * @return the singleton instance
     */
    public static InetAddressContentFilter get() {
        return ExtensionList.lookupSingleton(InetAddressContentFilter.class);
    }

    // http://www.java2s.com/example/java/java.util.regex/is-ipv4-address-by-regex.html
    private static final String IPv4 = "(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)(\\.(25[0-5]|2[0-4]\\d|[0-1]?\\d?\\d)){3}";
    // Following is based http://www.java2s.com/example/java/java.util.regex/is-ipv6-address-by-regex.html to which 
    // we add the mix notation (last 2 octet is IPv4)
    private static final String IPv6_STANDARD_AND_MIX = "(?i)(?:[0-9a-f]{1,4}:){6}(?:([0-9a-f]{1,4}):[0-9a-f]{1,4}|" + IPv4 + ")";
    private static final String IPv6_COMPRESSED_AND_MIX = "(?i)((?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4})*)?)::(((?:[0-9a-f]{1,4}:){1,5})?(" + IPv4 + ")|((?:[0-9a-f]{1,4}(?::[0-9a-f]{1,4})*)?))";
    private static final Pattern IP_ADDRESS = Pattern.compile("(?<![:.\\w])(" + IPv4 + '|' + IPv6_STANDARD_AND_MIX + '|' + IPv6_COMPRESSED_AND_MIX + ")(?![:.\\w])");

    @Override
    public @NonNull String filter(@NonNull String input) {
        ContentMappings mappings = ContentMappings.get();
        Matcher m = IP_ADDRESS.matcher(input);
        // Use the map keys to filter every IP found without repeating the filtering if the same is found twice.
        Map<String, String> searchAndReplacementValues = new HashMap<>();
        while (m.find()) {
            String ip = m.group();

            if (!mappings.getStopWords().contains(ip)) {
                ContentMapping map = mappings.getMappingOrCreate(ip, InetAddressContentFilter::newMapping);
                searchAndReplacementValues.put(ip, map.getReplacement());
            }
        }
        String filtered = input;
        if (!searchAndReplacementValues.isEmpty()) {
            filtered = WordReplacer.replaceWordsIgnoreCase(input, searchAndReplacementValues.keySet().toArray(new String[0]), searchAndReplacementValues.values().toArray(new String[0]));
        }

        return filtered;
    }

    private static ContentMapping newMapping(String original) {
        String replacement = DataFaker.get().apply(name -> "ip_" + name).get();
        return ContentMapping.of(original, replacement);
    }

}
