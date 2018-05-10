/*
 * Copyright (c) 2018 chibchasoft.com
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials are made
 * available under the terms of the Apache License v2.0 which accompanies
 * this distribution.
 *
 *      The Apache License v2.0 is available at
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Author <a href="mailto:jvelez@chibchasoft.com">Juan Velez</a>
 */
package com.chibchasoft.vertx.util.concurrent;

import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An ExecutorService that uses a {@link Context} to schedule execution of tasks.
 * {@code Submit} methods return {@link ContextFuture} instances. It is strongly recommended that
 * {@code ContextFuture} instances returned by this {@code ExecutorService} do NOT use the
 * {@link ContextFuture#get} methods but use the corresponding {@link Future#setHandler(Handler)}
 * method when used within a Vert.x {@link Context}
 */
public class ContextExecutorService extends AbstractExecutorService {
    /**
     * The {@link Context}
     */
    private final Context context;

    /**
     * To protect shutdown and runningTasksCount
     */
    private final Lock theLock = new ReentrantLock();

    /**
     * To be used when all tasks have terminated
     */
    private final Condition terminatedCondition = theLock.newCondition();

    /**
     * Keeps track of the number of tasks currently running.
     */
    private AtomicInteger runningTasksCount = new AtomicInteger(0);

    /**
     * Whether this {@code ExecutorService} is shutdown or not.
     */
    private volatile boolean shutdown = false;

    /**
     * Queue used to enqueue the commands to execute. Needed to comply with the contract for {@link ExecutorService#shutdownNow}
     */
    private BlockingQueue<Runnable> queue = new LinkedBlockingQueue<>();

    /**
     * Creates a new {@code ContextExecutorService} using the provided {@link Context}
     * @param context The {@link Context}
     */
    public ContextExecutorService(Context context) {
        assert context != null;
        this.context = context;
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        return new ContextFuture<T>(runnable, value);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return new ContextFuture<T>(callable);
    }

    @Override
    public void shutdown() {
        theLock.lock();
        try {
            shutdown = true;
        } finally {
            theLock.unlock();
        }
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        List<Runnable> remaining = new ArrayList<>();
        queue.drainTo(remaining);
        return remaining;
    }

    @Override
    public boolean isShutdown() {
        return shutdown;
    }

    @Override
    public boolean isTerminated() {
        theLock.lock();
        try {
            return shutdown && runningTasksCount.get() == 0;
        } finally {
            theLock.unlock();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        theLock.lock();
        try {
            long nanosLeftToWait = unit.toNanos(timeout);
            for (; ; ) {
                // Check if there is any time left to wait
                if (nanosLeftToWait <= 0) {
                    return false;
                } else if (isTerminated()) {
                    return true;
                } else {
                    // We need to wait for all tasks to be terminated
                    nanosLeftToWait = terminatedCondition.awaitNanos(nanosLeftToWait);
                }
            }
        } finally {
            theLock.unlock();
        }
    }

    @Override
    public void execute(Runnable command) {
        theLock.lock();

        try {
            // Checked whether it's shutdown.
            if (isShutdown()) {
                throw new RejectedExecutionException("ExecutorService already shutdown");
            }

            // Reject if the queue is full
            if (!queue.offer(command))
                throw new RejectedExecutionException("Queue full");
        } finally {
            theLock.unlock();
        }

        // "Enqueue" with the context a task to execute from the queue
        context.runOnContext(v -> executeFromTheQueue());
    }

    /**
     * Executes the command from the head of the queue if any
     */
    private void executeFromTheQueue() {
        Runnable command = queue.poll();
        if (command != null) {
            // Increase the running tasks count
            runningTasksCount.incrementAndGet();

            command.run();

            // Command executed. Decrease the running tasks count
            runningTasksCount.decrementAndGet();

            // If the ExecutorService is terminated, notify the terminatedCondition
            theLock.lock();
            try {
                if (isTerminated()) {
                    terminatedCondition.signalAll();
                }
            } finally {
                theLock.unlock();
            }
        }
    }
}
