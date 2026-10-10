# KAR Notes — Note taker by KAR Group

A private note-taking app for Android tablets (it also adapts to phones and foldables). Kotlin, Jetpack Compose, Material 3, in a Sky & Pastel Blue theme.

**All data stays on the tablet.** There is no account, no online storage, no sync and no analytics.

![Tablet preview](docs/preview/kar-notes-preview-sheet.png)

*The preview above is rendered from the app's layout, colours and text. Real emulator screenshots come from the GitHub build (see below).*

## Where your data lives

| What | Where | Leaves the tablet? |
|---|---|---|
| Notes, folders, tags, tasks, templates, history | Room/SQLite database in the app's private storage | No |
| Attachments (photos, PDFs, voice notes) | App-private `files/attachments` | No |
| Settings | DataStore in app-private storage | No |
| Backups | A file **you** save with the Android file picker (Downloads, SD card, USB drive) | Only if you move it yourself |

These guarantees are enforced in the build, not just promised:

- **No internet permission.** `AndroidManifest.xml` removes `INTERNET` and `ACCESS_NETWORK_STATE` with `tools:node="remove"`. That includes any a library would try to merge in. Android itself blocks every network connection the app might attempt.
- **No Android cloud backup or device transfer.** `allowBackup="false"`, and both `data_extraction_rules.xml` and `backup_rules.xml` exclude every data domain.
- **No cloud AI.** The optional assistant runs on the tablet only. The cloud provider options were removed from Settings, and any old cloud setting reads back as "off".
- **OCR on the tablet.** ML Kit text recognition uses the bundled on-device model.
- Sharing a note out (Share → another app) only ever happens when you tap it, and sends a copy of that one note.

To check on a device, run `adb shell dumpsys package com.kargroup.notetaker | grep permission`. `android.permission.INTERNET` must not appear. The GitHub build saves this list as `granted-permissions.txt`.

## Put it on GitHub and get the APK

1. Create an empty repository on GitHub, for example `kar-notes`, with no README and no .gitignore.
2. From this folder, run:
   ```bash
   git remote add origin https://github.com/<your-account>/kar-notes.git
   git push -u origin main
   ```
3. Open the repository's **Actions** tab. The **Android build** workflow runs automatically, in two parts:
   - **build** runs the domain unit tests, builds the debug APK, and records its SHA-256 checksum. The download is the artifact **kar-notes-debug-apk**.
   - **tablet** installs the APK on a Pixel Tablet emulator with Wi-Fi and data switched off and screenshots the app in light and dark mode. It also lists the permissions Android granted and runs the instrumented tests. The download is the artifact **tablet-screenshots**.
4. To install on a tablet, copy the APK across, open it and allow "install unknown apps" for your file manager. Or use `adb install kar-notes-*.apk`.

## Build locally (Android Studio)

Open the folder in Android Studio (Ladybug or newer), let Gradle sync, then run the **app** configuration on a tablet or emulator. You'll need JDK 17 and Android SDK 35; Android Studio installs the SDK for you.

```bash
./gradlew :core:domain:test          # product rules, search, templates, backup, AI policy (JVM)
./gradlew :app:assembleDebug         # debug APK → app/build/outputs/apk/debug/
./gradlew :app:lintDebug             # lint
./gradlew connectedDebugAndroidTest  # instrumented tests (needs a device/emulator)
./gradlew :app:assembleRelease       # release APK (needs your signing key, below)
```

**Release signing.** Create `keystore.properties` in the project root. It is git-ignored, so it never reaches GitHub. Fill in `storeFile`, `storePassword`, `keyAlias` and `keyPassword`. Alternatively, set the environment variables `MARGIN_KEYSTORE`, `MARGIN_KEYSTORE_PASSWORD`, `MARGIN_KEY_ALIAS` and `MARGIN_KEY_PASSWORD`. Never commit a keystore.

## Branding

| Item | File |
|---|---|
| Logo mark (vector, used in-app) | `app/src/main/res/drawable/kar_logo_mark.xml` |
| Logo + wordmark + tagline (in-app component) | `app/src/main/kotlin/app/margin/ui/components/Brand.kt` |
| Logo source artwork | `docs/brand/kar-group-mark.svg`, `docs/brand/kar-group-logo.svg` |
| Launcher icon | `res/drawable/ic_launcher_foreground.xml` on `@color/brand` |
| Colours | `ui/theme/Theme.kt` (app) and `res/values/colors.xml` (window, icon, widget) |
| App name and tagline | `tools/strings_source.py` (`app_name`, `brand_tagline`), then run `python3 tools/gen-strings.py` |

The logo and tagline appear on the first-launch screen, in the home screen's top bar and at the top of the library menu.

**Palette (Sky & Pastel Blue).** Sky blue `#2A76AD` is the primary colour, pastel blue `#CDE7FA` the containers, and sky mist `#F4F9FE` the background. Periwinkle `#5C6BB0` is used for pins and stars. Dark mode uses `#9DD0F7` on `#0E1822`. Every text and background pair meets WCAG AA contrast (4.5:1 or better). Wallpaper-based "dynamic colour" is off by default so the brand colours always show; it can be switched on in Settings → Appearance.

## Project layout

```
core/domain   Pure Kotlin: models, editor commands, autosave, search, templates (525 built-in),
              tasks/recurrence, import/export/backup, on-device assistant, security rules
core/data     Room database + FTS search index, repositories, DataStore settings, Keystore
app           Compose UI: home, notes, editor, search, folders/tags, templates, tasks/reminders,
              attachments/capture, knowledge, settings, backup/restore, app lock, widget
tools         Offline verification scripts (domain tests, SQL against SQLite, strings/resources)
```

## Verification status

What has been verified so far:
- **Domain module:** compiles, and all 137 unit tests pass (`tools/verify-domain.sh`).
- **SQL:** all 91 Room queries run against real SQLite 3.45, including the search index and cascade behaviour (`tools/verify-sql.py`).
- **Resources:** every string and resource reference resolves (`tools/check-resources.py`).

**Not yet verified:** the Android modules (`app`, `core/data`) have not been compiled. The machine that wrote them had no access to Google's Maven repository or the Android SDK. The first GitHub Actions run is the first real Android compile. If it reports errors, open the failed step's log and fix them, or ask Claude to fix them from that log. Treat the app as unreleased until the **build** and **tablet** jobs are green and you have tried the screenshots and APK yourself.
