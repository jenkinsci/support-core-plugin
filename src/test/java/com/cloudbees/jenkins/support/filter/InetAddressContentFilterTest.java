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

import hudson.BulkChange;
import jenkins.model.Jenkins;
import org.junit.*;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.quicktheories.core.Gen;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.integers;

public class InetAddressContentFilterTest {

    private static String originalVersion;

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    @Before
    public void resetMappings() {
        ContentMappings.get().clear();
    }

    @BeforeClass
    public static void storeVersion() {
        originalVersion = Jenkins.VERSION;
    }

    @AfterClass
    public static void restoreVersion() {
        Jenkins.VERSION = originalVersion;
    }

    @Issue("JENKINS-21670")
    @Test
    public void shouldFilterInetAddresses() {
        InetAddressContentFilter filter = InetAddressContentFilter.get();
        // speed up test execution by ignoring new content mappings
        ContentMappings mappings = ContentMappings.get();
        try (BulkChange ignored = new BulkChange(mappings)) {
            qt().forAll(inetAddress()).checkAssert(address ->
                    assertThat(ContentFilter.filter(filter, address))
                            .contains("ip_")
                            .doesNotContain(address)
            );
        }
    }

    @Issue("JENKINS-53184")
    @Test
    public void shouldNotFilterInetAddressMatchingJenkinsVersion() {
        Jenkins.VERSION = "1.2.3.4";
        resetMappings();
        InetAddressContentFilter filter = InetAddressContentFilter.get();
        assertThat(ContentFilter.filter(filter, Jenkins.VERSION))
                .isEqualTo(Jenkins.VERSION);
    }

    private Gen<String> inetAddress() {
        return ipv4().mix(ipv6());
    }

    private Gen<String> ipv4() {
        Gen<String> octet = integers().between(0, 255).map(Number::toString);
        return octet.zip(octet, octet, octet, (p1, p2, p3, p4) -> p1 + '.' + p2 + '.' + p3 + '.' + p4);
    }

    private Gen<String> ipv6() {
        Gen<String> part = integers().between(0, 65535).map(Integer::toHexString);
        return IntStream.range(0, 8)
                .mapToObj(i -> part)
                .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                .orElseThrow(IllegalStateException::new);
    }
}
