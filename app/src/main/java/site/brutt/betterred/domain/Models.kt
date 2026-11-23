package site.brutt.betterred.domain

import site.brutt.betterred.Words.Labels
import java.time.LocalDate

// The database stores the enum names (LIGHT, HAPPY, …). The words shown live in Words.kt.

enum class Flow(val label: String) {
    LIGHT(Labels.FLOW_LIGHT),
    MEDIUM(Labels.FLOW_MEDIUM),
    HEAVY(Labels.FLOW_HEAVY),
}

enum class Mood(val emoji: String, val label: String) {
    HAPPY("😊", Labels.MOOD_HAPPY),
    CALM("😌", Labels.MOOD_CALM),
    TIRED("😴", Labels.MOOD_TIRED),
    LOW("😢", Labels.MOOD_LOW),
    IRRITABLE("😤", Labels.MOOD_IRRITABLE),
    ANXIOUS("😰", Labels.MOOD_ANXIOUS),
}

/** Optional intimacy log. Never shared with a partner unless she chooses to (later). */
enum class Sex(val label: String) {
    PROTECTED(Labels.SEX_PROTECTED),
    UNPROTECTED(Labels.SEX_UNPROTECTED),
}

enum class Symptom(val label: String) {
    CRAMPS(Labels.SYM_CRAMPS),
    HEADACHE(Labels.SYM_HEADACHE),
    BLOATING(Labels.SYM_BLOATING),
    BACKACHE(Labels.SYM_BACKACHE),
    ACNE(Labels.SYM_ACNE),
    TENDER_BREASTS(Labels.SYM_TENDER),
    CRAVINGS(Labels.SYM_CRAVINGS),
    SPOTTING(Labels.SYM_SPOTTING),
}

/** Everything she logged for one day. A day with a [flow] counts as a period day. */
data class DayLog(
    val date: LocalDate,
    val flow: Flow? = null,
    val mood: Mood? = null,
    val symptoms: Set<Symptom> = emptySet(),
    val note: String = "",
    val sex: Sex? = null,
) {
    val isEmpty: Boolean get() = flow == null && mood == null && symptoms.isEmpty() && note.isBlank() && sex == null
    val hasDetails: Boolean get() = mood != null || symptoms.isNotEmpty() || note.isNotBlank()
}
