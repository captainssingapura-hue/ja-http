# ja-http vertx-host — Design Document

## Purpose

The vertx-host module is an **async hosting implementation** that binds the core action framework to [Eclipse Vert.x](https://vertx.io/) and [Jackson](https://github.com/FasterXML/jackson). It provides the machinery to receive HTTP requests, route them to registered actions, marshal parameters, execute the action, and serialize responses — all driven by the interfaces defined in core.

The companion `vertx-host-demo` module demonstrates how an application uses this library with shared demo actions from `action-demo-common`.

## Architecture

```
     core                action-demo-common         vertx-host (library)         vertx-host-demo (app)
┌──────────────┐    ┌──────────────────────┐    ┌──────────────────────────┐    ┌─────────────────────────┐
│              │    │                      │    │                          │    │                         │
│ ActionRegistry    │ EchoGetAction<REQ>   │    │ VertxActionHost          │    │ VertxMarshallers        │
│ GetAction    │    │ EchoPostAction<REQ>  │    │   ├─ Router (Vert.x)    │    │   (static factories)    │
│ PostAction   │    │ PongGetAction<REQ>   │    │   ├─ BodyHandler        │    │                         │
│ Action       │    │                      │    │   ├─ GET → GetHandler   │    │ EchoActionRegistry      │
│ Param        │    │ EchoQueryParams      │    │   └─ POST → PostHandler │    │   implements            │
│ ParamMarsh.  │    │ EchoPostBody         │    │              │          │    │   ActionRegistry<RC>    │
│ HttpReturn.  │    │ EchoHeaders          │    │              ▼          │    │                         │
│ External/    │    │ PongQueryParam       │    │        ErrorHandler     │    │ VertxHostDemo           │
│ InternalErr  │    │                      │    │        (shared)         │    │   (main)                │
│              │    │ BadPongRequestExc.   │    │                          │    │                         │
└──────────────┘    └──────────────────────┘    └──────────────────────────┘    └─────────────────────────┘
```

## Key Components

### VertxActionHost

The server entry point. Wires everything together:

1. Creates a Vert.x `Router`
2. Installs `BodyHandler` globally (required for POST body access)
3. Iterates the `ActionRegistry<RoutingContext>`, creating a typed handler for each path
4. Starts an HTTP server on the configured port

Accepts an optional `ObjectMapper` for custom Jackson configuration (date formats, naming strategies, module registration, etc.).

### GetActionHandler / PostActionHandler

Vert.x `Handler<RoutingContext>` implementations that bridge Vert.x to the core action interfaces. Each handler:

1. Calls the action's marshallers with the `RoutingContext` to extract typed parameters
2. Calls `execute()` which returns `CompletableFuture<R>`
3. Chains `.thenAccept()` to serialize the result to JSON via Jackson (async-friendly)
4. Chains `.exceptionally()` to delegate errors to `ErrorHandler`
5. Unwraps `CompletionException` before error handling

### ErrorHandler

Centralizes exception-to-response mapping for both handler types:

| Exception type | Status code | Response body | Server log |
|---|---|---|---|
| `HttpReturnableException` | From `statusCode()` | `externalError()` serialized via Jackson | `internalError()` at WARNING level |
| Jackson serialization failure of the external error | 500 | Generic `{"error":"Internal server error"}` | Full exception at SEVERE level |
| Any other exception | 500 | Generic `{"error":"Internal server error"}` | Full exception at SEVERE level |

The key property: **unrecognized exceptions never leak details**. Only exceptions that explicitly implement `HttpReturnableException` control what the caller sees.

## REQ Binding

The core framework's `REQ` type parameter is bound to `RoutingContext` throughout this module. This gives marshallers access to:

- `ctx.request().params()` — query string parameters
- `ctx.request().headers()` — HTTP headers
- `ctx.body().asString()` — raw request body (POST)
- `ctx.request().getParam(name)` — individual named parameters

## Demo Application

The `vertx-host-demo` module uses **shared actions from `action-demo-common`** and provides Vert.x-specific marshallers.

### VertxMarshallers

A utility class with static factory methods that produce `RoutingContext`-typed marshallers:

```java
VertxMarshallers.echoQueryParams()   → _QueryString<RoutingContext, EchoQueryParams>
VertxMarshallers.pongQueryParam()    → _QueryString<RoutingContext, PongQueryParam>
VertxMarshallers.echoHeaders()       → _Header<RoutingContext, EchoHeaders>
VertxMarshallers.emptyHeaders()      → _Header<RoutingContext, EchoHeaders>
VertxMarshallers.echoPostBody()      → _Post<RoutingContext, EchoPostBody>
```

### EchoActionRegistry

Wires shared actions with Vert.x-specific marshallers:

```java
public class EchoActionRegistry implements ActionRegistry<RoutingContext> {
    public Map<String, GetAction<RoutingContext, ?, ?, ?>> getActions() {
        return Map.of(
            "/echo", new EchoGetAction<>(
                    VertxMarshallers.echoQueryParams(),
                    VertxMarshallers.echoHeaders()),
            "/pong", new PongGetAction<>(
                    VertxMarshallers.pongQueryParam(),
                    VertxMarshallers.emptyHeaders())
        );
    }
    ...
}
```

### Demo Endpoints

| Method | Path | Action | Description |
|---|---|---|---|
| GET | `/echo` | `EchoGetAction` | Echoes all query params and headers |
| POST | `/echo` | `EchoPostAction` | Echoes parsed JSON body and headers |
| GET | `/pong` | `PongGetAction` | Returns `{"response":"Pong"}` for `?message=PING`, 400 error otherwise |

## Async Execution Model

Vert.x is event-loop based — blocking the event loop is forbidden. The `CompletableFuture<R>` return type from `execute()` fits naturally:

```
RoutingContext arrives on event loop
  → marshal params (fast, non-blocking)
  → execute() returns CompletableFuture<R>
  → .thenAccept(result -> write JSON response)
  → .exceptionally(ex -> ErrorHandler)
```

For actions that do purely CPU-bound work, `CompletableFuture.completedFuture()` resolves immediately on the event loop. For I/O-bound actions, the future can complete asynchronously on a different thread.

## Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| `core` | (sibling) | Action interfaces, Param types, error types |
| `vertx-core` | 4.5.11 | Async I/O, event loop |
| `vertx-web` | 4.5.11 | Router, RoutingContext, BodyHandler |
| `jackson-databind` | 2.17.2 | JSON serialization/deserialization |

Demo additionally depends on `action-demo-common` for shared actions and param records.

All versions are managed via parent POM properties. Java 21 is required.
