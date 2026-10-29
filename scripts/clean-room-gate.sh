#!/usr/bin/env bash
# Clean-room similarity gate and secrets gate for Pip.
#
# Fails (exit 1) when the tree contains traces of the inspected application
# or credential-looking assignments. Run before every build and before
# Phase 7 delivery.
#
# The inspected product and publisher names are assembled from fragments so
# this script never matches itself. INSPECTION-LOG.md is the one file
# allowed to name them: it records the source that was inspected.

set -u
cd "$(dirname "$0")/.."

name="an""dee"
publisher="kuafu""ai"
fail=0

check() {
  local label="$1"; shift
  local out
  out="$(grep -RniE "$@" --exclude-dir=.git --exclude-dir=build --exclude-dir=.gradle . || true)"
  if [ -n "$out" ]; then
    echo "FAIL [$label]"
    echo "$out"
    fail=1
  else
    echo "ok   [$label]"
  fi
}

check "product name"   --exclude=INSPECTION-LOG.md "$name"
check "publisher"      --exclude=INSPECTION-LOG.md "$publisher"
check "wildcard bind"  "0\.0\.0\.0"
check "secrets"        "(api[_-]?key|secret|token|password)\s*=\s*\"[^\"]+\""
apks="$(find . -path ./.git -prune -o -name '*.apk' -print)"
if [ -n "$apks" ]; then echo "FAIL [apk in tree]"; echo "$apks"; fail=1; else echo "ok   [apk in tree]"; fi

if command -v gitleaks >/dev/null 2>&1; then
  gitleaks detect --no-banner --redact || fail=1
else
  echo "skip [gitleaks] not installed"
fi

exit "$fail"
