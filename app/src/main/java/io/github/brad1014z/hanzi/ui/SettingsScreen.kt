package io.github.brad1014z.hanzi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Minimal Settings (spec 07 screen 7): audio toggles, debug-only pilot controls, and
 * destructive reset behind a confirmation (spec 04). Theme/TTS-picker/credits come
 * with later milestones.
 */
@Composable
fun SettingsScreen(
    soundOn: Boolean,
    autoPlay: Boolean,
    datasetVersion: String?,
    pilotToolsEnabled: Boolean = false,
    pilotExportConsent: Boolean = false,
    pilotFacilitatorLabel: String = "unlabeled",
    onSound: (Boolean) -> Unit,
    onAutoPlay: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onPilotExportConsent: (Boolean) -> Unit = {},
    onPilotFacilitatorLabel: (String) -> Unit = {},
    onCredits: () -> Unit = {},
    onBack: () -> Unit,
) {
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.weight(1f))
        }
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SettingSwitch("Sound effects", soundOn, onSound)
        SettingSwitch("Auto-play audio", autoPlay, onAutoPlay)

        if (pilotToolsEnabled) {
            SettingSwitch("Local pilot stroke export", pilotExportConsent, onPilotExportConsent)
            Text(
                "Consent required. Stores pseudonymous stroke data on this phone only; nothing uploads.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (pilotExportConsent) {
                Text(
                    "Facilitator label",
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "unlabeled" to "Unlabeled",
                        "honest" to "Honest",
                        "wrong-direction-order" to "Deliberate error",
                    ).forEach { (value, label) ->
                        OutlinedButton(
                            onClick = { onPilotFacilitatorLabel(value) },
                            enabled = pilotFacilitatorLabel != value,
                            modifier = Modifier.weight(1f),
                        ) { Text(label) }
                    }
                }
            }
        }

        // Spec 02 licence checklist: the attribution manifest ships in-app, because a
        // sideloaded pilot APK is redistribution too (APL/LGPL/CC BY-SA all require it).
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Credits & licences", fontWeight = FontWeight.Bold)
                Text(
                    "The open datasets this app is built from.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onCredits) { Text("Open ›") }
        }

        Spacer(Modifier.weight(1f))
        datasetVersion?.let {
            Text(
                "Dataset: $it",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Reset all progress…", color = MaterialTheme.colorScheme.error) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset all progress?") },
            text = {
                Text("Every review and learning milestone goes back to zero. The characters stay. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onResetProgress() }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Keep my progress") } },
        )
    }
}

@Composable
private fun SettingSwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        Text(label, fontWeight = FontWeight.Bold)
        Switch(checked = value, onCheckedChange = onChange)
    }
}
