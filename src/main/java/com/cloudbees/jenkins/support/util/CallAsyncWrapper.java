package com.cloudbees.jenkins.support.util;

import com.cloudbees.jenkins.support.SupportPlugin;
import hudson.model.Computer;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

/**
 * A utility class to safely call asynchronous operations on a {@link VirtualChannel}. The primary purpose of this
 * wrapper is to address a known issue where {@code VirtualChannel.callAsync(Callable)} can block indefinitely before
 * even returning a {@link Future}, especially when the channel is congested.
 *
 * This wrapper ensures that the asynchronous call is executed in a separate thread, allowing us to impose a timeout
 * on the operation. If the operation exceeds the specified timeout, it is cancelled, and an {@link IOException} is
 * returned indicating the timeout.
 *
 * <p>The timeout in this wrapper applies only to obtaining the Future, not to the remote execution.
 * The caller is responsible for applying their own timeout to the execution via {@code Future.get(timeout)}.
 *
 * @see com.cloudbees.jenkins.support.AsyncResultCache#get(hudson.model.Node, java.util.WeakHashMap, hudson.remoting.Callable, String)
 */
public final class CallAsyncWrapper {

    public static final Logger LOGGER = Logger.getLogger(CallAsyncWrapper.class.getName());

    public static <V, T extends Throwable> Future<V> callAsync(
            final VirtualChannel channel, final Callable<V, T> callable) throws IOException {

        LOGGER.fine(() -> String.format(
                "Submitting callAsync %s to channel %s with timeout %dms for obtaining Future",
                callable, channel, SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS));

        final long startTime = System.nanoTime();
        var executorFuture = Computer.threadPoolForRemoting.submit(() -> channel.callAsync(callable));

        try {
            Future<V> result = executorFuture.get(SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            final long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            LOGGER.fine(() -> String.format(
                    "Successfully obtained Future of %s from channel %s in %dms", callable, channel, duration));
            return result;
        } catch (InterruptedException | ExecutionException e) {
            final long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            LOGGER.fine(() -> String.format(
                    "Failed to obtain Future %s from channel %s after %dms: %s",
                    callable, channel, duration, e.getMessage()));
            throw new IOException(e);
        } catch (TimeoutException te) {
            final long duration = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            executorFuture.cancel(true);
            LOGGER.fine(() -> String.format(
                    "Timeout waiting for Future of %s from channel %s after %dms", callable, channel, duration));
            throw new IOException("Timeout waiting for channel.callAsync() to return after " + duration + "ms", te);
        }
    }

    private CallAsyncWrapper() {}
}
