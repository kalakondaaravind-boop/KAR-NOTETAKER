#!/usr/bin/env bash
# Turns Kotlin/Gradle errors in a build log into GitHub error annotations (visible on the run page).
# GitHub shows at most 10 error annotations per step, so errors are grouped ~12 per annotation.
LOG="$1"
[ -f "$LOG" ] || exit 0
grep -E '^e: |error: |^\* What went wrong' -A2 "$LOG" | grep -v '^--$' | sed 's|file:///home/runner/work/[^/]*/[^/]*/||' > /tmp/errs.txt || true
[ -s /tmp/errs.txt ] || exit 0
split -l 12 /tmp/errs.txt /tmp/errchunk.
n=0
for f in /tmp/errchunk.*; do
  n=$((n+1)); [ $n -gt 9 ] && { echo "::error title=More errors::see the ci-logs-build branch for the full log"; break; }
  msg="$(sed ':a;N;$!ba;s/%/%25/g;s/\r//g;s/\n/%0A/g' "$f")"
  echo "::error title=Build errors (part $n)::$msg"
done
