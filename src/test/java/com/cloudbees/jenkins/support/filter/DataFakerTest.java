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

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import jenkins.security.HMACConfidentialKey;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.kohsuke.randname.RandomNameGenerator;

@WithJenkins
class DataFakerTest {

    @Test
    void pseudonymsMatchExpectedFormat(JenkinsRule r) {
        DataFaker faker = new DataFaker();
        Pattern expectedPattern = Pattern.compile("^[a-z]+_[a-z_-]+_[0-9a-f]{8}$");

        for (int i = 0; i < 100; i++) {
            String pseudonym = faker.apply(name -> "user_" + name).apply("test" + i);
            assertTrue(
                    expectedPattern.matcher(pseudonym).matches(),
                    "Pseudonym '" + pseudonym + "' does not match expected format");
        }
    }

    @Test
    void tailIsExactlyEightHexChars(JenkinsRule r) {
        DataFaker faker = new DataFaker();

        for (int i = 0; i < 100; i++) {
            String pseudonym = faker.apply(name -> "user_" + name).apply("test" + i);
            assertTrue(pseudonym.matches("^.+_[0-9a-f]{8}$"), "Pseudonym should end with underscore and 8 hex chars");

            String tail = pseudonym.substring(pseudonym.lastIndexOf('_') + 1);
            assertEquals(8, tail.length(), "Tail should be exactly 8 characters");
            assertTrue(tail.matches("[0-9a-f]{8}"), "Tail should be 8 lowercase hex characters");
        }
    }

    @Test
    void deterministicPseudonymGeneration(JenkinsRule r) {
        DataFaker faker = new DataFaker();
        String original = "testuser";

        String pseudonym1 = faker.apply(name -> "user_" + name).apply(original);
        String pseudonym2 = faker.apply(name -> "user_" + name).apply(original);

        assertEquals(pseudonym1, pseudonym2, "Same original should produce identical pseudonym");
    }

    @Test
    void orderIndependence(JenkinsRule r) {
        DataFaker faker1 = new DataFaker();
        String original1 = "foo";
        String original2 = "bar";

        String foo1 = faker1.apply(name -> "user_" + name).apply(original1);
        String bar1 = faker1.apply(name -> "user_" + name).apply(original2);

        DataFaker faker2 = new DataFaker();
        String bar2 = faker2.apply(name -> "user_" + name).apply(original2);
        String foo2 = faker2.apply(name -> "user_" + name).apply(original1);

        assertEquals(foo1, foo2, "Order of minting should not affect pseudonym for 'foo'");
        assertEquals(bar1, bar2, "Order of minting should not affect pseudonym for 'bar'");
    }

    @Test
    void collisionSanityCheck(JenkinsRule r) {
        DataFaker faker = new DataFaker();
        Set<String> pseudonyms = new HashSet<>();
        int iterations = 200000;

        for (int i = 0; i < iterations; i++) {
            String pseudonym = faker.apply(name -> "user_" + name).apply("original" + i);
            pseudonyms.add(pseudonym);
        }

        assertEquals(iterations, pseudonyms.size(), "Expected zero collisions in " + iterations + " iterations");
    }

    @Test
    void legacyPseudonymPreservation(JenkinsRule r) {
        ContentMappings mappings = ContentMappings.get();
        String original = "testuser";
        String legacyPseudonym = "user_sad_cat";

        ContentMapping legacyMapping = ContentMapping.of(original, legacyPseudonym);
        mappings.getMappingOrCreate(original, o -> legacyMapping);

        ContentMapping retrieved = mappings.getMappingOrCreate(
                original,
                o -> ContentMapping.of(
                        o, DataFaker.get().apply(name -> "user_" + name).apply(o)));

        assertEquals(legacyPseudonym, retrieved.getReplacement(), "Legacy pseudonym should be preserved");
    }

    @Test
    void avoidsLibraryCursorHazard(JenkinsRule r) {
        assertThrows(
                IndexOutOfBoundsException.class,
                () -> new RandomNameGenerator(2144220205).next(),
                "Seed 2144220205 causes pos+prime overflow in RandomNameGenerator, yielding negative index");

        org.kohsuke.randname.Dictionary dict = new org.kohsuke.randname.Dictionary();
        int dictSize = dict.size();
        String[] testInputs = {"00000000", "ffffffff"};

        for (String hexInput : testInputs) {
            long value = Long.parseLong(hexInput, 16);
            long index = value % dictSize;

            assertTrue(
                    index >= 0 && index < dictSize,
                    String.format("Index %d not in [0, %d) for input %s", index, dictSize, hexInput));
        }

        DataFaker faker = new DataFaker();
        for (int i = 0; i < 1000; i++) {
            faker.apply(name -> "user_" + name).apply("test" + i);
        }
    }

    @Test
    void keyedPseudonyms(JenkinsRule r) {
        HMACConfidentialKey key1 = new HMACConfidentialKey(DataFaker.class, "pseudonyms");
        HMACConfidentialKey key2 = new HMACConfidentialKey(DataFaker.class, "something-else");

        String original = "testuser";
        String mac1 = key1.mac(original);
        String mac2 = key2.mac(original);

        assertNotEquals(mac1, mac2, "Different keys should produce different MACs for same original");
    }

    @Test
    void macLengthSufficient(JenkinsRule r) {
        HMACConfidentialKey key = new HMACConfidentialKey(DataFaker.class, "pseudonyms");
        String mac = key.mac("test");

        assertTrue(
                mac.length() >= 16,
                "MAC must be at least 16 hex chars to safely extract seed (8 chars) and tail (8 chars). Got: "
                        + mac.length());
        assertEquals(64, mac.length(), "Full HMAC-SHA256 should be 64 hex chars (32 bytes)");
    }
}
