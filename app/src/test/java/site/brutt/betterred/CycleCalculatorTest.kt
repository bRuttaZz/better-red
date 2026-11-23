package site.brutt.betterred

import site.brutt.betterred.domain.Chance
import site.brutt.betterred.domain.CycleCalculator
import site.brutt.betterred.domain.CycleSettings
import site.brutt.betterred.domain.Phase
import site.brutt.betterred.domain.Wishes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Prediction math tests. Run with:  ./gradlew testDebugUnitTest
 * (or right-click this file in Android Studio → Run).
 */
class CycleCalculatorTest {

    private val today: LocalDate = LocalDate.of(2026, 11, 23)

    /** Period days for each (start, length). */
    private fun periods(vararg runs: Pair<LocalDate, Int>): Set<LocalDate> =
        runs.flatMap { (start, len) -> (0 until len).map { start.plusDays(it.toLong()) } }.toSet()

    /** Same history as the HTML prototype: cycles of 28, 28, 30, 27, 29 days; current period started Nov 12. */
    private val history = run {
        val gaps = listOf(28, 28, 30, 27, 29)
        val lengths = listOf(5, 6, 5, 5, 4, 5)
        var start = LocalDate.of(2026, 11, 12)
        val starts = mutableListOf(start)
        for (g in gaps.reversed()) { start = start.minusDays(g.toLong()); starts.add(0, start) }
        periods(*starts.zip(lengths).toTypedArray())
    }

    private val model = CycleCalculator.model(history, CycleSettings(), today)

    @Test fun `averages the recent cycles to predict the next period`() {
        assertEquals(28, model.cycleLength)  // 28.4 rounds to 28
        assertEquals(5, model.periodLength)
        assertEquals(LocalDate.of(2026, 12, 10), model.nextStart)
        assertEquals(0, model.daysLate)
    }

    @Test fun `today is cycle day 12 in the fertile window`() {
        val info = CycleCalculator.dayInfo(today, model)
        assertEquals(12, info.cycleDay)
        assertEquals(Phase.FERTILE, info.phase)
        assertEquals(Chance.MEDIUM, info.chance)
    }

    @Test fun `ovulation is 14 days before the next period`() {
        assertEquals(LocalDate.of(2026, 11, 26), CycleCalculator.upcomingOvulation(model))
        val ov = CycleCalculator.dayInfo(LocalDate.of(2026, 11, 26), model)
        assertTrue(ov.isOvulation)
        assertEquals(Chance.HIGH, ov.chance)
        assertEquals(Chance.HIGH, CycleCalculator.dayInfo(LocalDate.of(2026, 11, 24), model).chance)
        assertEquals(Phase.LUTEAL, CycleCalculator.dayInfo(LocalDate.of(2026, 11, 28), model).phase)
        assertEquals(Phase.FOLLICULAR, CycleCalculator.dayInfo(LocalDate.of(2026, 11, 19), model).phase)
    }

    @Test fun `logged days are period days with low chance`() {
        val info = CycleCalculator.dayInfo(LocalDate.of(2026, 11, 13), model)
        assertEquals(Phase.PERIOD, info.phase)
        assertEquals(Chance.LOW, info.chance)
    }

    @Test fun `next period is predicted for the average period length`() {
        val start = LocalDate.of(2026, 12, 10)
        for (i in 0 until 5) assertTrue(CycleCalculator.dayInfo(start.plusDays(i.toLong()), model).isPredicted)
        assertFalse(CycleCalculator.dayInfo(start.plusDays(5), model).isPredicted)
        assertEquals(Phase.PERIOD_EXPECTED, CycleCalculator.dayInfo(start, model).phase)
    }

    @Test fun `a late period is reported and expected today`() {
        val m = CycleCalculator.model(periods(today.minusDays(40) to 5), CycleSettings(), today)
        assertEquals(12, m.daysLate)  // 40 days since start, 28-day cycle
        assertEquals(today, m.nextStart)
        assertEquals(Phase.PERIOD_EXPECTED, CycleCalculator.dayInfo(today, m).phase)
    }

    @Test fun `logging a new period moves the predictions`() {
        val early = history + periods(LocalDate.of(2026, 12, 6) to 5)
        val m = CycleCalculator.model(early, CycleSettings(), LocalDate.of(2026, 12, 20))
        assertEquals(LocalDate.of(2027, 1, 3), m.nextStart)  // 6 Dec + 28
    }

    @Test fun `unusual gaps are ignored`() {
        val base = LocalDate.of(2026, 1, 1)
        val days = periods(base to 5, base.plusDays(28) to 5, base.plusDays(56) to 5, base.plusDays(146) to 5)
        val m = CycleCalculator.model(days, CycleSettings(), base.plusDays(150))
        assertEquals(28, m.cycleLength)  // the 90-day gap is left out
    }

    @Test fun `manual lengths are used when auto-adjust is off`() {
        val m = CycleCalculator.model(history, CycleSettings(autoAdjust = false, cycleLength = 30, periodLength = 4), today)
        assertEquals(30, m.cycleLength)
        assertEquals(4, m.periodLength)
        assertEquals(LocalDate.of(2026, 12, 12), m.nextStart)
    }

    @Test fun `a single missed day inside a period is merged`() {
        val d = LocalDate.of(2026, 10, 1)
        val m = CycleCalculator.model(setOf(d, d.plusDays(1), d.plusDays(3)), CycleSettings(), today)
        assertEquals(1, m.periods.size)
        assertEquals(4, m.periods.single().length)
    }

    @Test fun `no data means no predictions`() {
        val m = CycleCalculator.model(emptySet(), CycleSettings(), today)
        assertFalse(m.hasData)
        assertNull(m.nextStart)
        assertNull(CycleCalculator.dayInfo(today, m).phase)
    }

    @Test fun `wishes read well with and without a name`() {
        val (withName, _) = Wishes.wish(Phase.PERIOD, LocalDate.ofEpochDay(0), "Better")
        assertEquals("Be gentle with yourself, Better 🤍", withName)
        val (noName, _) = Wishes.wish(Phase.PERIOD, LocalDate.ofEpochDay(0), "")
        assertEquals("Be gentle with yourself 🤍", noName)
        assertEquals("Mid-cycle", Wishes.label(Phase.FERTILE, showFertility = false))
    }
}
