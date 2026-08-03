package dev.backbee.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.backbee.core.playback.ArchiveProgress
import dev.backbee.core.playback.SmartResume
import dev.backbee.ui.components.SectionLabel
import dev.backbee.ui.theme.Dimens
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // SAF rather than a storage permission: the user points at the folder
    // Syncthing already watches and nothing else on the device is touched.
    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            viewModel.setBackupFolder(uri.toString())
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Dimens.gutter),
            verticalArrangement = Arrangement.spacedBy(Dimens.gap),
        ) {
            state.activeShow?.let { show ->
                item {
                    Card("This show · ${show.title}") {
                        StepperRow(
                            label = "Playback speed",
                            value = "${ArchiveProgress.formatSpeed(show.speed)}×",
                            onDown = { viewModel.setShowSpeed(show.speed - 0.1f) },
                            onUp = { viewModel.setShowSpeed(show.speed + 0.1f) },
                        )
                        StepperRow(
                            label = "Skip intro",
                            value = "${show.skipIntroSeconds}s",
                            onDown = { viewModel.setSkipIntro(show.skipIntroSeconds - 5) },
                            onUp = { viewModel.setSkipIntro(show.skipIntroSeconds + 5) },
                        )
                        StepperRow(
                            label = "Skip outro",
                            value = "${show.skipOutroSeconds}s",
                            onDown = { viewModel.setSkipOutro(show.skipOutroSeconds - 5) },
                            onUp = { viewModel.setSkipOutro(show.skipOutroSeconds + 5) },
                        )
                    }
                }
            }

            item {
                Card("Downloads") {
                    SliderRow(
                        label = "Keep ahead",
                        value = state.settings.downloadAhead.toFloat(),
                        range = 0f..30f,
                        steps = 29,
                        display = "${state.settings.downloadAhead} episodes",
                        onChange = { viewModel.setDownloadAhead(it.roundToInt()) },
                    )
                    SliderRow(
                        label = "Storage cap",
                        value = (state.settings.storageCapBytes / (1024f * 1024 * 1024)),
                        range = 1f..64f,
                        steps = 62,
                        display = "${state.settings.storageCapBytes / (1024 * 1024 * 1024)} GB " +
                            "(${state.bytesOnDisk / (1024 * 1024)} MB used)",
                        onChange = { viewModel.setStorageCapGb(it.roundToInt()) },
                    )
                    SliderRow(
                        label = "Delete played after",
                        value = state.settings.deletePlayedAfterHours.toFloat(),
                        range = 0f..168f,
                        steps = 0,
                        display = "${state.settings.deletePlayedAfterHours} hours",
                        onChange = { viewModel.setDeletePlayedAfterHours(it.roundToInt()) },
                    )
                    SwitchRow(
                        label = "Wi-Fi only",
                        description = "Downloads wait for an unmetered connection",
                        checked = state.settings.wifiOnlyDownloads,
                        onChange = viewModel::setWifiOnly,
                    )
                }
            }

            item {
                Card("Playback") {
                    StepperRow(
                        label = "Skip forward",
                        value = "${state.settings.skipForwardSeconds}s",
                        onDown = { viewModel.setSkipForwardSeconds(state.settings.skipForwardSeconds - 5) },
                        onUp = { viewModel.setSkipForwardSeconds(state.settings.skipForwardSeconds + 5) },
                    )
                    StepperRow(
                        label = "Skip back",
                        value = "${state.settings.skipBackSeconds}s",
                        onDown = { viewModel.setSkipBackSeconds(state.settings.skipBackSeconds - 5) },
                        onUp = { viewModel.setSkipBackSeconds(state.settings.skipBackSeconds + 5) },
                    )
                    SwitchRow(
                        label = "Auto-play on Bluetooth",
                        description = "Off by default: connecting to the car leaves the app ready, not talking",
                        checked = state.settings.autoPlayOnBluetooth,
                        onChange = viewModel::setAutoPlayOnBluetooth,
                    )
                }
            }

            item {
                Card("Resume rewind") {
                    Text(
                        text = "How far back to jump when you come back to an episode, " +
                            "scaled by how long you were away.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Dimens.gap),
                    )
                    state.settings.resumeTiers.forEachIndexed { index, tier ->
                        StepperRow(
                            label = tierLabel(tier.pauseAtMostSeconds),
                            value = "${tier.rewindSeconds}s",
                            onDown = {
                                viewModel.setResumeTiers(
                                    state.settings.resumeTiers.replaceRewind(index, tier.rewindSeconds - 5)
                                )
                            },
                            onUp = {
                                viewModel.setResumeTiers(
                                    state.settings.resumeTiers.replaceRewind(index, tier.rewindSeconds + 5)
                                )
                            },
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.setResumeTiers(SmartResume.DEFAULT_TIERS) },
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        Text("Reset to defaults")
                    }
                }
            }

            item {
                Card("Backup") {
                    Text(
                        text = "A nightly snapshot of the database is written to a folder you choose. " +
                            "Point it at whatever Syncthing watches and losing the phone costs at most a day.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SwitchRow(
                        label = "Nightly checkpoint",
                        description = state.settings.backupFolderUri?.let { "Writing to a folder you picked" }
                            ?: "No folder chosen yet",
                        checked = state.settings.backupEnabled,
                        onChange = viewModel::setBackupEnabled,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Dimens.gap),
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        OutlinedButton(onClick = { pickFolder.launch(null) }, modifier = Modifier.weight(1f)) {
                            Text("Choose folder")
                        }
                        OutlinedButton(
                            onClick = viewModel::backupNow,
                            enabled = state.settings.backupFolderUri != null,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text("Back up now")
                        }
                    }
                }
            }

            item {
                Card("Feeds") {
                    Text(
                        text = "Feeds are checked once a day, quietly. New episodes append to the end " +
                            "of the archive and nothing notifies you.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = viewModel::refreshFeedsNow,
                        modifier = Modifier.padding(top = Dimens.gap),
                    ) {
                        Text("Check now")
                    }
                }
            }
        }
    }
}

private fun List<dev.backbee.core.playback.ResumeTier>.replaceRewind(index: Int, rewind: Int) =
    mapIndexed { i, tier ->
        if (i == index) tier.copy(rewindSeconds = rewind.coerceIn(0, 600)) else tier
    }

private fun tierLabel(bound: Long): String = when {
    bound == Long.MAX_VALUE -> "Longer"
    bound < 3600 -> "Under ${bound / 60} min"
    bound < 86_400 -> "Under ${bound / 3600} hr"
    else -> "Under ${bound / 86_400} day"
}

@Composable
private fun Card(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            SectionLabel(title, modifier = Modifier.padding(bottom = Dimens.gap))
            content()
        }
    }
}

@Composable
private fun StepperRow(label: String, value: String, onDown: () -> Unit, onUp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        StepButton("−", onDown)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Dimens.gap),
        )
        StepButton("+", onUp)
    }
}

@Composable
private fun StepButton(symbol: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = symbol,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SwitchRow(label: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    display: String,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.padding(vertical = 6.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                text = display,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = value.coerceIn(range),
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}
