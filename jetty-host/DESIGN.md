# ja-http jetty-host — Design Document

## Purpose

The jetty-host module is a **synchronous hosting implementation** that binds the core action framework to [Eclipse Jetty 12](https://eclipse.dev/jetty/) and [Jackson](https://github.com/FasterXML/jackson). It demonstrates that the same action interfaces and business logic work equally well in a traditional thread-per-request model, complementing the async Vert.x implementation.

The companion `jetty-host-demo` module demonstrates how an application uses this library with shared demo actions from `action-demo-common`.

## Architecture

```
     core                action-demo-common         jetty-host (library)         jetty-host-demo (app)
┌──────────────┐    ┌──────────────────────┐    ┌──────────────────────────┐    ┌─────────────────────────┐
│              │    │                      │    │                          │    │                         │
│ ActionRegistry    │ EchoGetAction<REQ>   │    │ JettyActionHost          │    │ JettyMarshallers        │
│ GetAction    │    │ EchoPostAction<REQ>  │    │   extends Handler.Abstr. │    │   (static factories)    │
│ PostAction   │    │ PongGetAction<REQ>   │    │   ├─ dispatches by       │    │                         │
│ Action       │    │                      │    │   │  method + path       │    │ JettyEchoActionRegistry │
│ Param        │    │ EchoQueryParams      │    │   ├─ GET → GetHandler   │    │   implements            │
│ ParamMarsh.  │    │ EchoPostBody         │    │   ├─ POST → PostHandler │    │   ActionRegistry<Req>   │
│ HttpReturn.  │    │ EchoHeaders          │    │   └─ 404 fallback       │    │                         │
│ External/    │    │ PongQueryParam       │    │              │          │    │ JettyHostDemo           │
│ InternalErr  │    │                      │    │              ▼          │    │   (main)                │
│              │    │ BadPongRequestExc.   │    │        ErrorHandler     │    │                         │
└──────────────┘    └──────────────────────┘    └──────────────────────────┘    └─────────────────────────┘
```

## Key Components

### JettyActionHost

Extends Jetty 12's `Handler.Abstract`. Acts as both the server manager and the request dispatcher:

1. Creates a Jetty `Server` on the configured port
2. Sets itself as the server's handler
3. On each request, inspects the HTTP method and path
4. Looks up the matching action from the `ActionRegistry<Request>`
5. Delegates to `GetActionHandler` or `PostActionHandler`
6. Returns 404 for unmatched routes

Accepts an optional `ObjectMapper` for custom Jackson configuration.

Provides `startServer()`, `stopServer()`, and `joinServer()` methods (named to avoid colliding with `Handler.Abstract`'s final `start()`/`stop()` lifecycle methods).

### GetActionHandler / PostActionHandler

Plain classes (not Jetty handlers) that bridge Jetty requests to the core action interfaces. Each handler:

1. Calls the action's marshallers with the Jetty `Request` to extract typed parameters
2. Calls `execute()` which returns `CompletableFuture<R>`
3. Calls `.join()` to block until the future completes (synchronous model)
4. Serializes the result to JSON via Jackson and writes it to the `Response`
5. On exception, delegates to `ErrorHandler` after unwrapping `CompletionException`

### ErrorHandler

Centralizes exception-to-response mapping, mirroring the Vert.x implementation:

| Exception type | Status code | Response body | Server log |
|---|---|---|---|
| `HttpReturnableException` | From `statusCode()` | `externalError()` serialized via Jackson | `internalError()` at WARNING level |
| Jackson serialization failure of the external error | 500 | Generic `{"error":"Internal server error"}` | Full exception at SEVERE level |
| Any other exception | 500 | Generic `{"error":"Internal server error"}` | Full exception at SEVERE level |

Also provides a static `unwrap()` utility to extract the real exception from `CompletionException` wrappers.

## REQ Binding

The core framework's `REQ` type parameter is bound to `org.eclipse.jetty.server.Request` throughout this module. This gives marshallers access to:

- `Request.extractQueryParameters(request)` — query string as `Fields`
- `request.getHeaders()` — HTTP headers as `HttpFields`
- `Request.asInputStream(request)` — raw request body (POST)
- `request.getHttpURI().getPath()` — request path
- `request.getMethod()` — HTTP method

## Synchronous Execution Model

Jetty 12 uses a thread-per-request model. The `CompletableFuture<R>` from `execute()` is consumed via `.join()`:

```
Request arrives on Jetty thread
  → marshal params
  → execute() returns CompletableFuture<R>
  → .join() blocks until result is ready
  → serialize R to JSON
  → write response bytes + complete Callback
```

For actions that return `CompletableFuture.completedFuture()`, `.join()` returns immediately with no actual blocking. For truly async actions, the Jetty thread blocks until the future completes — acceptable in a thread-per-request model.

This demonstrates the key design property: **the same action code works without modification across async (Vert.x) and sync (Jetty) hosting**.

## Demo Application

The `jetty-host-demo` module uses **shared actions from `action-demo-common`** — the exact same action classes as the Vert.x demo — and provides Jetty-specific marshallers.

### JettyMarshallers

A utility class with static factory methods that produce `Request`-typed marshallers:

```java
JettyMarshallers.echoQueryParams()   → _QueryString<Request, EchoQueryParams>
JettyMarshallers.pongQueryParam()    → _QueryString<Request, PongQueryParam>
JettyMarshallers.echoHeaders()       → _Header<Request, EchoHeaders>
JettyMarshallers.emptyHeaders()      → _Header<Request, EchoHeaders>
JettyMarshallers.echoPostBody()      → _Post<Request, EchoPostBody>
```

### JettyEchoActionRegistry

Wires shared actions with Jetty-specific marshallers:

```java
public class JettyEchoActionRegistry implements ActionRegistry<Request> {
    public Map<String, GetAction<Request, ?, ?, ?>> getActions() {
        return Map.of(
            "/echo", new EchoGetAction<>(
                    JettyMarshallers.echoQueryParams(),
                    JettyMarshallers.echoHeaders()),
            "/pong", new PongGetAction<>(
                    JettyMarshallers.pongQueryParam(),
                    JettyMarshallers.emptyHeaders())
        );
    }
    ...
}
```

### Demo Endpoints (port 8081)

| Method | Path | Action | Description |
|---|---|---|---|
| GET | `/echo` | `EchoGetAction` | Echoes all query params and headers |
| POST | `/echo` | `EchoPostAction` | Echoes parsed JSON body and headers |
| GET | `/pong` | `PongGetAction` | Returns `{"response":"Pong"}` for `?message=PING`, 400 error otherwise |

All three endpoints produce **identical JSON responses** to the Vert.x demo, validating the hosting-agnostic design.

## Comparison: Vert.x vs Jetty Hosting

| Aspect | vertx-host | jetty-host |
|---|---|---|
| Threading model | Event loop (non-blocking) | Thread-per-request |
| Future consumption | `.thenAccept()` / `.exceptionally()` | `.join()` (blocking) |
| Handler type | `Handler<RoutingContext>` | Plain class, called from `Handler.Abstract` |
| Routing | Vert.x `Router` with path matching | Manual method + path lookup in `handle()` |
| Body access | `ctx.body().asString()` (via BodyHandler) | `Request.asInputStream()` |
| Query params | `ctx.request().params()` | `Request.extractQueryParameters()` |
| Headers | `ctx.request().headers()` | `request.getHeaders()` |
| Response writing | `ctx.response().end(string)` | `response.write(true, ByteBuffer, Callback)` |

## Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `core` | (sibling) | Action interfaces, Param types, error types |
| `jetty-server` | 12.0.16 | HTTP server, Handler API |
| `jackson-databind` | 2.17.2 | JSON serialization/deserialization |

Demo additionally depends on `action-demo-common` for shared actions and param records.

All versions are managed via parent POM properties. Java 21 is required.
