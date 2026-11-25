# Publishing on F-Droid

F-Droid builds the app itself from this repository's source (it never uses our APKs, unless we
later make the build reproducible, see the end) and signs it with its own key.

## What's ready in this repo

| | |
|---|---|
| `fastlane/metadata/android/en-US/` | everything F-Droid shows: `title.txt`, `short_description.txt` (≤ 80 characters), `full_description.txt`, `changelogs/<versionCode>.txt`, `images/icon.png` (512×512), `images/featureGraphic.png` (1024×500), `images/phoneScreenshots/1…11.png` |
| `app/build.gradle.kts` | `dependenciesInfo { includeInApk = false }`: F-Droid rejects the Google-signed dependency block AGP adds otherwise; release builds are unsigned unless a key is given, so F-Droid can sign them |
| License, source, tags | GPL-3.0, public on GitHub, one `vX.Y.Z` tag per release |

Screenshots were taken on the emulator with made-up data ("Ray"), never real data. To redo them,
see "Screenshots" below.

**Every release:** add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` (a few
lines, ≤ 500 characters) before tagging. F-Droid shows it as "What's new".

## Submitting (once)

1. Make a GitLab account and fork https://gitlab.com/fdroid/fdroiddata.
2. In the fork, add `metadata/site.brutt.betterRed.yml`:

   ```yaml
   Categories:
     - Sports & Health
   License: GPL-3.0-only
   AuthorName: bruttazz
   AuthorWebSite: https://brutt.site
   SourceCode: https://github.com/bRuttaZz/better-red
   IssueTracker: https://github.com/bRuttaZz/better-red/issues
   Changelog: https://github.com/bRuttaZz/better-red/releases

   AutoName: Better Red

   RepoType: git
   Repo: https://github.com/bRuttaZz/better-red.git

   Builds:
     - versionName: 1.2.1
       versionCode: 3
       commit: v1.2.1
       subdir: app
       gradle:
         - yes

   AutoUpdateMode: Version
   UpdateCheckMode: Tags
   CurrentVersion: 1.2.1
   CurrentVersionCode: 3
   ```

   (Reviewers may change the category name to the current list; that's normal.)
3. Check it locally if you can (optional; needs `fdroidserver`): `fdroid lint site.brutt.betterRed`
   and `fdroid build -v -l site.brutt.betterRed`.
4. Open a merge request to fdroiddata using the "App inclusion" template. Mention:
   - no trackers, no analytics, no proprietary libraries (AndroidX, jsoup, ZXing core, CameraX; all
     Apache 2.0 / MIT, listed in THIRD_PARTY_NOTICES.md);
   - network use: RSS feeds of women's health non-profits, and optional partner sharing through
     `better-red.brutt.site`, whose server is free software (GPL-3.0,
     https://github.com/bRuttaZz/better-red-broker) and can be self-hosted. So no "NonFreeNet"
     anti-feature.
5. Answer the reviewers' questions in the merge request. After it's merged, the app appears in the
   F-Droid client within a few days. Later versions are picked up automatically from new `v*` tags
   (`UpdateCheckMode: Tags`), as long as `versionName`/`versionCode` in `app/build.gradle.kts` match.

## Good to know

- **Different signatures.** F-Droid signs with its own key, GitHub releases with ours. Android
  won't update one from the other: switching means export a backup (Profile), uninstall, install
  the other, import.
- **Same APK on both, later (optional).** If F-Droid can rebuild our APK bit for bit
  ("reproducible build"), it can publish ours instead, and both sources update each other. That
  needs `Binaries:` and `AllowedAPKSigningKeys:` (our certificate's SHA-256, in every GitHub
  release's notes) in the metadata, and a build that is byte-identical on their server. Worth
  trying once the app is in.
- **Anti-features** F-Droid might ask about: none apply (no ads, tracking, non-free dependencies or
  non-free network services).
- The app ID is `site.brutt.betterRed`; it can never change once published.

## Screenshots

Taken on the "Medium Phone API 37" emulator (1080×2400) with a debug build:

1. Install the debug APK, open it once, then fill its database with sample data: pull
   `databases/better_red.db` with `adb exec-out run-as site.brutt.betterRed cat …`, insert
   made-up `day_log` rows and settings (`name`, `theme`), push it back with `adb exec-in run-as …`.
2. Clean status bar: `adb shell settings put global sysui_demo_allowed 1`, then
   `adb shell am broadcast -a com.android.systemui.demo -e command enter` (and `clock -e hhmm 0930`,
   `battery -e level 100`, `notifications -e visible false`).
3. `adb exec-out screencap -p > N.png` for each screen; widgets are added from the launcher's
   widget picker. Don't include screenshots of article pages with people's photos.
