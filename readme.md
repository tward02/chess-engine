# ♟️ Chess

A chess engine, desktop app, and multiplayer server — built from scratch in **Kotlin**.

It started as a move generator and a minimax bot, and has grown into a tournament-strength engine
that plays on Lichess over UCI, a Compose Desktop app to play and watch games, and the beginnings of
a server so the same engine can power **web, mobile and desktop** clients for online play against
people and bots.

---

## Highlights

| | |
|---|---|
| **Full legal move generation** | castling, en passant, promotion, check/stalemate, draws (50-move, threefold, insufficient material). Perft-verified. |
| **Tournament-strength search** | negamax + alpha-beta, **quiescence search**, **transposition table** (Zobrist), null-move pruning, late move reductions, SEE, aspiration windows, PVS, check extensions, killer/history ordering. |
| **Tapered evaluation** | material + piece-square tables blended by game phase, pawn structure, king safety, mobility, bishop pair, rook files. |
| **Opening book** | ~58k positions; bots follow theory out of the opening. |
| **Desktop app** | play vs the bot with animations, sound, an evaluation bar and last-move highlighting; plus headless and live bot **tournaments** (round-robin / knockout / Swiss). |
| **UCI bridge** | speak UCI to any GUI (Arena, Cute Chess) or play rated games on **Lichess** via `lichess-bot`. |
| **Server (in progress)** | Ktor REST + WebSocket API for server-driven games vs bots — the foundation for online multiplayer. |

---

## Project layout

A single Gradle multi-module build. The chess core is deliberately **Compose-free** so the server (and
future non-UI clients) can depend on it without dragging in the desktop UI toolkit.

```
chess-engine/
├── engine/    Pure Kotlin/JVM core — board, move generation, game rules,
│              bots, evaluators, opening book, tournaments, the UCI move codec.
│              No UI or server dependencies.
├── shared/    kotlinx.serialization wire DTOs (game state, moves, messages)
│              shared by the server and its clients.
├── server/    Ktor server — REST + WebSocket API for bot games (and, soon,
│              human-vs-human multiplayer). Depends on :engine + :shared.
└── desktop/   Compose Desktop app + UI. Builds the UCI jar. Depends on :engine.
```

```
            ┌──────────┐        ┌──────────┐
            │ :desktop │        │ :server  │
            └────┬─────┘        └────┬─────┘
                 │            ┌───────┴───────┐
                 │            │               │
                 ▼            ▼               ▼
            ┌──────────┐ ┌──────────┐
            │ :engine  │ │ :shared  │
            └──────────┘ └──────────┘
```

---

## Getting started

**Prerequisites:** JDK 21 (the build auto-provisions a toolchain), and the bundled Gradle wrapper.

```bash
# Build everything and run all tests (both fast — long perft tests are excluded)
.\gradlew.bat build
.\gradlew.bat test
```

> On macOS/Linux use `./gradlew` in place of `.\gradlew.bat`.

---

## The desktop app

Play against the bot, or run bot-vs-bot tournaments.

```bash
# Human vs bot game UI (default)
.\gradlew.bat :desktop:run
```

To launch a tournament UI instead, change the `mainClass` in `desktop/build.gradle`:

```groovy
compose.desktop { application { mainClass = 'com.tward.app.TournamentAppKt' } }        // 2-bot A/B
compose.desktop { application { mainClass = 'com.tward.app.MultiBotTournamentAppKt' } } // round-robin / knockout / Swiss
```

The board shows legal-move hints, an optional evaluation bar, captured-material tallies, a chess clock,
and **highlights the last move's from/to squares** for players and spectators.

---

## The engine as a library

`:engine` is a plain JVM library. Drive a game and ask a bot for a move:

```kotlin
import com.tward.engine.board.Board
import com.tward.engine.board.Colour
import com.tward.engine.game.ChessGame
import com.tward.engine.player.bot.AdvancedNegamaxBot

val game = ChessGame(Board.getStartingBoard())
val bot = AdvancedNegamaxBot(colour = Colour.WHITE)   // the strongest bot
val move = bot.chooseMove(game, timeLeft = 5_000)     // think within ~the clock it's given
game.makeMove(move)
```

**Bots**, from weakest to strongest: `RandomBot` → `MiniMaxBot` → `MiniMaxIterativeDeepeningBot` →
`NegamaxBot` → `AdvancedNegamaxBot`. Each `Evaluator` (`BasicEvaluator`, `StandardEvaluator`,
`AdaptiveEvaluator`, `PositionalEvaluator`, `CompactEvaluator`, `AdvancedEvaluator`) scores a position
in centipawns from White's perspective.

---

## 🔌 Play on Lichess (UCI)

The engine speaks UCI, so it can run in any UCI GUI or play **rated games on Lichess** through the
[`lichess-bot`](https://github.com/lichess-bot-devs/lichess-bot) bridge.

```bash
# Build the standalone UCI jar
.\gradlew.bat :desktop:uciJar     # -> desktop/build/libs/uci-engine.jar

# Try it locally
printf 'uci\nisready\nposition startpos moves e2e4 e7e5\ngo movetime 1000\nquit\n' | java -jar desktop/build/libs/uci-engine.jar
```

Full setup (bot account, token, `lichess-bot` config) is documented in **[UCI_LICHESS.md](UCI_LICHESS.md)**.

---

## The server (in progress)

A [Ktor](https://ktor.io) server exposes the engine over HTTP + WebSockets so any client can create a
game, play moves, and get bot replies. The server is **authoritative**: it validates every move against
the engine's legal moves, runs the clock, and decides the result — clients are never trusted.

```bash
.\gradlew.bat :server:run        # starts on http://localhost:8080
```

### REST

```bash
# Create a game vs the strongest bot, playing White
curl -X POST localhost:8080/api/games \
  -H 'Content-Type: application/json' \
  -d '{"opponent":"BOT","difficulty":"HARD","playerColour":"white","initialTimeMillis":300000}'
# -> { "gameId": "1a2b3c4d", "fen": "...", "sideToMove": "white", "legalMoves": ["e2e4", ...], ... }

# Play a move; in a bot game the response already includes the bot's reply
curl -X POST localhost:8080/api/games/<gameId>/moves \
  -H 'Content-Type: application/json' -d '{"uci":"e2e4"}'

# Fetch the current state
curl localhost:8080/api/games/<gameId>
```

### WebSocket

Connect to `ws://localhost:8080/ws/games/<gameId>` for a **live stream**: the server pushes a `state`
message on every move (yours, your opponent's, the bot's) and a `gameOver` message at the end. Send
`{"type":"move","uci":"g1f3"}` to move or `{"type":"resign"}` to resign. This is what online play and
spectating will be built on.

| Difficulty | Engine |
|---|---|
| `EASY` | `MiniMaxBot` (depth 2) |
| `MEDIUM` | `NegamaxBot` (~1s/move) |
| `HARD` | `AdvancedNegamaxBot` (full search) |

---

## Roadmap

The server above is **step 1** of bringing the engine online. What's coming:

- [ ] **Human-vs-human multiplayer** — two clients on one game over WebSockets, with matchmaking.
- [ ] **Accounts & auth** — JWT-based login so games and clocks are tied to identities.
- [ ] **Ratings & history** — Glicko/Elo, persisted games (PostgreSQL), leaderboards.
- [ ] **Spectating** — read-only WebSocket subscribers for any live game.
- [ ] **Web app** — a browser client (REST + WebSocket) to play and watch.
- [ ] **Mobile apps** — Android & iOS, with a shared board renderer via Compose Multiplatform.
- [ ] **Pluggable UCI engines** — a UCI-subprocess backend so the server can also host other engines
      (e.g. Stockfish) as opponents and difficulty tiers.
- [ ] **Reconnection & scale** — resume a game after a dropped connection; Redis pub/sub for multiple
      server instances.

---

## Building & testing

```bash
.\gradlew.bat test                                  # all modules, fast tests
.\gradlew.bat :engine:test --tests "engine.board.BoardTest"   # one class (qualify with the module)
.\gradlew.bat :desktop:test --tests "ui.model.ChessMatchTest"
.\gradlew.bat :engine:longTest                      # slow perft / deep-search tests (rarely needed)
.\gradlew.bat :desktop:uciJar                       # build the UCI jar
.\gradlew.bat :server:run                           # run the server
```

See **[CLAUDE.md](CLAUDE.md)** for a deeper tour of the architecture.
