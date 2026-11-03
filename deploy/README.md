# Deploying the bot + monitor on a Proxmox VM

Target: Debian 12/13 or Ubuntu 24.04 server, 2–4 vCPU (CPU type **host**), 2–4 GB RAM.
Both services run as an unprivileged `chessbot` user, which also lets the monitor see the
lichess-bot process (it can only read command lines of its own user's processes).

## 1. Build on your PC

```powershell
.\gradlew.bat :desktop:uciJar :monitor:installDist
```

Produces `desktop/build/libs/uci-engine.jar` and `monitor/build/install/chess-monitor/`.

## 2. Prepare the VM

```bash
sudo apt install -y openjdk-21-jre-headless python3-venv rsync
sudo useradd --system --create-home --home-dir /opt/chessbot chessbot
sudo mkdir -p /etc/chessbot
```

Copy everything over (from the repo root on your PC):

```bash
scp -r lichess-bot-master            vm:/tmp/lichess-bot
scp desktop/build/libs/uci-engine.jar vm:/tmp/
scp -r monitor/build/install/chess-monitor vm:/tmp/
scp deploy/*.service deploy/monitor.env.example vm:/tmp/
```

On the VM:

```bash
sudo rsync -a --exclude venv --exclude __pycache__ /tmp/lichess-bot/ /opt/chessbot/lichess-bot/
sudo cp /tmp/uci-engine.jar /opt/chessbot/lichess-bot/engines/
sudo rsync -a --delete /tmp/chess-monitor/ /opt/chessbot/chess-monitor/
sudo chown -R chessbot:chessbot /opt/chessbot

sudo -u chessbot python3 -m venv /opt/chessbot/lichess-bot/venv
sudo -u chessbot /opt/chessbot/lichess-bot/venv/bin/pip install -r /opt/chessbot/lichess-bot/requirements.txt
```

Edit `/opt/chessbot/lichess-bot/config.yml` for Linux paths, and give the JVM a fixed heap:

```yaml
engine:
  dir: "/opt/chessbot/lichess-bot/engines/"
  name: "uci-engine.jar"
  interpreter: "java"
  interpreter_options:
    - "-Xmx1g"
    - "-jar"
```

## 3. Configure and start

```bash
sudo cp /tmp/monitor.env.example /etc/chessbot/monitor.env
sudo nano /etc/chessbot/monitor.env          # token, MQTT broker
sudo chown root:chessbot /etc/chessbot/monitor.env && sudo chmod 640 /etc/chessbot/monitor.env

sudo cp /tmp/lichess-bot.service /tmp/chess-monitor.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now lichess-bot chess-monitor
journalctl -fu chess-monitor
```

Dashboard: `http://192.168.50.5:8090/` at home, `http://100.113.229.8:8090/` over Tailscale. JSON: `/api/status`.

**Updating the engine** (from your PC, no downtime): copy `deploy.env.example` to `deploy.env`
(git-ignored), fill it in, then run `.\deploy\deploy-engine.ps1`. It runs the tests, builds the jar,
uploads it to the first reachable host in `VM_HOSTS`, checks the checksum and the UCI handshake on
the VM, backs up the live jar and swaps the new one in with an atomic rename. lichess-bot starts a
fresh engine per game, so the current game finishes on the old jar and the next one uses the new
jar. Flags: `-SkipTests`, `-SkipBuild`, `-Rollback` (restores the latest backup).

To update the monitor, re-copy its folder and `sudo systemctl restart chess-monitor`.
Files copied from Windows lose their execute bit, and lichess-bot refuses to start an engine
without one, so after every copy run
`chmod +x /opt/chessbot/lichess-bot/engines/uci-engine.jar /opt/chessbot/chess-monitor/bin/chess-monitor`.

## Playing strength on the VM

- **Pondering** is on: keep `ponder: true` in `config.yml`. The engine thinks on the opponent's time
  and keeps the result in its hash table, so give the VM **at least 2 vCPUs** — the ponder search,
  lichess-bot and the monitor shouldn't fight over one core.
- **Speed check**: `java -jar /opt/chessbot/lichess-bot/engines/uci-engine.jar bench` on the VM runs a
  fixed-depth benchmark (about 10s on a PC) and prints nodes/s. Run the same command on your PC to
  see how much the VM costs. The node count is a fingerprint: it's identical on every machine for
  the same jar. (It runs a second JVM, so do it between games, or expect that game to be slower.)
  In play, `journalctl -u lichess-bot | grep chose | tail` shows `nodes=` and `total=` per move.
- **Proxmox host**: CPU type `host`, and the host's CPU governor on `performance`
  (`cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor` on the Proxmox shell).
- **`move_overhead`**: subtracted from the clock on every move. 2000 ms is generous; if the logs show
  no flagging, try 1000.
- **Online endgame tablebases** (free, perfect play with ≤7 pieces): in `config.yml` under
  `online_moves.online_egtb` set `enabled: true` and `min_time: 10`.

## 4. Home Assistant

1. Install the **Mosquitto broker** add-on, and create an HA user for the monitor (Settings → People).
   Put its credentials in `MQTT_USERNAME`/`MQTT_PASSWORD`.
2. Enable the **MQTT** integration. A device **"Chess bot tward-bot"** appears with its entities:
   state, Lichess online, process running, in game, live games, current opponent, current eval,
   last result, a rating per speed, and today's games/wins/losses/draws/rating change.
3. Show the dashboard inside HA: Settings → Dashboards → Add dashboard → **Webpage**, URL
   `http://<vm-ip>:8090/`. (If HA is served over HTTPS, the browser blocks an http iframe — use
   the same scheme for both, e.g. put the monitor behind HA's reverse proxy or Tailscale serve.)

Example automation — notify when the bot goes down:

```yaml
alias: Chess bot down
triggers:
  - trigger: state
    entity_id: sensor.chess_bot_tward_bot_state
    to: [down, offline]
    for: { minutes: 5 }
actions:
  - action: notify.mobile_app_your_phone
    data:
      message: "Chess bot is {{ trigger.to_state.state }}"
```

Entity IDs are derived from the device and entity names; check the exact ones on the device page.
