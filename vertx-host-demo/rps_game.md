# Rock-Paper-Scissors over WebSocket

A real-time multiplayer RPS game built on the actor-based pub-sub system. Two players connect via WebSocket, get matched by a lobby, and play a best-of-3 game.

## Architecture

```
Client A  ←WebSocket→  Session Actor A  ←→  Lobby Actor
                                              ↕ (match)
Client B  ←WebSocket→  Session Actor B  ←→  Game Topic  ←→  Game Actor
```

- **Lobby Actor** — queues players and pairs them. When two are waiting, creates a per-game topic and a game actor.
- **Game Actor** — manages one game. Subscribes to the game topic, waits for both players to be ready, publishes round starts, collects choices, resolves rounds, and declares the winner.
- **Session Actor** — per-client boundary. Translates between JSON WebSocket frames and actor messages. Auto-subscribes to the game topic when matched.
- **Game Topic** — a pub-sub topic shared by both sessions and the game actor. All game messages flow through it.

## Connection

```
WebSocket endpoint: ws://<host>:8081/pubsub
```

All communication is JSON text frames. The client sends **commands** (with an `"action"` field) and receives **events** (with a `"type"` field).

## Game Lifecycle

```
  Client                          Server
    |                               |
    |--- rps_join ----------------->|  (enter lobby)
    |<-- rps_waiting ---------------|
    |                               |  (waiting for opponent...)
    |<-- game_created --------------|  (matched! auto-subscribed to game topic)
    |<-- round_start ---------------|  (round 1 begins)
    |--- rps_move ----------------->|  (submit choice)
    |                               |  (waiting for opponent's choice...)
    |<-- round_result --------------|  (both chose, round resolved)
    |<-- round_start ---------------|  (next round, if game not over)
    |   ...                         |
    |<-- game_over -----------------|  (final scores)
    |                               |
```

## Client → Server Commands

### Join the lobby

```json
{"action": "rps_join", "name": "Alice"}
```

| Field    | Type   | Required | Description          |
|----------|--------|----------|----------------------|
| `action` | string | yes      | `"rps_join"`         |
| `name`   | string | yes      | Player display name  |

The name is used in game results and must not be blank. Two players with the same name will still be matched — the server distinguishes them internally.

### Submit a move

```json
{"action": "rps_move", "gameId": "G1", "move": "ROCK"}
```

| Field    | Type   | Required | Description                          |
|----------|--------|----------|--------------------------------------|
| `action` | string | yes      | `"rps_move"`                         |
| `gameId` | string | yes      | From the `game_created` event        |
| `move`   | string | yes      | `"ROCK"`, `"PAPER"`, or `"SCISSORS"` |

Submit one move per round. The move is case-insensitive on the wire but must be one of the three values. Submitting before a `round_start` or submitting twice in the same round is silently ignored by the game actor.

## Server → Client Events

### Waiting confirmation

```json
{"type": "rps_waiting", "name": "Alice"}
```

Sent immediately after `rps_join`. The player is now in the lobby queue.

### Game created (matched)

```json
{
  "type": "game_created",
  "gameId": "G1",
  "opponent": "Bob"
}
```

Both players receive this when matched. The `gameId` is required for `rps_move` commands. The client is already subscribed to the game topic at this point — no further setup needed.

### Round start

```json
{"type": "round_start", "round": 1}
```

Signals the client to collect a move from the user. Round numbers start at 1.

### Round result

```json
{
  "type": "round_result",
  "round": 1,
  "playerA": "Alice",
  "choiceA": "ROCK",
  "playerB": "Bob",
  "choiceB": "SCISSORS",
  "winner": "Alice"
}
```

| Field     | Type   | Description                                     |
|-----------|--------|-------------------------------------------------|
| `round`   | int    | Round number                                    |
| `playerA` | string | First player's name                             |
| `choiceA` | string | `"ROCK"`, `"PAPER"`, or `"SCISSORS"`            |
| `playerB` | string | Second player's name                            |
| `choiceB` | string | `"ROCK"`, `"PAPER"`, or `"SCISSORS"`            |
| `winner`  | string | Winner's name, or `"draw"` if tied              |

A draw does not count toward either player's score. The next `round_start` follows immediately.

### Game over

```json
{
  "type": "game_over",
  "playerA": "Alice",
  "scoreA": 2,
  "playerB": "Bob",
  "scoreB": 1
}
```

First player to 2 wins takes the match. After this event, the game topic is retired. The client can close the connection or rejoin the lobby for another game.

## Error handling

```json
{"type": "error", "message": "Unknown topic: game:G99"}
```

Errors are non-fatal. Common cases:

| Scenario                        | Error message                                  |
|---------------------------------|------------------------------------------------|
| Missing `action` field          | `"Missing 'action' field"`                     |
| Missing or blank `name`         | `"Missing 'name' field"`                       |
| Unknown game ID in `rps_move`   | `"Unknown game: <gameId>"`                     |
| Move before joining             | `"Not in a game"`                              |
| Invalid move value              | `"Invalid move: <value>. Use ROCK, PAPER, or SCISSORS"` |
| Invalid JSON                    | `"Invalid JSON: <details>"`                    |
| Lobby not available             | `"RPS lobby not available"`                    |

## Notes for web client implementors

### State machine

A web client should track these states:

```
DISCONNECTED → CONNECTED → WAITING → MATCHED → PLAYING → GAME_OVER
                                                  ↕
                                          (round loop)
```

- **CONNECTED**: WebSocket open. Show "Join" button.
- **WAITING**: After `rps_join` acknowledged by `rps_waiting`. Show spinner / "waiting for opponent".
- **MATCHED**: `game_created` received. Store `gameId` and `opponent`. Transition to PLAYING on first `round_start`.
- **PLAYING**: Show move buttons (Rock / Paper / Scissors). Disable after sending `rps_move`. Re-enable on next `round_start`. Display `round_result` as it arrives.
- **GAME_OVER**: `game_over` received. Show final score and a "Play Again" button (which sends another `rps_join`).

### Reconnection

There is no session persistence. If the WebSocket drops mid-game, the server unsubscribes the session and notifies the lobby. The opponent's game will hang waiting for a move (no timeout currently). A reconnecting client must `rps_join` again for a new game.

### Multiple games

A client can play multiple consecutive games without reconnecting. After `game_over`, send another `rps_join` to re-enter the lobby. The previous game's topic is automatically cleaned up.

### CORS / Origin

The WebSocket server does not enforce origin checks. For production, configure Vert.x `WebSocketConnectHandler` to validate the `Origin` header.

## Running the demo

### Start the server

```bash
mvn exec:java -pl vertx-host-demo \
  -Dexec.mainClass="hue.captains.singapura.tao.http.vertx.demo.pubsub.PubSubWsDemo"
```

### Bot client (random moves)

```bash
mvn exec:java -pl vertx-host-demo \
  -Dexec.mainClass="hue.captains.singapura.tao.http.vertx.demo.pubsub.rps.RpsClient" \
  -Dexec.args="Alice"
```

Run two bot clients in separate terminals to see a full game.

### Browser quick test

Open the browser console and paste:

```javascript
const ws = new WebSocket("ws://localhost:8081/pubsub");
ws.onmessage = e => console.log(JSON.parse(e.data));
ws.onopen = () => ws.send(JSON.stringify({action: "rps_join", name: "WebPlayer"}));

// After game_created + round_start:
// ws.send(JSON.stringify({action: "rps_move", gameId: "G1", move: "ROCK"}));
```
