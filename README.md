# Vert.x-Util

[![Build Status](https://travis-ci.org/juanavelez/vertx-util.svg?branch=master)](https://travis-ci.org/juanavelez/vertx-util)

A Collection of miscellaneous Vert.x-related methods

## Dependencies

Vert.x-Util requires Vert.x-Core version 3.4.1 or up.

## Methods

### VertxUtil.executeBlocking(identifier, blockingCodeHandler, resultHandler)

This method is similar to the existing `Context.executeBlocking(blockingCodeHandler, resultHandler)` but differs in that allows the blocking code handler to be assigned to (and executed by) a <a href="https://github.com/eclipse/vert.x/blob/master/src/main/java/io/vertx/core/impl/TaskQueue.java">TaskQueue</a> associated to the provided identifier (and current `Context`). A `TaskQueue` is created if it is not already mapped to the provided identifier (and `Context`), otherwise the existing `TaskqQueue` is used. The `TaskQueue` utilizes the same `Executor` associated to the current `Context` (which comes from the `WorkerPool` assigned to the `Context`). The mapping of `Context`s and identifiers to `TaskQueue`s is done via a weak hash map, which does not prevent the identifiers as well as the `TaskQueue`s from being Garbage Collected.

Calling this method more than once for the same identifier (and current `Context`), effectively results in the blocking code handler being executed sequentially with other previous blocking code handlers for the same identifer (and current `Context`) as it would normally happen with `Context.executeBlocking(blockingCodeHandler, true, resultHandler)`). There are no ordering guarantees, however, in relation to the execution of other blocking code handlers associated to different identifiers or even the same identifier but under a different `Context`.

**NOTES:** 

* This method needs to be called within the scope of an existing Vert.x `Context`.
* This method makes use of a Vertx internal API method: `ContextInternal.executeBlocking(Handler, TaskQueue, Handler)`

### VertxUtil.executeBlocking(context, identifier, blockingCodeHandler, resultHandler)

Similar to `VertxUtil.executeBlocking(identifier, blockingCodeHandler, resultHandler)` but using the provided context.

## Maven ##

Vert.x-Util is published to the [maven public repo](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.chibchasoft%22%20AND%20a%3A%22vertx-util%22).

Add the vertx-util dependency to your project, in case of maven like this:

```xml
        <dependency>
            <groupId>com.chibchasoft</groupId>
            <artifactId>vertx-util</artifactId>
            <version>1.1.1</version>
        </dependency>
```

