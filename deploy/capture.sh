#!/usr/bin/env bash
#
# Captures the Play Store screenshots from a running emulator.
#
#   ./deploy/capture.sh phone      1080x2160 @ 420dpi  (exactly 2:1, Play’s tallest accepted phone ratio)
#   ./deploy/capture.sh tablet-7   1200x1920 @ 240dpi
#   ./deploy/capture.sh tablet-10  1600x2560 @ 280dpi
#   ./deploy/capture.sh all
#
# The tablet forms are the same emulator resized with `wm size`/`wm density`, which is what
# Play actually judges: the screenshot's pixel dimensions and the layout at that width.
# Run ./build.sh install first, or pass an APK path as the second argument.
set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."
export PATH="$PATH:$HOME/Android/Sdk/platform-tools"

PKG=id.larus.adzan
ACTIVITY=$PKG/com.rusydiana.adzantime.ui.MainActivity
OUT=deploy/screenshots

adb get-state >/dev/null || { echo "no device — start an emulator first" >&2; exit 1; }

# A believable, stable frame: fixed clock, full battery, no notification clutter.
demo() { adb shell am broadcast -a com.android.systemui.demo -e command "$@" >/dev/null; }
freeze_status_bar() {
    adb shell settings put global sysui_demo_allowed 1 >/dev/null
    # Leave first: re-entering demo mode without exiting stacks a second copy of every icon
    # onto the status bar, and it is cumulative across runs.
    demo exit
    demo enter
    demo clock -e hhmm 1030
    demo battery -e level 100 -e plugged false
    # One clean wifi glyph: `fully true` drops the "no internet" exclamation the emulator
    # would otherwise show, and hiding mobile stops the dual-SIM icons doubling up.
    demo network -e wifi show -e level 4 -e fully true -e mobile hide -e sims 1 -e nosim hide
    demo notifications -e visible false
}

shoot() { sleep 2; adb exec-out screencap -p > "$1"; }

# Scrolls the current screen back to the top, then down by `$1` steps. The downward swipes are
# deliberately slow and short so they drag rather than fling — a fling lands wherever it likes
# and the same step count would frame a different section on every run.
scroll_to() {
    local n=$1 i
    local top=$((CUR_H * 40 / 100)) bottom=$((CUR_H * 70 / 100)) x=$((CUR_W / 2))
    for i in $(seq 8); do adb shell input swipe $x $top $x $bottom 60; done
    sleep 1
    for i in $(seq "$n"); do adb shell input swipe $x $bottom $x $top 500; sleep 1; done
}

capture_set() {
    local dir="$OUT/$1" w=$2 h=$3 d=$4 s2=$5 s3=$6
    CUR_W=$w CUR_H=$h
    mkdir -p "$dir"
    adb shell wm size "${w}x${h}" >/dev/null
    adb shell wm density "$d" >/dev/null
    sleep 2
    freeze_status_bar
    adb shell am force-stop $PKG
    adb shell am start -n $ACTIVITY >/dev/null
    sleep 5

    # The pill's centre, measured up from the bottom in dp: gesture inset, margin, half
    # the 68dp pill. In pixels that is density-dependent, so it has to be computed, not guessed.
    local tabs_y=$((h - d * 70 / 160)) x1=$((w * 16 / 100)) x2=$((w * 39 / 100)) x3=$((w * 61 / 100)) x4=$((w * 84 / 100))

    scroll_to 0; shoot "$dir/01-times.png"
    scroll_to "$s2"; shoot "$dir/02-calculation.png"
    scroll_to "$s3"; shoot "$dir/03-notifications.png"
    adb shell input tap $x2 $tabs_y; shoot "$dir/04-qibla.png"
    adb shell input tap $x3 $tabs_y; shoot "$dir/05-calendar.png"
    adb shell input tap $x4 $tabs_y; shoot "$dir/06-about.png"
    adb shell input tap $x1 $tabs_y >/dev/null

    echo "==> $dir  (${w}x${h} @ ${d}dpi)"
}

reset_display() { adb shell wm size reset >/dev/null; adb shell wm density reset >/dev/null; }
trap reset_display EXIT

case "${1:-all}" in
    phone)     capture_set phone     1080 2160 420 2 4 ;;
    tablet-7)  capture_set tablet-7  1200 1920 240 1 3 ;;
    tablet-10) capture_set tablet-10 1600 2560 280 1 2 ;;
    all)
        capture_set phone     1080 2160 420 2 4
        capture_set tablet-7  1200 1920 240 1 3
        capture_set tablet-10 1600 2560 280 1 2
        ;;
    *) echo "usage: $0 [phone|tablet-7|tablet-10|all]" >&2; exit 1 ;;
esac
