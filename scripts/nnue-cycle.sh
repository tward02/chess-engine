#!/usr/bin/env bash
# One NNUE distillation cycle on Linux (the self-hosted runner), one stage per call:
#
#   scripts/nnue-cycle.sh generate --name gen9 [--games 40000] [--nodes 30000] [--teacher default]
#   scripts/nnue-cycle.sh lichess  --name gen9 [--nodes 30000] [--lichess-max 2000]
#   scripts/nnue-cycle.sh train    --name gen9 [--extra-data auto|none|a.bin,b.bin] [--train-args "hidden=256 decay=0.01"]
#   scripts/nnue-cycle.sh gate     --name gen9 [--gate-args "-Pnnue.gate.timeMillis=20000"]
#   scripts/nnue-cycle.sh promote  --name gen9 [--force]
#
# The Linux twin of scripts/nnue-cycle.ps1 (same files, same seeds, same defaults), split into
# stages so a workflow can show and re-run each one. Data lives in $NNUE_DATA_DIR (default
# <repo>/nnue-data); on a runner point it outside the workspace, which checkout cleans.
#
# --extra-data auto (the default) trains on everything accumulated: every *-selfplay file plus the
# gen2 data. That is the recipe that has passed every gate since gen4.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
data_dir="${NNUE_DATA_DIR:-$repo_root/nnue-data}"
resources="$repo_root/engine/src/main/resources/nnue"

stage="${1:-}"
[[ -n "$stage" ]] && shift || { sed -n '2,15p' "$0"; exit 2; }

name=''
games=40000
nodes=30000
teacher='default'
threads=0
lichess_max=2000
extra_data='auto'
train_args=''
gate_args=''
force=false
while [[ $# -gt 0 ]]; do
    case "$1" in
        --name) name="$2"; shift 2 ;;
        --games) games="$2"; shift 2 ;;
        --nodes) nodes="$2"; shift 2 ;;
        --teacher) teacher="$2"; shift 2 ;;
        --threads) threads="$2"; shift 2 ;;
        --lichess-max) lichess_max="$2"; shift 2 ;;
        --extra-data) extra_data="$2"; shift 2 ;;
        --train-args) train_args="$2"; shift 2 ;;
        --gate-args) gate_args="$2"; shift 2 ;;
        --force) force=true; shift ;;
        *) echo "unknown option: $1" >&2; exit 2 ;;
    esac
done

[[ "$name" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "--name must be letters, digits, '.', '_' or '-'" >&2; exit 2; }
[[ "$data_dir" != *' '* ]] || { echo "NNUE_DATA_DIR must not contain spaces (the tools split on them)" >&2; exit 2; }
[[ "$threads" -gt 0 ]] || threads=$(( $(nproc) > 1 ? $(nproc) - 1 : 1 ))
mkdir -p "$data_dir"
# Git Bash on Windows: hand the JVM a native path (pwd -W), not an MSYS one. Linux has no -W.
data_dir="$(cd "$data_dir" && { pwd -W 2>/dev/null || pwd; })"

self_play="$data_dir/$name-selfplay.txt"
lichess_file="$data_dir/lichess-tward-bot.txt"
lichess_games="$data_dir/lichess-tward-bot.ndjson"
candidate="$data_dir/$name.nnue"
gate_log="$data_dir/$name-gate.log"

# A teacher is a bundled net name (default, default-v3) or a .nnue path.
if [[ "$teacher" == *.nnue || "$teacher" == */* ]]; then teacher="$(realpath "$teacher")"; fi

step() { printf '\n==> [%s] %s\n' "$(date +%H:%M)" "$*"; }
gradle() { (cd "$repo_root" && ./gradlew "$@" --console=plain); }

# Same seed as nnue-cycle.ps1: a new name gets new games, the same name reproduces them.
seed_for() {
    local s=0 i c
    for (( i = 0; i < ${#1}; i++ )); do
        printf -v c '%d' "'${1:i:1}"
        s=$(( (s * 31 + c) % 2147483647 ))
    done
    echo "$s"
}

# Prefer an up-to-date .bin; hand over the .txt otherwise (train packs it once).
pick() {
    local stem="$1"
    if [[ -f "$stem.txt" ]]; then echo "$stem.txt"; elif [[ -f "$stem.bin" ]]; then echo "$stem.bin"; fi
}

case "$stage" in
generate)
    step "Generating $games self-play games (omega, $nodes nodes, teacher $teacher, $threads threads)"
    gradle :engine:generateNnueData \
        "-PnnueArgs=games=$games bot=omega nodes=$nodes net=$teacher threads=$threads seed=$(seed_for "$name") out=$self_play"
    ;;

lichess)
    step "Re-labelling up to $lichess_max Lichess games with teacher $teacher"
    gradle :engine:importLichessGames \
        "-PnnueArgs=max=$lichess_max refresh=true bot=omega nodes=$nodes net=$teacher threads=$threads games=$lichess_games out=$lichess_file"
    ;;

train)
    data=()
    own="$(pick "${self_play%.txt}")"
    [[ -n "$own" ]] && data+=("$own")
    lichess="$(pick "${lichess_file%.txt}")"
    [[ -n "$lichess" ]] && data+=("$lichess")
    case "$extra_data" in
        none|'') ;;
        auto)
            shopt -s nullglob
            declare -A stems=()
            for f in "$data_dir"/*-selfplay.txt "$data_dir"/*-selfplay.bin "$data_dir"/data-gen2-*.txt "$data_dir"/data-gen2-*.bin; do
                stems["${f%.*}"]=1
            done
            for stem in $(printf '%s\n' "${!stems[@]}" | sort); do
                [[ "$stem" == "${self_play%.txt}" ]] && continue
                data+=("$(pick "$stem")")
            done
            ;;
        *)
            IFS=',' read -ra extras <<< "$extra_data"
            for f in "${extras[@]}"; do
                [[ "$f" == /* ]] || f="$data_dir/$f"
                data+=("$f")
            done
            ;;
    esac
    [[ ${#data[@]} -gt 0 ]] || { echo "No training data in $data_dir" >&2; exit 1; }
    for f in "${data[@]}"; do [[ -f "$f" && "$f" != *' '* ]] || { echo "Training data missing or has a space: $f" >&2; exit 1; }; done

    text=()
    for f in "${data[@]}"; do [[ "$f" == *.bin ]] || text+=("$f"); done
    if [[ ${#text[@]} -gt 0 ]]; then
        step "Packing ${#text[@]} text file(s) to binary (skipped when up to date)"
        gradle :engine:packNnueData "-PnnueArgs=data=$(IFS=','; echo "${text[*]}")"
        for i in "${!data[@]}"; do data[$i]="${data[$i]%.txt}"; [[ "${data[$i]}" == *.bin ]] || data[$i]="${data[$i]}.bin"; done
    fi

    step "Training $name on ${#data[@]} file(s):"
    printf '    %s\n' "${data[@]}"
    # Defaults match nnue-cycle.ps1; --train-args comes last, so its keys override them.
    gradle :engine:trainNnue \
        "-PnnueArgs=data=$(IFS=','; echo "${data[*]}") out=$candidate hidden=128 epochs=12 blend=0.3 lr=0.002 batch=1024 threads=$(nproc)${train_args:+ $train_args}"
    ;;

gate)
    [[ -f "$candidate" ]] || { echo "No candidate net at $candidate" >&2; exit 1; }
    step "Gating $name vs default (SPRT on Omega). Log: $gate_log"
    # shellcheck disable=SC2086  # gate_args is a list of -P options
    gradle :engine:longTest --tests 'engine.tournament.NnueGate' "-Pnnue.gate.candidate=$candidate" $gate_args --rerun \
        | tee "$gate_log"
    ;;

promote)
    if ! grep -q 'RESULT: PASSED' "$gate_log" 2>/dev/null && [[ "$force" != true ]]; then
        echo "Not promoting: $gate_log does not show 'RESULT: PASSED'. Use --force to override." >&2
        exit 1
    fi
    n=0
    while [[ -f "$resources/default-v$n.nnue" ]]; do n=$((n + 1)); done
    step "Promoting $name: default.nnue -> default-v$n.nnue, $name.nnue -> default.nnue"
    cp "$resources/default.nnue" "$resources/default-v$n.nnue"
    cp "$candidate" "$resources/default.nnue"
    ;;

*)
    echo "unknown stage: $stage (generate, lichess, train, gate, promote)" >&2
    exit 2
    ;;
esac
