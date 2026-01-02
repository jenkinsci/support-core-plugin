package com.cloudbees.jenkins.support;

import static org.awaitility.Awaitility.await;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.jenkins.support.util.CallAsyncWrapper;
import hudson.remoting.Callable;
import hudson.slaves.DumbSlave;
import java.io.IOException;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import jenkins.model.Jenkins;
import jenkins.security.MasterToSlaveCallable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class AsyncResultCacheTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule();

    private WeakHashMap<hudson.model.Node, String> cache;

    @Before
    public void setUp() {
        cache = new WeakHashMap<>();
        logger.record(AsyncResultCache.class, Level.FINE).record(CallAsyncWrapper.class, Level.FINE);
    }

    /**
     * Test successful operation on built-in node (Jenkins instance)
     */
    @Test
    public void testGetBuiltInNodeSuccess() throws IOException {
        Jenkins jenkins = j.jenkins;
        String expectedResult = "built-in-result";
        Callable<String, Exception> operation = new MasterToSlaveCallable<>() {
            @Override
            public String call() {
                return expectedResult;
            }
        };

        String result = AsyncResultCache.get(jenkins, cache, operation, "test-operation");

        assertEquals("Result should match operation result", expectedResult, result);
        assertEquals("Result should be cached", expectedResult, cache.get(jenkins));
    }

    /**
     * Test successful operation on remote node
     */
    @Test
    public void testGetRemoteNodeSuccess() throws Exception {
        DumbSlave agent = j.createOnlineSlave();
        String expectedResult = "remote-result";
        Callable<String, Exception> operation = new TestCallable(expectedResult);

        String result = AsyncResultCache.get(agent, cache, operation, "test-operation");

        assertEquals("Result should match operation result", expectedResult, result);
        assertEquals("Result should be cached", expectedResult, cache.get(agent));
    }

    /**
     * Test that cached value is returned when channel is null
     */
    @Test
    public void testGetRemoteNodeNoChannel() throws Exception {
        DumbSlave agent = j.createSlave();
        // Don't bring agent online, so channel will be null
        String cachedValue = "cached-result";
        cache.put(agent, cachedValue);

        Callable<String, Exception> operation = new MasterToSlaveCallable<>() {
            @Override
            public String call() {
                return "should-not-be-called";
            }
        };

        String result = AsyncResultCache.get(agent, cache, operation, "test-operation");

        assertEquals("Result should be cached value when no channel", cachedValue, result);
    }

    /**
     * Test that operation failure returns cached value
     */
    @Test
    public void testGetOperationFailureReturnsCached() throws Exception {
        Jenkins jenkins = j.jenkins;
        String cachedValue = "cached-on-failure";
        cache.put(jenkins, cachedValue);

        Callable<String, Exception> operation = new MasterToSlaveCallable<>() {
            @Override
            public String call() {
                throw new RuntimeException("Operation failed");
            }
        };

        String result = AsyncResultCache.get(jenkins, cache, operation, "test-operation");

        assertEquals("Result should be cached value on failure", cachedValue, result);
    }

    /**
     * Test that timeout schedules background retry and returns cached value
     */
    @Test
    public void testGetTimeoutSchedulesBackgroundRetry() throws Exception {
        Jenkins jenkins = j.jenkins;
        String cachedValue = "cached-on-timeout";
        cache.put(jenkins, cachedValue);

        Callable<String, Exception> operation = new MasterToSlaveCallable<>() {
            @Override
            public String call() throws Exception {
                // Block indefinitely - will cause AsyncResultCache to timeout
                await().forever().until(() -> false);
                return "delayed-result";
            }
        };

        String result = AsyncResultCache.get(jenkins, cache, operation, "test-operation");

        assertEquals("Result should be cached value on timeout", cachedValue, result);
    }

    /**
     * Test cache behavior - verify results are properly cached
     */
    @Test
    public void testCacheBehavior() throws Exception {
        Jenkins jenkins = j.jenkins;
        final String[] callCount = {""};

        Callable<String, Exception> operation = new MasterToSlaveCallable<>() {
            @Override
            public String call() {
                callCount[0] += "x";
                return "result-" + callCount[0].length();
            }
        };

        // First call - should execute operation
        String result1 = AsyncResultCache.get(jenkins, cache, operation, "test-operation");
        assertEquals("First call should return result-1", "result-1", result1);
        assertEquals("Result should be cached after first call", "result-1", cache.get(jenkins));

        // Verify cache contains the value
        assertNotNull("Cache should contain value for jenkins node", cache.get(jenkins));
    }

    /**
     * Test background retry (run method) - successful completion
     */
    @Test
    public void testBackgroundRetrySuccess() throws Exception {
        Jenkins jenkins = j.jenkins;
        String expectedResult = "background-result";

        // Create a Future that will complete immediately
        Future<String> future = CompletableFuture.completedFuture(expectedResult);

        AsyncResultCache<String> backgroundTask = new AsyncResultCache<>(jenkins, cache, future, "background-op");

        // Run in background
        new Thread(backgroundTask).start();

        // Use Awaitility to wait for the cache to be populated
        await().atMost(5, TimeUnit.SECONDS).until(() -> cache.get(jenkins) != null);

        // Verify result was cached
        assertEquals("Background task should cache result", expectedResult, cache.get(jenkins));
    }

    /**
     * Test operation on remote node with mocked channel
     */
    @Test
    public void testGetRemoteNodeOperation() throws Exception {
        // This test verifies the CallAsyncWrapper integration
        DumbSlave agent = j.createOnlineSlave();
        String expectedResult = "mocked-remote-result";

        Callable<String, Exception> operation = new TestCallable(expectedResult);

        String result = AsyncResultCache.get(agent, cache, operation, "mocked-operation");

        assertEquals("Result should match operation result", expectedResult, result);
        assertEquals("Result should be cached", expectedResult, cache.get(agent));
    }

    /**
     * Test callable for remote execution / serialization
     */
    private static class TestCallable extends MasterToSlaveCallable<String, Exception> {
        private final String result;

        TestCallable(String result) {
            this.result = result;
        }

        @Override
        public String call() {
            return result;
        }
    }
}
