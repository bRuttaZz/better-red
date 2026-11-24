# Better Red — notes for Claude

Better Red is a private, simple period tracker for Android. It is built mainly for one person, the
owner's girlfriend (her nickname is "Better", hence the name), and published as free software (GPL-3.0).
The owner is new to Android development: explain Android Studio steps plainly when they're needed.

- App ID `site.brutt.betterRed`, Kotlin package `site.brutt.betterred`
- Jetpack Compose + Material 3, AGP 9.5, Kotlin 2.2, minSdk 29 (Android 10), targetSdk 37
- Version lives in `app/build.gradle.kts` (`versionName`, `versionCode`)
- An HTML/CSS/JS UI prototype sits next to this repo in `../ui/` (reference only)

## Philosophy (decided with the owner; keep to it)

1. **Strictly serverless, no login.** No backend that processes or stores data, no accounts.
   Everything lives on the phone. The only network use is fetching RSS feeds and partner sharing,
   which goes through a small relay (the broker) that stores only end-to-end encrypted blobs it
   can't read. Both apps keep working if the broker is down; only the partner's view pauses.
2. **Dead simple.** Do the basics well: period calendar, next period, ovulation and fertile window,
   mood, symptoms, a short note. Say no to "super duper" features. When unsure, leave it out and ask.
3. **Women-first.** The app speaks to her, warmly. The home card leads with a daily wish for her
   phase; pregnancy chance is a quiet secondary line, never the headline. Fertility info can be
   hidden (shows "Mid-cycle" instead). Sex logging is optional and off the calendar unless she
   turns it on. Partner features must never feel like surveillance: she decides what's shared.
4. **Predictions adjust themselves**, mathematically, from her own logged periods (see
   `domain/CycleCalculator.kt`). They're estimates; the app says it's not medical advice and not birth control.
5. **One local SQLite file holds everything** (logs, settings, feeds, read marks). It can be
   exported and imported from Profile and opened by any SQLite tool. Android cloud backup is off;
   on uninstall Android offers to keep the data (`hasFragileUserData`).
6. **Future-proof data.** One row per day; everything she logs about the day is a JSON blob, so new
   things to log never need a migration and old rows always load. Details below.
7. **Modern, native Android look.** Material 3, her accent color (pastel red default; Leaf green,
   Sunflower yellow, or Wallpaper/dynamic on Android 12+), light/dark/system theme. Widgets follow
   the same colors and dark mode.
8. **Minimal libraries.** AndroidX/Compose/Material (incl. WorkManager and CameraX for partner
   sharing), **jsoup** (MIT) and **ZXing core** (Apache 2.0, QR codes) only. No image, network,
   database, JSON, crypto or widget libraries: use what Android has (SQLiteOpenHelper,
   HttpURLConnection, org.json, java.security/javax.crypto, RemoteViews). Any new dependency must be GPL-3-compatible and listed in
   `THIRD_PARTY_NOTICES.md` and the licenses dialog (`Words.Profile.OSS_TEXT`). Ask before adding one.
9. **All wording in one file.** Every string the app shows is in
   `app/src/main/java/site/brutt/betterred/Words.kt` (plain `const val`s, small `fun`s when
   something is filled in). Only the launcher/widget-picker names live in `res/values/strings.xml`
   because Android requires it. Write short, plain, friendly English.
10. **"…and the doctor said"** (the news tab) is dead simple too: reads from women's-health
    non-profits, loads fresh when the tab opens, shows only what the feeds currently provide,
    marks unread posts, opens posts in a built-in reader view. One default feed plus up to 3 of her own.

## Fixed decisions

- **Only the last 5 years are kept** (`History.YEARS` in `domain/Models.kt`; first day = first of
  the month 5 years back). Each time the app starts, `AppViewModel` deletes older `day_log` rows
  (`Store.deleteDaysBefore`). The calendar can't page further back, the onboarding date picker
  stops there, and queries load only that range (so predictions use only it). An imported backup's
  older days go at the next app start. This is stated in README.md, in the calendar (at the oldest
  month) and in Profile → Your data. Change all of those together.
- **Notes: max 200 characters** (`DayLog.NOTE_MAX`), enforced in the sheet, on save and on load.
- **Mood icons are outline faces** drawn as vectors in `ui/MoodIcons.kt` (same 1.6 line on a 24 grid).
  Don't go back to color emoji for moods.
- **Calendar day markers sit on the right edge:** ♥ (sex logged) top right, • (has notes) bottom
  right. Today's underline is bottom center.
- **Widgets must follow dark mode** without being redrawn: on Android 12+ every color is given to the
  launcher in both light and dark (`Widgets.Palette`, `RemoteViews.setColorInt` / `setIcon`).
  Never call `setTextColor`/`setInt` with a single color in widget code; go through `Palette`.
- **News widget rows go inside the widget update** on Android 12+ (`RemoteCollectionItems`), not via
  `NewsWidgetService` + `notifyAppWidgetViewDataChanged`. On newer Android the service path is
  converted in the background and its refresh re-sent the *previous* frame (bug: title/background
  stuck on old colors). The service is only for Android 10–11.

## Data storage (`data/Store.kt`, `data/DayJson.kt`)

One file: `better_red.db`, WAL off (so the file on disk is always complete and can be copied).

```
day_log   date TEXT PK ('YYYY-MM-DD')  period_day INTEGER 0/1  data TEXT (JSON)  updated_at INTEGER (Unix seconds)
settings  key TEXT PK, value TEXT                       (Prefs.kt reads these)
feed      id, url UNIQUE, title, enabled, builtin       (news sources)
read_item link PK, read_at                              (articles she opened; pruned after a year)
device    one row (id = 1): device_key, role, server, pair_id, secret, share_key,
          shared_at, synced_at, sync_error, partner_name   (partner sharing, v5; see share/ShareSync.kt)
```

`day_log.data` today:
`{"flow":"LIGHT|MEDIUM|HEAVY", "mood":"HAPPY|…", "tags":["CRAMPS","TENDER_BREASTS",…], "note":"…", "sex":"PROTECTED|UNPROTECTED"}`

Rules:
- **All day queries are by date** (`WHERE date >= ?` etc.); dates as text sort correctly.
- `period_day` is a real column (so period-only queries are cheap); it's 1 exactly when `flow` is set.
- JSON values are **enum names**, never display words (those are in `Words.kt`).
- Every key is optional; missing = nothing logged; empty values are left out.
- Reading never fails: unknown keys, unknown enum values and broken JSON are skipped.
- Saving merges into the existing JSON, so keys/tags this version doesn't know are kept.
- **To log something new:** add a field to `DayLog`, a key in `DayJson` (read + write), a test in
  `DayJsonTest`. Do **not** bump `DB_VERSION` for that.
- **Bump `DB_VERSION` only when tables/columns change**, and add a step in `onUpgrade`
  (`if (oldVersion < N) …`) so old databases *and old backup files* upgrade. Imported backups go
  through the same upgrade. A backup from a newer version is refused.
- SQLite has no JSON column type; JSON is stored as TEXT. Don't rely on SQLite's `json_*` functions
  (not guaranteed in Android 10's SQLite); parse in Kotlin with `org.json`.

## Code map (`app/src/main/java/site/brutt/betterred/`)

- `Words.kt` — every UI string
- `MainActivity.kt` — splash, edge-to-edge, widget taps, redraws widgets on start
- `domain/` — pure Kotlin, unit-tested: `Models.kt` (DayLog, Flow, Mood, Symptom, Sex, History),
  `CycleCalculator.kt` (cycle model, phases, fertile window, late detection), `Wishes.kt` (daily wish, labels)
- `data/` — `Store.kt` (SQLite), `DayJson.kt` (day JSON), `Prefs.kt` (settings), `Feeds.kt`, `Rss.kt`,
  `Http.kt`, `NewsCache.kt`, `ReadMode.kt` (jsoup reader view)
- `ui/` — `AppViewModel.kt` (all writes, one at a time, off the main thread; then reloads),
  `BetterRedApp.kt` (tabs), `calendar/` (CalendarScreen, LogSheet = the "Bloody note" sheet),
  `news/`, `profile/` (incl. About → Credits), `onboarding/`, `theme/`, `brand/` (logo), `MoodIcons.kt`, `Avatar.kt`,
  `Links.kt` (open a link in the browser)
- `share/` — partner sharing: `ShareCrypto` (keys, codes, encryption; pure JVM), `Share` (constants,
  QR format, settings blob, what's shared), `Broker` (HTTP client), `ShareSync` (push / pull /
  pair / stop), `ShareWorker` (WorkManager), `Qr` (ZXing). UI in `ui/partner/` (PartnerScreen, ScanScreen)
- `widget/` — `Widgets.kt` (draws both widgets, `Palette`), `CycleWidget`, `NewsWidget`, `NewsWidgetService`
- `tools/logo/` — scripts that trace and generate the logo vectors

Comments in the code are short plain English and refer to the user as "her"/"she". Keep that style.

## Build, test, run

Android Studio is installed as a Flatpak. Terminal builds must use Studio's bundled JDK and these
flags, otherwise Studio's Gradle sync breaks:

```bash
JAVA_HOME=$(ls -d /var/lib/flatpak/app/com.google.AndroidStudio/x86_64/stable/*/files/extra/jbr | head -1) \
  ./gradlew --no-daemon --no-configuration-cache -Pkotlin.compiler.execution.strategy=in-process \
  testDebugUnitTest assembleDebug
```

- Unit tests: `app/src/test/` (CycleCalculator, DayJson, Rss, ReadMode, ShareCrypto). Run them after
  every change. `org.json` is a test-only dependency (Android ships its own).
- `BrokerIntegrationTest` runs the whole sharing protocol against a live broker, only when
  `BR_TEST_BROKER=localhost:<port>` is set (start one from `../better-red-broker` with `go run .`).
- Debug builds accept `localhost:<port>` / `10.0.2.2:<port>` as the sharing server over plain http
  (`app/src/debug/res/xml/network_security_config.xml`); use `adb reverse tcp:8080 tcp:8080`.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; install with `adb install -r …`.
- **Releases** (docs/releasing.md): `Dockerfile` builds the signed release APK (JDK 21 + pinned
  Android SDK; keys only as build secrets), `make apk` runs it, and pushing a `v*` tag runs
  `.github/workflows/release.yml` (tag must be `v` + `versionName`) → GitHub release with the APK.
  Release signing comes from `BR_KEYSTORE_*` env vars or a git-ignored `keystore.properties`;
  without them the release APK is unsigned. When `compileSdk`/AGP change, update the Dockerfile's
  SDK `ARG`s.
- **Release builds use R8** (whole app; never narrow `optimization.packageScope`: that crashed at
  start). Keep rules for reflection go in `app/src/main/keepRules/rules.keep` (one for Room/WorkManager
  is there). Debug builds don't run R8, so **test a release APK on the emulator before tagging**:
  start, onboarding, log a day, restart, news, pairing. One universal APK (native libs are ~200 KB).
- The owner's Pixel may be connected over wireless adb. It holds **real data**: before installing a
  build that changes the database, back it up first:
  `adb exec-out run-as site.brutt.betterRed cat databases/better_red.db > ../backups/<name>.db`
- Don't change settings on the phone (dark mode, etc.) without asking.

## Git

- The repo is this folder (`better-red/`). The owner makes their own commits (they're GPG-signed):
  **don't commit or push** unless asked.
- Never commit signing keys (`*.jks`, `*.keystore`, `keystore.properties`) or `local.properties`.

## Partner sharing (built; details in docs/partner-sharing.md)

Decided with the owner, keep to it:

- **The broker is dumb and disposable**: `../better-red-broker` (Go + SQLite, own git repo and
  `docs/`). One table. Minting a pair id stores nothing; the row appears on her first write; if the
  server loses everything, her phone's next sync refills it (only the server's signing key matters).
- **Default server** `better-red.brutt.site`, pre-filled and editable; **domain only**, the app adds
  `https://` and the paths. Its landing page uses the app's light pastel-red theme (light only, no scripts).
- **Device id**: every phone has a random device key (`device` table). The pair id is
  `<device-id = hash(device key)>.<salted server signature>`, self-validating, sent in a header.
  Her phone proves it's hers by sending the device key (never stored by the broker).
- **Pairing**: mint a pair id (this deletes her old `device-id.*` pairs; the UI warns first), make a
  pair secret and a P-256 key pair, keep the **public** key, and show the QR once:
  `BR1|domain|pair id|private key|secret`. The partner scans it (camera or a screenshot).
- **Auth**: her writes carry the device key and the pair secret (stored as sent); the partner reads
  with a TOTP (HMAC-SHA256, 30 s, 8 digits) made from the secret. The partner can never write.
- **Data**: only the last **60 days** (`Share.MAX_DAYS`) + a settings blob `"0"` (name, her calculated
  cycle numbers, share choices). Each blob compressed, padded, encrypted (ECDH + HKDF + AES-GCM, pair
  id and day as associated data). Every day of the window is sent so the broker can't tell period
  days apart. Sex is never shared.
- **Sync**: her phone after every change, on app open and every 30 min (WorkManager); the partner on
  app open, "Sync now" and every 30 min. Nothing on the server = the partner shows "waiting for her
  phone" (calm, not an error). The broker deletes pairs unused for 10 days and days older than 60.
- **Partner's phone is view-only everywhere**; home card "How to support her today". Disconnect wipes
  it back to the welcome screen; "Scan a new code" re-pairs.
- **Stop sharing** deletes her data from the broker and forgets the pairing.

## Future plan (agreed, not built yet)

1. **Partner sharing, later:** reminders to the partner, maybe a second broker for redundancy.
2. **Reminders:** period coming (2 days before), daily mood check-in, optional fertile-window reminder.
3. **Small ideas:** red dot on the "Doctor said" tab when there are unread posts; a discreet/privacy
   option for the cycle widget; final logo polish.
4. **Publishing:** create the release signing key and GitHub secrets (docs/releasing.md), privacy policy ("data stays on the phone"),
   Play Store listing; maybe F-Droid since it's GPL.
