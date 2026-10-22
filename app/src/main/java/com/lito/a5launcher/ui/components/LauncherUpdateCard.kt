package com.lito.a5launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import com.lito.a5launcher.BuildConfig
import com.lito.a5launcher.GitHubLauncherUpdates
import com.lito.a5launcher.LauncherRelease
import com.lito.a5launcher.LauncherUpdateInstaller
import com.lito.a5launcher.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

@Composable
internal fun LauncherUpdateCard(
    busy: Boolean,
    installMessage: String?,
    onPrepared: (LauncherUpdateInstaller.PreparationResult) -> Unit,
    onSelectLocalApk: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updates = remember { GitHubLauncherUpdates() }
    var release by remember { mutableStateOf<LauncherRelease?>(null) }
    var checking by remember { mutableStateOf(true) }
    var downloading by remember { mutableStateOf(false) }
    var checkFailed by remember { mutableStateOf(false) }
    var downloadFailed by remember { mutableStateOf(false) }

    suspend fun checkRelease() {
        checking = true
        checkFailed = false
        try {
            release = runInterruptible(Dispatchers.IO) { updates.latest() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            release = null
            checkFailed = true
        } finally {
            checking = false
        }
    }

    LaunchedEffect(Unit) { checkRelease() }
    val working = busy || checking || downloading
    val available = release?.isNewerThan(BuildConfig.VERSION_NAME) == true
    val status = when {
        downloading -> stringResource(R.string.launcher_release_downloading)
        checking -> null
        checkFailed -> stringResource(R.string.launcher_release_check_failed)
        downloadFailed -> stringResource(R.string.launcher_release_download_failed)
        installMessage != null -> installMessage
        else -> null
    }
    val installLabel = if (checking) stringResource(R.string.launcher_release_checking) else release?.let { published ->
        if (available) stringResource(R.string.launcher_release_install_version, published.version)
        else stringResource(R.string.launcher_release_current)
    } ?: stringResource(R.string.launcher_release_install)

    SettingsCard(modifier) {
        SettingsSectionTitle(stringResource(R.string.system_update))
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.launcher_release_description), color = SettingsPalette.MutedText, fontSize = 10.sp, lineHeight = 14.sp)
        release?.let {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth()
                    .background(SettingsPalette.Control, RoundedCornerShape(7.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.launcher_release_version), color = SettingsPalette.MutedText, fontSize = 10.sp)
                Text(it.version, color = SettingsPalette.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.weight(1f))
        status?.let {
            Text(it, color = if (checkFailed || downloadFailed) SettingsPalette.Danger else SettingsPalette.Accent, fontSize = 10.sp, lineHeight = 14.sp)
            Spacer(Modifier.height(8.dp))
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsActionButton(
                text = installLabel,
                controlHeight = SettingsDimensions.ActionHeight,
                modifier = Modifier.weight(1f),
                enabled = available && !working,
                onClick = {
                    val selected = release ?: return@SettingsActionButton
                    downloading = true
                    downloadFailed = false
                    scope.launch {
                        try {
                            val prepared = runInterruptible(Dispatchers.IO) {
                                val apk = updates.download(selected, context.cacheDir)
                                try {
                                    LauncherUpdateInstaller.prepare(context, apk.toUri())
                                } finally {
                                    apk.delete()
                                }
                            }
                            onPrepared(prepared)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            downloadFailed = true
                        } finally {
                            downloading = false
                        }
                    }
                },
            )
            IconButton(
                onClick = { scope.launch { checkRelease() } },
                modifier = Modifier.size(48.dp),
                enabled = !working,
                colors = IconButtonDefaults.iconButtonColors(contentColor = SettingsPalette.Accent),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.launcher_release_check))
            }
        }
        Spacer(Modifier.height(8.dp))
        SettingsActionButton(
            stringResource(R.string.launcher_release_local), SettingsDimensions.ActionHeight,
            Modifier.fillMaxWidth(), enabled = !working,
        ) {
            checkFailed = false
            downloadFailed = false
            onSelectLocalApk()
        }
    }
}
