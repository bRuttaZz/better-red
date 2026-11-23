package site.brutt.betterred.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

/** Manual values, used until there is enough history (or when auto-adjust is off). */
data class CycleSettings(
    val autoAdjust: Boolean = true,
    val cycleLength: Int = 28,
    val periodLength: Int = 5,
    val lutealLength: Int = 14,
) {
    companion object {
        val CYCLE_RANGE = 18..50
        val PERIOD_RANGE = 1..10
        val LUTEAL_RANGE = 9..18
    }
}

data class Period(val start: LocalDate, val end: LocalDate) {
    val length: Int get() = daysBetween(start, end) + 1
}

/** One cycle: from a period start up to (not including) the next start. */
data class Cycle(
    val start: LocalDate,
    val next: LocalDate,
    val ovulation: LocalDate,
    val fertileStart: LocalDate,
    val fertileEnd: LocalDate,
    val predicted: Boolean,
) {
    val length: Int get() = daysBetween(start, next)
}

data class CycleModel(
    val today: LocalDate,
    val periodDays: Set<LocalDate>,
    val periods: List<Period>,
    /** Days between consecutive period starts, oldest first. */
    val gaps: List<Int>,
    val cycleLength: Int,
    val periodLength: Int,
    val lutealLength: Int,
    /** Expected next period start. If she is late, this is today. */
    val nextStart: LocalDate?,
    val daysLate: Int,
    val predictedStarts: List<LocalDate>,
    val cycles: List<Cycle>,
) {
    val hasData: Boolean get() = periods.isNotEmpty()
    val lastStart: LocalDate? get() = periods.lastOrNull()?.start
}

enum class Phase { PERIOD, PERIOD_EXPECTED, FOLLICULAR, FERTILE, OVULATION, LUTEAL }

enum class Chance { LOW, MEDIUM, HIGH }

data class DayInfo(
    val date: LocalDate,
    val isPeriod: Boolean,
    val isPredicted: Boolean,
    val isFertile: Boolean,
    val isOvulation: Boolean,
    val phase: Phase?,
    val chance: Chance?,
    val cycleDay: Int?,
    val cycle: Cycle?,
)

/**
 * Pure prediction math (no Android code, so it is unit-tested on the JVM).
 *
 * - A period is a run of logged flow days; gaps of one empty day are merged.
 * - Cycle length = average of the last 6 start-to-start gaps between 18 and 50 days.
 * - Period length = average of the last 6 finished periods.
 * - Ovulation = next period start − luteal phase (14 days by default).
 * - Fertile window = ovulation − 5 days … ovulation + 1 day.
 */
object CycleCalculator {
    private const val HISTORY = 6
    private const val FERTILE_BEFORE = 5
    private const val FERTILE_AFTER = 1
    private const val PREDICTED_CYCLES = 13

    fun model(periodDays: Set<LocalDate>, settings: CycleSettings, today: LocalDate): CycleModel {
        val periods = mutableListOf<Period>()
        for (day in periodDays.sorted()) {
            val last = periods.lastOrNull()
            if (last != null && daysBetween(last.end, day) <= 2) periods[periods.lastIndex] = last.copy(end = day)
            else periods += Period(day, day)
        }

        val starts = periods.map { it.start }
        val gaps = starts.zipWithNext { a, b -> daysBetween(a, b) }
        val recentGaps = gaps.filter { it in CycleSettings.CYCLE_RANGE }.takeLast(HISTORY)
        // A period that is still going on (ends today or yesterday) would drag the average down.
        val finished = periods.filter { daysBetween(it.end, today) > 1 }.ifEmpty { periods }
        val recentLengths = finished.takeLast(HISTORY).map { it.length }

        val cycleLength = if (settings.autoAdjust && recentGaps.isNotEmpty()) recentGaps.average().roundToInt() else settings.cycleLength
        val periodLength = if (settings.autoAdjust && recentLengths.isNotEmpty()) recentLengths.average().roundToInt() else settings.periodLength
        val luteal = settings.lutealLength

        val last = starts.lastOrNull()
        var next = last?.plusDays(cycleLength.toLong())
        var late = 0
        if (next != null && next.isBefore(today)) {
            late = daysBetween(next, today)
            next = today // overdue: expect it any day now
        }

        val predicted = if (next == null) emptyList()
        else List(PREDICTED_CYCLES) { i -> next.plusDays(i.toLong() * cycleLength) }

        val all = starts + predicted
        val cycles = all.zipWithNext().mapIndexed { i, (start, end) ->
            // For the current cycle, being late must not move ovulation (it already happened).
            val ovulationBase = if (i == starts.lastIndex) start.plusDays(cycleLength.toLong()) else end
            val ovulation = ovulationBase.minusDays(luteal.toLong())
            Cycle(
                start = start,
                next = end,
                ovulation = ovulation,
                fertileStart = ovulation.minusDays(FERTILE_BEFORE.toLong()),
                fertileEnd = ovulation.plusDays(FERTILE_AFTER.toLong()),
                predicted = i >= starts.size,
            )
        }

        return CycleModel(
            today = today,
            periodDays = periodDays,
            periods = periods,
            gaps = gaps,
            cycleLength = cycleLength,
            periodLength = periodLength,
            lutealLength = luteal,
            nextStart = next,
            daysLate = late,
            predictedStarts = predicted,
            cycles = cycles,
        )
    }

    fun dayInfo(date: LocalDate, m: CycleModel): DayInfo {
        val isPeriod = date in m.periodDays
        val isPredicted = !isPeriod && !date.isBefore(m.today) &&
            m.predictedStarts.any { daysBetween(it, date) in 0 until m.periodLength }

        val cycle = m.cycles.firstOrNull { !date.isBefore(it.start) && date.isBefore(it.next) }
            ?: return DayInfo(
                date, isPeriod, isPredicted, isFertile = false, isOvulation = false,
                phase = if (isPeriod) Phase.PERIOD else null,
                chance = if (isPeriod) Chance.LOW else null,
                cycleDay = null, cycle = null,
            )

        val k = daysBetween(cycle.ovulation, date) // days after ovulation (negative = before)
        val blocked = isPeriod || isPredicted
        val isOvulation = k == 0 && !blocked
        val isFertile = k in -FERTILE_BEFORE..FERTILE_AFTER && !blocked
        val chance = when {
            blocked -> Chance.LOW
            k in -2..0 -> Chance.HIGH
            k in -FERTILE_BEFORE..FERTILE_AFTER -> Chance.MEDIUM
            else -> Chance.LOW
        }
        val phase = when {
            isPeriod -> Phase.PERIOD
            isPredicted -> Phase.PERIOD_EXPECTED
            isOvulation -> Phase.OVULATION
            isFertile -> Phase.FERTILE
            k < -FERTILE_BEFORE -> Phase.FOLLICULAR
            else -> Phase.LUTEAL
        }
        return DayInfo(date, isPeriod, isPredicted, isFertile, isOvulation, phase, chance, daysBetween(cycle.start, date) + 1, cycle)
    }

    /** Ovulation that is still ahead (this cycle's, or the next one's). */
    fun upcomingOvulation(m: CycleModel): LocalDate? {
        val current = dayInfo(m.today, m).cycle
        if (current != null && !m.today.isAfter(current.ovulation)) return current.ovulation
        return m.cycles.firstOrNull { it.start == m.nextStart }?.ovulation
    }
}

internal fun daysBetween(a: LocalDate, b: LocalDate): Int = ChronoUnit.DAYS.between(a, b).toInt()
