# ja-http core — Design Document

## Purpose

The core module defines a **hosting-agnostic** framework for type-safe HTTP request handling. It provides the interfaces that application developers implement to define HTTP actions, while leaving the choice of HTTP server, serialization library, and deployment model entirely to the hosting layer.

The central idea: **an HTTP endpoint is a function from strongly-typed parameters to a strongly-typed result**, with the framework handling the conversion between raw HTTP and the domain types.

## Design Principles

1. **Type safety at the boundary.** Raw HTTP requests are untyped (string query params, byte-stream bodies, string headers). The framework forces an explicit marshalling step that converts these into domain-specific parameter types *before* the action logic runs. The action never touches raw HTTP.

2. **REQ is abstract.** All interfaces are parameterized by `REQ` — the raw request type. Core never imports Vert.x, Jetty, Servlet, or any other HTTP library. A hosting implementation binds `REQ` to its concrete request type (e.g. `RoutingContext` for Vert.x, `Request` for Jetty).

3. **Immutability.** Parameter types (`Param` implementations) are intended to be records or other immutable objects. Marshallers are stateless functions. Actions receive immutable inputs and return a result.

4. **Async by default.** All `execute()` methods return `CompletableFuture<R>`, using JDK types with no hosting dependency. Synchronous actions return `CompletableFuture.completedFuture(result)`. Async hosts (Vert.x) chain `.thenAccept()` / `.exceptionally()`. Synchronous hosts (Jetty) call `.join()`.

5. **Separation of error audiences.** The `HttpReturnableException` mechanism explicitly separates what the external caller sees from what the server logs. These are different types with different concerns — security-sensitive details stay internal.

6. **Injectable marshallers.** Actions are `REQ`-generic and receive their marshallers via constructor injection. This decouples business logic from hosting concerns — the same action class works across Vert.x, Jetty, or any future hosting implementation.

## Interface Hierarchy

### Parameters

```
Param (marker)
├── _Header       — extracted from HTTP headers
├── _QueryString   — extracted from the URL query string
└── _Post          — extracted from the request body
```

Application code defines concrete parameter types as records:

```java
record UserQuery(String name, int limit) implements Param._QueryString {}
```

### Marshallers

```
ParamMarshaller<REQ, P extends Param>
│   P marshal(REQ req)
│
├── _Header<REQ, HP>
├── _QueryString<REQ, QP>
└── _Post<REQ, PP>
```

Each marshaller sub-interface is a single-method functional interface. The hosting layer binds `REQ`, and application code provides the extraction logic — typically as a lambda in a dedicated `Marshallers` utility class:

```java
// In VertxMarshallers.java
public static ParamMarshaller._QueryString<RoutingContext, UserQuery> userQuery() {
    return ctx -> new UserQuery(
        ctx.request().getParam("name"),
        Integer.parseInt(ctx.request().getParam("limit"))
    );
}

// In JettyMarshallers.java
public static ParamMarshaller._QueryString<Request, UserQuery> userQuery() {
    return req -> {
        Fields fields = Request.extractQueryParameters(req);
        return new UserQuery(
            fields.get("name").getValue(),
            Integer.parseInt(fields.get("limit").getValue())
        );
    };
}
```

### Actions

```
Action<REQ, HP>
│   headerMarshaller()
│
├── GetAction<REQ, QP, HP, R>
│       queryStrMarshaller()
│       CompletableFuture<R> execute(QP, HP)
│
└── PostAction<REQ, PP, HP, R>
        postMarshaller()
        CompletableFuture<R> execute(PP, HP)
```

Both `GetAction` and `PostAction` extend `Action`, inheriting `headerMarshaller()`. Their `execute()` methods return `CompletableFuture<R>` for async-by-default semantics.

Actions are `REQ`-generic classes with marshallers injected via constructor:

```java
public class PongGetAction<REQ>
        implements GetAction<REQ, PongQueryParam, EchoHeaders, Map<String, String>> {

    public PongGetAction(ParamMarshaller._QueryString<REQ, PongQueryParam> qsMarshaller,
                         ParamMarshaller._Header<REQ, EchoHeaders> hMarshaller) { ... }
}
```

### ActionRegistry

```java
public interface ActionRegistry<REQ> {
    Map<String, GetAction<REQ, ?, ?, ?>> getActions();
    Map<String, PostAction<REQ, ?, ?, ?>> postActions();
}
```

The registry is an **interface** parameterized by `REQ`. Applications implement it and return immutable maps (`Map.of(...)`). The host reads the maps once at startup. Route configuration is fixed at construction — no runtime mutation.

### Error Handling

```
ExternalError (marker)     — serialized to the HTTP response
InternalError (marker)     — logged server-side

HttpReturnableException<E extends ExternalError, I extends InternalError>
    int statusCode()
    E externalError()
    I internalError()
```

Actions throw concrete exceptions that extend `RuntimeException` and implement `HttpReturnableException`. This gives the hosting layer everything it needs:

- **Status code** — e.g. 400 for bad input, 403 for authorization failures
- **External error** — a typed object serialized to JSON for the caller, containing only safe information
- **Internal error** — a typed object logged server-side, containing diagnostics (input received, stack location, internal state)

Exceptions that do *not* implement this interface are treated as unexpected errors — the hosting layer returns a generic 500 with no detail.

## Request Processing Flow

```
Raw HTTP Request
       │
       ▼
 ┌─────────────┐
 │  Marshaller  │  marshal(REQ) → typed Param
 │  (header)    │
 └─────────────┘
       │
       ▼
 ┌─────────────┐
 │  Marshaller  │  marshal(REQ) → typed Param
 │  (QS / body) │
 └─────────────┘
       │
       ▼
 ┌─────────────┐
 │   Action     │  execute(params...) → CompletableFuture<R>
 │  .execute()  │
 └─────────────┘
       │
       ├── async host (Vert.x) → .thenAccept() → serialize R → HTTP 200
       │                        → .exceptionally() → ErrorHandler
       │
       ├── sync host (Jetty)  → .join() → serialize R → HTTP 200
       │                       → catch → ErrorHandler
       │
       ├── HttpReturnableException
       │       ├── externalError() → JSON response (status code from exception)
       │       └── internalError() → server log
       │
       └── Unknown exception → generic 500, no detail leaked
```

## Module Structure

```
ja-http
├── core                  — interfaces only, no server dependency
├── action-demo-common    — REQ-generic demo actions + param records (depends on core)
├── vertx-host            — Vert.x hosting implementation (async)
├── vertx-host-demo       — Vert.x marshallers + registry + main
├── jetty-host            — Jetty 12 hosting implementation (sync via .join())
└── jetty-host-demo       — Jetty marshallers + registry + main
```

The `action-demo-common` module demonstrates the key architectural property: **the same action classes (`EchoGetAction<REQ>`, `EchoPostAction<REQ>`, `PongGetAction<REQ>`) are shared across both hosting implementations**. Only the marshallers and registry differ.

## Extension Points

- **New parameter origins** — `Param` can be extended with `_Cookie`, `_PathParam`, etc. A corresponding `ParamMarshaller` sub-interface and action interface would follow the same pattern.
- **New HTTP methods** — `PutAction`, `DeleteAction`, etc. can be added following `GetAction`/`PostAction` as templates.
- **New hosting implementations** — any HTTP server can implement the hosting layer by binding `REQ` and wiring marshallers + actions into its routing mechanism. The existing Vert.x (async) and Jetty (sync) implementations serve as templates.
- **New marshaller sources** — marshallers are just lambdas. They can be hand-written, code-generated, or annotation-driven.
