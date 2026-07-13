package io.github.brad1014z.hanzi.ui

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.brad1014z.hanzi.cloud.Cloud
import io.github.brad1014z.hanzi.engine.social.AccountState
import io.github.brad1014z.hanzi.engine.social.BoardEntry
import io.github.brad1014z.hanzi.engine.social.Friend
import io.github.brad1014z.hanzi.engine.social.Identity
import io.github.brad1014z.hanzi.engine.social.Weeks
import kotlinx.coroutines.launch

/**
 * Profile & Friends (spec 07 screen 8 / spec 12, M4 free-tier slice): generated
 * identity, friends by mutual code, and the weekly family board. Signed out it shows
 * one calm explanation + button — never a nag (spec 12 entry-point rules).
 */
@Composable
fun FamilyScreen(cloud: Cloud, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val account by cloud.account.state.collectAsState(initial = AccountState.SignedOut)
    var codeInput by remember { mutableStateOf("") }
    var addResult by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Home") }
            Text("Family", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
        }
        if (cloud.isPreview) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Preview mode — no Firebase project configured yet, so these are " +
                        "demo companions, not real people. Everything works for a feel-test.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        when (val state = account) {
            is AccountState.SignedOut -> {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Sign in to back up your progress and share a weekly board with your family. " +
                        "Everything in the app keeps working without it, forever.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { scope.launch { cloud.account.signIn() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign in with Google") }
            }

            is AccountState.SignedIn -> {
                val profile = state.profile
                // My card: generated identity only — the Google account never shows (spec 12).
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = Identity.avatar(profile.avatarId),
                                fontSize = 40.sp,
                                modifier = Modifier.clickable {
                                    scope.launch { cloud.account.setAvatar(profile.avatarId + 1) }
                                },
                            )
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    profile.displayName,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    "tap the avatar to change it",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { cloud.account.rerollDisplayName() } }) {
                                Text("🎲 new name")
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("My friend code", style = MaterialTheme.typography.labelMedium)
                                Text(
                                    profile.friendCode.chunked(3).joinToString("-"),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    Intent.createChooser(
                                        Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Add me in Hanzi! My friend code: ${profile.friendCode}",
                                            )
                                        },
                                        "Share friend code",
                                    ),
                                )
                            }) { Text("Share") }
                            TextButton(onClick = { scope.launch { cloud.account.rotateFriendCode() } }) {
                                Text("↻")
                            }
                        }
                    }
                }

                // Add a friend by code (spec 12: codes are the only way to connect).
                Spacer(Modifier.height(16.dp))
                Text("Add a friend", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { codeInput = it.uppercase().take(9) },
                        placeholder = { Text("their code, e.g. 7KQ-M2X") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.padding(4.dp))
                    Button(
                        onClick = {
                            val code = Identity.normalizeCode(codeInput)
                            scope.launch {
                                val name = if (Identity.isValidCode(code)) {
                                    cloud.social.addFriendByCode(code)
                                } else null
                                addResult = if (name != null) "Request sent to $name — they confirm on their phone."
                                else "That code doesn't look right — read it again together."
                                if (name != null) codeInput = ""
                            }
                        },
                        enabled = codeInput.isNotBlank(),
                    ) { Text("Add") }
                }
                addResult?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }

                // Friends + weekly board.
                val friends by cloud.social.friends.collectAsState(initial = emptyList())
                val weekId = remember { Weeks.weekId(System.currentTimeMillis()) }
                val board by cloud.social.weeklyBoard(weekId).collectAsState(initial = emptyList())

                Spacer(Modifier.height(16.dp))
                Text(
                    "This week's board · $weekId",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (board.isEmpty()) {
                    Text(
                        "Finish a quest to put XP on the board!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                board.forEachIndexed { i, entry -> BoardRow(i + 1, entry) }

                if (friends.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Text("Friends", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    friends.forEach { friend ->
                        FriendRow(
                            friend = friend,
                            onConfirm = { scope.launch { cloud.social.confirmFriend(friend.uid) } },
                            onRemove = { scope.launch { cloud.social.removeFriend(friend.uid) } },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
                TextButton(onClick = { scope.launch { cloud.account.signOut() } }) {
                    Text("Sign out (keeps everything on this phone)")
                }
            }
        }
    }
}

@Composable
private fun BoardRow(place: Int, entry: BoardEntry) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (entry.isMe) MaterialTheme.colorScheme.secondaryContainer
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                when (place) {
                    1 -> "🥇"; 2 -> "🥈"; 3 -> "🥉"; else -> " $place "
                },
                fontSize = 20.sp,
            )
            Text(Identity.avatar(entry.avatarId), fontSize = 24.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Text(
                entry.displayName + if (entry.isMe) " (me)" else "",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (entry.isMe) FontWeight.Bold else FontWeight.Normal,
            )
            Spacer(Modifier.weight(1f))
            Text("${entry.weeklyXp} XP", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FriendRow(friend: Friend, onConfirm: () -> Unit, onRemove: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Text(Identity.avatar(friend.avatarId), fontSize = 24.sp)
        Text(friend.displayName, modifier = Modifier.padding(start = 8.dp))
        if (friend.pending) {
            Text(
                " · waiting",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (friend.pending) {
            TextButton(onClick = onConfirm) { Text("Confirm") }
        }
        TextButton(onClick = onRemove) { Text("✕") }
    }
}
