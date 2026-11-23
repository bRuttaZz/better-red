package site.brutt.betterred.data

import org.json.JSONArray
import org.json.JSONObject
import site.brutt.betterred.domain.DayLog
import site.brutt.betterred.domain.Flow
import site.brutt.betterred.domain.Mood
import site.brutt.betterred.domain.Sex
import site.brutt.betterred.domain.Symptom
import java.time.LocalDate

/**
 * Reads and writes the `data` JSON of one day_log row (see Store.kt). Uses org.json, which is
 * part of Android, so no extra library.
 *
 * Today's keys (every key is optional; a missing key means "nothing logged"):
 *
 *   {
 *     "flow": "LIGHT" | "MEDIUM" | "HEAVY",   period intensity
 *     "mood": "HAPPY" | "CALM" | …,           one mood
 *     "tags": ["CRAMPS", "TENDER_BREASTS"],   symptoms
 *     "note": "…",                            at most 200 characters
 *     "sex":  "PROTECTED" | "UNPROTECTED"
 *   }
 *
 * Rules that keep old and new versions compatible:
 * - Values are the enum names, never the words shown on screen.
 * - Reading never fails: a key or value this version doesn't know is skipped.
 * - Writing keeps whatever this version doesn't know (other keys, unknown tags) from the row
 *   it replaces, so nothing a newer version stored is lost.
 * - Empty values are left out, so rows stay small.
 * - To add something new: add a key here (and a field on DayLog). No database migration needed.
 */
object DayJson {
    const val FLOW = "flow"
    const val MOOD = "mood"
    const val TAGS = "tags"
    const val NOTE = "note"
    const val SEX = "sex"

    fun read(date: LocalDate, periodDay: Boolean, json: String?): DayLog {
        val o = parse(json)
        val tags = o.optJSONArray(TAGS)
        return DayLog(
            date = date,
            // A period day whose intensity is missing (or unknown) still shows as a period day.
            flow = enumOrNull<Flow>(o.str(FLOW)) ?: if (periodDay) Flow.MEDIUM else null,
            mood = enumOrNull<Mood>(o.str(MOOD)),
            symptoms = buildSet {
                if (tags != null) for (i in 0 until tags.length()) enumOrNull<Symptom>(tags.opt(i) as? String)?.let(::add)
            },
            note = o.str(NOTE).take(DayLog.NOTE_MAX),
            sex = enumOrNull<Sex>(o.str(SEX)),
        )
    }

    /** The JSON for [log]. [previous] is the row's current JSON, so unknown keys and tags carry over. */
    fun write(log: DayLog, previous: String? = null): String {
        val o = parse(previous)
        val keptTags = o.optJSONArray(TAGS)?.let { arr ->
            (0 until arr.length()).mapNotNull { arr.opt(it) as? String }.filter { it.isNotBlank() && enumOrNull<Symptom>(it) == null }
        }.orEmpty()

        o.putOrRemove(FLOW, log.flow?.name)
        o.putOrRemove(MOOD, log.mood?.name)
        o.putOrRemove(NOTE, log.note.trim().take(DayLog.NOTE_MAX).ifEmpty { null })
        o.putOrRemove(SEX, log.sex?.name)
        val tags = log.symptoms.sortedBy { it.ordinal }.map { it.name } + keptTags
        if (tags.isEmpty()) o.remove(TAGS) else o.put(TAGS, JSONArray(tags))
        return o.toString()
    }

    private fun parse(json: String?): JSONObject =
        if (json.isNullOrBlank()) JSONObject() else runCatching { JSONObject(json) }.getOrElse { JSONObject() }

    /** The text at [key], or "" when it is missing, null or not text. */
    private fun JSONObject.str(key: String): String = (opt(key) as? String).orEmpty()

    private fun JSONObject.putOrRemove(key: String, value: String?) {
        if (value == null) remove(key) else put(key, value)
    }
}

internal inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
    if (name.isNullOrBlank()) null else runCatching { enumValueOf<T>(name) }.getOrNull()
