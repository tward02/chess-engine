"""Generates deploy/home-assistant-dashboard.yaml in the style of the user's Home Energy dashboard."""
import sys
import yaml

M = "sensor.chess_bot_tward_bot_"      # monitor (MQTT)
MB = "binary_sensor.chess_bot_tward_bot_"
L = "sensor.tward_bot_"                # official Lichess integration
# The monitor is reached on the LAN at home and over Tailscale when away; the browser loads the
# iframe, so pick by whether you're home (the person entity, driven by the phone app).
PERSON = "person.tyler"
VM_LAN = "http://192.168.50.5:8090/"
VM_TAILNET = "http://100.113.229.8:8090/"
AT_HOME = {"condition": "state", "entity": PERSON, "state": "home"}
AWAY = {"condition": "state", "entity": PERSON, "state_not": "home"}
TV = "https://lichess.org/@/tward-bot/tv"

BACKGROUND = (
    "radial-gradient(circle at 12% 8%, rgba(56,189,248,0.20), transparent 38%), "
    "radial-gradient(circle at 88% 92%, rgba(249,115,22,0.16), transparent 42%), "
    "linear-gradient(160deg, #070b14 0%, #0d1424 55%, #111827 100%) fixed"
)

# The energy dashboard's four card-mod styles, verbatim.
HERO = """ha-card {
  --primary-text-color: #f8fafc;
  --secondary-text-color: #94a3b8;
  --card-primary-font-size: 30px;
  --card-primary-font-weight: 800;
  --card-primary-letter-spacing: -0.5px;
  --card-secondary-font-size: 15px;
  --icon-size: 56px;
  background: none;
  border: none;
  box-shadow: none;
  padding: 8px 4px;
}
"""
TITLE = """ha-card {
  --primary-text-color: #f8fafc;
  --secondary-text-color: #64748b;
  --title-font-size: 22px;
  --title-font-weight: 700;
}
"""
TILE = """ha-card {
  --primary-text-color: #f8fafc;
  --secondary-text-color: #94a3b8;
  --card-primary-font-size: 22px;
  --card-primary-font-weight: 700;
  --card-secondary-font-size: 13px;
  --icon-size: 46px;
  background: rgba(255,255,255,0.05);
  backdrop-filter: blur(20px) saturate(140%);
  -webkit-backdrop-filter: blur(20px) saturate(140%);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 24px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.35);
}
"""
SMALL = """ha-card {
  --primary-text-color: #f8fafc;
  --secondary-text-color: #94a3b8;
  --card-primary-font-size: 16px;
  --card-primary-font-weight: 700;
  --card-secondary-font-size: 12px;
  background: rgba(255,255,255,0.04);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255,255,255,0.06);
  border-radius: 20px;
  box-shadow: none;
}
"""
GLASS = """ha-card {
  --primary-text-color: #f1f5f9;
  --secondary-text-color: #94a3b8;
  background: rgba(255,255,255,0.05);
  backdrop-filter: blur(20px) saturate(140%);
  -webkit-backdrop-filter: blur(20px) saturate(140%);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: 24px;
  box-shadow: 0 10px 30px rgba(0,0,0,0.35);
}
"""
GLASS_IFRAME = GLASS.replace("}\n", "  overflow: hidden;\n}\n")
CHIPS = """ha-card {
  --primary-text-color: #e2e8f0;
  --chip-background: rgba(255,255,255,0.07);
  --chip-border-width: 1px;
  --chip-border-color: rgba(255,255,255,0.08);
  --chip-border-radius: 20px;
  --chip-height: 40px;
  --chip-padding: 0 14px;
  --chip-font-size: 14px;
  --chip-icon-size: 18px;
  --chip-box-shadow: none;
  backdrop-filter: blur(14px);
}
"""

FULL = {"columns": "full"}
IN_GAME = [{"condition": "state", "entity": MB + "in_game", "state": "on"}]


def mod(style):
    return {"style": style}


def title(text, subtitle=None):
    card = {"type": "custom:mushroom-title-card", "title": text}
    if subtitle:
        card["subtitle"] = subtitle
    card["grid_options"] = FULL
    card["card_mod"] = mod(TITLE)
    return card


def template(primary, secondary, icon, icon_color, style, entity=None, layout=None, tap=None):
    card = {"type": "custom:mushroom-template-card"}
    if entity:
        card["entity"] = entity
    card["primary"] = primary
    card["secondary"] = secondary
    card["icon"] = icon
    card["icon_color"] = icon_color
    if layout:
        card["layout"] = layout
    card["tap_action"] = tap or {"action": "more-info"}
    card["card_mod"] = mod(style)
    return card


def row(cards, columns=4):
    return {"type": "grid", "columns": columns, "square": False, "grid_options": FULL, "cards": cards}


def chip(entity, icon, icon_color, content):
    return {"type": "template", "entity": entity, "icon": icon, "icon_color": icon_color,
            "content": content, "tap_action": {"action": "more-info"}}


def apex(title_text, span, series, extra=None, height=250):
    card = {
        "type": "custom:apexcharts-card",
        "grid_options": FULL,
        "graph_span": span,
        "header": {"show": True, "title": title_text},
        "apex_config": {
            "chart": {"height": height, "foreColor": "#94a3b8", "toolbar": {"show": False}},
            "grid": {"borderColor": "rgba(255,255,255,0.06)", "strokeDashArray": 4},
            "stroke": {"curve": "smooth", "width": 3},
            "legend": {"show": True, "position": "top", "horizontalAlign": "right"},
            "dataLabels": {"enabled": False},
            "tooltip": {"theme": "dark"},
            "yaxis": {"forceNiceScale": True, "decimalsInFloat": 0},
        },
        "series": series,
        "card_mod": mod(GLASS),
    }
    if extra:
        card.update(extra)
    return card


STATE_COLOR = ("{% set s = states('" + M + "state') %}"
               "{{ {'playing':'green','idle':'light-blue','offline':'orange','down':'red'}.get(s, 'blue-grey') }}")
ON_OFF_COLOR = "{{{{ 'green' if is_state('{e}', 'on') else 'red' }}}}"

hero = {
    "type": "grid",
    "column_span": 3,
    "cards": [
        {
            "type": "custom:mushroom-template-card",
            "entity": M + "state",
            "primary": (
                "{% set s = states('" + M + "state') %}"
                "{{ {'playing':'Playing ' ~ states('" + M + "current_opponent'),'idle':'Waiting for a challenge','offline':'Offline',"
                "'down':'Bot is down','starting':'Starting up'}.get(s, 'tward-bot') }}"
            ),
            "secondary": (
                "{% set g = states('" + M + "games_today') | int(0) %}"
                "{% set w = states('" + M + "wins_today') | int(0) %}"
                "{% set d = states('" + M + "draws_today') | int(0) %}"
                "{% set delta = states('" + M + "rating_change_today') | int(0) %}"
                "tward-bot · blitz {{ states('" + M + "blitz_rating') }} · "
                "{{ '%+d' | format(delta) }} today · {{ g }} game{{ '' if g == 1 else 's' }}"
                "{% if g > 0 %} · {{ ((w + 0.5 * d) / g * 100) | round(0) | int }}% score{% endif %}"
            ),
            "icon": (
                "{% set s = states('" + M + "state') %}"
                "{{ {'playing':'mdi:sword-cross','idle':'mdi:chess-king','offline':'mdi:lan-disconnect',"
                "'down':'mdi:robot-dead-outline'}.get(s, 'mdi:chess-king') }}"
            ),
            "icon_color": STATE_COLOR,
            "tap_action": {"action": "more-info"},
            "grid_options": FULL,
            "card_mod": mod(HERO),
        },
        {
            "type": "custom:mushroom-chips-card",
            "alignment": "start",
            "grid_options": FULL,
            "chips": [
                chip(MB + "process_running", "mdi:cog", ON_OFF_COLOR.format(e=MB + "process_running"),
                     "Process {{ 'up' if is_state('" + MB + "process_running', 'on') else 'down' }}"),
                chip(MB + "lichess_online", "mdi:web", ON_OFF_COLOR.format(e=MB + "lichess_online"),
                     "Lichess {{ 'online' if is_state('" + MB + "lichess_online', 'on') else 'offline' }}"),
                chip(M + "bullet_rating", "mdi:bullet", "deep-orange",
                     "Bullet {{ states('" + M + "bullet_rating') }}"),
                chip(M + "blitz_rating", "mdi:lightning-bolt", "cyan",
                     "Blitz {{ states('" + M + "blitz_rating') }}"),
                chip(M + "last_result", "mdi:flag-checkered",
                     "{% set r = states('" + M + "last_result') %}"
                     "{{ 'green' if r.startswith('Won') else 'red' if r.startswith('Lost') else 'blue-grey' }}",
                     "{{ states('" + M + "last_result') }}"),
            ],
            "card_mod": mod(CHIPS),
        },
        # Lichess's per-user TV page always shows the bot's current game, so a fixed URL works.
        dict(template(
            "Watch live game",
            "vs {{ states('" + M + "current_opponent') }} · opens Lichess TV",
            "mdi:television-play", "red", SMALL,
            tap={"action": "url", "url_path": TV},
        ), grid_options=FULL, visibility=IN_GAME),
    ],
}

live = {
    "type": "grid",
    "column_span": 2,
    "visibility": IN_GAME,
    "cards": [
        title("Live game", "Board, clocks and eval from the monitor"),
        {"type": "iframe", "url": VM_LAN, "aspect_ratio": "110%", "grid_options": FULL,
         "visibility": [AT_HOME], "card_mod": mod(GLASS_IFRAME)},
        {"type": "iframe", "url": VM_TAILNET, "aspect_ratio": "110%", "grid_options": FULL,
         "visibility": [AWAY], "card_mod": mod(GLASS_IFRAME)},
    ],
}

EVAL_CP = "states('" + M + "current_eval') | int(0)"
position = {
    "type": "grid",
    "column_span": 1,
    "visibility": IN_GAME,
    "cards": [
        title("Position"),
        template("{{ states('" + M + "current_opponent') }}", "Opponent", "mdi:account-tie", "deep-purple",
                 TILE, entity=M + "current_opponent", layout="vertical"),
        {
            "type": "gauge",
            "entity": M + "current_eval",
            "name": "Eval · bot's side",
            "min": -600, "max": 600, "needle": True,
            "severity": {"red": -600, "yellow": -150, "green": 150},
            "grid_options": FULL,
            "card_mod": mod(GLASS),
        },
        template(
            "{% set cp = " + EVAL_CP + " %}{{ '%+.1f' | format(cp / 100) }}",
            "{% set cp = " + EVAL_CP + " %}"
            "{{ 'Winning' if cp > 300 else 'Better' if cp > 80 else 'Losing' if cp < -300 "
            "else 'Worse' if cp < -80 else 'Level' }}",
            "mdi:scale-balance",
            "{% set cp = " + EVAL_CP + " %}{{ 'green' if cp > 80 else 'red' if cp < -80 else 'blue-grey' }}",
            SMALL, entity=M + "current_eval",
        ),
    ],
}

today = {
    "type": "grid",
    "column_span": 3,
    "cards": [
        title("Today", "Results since midnight"),
        row([
            template("{{ states('" + M + "wins_today') | int(0) }}", "Wins", "mdi:trophy", "green",
                     TILE, entity=M + "wins_today", layout="vertical"),
            template("{{ states('" + M + "draws_today') | int(0) }}", "Draws", "mdi:handshake", "blue-grey",
                     TILE, entity=M + "draws_today", layout="vertical"),
            template("{{ states('" + M + "losses_today') | int(0) }}", "Losses", "mdi:close-circle", "red",
                     TILE, entity=M + "losses_today", layout="vertical"),
            template("{{ '%+d' | format(states('" + M + "rating_change_today') | int(0)) }}", "Rating change",
                     "mdi:delta",
                     "{% set d = states('" + M + "rating_change_today') | int(0) %}"
                     "{{ 'green' if d > 0 else 'red' if d < 0 else 'blue-grey' }}",
                     TILE, entity=M + "rating_change_today", layout="vertical"),
        ]),
    ],
}

SPEEDS = [
    ("bullet", "Bullet", "mdi:bullet", "deep-orange", "#fb923c"),
    ("blitz", "Blitz", "mdi:lightning-bolt", "cyan", "#22d3ee"),
    ("rapid", "Rapid", "mdi:timer-sand", "light-green", "#a3e635"),
    ("classical", "Classical", "mdi:chess-rook", "purple", "#a78bfa"),
]

ratings = {
    "type": "grid",
    "column_span": 3,
    "cards": [
        title("Ratings", "Live from the monitor · games played from the Lichess integration"),
        row([
            template(
                "{{ states('" + M + key + "_rating') }}",
                name + " · {{ states('" + L + key + "_games') | int(0) }} games",
                icon, colour, TILE, entity=M + key + "_rating", layout="vertical",
            )
            for key, name, icon, colour, _ in SPEEDS
        ]),
        apex("Rating · last 48 hours", "48h", [
            {"entity": M + key + "_rating", "name": name, "type": "line", "color": hexc,
             "group_by": {"func": "last", "duration": "30min", "fill": "last"}}
            for key, name, _, _, hexc in SPEEDS if key in ("bullet", "blitz")
        ]),
        apex("Rating · last 30 days", "30d", [
            {"entity": M + key + "_rating", "name": name, "type": "line", "color": hexc,
             "statistics": {"type": "mean", "period": "day", "align": "start"}}
            for key, name, _, _, hexc in SPEEDS if key in ("bullet", "blitz")
        ], extra={"span": {"end": "day"}}),
    ],
}

feed = {
    "type": "grid",
    "column_span": 1,
    "cards": [
        title("Results feed", "Last 3 days"),
        {"type": "logbook", "entities": [M + "last_result"], "hours_to_show": 72,
         "grid_options": FULL, "card_mod": mod(GLASS)},
    ],
}

uptime = {
    "type": "grid",
    "column_span": 2,
    "cards": [
        title("Uptime", "Last 24 hours"),
        {"type": "history-graph", "hours_to_show": 24, "grid_options": FULL, "card_mod": mod(GLASS),
         "entities": [
             {"entity": M + "state", "name": "State"},
             {"entity": MB + "process_running", "name": "Process"},
             {"entity": MB + "lichess_online", "name": "Lichess"},
             {"entity": MB + "in_game", "name": "In game"},
         ]},
    ],
}

dashboard = {
    "title": "Chess Bot",
    "views": [
        {
            "title": "Overview",
            "path": "overview",
            "icon": "mdi:chess-king",
            "type": "sections",
            "max_columns": 3,
            "dense_section_placement": True,
            "background": BACKGROUND,
            "sections": [hero, live, position, today, ratings, feed, uptime],
        },
        {
            "title": "Live board",
            "path": "live",
            "icon": "mdi:checkerboard",
            "type": "panel",
            "background": BACKGROUND,
            "cards": [{
                "type": "vertical-stack",
                "cards": [
                    {"type": "conditional", "conditions": [AT_HOME],
                     "card": {"type": "iframe", "url": VM_LAN, "aspect_ratio": "75%", "card_mod": mod(GLASS_IFRAME)}},
                    {"type": "conditional", "conditions": [AWAY],
                     "card": {"type": "iframe", "url": VM_TAILNET, "aspect_ratio": "75%", "card_mod": mod(GLASS_IFRAME)}},
                ],
            }],
        },
    ],
}


class Dumper(yaml.SafeDumper):
    def increase_indent(self, flow=False, indentless=False):
        return super().increase_indent(flow, False)

    def ignore_aliases(self, data):
        return True


def str_presenter(dumper, data):
    if "\n" in data:
        return dumper.represent_scalar("tag:yaml.org,2002:str", data, style="|")
    if len(data) > 70:
        return dumper.represent_scalar("tag:yaml.org,2002:str", data, style=">")
    return dumper.represent_scalar("tag:yaml.org,2002:str", data)


Dumper.add_representer(str, str_presenter)

HEADER = """# Home Assistant dashboard for the chess bot, styled like the Home Energy dashboard (same
# background, card-mod glass styles, Mushroom + ApexCharts cards). Generated; edit freely.
#
# Combines the monitor's MQTT entities (sensor.chess_bot_tward_bot_*) with the official Lichess
# integration (sensor.tward_bot_*_games).
#
# Needs (HACS): Mushroom, card-mod, ApexCharts card. All three are already used by Home Energy.
#
# Install: Settings -> Dashboards -> Add dashboard -> "New dashboard from scratch" -> open it ->
# pencil -> three dots -> Raw configuration editor -> replace everything with this file -> Save.
#
# The live board loads from 192.168.50.5 when person.tyler is home and from the Tailscale address
# 100.113.229.8 when away. Before pasting, find/replace if different:
#   person.tyler          -> your person entity (Settings -> People; its location comes from the phone app)
#   sensor.tward_bot_     -> the Lichess integration's prefix (check its device page)

"""

with open(sys.argv[1], "w", encoding="utf-8", newline="\n") as f:
    f.write(HEADER)
    yaml.dump(dashboard, f, Dumper=Dumper, sort_keys=False, allow_unicode=True, width=100)
