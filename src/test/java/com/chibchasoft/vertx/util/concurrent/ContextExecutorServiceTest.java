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

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test ContextExecutorService and ContextFuture
 *
 * @author <a href="mailto:jvelez@chibchasoft.com">Juan Velez</a>
 */
public class ContextExecutorServiceTest extends VertxTestBase {
    @Test
    public void testRunOnContextSubmitOneRunnableFinishesSuccessfully() throws InterruptedException {
        Context context = vertx.getOrCreateContext();
        ExecutorService executorService = new ContextExecutorService(context);
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);

        context.runOnContext(v -> {
            thread.compareAndSet(null, Thread.currentThread());
            assertTrue(latch.getCount() == 2);
            latch.countDown();

            executorService.submit(() -> {
                assertTrue(thread.get() == Thread.currentThread());
                assertTrue(latch.getCount() == 1);
                latch.countDown();
            });
        });

        awaitLatch(latch);
    }

    @Test
    public void testRunOnContextSubmitMultipleRunnablesFinishSuccessfully() throws InterruptedException {
        Context context = vertx.getOrCreateContext();
        ExecutorService executorService = new ContextExecutorService(context);
        AtomicReference<Thread> thread = new AtomicReference<>();

        int total = 3;
        CountDownLatch latch = new CountDownLatch(total);

        context.runOnContext(v -> {
            thread.compareAndSet(null, Thread.currentThread());
            assertTrue(latch.getCount() == total);
            latch.countDown();

            for (int i = 1; i <= total; i++) {
                int j= total - i;
                executorService.submit(() -> {
                    assertTrue(thread.get() == Thread.currentThread());
                    assertEquals(latch.getCount(), j);
                    latch.countDown();
                });
            }
        });

        awaitLatch(latch);
    }

    @Test
    public void testRunWithinEventLoopVerticleSubmitOneRunnableFinishesSuccessfully() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        vertx.deployVerticle(new AbstractVerticle() {
            @Override
            public void start() {
                ExecutorService executorService = new ContextExecutorService(context);
                Thread thread = Thread.currentThread();

                assertTrue(latch.getCount() == 2);
                latch.countDown();

                executorService.submit(() -> {
                    assertTrue(thread == Thread.currentThread());
                    assertTrue(latch.getCount() == 1);
                    latch.countDown();
                });
            }
        });

        awaitLatch(latch);
    }

    @Test
    public void testWithinEventLoopVerticleSubmitMultipleRunnablesFinishSuccessfully() throws InterruptedException {
        int total = 3;
        CountDownLatch latch = new CountDownLatch(total);

        vertx.deployVerticle(new AbstractVerticle() {
            @Override
            public void start() {
                ExecutorService executorService = new ContextExecutorService(context);
                Thread thread = Thread.currentThread();

                for (int i = 0; i < total; i++) {
                    int j = total - i;
                    executorService.submit(() -> {
                        assertTrue(thread == Thread.currentThread());
                        assertEquals(latch.getCount(), j);
                        latch.countDown();
                    });
                }
            }
        });

        awaitLatch(latch);
    }

    @Test
    public void testRunWithinWorkerVerticleSubmitOneRunnableFinishesSuccessfully() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        vertx.deployVerticle(new AbstractVerticle() {
            @Override
            public void start() {
                ExecutorService executorService = new ContextExecutorService(context);
                Thread thread = Thread.currentThread();

                assertTrue(latch.getCount() == 2);
                latch.countDown();

                executorService.submit(() -> {
                    assertTrue(thread == Thread.currentThread());
                    assertTrue(latch.getCount() == 1);
                    latch.countDown();
                });
            }
        }, new DeploymentOptions().setWorker(true));

        awaitLatch(latch);
    }

    @Test
    public void testRunWithinWorkerVerticleSubmitMultipleRunnablesFinishSuccessfully() throws InterruptedException {
        int total = 3;
        CountDownLatch latch = new CountDownLatch(total);

        vertx.deployVerticle(new AbstractVerticle() {
            @Override
            public void start() {
                ExecutorService executorService = new ContextExecutorService(context);
                Thread thread = Thread.currentThread();

                for (int i = 0; i < total; i++) {
                    int j = total - i;
                    executorService.submit(() -> {
                        assertTrue(thread == Thread.currentThread());
                        assertEquals(latch.getCount(), j);
                        latch.countDown();
                    });
                }
            }
        }, new DeploymentOptions().setWorker(true));

        awaitLatch(latch);
    }

    @Test
    public void testContextFutureRunnableNormalCompletion() throws InterruptedException {
        Context context = vertx.getOrCreateContext();
        ExecutorService executorService = new ContextExecutorService(context);
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(3);

        context.runOnContext(v -> {
            thread.compareAndSet(null, Thread.currentThread());
            assertTrue(latch.getCount() == 3);
            latch.countDown();

            Future<?> rf = executorService.submit(() -> {
                assertTrue(thread.get() == Thread.currentThread());
                assertTrue(latch.getCount() == 2);
                latch.countDown();
            });

            assertTrue(rf instanceof ContextFuture);

            ContextFuture<?> cf = (ContextFuture<?>) rf;
            cf.setHandler(ar -> {
                thread.compareAndSet(null, Thread.currentThread());
                assertTrue(ar.succeeded());
                assertFalse(ar.failed());
                assertTrue(ar.result() == null);
                assertTrue(ar.cause() == null);
                assertTrue(latch.getCount() == 1);
                latch.countDown();
            });
        });

        awaitLatch(latch);
    }

    @Test
    public void testContextFutureRunnableCancel() throws InterruptedException {
        Context context = vertx.getOrCreateContext();
        ExecutorService executorService = new ContextExecutorService(context);
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(2);

        context.runOnContext(v -> {
            thread.compareAndSet(null, Thread.currentThread());
            assertTrue(latch.getCount() == 2);
            latch.countDown();

            Future<?> rf = executorService.submit(() -> assertTrue(false));

            assertTrue(rf instanceof ContextFuture);

            assertTrue(rf.cancel(false));

            ContextFuture<?> cf = (ContextFuture<?>) rf;
            cf.setHandler(ar -> {
                thread.compareAndSet(null, Thread.currentThread());
                assertFalse(ar.succeeded());
                assertTrue(ar.failed());
                assertTrue(ar.result() == null);
                assertTrue(ar.cause() == null);
                assertTrue(latch.getCount() == 1);
                latch.countDown();
            });
        });

        awaitLatch(latch);
    }

    @Test
    public void testContextFutureRunnableExceptionalCompletion() throws InterruptedException {
        Context context = vertx.getOrCreateContext();
        ExecutorService executorService = new ContextExecutorService(context);
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(3);

        context.runOnContext(v -> {
            thread.compareAndSet(null, Thread.currentThread());
            assertTrue(latch.getCount() == 3);
            latch.countDown();

            Future<?> rf = executorService.submit(() -> {
                assertTrue(thread.get() == Thread.currentThread());
                assertTrue(latch.getCount() == 2);
                latch.countDown();
                throw new RuntimeException("error");
            });

            assertTrue(rf instanceof ContextFuture);

            ContextFuture<?> cf = (ContextFuture<?>) rf;
            cf.setHandler(ar -> {
                thread.compareAndSet(null, Thread.currentThread());
                assertFalse(ar.succeeded());
                assertTrue(ar.failed());
                assertTrue(ar.result() == null);
                assertTrue(ar.cause() instanceof RuntimeException);
                assertTrue(ar.cause().getMessage().equals("error"));
                assertTrue(latch.getCount() == 1);
                latch.countDown();
            });
        });

        awaitLatch(latch);
    }

    @Test
    public void testContextFutureCallableNormalCompletion() throws InterruptedException {
        Context context = vertx.getOrCreateContext();
        ExecutorService executorService = new ContextExecutorService(context);
        AtomicReference<Thread> thread = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(3);

        context.runOnContext(v -> {
            thread.compareAndSet(null, Thread.currentThread());
            assertTrue(latch.getCount() == 3);
            latch.countDown();

            Future<?> rf = executorService.submit(() -> {
                assertTrue(thread.get() == Thread.currentThread());
                assertTrue(latch.getCount() == 2);
                latch.countDown();
                return 1;
            });

            assertTrue(rf instanceof ContextFuture);

            ContextFuture<Integer> cf = (ContextFuture<Integer>) rf;
            cf.setHandler(ar -> {
                thread.compareAndSet(null, Thread.currentThread());
                assertTrue(ar.succeeded());
                assertFalse(ar.failed());
                assertEquals(ar.result(), Integer.valueOf(1));
                assertTrue(ar.cause() == null);
                assertTrue(latch.getCount() == 1);
                latch.countDown();
            });
        });

        awaitLatch(latch);
    }
}
