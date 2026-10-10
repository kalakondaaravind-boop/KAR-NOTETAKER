#!/usr/bin/env bash
# Walks through the installed debug app on an emulator (adb + uiautomator) and saves REAL screenshots.
# Used by .github/workflows/android.yml. Best-effort: a step that can't find its target is logged and skipped.
# Only elements belonging to the app's own package are matched (never the launcher or Android Settings).
#
# Usage: tools/ci-ui-tour.sh <output-dir>
set -u
PKG=com.kargroup.notetaker.debug
OUT="${1:-screenshots}"
mkdir -p "$OUT/ui-dumps"
LOG="$OUT/ui-tour.log"
log() { echo "$*" | tee -a "$LOG"; }
STEP=0

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png" && log "screenshot: $1"; }

launch() { adb shell am start -W -n "$PKG/app.margin.MainActivity" >/dev/null; sleep "${1:-3}"; }

# Prints "x y" of the first element of the app matching: text/desc equal to $1, or contains $1 when $2=contains,
# or class equal to $1 when $2=class. Each dump is kept in ui-dumps/ for debugging.
find_center() {
  # runs in a subshell ($(...)), so the step number comes from the files already saved
  local n; n=$(( $(ls "$OUT/ui-dumps" | wc -l) + 1 ))
  local f; f="$OUT/ui-dumps/$(printf %02d $n)-$(echo "$1" | tr -c 'A-Za-z0-9' '_' | cut -c1-24).xml"
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || log "uiautomator dump failed"
  adb exec-out cat /sdcard/ui.xml > "$f"
  python3 - "$1" "${2:-equals}" "$PKG" "$f" <<'PY'
import re, sys, xml.etree.ElementTree as ET
want, mode, pkg, path = sys.argv[1:5]
try: root = ET.parse(path).getroot()
except Exception: sys.exit(0)
for n in root.iter("node"):
    if n.get("package") != pkg: continue
    t, d, c = n.get("text") or "", n.get("content-desc") or "", n.get("class") or ""
    ok = (mode == "class" and c == want) or (mode == "contains" and (want in t or want in d)) or (mode == "equals" and want in (t, d))
    if ok:
        x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds", "")))
        print((x1 + x2) // 2, (y1 + y2) // 2); break
PY
}

tap() {
  local c; c="$(find_center "$1" "${2:-equals}")"
  if [ -n "$c" ]; then adb shell input tap $c; log "tap: $1"; sleep 1; return 0; fi
  log "NOT FOUND: $1"; return 1
}

type_text() { adb shell input text "$(echo "$1" | sed 's/ /%s/g')"; sleep 0.5; }

adb shell am force-stop "$PKG"
launch 8
shot 01-welcome 1

# Hidden OpenAI setup: 10 quick taps on the logo (merged element described as "KAR Group, Note taker").
c="$(find_center 'KAR Group' contains)"
if [ -n "$c" ]; then
  for _ in 1 2 3 4 5 6 7 8 9 10; do adb shell input tap $c; done
  log "tapped logo 10x at $c"
  shot 02-hidden-openai-setup 2
  tap "Cancel" || adb shell input keyevent KEYCODE_BACK
else
  log "NOT FOUND: logo"
fi

tap "Skip"
shot 03-home-empty 6
# Home is a scrolling list: make sure we start at the top.
scroll_top() { for _ in 1 2; do adb shell input swipe 1280 500 1280 1400 200; done; sleep 1; }
scroll_top

# Quick-capture notes so Home has content (3+ notes also shows the backup reminder).
for t in "Q4 planning workshop agenda" "Supplier comparison for three vendors" "Weekly review wins and next steps"; do
  if tap "android.widget.EditText" class; then type_text "$t"; tap "Save quick note" || true; fi
done
adb shell input keyevent KEYCODE_ESCAPE; sleep 1; scroll_top
shot 04-home-with-notes 3

if tap "Open library"; then shot 05-library-menu 2; adb shell input keyevent KEYCODE_BACK; sleep 2; fi
shot 05b-after-back-closes-menu 1
scroll_top

if tap "New note"; then
  sleep 3; type_text "Board briefing"; adb shell input keyevent KEYCODE_TAB
  type_text "Key decisions and owners"; shot 06-editor 2
  adb shell input keyevent KEYCODE_ESCAPE; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 2
fi
launch 2; scroll_top

# Drawing canvas: pen strokes, a shape, clipart, selection with cut/copy/paste.
if tap "Drawing"; then
  sleep 4
  c="$(find_center 'Drawing canvas' contains)"
  if [ -n "$c" ]; then
    set -- $c; cx=$1; cy=$2
    # a wave in pen (several connected strokes)
    x=$((cx-300)); y=$((cy-250))
    for d in 0 1 2 3 4 5; do
      nx=$((x+100)); if [ $((d % 2)) -eq 0 ]; then ny=$((y-80)); else ny=$((y+80)); fi
      adb shell input swipe $x $y $nx $ny 250; x=$nx; y=$ny
    done
    tap "Marker" && adb shell input swipe $((cx-300)) $((cy-80)) $((cx+300)) $((cy-80)) 400
    tap "Highlighter" && adb shell input swipe $((cx-300)) $((cy-150)) $((cx+200)) $((cy-150)) 400
    if tap "Shapes"; then tap "Ellipse"; adb shell input swipe $((cx-250)) $((cy+20)) $((cx-50)) $((cy+180)) 400; fi
    if tap "Clipart"; then sleep 2; shot 09-clipart-picker 1; tap "Idea"; fi
    shot 10-drawing-canvas 2
    if tap "Copy"; then tap "Paste"; shot 11-drawing-copy-paste 1; fi
    tap "Pen"
    if tap "More colours"; then shot 12-colour-picker 2; tap "Cancel"; fi
  else
    log "NOT FOUND: drawing canvas"
  fi
  adb shell input keyevent KEYCODE_BACK; sleep 3
fi
launch 2; scroll_top

# Page templates: a to-do page with check boxes printed on the paper.
if tap "New page"; then
  shot 13-page-templates 2
  if tap "To-do list"; then sleep 3; shot 14-todo-page 2
    if tap "Font"; then shot 15-font-menu 1; tap "Caveat"; sleep 1; shot 16-handwriting-font 1; fi
    adb shell input keyevent KEYCODE_ESCAPE; sleep 1; adb shell input keyevent KEYCODE_BACK; sleep 2
  fi
fi
launch 2

adb shell cmd uimode night yes; sleep 3; launch 3; shot 07-home-dark 1; adb shell cmd uimode night no; sleep 3; launch 3

tap "Settings" && shot 08-settings 2
log "done"
