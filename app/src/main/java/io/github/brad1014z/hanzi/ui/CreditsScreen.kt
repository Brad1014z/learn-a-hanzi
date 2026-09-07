package io.github.brad1014z.hanzi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * One attribution entry (spec 02 licence checklist). Full licence texts ship as plain
 * text files under `assets/licenses/` so the credits screen works offline, like
 * everything else.
 */
data class Credit(
    val source: String,
    val usedFor: String,
    val license: String,
    val licenseAsset: String?,
    val url: String,
    val note: String? = null,
)

/**
 * The attribution manifest the app ships (spec 02 "Licence-obligation checklist").
 * Mirrors `data/ingest-report.md`; when the ingest tool re-pins a source, update both.
 */
val CREDITS: List<Credit> = listOf(
    Credit(
        source = "make-me-a-hanzi — graphics.txt",
        usedFor = "Stroke outlines and medians for every character (the shapes you trace).",
        license = "Arphic Public License",
        licenseAsset = "licenses/arphic-public-license.txt",
        url = "https://github.com/skishore/makemeahanzi",
        note = "Derived from Arphic Technology's PL KaitiM GB / PL UKai fonts. " +
            "The stroke data in this app remains under the APL.",
    ),
    Credit(
        source = "make-me-a-hanzi — dictionary.txt",
        usedFor = "Definitions, pinyin, radicals and decompositions.",
        license = "GNU LGPL v3 (or later)",
        licenseAsset = "licenses/lgpl-3.0.txt",
        url = "https://github.com/skishore/makemeahanzi",
        note = "The LGPL incorporates the GPL v3 text by reference; both ship in the app. " +
            "The bundled dataset is regenerable from these public sources with the open " +
            "ingest tool in the repository.",
    ),
    Credit(
        source = "CC-CEDICT",
        usedFor = "Example words and phrases shown with each character.",
        license = "Creative Commons Attribution-ShareAlike 4.0",
        licenseAsset = "licenses/cc-by-sa-4.0.txt",
        url = "https://www.mdbg.net/chinese/dictionary?page=cc-cedict",
        note = "CEDICT-derived rows in the dataset are redistributed under CC BY-SA 4.0.",
    ),
    Credit(
        source = "Unihan (Unicode Character Database)",
        usedFor = "Stroke-count cross-checks at ingest.",
        license = "Unicode License v3",
        licenseAsset = "licenses/unicode-license-v3.txt",
        url = "https://www.unicode.org/charts/unihan.html",
    ),
    Credit(
        source = "Tatoeba (cmn corpus)",
        usedFor = "Character and word frequency ranks that order the curriculum. " +
            "No Tatoeba sentence text ships in the app.",
        license = "Creative Commons Attribution 2.0 France",
        licenseAsset = "licenses/cc-by-2.0-fr.txt",
        url = "https://tatoeba.org",
    ),
    Credit(
        source = "Example sentences",
        usedFor = "One short sentence per character.",
        license = "Generated for this app, human-reviewed",
        licenseAsset = null,
        url = "https://github.com/Brad1014z/learn-a-hanzi",
        note = "Written by an AI model (claude-fable-5) with vocabulary limited to the " +
            "characters you learn here, then reviewed by a person before shipping. The " +
            "prompt and model version are pinned in the repository.",
    ),
    Credit(
        source = "Pronunciation audio",
        usedFor = "Every character, word and sentence, spoken offline.",
        license = "Synthesised for this app",
        licenseAsset = null,
        url = "https://cloud.google.com/text-to-speech",
        note = "Google Cloud Text-to-Speech voice cmn-CN-Chirp3-HD-Leda, generated once " +
            "at build time — the app never calls a network service to speak.",
    ),
)

/**
 * Credits & licences (spec 02 checklist; spec 07 Settings → Credits). Lists every data
 * source with what it's used for, and opens the full licence text inline.
 */
@Composable
fun CreditsScreen(onBack: () -> Unit) {
    var openLicense by remember { mutableStateOf<Credit?>(null) }
    val showing = openLicense
    if (showing?.licenseAsset != null) {
        LicenseTextScreen(credit = showing, onBack = { openLicense = null })
        return
    }
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Spacer(Modifier.weight(1f))
        }
        Text("Credits & licences", style = MaterialTheme.typography.headlineMedium)
        Text(
            "This app is free and open. Its learning content comes from open datasets — " +
                "named and thanked here, with their licences in full. The code is on GitHub.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        CREDITS.forEach { credit ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.padding(14.dp)) {
                    Text(credit.source, fontWeight = FontWeight.Bold)
                    Text(
                        credit.usedFor,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    credit.note?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        credit.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    ) {
                        Text(
                            credit.license,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        if (credit.licenseAsset != null) {
                            TextButton(onClick = { openLicense = credit }) { Text("Read licence") }
                        }
                    }
                }
            }
        }
        Text(
            "Application code: see LICENSE in the repository. Family and friends who " +
                "playtested early builds: thank you.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 16.dp, bottom = 24.dp),
        )
    }
}

@Composable
private fun LicenseTextScreen(credit: Credit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val assetPath = credit.licenseAsset ?: return
    val text by produceState<String?>(null, assetPath) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(assetPath).bufferedReader().use { it.readText() }
            }.getOrElse { "Licence text could not be loaded. See ${credit.url}." }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Credits") }
            Spacer(Modifier.weight(1f))
        }
        Text(credit.license, style = MaterialTheme.typography.titleLarge)
        Text(
            credit.source,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        val body = text
        if (body == null) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 24.dp))
        } else {
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}
