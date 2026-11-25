<img src="./tools/logo/logo-round.png" align="right" width="100px">
    
# Better Red

[![GPLv3 License](https://img.shields.io/badge/License-GPL%20v3-yellow.svg)](https://opensource.org/licenses/)
![Workflow status](https://github.com/bruttazz/better-red/actions/workflows/release.yml/badge.svg)

APK Download: [![Release](https://img.shields.io/github/release/bruttazz/better-red.svg?style=flat-square)](https://github.com/bruttazz/better-red/releases/latest)

---

> ⚠️ Vibe coded

<!-- ai-footprint:start -->
**🌍 AI footprint so far** · built with Claude Code

| ⚡ Energy | 💧 Water | 🧠 Tokens processed | 💬 Sessions | 🔁 Model calls |
|:-:|:-:|:-:|:-:|:-:|
| **~5.3 kWh** | **~19 L** | 224.2 M | 5 | 678 |

For reference: that's about the electricity a 1.5-ton AC uses in ~3.6 hours, and ~38 half-litre bottles of water.

<sub>Model: Opus 5.5 (high). Estimated from Claude Code session logs (could be ~3× lower or higher); water includes on-site cooling (~2.3 L) and generating the electricity. Updated automatically, last on 2026-11-26. [How it's calculated](.claude/footprint/footprint.py).</sub>
<!-- ai-footprint:end -->

---

A simple, private period tracker for Android. No account, no servers: everything stays in one
SQLite file on the phone, which can be exported and imported from Profile.

**Partner sharing** (optional): her partner can see what she chooses to share, view only. It goes
end-to-end encrypted through a small relay, [better-red-broker](../better-red-broker), that can't
read it. Pair in person with a QR code; only the last 60 days are ever shared. See
[docs/partner-sharing.md](docs/partner-sharing.md).

- App ID: `site.brutt.betterRed`
- All text shown in the app: `app/src/main/java/site/brutt/betterred/Words.kt`
- Logo tools: `tools/logo/`

## Download and build

Signed APKs are on the [releases page](https://github.com/bRuttaZz/better-red/releases) (Android 10+).
F-Droid: see [docs/fdroid.md](docs/fdroid.md) (store texts and screenshots are in `fastlane/`).
Pushing a `v*` tag builds and publishes one automatically. To build it yourself you only need
podman or docker:

```bash
make apk          # dist/better-red-<version>-unsigned.apk; KEYSTORE=… KEY_ALIAS=… to sign
```

See [docs/releasing.md](docs/releasing.md) (signing key, GitHub secrets, making a release).

## Decisions

- **Only the last 5 years are kept.** The calendar pages back at most 5 years (to the first day
  of that month), and predictions use only those days. Older days are deleted from the database
  each time the app starts (an exported backup still has whatever it had when it was made). The app
  says so in the calendar (at the oldest month) and in Profile → Your data. The limit is one
  constant: `History.YEARS` in `domain/Models.kt`.
- **Notes are at most 200 characters** per day (`DayLog.NOTE_MAX`).
- **Partner sharing: 60 days, view only, end-to-end encrypted.** Default server
  `better-red.brutt.site` (editable, domain only). Sex is never shared.
- **One row per day, details in JSON.** The `day_log` table has `date` (primary key, `YYYY-MM-DD`),
  `period_day` (0/1), `data` (JSON text) and `updated_at` (Unix time, seconds). Flow, mood, tags
  (symptoms), note and sex live in the JSON, so new things to log need no database migration and
  old rows keep working. The format is described in `data/DayJson.kt`.

## License

Better Red is free software, licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
The open-source libraries it uses (Apache 2.0 and MIT, both GPL-compatible) are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
