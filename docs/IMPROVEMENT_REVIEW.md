# KAR Notes: product and delivery review

*Senior-consultant review of the Android tablet note-taking app, October 2026*

## 1. Executive summary

KAR Notes has an unusually strong foundation for its stage. The data, search, template, task, backup and AI rules sit in a tested core: 149 automated tests, plus 91 database queries checked against SQLite. Privacy choices are deliberate: data stays on the tablet, there's no account, and AI is opt-in.

The main risk is not features. It is **delivery and data protection**:

1. **The Android app has never been compiled.** Until the first CI build passes, every screen is unproven. This is the single largest risk to go-live.
2. **"Tablet-only" also means a single point of failure.** If a tablet is lost, broken or reset, every note is gone unless the user made a backup file. Before this review, nothing prompted users to back up.
3. **AI now sends text to OpenAI.** That is acceptable with consent, but the business needs evidence of what was sent, not just a policy statement.

This release addresses all three. It adds a **backup-health reminder**, an **OpenAI transparency log**, and an **automated UI walkthrough in CI** that produces real screenshots as evidence. It also delivers the requested **3D glossy visual identity** and a faster Home screen.

## 2. Assessment scorecard

| Dimension | Before | After this release | Comment |
|---|---|---|---|
| Delivery readiness | 🔴 Red | 🟠 Amber | CI builds, tests and screenshots on every push. Becomes green after the first passing run. |
| Data protection and continuity | 🟠 Amber | 🟢 Green | Verified backups, plus a Home reminder when there is no recent backup |
| Privacy and AI governance | 🟠 Amber | 🟢 Green | Consent through the hidden setup, an encrypted key, a per-request log and one-tap disconnect |
| Usability and time-to-value | 🟠 Amber | 🟢 Green | Six one-tap quick actions; tasks completed from Home with Undo |
| Visual identity | 🟠 Amber | 🟢 Green | Glossy KAR Group brand system; flat mode kept for accessibility |
| Functional breadth | 🟢 Green | 🟢 Green | See `FEATURE_COVERAGE.md`. Gaps: PDF text, semantic search, translations |
| Quality evidence | 🟠 Amber | 🟠 Amber | Strong core tests; the app's on-device tests haven't run yet |

## 3. Key findings and recommendations

| # | Finding | Recommendation | Impact | Effort | Status |
|---|---|---|---|---|---|
| R1 | The app has never been compiled; no on-device evidence exists | CI pipeline: build, unit tests, lint, emulator walkthrough with screenshots | Very high | Low | **Done** (`tools/ci-ui-tour.sh`). Needs the first run. |
| R2 | Device-only storage with no backup prompting | Track the last verified backup. A Home banner appears when there are 3+ notes and no backup within 14 days; it can be snoozed for 3 days, the interval set to 7/14/30 days, or switched off. | Very high | Low | **Done** (`BackupHealth`, tested) |
| R3 | No audit trail for cloud AI | Count only requests that actually reached OpenAI, with a timestamp, shown in Privacy | High | Low | **Done** (`AiAudit`, tested) |
| R4 | Home took several taps to start common work | Quick actions: New note, Checklist, Meeting notes, Daily journal, Ask my notes, Templates | High | Low | **Done** |
| R5 | Completing a task meant leaving Home | Tick tasks off on Home, with a snackbar Undo | Medium | Low | **Done** |
| R6 | The brand felt flat and generic | Glossy design system: depth, gradient, sheen and bevel; glossy logo and icon; text kept at WCAG AA | Medium | Medium | **Done** (`ui/theme/Gloss.kt`) |
| R7 | A visual style must not cost accessibility | User switch Settings → Appearance → *3D glossy style*; every gradient tested at ≥ 4.5:1 | Medium | Low | **Done** |
| R8 | Exports carried the old product name | Backup and export files now named `kar-notes-…` | Low | Low | **Done** |
| R9 | PDFs aren't searchable | Extract PDF text on the device (render the pages, then run the on-device text reader) | Medium | Medium | Recommended next |
| R10 | Only keyword search | Semantic search with OpenAI embeddings: opt-in, with the same consent and log | Medium | Medium | Recommended |
| R11 | No AI cost visibility | Show the token usage OpenAI reports, as a monthly estimate | Medium | Low | Recommended |
| R12 | Device-loss exposure remains for users who never back up | Optional automatic backup to a user-chosen folder or SD card, run by the system on a schedule (WorkManager) | High | Medium | Recommended |
| R13 | English only | Translation pipeline; RTL is already enabled | Medium | Medium | Recommended if rolled out regionally |
| R14 | Enterprise deployment | Managed-configuration settings (for example, disable OpenAI or require the app lock) for KAR Group devices under mobile device management | Medium | Medium | Optional |

## 4. Roadmap

| Horizon | Scope | Exit criterion |
|---|---|---|
| **Next 30 days** | Push to GitHub. Fix the first-compile issues. Review the CI screenshots. Pilot with 3–5 users on real tablets. | Green CI run. No data-loss defects in the pilot. |
| **31–60 days** | R12 automatic backups, R9 PDF text, R11 AI cost display, TalkBack pass on a tablet | Every pilot user has a backup less than 14 days old |
| **61–90 days** | R10 semantic search, R13 translations, R14 device-management settings, signed release build | Release candidate signed, plus a checklist for the Play Store or internal distribution |

## 5. Success measures (KPIs)

All measures are local and never transmitted. They can be read from Settings or gathered in pilot interviews.

| KPI | Target |
|---|---|
| Time from opening the app to the first saved note | < 5 seconds (quick capture or quick action) |
| Pilot users with a backup less than 14 days old | ≥ 90% |
| Crashes or lost-note incidents in the pilot | 0 |
| AI requests visible in the transparency log versus used | 100% reconciled |
| Accessibility: text contrast on every surface | ≥ 4.5:1 (met by design) |

## 6. Residual risks

- **Unverified Android build** until CI is green. Mitigation: the CI walkthrough, then fix-forward from the logs.
- **OpenAI terms and model availability can change.** Mitigation: the model is editable in the app, and errors show OpenAI's own message.
- **Hidden setup is obscurity, not security.** Anyone with the tablet and the knowledge can open it. Mitigation: turn on the app lock (Settings → Privacy) on shared tablets. The key itself is encrypted and never displayed.
