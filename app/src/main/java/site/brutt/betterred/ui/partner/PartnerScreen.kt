package site.brutt.betterred.ui.partner

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Egg
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Healing
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.automirrored.rounded.StickyNote2
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import site.brutt.betterred.Words
import site.brutt.betterred.data.Prefs
import site.brutt.betterred.share.Qr
import site.brutt.betterred.share.Server
import site.brutt.betterred.share.Share
import site.brutt.betterred.share.ShareRole
import site.brutt.betterred.share.SyncError
import site.brutt.betterred.ui.AppState
import site.brutt.betterred.ui.AppViewModel
import site.brutt.betterred.ui.Avatar

/** The Partner tab. Her phone: share (or start sharing). The partner's phone: what she shares. */
@Composable
fun PartnerScreen(state: AppState, vm: AppViewModel, onScanNew: () -> Unit, modifier: Modifier = Modifier) {
    val busy by vm.shareBusy.collectAsState()
    val code by vm.pairingCode.collectAsState()
    // Ticks once a minute so "synced 3 min ago" stays right.
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = System.currentTimeMillis() / 1000 } }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(Words.Partner.TITLE, style = MaterialTheme.typography.headlineMedium)
        }
        when (state.share.role) {
            null -> NotSharing(busy, onStart = vm::startSharing)
            ShareRole.PUBLISHER -> Sharing(state, busy, now, vm)
            ShareRole.PARTNER -> Viewing(state, busy, now, vm, onScanNew)
        }
    }
    code?.let { PairingCodeDialog(it, state.share.fingerprint, onClose = vm::closePairingCode) }
}

// ---------------------------------------------------------------- her phone, before sharing

@Composable
private fun NotSharing(busy: Boolean, onStart: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    var server by remember { mutableStateOf(Share.DEFAULT_SERVER) }
    var editing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).rotate(-6f).clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Favorite, null, Modifier.size(44.dp).rotate(6f), tint = cs.onPrimaryContainer) }
        Spacer(Modifier.height(18.dp))
        Text(Words.Partner.SHARE_TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(Words.Partner.SHARE_BODY, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
    }
    Spacer(Modifier.height(20.dp))
    Bullet(Icons.Rounded.QrCodeScanner, Words.Partner.B_PAIR, Words.Partner.B_PAIR_SUB)
    Bullet(Icons.Rounded.Lock, Words.Partner.B_E2E, Words.Partner.B_E2E_SUB)
    Bullet(Icons.Rounded.Visibility, Words.Partner.B_CONTROL, Words.Partner.B_CONTROL_SUB)

    // The server is filled in already; she can change it, but is never asked to.
    Spacer(Modifier.height(8.dp))
    if (editing) {
        OutlinedTextField(
            value = server, onValueChange = { server = it.take(260) }, singleLine = true,
            label = { Text(Words.Partner.SERVER) }, supportingText = { Text(Words.Partner.SERVER_HINT) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Rounded.Dns, null, Modifier.size(18.dp), tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            val shown = Server.parse(server, allowLocal = true)?.let { Words.Partner.serverLabel(it.host, it.password != null) } ?: server
            Text("${Words.Partner.SERVER}: $shown", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { editing = true }) { Text(Words.Partner.CHANGE) }
        }
    }
    Spacer(Modifier.height(12.dp))
    Button(onClick = { onStart(server) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Rounded.QrCode2, null)
        Spacer(Modifier.width(10.dp))
        Text(if (busy) Words.Partner.STARTING else Words.Partner.START)
    }
}

@Composable
private fun Bullet(icon: ImageVector, title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ---------------------------------------------------------------- her phone, sharing

@Composable
private fun Sharing(state: AppState, busy: Boolean, now: Long, vm: AppViewModel) {
    val share = state.share
    val choices = state.prefs.share
    var confirmNew by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }

    StatusCard(
        avatar = { Avatar("", "", 44.dp) },
        title = Words.Partner.YOUR_PARTNER,
        status = if (share.syncedAt > 0) Words.Partner.sharingSynced(Words.Partner.ago(share.syncedAt, now)) else Words.Partner.SHARING_NOT_YET,
        pill = Words.Partner.CAN_VIEW,
    )
    share.error?.let { ErrorNote(Words.Partner.error(it)) }

    Section(Words.Partner.WHAT_THEY_SEE)
    Group {
        Shared(Icons.Rounded.WaterDrop, Words.Partner.PERIOD, Words.Partner.PERIOD_SUB, true) // always shared
        ChoiceRow(Icons.Rounded.Mood, Words.Partner.MOOD, Words.Partner.MOOD_SUB, choices.mood) { vm.setShareChoice(Prefs.Keys.SHARE_MOOD, it) }
        ChoiceRow(Icons.Rounded.Egg, Words.Partner.FERTILITY, Words.Partner.FERTILITY_SUB, choices.fertility) { vm.setShareChoice(Prefs.Keys.SHARE_FERTILITY, it) }
        ChoiceRow(Icons.Rounded.Healing, Words.Partner.SYMPTOMS, Words.Partner.SYMPTOMS_SUB, choices.symptoms) { vm.setShareChoice(Prefs.Keys.SHARE_SYMPTOMS, it) }
        ChoiceRow(Icons.AutoMirrored.Rounded.StickyNote2, Words.Partner.NOTES, Words.Partner.NOTES_SUB, choices.notes) { vm.setShareChoice(Prefs.Keys.SHARE_NOTES, it) }
    }
    Text(
        "${Words.Partner.lastDays(Share.MAX_DAYS)} ${Words.Partner.SEX_NEVER}",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
    )

    Section(Words.Partner.SECTION_SYNC)
    Group {
        InfoRow(Icons.Rounded.Lock, Words.Partner.ENCRYPTED, Words.Partner.fingerprint(share.fingerprint))
        InfoRow(Icons.Rounded.Dns, Words.Partner.SERVER, Words.Partner.serverLabel(share.server.orEmpty(), share.serverHasPassword))
        ActionRow(Icons.Rounded.Sync, Words.Partner.SYNC_NOW, if (share.syncedAt > 0) Words.Partner.ago(share.syncedAt, now) else "", enabled = !busy, onClick = vm::syncNow)
        ActionRow(Icons.Rounded.QrCode2, Words.Partner.NEW_CODE, Words.Partner.NEW_CODE_SUB, enabled = !busy) { confirmNew = true }
        ActionRow(Icons.Rounded.LinkOff, Words.Partner.STOP, Words.Partner.STOP_SUB, enabled = !busy, danger = true) { confirmStop = true }
    }

    if (confirmNew) Confirm(Words.Partner.NEW_CODE_TITLE, Words.Partner.NEW_CODE_BODY, Words.Partner.NEW_CODE_CONFIRM, danger = false,
        onDismiss = { confirmNew = false }) { confirmNew = false; vm.newPairingCode() }
    if (confirmStop) Confirm(Words.Partner.STOP_TITLE, Words.Partner.STOP_BODY, Words.Partner.STOP_CONFIRM, danger = true,
        onDismiss = { confirmStop = false }) { confirmStop = false; vm.stopSharing() }
}


// ---------------------------------------------------------------- the partner's phone

@Composable
private fun Viewing(state: AppState, busy: Boolean, now: Long, vm: AppViewModel, onScanNew: () -> Unit) {
    val share = state.share
    val name = share.partnerName.orEmpty()
    val choices = state.prefs.share
    var confirmDisconnect by remember { mutableStateOf(false) }

    StatusCard(
        avatar = { Avatar("", name, 44.dp) },
        title = name.ifBlank { Words.Partner.YOUR_PARTNER },
        status = if (share.syncedAt > 0) Words.Partner.connectedSynced(Words.Partner.ago(share.syncedAt, now)) else Words.Partner.CONNECTED_WAITING,
        pill = Words.Partner.VIEW_ONLY,
    )
    // "Waiting for her phone" isn't something he can fix, so it's a calm note, not an error.
    share.error?.let { ErrorNote(Words.Partner.partnerError(it, name), calm = it == SyncError.WAITING) }

    Section(Words.Partner.sharesWithYou(name))
    Group {
        Shared(Icons.Rounded.WaterDrop, Words.Partner.PERIOD, Words.Partner.PERIOD_SUB, true)
        Shared(Icons.Rounded.Mood, Words.Partner.MOOD, Words.Partner.MOOD_SUB, choices.mood)
        Shared(Icons.Rounded.Egg, Words.Partner.FERTILITY, Words.Partner.FERTILITY_SUB, choices.fertility)
        Shared(Icons.Rounded.Healing, Words.Partner.SYMPTOMS, Words.Partner.SYMPTOMS_SUB, choices.symptoms)
        Shared(Icons.AutoMirrored.Rounded.StickyNote2, Words.Partner.NOTES, Words.Partner.NOTES_SUB, choices.notes)
    }

    Section(Words.Partner.SECTION_SYNC)
    Group {
        InfoRow(Icons.Rounded.Lock, Words.Partner.ENCRYPTED, Words.Partner.fingerprint(share.fingerprint))
        InfoRow(Icons.Rounded.Dns, Words.Partner.SERVER, Words.Partner.serverLabel(share.server.orEmpty(), share.serverHasPassword))
        ActionRow(Icons.Rounded.Sync, Words.Partner.SYNC_NOW, if (share.syncedAt > 0) Words.Partner.ago(share.syncedAt, now) else "", enabled = !busy, onClick = vm::syncNow)
        ActionRow(Icons.Rounded.QrCodeScanner, Words.Partner.SCAN_NEW, Words.Partner.SCAN_NEW_SUB, enabled = !busy, onClick = onScanNew)
        ActionRow(Icons.Rounded.LinkOff, Words.Partner.DISCONNECT, Words.Partner.DISCONNECT_SUB, enabled = !busy, danger = true) { confirmDisconnect = true }
    }
    if (confirmDisconnect) Confirm(Words.Partner.DISCONNECT_TITLE, Words.Partner.disconnectBody(name), Words.Partner.DISCONNECT, danger = true,
        onDismiss = { confirmDisconnect = false }) { confirmDisconnect = false; vm.disconnectPartner() }
}

// ---------------------------------------------------------------- the pairing code (QR)

/** Full screen, shown once. The QR holds the private key, so it is never saved anywhere. */
@Composable
private fun PairingCodeDialog(code: String, fingerprint: String, onClose: () -> Unit) {
    val bitmap = remember(code) { Qr.bitmap(code, 720).asImageBitmap() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth()) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, Words.Partner.DONE) }
                }
                Text(Words.Partner.CODE_TITLE, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(Words.Partner.CODE_SUB, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(20.dp))
                // Always black on white (in dark mode too), so every camera reads it.
                Box(Modifier.clip(RoundedCornerShape(24.dp)).background(Color.White).padding(16.dp)) {
                    Image(bitmap, contentDescription = Words.Partner.CODE_TITLE, filterQuality = FilterQuality.None, modifier = Modifier.size(260.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(fingerprint, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
                Text(Words.Partner.FINGERPRINT, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(Words.Partner.CODE_WARNING, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(Words.Partner.DONE) }
            }
        }
    }
}

// ---------------------------------------------------------------- pieces

@Composable
private fun StatusCard(avatar: @Composable () -> Unit, title: String, status: String, pill: String) {
    val cs = MaterialTheme.colorScheme
    Surface(shape = RoundedCornerShape(24.dp), color = cs.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            avatar()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(cs.primary))
                    Spacer(Modifier.width(6.dp))
                    Text(status, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
            }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(cs.surfaceContainerHighest).padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.Visibility, null, Modifier.size(14.dp), tint = cs.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Text(pill, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ErrorNote(text: String, calm: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    val bg = if (calm) cs.surfaceContainerHigh else cs.errorContainer
    val fg = if (calm) cs.onSurfaceVariant else cs.onErrorContainer
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(RoundedCornerShape(16.dp)).background(bg).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (calm) Icons.Rounded.HourglassEmpty else Icons.Rounded.ErrorOutline, null, tint = fg)
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 8.dp, top = 22.dp, bottom = 8.dp),
    )
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
    }
}

private val rowColors @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

@Composable
private fun ChoiceRow(icon: ImageVector, title: String, sub: String, on: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null) },
        trailingContent = { Switch(checked = on, onCheckedChange = onChange) },
        colors = rowColors,
    )
}

@Composable
private fun Shared(icon: ImageVector, title: String, sub: String, on: Boolean) {
    ListItem(
        headlineContent = { Text(title) }, supportingContent = { Text(sub) },
        leadingContent = { Icon(icon, null) },
        trailingContent = {
            Icon(
                if (on) Icons.Rounded.CheckCircle else Icons.Rounded.Block, null,
                tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        },
        colors = rowColors,
    )
}

@Composable
private fun InfoRow(icon: ImageVector, title: String, sub: String) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(sub) }, leadingContent = { Icon(icon, null) }, colors = rowColors)
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, sub: String, enabled: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(onClick = onClick, enabled = enabled, color = Color.Transparent) {
        ListItem(
            headlineContent = { Text(title, color = color) },
            supportingContent = if (sub.isNotEmpty()) ({ Text(sub) }) else null,
            leadingContent = { Icon(icon, null, tint = if (danger) color else MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = rowColors,
        )
    }
}

@Composable
private fun Confirm(title: String, body: String, confirm: String, danger: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Words.Partner.CANCEL) } },
    )
}
