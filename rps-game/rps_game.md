# Rock-Paper-Scissors over WebSocket

A real-time multiplayer RPS game built entirely on generic pub-sub primitives. The WebSocket bridge knows nothing about RPS — all game logic flows through standard topic subscribe/publish operations.

## Architecture

```
                         Actor System (any implementation)
                        /            |             \
              Topic Manager    Lobby Actor     Game Actor(s)
               (directory)    (subscribes to   (subscribes to
                              "lobby" topic)   "game:G1" topic)

  Client A  <--WS-->  Session A  <--pub/sub-->  lobby topic
  Client B  <--WS-->  Session B  <--pub/sub-->  game:G1 topic
```

**Separation of concerns:**

| Layer | Module | Knows about |
|-------|--------|-------------|
| Actor primitives | `actor` | `Actor`, `ActorRef`, `ActorSystem`, `Message`, `ActorAction` |
| Pub-sub actors | `actor-pub-sub` | `TopicActor`, `TopicManager`, `TopicPayload` |
| WebSocket bridge | `vertx-host-ws` | Vert.x WebSocket, JSON frames, generic pub-sub commands |
| RPS game | `rps-game` | Lobby, game rules, matchmaking (no hosting deps) |

The WebSocket bridge is reusable for any application. RPS actors are regular actors that communicate through `TopicPayload` — they have no WebSocket or Vert.x dependencies.

### ActorSystem interface

The pub-sub system works with any `ActorSystem` implementation:

```java
public interface ActorSystem {
    ActorRef allocateRef(String name);
    void register(ActorRef ref, Actor<?, ?> actor);
    <...> A registerFrontier(ActorRef ref, FrontierActor._Constructor<...> constructor);
    void inject(ActorRef target, Message._Receive message);
}
```

- `VertxActorSystem` — dispatches on the Vert.x event loop (non-blocking)
- `SingleThreadActorSystem` — synchronous loop (good for testing/demos)

## Connection

```
WebSocket endpoint: ws://<host>:8081/pubsub
```

All communication is JSON text frames. The client sends **commands** (with an `"action"` field) and receives **events** (with a `"type"` field). The session only understands four generic commands: `list`, `subscribe`, `unsubscribe`, `publish`.

## Game Lifecycle

```
  Client                          Server
    |                               |
    |<-- topics [...,"lobby",...] ---|  (initial topic list on connect)
    |--- subscribe "lobby" -------->|
    |--- publish "lobby"            |
    |     {type:join, name:Alice} ->|  (lobby actor receives via topic)
    |                               |
    |<-- data "lobby"               |  (lobby broadcasts match)
    |     {type:game_created,       |
    |      gameId:G1,               |
    |      gameTopic:game:G1,...} ---|
    |                               |
    |--- list --------------------->|  (refresh to discover new game topic)
    |<-- topics [...,"game:G1",...]-|
    |--- subscribe "game:G1" ----->|
    |--- publish "game:G1"         |
    |     {type:ready, name:Alice}->|  (game actor waits for 2 ready)
    |                               |
    |<-- data "game:G1"             |
    |     {type:round_start,        |
    |      round:1} ----------------|
    |--- publish "game:G1"         |
    |     {type:move, name:Alice,   |
    |      move:ROCK} ------------->|
    |                               |  (waiting for opponent's move...)
    |<-- data "game:G1"             |
    |     {type:round_result,...} --|
    |   ...                         |
    |<-- data "game:G1"             |
    |     {type:game_over,...} -----|
    |                               |
```

## Client Commands (generic pub-sub)

### List topics

```json
{"action": "list"}
```

### Subscribe to a topic

```json
{"action": "subscribe", "topic": "lobby"}
```

### Unsubscribe from a topic

```json
{"action": "unsubscribe", "topic": "lobby"}
```

### Publish to a topic

```json
{"action": "publish", "topic": "lobby", "payload": {"type": "join", "name": "Alice"}}
```

The `payload` can be any JSON value. It is delivered to all topic subscribers as a `TopicPayload`.

## Server Events

### Topic list

```json
{"type": "topics", "topics": ["prices", "news", "lobby"]}
```

### Topic data

```json
{"type": "data", "topic": "lobby", "payload": {"type": "game_created", ...}}
```

All topic messages arrive wrapped in a `data` event with the topic name and the parsed payload.

### Subscribed / Unsubscribed

```json
{"type": "subscribed", "topic": "lobby"}
{"type": "unsubscribed", "topic": "lobby"}
```

### Error

```json
{"type": "error", "message": "Unknown topic: foo"}
```

## RPS Topic Payloads

### Lobby topic (`"lobby"`)

**Client publishes:**

```json
{"type": "join", "name": "Alice"}
{"type": "leave", "name": "Alice"}
```

**Lobby actor publishes (broadcast to all lobby subscribers):**

```json
{
  "type": "game_created",
  "gameId": "G1",
  "gameTopic": "game:G1",
  "playerA": "Alice",
  "playerB": "Bob"
}
```

Both matched players and all other lobby subscribers see this. Clients filter by checking if their name is `playerA` or `playerB`.

### Game topic (`"game:G1"`)

**Client publishes:**

```json
{"type": "ready", "name": "Alice"}
{"type": "move", "name": "Alice", "move": "ROCK"}
```

Valid moves: `ROCK`, `PAPER`, `SCISSORS` (case-insensitive).

**Game actor publishes:**

```json
{"type": "round_start", "round": 1}
```

```json
{
  "type": "round_result",
  "round": 1,
  "playerA": "Alice", "choiceA": "ROCK",
  "playerB": "Bob", "choiceB": "SCISSORS",
  "winner": "Alice"
}
```

```json
{
  "type": "game_over",
  "playerA": "Alice", "scoreA": 2,
  "playerB": "Bob", "scoreB": 1
}
```

The `winner` field is the player's name or `"draw"`. First to 2 wins takes the match.

## Notes for web client implementors

### State machine

```
CONNECTED → LOBBY_JOINED → MATCHED → SUBSCRIBING_GAME → PLAYING → GAME_OVER
                                        (list → subscribe)
```

- **CONNECTED**: WebSocket open, initial topic list received.
- **LOBBY_JOINED**: Subscribed to "lobby", published join. Waiting for match.
- **MATCHED**: Received `game_created` from lobby topic. Need to discover and subscribe to game topic.
- **SUBSCRIBING_GAME**: Sent `list` to refresh topics. On receiving the topic list containing the game topic, subscribe and publish `ready`.
- **PLAYING**: Show move buttons. On `round_start`, enable input. On `round_result`, show result. Disable input until next `round_start`.
- **GAME_OVER**: Show final score. Offer "Play Again" (re-publish join to lobby).

### Topic data filtering

Since pub-sub topics broadcast to all subscribers, clients receive all messages on a topic — including their own publishes echoed back (e.g. `move` and `ready`). Ignore payload types that are not relevant to the current state.

### Multiple games

After `game_over`, the client can publish another `join` to the lobby topic without reconnecting. The previous game's topic is automatically retired by the game actor.

## Running the demo

### Start the server

```bash
mvn exec:java -pl vertx-host-demo \
  -Dexec.mainClass="hue.captains.singapura.tao.http.vertx.demo.pubsub.rps.PubSubWsDemo"
```

### Bot client (random moves)

```bash
mvn exec:java -pl vertx-host-demo \
  -Dexec.mainClass="hue.captains.singapura.tao.http.vertx.demo.pubsub.rps.RpsClient" \
  -Dexec.args="Alice"
```

Run two bot clients in separate terminals to see a full game.

### Browser quick test

```javascript
const ws = new WebSocket("ws://localhost:8081/pubsub");
ws.onmessage = e => console.log(JSON.parse(e.data));

// After receiving initial topics:
ws.send(JSON.stringify({action: "subscribe", topic: "lobby"}));
ws.send(JSON.stringify({action: "publish", topic: "lobby", payload: {type: "join", name: "WebPlayer"}}));

// After game_created arrives and topic list refreshed:
// ws.send(JSON.stringify({action: "subscribe", topic: "game:G1"}));
// ws.send(JSON.stringify({action: "publish", topic: "game:G1", payload: {type: "ready", name: "WebPlayer"}}));
// ws.send(JSON.stringify({action: "publish", topic: "game:G1", payload: {type: "move", name: "WebPlayer", move: "ROCK"}}));
```
