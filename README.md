<img src="./tools/logo/logo-round.png" align="right" width="100px">
    
# Better Red

> ⚠️ Vibe coded

A simple, private period tracker for Android. No account, no servers: everything stays in one
SQLite file on the phone, which can be exported and imported from Profile.

- App ID: `site.brutt.betterRed`
- All text shown in the app: `app/src/main/java/site/brutt/betterred/Words.kt`
- Logo tools: `tools/logo/`

## Decisions

- **Only the last 5 years are kept.** The calendar pages back at most 5 years (to the first day
  of that month), and predictions use only those days. Older days are deleted from the database
  each time the app starts (an exported backup still has whatever it had when it was made). The app
  says so in the calendar (at the oldest month) and in Profile → Your data. The limit is one
  constant: `History.YEARS` in `domain/Models.kt`.
- **Notes are at most 200 characters** per day (`DayLog.NOTE_MAX`).
- **One row per day, details in JSON.** The `day_log` table has `date` (primary key, `YYYY-MM-DD`),
  `period_day` (0/1), `data` (JSON text) and `updated_at` (Unix time, seconds). Flow, mood, tags
  (symptoms), note and sex live in the JSON, so new things to log need no database migration and
  old rows keep working. The format is described in `data/DayJson.kt`.

## License

Better Red is free software, licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE).
The open-source libraries it uses (Apache 2.0 and MIT, both GPL-compatible) are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
