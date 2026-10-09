#!/usr/bin/env python3
"""Checks that every R.<type>.<name> referenced from Kotlin exists as a resource, and that the
manifest's component classes exist as Kotlin classes. A cheap guard while the Android build
can't run in this environment."""
import glob, re, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parent.parent
APP = ROOT / "app/src/main"
src = {f: open(f, encoding="utf-8").read() for f in glob.glob(str(APP / "kotlin/**/*.kt"), recursive=True)}
alltext = "".join(src.values())
ok = True
files = {p.stem for p in (APP / "res").rglob("*") if p.is_file()}
ids = set(re.findall(r'@\+id/([a-zA-Z0-9_]+)', "".join(open(p, encoding="utf-8").read() for p in (APP / "res").rglob("*.xml"))))
colors = set(re.findall(r'<color name="([a-z0-9_]+)"', "".join(open(p, encoding="utf-8").read() for p in (APP / "res").rglob("*.xml"))))
for kind, name in sorted(set(re.findall(r"\bR\.(drawable|layout|xml|mipmap|id|color|raw)\.([a-zA-Z0-9_]+)", alltext))):
    exists = name in ids if kind == "id" else name in colors if kind == "color" else name in files
    if not exists:
        print(f"MISSING R.{kind}.{name}"); ok = False
for ref in set(re.findall(r'@(drawable|layout|xml|mipmap|color)/([a-z0-9_]+)', "".join(open(p, encoding="utf-8").read() for p in list((APP / "res").rglob("*.xml")) + [APP / "AndroidManifest.xml"]))):
    kind, name = ref
    if (kind == "color" and name not in colors) or (kind != "color" and name not in files):
        print(f"MISSING @{kind}/{name}"); ok = False
manifest = open(APP / "AndroidManifest.xml", encoding="utf-8").read()
for cls in re.findall(r'android:name="(\.[A-Za-z0-9_.]+)"', manifest):
    simple = cls.rsplit(".", 1)[-1]
    if not re.search(r"class\s+" + simple + r"\b", alltext):
        print(f"MISSING manifest class {cls}"); ok = False
print("resources OK" if ok else "resource problems found")
sys.exit(0 if ok else 1)
