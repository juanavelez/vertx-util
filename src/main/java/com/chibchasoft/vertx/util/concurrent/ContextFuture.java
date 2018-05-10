/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Sun designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Sun in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 */
/*
 * This file is available under and governed by the GNU General Public
 * License version 2 only, as published by the Free Software Foundation.
 * However, the following notice accompanied the original version of this
 * file:
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/licenses/publicdomain
 */
/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */
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

import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.impl.NoStackTraceThrowable;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

/**
 * An implementation of {@link RunnableFuture} and {@link Future}.
 * Based on {@link FutureTask} and {@code io.vertx.core.impl.FutureImpl}
 * This is the {@link RunnableFuture} returned by {@link ContextExecutorService}.
 * When this {@code RunnableFuture} is used within a Context execution, it is
 * strongly recommended not to use the {@link java.util.concurrent.Future#get()}
 * methods as these will <b>block</b> the {@link Context} indefinitely if
 * executed within an {@code EventLoopContext} or a non-multi-threaded
 * {@code WorkerContext}.
 * @param <T> The result type returned by this ContextFuture's {@code get} methods
 */
public class ContextFuture<T> implements RunnableFuture<T>, Future<T> {
    /**
     * The run state of this task, initially NEW.  The run state
     * transitions to a terminal state only in methods set,
     * setException, and cancel.  During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)). Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    private volatile int state;
    private static final int NEW          = 0;
    private static final int COMPLETING   = 1;
    private static final int NORMAL       = 2;
    private static final int EXCEPTIONAL  = 3;
    private static final int CANCELLED    = 4;
    private static final int INTERRUPTING = 5;
    private static final int INTERRUPTED  = 6;

    /** The underlying callable; nulled out after running */
    private Callable<T> callable;
    /** The result to return or exception to throw from get() */
    private Object outcome; // non-volatile, protected by state reads/writes
    /** The thread running the callable; CASed during run() */
    private volatile Thread runner;
    /** Treiber stack of waiting threads */
    private volatile WaitNode waiters;

    /**
     * Returns result or throws exception for completed task.
     *
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private T report(int s) throws ExecutionException {
        Object x = outcome;
        if (s == NORMAL)
            return (T)x;
        if (s >= CANCELLED)
            throw new CancellationException();
        throw new ExecutionException((Throwable)x);
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public ContextFuture(Callable<T> callable) {
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Runnable}, and arrange that {@code get} will return the
     * given result on successful completion.
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If
     * you don't need a particular result, consider using
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     */
    public ContextFuture(Runnable runnable, T result) {
        this.callable = Executors.callable(runnable, result);
        this.state = NEW;       // ensure visibility of callable
    }

    @Override
    public boolean isCancelled() {
        return state >= CANCELLED;
    }

    @Override
    public boolean isDone() {
        return state != NEW;
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        if (!(state == NEW &&
                UNSAFE.compareAndSwapInt(this, stateOffset, NEW,
                        mayInterruptIfRunning ? INTERRUPTING : CANCELLED)))
            return false;
        try {    // in case call to interrupt throws exception
            if (mayInterruptIfRunning) {
                try {
                    Thread t = runner;
                    if (t != null)
                        t.interrupt();
                } finally { // final state
                    UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED);
                }
            }
        } finally {
            finishCompletion();
        }
        return true;
    }

    @Override
    public T get() throws InterruptedException, ExecutionException {
        int s = state;
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);
        return report(s);
    }

    @Override
    public T get(long timeout, TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null)
            throw new NullPointerException();
        int s = state;
        if (s <= COMPLETING &&
                (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
            throw new TimeoutException();
        return report(s);
    }

    /**
     * Protected method invoked when this task transitions to state
     * {@code isDone} (whether normally or via cancellation). The
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() {
        // Execute any handlers
        Handler<AsyncResult<T>> h = handler.get();
        if (h != null) {
            h.handle(this);
        }
    }

    /**
     * Sets the result of this future to the given value unless
     * this future has already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(T v) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            finishCompletion();
        }
    }

    /**
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            finishCompletion();
        }
    }

    @Override
    public void run() {
        if (state != NEW ||
                !UNSAFE.compareAndSwapObject(this, runnerOffset,
                        null, Thread.currentThread()))
            return;
        try {
            Callable<T> c = callable;
            if (c != null && state == NEW) {
                T result;
                boolean ran;
                try {
                    result = c.call();
                    ran = true;
                } catch (Throwable ex) {
                    result = null;
                    ran = false;
                    setException(ex);
                }
                if (ran)
                    set(result);
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            int s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only
     * delivered to a task while in run or runAndReset.
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a
        // chance to interrupt us.  Let's spin-wait patiently.
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber
     * stack.  See other classes such as Phaser and SynchronousQueue
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }

    /**
     * Removes and signals all waiting threads, invokes done(), and
     * nulls out callable.
     */
    private void finishCompletion() {
        // assert state > COMPLETING;
        for (WaitNode q; (q = waiters) != null;) {
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;
                        LockSupport.unpark(t);
                    }
                    WaitNode next = q.next;
                    if (next == null)
                        break;
                    q.next = null; // unlink to help gc
                    q = next;
                }
                break;
            }
        }

        done();

        callable = null;        // to reduce footprint
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
            throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (;;) {
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            int s = state;
            if (s > COMPLETING) {
                if (q != null)
                    q.thread = null;
                return s;
            }
            else if (s == COMPLETING) // cannot time out yet
                Thread.yield();
            else if (q == null)
                q = new WaitNode();
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                        q.next = waiters, q);
            else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                LockSupport.parkNanos(this, nanos);
            }
            else
                LockSupport.park(this);
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid
     * accumulating garbage.  Internal nodes are simply unspliced
     * without CAS since it is harmless if they are traversed anyway
     * by releasers.  To avoid effects of unsplicing from already
     * removed nodes, the list is retraversed in case of an apparent
     * race.  This is slow when there are a lot of nodes, but we don't
     * expect lists to be long enough to outweigh higher-overhead
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            for (;;) {          // restart on removeWaiter race
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        pred = q;
                    else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) // check for race
                            continue retry;
                    }
                    else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                            q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    // Unsafe mechanics
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = getUnsafe();
            Class<?> k = ContextFuture.class;
            stateOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                    (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

    /**
     * Implementation section of {@link io.vertx.core.impl.FutureImpl}
     */
    private AtomicReference<Handler<AsyncResult<T>>> handler = new AtomicReference<>();

    @Override
    public boolean isComplete() {
        return isDone();
    }

    @Override
    public Future<T> setHandler(Handler<AsyncResult<T>> handler) {
        Handler<AsyncResult<T>> prevHandler;
        do {
            prevHandler = this.handler.get();
        } while (!this.handler.compareAndSet(prevHandler, handler));

        boolean callHandler = isComplete();
        if (callHandler) {
            handler.handle(this);
        }
        return this;
    }

    @Override
    public void complete(T result) {
        if (!tryComplete(result)) {
            int s = state;
            throw new IllegalStateException("Result is already complete: " + (s == NORMAL ? "succeeded" : "failed"));
        }
    }

    @Override
    public void complete() {
        if (!tryComplete()) {
            int s = state;
            throw new IllegalStateException("Result is already complete: " + (s == NORMAL ? "succeeded" : "failed"));
        }
    }

    @Override
    public void fail(Throwable cause) {
        if (!tryFail(cause)) {
            int s = state;
            throw new IllegalStateException("Result is already complete: " + (s == NORMAL ? "succeeded" : "failed"));
        }
    }

    @Override
    public void fail(String failureMessage) {
        if (!tryFail(failureMessage)) {
            int s = state;
            throw new IllegalStateException("Result is already complete: " + (s == NORMAL ? "succeeded" : "failed"));
        }
    }

    @Override
    public boolean tryComplete(T result) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = result;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            finishCompletion();

            Handler<AsyncResult<T>> h = handler.get();
            if (h != null) {
                 h.handle(this);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean tryComplete() {
        return tryComplete(null);
    }

    @Override
    public boolean tryFail(Throwable cause) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = cause != null ? cause : new NoStackTraceThrowable(null);
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            finishCompletion();

            Handler<AsyncResult<T>> h = handler.get();
            if (h != null) {
                h.handle(this);
            }

            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean tryFail(String failureMessage) {
        return tryFail(new NoStackTraceThrowable(failureMessage));
    }

    @Override
    public T result() {
        int s = state;
        return (s == NORMAL) ? (T) outcome : null;
    }

    @Override
    public Throwable cause() {
        int s = state;
        return s >= EXCEPTIONAL ? (Throwable) outcome : null;
    }

    @Override
    public boolean succeeded() {
        int s = state;
        return s == NORMAL;
    }

    @Override
    public boolean failed() {
        int s = state;
        return s >= EXCEPTIONAL;
    }

    @Override
    public void handle(AsyncResult<T> asyncResult) {
        if (asyncResult.succeeded()) {
            complete(asyncResult.result());
        } else {
            fail(asyncResult.cause());
        }
    }

    private static Unsafe getUnsafe() throws NoSuchFieldException, IllegalAccessException {
        Field singleoneInstanceField = Unsafe.class.getDeclaredField("theUnsafe");
        singleoneInstanceField.setAccessible(true);
        return (Unsafe) singleoneInstanceField.get(null);
    }
}
