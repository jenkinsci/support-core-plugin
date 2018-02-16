package com.cloudbees.jenkins.support;

import com.cloudbees.jenkins.support.util.Anonymizer;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import jenkins.model.Jenkins;

import java.io.IOException;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
* @author Stephen Connolly
*/
public class AsyncResultCache<T> implements Runnable {
    private static final Logger LOGGER = Logger.getLogger(AsyncResultCache.class.getName());
    private final WeakHashMap<Node, T> cache;
    private final Future<T> future;
    private final Node node;
    private final String name;
    private final boolean shouldAnonymize;

    public static <V, T extends java.lang.Throwable> V get(Node node, WeakHashMap<Node, V> cache, /*MasterToSlave*/Callable<V, T> operation, String name, V defaultIfNull)
            throws IOException {
        return get(node, cache, false, operation, name, defaultIfNull);
    }

    public static <V, T extends java.lang.Throwable> V get(Node node, WeakHashMap<Node, V> cache, boolean shouldAnonymize, /*MasterToSlave*/Callable<V, T> operation, String name, V defaultIfNull)
            throws IOException {
        V result = get(node, cache, shouldAnonymize, operation, name);
        return result == null ? defaultIfNull : result;
    }

    public static <V, T extends java.lang.Throwable> V get(Node node, WeakHashMap<Node, V> cache, /*MasterToSlave*/Callable<V, T> operation, String name)
            throws IOException {
        return get(node, cache, false, operation, name);
    }

    public static <V, T extends java.lang.Throwable> V get(Node node, WeakHashMap<Node, V> cache, boolean shouldAnonymize, /*MasterToSlave*/Callable<V, T> operation, String name)
            throws IOException {
        if (node == null) return null;
        VirtualChannel channel = node.getChannel();
        if (channel == null) {
            synchronized (cache) {
                return cache.get(node);
            }
        }
        Future<V> future = channel.callAsync(operation);
        try {
            final V result = future.get(SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            synchronized (cache) {
                cache.put(node, result);
            }
            return result;
        } catch (InterruptedException | ExecutionException e) {
            final LogRecord lr = new LogRecord(Level.FINE, "Could not retrieve {0} from {1}");
            lr.setParameters(new Object[]{name, getNodeName(node, shouldAnonymize)});
            lr.setThrown(e);
            LOGGER.log(lr);
            synchronized (cache) {
                return cache.get(node);
            }
        } catch (TimeoutException e) {
            final LogRecord lr = new LogRecord(Level.FINER, "Could not retrieve {0} from {1}");
            lr.setParameters(new Object[]{name, getNodeName(node, shouldAnonymize)});
            lr.setThrown(e);
            LOGGER.log(lr);
            Computer.threadPoolForRemoting.submit(new AsyncResultCache<>(node, cache, future, name, shouldAnonymize));
            synchronized (cache) {
                return cache.get(node);
            }
        }
    }

    private static String getNodeName(Node node, boolean shouldAnonymize) {
        if (shouldAnonymize) {
            return node instanceof Jenkins ? "master" : Anonymizer.anonymize(node.getNodeName());
        }
        return node instanceof Jenkins ? "master" : node.getNodeName();
    }

    public AsyncResultCache(Node node, WeakHashMap<Node, T> cache, Future<T> future, String name) {
        this(node, cache, future, name, false);
    }

    public AsyncResultCache(Node node, WeakHashMap<Node, T> cache, Future<T> future, String name, boolean shouldAnonymize) {
        this.node = node;
        this.cache = cache;
        this.future = future;
        this.name = name;
        this.shouldAnonymize = shouldAnonymize;
    }

    public void run() {
        T result;
        try {
            result = future.get(SupportPlugin.REMOTE_OPERATION_CACHE_TIMEOUT_SEC, TimeUnit.SECONDS);
            synchronized (cache) {
                cache.put(node, result);
            }
        } catch (InterruptedException | ExecutionException e1) {
            final LogRecord lr = new LogRecord(Level.FINE, "Could not retrieve {0} from {1} for caching");
            lr.setParameters(new Object[]{name, getNodeName(node, shouldAnonymize)});
            lr.setThrown(e1);
            LOGGER.log(lr);
        } catch (TimeoutException e1) {
            final LogRecord lr = new LogRecord(Level.INFO, "Could not retrieve {0} from {1} for caching");
            lr.setParameters(new Object[]{name, getNodeName(node, shouldAnonymize)});
            lr.setThrown(e1);
            LOGGER.log(lr);
            future.cancel(true);
        }
    }
}
