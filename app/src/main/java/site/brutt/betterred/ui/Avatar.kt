package site.brutt.betterred.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.brutt.betterred.Words

/** Profile icons she can pick from. "" means "use my initial". */
val AvatarChoices = listOf("", "🌻", "🌸", "🌷", "🌙", "🦋", "🍓", "🐱", "✨", "☕", "🍒", "🐰")

/** Her profile icon: an emoji she picked, or her initial, or ♡ if there's no name yet. */
@Composable
fun Avatar(avatar: String, name: String, size: Dp, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(cs.primaryContainer)
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = Words.Calendar.OPEN_PROFILE, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (avatar.isNotEmpty()) {
            Text(avatar, fontSize = (size.value * 0.5f).sp)
        } else {
            Text(
                name.trim().firstOrNull()?.uppercase() ?: "♡",
                fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.SemiBold, color = cs.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun AvatarPickerDialog(current: String, name: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Words.Profile.ICON_TITLE) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarChoices.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { choice ->
                            val selected = choice == current
                            Avatar(
                                choice, name, 52.dp,
                                if (selected) Modifier.border(3.dp, cs.primary, CircleShape) else Modifier,
                                onClick = { onPick(choice) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(Words.Profile.DONE) } },
    )
}
