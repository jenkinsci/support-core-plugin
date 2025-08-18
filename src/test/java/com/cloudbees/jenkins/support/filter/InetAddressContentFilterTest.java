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

import static org.assertj.core.api.Assertions.assertThat;
import static org.quicktheories.QuickTheory.qt;
import static org.quicktheories.generators.SourceDSL.integers;

import hudson.BulkChange;
import java.util.stream.IntStream;
import jenkins.model.Jenkins;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.quicktheories.core.Gen;

@WithJenkins
class InetAddressContentFilterTest {

    private static String originalVersion;

    private JenkinsRule j;

    @BeforeEach
    void resetMappings(JenkinsRule j) {
        this.j = j;
        ContentMappings.get().clear();
    }

    @BeforeAll
    static void storeVersion() {
        originalVersion = Jenkins.VERSION;
    }

    @AfterAll
    static void restoreVersion() {
        Jenkins.VERSION = originalVersion;
    }

    @Issue("JENKINS-21670")
    @Test
    void shouldFilterInetAddresses() {
        InetAddressContentFilter filter = InetAddressContentFilter.get();
        // speed up test execution by ignoring new content mappings
        ContentMappings mappings = ContentMappings.get();
        try (BulkChange ignored = new BulkChange(mappings)) {
            qt().forAll(inetAddress()).checkAssert(address -> assertThat(ContentFilter.filter(filter, address))
                    .contains("ip_")
                    .doesNotContain(address));
        }
    }

    @Issue("JENKINS-53184")
    @Test
    void shouldNotFilterInetAddressMatchingJenkinsVersion() {
        Jenkins.VERSION = "1.2.3.4";
        ContentMappings.get().clear();
        InetAddressContentFilter filter = InetAddressContentFilter.get();
        assertThat(ContentFilter.filter(filter, Jenkins.VERSION)).isEqualTo(Jenkins.VERSION);
    }

    private Gen<String> inetAddress() {
        return ipv4().mix(ipv6());
    }

    private Gen<String> ipv4() {
        Gen<String> octet = integers().between(0, 255).map(Number::toString);
        return octet.zip(octet, octet, octet, (p1, p2, p3, p4) -> p1 + '.' + p2 + '.' + p3 + '.' + p4);
    }

    private Gen<String> ipv6() {
        return ipv6Standard().mix(ipv6Compressed()).mix(ipv6Mixed()).mix(ipv6MixedCompressed());
    }

    private Gen<String> ipv6Standard() {
        Gen<String> part = integers().between(0, 65535).map(Integer::toHexString);
        return IntStream.range(0, 8)
                .mapToObj(i -> part)
                .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                .orElseThrow(IllegalStateException::new);
    }

    private Gen<String> ipv6Compressed() {
        Gen<String> part = integers().between(0, 65535).map(Integer::toHexString);
        Gen<String> result = null;
        // First pick an index between 0 and 7 where the compression start
        // Then pick an index between the start index and 8 where the compression stops
        // Generate the left part from 0 to the start index
        // Generate the right part from the stop index to 8
        // If the start index is 0 everything is compressed until the stop index, the string starts with ::
        // If the stop index is 7 everything is compressed before the stop index, the string ends with ::
        for (int compStartIndex = 0; compStartIndex < 7; compStartIndex++) {
            for (int compStopIndex = compStartIndex + 1; compStopIndex < 8; compStopIndex++) {
                Gen<String> compResult;
                if (compStartIndex == 0) {
                    compResult = IntStream.range(compStopIndex, 8)
                            .mapToObj(i -> part)
                            .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                            .orElseThrow(IllegalStateException::new)
                            .map(s -> "::" + s);
                } else if (compStopIndex == 7) {
                    compResult = IntStream.range(0, compStartIndex)
                            .mapToObj(i -> part)
                            .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                            .orElseThrow(IllegalStateException::new)
                            .map(s -> s + "::");
                } else {
                    Gen<String> leftPart = IntStream.range(0, compStartIndex)
                            .mapToObj(i -> part)
                            .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                            .orElseThrow(IllegalStateException::new);
                    Gen<String> rightPart = IntStream.range(compStopIndex, 8)
                            .mapToObj(i -> part)
                            .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                            .orElseThrow(IllegalStateException::new);
                    compResult = leftPart.zip(rightPart, (s1, s2) -> s1 + "::" + s2);
                }
                if (result == null) {
                    result = compResult;
                } else {
                    result.mix(compResult);
                }
            }
        }
        return result;
    }

    private Gen<String> ipv6Mixed() {
        Gen<String> part = integers().between(0, 65535).map(Integer::toHexString);
        Gen<String> ipv4 = ipv4();
        return IntStream.range(0, 6)
                .mapToObj(i -> part)
                .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                .orElseThrow(IllegalStateException::new)
                .zip(ipv4, (s1, s2) -> s1 + ':' + s2);
    }

    private Gen<String> ipv6MixedCompressed() {
        Gen<String> part = integers().between(0, 65535).map(Integer::toHexString);
        Gen<String> ipv4 = ipv4();
        Gen<String> result = null;
        // First pick an index between 0 and 7 where the compression start
        // Then pick an index between the start index and 8 where the compression stops
        // Generate the left part from 0 to the start index
        // Generate the right part from the stop index to 8
        // If the start index is 0 everything is compressed until the stop index, the string starts with ::
        // If the stop index is 7 everything is compressed before the stop index, the string ends with ::
        for (int compStartIndex = 0; compStartIndex < 6; compStartIndex++) {
            for (int compStopIndex = compStartIndex + 1; compStopIndex < 6; compStopIndex++) {
                Gen<String> compResult;
                if (compStartIndex == 0) {
                    compResult = IntStream.range(compStopIndex, 6)
                            .mapToObj(i -> part)
                            .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                            .orElseThrow(IllegalStateException::new)
                            .zip(ipv4, (s1, s2) -> s1 + ":" + s2)
                            .map(s -> "::" + s);
                } else if (compStopIndex == 5) {
                    compResult = IntStream.range(0, compStartIndex)
                            .mapToObj(i -> part)
                            .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                            .orElseThrow(IllegalStateException::new)
                            .zip(ipv4, (s1, s2) -> s1 + "::" + s2);
                } else {
                    Gen<String> leftPart = IntStream.range(0, compStartIndex)
                            .mapToObj(i -> part)
                            .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                            .orElseThrow(IllegalStateException::new);
                    Gen<String> rightPart = IntStream.range(compStopIndex, 6)
                            .mapToObj(i -> part)
                            .reduce((left, right) -> left.zip(right, (s1, s2) -> s1 + ':' + s2))
                            .orElseThrow(IllegalStateException::new);
                    compResult =
                            leftPart.zip(rightPart, (s1, s2) -> s1 + "::" + s2).zip(ipv4, (s1, s2) -> s1 + ':' + s2);
                }
                if (result == null) {
                    result = compResult;
                } else {
                    result.mix(compResult);
                }
            }
        }
        return result;
    }
}
