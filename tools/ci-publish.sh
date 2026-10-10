#!/usr/bin/env bash
# Publishes a folder of CI output (logs, screenshots) to a dedicated branch so it can be read with plain git,
# even where artifact downloads are blocked. The branch only ever holds the latest run's output.
# Usage: tools/ci-publish.sh <folder> <branch>     (needs GITHUB_TOKEN with contents: write)
set -u
SRC="$1"; BRANCH="$2"
[ -d "$SRC" ] || { echo "nothing to publish"; exit 0; }
TMP="$(mktemp -d)"
cp -r "$SRC"/. "$TMP"/
cd "$TMP"
echo "run ${GITHUB_RUN_ID:-local} · commit ${GITHUB_SHA:-?} · $(date -u +%FT%TZ)" > RUN.txt
git init -q -b "$BRANCH"
git add -A
git -c user.name="ci" -c user.email="ci@users.noreply.github.com" commit -qm "CI output for run ${GITHUB_RUN_ID:-local}"
git push -qf "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "$BRANCH" && echo "published to $BRANCH"
