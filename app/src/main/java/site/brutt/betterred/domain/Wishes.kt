package site.brutt.betterred.domain

import site.brutt.betterred.Words.Day
import java.time.LocalDate
import java.time.LocalTime

/** Picks the right words (from Words.kt) for a day. No text lives here. */
object Wishes {
    enum class Key { PERIOD, EXPECTED, FOLLICULAR, MID, LUTEAL, NONE }

    private val forHer = mapOf(
        Key.PERIOD to Day.WISH_PERIOD,
        Key.EXPECTED to Day.WISH_EXPECTED,
        Key.FOLLICULAR to Day.WISH_FOLLICULAR,
        Key.MID to Day.WISH_MID,
        Key.LUTEAL to Day.WISH_LUTEAL,
        Key.NONE to Day.WISH_NO_DATA,
    )

    private val support = mapOf(
        Key.PERIOD to Day.SUPPORT_PERIOD,
        Key.EXPECTED to Day.SUPPORT_EXPECTED,
        Key.FOLLICULAR to Day.SUPPORT_FOLLICULAR,
        Key.MID to Day.SUPPORT_MID,
        Key.LUTEAL to Day.SUPPORT_LUTEAL,
        Key.NONE to Day.SUPPORT_NO_DATA,
    )

    fun key(phase: Phase?): Key = when (phase) {
        null -> Key.NONE
        Phase.PERIOD -> Key.PERIOD
        Phase.PERIOD_EXPECTED -> Key.EXPECTED
        Phase.FOLLICULAR -> Key.FOLLICULAR
        Phase.FERTILE, Phase.OVULATION -> Key.MID
        Phase.LUTEAL -> Key.LUTEAL
    }

    /** A wish that changes every day. */
    fun wish(phase: Phase?, date: LocalDate, name: String): Pair<String, String> {
        val list = forHer.getValue(key(phase))
        val (title, body) = list[Math.floorMod(date.toEpochDay(), list.size.toLong()).toInt()]
        return title.withName(name) to body.withName(name)
    }

    fun support(phase: Phase?, name: String): String = support.getValue(key(phase)).withName(name)

    /** Phase name. With fertility hidden, fertile and ovulation days become "Mid-cycle". */
    fun label(phase: Phase?, showFertility: Boolean): String? = when (phase) {
        null -> null
        Phase.PERIOD -> Day.PHASE_PERIOD
        Phase.PERIOD_EXPECTED -> Day.PHASE_EXPECTED
        Phase.FOLLICULAR -> Day.PHASE_FOLLICULAR
        Phase.FERTILE -> if (showFertility) Day.PHASE_FERTILE else Day.PHASE_MID
        Phase.OVULATION -> if (showFertility) Day.PHASE_OVULATION else Day.PHASE_MID
        Phase.LUTEAL -> Day.PHASE_LUTEAL
    }

    fun tip(phase: Phase?, showFertility: Boolean): String? = when (phase) {
        null -> null
        Phase.PERIOD -> Day.TIP_PERIOD
        Phase.PERIOD_EXPECTED -> Day.TIP_EXPECTED
        Phase.FOLLICULAR -> Day.TIP_FOLLICULAR
        Phase.FERTILE -> if (showFertility) Day.TIP_FERTILE else Day.TIP_MID
        Phase.OVULATION -> if (showFertility) Day.TIP_OVULATION else Day.TIP_MID
        Phase.LUTEAL -> Day.TIP_LUTEAL
    }

    fun chance(chance: Chance): String = when (chance) {
        Chance.LOW -> Day.CHANCE_LOW
        Chance.MEDIUM -> Day.CHANCE_MEDIUM
        Chance.HIGH -> Day.CHANCE_HIGH
    }

    fun greeting(now: LocalTime = LocalTime.now()): String = when (now.hour) {
        in 0..11 -> Day.MORNING
        in 12..16 -> Day.AFTERNOON
        else -> Day.EVENING
    }

    /** No name set → "Be gentle with yourself 🤍" instead of "…, you". */
    private fun String.withName(name: String): String =
        if (name.isNotBlank()) replace("{n}", name.trim())
        else replace(", {n}", "").replace(" {n}", "").replace("{n}’s", "your partner’s").replace("{n}", "your partner")
}
