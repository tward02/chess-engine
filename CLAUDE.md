# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Run the chess game UI (default)
.\gradlew.bat run

# Run the tournament UI (switch main class temporarily in build.gradle)
# compose.desktop { application { mainClass = 'com.tward.app.TournamentAppKt' } }
.\gradlew.bat run

# Build (compile only)
.\gradlew.bat compileKotlin --console=plain

# Run tests (excludes @LongRunning tests — always use this)
.\gradlew.bat test

# Run a single test class
.\gradlew.bat test --tests "engine.board.BoardTest"

# Run a single test method
.\gradlew.bat test --tests "engine.board.BoardTest.someTestMethod"

# Run long tests (slow — perft and deep searches — do not run routinely)
.\gradlew.bat longTest
```

**Never run `longTest` for routine verification.** Use `test` only.

Long-running tests are tagged `@LongRunning` (`utils.LongRunning` annotation → JUnit5 `@Tag("long")`). The `test` task excludes this tag; `longTest` includes it.

## Architecture

Kotlin + Compose Desktop application targeting JVM 21. Two entry points in `com.tward.app`: `GameApp.kt` (human vs. bot game UI) and `TournamentApp.kt` (bot vs. bot tournament UI). The `compose.desktop.mainClass` in `build.gradle` selects which runs.

### Engine (`com.tward.engine`)

**Board layer** (`engine.board`): `Board` holds piece positions; `Square` is a (row, col) value type; `Piece` carries type + colour; `Move` encodes from/to with promotion and special-move flags. `MoveDescriber` converts moves to standard chess notation (O-O, exd6 e.p., e8=Q, +/#) — pure and fully tested.

**Game layer** (`engine.game`): `ChessGame` is the authoritative game state (board + history + game-over detection). `MoveGenerator` produces all legal moves for a position — the performance-critical hot path hit millions of times per search. Its logger lives in the companion object (not per-instance) to avoid allocation overhead.

**Player layer** (`engine.player`):
- `Player` — base type for human or bot player.
- `MiniMaxBot` — alpha-beta minimax with configurable depth, `Evaluator`, and `MoveOrderer`. `nodesSearched` is public. Calls `orderer.reset()` before each search and `orderer.onBetaCutoff(move, ply, depth)` on cutoffs. `chooseMove` first checks `OpeningBook`.
- `MiniMaxIterativeDeepeningBot` — wraps minimax with ID to use the full time budget.
- `RandomBot` — baseline for tournament comparisons.

**Evaluators** (`engine.player.evaluator`): All implement `Evaluator.evaluate(game, depth): Int`, scored from White's perspective (white − black).
- `BasicEvaluator` — material only.
- `StandardEvaluator(aggression)` — material + middlegame PST + mobility + check/castle bonuses. `open` with `protected open` hooks (`locationValue`, `mobility`, `castled`) for subclasses. Its `mobility()` contains a non-local `return` inside a `foldRight` — leave it as-is.
- `AdaptiveEvaluator : StandardEvaluator()` — tapered (MG/EG blend). Computes `phase` once in its `evaluate` override then stashes it in a field the three overridden hooks read. **Not thread-safe** — each bot needs its own instance (the tournament factory ensures this).
- `PieceSquareTables` (object) — `gamePhase(board)` (0..24 from non-pawn material) and `locationValue(type, colour, square, phase)` (MG/EG linear blend). Row 0 = rank 8, White's perspective; Black mirrors vertically.

**Move ordering** (`engine.player.ordering`): `MoveOrderer` interface with `order(moves, ply)` + stateful hooks.
- `KillerHistoryMoveOrderer` — default for `MiniMaxBot`. Killer moves (2 slots/ply) + history heuristic layered over MVV-LVA. Score bands: captures (1,000,000+) > killer1 (900k) > killer2 (800k) > history (≤700k) > quiet (0). **Stateful and not thread-safe.**
- `MvvLvaMoveOrderer` — stateless; `scoreOf(move)` is public for reuse.
- `NoOpMoveOrderer` — baseline for measuring pruning benefit.

**Opening book** (`engine.openingBook`): `OpeningBook` caches parsed books per file path in a companion `ConcurrentHashMap`, so constructing many bots is cheap.

**Tournament** (`engine.tournament`): `BotSpec(name, createBot: (Colour)->ChessBot)` factory + `Tournament` that runs `totalGames` headless across a fixed thread pool. Games are claimed from a shared `AtomicInteger`; results tallied into atomics. Contenders alternate colours by game index. Keep `useOpeningBookMoves` enabled — bots are deterministic so the book provides variety. Always use **fresh bot instances per game** (bots hold per-game state like opening-book progress).

### UI (`com.tward.ui`)

Compose Desktop views. `BoardView` accepts `showResultDialog` and `onGameOver` params used by the tournament display. `TournamentView` shows one live game (via `BoardView`) while headless workers run; both claim from the same game pool so counts are never double-counted. `ChessMatch` is the UI-layer game model; `ClockManager` drives the chess clock.

### Logging (`com.tward.logging`)

Small facade over `java.util.logging`. `Log.of<T>()` → `AppLogger` with lazy `() -> String` messages at `info`/`debug`/`warn`/`error`. `LogConfig.configure(level)` called once in main.

- `info` — lifecycle / headline events (app start, game over, tournament standings).
- `debug` (JUL `FINE`) — per-move detail (each move, book-move selection, bot search nodes/score/time). Default level is INFO; set `Level.FINE` to see per-move detail.
- Move logging lives in `ChessMatch.makeMove` and `MiniMaxBot.chooseMove` only — **never** in `Board.makeMove`, `MoveGenerator`, or the minimax recursion (hot search path).

### Compose UI testing

Tests use `runComposeUiTest { setContent {...}; onNodeWithText(...).assertIsDisplayed() }` — no JUnit4 rule needed, coexists with JUnit5. For composables with a perpetual `LaunchedEffect` (e.g. `ChessClock`), set `mainClock.autoAdvance = false` before `setContent` to prevent the test hanging. To avoid loading image resources, test `CapturedPieces` with an empty piece list.
