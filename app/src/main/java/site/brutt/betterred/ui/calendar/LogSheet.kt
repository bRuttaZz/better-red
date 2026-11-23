package site.brutt.betterred.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import site.brutt.betterred.Words
import site.brutt.betterred.domain.DayLog
import site.brutt.betterred.domain.Flow
import site.brutt.betterred.domain.Sex
import site.brutt.betterred.domain.Symptom
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The "Bloody note" sheet: flow, mood, symptoms and a note for one day. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LogSheet(initial: DayLog, showSex: Boolean, onDismiss: () -> Unit, onSave: (DayLog) -> Unit) {
    var flow by remember { mutableStateOf(initial.flow) }
    var mood by remember { mutableStateOf(initial.mood) }
    var symptoms by remember { mutableStateOf(initial.symptoms) }
    var note by remember { mutableStateOf(initial.note) }
    var sex by remember { mutableStateOf(initial.sex) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                initial.date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())),
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold,
            )

            Section(Words.Log.PERIOD)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = flow == null, onClick = { flow = null }, label = { Text(Words.Log.NONE) },
                    leadingIcon = if (flow == null) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                )
                Flow.entries.forEach { f ->
                    FilterChip(
                        selected = flow == f, onClick = { flow = f }, label = { Text(f.label) },
                        leadingIcon = { Icon(Icons.Rounded.WaterDrop, null, Modifier.size(18.dp)) },
                    )
                }
            }

            Section(Words.Log.MOOD)
            MoodRow(selected = mood, onPick = { mood = if (mood == it) null else it })

            Section(Words.Log.SYMPTOMS)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Symptom.entries.forEach { s ->
                    val on = s in symptoms
                    FilterChip(
                        selected = on, onClick = { symptoms = if (on) symptoms - s else symptoms + s },
                        label = { Text(s.label) },
                        leadingIcon = if (on) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                    )
                }
            }

            if (showSex) {
                Section(Words.Log.SEX)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = sex == null, onClick = { sex = null }, label = { Text(Words.Log.NONE) },
                        leadingIcon = if (sex == null) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                    )
                    Sex.entries.forEach { s ->
                        FilterChip(
                            selected = sex == s, onClick = { sex = s }, label = { Text(s.label) },
                            leadingIcon = { Icon(Icons.Rounded.Favorite, null, Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            Section(Words.Log.NOTE)
            OutlinedTextField(
                value = note, onValueChange = { note = it.take(500) },
                placeholder = { Text(Words.Log.NOTE_HINT) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp),
            )

            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(Words.Log.CANCEL) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(DayLog(initial.date, flow, mood, symptoms, note.trim(), sex)) }) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(Words.Log.SAVE)
                }
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 10.dp),
    )
}
