# ♟️ Chess

A chess engine, desktop app, and multiplayer server — built from scratch in **Kotlin**. Some fun and experimenting with claude and my own skills to expand my chess knowledge and multiplayer server gameplay skills.

It started as a move generator and a minimax bot, and has grown into a tournament-strength engine
that plays on Lichess over UCI, a Compose Desktop app to play and watch games, and the beginnings of
a server so the same engine can power **web, mobile and desktop** clients for online play against
people and bots.

---

## Highlights

|                                |                                                                                                                                                                                                   |
|--------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Full legal move generation** | castling, en passant, promotion, check/stalemate, draws (50-move, threefold, insufficient material). Perft-verified.                                                                              |
| **Tournament-strength search** | negamax + alpha-beta, **quiescence search**, **transposition table** (Zobrist), null-move pruning, late move reductions, SEE, aspiration windows, PVS, check extensions, killer/history ordering. |
| **Tapered evaluation**         | material + piece-square tables blended by game phase, pawn structure, king safety, mobility, bishop pair, rook files.                                                                             |
| **Opening book**               | ~58k positions; bots follow theory out of the opening.                                                                                                                                            |
| **Desktop app**                | play vs the bot with animations, sound, an evaluation bar and last-move highlighting; plus headless and live bot **tournaments** (round-robin / knockout / Swiss).                                |
| **UCI bridge**                 | speak UCI to any GUI (Arena, Cute Chess) or play rated games on **Lichess** via `lichess-bot`.                                                                                                    |
| **Server (in progress)**       | Ktor REST + WebSocket API for server-driven games vs bots — the foundation for online multiplayer.                                                                                                |

---

## Project layout

A single Gradle multi-module build. The chess core is deliberately **Compose-free** so the server (and
future non-UI clients) can depend on it without dragging in the desktop UI toolkit.

```
chess-engine/
├── engine/    Pure Kotlin/JVM core — board, move generation, game rules,
│              bots, evaluators, opening book, tournaments, the UCI move codec.
│              No UI or server dependencies.
├── shared/    kotlinx.serialization wire DTOs (game state, moves, lobby + game
│              messages) shared by the server and its clients.
├── server/    Ktor server — bot catalog, a lobby (presence + challenges), and
│              REST + WebSocket APIs for bot and human-vs-human games.
│              Depends on :engine + :shared.
├── ui/        Shared Compose components — the model-agnostic ChessBoardView and
│              PieceImage (+ piece assets). Depends on :engine.
├── desktop/   Compose Desktop app + UI. Builds the UCI jar. Depends on :engine + :ui.
└── client/    Lightweight Compose test client for the server (lobby + play),
               rendering with the shared board. Depends on :engine + :shared + :ui.
```

```
       ┌──────────┐   ┌──────────┐         ┌──────────┐
       │ :desktop │   │ :client  │         │ :server  │
       └─┬──────┬─┘   └─┬─────┬──┘         └─┬─────┬──┘
         │      │       │     │              │     │
         │      ▼       ▼     │              │     ▼
         │    ┌──────────┐    │              │  ┌──────────┐
         │    │   :ui    │    │              │  │ :shared  │
         │    └────┬─────┘    │              │  └──────────┘
         ▼         ▼          ▼              ▼
       ┌────────────────────────────────────────┐
       │                 :engine                 │
       └────────────────────────────────────────┘
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

A [Ktor](https://ktor.io) server exposes the engine over HTTP + WebSockets. It has a **lobby** (see who
is connected, challenge a player or a bot), a **bot catalog** (many named opponents with approximate
Elos), and runs the games. The server is **authoritative**: it validates every move against the
engine's legal moves, enforces whose turn it is, runs the clock, and decides the result.

```bash
.\gradlew.bat :server:run        # starts on http://localhost:8080
```

### Bots

`GET /api/bots` returns the catalog — many opponents built from different bot types, evaluators, move
orderings and time budgets, each with a name and approximate Elo (from ~250 to ~2250). The list is
hard-coded for now and designed to move into a database later; adding a new opponent is one more entry.

```bash
curl localhost:8080/api/bots
# -> [ { "id":"randall","name":"Randall the Random","approxElo":250,"style":"Chaos", ... }, ... ]
```

### REST (quick bot game)

```bash
# Create a game against a catalog bot, playing White
curl -X POST localhost:8080/api/games \
  -H 'Content-Type: application/json' \
  -d '{"botId":"grandmaster-greg","playerColour":"white","initialTimeMillis":300000}'
# -> { "gameId":"1a2b3c4d", "fen":"...", "sideToMove":"white", "legalMoves":["e2e4", ...], ... }

# Play a move; the response already includes the bot's reply
curl -X POST localhost:8080/api/games/<gameId>/moves \
  -H 'Content-Type: application/json' -d '{"uci":"e2e4"}'
```

### WebSockets

- **`/ws/lobby`** — send `join`, see the live player list and bot catalog, and `challengePlayer` /
  `challengeBot` / `accept` / `decline`. The server replies with `gameStarted` (and your colour) once a
  game is on.
- **`/ws/games/<id>?colour=white|black`** — the live game stream: a `state` message on every move and a
  `gameOver` at the end. Send `{"type":"move","uci":"g1f3"}` or `{"type":"resign"}`. Omit `colour` to
  spectate.

### Test client

A lightweight Compose desktop client drives all of the above — connect, see who's online, challenge a
player or bot, and play on a board. (Web/mobile clients come later; this is for testing now.)

```bash
.\gradlew.bat :client:run        # connect it to a running :server
```

---

## Roadmap

Online play is taking shape. Done so far: the bot catalog, the lobby (presence + challenges),
human-vs-human and bot games, and the desktop test client. Still to come:

- [x] **Bot catalog** — many named opponents with approximate Elos; expandable, DB-bound later.
- [x] **Lobby** — see connected players, challenge players and bots, accept/decline.
- [x] **Human-vs-human games** — two clients on one server-authoritative game over WebSockets.
- [x] **Desktop test client** — lightweight Compose client for the server.
- [ ] **Accounts & auth** — JWT login so players, colours and clocks are tied to real identities.
- [ ] **Ratings & history** — Glicko/Elo, persisted games (PostgreSQL), leaderboards; move the bot
      catalog into the database.
- [ ] **Spectating & reconnection** — read-only subscribers; resume a game after a dropped connection.
- [ ] **Web & mobile apps** — browser client and Android/iOS (shared board via Compose Multiplatform).
- [ ] **Pluggable UCI engines** — a UCI-subprocess backend so the server can also host other engines
      (e.g. Stockfish) as opponents.
- [ ] **Matchmaking & scale** — auto-pairing, Redis pub/sub across multiple server instances.

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
