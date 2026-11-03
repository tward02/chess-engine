#!/usr/bin/env bash
# Runs on the VM (copied and invoked by deploy-engine.ps1).
#   install  <uploaded-jar> <sha256> <engine-dir> <engine-name> <keep-backups>
#   rollback <engine-dir> <engine-name>
#
# The live jar is replaced with an atomic rename: lichess-bot starts a fresh engine process per
# game, so the game in progress keeps the old file and the next game picks up the new one. No
# service restart, no downtime.
set -euo pipefail

mode=$1; shift

pick_sudo() {
    SUDO=""
    [ -w "$1" ] || SUDO="sudo"
}

swap_in() {   # <source-file> <engine-dir> <engine-name>
    local src=$1 dir=$2 name=$3
    # Staged in the same directory so the final mv is a same-filesystem (atomic) rename.
    $SUDO install -m 755 "$src" "$dir/$name.new"
    $SUDO mv -f "$dir/$name.new" "$dir/$name"
}

case "$mode" in
install)
    upload=$1 sha=$2 dir=$3 name=$4 keep=$5
    pick_sudo "$dir"

    actual=$(sha256sum "$upload" | cut -d' ' -f1)
    if [ "$actual" != "$sha" ]; then
        echo "Checksum mismatch: expected $sha, got $actual. Live jar untouched." >&2
        exit 1
    fi
    echo "Checksum OK."

    if ! printf 'uci\nquit\n' | timeout 30 java -jar "$upload" 2>/dev/null | grep -q '^uciok'; then
        echo "New jar failed the UCI handshake on this VM. Live jar untouched." >&2
        exit 1
    fi
    echo "UCI handshake OK."

    if [ -f "$dir/$name" ]; then
        backup="$dir/$name.$(date +%Y%m%d-%H%M%S).bak"
        $SUDO cp -p "$dir/$name" "$backup"
        echo "Backed up current jar to $backup"
    fi

    swap_in "$upload" "$dir" "$name"
    rm -f "$upload"
    echo "Installed $dir/$name"

    # Prune old backups, keeping the newest.
    ls -1t "$dir/$name".*.bak 2>/dev/null | tail -n +$((keep + 1)) | while read -r old; do
        $SUDO rm -f "$old"
    done
    ;;
rollback)
    dir=$1 name=$2
    pick_sudo "$dir"
    latest=$(ls -1t "$dir/$name".*.bak 2>/dev/null | head -n 1 || true)
    if [ -z "$latest" ]; then
        echo "No backup found in $dir." >&2
        exit 1
    fi
    swap_in "$latest" "$dir" "$name"
    $SUDO rm -f "$latest"
    echo "Rolled back to $latest"
    ;;
*)
    echo "Unknown mode: $mode" >&2
    exit 2
    ;;
esac

if pgrep -f -- "-jar .*$name" >/dev/null; then
    echo "A game is in progress on the old engine; the new one is used from the next game."
else
    echo "No game in progress; the next game uses the new engine."
fi
