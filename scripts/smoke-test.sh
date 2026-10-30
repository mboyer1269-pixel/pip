#!/usr/bin/env bash
# Device smoke test (CI emulator). Installs the debug APK and checks, on a real
# Android system, what unit tests cannot:
#   1. the app launches without crashing;
#   2. the push-to-talk gesture (ASSIST intent, SPEC 16) turns Pip on:
#      PipService runs in the foreground and S1 is posted (V1-1, FR-P.3);
#   3. the Stop action on S1 stops Pip (FR UI), and the home screen says so;
#   4. the bubble (SPEC 15) shows over another app once enabled; a tap brings
#      Pip to the front, a drag only moves it, a long press reads the app
#      underneath (FR-B.6); with Pip as the assistant, the ASSIST key turns
#      Pip on (SPEC 16);
#   5. the English strings load when the app locale is English;
#   6. the crash buffer stays empty for the app.
# Usage: scripts/smoke-test.sh path/to/app-debug.apk
set -euo pipefail
apk="$1"
pkg="ca.michael.pip.debug"
fail() { echo "SMOKE FAIL: $*"; adb logcat -b crash -d || true; exit 1; }

# UI helpers (uiautomator). find_text prints the centre "x y" of the first node
# whose text or content-desc matches $1 (case-insensitive, exact), or nothing.
# ui_labels lists every visible label, for failure reports.
ui_dump() { adb shell uiautomator dump /sdcard/pip-ui.xml >/dev/null 2>&1 || true; adb shell cat /sdcard/pip-ui.xml 2>/dev/null || true; }
find_text() {
  ui_dump | python3 -c '
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[1].casefold()
try: root = ET.fromstring(sys.stdin.read())
except ET.ParseError: sys.exit(0)
for n in root.iter("node"):
    labels = {n.get("text", "").strip().casefold(), n.get("content-desc", "").strip().casefold()}
    if want in labels:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
        print((x1 + x2) // 2, (y1 + y2) // 2); break
' "$1"
}
ui_labels() {
  ui_dump | python3 -c '
import sys, xml.etree.ElementTree as ET
try: root = ET.fromstring(sys.stdin.read())
except ET.ParseError: print("(no UI dump)"); sys.exit(0)
seen = [l for n in root.iter("node") for l in (n.get("text", ""), n.get("content-desc", "")) if l.strip()]
print("visible: " + " | ".join(seen))
'
}
# scroll_to_text: wait_text, swiping up between tries (long screens).
scroll_to_text() {
  size="$(adb shell wm size | grep -oE '[0-9]+x[0-9]+' | tail -n 1 || true)"; [ -n "$size" ] || size=1080x1920
  w="${size%x*}"; h="${size#*x}"
  for i in 1 2 3 4 5 6; do
    xy="$(find_text "$1")"; [ -n "$xy" ] && return 0
    adb shell input swipe $((w / 2)) $((h * 3 / 4)) $((w / 2)) $((h / 4)) 300
    sleep 1
  done
  return 1
}
# bubble_pos prints the centre "x y" of Pip's overlay window, or nothing.
bubble_pos() {
  adb shell dumpsys window windows | python3 -c '
import re, sys
pkg = sys.argv[1]
for block in sys.stdin.read().split("Window #")[1:]:
    if "APPLICATION_OVERLAY" in block and pkg in block:
        m = re.search(r"(?:mFrame|\bframe)=\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", block)
        if m:
            x1, y1, x2, y2 = map(int, m.groups())
            print((x1 + x2) // 2, (y1 + y2) // 2); break
' "$pkg"
}
# resumed prints the resumed activity line.
resumed() {
  acts="$(adb shell dumpsys activity activities)"
  grep -E "topResumedActivity|mResumedActivity" <<<"$acts" | head -n 1 | tr -d "\r" || true
}
wait_text() { for i in $(seq 1 15); do xy="$(find_text "$1")"; [ -n "$xy" ] && return 0; sleep 1; done; return 1; }

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
adb logcat -b crash -c || true

adb install -r -g "$apk"
echo "ok   installed (all runtime permissions granted with -g)"
adb shell cmd locale set-app-locales "$pkg" --locales fr >/dev/null
echo "info app locale: $(adb shell cmd locale get-app-locales "$pkg" 2>&1 | tr -d "\r")"

adb shell am start -W -n "$pkg/ca.michael.pip.ui.MainActivity" >/dev/null
sleep 3
adb shell pidof "$pkg" >/dev/null || fail "app process not running after launch"
echo "ok   launched"
echo "info home screen: $(ui_labels)"

# Push to talk: the assistant gesture arrives as ASSIST; Pip must turn itself on.
adb shell am start -W -a android.intent.action.ASSIST -n "$pkg/ca.michael.pip.ui.MainActivity" >/dev/null
# Full dump (-a): the short one omits isForeground. Poll, since the service is
# listed before it calls startForeground. Output is captured first so grep -q
# cannot SIGPIPE adb under pipefail.
dump=""
for i in $(seq 1 20); do
  dump="$(adb shell dumpsys activity -a services "$pkg")"
  if grep -q "isForeground=true" <<<"$dump"; then break; fi
  sleep 1
done
grep -q "PipService" <<<"$dump" || fail "PipService not running after ASSIST"
if ! grep -q "isForeground=true" <<<"$dump"; then
  grep -iE "ServiceRecord|isForeground|foreground" <<<"$dump" || true
  fail "PipService is not in the foreground"
fi
echo "ok   ASSIST turned Pip on (foreground service running)"

notif="$(adb shell dumpsys notification --noredact)"
grep -q "pip_status" <<<"$notif" || fail "S1 notification (channel pip_status) not posted"
echo "ok   S1 notification posted"

# Stop from S1 (posted in French: the locale was forced before launch). The
# shade may show S1 collapsed; a downward swipe on its title expands it.
adb shell cmd statusbar expand-notifications
if ! wait_text "Arrêter"; then
  title="$(find_text "Pip (debug)")"
  [ -n "$title" ] || { ui_labels; fail "S1 not visible in the shade"; }
  set -- $title
  adb shell input swipe "$1" "$2" "$1" "$(( $2 + 400 ))" 300
  if ! wait_text "Arrêter"; then
    exp="$(find_text "Expand")"
    [ -n "$exp" ] && adb shell input tap $exp
    wait_text "Arrêter" || { ui_labels; fail "S1 Stop action (Arrêter) not visible in the shade"; }
  fi
fi
# Tap only once the position is stable (two identical reads: the shade may
# still be settling), then check Pip stopped. Up to 3 taps; each is logged.
stopped=no
for attempt in 1 2 3; do
  prev=""
  for i in $(seq 1 10); do
    xy="$(find_text "Arrêter")"
    [ -n "$xy" ] && [ "$xy" = "$prev" ] && break
    prev="$xy"; sleep 1
  done
  [ -n "$xy" ] || break
  echo "info tap Arrêter at $xy (attempt $attempt)"
  adb shell input tap $xy
  for i in $(seq 1 10); do
    dump="$(adb shell dumpsys activity services "$pkg")"
    grep -q "PipService" <<<"$dump" || { stopped=yes; break; }
    sleep 1
  done
  [ "$stopped" = yes ] && break
  adb shell cmd statusbar expand-notifications
done
adb shell cmd statusbar collapse
[ "$stopped" = yes ] || { ui_labels; fail "PipService still running after Stop"; }
echo "ok   Stop on S1 stopped Pip"

adb shell am start -W -n "$pkg/ca.michael.pip.ui.MainActivity" >/dev/null
wait_text "Allumer Pip" || { ui_labels; false; } || fail "home does not offer « Allumer Pip » after Stop"
echo "ok   home shows « Allumer Pip » (FR)"

# Bubble (SPEC 15). The overlay permission is pre-granted here; on the phone the
# owner grants it in Android's own screen (FR-B.2).
adb shell appops set "$pkg" SYSTEM_ALERT_WINDOW allow
adb shell input tap $xy   # « Allumer Pip », found just above
scroll_to_text "Réglages" || { ui_labels; fail "home has no « Réglages » button"; }
adb shell input tap $xy
scroll_to_text "Afficher la bulle de Pip par-dessus les autres apps" || { ui_labels; fail "bubble toggle not found in Réglages"; }
adb shell input tap $xy
sleep 2
adb shell input keyevent KEYCODE_HOME
bubble_xy=""
for i in $(seq 1 15); do
  bubble_xy="$(bubble_pos)"
  [ -n "$bubble_xy" ] && break
  sleep 1
done
if [ -z "$bubble_xy" ]; then
  adb shell dumpsys window windows | grep -E "Window #|ty=|mFrame=" | grep -B1 -A1 -iE "overlay|$pkg" | head -40 || true
  fail "bubble overlay window not shown over the launcher"
fi
echo "ok   bubble shown over another app (at $bubble_xy)"
adb shell input tap $bubble_xy
top=""
for i in $(seq 1 10); do
  acts="$(adb shell dumpsys activity activities)"
  top="$(grep -E "topResumedActivity|mResumedActivity" <<<"$acts" | head -n 1 | tr -d "\r" || true)"
  grep -q "$pkg/ca.michael.pip.ui.MainActivity" <<<"$top" && break
  sleep 1
done
grep -q "$pkg/ca.michael.pip.ui.MainActivity" <<<"$top" || fail "tap on the bubble did not bring Pip to the front ($top)"
echo "ok   tap on the bubble opened Pip"

# Drag (FR-B.3): the bubble moves and Pip does not open.
adb shell input keyevent KEYCODE_HOME
sleep 2
bubble_xy="$(bubble_pos)"; [ -n "$bubble_xy" ] || fail "bubble gone after going home"
set -- $bubble_xy
adb shell input swipe "$1" "$2" "$1" "$(( $2 + 250 ))" 300
sleep 2
moved="$(bubble_pos)"
[ -n "$moved" ] && [ "$moved" != "$bubble_xy" ] || fail "drag did not move the bubble ($bubble_xy -> $moved)"
grep -q "$pkg/ca.michael.pip.ui.MainActivity" <<<"$(resumed)" && fail "drag opened Pip (a drag must only move the bubble)"
echo "ok   drag moved the bubble ($bubble_xy -> $moved) without opening Pip"

# Long press (FR-B.6): with accessibility on, Pip reads the app underneath and opens.
adb shell settings put secure enabled_accessibility_services "$pkg/ca.michael.pip.screen.PipAccessibilityService"
adb shell settings put secure accessibility_enabled 1
sleep 3
set -- $moved
adb shell input swipe "$1" "$2" "$1" "$2" 1500
# Proof of the capture: « Voir ce que j'ai lu » only shows when this turn holds a
# snapshot (the « J'ai lu l'écran… » line is soon replaced by the voice outcome).
wait_text "Voir ce que j'ai lu" \
  || { ui_labels; fail "long press on the bubble did not read the screen"; }
echo "ok   long press read the app underneath and opened Pip"

# Assistant gesture (SPEC 16): Pip as the assistant role holder, Pip off, then
# the system ASSIST key must turn Pip on (foreground service).
adb shell cmd role add-role-holder --user 0 android.app.role.ASSISTANT "$pkg" || true
echo "info assistant role: $(adb shell cmd role get-role-holders --user 0 android.app.role.ASSISTANT | tr -d "\r")"
adb shell am force-stop "$pkg"
adb shell input keyevent KEYCODE_HOME
sleep 2
adb shell input keyevent KEYCODE_ASSIST
dump=""
for i in $(seq 1 20); do
  dump="$(adb shell dumpsys activity -a services "$pkg")"
  grep -q "isForeground=true" <<<"$dump" && break
  sleep 1
done
grep -q "isForeground=true" <<<"$dump" || fail "ASSIST key with Pip as assistant did not turn Pip on ($(resumed))"
echo "ok   assistant key (Pip as assistant) turned Pip on"

# English strings (values/) load when the app locale is English.
adb shell cmd locale set-app-locales "$pkg" --locales en >/dev/null
adb shell am force-stop "$pkg"
adb shell am start -W -n "$pkg/ca.michael.pip.ui.MainActivity" >/dev/null
wait_text "Turn Pip on" || { ui_labels; false; } || fail "English home string (Turn Pip on) not shown"
echo "ok   home shows \"Turn Pip on\" (EN)"

sleep 3
crashes="$(adb logcat -b crash -d | grep -c "$pkg" || true)"
[ "$crashes" = "0" ] || fail "crash buffer mentions $pkg"
adb shell pidof "$pkg" >/dev/null || fail "app process died"
echo "ok   no crash for $pkg"
echo "SMOKE PASS"
