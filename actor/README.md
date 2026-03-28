# ja-http actor

A lightweight, type-safe actor model for message-driven communication between remote clients and host services.

## Overview

The actor module defines the core abstractions for an actor system where actors are **functionally pure** -- they process incoming messages and return a list of actions rather than mutating state or interacting with the actor system directly. The actor system is responsible for executing those actions and guaranteeing sequential message delivery to each actor.

## Interfaces

### `Message`

Marker interface for all messages. Deliberately distinguishes between:

- `Message._Receive` -- messages an actor can receive
- `Message._Send` -- messages an actor can send

In practice these may be the same type, but the distinction enables the type system to enforce directionality.

### `Actor<R, S>`

Core actor interface. Defines:

- `receive(List<R> messages)` -- processes a batch of received messages and returns a list of `ActorAction`s. Messages are batched because the actor system has no knowledge of how to optimise accumulated messages; that decision is left to the actor.
- `Actor._TypeRef<R, S>` -- type-level reference used when spawning sub-actors.

### `ActorAction` (sealed)

Sealed interface representing the side-effects an actor can request. Permitted implementations:

- `SendMessage<M>(ActorRef to, M message)` -- send a message to another actor
- `SpawnSubActor<R, S, A>(A actorType, List<R> initialMessages)` -- spawn a child actor with initial messages (analogous to constructor arguments)

### `ActorRef`

Opaque reference to an actor. Used as the destination in `SendMessage`. Implementation details are hidden from the actor interface.

### `FrontierActor<R, S>`

Extends `Actor` for actors that sit at the boundary between the actor system and external systems. Frontier actors:

- Translate external events into typed internal messages
- Translate internal messages into external protocol calls
- Use a callback-style `Consumer<ActorAction.SendMessage<S>>` listener (provided at construction time via `_Constructor`) to push externally-triggered messages into the actor system

The `_Constructor` interface enforces structural immutability -- once constructed, the listener is fixed.

## Maven coordinates

```xml
<dependency>
    <groupId>io.github.captainssingapura-hue.tong.ja-http</groupId>
    <artifactId>actor</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Requires Java 21+.
