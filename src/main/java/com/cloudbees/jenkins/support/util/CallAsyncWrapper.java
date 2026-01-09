package com.cloudbees.jenkins.support.util;

import com.cloudbees.jenkins.support.SupportPlugin;
import hudson.model.Computer;
import hudson.remoting.AsyncFutureImpl;
import hudson.remoting.Callable;
import hudson.remoting.Future;
import hudson.remoting.VirtualChannel;
import java.io.IOException;
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
 */
public final class CallAsyncWrapper {

    public static final Logger LOGGER = Logger.getLogger(CallAsyncWrapper.class.getName());

    public static <V, T extends Throwable> Future<V> callAsync(
            final VirtualChannel channel, final Callable<V, T> callable) {
        final AsyncFutureImpl<V> resultFuture = new AsyncFutureImpl<>();

        LOGGER.info(() -> String.format(
                "Submitting async call %s to channel %s with timeout of %sms",
                callable, channel, SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS));

        Computer.threadPoolForRemoting.submit(() -> {
            Future<V> innerFuture = null;
            final long callAsyncStartTime;
            final long callAsyncDuration;
            final long callAsyncFutureTime;
            final long callAsyncFutureDuration;
            try {
                callAsyncStartTime = System.nanoTime();
                innerFuture = channel.callAsync(callable);
                callAsyncDuration = System.nanoTime() - callAsyncStartTime;
                LOGGER.finer(() -> String.format(
                        "Async call %s to channel %s started in %d ms",
                        callable, channel, TimeUnit.NANOSECONDS.toMillis(callAsyncDuration)));
                try {
                    callAsyncFutureTime = System.nanoTime();
                    V result = innerFuture.get(SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    callAsyncFutureDuration = System.nanoTime() - callAsyncFutureTime;
                    LOGGER.finer(() -> String.format(
                            "innerFuture for call %s to channel %s completed successfully in %d ms",
                            callable, channel, TimeUnit.NANOSECONDS.toMillis(callAsyncFutureDuration)));
                    resultFuture.set(result);
                } catch (TimeoutException te) {
                    LOGGER.finer(
                            () -> String.format("innerFuture for call %s to channel %s timed out", callable, channel));
                    innerFuture.cancel(true);
                    resultFuture.set(new IOException(
                            "Operation timed out after " + SupportPlugin.REMOTE_OPERATION_TIMEOUT_MS + "ms", te));
                } catch (Throwable t) {
                    LOGGER.finer(() -> String.format(
                            "innerFuture for call %s to channel %s failed during execution", callable, channel));
                    resultFuture.set(t);
                }

            } catch (Throwable t) {
                if (innerFuture != null) {
                    innerFuture.cancel(true);
                }
                LOGGER.finer(() -> String.format("Async call %s to channel %s failed to execute", callable, channel));
                resultFuture.set(t);
            }
        });
        LOGGER.finer(() -> String.format("Async call %s to channel %s submitted", callable, channel));
        return resultFuture;
    }

    private CallAsyncWrapper() {}
}
