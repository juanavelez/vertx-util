/*
 * Copyright (c) 2017 chibchasoft.com
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
package com.chibchasoft.vertx.util;

import com.chibchasoft.vertx.util.concurrent.ConcurrentReferenceHashMap;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Context;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.TaskQueue;
import io.vertx.test.core.VertxTestBase;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Test VertxUtil
 *
 * @author <a href="mailto:jvelez@chibchasoft.com">Juan Velez</a>
 */
public class VertxUtilTest extends VertxTestBase {
    @Override
    public void tearDown() throws Exception {
        super.tearDown();
        // Force GC
        System.gc();
        ConcurrentReferenceHashMap<Context, ConcurrentReferenceHashMap<Object, TaskQueue>> contextMap =
                VertxUtil.TASK_QUEUES;
        for (Context context : contextMap.keySet()) {
            throw new IllegalStateException("Found context " + context + " left in VertxUtil.TASK_QUEUES after GC");
        }
    }

    @Test(expected = IllegalStateException.class)
    public void testExecuteBlockingNoContext() {
        VertxUtil.executeBlocking("Hello,", fut -> {
            throw new RuntimeException("We should not have gotten here");
        }, res -> {
            throw new RuntimeException("We should not have gotten here");
        });
    }

    /**
     * Test {@link VertxUtil#executeBlocking(Object, Handler, Handler)}
     */
    @Test
    public void testExecuteBlockingWithinNonWorkerContext() {
        ContextInternal context = (ContextInternal) vertx.getOrCreateContext();
        waitFor(4);
        context.runOnContext( v -> {
            CountDownLatch latch1 = new CountDownLatch(1);
            CountDownLatch latch2 = new CountDownLatch(1);
            CountDownLatch latch3 = new CountDownLatch(1);
            CountDownLatch latch4 = new CountDownLatch(1);

            String queue1 = "queue1";
            String queue2 = "queue2";

            AtomicReference<TaskQueue> taskQueue1 = new AtomicReference<>(null);
            AtomicReference<TaskQueue> taskQueue2 = new AtomicReference<>(null);

            VertxUtil.executeBlocking(queue1, fut -> {
                @SuppressWarnings("unchecked")
                ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                assertNotNull(taskQueues);
                taskQueue1.set(taskQueues.get(queue1));
                assertNotNull(taskQueue1.get());

                try {
                    awaitLatch(latch3);
                    latch1.countDown();
                    fut.complete();
                } catch (Exception e) {
                    fut.fail(e);
                }
            }, ar -> {
                assertTrue(ar.succeeded());
                complete();
            });

            VertxUtil.executeBlocking(queue1, fut -> {
                assertNotNull(taskQueue1);
                ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                assertNotNull(taskQueues);
                assertEquals(taskQueue1.get(), taskQueues.get(queue1));

                try {
                    latch1.await();
                    latch2.countDown();
                    fut.complete();
                } catch (Exception e) {
                    fut.fail(e);
                }
            }, ar -> {
                assertTrue(ar.succeeded());
                complete();
            });

            VertxUtil.executeBlocking(queue2, fut -> {
                ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                assertNull(taskQueue2.get());
                taskQueue2.set(taskQueues.get(queue2));
                assertNotNull(taskQueue2.get());

                latch3.countDown();
                fut.complete();
            }, ar -> {
                assertTrue(ar.succeeded());
                complete();
            });

            VertxUtil.executeBlocking(queue2, fut -> {
                assertNotNull(taskQueue2);
                ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                assertNotNull(taskQueues);
                assertNotNull(taskQueue2.get());
                assertEquals(taskQueue2.get(), taskQueues.get(queue2));

                try {
                    latch3.await();
                    latch4.countDown();
                    fut.complete();
                } catch (Exception e) {
                    fut.fail(e);
                }
            }, ar -> {
                assertTrue(ar.succeeded());
                complete();
            });
        });
        await();
    }

    /**
     * Test {@link VertxUtil#executeBlocking(Object, Handler, Handler)} executing from within a Worker Verticle
     */
    @Test
    public void testExecuteBlockingWithinWorkerVerticle() {
        waitFor(4);
        vertx.deployVerticle(new AbstractVerticle() {
            @Override
            public void start() throws Exception {
                CountDownLatch latch1 = new CountDownLatch(1);
                CountDownLatch latch2 = new CountDownLatch(1);
                CountDownLatch latch3 = new CountDownLatch(1);
                CountDownLatch latch4 = new CountDownLatch(1);

                String queue1 = "queue1";
                String queue2 = "queue2";

                AtomicReference<TaskQueue> taskQueue1 = new AtomicReference<>(null);
                AtomicReference<TaskQueue> taskQueue2 = new AtomicReference<>(null);

                VertxUtil.executeBlocking(queue1, fut -> {
                    ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                    assertNotNull(taskQueues);
                    taskQueue1.set(taskQueues.get(queue1));
                    assertNotNull(taskQueue1.get());

                    try {
                        awaitLatch(latch3);
                        latch1.countDown();
                        fut.complete();
                    } catch (Exception e) {
                        fut.fail(e);
                    }
                }, ar -> {
                    assertTrue(ar.succeeded());
                    complete();
                });

                VertxUtil.executeBlocking(queue1, fut -> {
                    assertNotNull(taskQueue1);
                    ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                    assertNotNull(taskQueues);
                    assertEquals(taskQueue1.get(), taskQueues.get(queue1));

                    try {
                        latch1.await();
                        latch2.countDown();
                        fut.complete();
                    } catch (Exception e) {
                        fut.fail(e);
                    }
                }, ar -> {
                    assertTrue(ar.succeeded());
                    complete();
                });

                VertxUtil.executeBlocking(queue2, fut -> {
                    ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                    assertNull(taskQueue2.get());
                    taskQueue2.set(taskQueues.get(queue2));
                    assertNotNull(taskQueue2.get());

                    latch3.countDown();
                    fut.complete();
                }, ar -> {
                    assertTrue(ar.succeeded());
                    complete();
                });

                VertxUtil.executeBlocking(queue2, fut -> {
                    assertNotNull(taskQueue2);
                    ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                    assertNotNull(taskQueues);
                    assertNotNull(taskQueue2.get());
                    assertEquals(taskQueue2.get(), taskQueues.get(queue2));

                    try {
                        latch3.await();
                        latch4.countDown();
                        fut.complete();
                    } catch (Exception e) {
                        fut.fail(e);
                    }
                }, ar -> {
                    assertTrue(ar.succeeded());
                    complete();
                });
            }
        }, new DeploymentOptions().setWorker(true));
        await();
    }

    @Test(expected = IllegalStateException.class)
    public void testExecuteBlockingContextNoContext() {
        VertxUtil.executeBlocking(null, "Hello,", fut -> {
            throw new RuntimeException("We should not have gotten here");
        }, res -> {
            throw new RuntimeException("We should not have gotten here");
        });
    }

    /**
     * Test {@link VertxUtil#executeBlocking(Context, Object, Handler, Handler)}
     */
    @Test
    public void testExecuteBlockingContextWithinNonWorkerContext() {
        Context context = vertx.getOrCreateContext();

        waitFor(4);
        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);
        CountDownLatch latch4 = new CountDownLatch(1);

        String queue1 = "queue1";
        String queue2 = "queue2";

        AtomicReference<TaskQueue> taskQueue1 = new AtomicReference<>(null);
        AtomicReference<TaskQueue> taskQueue2 = new AtomicReference<>(null);

        VertxUtil.executeBlocking(context, queue1, fut -> {
            @SuppressWarnings("unchecked")
            ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
            assertNotNull(taskQueues);
            taskQueue1.set(taskQueues.get(queue1));
            assertNotNull(taskQueue1.get());

            try {
                awaitLatch(latch3);
                latch1.countDown();
                fut.complete();
            } catch (Exception e) {
                fut.fail(e);
            }
        }, ar -> {
            assertTrue(ar.succeeded());
            complete();
        });

        VertxUtil.executeBlocking(context, queue1, fut -> {
            assertNotNull(taskQueue1);
            ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
            assertNotNull(taskQueues);
            assertEquals(taskQueue1.get(), taskQueues.get(queue1));

            try {
                latch1.await();
                latch2.countDown();
                fut.complete();
            } catch (Exception e) {
                fut.fail(e);
            }
        }, ar -> {
            assertTrue(ar.succeeded());
            complete();
        });

        VertxUtil.executeBlocking(context, queue2, fut -> {
            ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
            assertNull(taskQueue2.get());
            taskQueue2.set(taskQueues.get(queue2));
            assertNotNull(taskQueue2.get());

            latch3.countDown();
            fut.complete();
        }, ar -> {
            assertTrue(ar.succeeded());
            complete();
        });

        VertxUtil.executeBlocking(context, queue2, fut -> {
            assertNotNull(taskQueue2);
            ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
            assertNotNull(taskQueues);
            assertNotNull(taskQueue2.get());
            assertEquals(taskQueue2.get(), taskQueues.get(queue2));

            try {
                latch3.await();
                latch4.countDown();
                fut.complete();
            } catch (Exception e) {
                fut.fail(e);
            }
        }, ar -> {
            assertTrue(ar.succeeded());
            complete();
        });

        await();
    }

    /**
     * Test {@link VertxUtil#executeBlocking(Context, Object, Handler, Handler)} executing using a Worker Verticle
     */
    @Test
    public void testExecuteBlockingContextWithinWorkerVerticle() {
        waitFor(4);
        vertx.deployVerticle(new AbstractVerticle() {
            @Override
            public void start() throws Exception {
                CountDownLatch latch1 = new CountDownLatch(1);
                CountDownLatch latch2 = new CountDownLatch(1);
                CountDownLatch latch3 = new CountDownLatch(1);
                CountDownLatch latch4 = new CountDownLatch(1);

                String queue1 = "queue1";
                String queue2 = "queue2";

                AtomicReference<TaskQueue> taskQueue1 = new AtomicReference<>(null);
                AtomicReference<TaskQueue> taskQueue2 = new AtomicReference<>(null);

                VertxUtil.executeBlocking(context, queue1, fut -> {
                    ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                    assertNotNull(taskQueues);
                    taskQueue1.set(taskQueues.get(queue1));
                    assertNotNull(taskQueue1.get());

                    try {
                        awaitLatch(latch3);
                        latch1.countDown();
                        fut.complete();
                    } catch (Exception e) {
                        fut.fail(e);
                    }
                }, ar -> {
                    assertTrue(ar.succeeded());
                    complete();
                });

                VertxUtil.executeBlocking(context, queue1, fut -> {
                    assertNotNull(taskQueue1);
                    ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                    assertNotNull(taskQueues);
                    assertEquals(taskQueue1.get(), taskQueues.get(queue1));

                    try {
                        latch1.await();
                        latch2.countDown();
                        fut.complete();
                    } catch (Exception e) {
                        fut.fail(e);
                    }
                }, ar -> {
                    assertTrue(ar.succeeded());
                    complete();
                });

                VertxUtil.executeBlocking(context, queue2, fut -> {
                    ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                    assertNull(taskQueue2.get());
                    taskQueue2.set(taskQueues.get(queue2));
                    assertNotNull(taskQueue2.get());

                    latch3.countDown();
                    fut.complete();
                }, ar -> {
                    assertTrue(ar.succeeded());
                    complete();
                });

                VertxUtil.executeBlocking(context, queue2, fut -> {
                    assertNotNull(taskQueue2);
                    ConcurrentReferenceHashMap<Object, TaskQueue> taskQueues = getTaskQueues(Vertx.currentContext());
                    assertNotNull(taskQueues);
                    assertNotNull(taskQueue2.get());
                    assertEquals(taskQueue2.get(), taskQueues.get(queue2));

                    try {
                        latch3.await();
                        latch4.countDown();
                        fut.complete();
                    } catch (Exception e) {
                        fut.fail(e);
                    }
                }, ar -> {
                    assertTrue(ar.succeeded());
                    complete();
                });
            }
        }, new DeploymentOptions().setWorker(true));
        await();
    }

    private ConcurrentReferenceHashMap<Object, TaskQueue> getTaskQueues(Context context) {
        ConcurrentReferenceHashMap<Context, ConcurrentReferenceHashMap<Object, TaskQueue>> contextMap =
                VertxUtil.TASK_QUEUES;
        assertNotNull(contextMap);
        return contextMap.get(context);
    }
}
