# ja-http core

Type-safe HTTP action interfaces for defining request handlers with compile-time parameter safety.

## Overview

The core module provides a minimal set of interfaces for modelling HTTP actions (request handlers). It separates the concerns of **parameter typing** (`Param`), **parameter extraction** (`ParamMarshaller`), and **action execution** (`GetAction`, `PostAction`), leaving the concrete HTTP library choice to the consumer.

The `REQ` type parameter is intentionally left unbound so this module stays independent of any specific HTTP server implementation.

## Interfaces

### `Param`

Marker interface hierarchy that classifies HTTP parameters by origin:

- `Param._Header` -- header parameters
- `Param._QueryString` -- query string parameters
- `Param._Post` -- POST body parameters

### `ParamMarshaller<REQ, P>`

Converts a raw (typically untyped) request into a typed `Param` object. Specialised sub-interfaces mirror the `Param` hierarchy:

- `ParamMarshaller._Header<REQ, HP>`
- `ParamMarshaller._QueryString<REQ, QP>`
- `ParamMarshaller._Post<REQ, PP>`

### `Action<REQ, HP>`

Base interface for all HTTP actions. Declares a `headerMarshaller()` for extracting header parameters from the raw request.

### `GetAction<REQ, QP, HP, R>`

Extends `Action`. Adds a `queryStrMarshaller()` and an `execute(QP, HP)` method for handling GET requests with typed query-string and header parameters.

### `PostAction<REQ, PP, HP, R>`

Declares a `postMarshaller()` and an `execute(PP, HP)` method for handling POST requests with typed body and header parameters.

## Maven coordinates

```xml
<dependency>
    <groupId>io.github.captainssingapura-hue.tong.ja-http</groupId>
    <artifactId>core</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Requires Java 21+.
