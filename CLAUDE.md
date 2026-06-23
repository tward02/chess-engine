# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project layout (Gradle multi-module)

Six modules under one build:
- **`:engine`** — pure Kotlin/JVM core (`engine`, `uci`, `logging` packages + the `moveBook` resource). No Compose/UI deps, so the server depends on it directly. Owns the long-running tests.
- **`:shared`** — kotlinx.serialization wire DTOs (`com.tward.shared.Protocol`): `GameStateDto` (carries a `Termination` + `outcomeText()` for display strings like "Black won on time"), `CreateGameRequest`, `MoveRequest`, `BotInfo`, the polymorphic game `ClientMessage`/`ServerMessage`, and the lobby `LobbyClientMessage`/`LobbyServerMessage` envelopes (+ `PlayerInfo`/`ChallengeInfo`). Engine-free (FEN strings + UCI move strings) so it stays portable to non-JVM clients.
- **`:server`** — Ktor (Netty) server, depends on `:engine` + `:shared`. `BotCatalog`/`BotSpec`/`BotFactory` define many selectable bots (data-driven, DB-ready) built from bot type × evaluator × orderer × budget. `LobbyManager` tracks presence and brokers challenges (player and bot); `GameRegistry`/`GameSession` run games (`Participant` = Human/Bot, identity/colour-aware, Mutex-guarded, `SharedFlow` events). REST (`GET /api/bots`, `POST /api/games`, `GET /api/games/{id}`, `POST /api/games/{id}/moves`) + `/ws/lobby` and `/ws/games/{id}?colour=` WebSockets. Server-authoritative (validates moves, enforces turn, runs clock, decides result). Run with `:server:run` (port 8080).
- **`:ui`** — shared Compose components (`com.tward.ui.board`): the model-agnostic `ChessBoardView` (squares, pieces, highlights, click callback, orientation) and `PieceImage` (+ the `pieces/` assets), plus `Sounds` (+ `sounds/` assets), `formatClock` and the self-ticking `GameClock`, `CapturedPieces` + the `capturedMaterial` helper, and `ChessTheme` (light/dark palette). Depends on `:engine`. Used by both `:desktop` and `:client` so rendering, sounds and theming live in one place.
- **`:desktop`** — Compose Desktop app + UI (`ui`, `app` packages + `sounds` resource). `implementation project(':engine', ':ui')`. `BoardView` renders its grid via `:ui`'s `ChessBoardView`, layering drag/animation overlays on top. Builds the UCI jar.
- **`:client`** — lightweight Compose desktop test client for the server (`com.tward.client`): lobby + click-to-move board (the shared `ChessBoardView`) over the ktor WebSocket client. Depends on `:engine` + `:shared` + `:ui`. Run with `:client:run`.

Plugin versions live in `settings.gradle` (`pluginManagement`); the root `build.gradle` declares them `apply false` so each loads once.

## Commands

```bash
# Run the chess game UI (default)
.\gradlew.bat :desktop:run

# Run a tournament UI (switch the main class in desktop/build.gradle):
# compose.desktop { application { mainClass = 'com.tward.app.TournamentAppKt' } }       # 2-bot A/B
# compose.desktop { application { mainClass = 'com.tward.app.MultiBotTournamentAppKt' } } # multi-bot
.\gradlew.bat :desktop:run

# Build (compile only) — both modules
.\gradlew.bat compileKotlin --console=plain

# Run all tests, both modules (excludes @LongRunning — always use this)
.\gradlew.bat test

# Run a single test class/method (qualify with the owning module)
.\gradlew.bat :engine:test --tests "engine.board.BoardTest"
.\gradlew.bat :engine:test --tests "engine.board.BoardTest.someTestMethod"
.\gradlew.bat :desktop:test --tests "ui.model.ChessMatchTest"

# Build the standalone UCI jar -> desktop/build/libs/uci-engine.jar
.\gradlew.bat :desktop:uciJar

# Run long tests (slow — perft and deep searches — do not run routinely)
.\gradlew.bat :engine:longTest
```

**Never run `longTest` for routine verification.** Use `test` only.

Long-running tests are tagged `@LongRunning` (`utils.LongRunning` annotation → JUnit5 `@Tag("long")`), and live in `:engine`. The `test` task excludes this tag; `:engine:longTest` includes it.

## Architecture

Kotlin targeting JVM 21, split into a Compose-free `:engine` library and a `:desktop` Compose app (see Project layout above). Three entry points in `com.tward.app` (in `:desktop`): `GameApp.kt` (human vs. bot game UI), `TournamentApp.kt` (2-bot A/B tournament UI), and `MultiBotTournamentApp.kt` (3+ bot tournament UI with formats). The `compose.desktop.mainClass` in `desktop/build.gradle` selects which runs. `UciApp.kt` (also in `com.tward.app`) is the headless UCI entry built into the UCI jar.

### Engine (`com.tward.engine`)

**Board layer** (`engine.board`): `Board` holds piece positions; `Square` is a `(col, row)` value type; `Piece` carries type + colour; `Move` encodes from/to with promotion and special-move flags. `Board` caches each side's king square (updated in `setPiece`) so `findKing` is O(1) on the search hot path, with a validated full-scan fallback so correctness never depends on the cache. `MoveDescriber` converts moves to standard chess notation (O-O, exd6 e.p., e8=Q, +/#) — pure and fully tested.

**Game layer** (`engine.game`): `ChessGame` is the authoritative game state (board + history + game-over detection). `MoveGenerator` produces all legal moves for a position — the performance-critical hot path hit millions of times per search. Its logger lives in the companion object (not per-instance) to avoid allocation overhead.

**Player layer** (`engine.player`):
- `Player` — base type for human or bot player.
- `MiniMaxBot` — alpha-beta minimax with configurable depth, `Evaluator`, and `MoveOrderer`. `nodesSearched` is public. Calls `orderer.reset()` before each search and `orderer.onBetaCutoff(move, ply, depth)` on cutoffs. `chooseMove` first checks `OpeningBook`.
- `MiniMaxIterativeDeepeningBot` — wraps minimax with ID to use the full time budget.
- `NegamaxBot` — `open` negamax + alpha-beta bot: iterative deepening, **quiescence search** (the fix for hanging pieces past the search horizon), PVS, check extensions, killer/history ordering, delta pruning, clock-aware time management. Defaults to `CompactEvaluator`. `protected open` search hooks (`negamax`, `searchRoot`, `searchToDepth`, `shouldPruneCapture`) let subclasses extend it without modifying it. `fixedDepth=` gives deterministic clock-free search for tests; the info log includes the `depth=` reached.
- `AdvancedNegamaxBot : NegamaxBot` — the strongest bot and the **UCI/Lichess default** (`UciApp`). Adds a `Zobrist`-keyed **transposition table**, **null-move pruning**, **late move reductions**, **SEE** capture pruning (`StaticExchangeEvaluator`) and **aspiration windows**, plus a deeper opening book. Each technique is a constructor toggle (all default on). Construct one per game (TT + orderer hold per-search state).
- `RandomBot` — baseline for tournament comparisons.
- `Zobrist` (object) — 64-bit position hash, the transposition-table key, recomputed per node. `StaticExchangeEvaluator` (object) — SEE: the material a capture wins/loses, used to prune losing quiescence captures.

**Evaluators** (`engine.player.evaluator`): All implement `Evaluator.evaluate(game, depth): Int`, scored from White's perspective (white − black).
- `BasicEvaluator` — material only.
- `StandardEvaluator(aggression)` — material + middlegame PST + mobility + check/castle bonuses. `open` with `protected open` hooks (`locationValue`, `mobility`, `castled`) for subclasses. Its `mobility()` contains a non-local `return` inside a `foldRight` — leave it as-is.
- `AdaptiveEvaluator : StandardEvaluator()` — tapered (MG/EG blend). Computes `phase` once in its `evaluate` override then stashes it in a field the three overridden hooks read. **Not thread-safe** — each bot needs its own instance (the tournament factory ensures this).
- `PositionalEvaluator : AdaptiveEvaluator()` — adds pawn structure (doubled/isolated/passed), bishop pair, rook open/half-open files, king pawn shield and a space term. `open`; its `positionalScore` and the `spaceScore` hook are `protected` for subclass reuse.
- `CompactEvaluator : PositionalEvaluator()` — the **search default**. Reuses the positional terms but **skips the expensive `mobility()`/`check()`/`castled()`** base (no per-eval move generation) and adds a tempo term; overrides `spaceScore` to 0. The speed buys ~2 extra ply of search. Effectively stateless (computes phase locally).
- `AdvancedEvaluator : CompactEvaluator()` — adds piece mobility + king safety (king-zone attacks, open files by the king). Richer but slower; A/B-tested as **net-negative at fast time controls**, so it is opt-in, not the default.
- `QuiescenceEvaluator(base)` — wraps any evaluator with a capture/promotion quiescence search for a swing-free static score. Used by the UI eval bar, not by the search bots (which quiesce internally).
- `PieceSquareTables` (object) — `gamePhase(board)` (0..24 from non-pawn material) and `locationValue(type, colour, square, phase)` (MG/EG linear blend). Row 0 = rank 8, White's perspective; Black mirrors vertically.

**Move ordering** (`engine.player.ordering`): `MoveOrderer` interface with `order(moves, ply)` + stateful hooks.
- `KillerHistoryMoveOrderer` — default for the `MiniMaxBot` and `NegamaxBot` families. Killer moves (2 slots/ply) + history heuristic layered over MVV-LVA. Score bands: captures (1,000,000+) > killer1 (900k) > killer2 (800k) > history (≤700k) > quiet (0). **Stateful and not thread-safe.**
- `MvvLvaMoveOrderer` — stateless; `scoreOf(move)` is public for reuse.
- `NoOpMoveOrderer` — baseline for measuring pruning benefit.

**Opening book** (`engine.openingBook`): `OpeningBook` caches parsed books per file path in a companion `ConcurrentHashMap`, so constructing many bots is cheap.

**Tournament** (`engine.tournament`): `BotSpec(name, createBot: (Colour)->ChessBot)` factory + `Tournament` that runs `totalGames` headless across a fixed thread pool. Games are claimed from a shared `AtomicInteger`; results tallied into atomics. Contenders alternate colours by game index. Keep `useOpeningBookMoves` enabled — bots are deterministic so the book provides variety. Always use **fresh bot instances per game** (bots hold per-game state like opening-book progress). `GamePlay.kt` holds the shared `playGame(white, black, maxPlies, initialTimeMillis)` loop and `winner(result)` helper used by both tournament runners.

**Multi-bot tournament** (`engine.tournament`): generalises the above to 3+ contenders. `TournamentFormat` is a pure function `nextRound(contenders, history) -> List<Pairing>` (empty ⇒ finished), with `RoundRobinFormat(doubleRound)` (circle method, static), `KnockoutFormat(tiebreak)` (single-elimination; draws broken by seed via `KnockoutTiebreak`, not replay, to guarantee termination between draw-prone bots), and `SwissFormat(rounds)` (score-based pairing avoiding rematches). `Standings.from(contenders, history)` derives the ranked leaderboard purely (win=1, draw=0.5, bye=1). `MultiBotTournament` plays each round concurrently then waits for the whole round to be recorded before asking for the next (adaptive formats need every result first); with `reserveOneForDisplay=true` it holds one shuffled game per round back for the UI to drive live. The formats/standings are pure and unit-tested; the runner is tested headlessly like `Tournament`.

### UCI (`com.tward.uci`)

`UciEngine` is a transport-agnostic UCI protocol handler: `handle(line)` processes one input line and emits responses through an `output` lambda (driven by stdin/stdout in production, by a list in tests). It implements the play subset (uci, isready, ucinewgame, position, go, stop, quit), searches synchronously inside `go`, and reuses `Board.fromFEN`/`ChessGame`/the bot's `chooseMove` directly. `UciMoveCodec` translates between `Move` and UCI long algebraic ("e2e4", "e7e8q", "e1g1"). The bot it plays is set by the `botFactory` in `com.tward.app.UciApp` (the headless entry point built into the UCI jar — see UCI_LICHESS.md). Engine logs go to stderr so stdout stays a clean UCI channel.

### UI (`com.tward.ui`)

Compose Desktop views. The board grid + piece rendering live in the shared **`:ui`** module (`ChessBoardView`/`PieceImage`); `BoardView` here renders through `ChessBoardView` and adds the desktop-only drag, animation and promotion overlays. `BoardView` accepts `showResultDialog` and `onGameOver` params used by the tournament display, and highlights the **last move's from/to squares** (from `ChessMatch.lastMove`). Its optional eval bar runs `QuiescenceEvaluator(PositionalEvaluator())` once per move on a background dispatcher (speed is irrelevant there, so it uses a rich evaluator, not the search default). `TournamentView` shows one live game (via `BoardView`) while headless workers run; both claim from the same game pool so counts are never double-counted. `MultiBotTournamentView` shows one randomly chosen live game of the current round (the engine's reserved game) alongside a live standings table polled from `MultiBotTournament.standings()`. `ChessMatch` is the UI-layer game model; `ClockManager` drives the chess clock.

### Logging (`com.tward.logging`)

Small facade over `java.util.logging`. `Log.of<T>()` → `AppLogger` with lazy `() -> String` messages at `info`/`debug`/`warn`/`error`. `LogConfig.configure(level)` called once in main.

- `info` — lifecycle / headline events (app start, game over, tournament standings).
- `debug` (JUL `FINE`) — per-move detail (each move, book-move selection, bot search nodes/score/time). Default level is INFO; set `Level.FINE` to see per-move detail.
- Per-move logging lives in `ChessMatch.makeMove` and the bots' `chooseMove` (MiniMax + Negamax families) only — **never** in `Board.makeMove`, `MoveGenerator`, or the minimax/negamax/quiescence recursion (hot search path).

## Code style

Every source file must end with a trailing newline.

### Comments

Keep comments clear and concise. Only comment on code that doesn't easily explain itself — skip comments that just restate what the code says. Classes and methods more likely to be public-facing (interfaces, public APIs consumed outside their own file) can carry more detail and KDoc, since callers won't have the implementation in front of them.

### Compose UI testing

Tests use `runComposeUiTest { setContent {...}; onNodeWithText(...).assertIsDisplayed() }` — no JUnit4 rule needed, coexists with JUnit5. For composables with a perpetual `LaunchedEffect` (e.g. `ChessClock`), set `mainClock.autoAdvance = false` before `setContent` to prevent the test hanging. To avoid loading image resources, test `CapturedPieces` with an empty piece list.
