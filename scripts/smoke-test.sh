#!/usr/bin/env bash
# Device smoke test (CI emulator). Installs the debug APK and checks, on a real
# Android system, what unit tests cannot:
#   1. the app launches without crashing;
#   2. the push-to-talk gesture (ASSIST intent, SPEC 16) turns Pip on:
#      PipService runs in the foreground and S1 is posted (V1-1, FR-P.3);
#   3. the Stop action on S1 stops Pip (FR UI), and the home screen says so;
#   4. the English strings load when the app locale is English;
#   5. the crash buffer stays empty for the app.
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
echo "info app config: $(adb shell dumpsys activity -p "$pkg" activities | grep -m1 -oE "mLastReportedConfiguration=\{[^ ]+ [^ ]+ [^ ]+ [^ ]+" | tr -d "\r")"

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
adb shell input tap $xy
adb shell cmd statusbar collapse
for i in $(seq 1 15); do
  dump="$(adb shell dumpsys activity services "$pkg")"
  grep -q "PipService" <<<"$dump" || break
  sleep 1
done
grep -q "PipService" <<<"$dump" && fail "PipService still running after Stop"
echo "ok   Stop on S1 stopped Pip"

adb shell am start -W -n "$pkg/ca.michael.pip.ui.MainActivity" >/dev/null
wait_text "Allumer Pip" || { ui_labels; false; } || fail "home does not offer « Allumer Pip » after Stop"
echo "ok   home shows « Allumer Pip » (FR)"

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
