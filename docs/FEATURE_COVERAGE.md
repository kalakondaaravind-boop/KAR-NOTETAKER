# Feature coverage against the master specification

Audited 10 Oct 2026 against the specification sections (§) the app was built from.

**What the statuses mean**

| Status | Meaning |
|---|---|
| **VERIFIED** | The logic lives in `core/domain` (or is SQL) and is covered by passing automated tests: 149 JVM tests, plus 91 SQL statements run against SQLite. |
| **IMPLEMENTED** | UI and wiring exist in the Android modules but haven't been compiled or run yet. The first GitHub Actions build is the first compile. |
| **PARTIAL** | Part of the requirement is present. The gap is stated. |
| **DEFERRED** | Not built. Planned for later. |
| **EXCLUDED** | Deliberately not built, because of your instructions or the spec's exclusions. |

Nothing in the Android UI can be **VERIFIED** until the app compiles and runs. The GitHub workflow's emulator job is the first step towards that.

## Summary

| Area | Status |
|---|---|
| Notes and editor (§8) | VERIFIED logic, IMPLEMENTED UI |
| Autosave and draft protection (§8.4) | VERIFIED (debounce, failure, retry, storage full, flush) |
| Revision history (§8.5) | VERIFIED (retention policy, line diff), IMPLEMENTED UI |
| Capture (§9) | IMPLEMENTED, with PDF text extraction PARTIAL |
| Organization: folders, tags, smart collections (§10) | VERIFIED rules and SQL |
| Backlinks and related notes (§10.4) | VERIFIED, with the visual graph PARTIAL (text views only) |
| Search (§11.1–11.2) | VERIFIED (parser, FTS, fuzzy, 10k-note timing) |
| Semantic search (§11.3) | DEFERRED |
| Templates (§12) | VERIFIED: 525 templates cover all 674 catalogue entries |
| Tasks and reminders (§13) | VERIFIED (recurrence, buckets, DST), IMPLEMENTED alarms |
| AI (§14) | VERIFIED policy, grounding, providers and errors; IMPLEMENTED UI, including the hidden OpenAI setup |
| Data: database, backup, import, export (§15–16) | VERIFIED codecs, archive, encryption, SQL |
| Private sync (§16.5) | EXCLUDED: you asked for no online storage |
| Security and privacy (§17) | VERIFIED rules, IMPLEMENTED lock |
| Accessibility and i18n (§18) | PARTIAL: English only, and screen-reader behaviour hasn't been tested on a device |
| Performance (§19) | PARTIAL: search measured on the JVM only, nothing measured on a device |
| Settings (§20) | IMPLEMENTED |
| Platform integration (§21) | IMPLEMENTED |
| Tests (§22) | PARTIAL: JVM and SQL tests run; the instrumented tests are written but have never run |
| Release (§23) | PARTIAL: the CI workflow builds the APK and computes its checksum, but nothing has been built yet |

## Detail

### §6 Navigation, onboarding, design
| Item | Status | Where |
|---|---|---|
| Home, All notes, Folders, Tags, Pinned/Favorites, Tasks, Templates, Search, Attachments, Trash/Archive, Settings, AI/Knowledge | IMPLEMENTED | `ui/navigation/AppShell.kt`: a rail on tablets, a bottom bar on phones, and a library menu |
| Onboarding: storage, AI choice, backup explanation, skip | IMPLEMENTED | `feature/onboarding` |
| Optional app lock during setup | PARTIAL | Lives in Settings → Privacy, not in onboarding |
| Light/dark themes, Sky & Pastel Blue, contrast ≥ 4.5:1, dynamic colour toggle | IMPLEMENTED | `ui/theme/Theme.kt` |
| KAR Group logo and "Note taker" tagline (glossy) | IMPLEMENTED | `ui/components/Brand.kt`, `res/drawable/kar_logo_mark.xml` |
| 3D glossy design layer with a flat fallback (Settings → Appearance) | IMPLEMENTED (contrast VERIFIED by calculation) | `ui/theme/Gloss.kt`, `docs/DESIGN.md` |

### §7 Home
| Item | Status |
|---|---|
| Quick capture, pinned, recent, favorites, upcoming tasks, folders, recent templates | IMPLEMENTED |
| Show, hide and reorder sections | IMPLEMENTED (Settings → Home screen) |
| Quick actions: new note, checklist, meeting notes, daily journal, Ask my notes, templates | IMPLEMENTED |
| Complete tasks from Home, with Undo | IMPLEMENTED |
| Backup reminder (3+ notes, no backup in 7/14/30 days, snooze, off) | VERIFIED rule (`BackupHealth`), IMPLEMENTED banner |
| Contextual suggestions | DEFERRED |

### §8 Notes and editor
| Item | Status | Where |
|---|---|---|
| Create/edit/delete, rename, pin, favorite, archive, duplicate, move, tags | IMPLEMENTED (repository rules VERIFIED) | `EditorViewModel`, `NoteRepositoryImpl` |
| Trash, restore, permanent delete with confirmation, trash retention | IMPLEMENTED (SQL VERIFIED) | `NotesList`, `MaintenanceWorker` |
| Multi-select and bulk actions | IMPLEMENTED | `NotesList` |
| Save a note as a template; note details; share/export | IMPLEMENTED | `EditorScreen` |
| Markdown: headings, bold, italic, underline, strike, lists, checklists, indent, quote, divider, link, code, inline code, table, image, date/time | VERIFIED (parser, commands), IMPLEMENTED toolbar | `domain/text`, `domain/editor` |
| Undo/redo, find and replace, word count, reading time, list continuation, auto-link | VERIFIED | `UndoStack`, `FindReplace`, `TextStats`, `EditorCommands.onTextChanged` |
| Focus mode, text size, monospace, Markdown preview, hardware keyboard shortcuts | IMPLEMENTED | `EditorScreen` |
| Autosave pipeline and process-death recovery | VERIFIED (state machine), IMPLEMENTED (lifecycle flush) | `AutosaveController` |
| Revisions: timestamps, restore, compare, retention | VERIFIED (policy, diff), IMPLEMENTED (UI) | `Revisions.kt` |
| Rich-text clipboard (HTML paste) | DEFERRED (plain text only) | |

### §9 Capture
| Item | Status |
|---|---|
| Text, quick note, checklist, share sheet (text, URLs, images, PDF, audio), file attach | IMPLEMENTED |
| Camera (through the system camera app), voice recording, speech-to-text (system recogniser) | IMPLEMENTED |
| OCR from images on the device (ML Kit), original image kept | IMPLEMENTED |
| PDF import | PARTIAL: stored as an attachment, but its text isn't extracted or searchable |
| Clipboard capture | IMPLEMENTED: optional paste button on Home that reads the clipboard only on tap (added in this pass) |
| Quick-capture notification | IMPLEMENTED: optional, Settings → Capture (added in this pass) |
| App shortcuts, home-screen widget | IMPLEMENTED |
| Document scanner (edge detection) | DEFERRED: the camera plus OCR covers the basic case |

### §10 Organization and knowledge
| Item | Status |
|---|---|
| Nested folders with a depth limit and cycle prevention, colours, pinning, archive, safe delete | VERIFIED rules, IMPLEMENTED UI |
| One primary folder per note, plus any number of tags | VERIFIED (documented in the domain model) |
| Tags: case and whitespace normalisation, merge on rename, colours, unused-tag cleanup, bulk | VERIFIED |
| Smart collections (recent, untagged, attachments, links, tasks, long-form, not reviewed, and more) | VERIFIED: the SQL matches the Kotlin filter semantics |
| `[[wiki links]]`, backlinks, related notes, duplicates, orphans | VERIFIED |
| Visual knowledge graph | PARTIAL: text tabs (links, related, duplicates, orphans), which the spec accepts as the accessible alternative. No drawn graph. |

### §11 Search
| Item | Status |
|---|---|
| Titles, bodies, tags, folders, attachment names, OCR text | VERIFIED (FTS4 with accent folding) |
| Phrases, exclusion, OR, grouping, `title:` and `tag:` style fields, date ranges, task and attachment filters | VERIFIED |
| Snippets, highlighting, relevance, sorting, saved searches, history controls, syntax help | VERIFIED logic, IMPLEMENTED UI |
| Fuzzy matching, clearly labelled as fuzzy | VERIFIED |
| Semantic (embedding) search | DEFERRED: could use the OpenAI key next |

### §12 Templates
| Item | Status |
|---|---|
| Schema engine: field types, validation, sections, repeatables, conditionals, versioning, migration | VERIFIED |
| Built-in library: 525 templates covering all 674 catalogue entries (A–AF) | VERIFIED: every template generates a valid note (`BuiltInTemplatesTest`) |
| Custom templates, duplication, reset, template builder, JSON import/export | VERIFIED codecs, IMPLEMENTED UI |
| Per-template inventory | `docs/TEMPLATE_INVENTORY.md` |

### §13 Tasks
| Item | Status |
|---|---|
| Embedded checklist tasks and standalone tasks, due dates and times, priority, subtasks | VERIFIED |
| Today, upcoming and overdue views; history; search and filter | VERIFIED logic, IMPLEMENTED UI |
| Recurrence (RRULE subset), DST and time-zone handling | VERIFIED |
| Exact alarms with fallback, reboot and time-change rescheduling, snooze, permission denial | IMPLEMENTED |

### §14 AI
| Item | Status |
|---|---|
| 34 actions: summarise, extract, rewrite, tone, grammar, translate, outline, study questions, flashcards, title/tag/folder suggestions, ask selected notes, ask the library, template from description, fill template, contradictions, agenda, transcript summary, action plan, and more | VERIFIED prompts and parsing, IMPLEMENTED UI |
| On-device assistant (no network) | VERIFIED |
| OpenAI with your own key | VERIFIED provider (request, `max_completion_tokens`, errors); IMPLEMENTED hidden setup: tap the logo **10 times** |
| Preview before insert; insert, replace, copy or discard; generated-text label; source citations; "not enough evidence" | VERIFIED parsing, IMPLEMENTED UI |
| Prompt-injection separation; AI can't delete or export | VERIFIED |
| Per-note "exclude from AI", archived and trashed notes excluded | VERIFIED |
| Log of requests sent to OpenAI, with the last-sent time (Settings → Privacy) | VERIFIED rule (`AiAudit`), IMPLEMENTED UI |
| Usage and cost display | DEFERRED |

### §15–16 Data
| Item | Status |
|---|---|
| Room schema (v1), transactions, cascades, indexes | VERIFIED (SQL), with the migration test scaffolded for future versions |
| Export: Markdown, HTML, plain text, JSON, ZIP with attachments, templates | VERIFIED |
| Import: Markdown, text, HTML, the app's own JSON; size, encoding and duplicate checks; reports skipped and failed items | VERIFIED |
| Backup archive with an integrity manifest, optional passphrase (AES-GCM, PBKDF2) | VERIFIED: a backup restores into a clean library in tests |
| Private sync | EXCLUDED |

### §17 Security and privacy
| Item | Status |
|---|---|
| No secrets in source (scanner test), no note text in logs, notification previews off by default | VERIFIED |
| API key stored with Android Keystore encryption, never displayed | IMPLEMENTED |
| App lock (biometric or device PIN), timeout, hidden from recents and screenshots | IMPLEMENTED |
| Android cloud backup and device transfer disabled | IMPLEMENTED |
| Network | INTERNET is used **only** for OpenAI AI actions you run. Nothing is stored online. |

### §18–23
| Item | Status |
|---|---|
| All strings in resources; RTL enabled | IMPLEMENTED |
| Translations | DEFERRED: English only |
| TalkBack labels, headings, merged rows | IMPLEMENTED, not yet tested on a device |
| Device performance measurements | DEFERRED until the app runs |
| Release signing through a git-ignored `keystore.properties` | IMPLEMENTED |

## Highest-impact remaining work
1. **First compile on GitHub**, then fix whatever it reports. This blocks everything else.
2. Run the instrumented tests and the critical regression flows (§22.4) on the emulator.
3. Extract PDF text so PDFs become searchable.
4. Semantic search using OpenAI embeddings. This is optional and would send note text to OpenAI only after consent.
5. Translations, and a TalkBack pass on a real tablet.
