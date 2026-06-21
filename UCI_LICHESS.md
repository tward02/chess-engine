# Running the engine on Lichess (UCI)

The engine speaks [UCI](https://backscattering.de/chess/uci/) over stdin/stdout via
`com.tward.app.UciApp`. Lichess doesn't talk UCI directly — you bridge it with the
official [`lichess-bot`](https://github.com/lichess-bot-devs/lichess-bot) Python client,
which connects to the Lichess Bot API and pipes moves to/from your engine process.

```
Lichess  <--HTTP/streaming-->  lichess-bot (Python)  <--UCI over stdio-->  uci-engine.jar
```

## 1. Build the engine jar

```bash
.\gradlew.bat :desktop:uciJar
```

Produces `desktop/build/libs/uci-engine.jar`, a standalone runnable jar. Test it locally:

```bash
printf 'uci\nisready\nposition startpos moves e2e4 e7e5\ngo movetime 1000\nquit\n' | java -jar desktop/build/libs/uci-engine.jar
```

You should see `id name ...`, `uciok`, `readyok`, then a `bestmove ...`. (Engine logs
go to stderr, so they never corrupt the UCI channel on stdout.)

> The jar is built from the `:desktop` module (where `com.tward.app.UciApp` lives) and
> bundles the `:engine` core. Prefer not to build a jar? You can also run the main class via
> `.\gradlew.bat :desktop:run` after setting `mainClass = 'com.tward.app.UciAppKt'` in
> `desktop/build.gradle`, but the jar is what `lichess-bot` should invoke.

## 2. Create a Lichess bot account

A bot account must have **zero games played**. Use a fresh account.

1. Register a new account at https://lichess.org/signup.
2. Create a personal API token at https://lichess.org/account/oauth/token/create
   with the **`bot:play`** scope.
3. Upgrade the account to a BOT account (irreversible) — `lichess-bot` does this for
   you on first run, or via:
   ```bash
   curl -d '' https://lichess.org/api/bot/account/upgrade -H "Authorization: Bearer <TOKEN>"
   ```

## 3. Install lichess-bot

```bash
git clone https://github.com/lichess-bot-devs/lichess-bot.git
cd lichess-bot
pip install -r requirements.txt
```

## 4. Point lichess-bot at the engine

Copy `lichess-bot`'s `config.yml.default` to `config.yml` and edit:

```yaml
token: "<YOUR_BOT_API_TOKEN>"

engine:
  dir: "C:/Users/tyler/IdeaProjects/chess-engine/build/libs"   # folder containing the command
  name: "run-uci.bat"        # see wrapper below (a jar isn't directly executable by lichess-bot)
  protocol: "uci"

  # Optional — no UCI options are implemented yet, leave empty:
  uci_options: {}

challenge:                   # which incoming challenges to accept
  concurrency: 1
  variants:
    - standard
  time_controls:
    - blitz
    - rapid
    - classical
```

`lichess-bot` runs `dir/name` as the engine process, so wrap the jar in a tiny script
(it expects an executable, not a `.jar`). Put this next to the jar as
`build/libs/run-uci.bat`:

```bat
@echo off
java -jar "%~dp0uci-engine.jar"
```

(On Linux/macOS use a `run-uci.sh` with `#!/usr/bin/env bash` + `exec java -jar "$(dirname "$0")/uci-engine.jar"` and `chmod +x` it.)

## 5. Run it

```bash
python lichess-bot.py
```

The bot logs in, accepts matching challenges, and starts playing. Challenge it from
another account (or enable `matchmaking` in `config.yml` to auto-seek games). After a
handful of rated games Lichess assigns it a rating.

## How the bridge maps to the engine

| UCI command                              | Handling in `UciEngine`                                                        |
| ---------------------------------------- | ------------------------------------------------------------------------------ |
| `uci`                                    | replies `id name`/`id author`/`uciok`                                           |
| `isready`                                | replies `readyok`                                                              |
| `ucinewgame`                             | resets to the start position; new bot built on next `go`                       |
| `position startpos [moves ...]`          | builds a `ChessGame`, replays moves via `UciMoveCodec`                         |
| `position fen <fen> [moves ...]`         | parses with `Board.fromFEN`, replays moves                                     |
| `go wtime .. btime .. [movetime ..]`     | passes the side-to-move's clock (or `movetime`) as `timeLeft` to the bot       |
| `stop`                                   | no-op (search is synchronous; `bestmove` is already sent)                      |
| `quit`                                   | exits                                                                          |

The bot is `AdvancedNegamaxBot` by default — the strongest engine. It extends `NegamaxBot`
(iterative-deepening negamax + alpha-beta, PVS, quiescence search, check extensions,
killer/history ordering) and adds a Zobrist transposition table, null-move pruning, late
move reductions, static-exchange-evaluation capture pruning, aspiration windows and a deeper
opening book, all over the fast `CompactEvaluator`. It manages its own think time from the
clock Lichess sends, and logs the search depth reached to stderr each move. To play a
different/weaker bot, change the `botFactory` lambda in `com.tward.app.UciApp`. No existing
engine class behaviour is changed by the UCI layer.

## Notes / limitations

- No `option` lines are advertised, so there's nothing to configure from the GUI side.
- `go infinite` / pondering is not supported; every `go` searches and returns one move.
- Move underpromotion is supported (`e7e8q/r/b/n`), as is castling (`e1g1`) and en passant.