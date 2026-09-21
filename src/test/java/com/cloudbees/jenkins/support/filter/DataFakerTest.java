/*
 * The MIT License
 *
 * Copyright (c) 2026, CloudBees, Inc.
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

import hudson.ExtensionList;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.JenkinsSessionExtension;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;
import org.jvnet.hudson.test.recipes.LocalData;

@WithJenkins
class DataFakerTest {

    @Test
    void pseudonymsMatchExpectedFormat(JenkinsRule r) {
        DataFaker faker = ExtensionList.lookupSingleton(DataFaker.class);
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
        DataFaker faker = ExtensionList.lookupSingleton(DataFaker.class);

        for (int i = 0; i < 100; i++) {
            String pseudonym = faker.apply(name -> "user_" + name).apply("test" + i);
            String tail = pseudonym.substring(pseudonym.lastIndexOf('_') + 1);
            assertTrue(tail.matches("[0-9a-f]{8}"), "Tail should be 8 lowercase hex characters");
        }
    }

    @Nested
    class SessionTests {
        @RegisterExtension
        JenkinsSessionExtension session = new JenkinsSessionExtension();

        @Test
        void deterministicPseudonymGenerationAcrossRestarts() throws Throwable {
            String[] holder = new String[1];

            session.then(r -> {
                DataFaker faker = ExtensionList.lookupSingleton(DataFaker.class);
                holder[0] = faker.apply(name -> "user_" + name).apply("testuser");
            });

            session.then(r -> {
                DataFaker faker = ExtensionList.lookupSingleton(DataFaker.class);
                String pseudonym2 = faker.apply(name -> "user_" + name).apply("testuser");
                assertEquals(holder[0], pseudonym2, "Same original should produce identical pseudonym across restarts");
            });
        }
    }

    @Test
    void orderIndependence(JenkinsRule r) {
        DataFaker faker1 = ExtensionList.lookupSingleton(DataFaker.class);
        String original1 = "foo";
        String original2 = "bar";

        String foo1 = faker1.apply(name -> "user_" + name).apply(original1);
        String bar1 = faker1.apply(name -> "user_" + name).apply(original2);

        DataFaker faker2 = ExtensionList.lookupSingleton(DataFaker.class);
        String bar2 = faker2.apply(name -> "user_" + name).apply(original2);
        String foo2 = faker2.apply(name -> "user_" + name).apply(original1);

        assertEquals(foo1, foo2, "Order of minting should not affect pseudonym for 'foo'");
        assertEquals(bar1, bar2, "Order of minting should not affect pseudonym for 'bar'");
    }

    @Test
    void collisionSanityCheck(JenkinsRule r) {
        DataFaker faker = ExtensionList.lookupSingleton(DataFaker.class);
        Set<String> pseudonyms = new HashSet<>();
        int iterations = 200000;

        for (int i = 0; i < iterations; i++) {
            String pseudonym = faker.apply(name -> "user_" + name).apply("original" + i);
            pseudonyms.add(pseudonym);
        }

        assertEquals(iterations, pseudonyms.size(), "Expected zero collisions in " + iterations + " iterations");
    }

    @LocalData
    @Test
    void legacyPseudonymPreservation(JenkinsRule r) {
        ContentMappings mappings = ContentMappings.get();
        String original = "testuser";
        String legacyPseudonym = "user_sad_cat";

        ContentMapping retrieved = mappings.getMappingOrCreate(
                original,
                o -> ContentMapping.of(
                        o, DataFaker.get().apply(name -> "user_" + name).apply(o)));

        assertEquals(legacyPseudonym, retrieved.getReplacement(), "Legacy pseudonym should be preserved");
    }

    @Test
    void floorModGuaranteesNonNegativeIndices(JenkinsRule r) {
        // Guards against negative-index bugs: Java's % takes the sign of the dividend,
        // so wordFor must use floorMod to guarantee non-negative results for any input.
        for (long hostile : new long[] {Long.MIN_VALUE, -1L, Integer.MIN_VALUE, 0L, 0xFFFFFFFFL, Long.MAX_VALUE}) {
            String word = DataFaker.wordFor(hostile); // must not throw
            assertNotNull(word, "hostile input " + hostile);
        }
    }
}
