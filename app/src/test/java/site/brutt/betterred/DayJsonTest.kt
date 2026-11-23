package site.brutt.betterred

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import site.brutt.betterred.data.DayJson
import site.brutt.betterred.domain.DayLog
import site.brutt.betterred.domain.Flow
import site.brutt.betterred.domain.History
import site.brutt.betterred.domain.Mood
import site.brutt.betterred.domain.Sex
import site.brutt.betterred.domain.Symptom
import java.time.LocalDate

class DayJsonTest {
    private val day = LocalDate.of(2026, 11, 3)

    @Test fun roundTrip() {
        val log = DayLog(day, Flow.HEAVY, Mood.CALM, setOf(Symptom.TENDER_BREASTS, Symptom.CRAMPS), "warm tea", Sex.PROTECTED)
        assertEquals(log, DayJson.read(day, periodDay = true, DayJson.write(log)))
    }

    @Test fun emptyDayIsEmptyObject() {
        assertEquals("{}", DayJson.write(DayLog(day)))
    }

    @Test fun leavesOutEmptyValues() {
        val o = JSONObject(DayJson.write(DayLog(day, mood = Mood.HAPPY)))
        assertEquals(setOf("mood"), o.keys().asSequence().toSet())
    }

    @Test fun readsOldOrBrokenRowsWithoutFailing() {
        assertEquals(DayLog(day), DayJson.read(day, false, null))
        assertEquals(DayLog(day), DayJson.read(day, false, "not json"))
        assertEquals(DayLog(day), DayJson.read(day, false, """{"note":null,"mood":42,"tags":"CRAMPS"}"""))
    }

    @Test fun skipsValuesThisVersionDoesNotKnow() {
        val log = DayJson.read(day, false, """{"mood":"EUPHORIC","tags":["CRAMPS","NAUSEA"],"water":8}""")
        assertNull(log.mood)
        assertEquals(setOf(Symptom.CRAMPS), log.symptoms)
    }

    @Test fun keepsUnknownKeysAndTagsWhenSaving() {
        val newer = """{"tags":["CRAMPS","NAUSEA"],"water":8,"mood":"LOW"}"""
        val edited = DayJson.read(day, false, newer).copy(mood = Mood.HAPPY, symptoms = setOf(Symptom.ACNE))
        val o = JSONObject(DayJson.write(edited, previous = newer))
        assertEquals(8, o.getInt("water"))
        assertEquals("HAPPY", o.getString("mood"))
        val tags = o.getJSONArray("tags").let { a -> (0 until a.length()).map { a.getString(it) } }
        assertEquals(listOf("ACNE", "NAUSEA"), tags)
    }

    @Test fun periodDayWithoutIntensityStillCounts() {
        assertEquals(Flow.MEDIUM, DayJson.read(day, periodDay = true, "{}").flow)
        assertNull(DayJson.read(day, periodDay = false, "{}").flow)
    }

    @Test fun noteIsCapped() {
        val long = "a".repeat(DayLog.NOTE_MAX + 50)
        val o = JSONObject(DayJson.write(DayLog(day, note = long)))
        assertEquals(DayLog.NOTE_MAX, o.getString("note").length)
        assertEquals(DayLog.NOTE_MAX, DayJson.read(day, false, """{"note":"$long"}""").note.length)
    }

    @Test fun historyStartsAtFirstOfMonthFiveYearsBack() {
        assertEquals(LocalDate.of(2021, 11, 1), History.firstDay(LocalDate.of(2026, 11, 24)))
        assertTrue(History.YEARS == 5)
    }
}
