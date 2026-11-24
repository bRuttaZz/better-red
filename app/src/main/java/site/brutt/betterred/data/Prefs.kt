package site.brutt.betterred.data

import site.brutt.betterred.domain.CycleSettings
import site.brutt.betterred.share.ShareChoices

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App color. RED = pastel red, GREEN = leaf, YELLOW = sunflower, DYNAMIC = Android 12+ wallpaper colors. */
enum class Accent { RED, GREEN, YELLOW, DYNAMIC }

/**
 * App settings. Stored as key/value rows in the same SQLite file as her logs,
 * so exporting the database moves everything.
 */
data class Prefs(
    val name: String = "",
    val onboarded: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val accent: Accent = Accent.RED,
    val showFertility: Boolean = true,
    /** Optional: show the "Sex" row when logging, and ♥ on the calendar. */
    val trackSex: Boolean = true,
    /** Profile icon: "" = her initial, otherwise an emoji. */
    val avatar: String = "",
    val cycle: CycleSettings = CycleSettings(),
    /** Partner sharing: what she shares. On a partner's phone: what she shares with them. */
    val share: ShareChoices = ShareChoices(),
) {
    object Keys {
        const val NAME = "name"
        const val ONBOARDED = "onboarded"
        const val THEME = "theme"
        const val ACCENT = "accent"
        const val SHOW_FERTILITY = "show_fertility"
        const val TRACK_SEX = "track_sex"
        const val AVATAR = "avatar"
        const val AUTO_ADJUST = "auto_adjust"
        const val CYCLE_LENGTH = "cycle_length"
        const val PERIOD_LENGTH = "period_length"
        const val LUTEAL_LENGTH = "luteal_length"
        const val SHARE_MOOD = "share_mood"
        const val SHARE_SYMPTOMS = "share_symptoms"
        const val SHARE_NOTES = "share_notes"
        const val SHARE_FERTILITY = "share_fertility"
    }

    companion object {
        fun from(map: Map<String, String>): Prefs {
            val d = Prefs()
            fun bool(k: String, def: Boolean) = map[k]?.toBooleanStrictOrNull() ?: def
            fun int(k: String, def: Int, range: IntRange) = map[k]?.toIntOrNull()?.coerceIn(range) ?: def
            return Prefs(
                name = map[Keys.NAME] ?: d.name,
                onboarded = bool(Keys.ONBOARDED, d.onboarded),
                theme = map[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
                accent = map[Keys.ACCENT]?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: d.accent,
                showFertility = bool(Keys.SHOW_FERTILITY, d.showFertility),
                trackSex = bool(Keys.TRACK_SEX, d.trackSex),
                avatar = map[Keys.AVATAR] ?: d.avatar,
                cycle = CycleSettings(
                    autoAdjust = bool(Keys.AUTO_ADJUST, d.cycle.autoAdjust),
                    cycleLength = int(Keys.CYCLE_LENGTH, d.cycle.cycleLength, CycleSettings.CYCLE_RANGE),
                    periodLength = int(Keys.PERIOD_LENGTH, d.cycle.periodLength, CycleSettings.PERIOD_RANGE),
                    lutealLength = int(Keys.LUTEAL_LENGTH, d.cycle.lutealLength, CycleSettings.LUTEAL_RANGE),
                ),
                share = ShareChoices(
                    mood = bool(Keys.SHARE_MOOD, d.share.mood),
                    symptoms = bool(Keys.SHARE_SYMPTOMS, d.share.symptoms),
                    notes = bool(Keys.SHARE_NOTES, d.share.notes),
                    fertility = bool(Keys.SHARE_FERTILITY, d.share.fertility),
                ),
            )
        }
    }
}
