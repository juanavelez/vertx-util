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
import io.vertx.core.AsyncResult;
import io.vertx.core.Context;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.TaskQueue;

/**
 * Collection of Vertx Utility methods.
 *
 * @author <a href="mailto:jvelez@chibchasoft.com">Juan Velez</a>
 */
public class VertxUtil {
    /**
     * {@link Context}s are mapped to Map of {@link Object}s to {@link TaskQueue}s
     */
    private static final ConcurrentReferenceHashMap<Context, ConcurrentReferenceHashMap<Object, TaskQueue>> TASK_QUEUES =
            new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK);

    /**
     * <p>Similar to {@link Context#executeBlocking(Handler, Handler)} but when this method is called several times on
     * the same {@link Context} for the same {@code identifier}, executions associated to that value for that context
     * will be executed serially. However, there will be no ordering guarantees in relation to executions for different
     * identifiers for the same context or even for the same identifier but for different contexts.</p>
     *
     * <p><b>NOTES:</b></p>
     *
     * - This method needs to be called within the scope of a Vertx Context.<br>
     * - This method relies on a Vertx internal API method
     * {@link ContextInternal#executeBlocking(Handler, TaskQueue, Handler)}.<br>
     *
     * @param identifier          Object used to group and serialize executions
     * @param blockingCodeHandler handler representing the blocking code to run
     * @param resultHandler       handler that will be called when the blocking code is complete
     * @param <T>                 the type of the result
     */
    public static <T> void executeBlocking(Object identifier, Handler<Future<T>> blockingCodeHandler,
                                           Handler<AsyncResult<T>> resultHandler) {
        executeBlocking(Vertx.currentContext(), identifier, blockingCodeHandler, resultHandler);
    }

    /**
     * <p>Similar to {@link Context#executeBlocking(Handler, Handler)} but when this method is called several times on
     * the same provided {@code Context} for the same {@code identifier}, executions associated to that value for that
     * {@code context} will be executed serially. However, there will be no ordering guarantees in relation to
     * executions for different identifiers for the same {@code context} or even for the same identifier but for
     * different {@code context}s.</p>
     *
     * <p><b>NOTE:</b> This method relies on a Vertx internal API method
     * {@link ContextInternal#executeBlocking(Handler, TaskQueue, Handler)}</p>
     *
     * @param context             The {@link Context} to be used to execute the blocking code
     * @param identifier          Object used to group and serialize executions
     * @param blockingCodeHandler handler representing the blocking code to run
     * @param resultHandler       handler that will be called when the blocking code is complete
     * @param <T>                 the type of the result
     */
    public static <T> void executeBlocking(Context context, Object identifier, Handler<Future<T>> blockingCodeHandler,
                                           Handler<AsyncResult<T>> resultHandler) {
        if (context == null)
            throw new IllegalStateException("A context is required");
        if (identifier == null)
            throw new IllegalArgumentException("An identifier is required");
        ContextInternal contextInternal = (ContextInternal) context;

        TaskQueue taskQueue = TASK_QUEUES.computeIfAbsent(context, k ->
                new ConcurrentReferenceHashMap<>(16, ConcurrentReferenceHashMap.ReferenceType.WEAK))
                .computeIfAbsent(identifier, k -> new TaskQueue());

        contextInternal.executeBlocking(blockingCodeHandler, taskQueue, resultHandler);
    }
}
