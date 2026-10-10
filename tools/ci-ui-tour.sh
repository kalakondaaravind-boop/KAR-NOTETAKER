#!/usr/bin/env bash
# Walks through the installed debug app on an emulator (adb + uiautomator) and saves REAL screenshots.
# Used by .github/workflows/android.yml. Best-effort: a step that can't find its target is logged and skipped,
# so one changed label never hides the rest of the evidence.
#
# Usage: tools/ci-ui-tour.sh <output-dir>
set -u
PKG=com.kargroup.notetaker.debug
OUT="${1:-screenshots}"
mkdir -p "$OUT"
LOG="$OUT/ui-tour.log"
log() { echo "$*" | tee -a "$LOG"; }

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png" && log "screenshot: $1"; }

# Prints "x y" (centre) of the first on-screen element whose text or content-description equals $1.
find_center() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb exec-out cat /sdcard/ui.xml | python3 -c '
import re, sys, xml.etree.ElementTree as ET
want = sys.argv[1]
try: root = ET.fromstring(sys.stdin.read())
except Exception: sys.exit(0)
for n in root.iter("node"):
    if want in (n.get("text"), n.get("content-desc")):
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds", "")))
        print((x1 + x2) // 2, (y1 + y2) // 2); break
' "$1"
}

tap() {
  local c; c="$(find_center "$1")"
  if [ -n "$c" ]; then adb shell input tap $c; log "tap: $1"; sleep 1; return 0; fi
  log "NOT FOUND: $1"; return 1
}

type_text() { adb shell input text "$(echo "$1" | sed 's/ /%s/g')"; sleep 0.5; }

adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/app.margin.MainActivity" >/dev/null
shot 01-welcome 6

# Hidden OpenAI setup: 10 quick taps on the logo.
c="$(find_center 'KAR Group, Note taker')"
if [ -n "$c" ]; then
  for _ in 1 2 3 4 5 6 7 8 9 10; do adb shell input tap $c; done
  log "tapped logo 10x"
  shot 02-hidden-openai-setup 2
  tap "Cancel" || adb shell input keyevent KEYCODE_BACK
else
  log "NOT FOUND: logo"
fi

tap "Skip" && shot 03-home-empty 3

# A few quick-capture notes so Home has content (3+ notes also triggers the backup reminder).
for t in "Q4 planning workshop agenda" "Supplier comparison for three vendors" "Weekly review wins and next steps"; do
  if tap "Capture a thought…"; then type_text "$t"; tap "Save quick note" || true; fi
done
adb shell input keyevent KEYCODE_ESCAPE 2>/dev/null; adb shell input keyevent KEYCODE_BACK; sleep 1
adb shell am start -W -n "$PKG/app.margin.MainActivity" >/dev/null
shot 04-home-with-notes 3

tap "Open library" && { shot 05-library-menu 2; adb shell input keyevent KEYCODE_BACK; sleep 1; }

if tap "New note"; then
  sleep 2; type_text "Board briefing"; adb shell input keyevent KEYCODE_TAB
  type_text "Key decisions and owners"; shot 06-editor 2
  adb shell input keyevent KEYCODE_BACK; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 1
fi

adb shell cmd uimode night yes; shot 07-home-dark 4; adb shell cmd uimode night no; sleep 2

tap "Settings" && shot 08-settings 2
log "done"
