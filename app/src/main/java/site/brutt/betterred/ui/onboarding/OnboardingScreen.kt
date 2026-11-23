package site.brutt.betterred.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.brutt.betterred.Words
import site.brutt.betterred.domain.History
import site.brutt.betterred.domain.CycleSettings
import site.brutt.betterred.ui.brand.BrandMark
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * First launch: welcome → who is this for → last period + usual lengths.
 * The partner option is shown but not available yet (pairing comes later).
 */
@Composable
fun OnboardingScreen(
    onFinish: (name: String, lastPeriodStart: LocalDate?, cycleLength: Int, periodLength: Int) -> Unit,
    onImport: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var cycle by rememberSaveable { mutableIntStateOf(28) }
    var period by rememberSaveable { mutableIntStateOf(5) }
    var lastStartEpoch by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler(enabled = step > 0) { step-- }

    // Surface sets the text color for light/dark (without it, plain Text defaults to black).
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxSize()) {
    Column(
        Modifier.fillMaxSize().safeDrawingPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        if (step > 0) {
            IconButton(onClick = { step-- }, modifier = Modifier.padding(start = 0.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, Words.Welcome.BACK)
            }
            LinearProgressIndicator(
                progress = { step / 2f }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                drawStopIndicator = {},
            )
        }
        when (step) {
            0 -> Welcome(onNext = { step = 1 }, onImport = onImport)
            1 -> WhoFor(onNext = { step = 2 })
            else -> Setup(
                name = name, onName = { name = it.take(24) },
                lastStart = lastStartEpoch?.let(LocalDate::ofEpochDay), onLastStart = { lastStartEpoch = it?.toEpochDay() },
                cycle = cycle, onCycle = { cycle = it }, period = period, onPeriod = { period = it },
                onDone = { onFinish(name, lastStartEpoch?.let(LocalDate::ofEpochDay), cycle, period) },
                onSkip = { onFinish(name, null, cycle, period) },
            )
        }
    }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit, onImport: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(56.dp))
        BrandMark(height = 104.dp, animate = true)
        Spacer(Modifier.height(32.dp))
        Text(Words.APP_NAME, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            Words.Welcome.TAGLINE,
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(64.dp))
        Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(Words.Welcome.GET_STARTED) }
        TextButton(onClick = onImport, modifier = Modifier.padding(top = 8.dp)) {
            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(Words.Welcome.RESTORE)
        }
    }
}

@Composable
private fun WhoFor(onNext: () -> Unit) {
    Text(Words.Welcome.WHO_TITLE, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    Text(Words.Welcome.WHO_SUB, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    Option(Icons.Rounded.WaterDrop, Words.Welcome.FOR_ME, Words.Welcome.FOR_ME_SUB, selected = true, enabled = true)
    Option(Icons.Rounded.Favorite, Words.Welcome.FOR_PARTNER, Words.Welcome.FOR_PARTNER_SUB, selected = false, enabled = false)
    Spacer(Modifier.height(32.dp))
    Button(onClick = onNext, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(Words.Welcome.CONTINUE) }
}

@Composable
private fun Option(icon: ImageVector, title: String, sub: String, selected: Boolean, enabled: Boolean) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = if (selected) cs.primaryContainer else cs.surfaceContainerLow,
        contentColor = if (selected) cs.onPrimaryContainer else cs.onSurface,
        border = BorderStroke(1.5.dp, if (selected) cs.primary else cs.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(if (selected) cs.primary else cs.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = if (selected) cs.onPrimary else cs.onSurfaceVariant) }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = if (enabled) androidx.compose.ui.graphics.Color.Unspecified else cs.onSurfaceVariant)
                Text(sub, style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Setup(
    name: String, onName: (String) -> Unit,
    lastStart: LocalDate?, onLastStart: (LocalDate?) -> Unit,
    cycle: Int, onCycle: (Int) -> Unit,
    period: Int, onPeriod: (Int) -> Unit,
    onDone: () -> Unit, onSkip: () -> Unit,
) {
    var picking by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme

    Text(Words.Welcome.SETUP_TITLE, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 12.dp))
    Text(Words.Welcome.SETUP_SUB, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)

    OutlinedTextField(
        value = name, onValueChange = onName, singleLine = true,
        label = { Text(Words.Welcome.NAME_LABEL) }, supportingText = { Text(Words.Welcome.NAME_HELP) },
        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
    )

    Surface(
        onClick = { picking = true }, shape = RoundedCornerShape(16.dp), color = cs.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = cs.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(Words.Welcome.LAST_PERIOD, style = MaterialTheme.typography.bodyLarge)
                Text(
                    lastStart?.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())) ?: Words.Welcome.PICK_DATE,
                    style = MaterialTheme.typography.bodyMedium, color = if (lastStart == null) cs.onSurfaceVariant else cs.primary,
                )
            }
        }
    }

    Stepper(Words.Welcome.CYCLE_LENGTH, Words.Welcome.CYCLE_LENGTH_SUB, cycle, CycleSettings.CYCLE_RANGE, onCycle)
    HorizontalDivider(color = cs.outlineVariant)
    Stepper(Words.Welcome.PERIOD_LENGTH, Words.Welcome.PERIOD_LENGTH_SUB, period, CycleSettings.PERIOD_RANGE, onPeriod)

    Spacer(Modifier.height(28.dp))
    Button(onClick = onDone, enabled = lastStart != null, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(Words.Welcome.DONE) }
    TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text(Words.Welcome.SKIP) }

    if (picking) {
        val todayMillis = LocalDate.now().atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val firstMillis = History.firstDay(LocalDate.now()).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = lastStart?.atStartOfDay()?.toInstant(ZoneOffset.UTC)?.toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis in firstMillis..todayMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onLastStart(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    picking = false
                }) { Text(Words.Profile.OK) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text(Words.Profile.CANCEL) } },
        ) { DatePicker(state = pickerState) }
    }
}

@Composable
private fun Stepper(title: String, sub: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            OutlinedIconButton(onClick = { onChange(value - 1) }, enabled = value > range.first, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Remove, Words.Profile.DECREASE, Modifier.size(18.dp))
            }
            Text("$value", Modifier.width(40.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
            OutlinedIconButton(onClick = { onChange(value + 1) }, enabled = value < range.last, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Add, Words.Profile.INCREASE, Modifier.size(18.dp))
            }
        }
    }
}
