#!/usr/bin/env bash
# Device smoke test (CI emulator). Installs the debug APK and checks, on a real
# Android system, what unit tests cannot:
#   1. the app launches without crashing;
#   2. the push-to-talk gesture (ASSIST intent, SPEC 16) turns Pip on:
#      PipService runs in the foreground and S1 is posted (V1-1, FR-P.3);
#   3. the crash buffer stays empty for the app.
# Usage: scripts/smoke-test.sh path/to/app-debug.apk
set -euo pipefail
apk="$1"
pkg="ca.michael.pip.debug"
fail() { echo "SMOKE FAIL: $*"; adb logcat -b crash -d || true; exit 1; }

adb wait-for-device
adb shell 'while [ "$(getprop sys.boot_completed)" != "1" ]; do sleep 2; done'
adb logcat -b crash -c || true

adb install -r -g "$apk"
echo "ok   installed (all runtime permissions granted with -g)"

adb shell am start -W -n "$pkg/ca.michael.pip.ui.MainActivity" >/dev/null
sleep 3
adb shell pidof "$pkg" >/dev/null || fail "app process not running after launch"
echo "ok   launched"

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

sleep 3
crashes="$(adb logcat -b crash -d | grep -c "$pkg" || true)"
[ "$crashes" = "0" ] || fail "crash buffer mentions $pkg"
adb shell pidof "$pkg" >/dev/null || fail "app process died"
echo "ok   no crash for $pkg"
echo "SMOKE PASS"
