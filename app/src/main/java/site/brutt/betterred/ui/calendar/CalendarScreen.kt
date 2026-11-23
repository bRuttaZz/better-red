package site.brutt.betterred.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.brutt.betterred.Words
import site.brutt.betterred.domain.CycleCalculator
import site.brutt.betterred.domain.CycleModel
import site.brutt.betterred.domain.DayInfo
import site.brutt.betterred.domain.DayLog
import site.brutt.betterred.domain.Mood
import site.brutt.betterred.domain.Phase
import site.brutt.betterred.domain.Wishes
import site.brutt.betterred.domain.daysBetween
import site.brutt.betterred.ui.AppState
import site.brutt.betterred.ui.Avatar
import site.brutt.betterred.ui.brand.BrandMark
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private val longDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
private val shortDate = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

@Composable
fun CalendarScreen(
    state: AppState,
    today: LocalDate,
    onTogglePeriod: (LocalDate) -> Unit,
    onMood: (LocalDate, Mood) -> Unit,
    onSaveLog: (DayLog) -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefs = state.prefs
    val model = remember(state.periodDays, prefs.cycle, today) {
        CycleCalculator.model(state.periodDays, prefs.cycle, today)
    }
    var monthEpoch by rememberSaveable { mutableLongStateOf(today.withDayOfMonth(1).toEpochDay()) }
    var selectedEpoch by rememberSaveable { mutableLongStateOf(today.toEpochDay()) }
    var editing by remember { mutableStateOf(false) }
    val month = LocalDate.ofEpochDay(monthEpoch)
    val selected = LocalDate.ofEpochDay(selectedEpoch)
    val selectedLog = state.logs[selected] ?: DayLog(selected)
    val goToday = { selectedEpoch = today.toEpochDay(); monthEpoch = today.withDayOfMonth(1).toEpochDay() }

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)
    ) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            BrandMark(height = 26.dp)
            Spacer(Modifier.width(12.dp))
            Text(Words.Calendar.TITLE, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = goToday) { Icon(Icons.Rounded.Today, contentDescription = Words.Calendar.JUMP_TO_TODAY) }
            Spacer(Modifier.width(4.dp))
            Avatar(prefs.avatar, prefs.name, 36.dp, onClick = onOpenProfile)
        }

        // Month switcher: < November 2026 >
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { monthEpoch = month.minusMonths(1).toEpochDay() }) {
                Icon(Icons.Rounded.ChevronLeft, contentDescription = Words.Calendar.PREV_MONTH)
            }
            Text(
                month.format(monthTitle), style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, modifier = Modifier.width(170.dp)
            )
            IconButton(onClick = { monthEpoch = month.plusMonths(1).toEpochDay() }) {
                Icon(Icons.Rounded.ChevronRight, contentDescription = Words.Calendar.NEXT_MONTH)
            }
        }

        MonthGrid(
            month = month, model = model, logs = state.logs, today = today, selected = selected,
            showFertility = prefs.showFertility, showSex = prefs.trackSex, onPick = { selectedEpoch = it.toEpochDay() },
        )

        Spacer(Modifier.height(12.dp))
        DayCard(
            info = CycleCalculator.dayInfo(selected, model), model = model, isToday = selected == today,
            name = prefs.name, showFertility = prefs.showFertility, onBackToToday = goToday,
        )

        if (!selected.isAfter(today)) {
            Spacer(Modifier.height(10.dp))
            LogCard(log = selectedLog, isToday = selected == today, showSex = prefs.trackSex, onMood = { onMood(selected, it) })
        }

        if (model.hasData) {
            Spacer(Modifier.height(10.dp))
            StatRow(model, prefs.showFertility)
        }

        Spacer(Modifier.height(16.dp))
        if (selected.isAfter(today)) {
            Text(
                Words.Calendar.FUTURE_NOTE, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val isPeriod = selectedLog.flow != null
                FilledTonalButton(onClick = { onTogglePeriod(selected) }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(if (isPeriod) Icons.Rounded.Remove else Icons.Rounded.Add, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isPeriod) Words.Calendar.NOT_PERIOD_DAY else Words.Calendar.PERIOD_DAY, maxLines = 1)
                }
                Button(onClick = { editing = true }, modifier = Modifier.weight(1f).height(56.dp)) {
                    Icon(Icons.Rounded.EditNote, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (selectedLog.isEmpty) Words.Calendar.BLOODY_NOTE else Words.Calendar.EDIT_NOTE, maxLines = 1)
                }
            }
        }
    }

    if (editing) {
        LogSheet(initial = selectedLog, showSex = prefs.trackSex, onDismiss = { editing = false }, onSave = { onSaveLog(it); editing = false })
    }
}

// ---------------------------------------------------------------- month grid

@Composable
private fun MonthGrid(
    month: LocalDate,
    model: CycleModel,
    logs: Map<LocalDate, DayLog>,
    today: LocalDate,
    selected: LocalDate,
    showFertility: Boolean,
    showSex: Boolean,
    onPick: (LocalDate) -> Unit,
) {
    val firstDayOfWeek = DayOfWeek.SUNDAY
    val leading = Math.floorMod(month.dayOfWeek.value - firstDayOfWeek.value, 7)
    val days = month.lengthOfMonth()
    val cells: List<LocalDate?> = List(leading) { null } + List(days) { month.plusDays(it.toLong()) }

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Row {
                repeat(7) { i ->
                    Text(
                        firstDayOfWeek.plus(i.toLong()).getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f).padding(bottom = 6.dp)
                    )
                }
            }
            cells.chunked(7).forEach { week ->
                Row {
                    for (i in 0 until 7) {
                        val date = week.getOrNull(i)
                        Box(Modifier.weight(1f).aspectRatio(1f)) {
                            if (date != null) DayCell(
                                date = date, info = CycleCalculator.dayInfo(date, model),
                                isToday = date == today, isSelected = date == selected,
                                hasDetails = logs[date]?.hasDetails == true, showFertility = showFertility,
                                hadSex = showSex && logs[date]?.sex != null,
                                onClick = { onPick(date) },
                            )
                        }
                    }
                }
            }
            Legend(showFertility)
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    info: DayInfo,
    isToday: Boolean,
    isSelected: Boolean,
    hasDetails: Boolean,
    showFertility: Boolean,
    hadSex: Boolean,
    onClick: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val fertile = showFertility && (info.isFertile || info.isOvulation)
    val ovulation = showFertility && info.isOvulation
    val fill = when {
        info.isPeriod -> cs.primary
        fertile -> cs.tertiaryContainer.copy(alpha = 0.55f)
        else -> Color.Transparent
    }
    val textColor = when {
        info.isPeriod -> cs.onPrimary
        info.isPredicted -> cs.primary
        else -> cs.onSurface
    }
    // Only the inner circle is clipped (for the tap ripple); the little markers sit outside it.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                .then(if (isSelected) Modifier.border(2.dp, cs.onSurface, CircleShape) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(fill)
                    .then(if (ovulation) Modifier.border(2.dp, cs.tertiary, CircleShape) else Modifier)
                    .then(if (info.isPredicted) Modifier.dashedCircle(cs.primary) else Modifier),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${date.dayOfMonth}", color = textColor, fontSize = 14.sp,
                    fontWeight = when {
                        isToday -> FontWeight.ExtraBold
                        info.isPeriod || info.isPredicted || ovulation -> FontWeight.SemiBold
                        else -> FontWeight.Normal
                    },
                )
            }
        }
        if (isToday) Box(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp).size(14.dp, 3.dp)
                .clip(RoundedCornerShape(2.dp)).background(cs.onSurface)
        )
        if (hadSex) Icon(
            Icons.Rounded.Favorite, contentDescription = Words.Calendar.SEX_LOGGED, tint = cs.primary,
            modifier = Modifier.align(Alignment.TopStart).padding(top = 1.dp, start = 1.dp).size(12.dp),
        )
        if (hasDetails) Box(
            Modifier.align(Alignment.TopEnd).padding(top = 3.dp, end = 3.dp).size(7.dp)
                .clip(CircleShape).background(cs.onSurfaceVariant)
        )
    }
}

private fun Modifier.dashedCircle(color: Color) = drawBehind {
    val w = 1.5.dp.toPx()
    drawCircle(
        color = color, radius = size.minDimension / 2 - w / 2,
        style = Stroke(width = w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
    )
}

@Composable
private fun Legend(showFertility: Boolean) {
    val cs = MaterialTheme.colorScheme
    @Composable
    fun Item(label: String, dot: Modifier) = Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).then(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
    }
    Row(Modifier.padding(start = 6.dp, top = 10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Item(Words.Calendar.LEGEND_PERIOD, Modifier.clip(CircleShape).background(cs.primary))
        Item(Words.Calendar.LEGEND_EXPECTED, Modifier.dashedCircle(cs.primary))
        if (showFertility) {
            Item(Words.Calendar.LEGEND_FERTILE, Modifier.clip(CircleShape).background(cs.tertiaryContainer))
            Item(Words.Calendar.LEGEND_OVULATION, Modifier.clip(CircleShape).background(cs.tertiaryContainer).border(1.5.dp, cs.tertiary, CircleShape))
        }
    }
}

// ---------------------------------------------------------------- her day card

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DayCard(
    info: DayInfo,
    model: CycleModel,
    isToday: Boolean,
    name: String,
    showFertility: Boolean,
    onBackToToday: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val label = Wishes.label(info.phase, showFertility)
    // The wish is only for today. Other days just show the date and the phase.
    val (eyebrow, title, body) = when {
        !isToday -> Triple(info.date.format(longDate).uppercase(), label ?: Words.Calendar.NO_PREDICTION, null)
        !model.hasData -> {
            val (t, b) = Wishes.wish(null, info.date, name)
            Triple(Wishes.greeting().uppercase(), t, b)
        }
        else -> {
            val (t, b) = Wishes.wish(info.phase, info.date, name)
            Triple("${Wishes.greeting()} · ${info.date.format(longDate)}".uppercase(), t, b)
        }
    }

    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (info.cycle != null) {
                    CycleRing(info, model, showFertility)
                    Spacer(Modifier.width(14.dp))
                }
                Column {
                    Text(eyebrow, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, letterSpacing = 0.6.sp)
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 3.dp))
                }
            }
            if (body != null) Text(body, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))

            val showChip = isToday && label != null
            val showChance = showFertility && info.chance != null && model.hasData
            if (showChip || showChance || !isToday) {
                FlowRow(
                    Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showChip) PhaseChip(label!!, info, showFertility)
                    if (showChance) Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Egg, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(Wishes.chance(info.chance!!), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                    }
                    if (!isToday) TextButton(onClick = onBackToToday, modifier = Modifier.height(32.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(Words.Calendar.BACK_TO_TODAY)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhaseChip(label: String, info: DayInfo, showFertility: Boolean) {
    val cs = MaterialTheme.colorScheme
    val dot = when {
        info.phase == Phase.PERIOD || info.phase == Phase.PERIOD_EXPECTED -> cs.primary
        showFertility && (info.isFertile || info.isOvulation) -> cs.tertiary
        else -> cs.outline
    }
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(cs.surfaceContainerHighest).padding(start = 10.dp, end = 12.dp).height(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** Ring showing where today sits in the cycle: period arc, (optional) fertile arc, and a dot for today. */
@Composable
fun CycleRing(info: DayInfo, model: CycleModel, showFertility: Boolean, ringSize: Dp = 72.dp) {
    val cycle = info.cycle ?: return
    val cs = MaterialTheme.colorScheme
    val len = cycle.length.toFloat()
    val fertileFrom = daysBetween(cycle.start, cycle.fertileStart).toFloat()
    val fertileTo = daysBetween(cycle.start, cycle.fertileEnd) + 1f
    val periodTo = min(model.periodLength.toFloat(), len)
    val day = (info.cycleDay ?: 1) - 0.5f

    Box(Modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = size.minDimension * 0.11f
            val inset = stroke / 2 + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            fun arc(from: Float, to: Float, color: Color) = drawArc(
                color = color, startAngle = -90f + from / len * 360f, sweepAngle = (to - from) / len * 360f,
                useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke),
            )
            arc(0f, len, cs.surfaceContainerHighest)
            arc(0f, periodTo, cs.primary)
            if (showFertility) arc(fertileFrom, fertileTo, cs.tertiary.copy(alpha = 0.55f))

            val angle = day / len * 2 * PI
            val r = arcSize.width / 2
            val knob = Offset(center.x + r * sin(angle).toFloat(), center.y - r * cos(angle).toFloat())
            drawCircle(cs.surfaceContainer, radius = stroke * 0.75f + 2.5.dp.toPx(), center = knob)
            drawCircle(cs.onSurface, radius = stroke * 0.75f, center = knob)
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(Words.Calendar.RING_DAY, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            Text("${info.cycleDay}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ---------------------------------------------------------------- mood + summary

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LogCard(log: DayLog, isToday: Boolean, showSex: Boolean, onMood: (Mood) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (isToday) Words.Calendar.HOW_ARE_YOU else Words.Calendar.notesFor(log.date.format(shortDate)),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(10.dp))
            MoodRow(selected = log.mood, onPick = onMood)
            val chips = buildList {
                log.flow?.let { add(Words.Calendar.flowChip(it.label)) }
                if (showSex) log.sex?.let { add(Words.Calendar.sexChip(it.label)) }
                log.symptoms.forEach { add(it.label) }
            }
            if (chips.isNotEmpty()) FlowRow(
                Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                chips.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
            }
            if (log.note.isNotBlank()) Text(
                "“${log.note}”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic,
                color = cs.onSurfaceVariant, modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
fun MoodRow(selected: Mood?, onPick: (Mood) -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Mood.entries.forEach { mood ->
            val on = mood == selected
            Surface(
                onClick = { onPick(mood) },
                shape = RoundedCornerShape(16.dp),
                color = if (on) cs.primaryContainer else Color.Transparent,
                contentColor = if (on) cs.onPrimaryContainer else cs.onSurfaceVariant,
                border = if (on) null else BorderStroke(1.dp, cs.outlineVariant),
                modifier = Modifier.weight(1f),
            ) {
                Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(mood.emoji, fontSize = 22.sp)
                    Text(mood.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- stats

@Composable
private fun StatRow(model: CycleModel, showFertility: Boolean) {
    val today = model.today
    val todayInfo = CycleCalculator.dayInfo(today, model)
    val (periodBig, periodSmall) = when {
        model.daysLate > 0 -> Words.Calendar.daysLate(model.daysLate) to
            Words.Calendar.expectedOn(today.minusDays(model.daysLate.toLong()).format(shortDate))
        todayInfo.isPeriod -> {
            val run = model.periods.last()
            Words.Calendar.periodDay(daysBetween(run.start, today) + 1) to Words.Calendar.OF_YOUR_PERIOD
        }
        else -> inDays(daysBetween(today, model.nextStart!!)) to Words.Calendar.nextPeriodOn(model.nextStart.format(shortDate))
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Stat(Icons.Rounded.WaterDrop, periodBig, periodSmall, tertiary = false, modifier = Modifier.weight(1f))
        if (showFertility) {
            val ov = CycleCalculator.upcomingOvulation(model)
            Stat(
                Icons.Rounded.Egg, ov?.let { inDays(daysBetween(today, it)) } ?: "—",
                ov?.let { Words.Calendar.ovulationOn(it.format(shortDate)) } ?: Words.Calendar.OVULATION,
                tertiary = true, modifier = Modifier.weight(1f),
            )
        } else {
            Stat(
                Icons.Rounded.Autorenew, todayInfo.cycleDay?.let { Words.Calendar.cycleDay(it) } ?: "—",
                Words.Calendar.ofCycle(model.cycleLength), tertiary = true, modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, big: String, small: String, tertiary: Boolean, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(20.dp), color = cs.surfaceContainer, modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(34.dp).clip(CircleShape)
                    .background(if (tertiary) cs.tertiaryContainer else cs.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, Modifier.size(20.dp), tint = if (tertiary) cs.onTertiaryContainer else cs.onPrimaryContainer)
            }
            Spacer(Modifier.height(8.dp))
            Text(big, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(small, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
        }
    }
}

private fun inDays(n: Int) = Words.Calendar.inDays(n)
