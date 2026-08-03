package dev.backbee.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.core.playback.RelativeTime
import dev.backbee.core.playback.ResumeTier
import dev.backbee.core.playback.SmartResume
import dev.backbee.ui.components.BrutalOutlineButton
import dev.backbee.ui.components.BrutalPanel
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Mono
import dev.backbee.ui.components.Readout
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.Shadow
import dev.backbee.ui.theme.Stroke
import dev.backbee.ui.theme.backbeeColors

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = backbeeColors

    // SAF rather than a storage permission: point at the folder Syncthing
    // already watches, and nothing else on the device is touched.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setBackupFolder(uri.toString())
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.bgPage),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.gutter),
        verticalArrangement = Arrangement.spacedBy(Dimens.space3),
    ) {
        state.activeShow?.let { show ->
            item {
                BrutalPanel(Modifier.fillMaxWidth()) {
                    Label("Per show — ${show.title}")
                    Spacer(Modifier.height(Dimens.space2))
                    Stepper("Playback speed", "${ArchiveProgress.formatSpeed(show.speed)}×",
                        { viewModel.setShowSpeed(show.speed - 0.1f) }, { viewModel.setShowSpeed(show.speed + 0.1f) })
                    Stepper("Skip intro", "${show.skipIntroSeconds}s",
                        { viewModel.setSkipIntro(show.skipIntroSeconds - 5) }, { viewModel.setSkipIntro(show.skipIntroSeconds + 5) })
                    Stepper("Skip outro", "${show.skipOutroSeconds}s",
                        { viewModel.setSkipOutro(show.skipOutroSeconds - 5) }, { viewModel.setSkipOutro(show.skipOutroSeconds + 5) })
                }
            }
        }

        item {
            BrutalPanel(Modifier.fillMaxWidth()) {
                Label("Downloads")
                Spacer(Modifier.height(Dimens.space2))
                Stepper("Keep ahead", "${state.settings.downloadAhead} eps",
                    { viewModel.setDownloadAhead(state.settings.downloadAhead - 1) },
                    { viewModel.setDownloadAhead(state.settings.downloadAhead + 1) })
                Stepper("Storage cap", "${state.settings.storageCapBytes / (1024 * 1024 * 1024)} GB",
                    { viewModel.setStorageCapGb((state.settings.storageCapBytes / (1024 * 1024 * 1024)).toInt() - 1) },
                    { viewModel.setStorageCapGb((state.settings.storageCapBytes / (1024 * 1024 * 1024)).toInt() + 1) })
                Stepper("Delete played after", "${state.settings.deletePlayedAfterHours}h",
                    { viewModel.setDeletePlayedAfterHours(state.settings.deletePlayedAfterHours - 6) },
                    { viewModel.setDeletePlayedAfterHours(state.settings.deletePlayedAfterHours + 6) })
                SwitchRow("Wi-Fi only", "DOWNLOADS WAIT FOR AN UNMETERED CONNECTION",
                    state.settings.wifiOnlyDownloads, viewModel::setWifiOnly)
            }
        }

        item {
            BrutalPanel(Modifier.fillMaxWidth()) {
                Label("Playback & routing")
                Spacer(Modifier.height(Dimens.space2))
                Stepper("Skip forward", "${state.settings.skipForwardSeconds}s",
                    { viewModel.setSkipForwardSeconds(state.settings.skipForwardSeconds - 5) },
                    { viewModel.setSkipForwardSeconds(state.settings.skipForwardSeconds + 5) })
                Stepper("Skip back", "${state.settings.skipBackSeconds}s",
                    { viewModel.setSkipBackSeconds(state.settings.skipBackSeconds - 5) },
                    { viewModel.setSkipBackSeconds(state.settings.skipBackSeconds + 5) })
                SwitchRow(
                    "Auto-play on Bluetooth",
                    "OFF BY DEFAULT: CONNECTING LEAVES THE APP READY, NOT TALKING",
                    state.settings.autoPlayOnBluetooth,
                    viewModel::setAutoPlayOnBluetooth,
                )
            }
        }

        item {
            BrutalPanel(Modifier.fillMaxWidth()) {
                Label("Rewind on resume")
                Mono(
                    "HOW FAR BACK TO JUMP, SCALED BY HOW LONG YOU WERE AWAY.",
                    style = BackbeeType.monoMicro,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = Dimens.space2),
                )
                // Segmented, as in the spec: the four tiers side by side so the
                // shape of the rule is visible at a glance.
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.space2)) {
                    state.settings.resumeTiers.forEachIndexed { index, tier ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .background(colors.bgPanel)
                                .clickable {
                                    viewModel.setResumeTiers(
                                        state.settings.resumeTiers.bump(index, +5)
                                    )
                                }
                                .padding(vertical = Dimens.space2),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Mono("${tier.rewindSeconds}s", style = BackbeeType.mono, color = colors.accentPrimary)
                            Mono(tierLabel(tier.pauseAtMostSeconds), style = BackbeeType.monoMicro, color = colors.textMuted)
                        }
                    }
                }
                BrutalOutlineButton(
                    onClick = { viewModel.setResumeTiers(SmartResume.DEFAULT_TIERS) },
                    shadow = Shadow.sm,
                    modifier = Modifier.padding(top = Dimens.space2),
                ) {
                    Mono("RESET TO DEFAULTS", style = BackbeeType.monoSmall, color = colors.textPrimary)
                }
            }
        }

        item {
            BrutalPanel(Modifier.fillMaxWidth()) {
                Label("Backup")
                Mono(
                    "A NIGHTLY DB SNAPSHOT GOES TO A FOLDER YOU CHOOSE. POINT IT AT " +
                        "WHATEVER SYNCTHING WATCHES AND LOSING THE PHONE COSTS AT MOST A DAY.",
                    style = BackbeeType.monoMicro,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = Dimens.space2),
                )
                SwitchRow(
                    "Nightly checkpoint",
                    if (state.settings.backupFolderUri != null) "FOLDER SELECTED" else "NO FOLDER CHOSEN YET",
                    state.settings.backupEnabled,
                    viewModel::setBackupEnabled,
                )
                Row(
                    Modifier.padding(top = Dimens.space2),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    BrutalOutlineButton(onClick = { pickFolder.launch(null) }, modifier = Modifier.weight(1f)) {
                        Mono("CHOOSE FOLDER", style = BackbeeType.monoSmall, color = colors.textPrimary)
                    }
                    BrutalOutlineButton(
                        onClick = viewModel::backupNow,
                        enabled = state.settings.backupFolderUri != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Mono("BACK UP NOW", style = BackbeeType.monoSmall, color = colors.textPrimary)
                    }
                }
            }
        }

        // The machinery, inspectable. "Is my position actually being written?"
        // should be answerable without a debugger.
        item {
            val diag = state.diagnostics
            Readout(
                lines = listOfNotNull(
                    diag.lastCheckpointAtMillis?.let {
                        "DB CHECKPOINT: ${RelativeTime.since(it, System.currentTimeMillis())} // " +
                            (diag.lastCheckpointResult ?: "UNKNOWN")
                    } ?: "DB CHECKPOINT: NEVER RUN",
                    diag.lastFeedRefreshAtMillis?.let {
                        "FEED REFRESH: ${RelativeTime.since(it, System.currentTimeMillis())}"
                    } ?: "FEED REFRESH: NOT YET",
                    "POSITION FLUSHES TODAY: ${diag.positionFlushesToday}",
                ),
            )
        }

        item {
            BrutalOutlineButton(
                onClick = viewModel::refreshFeedsNow,
                shadow = Shadow.sm,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Mono("CHECK FEEDS NOW", style = BackbeeType.monoSmall, color = colors.textPrimary)
            }
        }
    }
}

private fun List<ResumeTier>.bump(index: Int, delta: Int): List<ResumeTier> = mapIndexed { i, tier ->
    // Wraps at 120s so a single control can cycle without needing a second one.
    if (i == index) tier.copy(rewindSeconds = ((tier.rewindSeconds + delta) % 125).coerceAtLeast(0)) else tier
}

private fun tierLabel(bound: Long): String = when {
    bound == Long.MAX_VALUE -> "LONGER"
    bound < 3600 -> "<${bound / 60}MIN"
    bound < 86_400 -> "<${bound / 3600}HR"
    else -> "<${bound / 86_400}DAY"
}

@Composable
private fun Stepper(label: String, value: String, onDown: () -> Unit, onUp: () -> Unit) {
    val colors = backbeeColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mono(label.uppercase(), style = BackbeeType.monoSmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
        StepKey("−", onDown)
        Mono(value, style = BackbeeType.mono, color = colors.accentPrimary,
            modifier = Modifier.padding(horizontal = Dimens.space3))
        StepKey("+", onUp)
    }
}

@Composable
private fun StepKey(symbol: String, onClick: () -> Unit) {
    val colors = backbeeColors
    Mono(
        text = symbol,
        style = BackbeeType.mono,
        color = colors.textPrimary,
        modifier = Modifier
            .background(colors.bgInverse.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = backbeeColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = Dimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Mono(label.uppercase(), style = BackbeeType.monoSmall, color = colors.textPrimary)
            Mono(description, style = BackbeeType.monoMicro, color = colors.textMuted)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
