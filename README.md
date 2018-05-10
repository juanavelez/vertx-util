# Vert.x-Util

[![Build Status](https://travis-ci.org/juanavelez/vertx-util.svg?branch=master)](https://travis-ci.org/juanavelez/vertx-util)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.chibchasoft/vertx-util/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Ccom.chibchasoft.vertx-util)
[![codecov](https://codecov.io/gh/juanavelez/vertx-util/branch/master/graph/badge.svg)](https://codecov.io/gh/juanavelez/vertx-util)

A Collection of miscellaneous Vert.x-related methods

## Dependencies

Vert.x-Util requires Vert.x-Core version 3.4.1 or up.

## Classes

### com.chibchasoft.vertx.util.VertxUtil

A collection of utility methods related to Vert.x

#### VertxUtil.executeBlocking(identifier, blockingCodeHandler, resultHandler)

This method is similar to the existing `Context.executeBlocking(blockingCodeHandler, resultHandler)` but differs 
in that allows the blocking code handler to be assigned to (and executed by) a
<a href="https://github.com/eclipse/vert.x/blob/master/src/main/java/io/vertx/core/impl/TaskQueue.java">TaskQueue</a>
associated to the provided identifier (and current `Context`). A `TaskQueue` is created if it is not already mapped
to the provided identifier (and `Context`), otherwise the existing `TaskqQueue` is used. The `TaskQueue` utilizes
the same `Executor` associated to the current `Context` (which comes from the `WorkerPool` assigned to the `Context`).
The mapping of `Context`s and identifiers to `TaskQueue`s is done via a weak hash map, which does not prevent the
identifiers as well as the `TaskQueue`s from being Garbage Collected.

Calling this method more than once for the same identifier (and current `Context`), effectively results in the
blocking code handler being executed sequentially with other previous blocking code handlers for the same identifier 
(and current `Context`) as it would normally happen with `Context.executeBlocking(blockingCodeHandler, true,
resultHandler)`). There are no ordering guarantees, however, in relation to the execution of other blocking code 
handlers associated to different identifiers or even the same identifier but under a different `Context`.

**NOTES:** 

* This method needs to be called within the scope of an existing Vert.x `Context`.
* This method makes use of a Vertx internal API method: `ContextInternal.executeBlocking(Handler, TaskQueue, Handler)`

#### VertxUtil.executeBlocking(context, identifier, blockingCodeHandler, resultHandler)

Similar to `VertxUtil.executeBlocking(identifier, blockingCodeHandler, resultHandler)` but using the provided context.

### com.chibchasoft.vertx.util.concurrent.ContextExecutorService

Implementation of `ExecutorService` which relies on a Vert.x `Context` for the execution of submitted tasks.
Submit methods for `ContextExecutorService` return `ContextFuture` instances. This is a `RunnableFuture` and 
a Vert.x `Future` that it is implemented usig the code of both `FutureTask` and Vert.x `FutureImpl`.
As submitted tasks are executed within the scope of a `Context`, a normal `FutureTask.get()` would be ill-advised
as it would both block the Context as well as preventing the actual task of being executed (in the case of an
Event Loop Context). 

In this case the correct way of usage for the `ContextFuture` would be to use the provided Vert.x `Future` methods
(setHandler, succeeded, failed, etc.).

**NOTES:**

* It is not recommended to call cancel(true) as it is not clearly defined how the underlying Context would react to
being interrupted.
* It is only recommended to use `ContextExecutorService` as the `ExecutorService` for a Client library as long as
the Client Library does not take ownership of the returned `ContextFuture`, this is, the Client Library should
return the `ContextFuture` for the caller to own as thus avoiding the usage of `RunnableFuture` methods
(`get()` and `get(long, TimeUnit)`) that would block the Context. 

**Clarification**

I am not well versed in the usage of software licenses so I did my best effort in trying to comply with the licensing
requirements of both `java.util.concurrent.FutureTask` and `io.vertx.core.impl.FutureImpl`on which `ContextFuture`
is heavily based (mostly verbatim in the case of the `FutureTask` methods). You will notice that
`ContextFuture` source class file includes three licenses: Oracle/Sun's (`FutureTask`), Eclipse's(`FutureImpl`) and
Apache's (My own changes). If you think that I am doing something wrong (license-wise) please let me know
(*jvelez@chibchasoft.com*).
 
## Maven ##

Vert.x-Util is published to the
[maven public repo](http://search.maven.org/#search%7Cgav%7C1%7Cg%3A%22com.chibchasoft%22%20AND%20a%3A%22vertx-util%22).

Add the vertx-util dependency to your project, in case of maven like this:

```xml
        <dependency>
            <groupId>com.chibchasoft</groupId>
            <artifactId>vertx-util</artifactId>
            <version>VERSION</version>
        </dependency>
```

where VERSION is [![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.chibchasoft/vertx-util/badge.svg)](https://search.maven.org/#search%7Cga%7C1%7Ccom.chibchasoft.vertx-util)

