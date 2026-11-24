package site.brutt.betterred.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import site.brutt.betterred.Words
import site.brutt.betterred.ui.openExternally
import site.brutt.betterred.domain.History
import site.brutt.betterred.ui.theme.RedDarkScheme
import site.brutt.betterred.ui.theme.RedLightScheme
import site.brutt.betterred.ui.theme.Tones
import site.brutt.betterred.ui.theme.isDark
import site.brutt.betterred.ui.theme.toneScheme
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoGraph
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.VolunteerActivism
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import site.brutt.betterred.data.Accent
import site.brutt.betterred.data.ThemeMode
import site.brutt.betterred.domain.CycleCalculator
import site.brutt.betterred.domain.CycleSettings
import site.brutt.betterred.ui.AppState
import site.brutt.betterred.ui.AppViewModel
import site.brutt.betterred.ui.Avatar
import site.brutt.betterred.ui.AvatarPickerDialog
import site.brutt.betterred.ui.theme.supportsDynamicColor
import java.time.LocalDate

@Composable
fun ProfileScreen(state: AppState, vm: AppViewModel, today: LocalDate, modifier: Modifier = Modifier) {
    val prefs = state.prefs
    val model = remember(state.periodDays, prefs.cycle, today) { CycleCalculator.model(state.periodDays, prefs.cycle, today) }
    val cs = MaterialTheme.colorScheme

    var renaming by remember { mutableStateOf(false) }
    var pickingAvatar by remember { mutableStateOf(false) }
    var showLicenses by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
    val context = LocalContext.current
    // Read from the installed app, so it always matches versionName in app/build.gradle.kts.
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    var confirmImport by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.sqlite3")) { uri ->
        uri?.let(vm::exportTo)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importFrom)
    }

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)
    ) {
        // ---- header
        Column(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Avatar(prefs.avatar, prefs.name, 80.dp, onClick = { pickingAvatar = true })
                // small edit badge
                Box(
                    Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(cs.primary),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Edit, Words.Profile.CHANGE_ICON, Modifier.size(14.dp), tint = cs.onPrimary) }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                Text(prefs.name.ifBlank { Words.Profile.NO_NAME }, style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = { renaming = true }) { Icon(Icons.Rounded.Edit, Words.Profile.EDIT_NAME, Modifier.size(20.dp)) }
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceContainerHigh).padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.CloudOff, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
                Text(Words.Profile.LOCAL_ONLY, style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
        }

        // ---- cycle (on a partner's phone these are her numbers, set by her phone: not shown)
        if (!state.isPartner) {
            SectionTitle(Words.Profile.SECTION_CYCLE)
            Group(
                {
                    Column(Modifier.padding(16.dp)) {
                        Row {
                            Kpi("${model.cycleLength}", Words.Profile.AVG_CYCLE, Modifier.weight(1f))
                            Kpi("${model.periodLength}", Words.Profile.AVG_PERIOD, Modifier.weight(1f))
                            Kpi("${model.periods.size}", Words.Profile.PERIODS_LOGGED, Modifier.weight(1f))
                        }
                        CycleBars(model.gaps.filter { it in CycleSettings.CYCLE_RANGE }.takeLast(6))
                    }
                },
                {
                    SwitchRow(
                        Icons.Rounded.Egg, Words.Profile.SHOW_FERTILITY, Words.Profile.SHOW_FERTILITY_SUB,
                        prefs.showFertility, vm::setShowFertility,
                    )
                },
                {
                    SwitchRow(
                        Icons.Rounded.Favorite, Words.Profile.LOG_SEX, Words.Profile.LOG_SEX_SUB,
                        prefs.trackSex, vm::setTrackSex,
                    )
                },
                {
                    SwitchRow(
                        Icons.Rounded.AutoGraph, Words.Profile.AUTO_ADJUST, Words.Profile.AUTO_ADJUST_SUB,
                        prefs.cycle.autoAdjust, vm::setAutoAdjust,
                    )
                },
                {
                    val auto = prefs.cycle.autoAdjust
                    StepperRow(
                        Icons.Rounded.Autorenew, Words.Profile.CYCLE_LENGTH, if (auto) Words.Profile.CALCULATED else Words.Profile.SET_BY_YOU,
                        if (auto) model.cycleLength else prefs.cycle.cycleLength, CycleSettings.CYCLE_RANGE, !auto, vm::setCycleLength,
                    )
                },
                {
                    val auto = prefs.cycle.autoAdjust
                    StepperRow(
                        Icons.Rounded.WaterDrop, Words.Profile.PERIOD_LENGTH, if (auto) Words.Profile.CALCULATED else Words.Profile.SET_BY_YOU,
                        if (auto) model.periodLength else prefs.cycle.periodLength, CycleSettings.PERIOD_RANGE, !auto, vm::setPeriodLength,
                    )
                },
                {
                    StepperRow(
                        Icons.Rounded.NightsStay, Words.Profile.LUTEAL, Words.Profile.LUTEAL_SUB,
                        prefs.cycle.lutealLength, CycleSettings.LUTEAL_RANGE, true, vm::setLutealLength,
                    )
                },
            )
        }

        // ---- appearance
        SectionTitle(Words.Profile.SECTION_LOOK)
        Group(
            {
                Column(Modifier.padding(16.dp)) {
                    Text(Words.Profile.THEME, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    Segmented(
                        ThemeMode.entries, prefs.theme,
                        {
                            when (it) {
                                ThemeMode.SYSTEM -> Words.Profile.THEME_SYSTEM
                                ThemeMode.LIGHT -> Words.Profile.THEME_LIGHT
                                ThemeMode.DARK -> Words.Profile.THEME_DARK
                            }
                        },
                        vm::setTheme,
                    )
                }
            },
            {
                Column(Modifier.padding(16.dp)) {
                    Text(Words.Profile.COLOR, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (supportsDynamicColor) Words.Profile.COLOR_DYNAMIC_SUB else Words.Profile.COLOR_DYNAMIC_UNAVAILABLE,
                        style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(14.dp))
                    ColorSwatches(selected = prefs.accent, dark = prefs.theme.isDark(), onPick = vm::setAccent)
                }
            },
        )

        // ---- data (a partner's phone only has her shared days: no backups there)
        SectionTitle(Words.Profile.SECTION_DATA)
        if (state.isPartner) Group(
            {
                ClickRow(Icons.Rounded.DeleteForever, Words.Profile.DELETE, Words.Profile.DELETE_SUB, danger = true) {
                    confirmDelete = true
                }
            },
        ) else Group(
            {
                ClickRow(Icons.Rounded.Upload, Words.Profile.EXPORT, Words.Profile.EXPORT_SUB) {
                    exportLauncher.launch(Words.Profile.exportFileName(today.toString()))
                }
            },
            {
                ClickRow(Icons.Rounded.Download, Words.Profile.IMPORT, Words.Profile.IMPORT_SUB) {
                    confirmImport = true
                }
            },
            { InfoRow(Icons.Rounded.History, Words.History.profileTitle(History.YEARS), Words.History.PROFILE_SUB) },
            {
                ClickRow(Icons.Rounded.DeleteForever, Words.Profile.DELETE, Words.Profile.DELETE_SUB, danger = true) {
                    confirmDelete = true
                }
            },
        )

        SectionTitle(Words.Profile.SECTION_ABOUT)
        Group(
            { InfoRow(Icons.Rounded.Info, Words.Profile.version(appVersion), Words.Profile.VERSION_SUB) },
            { InfoRow(Icons.Rounded.Gavel, Words.Profile.LICENSE, Words.Profile.LICENSE_SUB) },
            { ClickRow(Icons.Rounded.Code, Words.Profile.OSS, Words.Profile.OSS_SUB) { showLicenses = true } },
            { ClickRow(Icons.Rounded.VolunteerActivism, Words.Profile.CREDITS, Words.Profile.CREDITS_SUB) { showCredits = true } },
            { InfoRow(Icons.Rounded.HealthAndSafety, Words.Profile.NOT_MEDICAL, Words.Profile.NOT_MEDICAL_SUB) },
        )
    }

    if (showCredits) CreditsDialog(onDismiss = { showCredits = false })

    if (showLicenses) {
        AlertDialog(
            onDismissRequest = { showLicenses = false },
            title = { Text(Words.Profile.OSS) },
            text = {
                Text(
                    Words.Profile.OSS_TEXT, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                )
            },
            confirmButton = { TextButton(onClick = { showLicenses = false }) { Text(Words.Profile.DONE) } },
        )
    }
    if (pickingAvatar) {
        AvatarPickerDialog(
            current = prefs.avatar, name = prefs.name,
            onPick = { vm.setAvatar(it) }, onDismiss = { pickingAvatar = false },
        )
    }
    if (renaming) {
        var text by remember { mutableStateOf(prefs.name) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(Words.Profile.NAME_TITLE) },
            text = {
                Column {
                    Text(Words.Profile.NAME_BODY, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(text, { text = it.take(24) }, singleLine = true, placeholder = { Text(Words.Profile.NAME_HINT) })
                }
            },
            confirmButton = { TextButton(onClick = { vm.setName(text); renaming = false }) { Text(Words.Profile.SAVE) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(Words.Profile.CANCEL) } },
        )
    }
    if (confirmImport) {
        AlertDialog(
            onDismissRequest = { confirmImport = false },
            icon = { Icon(Icons.Rounded.Download, null) },
            title = { Text(Words.Profile.IMPORT_TITLE) },
            text = { Text(Words.Profile.IMPORT_BODY) },
            confirmButton = {
                TextButton(onClick = { confirmImport = false; importLauncher.launch(arrayOf("*/*")) }) { Text(Words.Profile.CHOOSE_FILE) }
            },
            dismissButton = { TextButton(onClick = { confirmImport = false }) { Text(Words.Profile.CANCEL) } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.DeleteForever, null, tint = cs.error) },
            title = { Text(Words.Profile.DELETE_TITLE) },
            text = { Text(Words.Profile.DELETE_BODY) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteEverything() }) { Text(Words.Profile.DELETE_CONFIRM, color = cs.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(Words.Profile.CANCEL) } },
        )
    }
}

// ---------------------------------------------------------------- building blocks

@Composable
private fun SectionTitle(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 22.dp, bottom = 8.dp),
    )
}

/** Android 16-style grouped list: rounded ends, small gaps between rows. */
@Composable
private fun Group(vararg rows: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { i, row ->
            val top = if (i == 0) 22.dp else 6.dp
            val bottom = if (i == rows.lastIndex) 22.dp else 6.dp
            Surface(
                shape = RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth(),
            ) { row() }
        }
    }
}

private val rowColors @Composable get() = ListItemDefaults.colors(containerColor = androidx.compose.ui.graphics.Color.Transparent)

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = rowColors,
    )
}

@Composable
private fun StepperRow(
    icon: ImageVector, title: String, sub: String, value: Int, range: IntRange, enabled: Boolean, onChange: (Int) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(onClick = { onChange(value - 1) }, enabled = enabled && value > range.first, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Remove, Words.Profile.DECREASE, Modifier.size(18.dp))
                }
                Text("$value", Modifier.width(40.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                OutlinedIconButton(onClick = { onChange(value + 1) }, enabled = enabled && value < range.last, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Rounded.Add, Words.Profile.INCREASE, Modifier.size(18.dp))
                }
            }
        },
        colors = rowColors,
    )
}

@Composable
private fun ClickRow(icon: ImageVector, title: String, sub: String, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, color = androidx.compose.ui.graphics.Color.Transparent) {
        ListItem(
            headlineContent = { Text(title, color = color) }, supportingContent = { Text(sub) },
            leadingContent = { Icon(icon, null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = rowColors,
        )
    }
}

/** Profile → About → Credits: the author (opens brutt.site) and the source code (opens GitHub). */
@Composable
private fun CreditsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    @Composable
    fun LinkRow(icon: ImageVector, label: String, name: String, url: String) {
        Surface(onClick = { openExternally(context, url) }, color = androidx.compose.ui.graphics.Color.Transparent, shape = MaterialTheme.shapes.medium) {
            ListItem(
                overlineContent = { Text(label) },
                headlineContent = { Text(name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text(Words.Credits.shown(url)) },
                leadingContent = { Icon(icon, null) },
                trailingContent = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, Words.Credits.OPENS_IN_BROWSER) },
                colors = rowColors,
            )
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Words.Credits.TITLE) },
        text = {
            Column {
                LinkRow(Icons.Rounded.Person, Words.Credits.AUTHOR, Words.Credits.AUTHOR_NAME, Words.Credits.AUTHOR_URL)
                LinkRow(Icons.Rounded.Code, Words.Credits.SOURCE, Words.Credits.shown(Words.Credits.SOURCE_URL).substringAfter('/'), Words.Credits.SOURCE_URL)
                Text(
                    Words.Credits.FOOTER, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 16.dp, top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Words.Profile.DONE) } },
    )
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, sub: String) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null) }, colors = rowColors,
    )
}

@Composable
private fun Kpi(value: String, label: String, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Little bar chart of her last cycle lengths. The newest is highlighted. */
@Composable
private fun CycleBars(gaps: List<Int>) {
    if (gaps.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val lo = gaps.min() - 2
    val span = (gaps.max() - lo).coerceAtLeast(1)
    Row(
        Modifier.fillMaxWidth().padding(top = 14.dp).height(76.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom,
    ) {
        gaps.forEachIndexed { i, g ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.fillMaxWidth().height((14 + (g - lo) * 42 / span).dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 4.dp, bottomEnd = 4.dp))
                        .background(if (i == gaps.lastIndex) cs.primary else cs.primaryContainer)
                )
                Text("$g", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
    Text(
        Words.Profile.lastCycles(gaps.size), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onPick: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, option ->
            SegmentedButton(
                selected = option == selected, onClick = { onPick(option) },
                shape = SegmentedButtonDefaults.itemShape(index = i, count = options.size),
            ) { Text(label(option)) }
        }
    }
}

/** Round color choices, drawn like Android's wallpaper-color picker (main color on top, two accents below). */
@Composable
private fun ColorSwatches(selected: Accent, dark: Boolean, onPick: (Accent) -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    val options = Accent.entries.filter { it != Accent.DYNAMIC || supportsDynamicColor }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        options.forEach { accent ->
            val scheme = when (accent) {
                Accent.RED -> if (dark) RedDarkScheme else RedLightScheme
                Accent.GREEN -> toneScheme(Tones.GREEN, dark)
                Accent.YELLOW -> toneScheme(Tones.YELLOW, dark)
                Accent.DYNAMIC -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            val label = when (accent) {
                Accent.RED -> Words.Profile.COLOR_RED
                Accent.GREEN -> Words.Profile.COLOR_GREEN
                Accent.YELLOW -> Words.Profile.COLOR_YELLOW
                Accent.DYNAMIC -> Words.Profile.COLOR_DYNAMIC
            }
            val isOn = accent == selected
            Column(
                Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(role = Role.RadioButton, onClickLabel = label) { onPick(accent) }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .then(if (isOn) Modifier.border(3.dp, cs.onSurface, CircleShape).padding(5.dp) else Modifier.padding(5.dp))
                        .clip(CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawArc(scheme.primary, 180f, 180f, useCenter = true)
                        drawArc(scheme.primaryContainer, 90f, 90f, useCenter = true)
                        drawArc(scheme.tertiary, 0f, 90f, useCenter = true)
                    }
                    if (isOn) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = scheme.onPrimary)
                }
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}
