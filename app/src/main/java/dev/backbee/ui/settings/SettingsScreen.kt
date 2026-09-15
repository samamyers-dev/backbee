package dev.backbee.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import dev.backbee.ui.components.CarbonToggle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.BuildConfig
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.core.playback.RelativeTime
import dev.backbee.core.playback.ResumeTier
import dev.backbee.core.playback.SmartResume
import dev.backbee.ui.components.CarbonOutlineButton
import dev.backbee.ui.components.CarbonPanel
import dev.backbee.ui.components.Label
import dev.backbee.ui.components.Readout
import dev.backbee.ui.theme.BackbeeType
import dev.backbee.ui.theme.Dimens
import dev.backbee.ui.theme.backbeeColors

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val restore by viewModel.restore.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = backbeeColors

    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.inspectBackup(uri)
    }

    // SAF rather than a storage permission: the user picks one folder (a
    // synced folder, an SD card, anything a document provider exposes) and
    // nothing else on the device is touched.
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            // Some providers hand out a tree without a persistable grant and
            // throw here; the folder is then only good for this session, which
            // the nightly job reports as "not writable" rather than crashing now.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
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
                CarbonPanel(Modifier.fillMaxWidth()) {
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
            CarbonPanel(Modifier.fillMaxWidth()) {
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
                SwitchRow("Wi-Fi only", "Downloads wait for an unmetered connection",
                    state.settings.wifiOnlyDownloads, viewModel::setWifiOnly)
            }
        }

        item {
            CarbonPanel(Modifier.fillMaxWidth()) {
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
                    "Off by default: connecting leaves the app ready, not talking",
                    state.settings.autoPlayOnBluetooth,
                    viewModel::setAutoPlayOnBluetooth,
                )
            }
        }

        item {
            CarbonPanel(Modifier.fillMaxWidth()) {
                Label("Rewind on resume")
                Text(
                    "How far back to jump, scaled by how long you were away.",
                    style = BackbeeType.labelSmall,
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
                                .background(colors.field)
                                .defaultMinSize(minHeight = 48.dp)
                                .clickable {
                                    viewModel.setResumeTiers(
                                        state.settings.resumeTiers.bump(index, +5)
                                    )
                                }
                                .padding(vertical = Dimens.space2),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text("${tier.rewindSeconds}s", style = BackbeeType.body, color = colors.textAccentOnField)
                            Text(tierLabel(tier.pauseAtMostSeconds), style = BackbeeType.labelSmall, color = colors.textMuted)
                        }
                    }
                }
                CarbonOutlineButton(
                    onClick = { viewModel.setResumeTiers(SmartResume.DEFAULT_TIERS) },
                    modifier = Modifier.padding(top = Dimens.space2),
                ) {
                    Label("Reset to defaults", style = BackbeeType.bodySmall, color = colors.textPrimary)
                }
            }
        }

        item {
            CarbonPanel(Modifier.fillMaxWidth()) {
                Label("Backup")
                Text(
                    "Every night a copy of your place in every archive goes to a folder you " +
                        "Choose. pick one that syncs off the phone and losing it costs at most a day.",
                    style = BackbeeType.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = Dimens.space2),
                )
                SwitchRow(
                    "Nightly backup",
                    if (state.settings.backupFolderUri != null) "Folder selected" else "No folder chosen yet",
                    state.settings.backupEnabled,
                    viewModel::setBackupEnabled,
                )
                Row(
                    Modifier.padding(top = Dimens.space2),
                    horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                ) {
                    CarbonOutlineButton(onClick = { pickFolder.launch(null) }, modifier = Modifier.weight(1f)) {
                        Label("Choose folder", style = BackbeeType.bodySmall, color = colors.textPrimary)
                    }
                    CarbonOutlineButton(
                        onClick = viewModel::backupNow,
                        enabled = state.settings.backupFolderUri != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Label("Back up now", style = BackbeeType.bodySmall, color = colors.textPrimary)
                    }
                }

                // The way back after a lost or replaced phone. The file is read
                // and checked first; nothing changes until the numbers below
                // have been looked at and confirmed.
                CarbonOutlineButton(
                    onClick = { pickBackup.launch(arrayOf("*/*")) },
                    enabled = !restore.inspecting && !restore.restoring,
                    modifier = Modifier.fillMaxWidth().padding(top = Dimens.space2),
                ) {
                    Label(
                        if (restore.inspecting) "Reading backup…" else "Restore from a backup file",
                        style = BackbeeType.bodySmall,
                        color = colors.textPrimary,
                    )
                }
                restore.error?.let { error ->
                    Readout(
                        lines = listOf("Restore: ${error}"),
                        tone = colors.onInverseAlert,
                        modifier = Modifier.padding(top = Dimens.space2),
                    )
                }
                restore.preview?.let { preview ->
                    Readout(
                        lines = listOf(
                            "File: ${preview.name}",
                            "${preview.shows} show(s) · ${preview.playedEpisodes} episodes played · ${preview.sizeBytes / 1024} KB",
                            "This replaces every position on this phone.",
                            "The app restarts when it is done.",
                        ),
                        tone = colors.onInverseAlert,
                        modifier = Modifier.padding(top = Dimens.space2),
                    )
                    Row(
                        Modifier.padding(top = Dimens.space2),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.space2),
                    ) {
                        CarbonOutlineButton(
                            onClick = viewModel::cancelRestore,
                            enabled = !restore.restoring,
                            modifier = Modifier.weight(1f),
                        ) {
                            Label("Keep what i have", style = BackbeeType.bodySmall, color = colors.textPrimary)
                        }
                        CarbonOutlineButton(
                            onClick = viewModel::confirmRestore,
                            enabled = !restore.restoring,
                            contentColor = colors.textAlert,
                            modifier = Modifier.weight(1f),
                        ) {
                            Label(
                                if (restore.restoring) "Restoring…" else "Restore and restart",
                                style = BackbeeType.bodySmall,
                                color = colors.textAlert,
                            )
                        }
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
                        "Last backup: ${RelativeTime.since(it, System.currentTimeMillis())} // " +
                            (diag.lastCheckpointResult ?: "Unknown")
                    } ?: "Last backup: never run",
                    diag.lastFeedRefreshAtMillis?.let {
                        "Feed refresh: ${RelativeTime.since(it, System.currentTimeMillis())}"
                    } ?: "Feed refresh: not yet",
                    "Position flushes today: ${diag.positionFlushesToday}",
                ),
            )
        }

        item {
            CarbonOutlineButton(
                onClick = viewModel::refreshFeedsNow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Label("Check feeds now", style = BackbeeType.bodySmall, color = colors.textPrimary)
            }
        }

        item {
            CarbonPanel(Modifier.fillMaxWidth()) {
                Label("About")
                Text(
                    "Backbee ${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}",
                    style = BackbeeType.bodySmall,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = Dimens.space2),
                )
                Text(
                    "No accounts. no analytics. nothing about what you listen to leaves this phone.",
                    style = BackbeeType.labelSmall,
                    color = colors.textMuted,
                    modifier = Modifier.padding(vertical = Dimens.space2),
                )
                CarbonOutlineButton(
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.PRIVACY_POLICY_URL)))
                        }
                    },
                ) {
                    Label("Privacy policy", style = BackbeeType.bodySmall, color = colors.textPrimary)
                }
            }
        }
    }
}

private fun List<ResumeTier>.bump(index: Int, delta: Int): List<ResumeTier> = mapIndexed { i, tier ->
    // Wraps at 120s so a single control can cycle without needing a second one.
    if (i == index) tier.copy(rewindSeconds = ((tier.rewindSeconds + delta) % 125).coerceAtLeast(0)) else tier
}

private fun tierLabel(bound: Long): String = when {
    bound == Long.MAX_VALUE -> "Longer"
    bound < 3600 -> "<${bound / 60}min"
    bound < 86_400 -> "<${bound / 3600}hr"
    else -> "<${bound / 86_400}day"
}

@Composable
private fun Stepper(label: String, value: String, onDown: () -> Unit, onUp: () -> Unit) {
    val colors = backbeeColors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = BackbeeType.bodySmall, color = colors.textPrimary, modifier = Modifier.weight(1f))
        StepKey("−", "Decrease $label", onDown)
        Text(value, style = BackbeeType.body, color = colors.textAccent,
            modifier = Modifier.padding(horizontal = Dimens.space3))
        StepKey("+", "Increase $label", onUp)
    }
}

@Composable
private fun StepKey(symbol: String, actionLabel: String, onClick: () -> Unit) {
    CarbonOutlineButton(
        onClick = onClick,
        modifier = Modifier.width(48.dp)
            .semantics(mergeDescendants = true) { contentDescription = actionLabel },
    ) {
        Label(symbol, style = BackbeeType.body)
    }
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = backbeeColors
    // The whole row toggles, and toggleable merges the label and description
    // into it, so it announces as one control. A bare Switch reads out as
    // "switch, off" with no hint of what it switches.
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, onValueChange = onChange, role = Role.Switch)
            .padding(vertical = Dimens.space2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = BackbeeType.bodySmall, color = colors.textPrimary)
            Text(description, style = BackbeeType.labelSmall, color = colors.textMuted)
        }
        CarbonToggle(checked = checked, onCheckedChange = null)
    }
}
