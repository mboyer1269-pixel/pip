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
# Known credential shapes, assembled from fragments so this file never matches itself.
pk="PRIVATE"" KEY-----"
check "private key"    -e "-----BEGIN [A-Z ]*$pk"
check "provider keys"  -e "(s""k-[A-Za-z0-9_-]{20,}|gh""p_[A-Za-z0-9]{30,}|github_p""at_[A-Za-z0-9_]{30,}|AK""IA[0-9A-Z]{16}|AI""za[0-9A-Za-z_-]{35}|xox""[baprs]-[A-Za-z0-9-]{10,})"
check "bearer literal" -e "Bearer [A-Za-z0-9._-]{20,}"
signing="$(find . -path ./.git -prune -o \( -name '*.jks' -o -name '*.keystore' -o -name '*.p12' -o -name 'local.properties' \) -print)"
if [ -n "$signing" ]; then echo "FAIL [signing or local files]"; echo "$signing"; fail=1; else echo "ok   [signing or local files]"; fi
apks="$(find . -path ./.git -prune -o -name '*.apk' -print)"
if [ -n "$apks" ]; then echo "FAIL [apk in tree]"; echo "$apks"; fail=1; else echo "ok   [apk in tree]"; fi

if command -v gitleaks >/dev/null 2>&1; then
  gitleaks detect --no-banner --redact || fail=1
else
  echo "skip [gitleaks] not installed"
fi

exit "$fail"
