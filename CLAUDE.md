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
   Everything lives on the phone. The only network use is fetching RSS feeds and, later,
   partner sync over public relays (see Future plan).
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
8. **Minimal libraries.** AndroidX/Compose/Material plus **jsoup** (MIT) only. No image, network,
   database, JSON or widget libraries: use what Android has (SQLiteOpenHelper, HttpURLConnection,
   org.json, RemoteViews). Any new dependency must be GPL-3-compatible and listed in
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

- Unit tests: `app/src/test/` (CycleCalculator, DayJson, Rss, ReadMode). Run them after every change.
  `org.json` is a test-only dependency (Android ships its own).
- APK: `app/build/outputs/apk/debug/app-debug.apk`; install with `adb install -r …`.
- The owner's Pixel may be connected over wireless adb. It holds **real data**: before installing a
  build that changes the database, back it up first:
  `adb exec-out run-as site.brutt.betterRed cat databases/better_red.db > ../backups/<name>.db`
- Don't change settings on the phone (dark mode, etc.) without asking.

## Git

- The repo is this folder (`better-red/`). The owner makes their own commits (they're GPG-signed):
  **don't commit or push** unless asked.
- Never commit signing keys (`*.jks`, `*.keystore`, `keystore.properties`) or `local.properties`.

## Future plan (agreed, not built yet)

1. **Partner mode** (the "Partner" tab is a placeholder):
   - Pair by scanning each other's QR codes (exchange public keys; local key pairs, no accounts).
   - The partner is **view-only**. She chooses what's shared: period dates always; mood, fertility
     (off by default), symptoms, notes optional.
   - Transport stays serverless: an encrypted snapshot published as a retained MQTT message on
     public brokers (two for redundancy); Nostr relays as the alternative.
   - The partner's home card shows "How to support Better today" (`Words.Day.SUPPORT_*`) instead of a wish.
   - Onboarding already asks "For me / For my partner" (partner = coming soon).
2. **Reminders:** period coming (2 days before), daily mood check-in, optional fertile-window reminder.
3. **Small ideas:** red dot on the "Doctor said" tab when there are unread posts; a discreet/privacy
   option for the cycle widget; final logo polish.
4. **Publishing:** release signing key (never committed), privacy policy ("data stays on the phone"),
   Play Store listing; maybe F-Droid since it's GPL.
