#!/usr/bin/env bash
# T-12: every <uses-permission> in the app manifest must appear in PERMISSIONS.md.
# Usage: scripts/check-permissions.sh [manifest]   (default: the source manifest;
# CI passes the merged manifest so library-added permissions are caught too).
# Exit 0 when every permission is documented.
set -u
cd "$(dirname "$0")/.."

manifest="${1:-app/src/main/AndroidManifest.xml}"
doc="PERMISSIONS.md"
fail=0

echo "manifest: $manifest"
perms="$(grep -oE '<uses-permission[^>]*android:name="[^"]+"' "$manifest" | sed -E 's/.*android:name="([^"]+)".*/\1/' | sort -u)"
if [ -z "$perms" ]; then echo "FAIL no permission found in $manifest"; exit 1; fi

for p in $perms; do
  if grep -qF "\`$p\`" "$doc"; then
    echo "ok   $p"
  else
    echo "FAIL $p is declared but not documented in $doc"
    fail=1
  fi
done
exit "$fail"
