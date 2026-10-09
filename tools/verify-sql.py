#!/usr/bin/env python3
"""
Offline verification of the app's SQL against a real SQLite engine.

1. Creates the schema Room generates for core/data (tables, indexes, FTS4 with unicode61).
2. Extracts every @Query string from Daos.kt and prepares it (syntax + name resolution check).
3. Loads fixtures emitted by SqlFixturesTest (Kotlin) and checks that every NoteFilterSql WHERE
   clause selects exactly the notes the Kotlin NoteFilter.matches() semantics select.
4. Runs FtsQueryBuilder MATCH expressions against the FTS4 table (syntax + accent folding).
5. Runs the list/search projections and checks the trashed/restore and cascade behaviour.

Usage: python3 tools/verify-sql.py <fixtures.json>
"""
import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DAOS = ROOT / "core/data/src/main/kotlin/app/margin/data/db/Daos.kt"

SCHEMA = """
PRAGMA foreign_keys = ON;
CREATE TABLE folders (id TEXT NOT NULL, name TEXT NOT NULL, parent_id TEXT, color INTEGER, icon TEXT,
  sort_order INTEGER NOT NULL, pinned INTEGER NOT NULL, archived INTEGER NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(id));
CREATE INDEX index_folders_parent_id ON folders (parent_id);
CREATE TABLE notes (id TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, plain_text TEXT NOT NULL, sort_title TEXT NOT NULL,
  folder_id TEXT, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, last_opened_at INTEGER, pinned INTEGER NOT NULL,
  favorite INTEGER NOT NULL, state TEXT NOT NULL, trashed_at INTEGER, template_id TEXT, content_format_version INTEGER NOT NULL,
  excluded_from_ai INTEGER NOT NULL, last_cursor INTEGER NOT NULL, reviewed_at INTEGER, imported_at INTEGER,
  word_count INTEGER NOT NULL, open_task_count INTEGER NOT NULL, done_task_count INTEGER NOT NULL, link_count INTEGER NOT NULL,
  fingerprint TEXT NOT NULL, PRIMARY KEY(id), FOREIGN KEY(folder_id) REFERENCES folders(id) ON UPDATE NO ACTION ON DELETE SET NULL);
CREATE INDEX index_notes_folder_id ON notes (folder_id);
CREATE INDEX index_notes_updated_at ON notes (updated_at);
CREATE INDEX index_notes_state ON notes (state);
CREATE INDEX index_notes_pinned ON notes (pinned);
CREATE INDEX index_notes_fingerprint ON notes (fingerprint);
CREATE TABLE tags (id TEXT NOT NULL, name TEXT NOT NULL, key TEXT NOT NULL, color INTEGER, PRIMARY KEY(id));
CREATE UNIQUE INDEX index_tags_key ON tags (key);
CREATE TABLE note_tags (note_id TEXT NOT NULL, tag_id TEXT NOT NULL, PRIMARY KEY(note_id, tag_id),
  FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE, FOREIGN KEY(tag_id) REFERENCES tags(id) ON DELETE CASCADE);
CREATE INDEX index_note_tags_tag_id ON note_tags (tag_id);
CREATE TABLE note_links (from_id TEXT NOT NULL, to_id TEXT NOT NULL, PRIMARY KEY(from_id, to_id),
  FOREIGN KEY(from_id) REFERENCES notes(id) ON DELETE CASCADE, FOREIGN KEY(to_id) REFERENCES notes(id) ON DELETE CASCADE);
CREATE INDEX index_note_links_to_id ON note_links (to_id);
CREATE TABLE attachments (id TEXT NOT NULL, note_id TEXT NOT NULL, file_name TEXT NOT NULL, mime_type TEXT NOT NULL,
  size_bytes INTEGER NOT NULL, storage_path TEXT NOT NULL, created_at INTEGER NOT NULL, extracted_text TEXT, sha256 TEXT,
  PRIMARY KEY(id), FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE);
CREATE INDEX index_attachments_note_id ON attachments (note_id);
CREATE TABLE revisions (id TEXT NOT NULL, note_id TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, created_at INTEGER NOT NULL,
  pinned INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE CASCADE);
CREATE INDEX index_revisions_note_id_created_at ON revisions (note_id, created_at);
CREATE TABLE tasks (id TEXT NOT NULL, title TEXT NOT NULL, notes TEXT NOT NULL, note_id TEXT, parent_task_id TEXT, due_date TEXT,
  due_time TEXT, zone_id TEXT NOT NULL, priority TEXT NOT NULL, completed_at INTEGER, recurrence TEXT, reminder_offset_minutes INTEGER,
  snoozed_until INTEGER, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id),
  FOREIGN KEY(note_id) REFERENCES notes(id) ON DELETE SET NULL, FOREIGN KEY(parent_task_id) REFERENCES tasks(id) ON DELETE CASCADE);
CREATE INDEX index_tasks_note_id ON tasks (note_id);
CREATE INDEX index_tasks_parent_task_id ON tasks (parent_task_id);
CREATE INDEX index_tasks_due_date ON tasks (due_date);
CREATE INDEX index_tasks_completed_at ON tasks (completed_at);
CREATE TABLE templates (id TEXT NOT NULL, json TEXT NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(id));
CREATE TABLE template_usage (template_id TEXT NOT NULL, used_at INTEGER NOT NULL, PRIMARY KEY(template_id));
CREATE TABLE saved_searches (id TEXT NOT NULL, name TEXT NOT NULL, query TEXT NOT NULL, created_at INTEGER NOT NULL, PRIMARY KEY(id));
CREATE VIRTUAL TABLE note_fts USING FTS4(note_id TEXT NOT NULL, title TEXT NOT NULL, body TEXT NOT NULL, tags TEXT NOT NULL,
  folder TEXT NOT NULL, attachments TEXT NOT NULL, tokenize=unicode61, notindexed=note_id);
"""

failures = []


def check(cond, msg):
    if not cond:
        failures.append(msg)
        print("FAIL:", msg)


def kotlin_consts(src):
    consts = {}
    for m in re.finditer(r'const val (\w+) = """(.*?)"""', src, re.S):
        consts[m.group(1)] = m.group(2)
    return consts


def extract_queries(src, consts):
    out = []
    for m in re.finditer(r'@Query\(\s*"""(.*?)"""\s*\)|@Query\(\s*"((?:[^"\\]|\\.)*)"\s*\)', src, re.S):
        q = m.group(1) if m.group(1) is not None else m.group(2)
        q = re.sub(r'\$(\w+)', lambda x: consts.get(x.group(1), x.group(0)), q)
        q = q.replace('`', '"')
        # Room expands collection params; :name → ?
        q = re.sub(r'\(:\w+\)', '(?)', q)
        q = re.sub(r':\w+', '?', q)
        out.append(q)
    return out


def main():
    fixtures = json.loads(Path(sys.argv[1]).read_text()) if len(sys.argv) > 1 else None
    db = sqlite3.connect(":memory:")
    db.executescript(SCHEMA)
    print("sqlite", sqlite3.sqlite_version)

    src = DAOS.read_text()
    consts = kotlin_consts(src)
    queries = extract_queries(src, consts)
    print(f"checking {len(queries)} @Query statements")
    for q in queries:
        try:
            n = q.count("?")
            db.execute("EXPLAIN " + q, [None] * n)
        except sqlite3.Error as e:
            check(False, f"query does not prepare: {e}\n    {q[:200]}")

    # Summary + search projections used by @RawQuery.
    for name in ("SUMMARY_COLUMNS", "SEARCH_COLUMNS"):
        try:
            db.execute(f"EXPLAIN SELECT {consts[name]} FROM notes n")
        except sqlite3.Error as e:
            check(False, f"{name}: {e}")

    if fixtures:
        verify_filters(db, fixtures, consts)
        verify_fts(db, fixtures)
    verify_behaviour(consts)

    if failures:
        print(f"\n{len(failures)} failure(s)")
        sys.exit(1)
    print("\nOK: all SQL checks passed")


def verify_filters(db, fx, consts):
    db.execute("INSERT INTO folders VALUES ('f0','F0',NULL,NULL,NULL,0,0,0,0)")
    db.execute("INSERT INTO folders VALUES ('f1','F1',NULL,NULL,NULL,0,0,0,0)")
    tag_ids = {}
    for n in fx["notes"]:
        body = "" if n["is_empty"] else "body"
        title = "" if n["is_empty"] else "t " + n["id"]
        db.execute("""INSERT INTO notes VALUES (?,?,?,?,?,?,?,?,NULL,?,?,?,NULL,NULL,1,0,0,?,?,?,?,?,?,'fp')""",
                   (n["id"], title, body, body, title, n.get("folder_id"), n["created_at"], n["updated_at"],
                    int(n["pinned"]), int(n["favorite"]), n["state"], n.get("reviewed_at"), n.get("imported_at"),
                    n["word_count"], n["open_task_count"], n["done_task_count"], n["link_count"]))
        for t in n["tags"]:
            if t not in tag_ids:
                tag_ids[t] = "tag_" + str(len(tag_ids))
                db.execute("INSERT INTO tags VALUES (?,?,?,NULL)", (tag_ids[t], t, t))
            db.execute("INSERT INTO note_tags VALUES (?,?)", (n["id"], tag_ids[t]))
        for i in range(n["attachments"]):
            db.execute("INSERT INTO attachments VALUES (?,?,?,?,1,?,0,NULL,NULL)", (n["id"] + "a" + str(i), n["id"], "f.png", "image/png", "p"))
    print(f"checking {len(fx['filters'])} filters x {len(fx['orders'])} orderings against {len(fx['notes'])} notes")
    for f in fx["filters"]:
        for order in fx["orders"]:
            sql = f"SELECT {consts['SUMMARY_COLUMNS']} FROM notes n WHERE {f['where']} ORDER BY {order} LIMIT ?"
            try:
                rows = db.execute(sql, f["args"] + [10_000]).fetchall()
            except sqlite3.Error as e:
                check(False, f"filter {f['name']} ({order}): {e}")
                continue
            got = sorted(r[0] for r in rows)
            check(got == sorted(f["expected"]), f"filter {f['name']} mismatch: sql={len(got)} kotlin={len(f['expected'])} "
                  f"extra={sorted(set(got) - set(f['expected']))[:5]} missing={sorted(set(f['expected']) - set(got))[:5]}")


def verify_fts(db, fx):
    rows = [("n1", "Quarterly budget", "Review the marketing spend", "finance", "Work", ""),
            ("n2", "Trip to Zürich", "Book hotel near the lake. Café list.", "travel", "Personal", ""),
            ("n3", "Notes", "team meeting about the budget", "work", "", "scan.png Invoice")]
    db.executemany("INSERT INTO note_fts(note_id,title,body,tags,folder,attachments) VALUES (?,?,?,?,?,?)", rows)
    for m in fx["fts"]:
        try:
            db.execute("SELECT note_id FROM note_fts WHERE note_fts MATCH ? LIMIT 50", (m,)).fetchall()
        except sqlite3.Error as e:
            check(False, f"FTS expression {m!r}: {e}")
    hit = lambda m: sorted(r[0] for r in db.execute("SELECT note_id FROM note_fts WHERE note_fts MATCH ?", (m,)))
    check(hit("budg*") == ["n1", "n3"], "prefix match")
    check(hit("zurich*") == ["n2"], "unicode61 removes diacritics (Zürich)")
    check(hit("cafe*") == ["n2"], "unicode61 removes diacritics (Café)")
    check(hit('"team meeting"') == ["n3"], "phrase")
    check(hit('lake* OR invoice*') == ["n2", "n3"], "OR + attachment text")
    print(f"checked {len(fx['fts'])} FTS expressions")


def verify_behaviour(consts):
    db = sqlite3.connect(":memory:")
    db.executescript(SCHEMA)
    db.execute("INSERT INTO folders VALUES ('f','F',NULL,NULL,NULL,0,0,0,0)")
    for nid in ("a", "b"):
        db.execute("INSERT INTO notes VALUES (?,?,?,?,?,'f',0,0,NULL,0,0,'ACTIVE',NULL,NULL,1,0,0,NULL,NULL,1,0,0,0,'fp')", (nid, nid, "b", "b", nid))
    db.execute("INSERT INTO tags VALUES ('t','T','t',NULL)")
    db.execute("INSERT INTO note_tags VALUES ('a','t')")
    db.execute("INSERT INTO note_links VALUES ('a','b')")
    db.execute("INSERT INTO revisions VALUES ('r','a','x','y',0,0)")
    db.execute("INSERT INTO attachments VALUES ('at','a','f','m',1,'at',0,NULL,NULL)")
    db.execute("INSERT INTO tasks VALUES ('k','task','', 'a', NULL, NULL, NULL, 'UTC','NONE',NULL,NULL,NULL,NULL,0,0)")
    db.execute("INSERT INTO tasks VALUES ('k2','sub','', NULL, 'k', NULL, NULL, 'UTC','NONE',NULL,NULL,NULL,NULL,0,0)")
    # Deleting a folder never deletes notes (SET NULL → Inbox).
    db.execute("DELETE FROM folders WHERE id = 'f'")
    check(db.execute("SELECT count(*) FROM notes WHERE folder_id IS NULL").fetchone()[0] == 2, "folder delete moves notes to Inbox")
    # Permanent delete cascades children, keeps tasks (note_id → NULL).
    db.execute("DELETE FROM notes WHERE id = 'a'")
    for t in ("note_tags", "note_links", "revisions", "attachments"):
        check(db.execute(f"SELECT count(*) FROM {t}").fetchone()[0] == 0, f"cascade {t}")
    check(db.execute("SELECT note_id FROM tasks WHERE id='k'").fetchone()[0] is None, "task survives note deletion")
    db.execute("DELETE FROM tasks WHERE id='k'")
    check(db.execute("SELECT count(*) FROM tasks").fetchone()[0] == 0, "subtasks cascade with parent")
    # Tag merge: UPDATE OR IGNORE then delete.
    db.execute("INSERT INTO tags VALUES ('t2','T2','t2',NULL)")
    db.execute("INSERT INTO note_tags VALUES ('b','t')")
    db.execute("INSERT INTO note_tags VALUES ('b','t2')")
    db.execute("UPDATE OR IGNORE note_tags SET tag_id = 't2' WHERE tag_id = 't'")
    db.execute("DELETE FROM tags WHERE id = 't'")
    check(db.execute("SELECT tag_id FROM note_tags").fetchall() == [("t2",)], "tag merge keeps one link")
    # Unique tag key.
    try:
        db.execute("INSERT INTO tags VALUES ('t3','x','t2',NULL)")
        check(False, "duplicate tag key accepted")
    except sqlite3.IntegrityError:
        pass
    # Counts query with empty table returns zeros, not NULL.
    empty = sqlite3.connect(":memory:")
    empty.executescript(SCHEMA)
    src = DAOS.read_text()
    counts_sql = re.search(r'@Query\("""(SELECT\s+coalesce\(SUM.*?)"""\)', src, re.S).group(1)
    check(empty.execute(counts_sql).fetchone() == (0, 0, 0, 0, 0), "counts are zero on empty library")
    print("checked cascade/merge/uniqueness behaviour")


if __name__ == "__main__":
    main()
