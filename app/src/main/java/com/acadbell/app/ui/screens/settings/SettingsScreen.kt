package com.acadbell.app.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.acadbell.app.alarm.AlarmSoundPlayer
import com.acadbell.app.domain.AlarmSoundChoice
import com.acadbell.app.ui.components.AcadCard
import com.acadbell.app.ui.components.AcadPill
import com.acadbell.app.ui.components.InfoBanner
import com.acadbell.app.ui.components.SectionHeader
import com.acadbell.app.ui.theme.Shape
import com.acadbell.app.ui.icons.AcadIcons

@Composable
fun SettingsScreen(
    onOpenDiagnostics: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val sound by viewModel.defaultSound.collectAsStateWithLifecycle()
    val vibration by viewModel.defaultVibration.collectAsStateWithLifecycle()
    val snooze by viewModel.defaultSnooze.collectAsStateWithLifecycle()
    val keepAwake by viewModel.keepScreenOn.collectAsStateWithLifecycle()
    val perms by viewModel.permissions.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refreshPermissions(context) }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
            viewModel.refreshPermissions(context)
        }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel.refreshPermissions(context) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? -> uri?.let(viewModel::exportBackup) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? -> uri?.let(viewModel::importBackup) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 12.dp, 20.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text("Settings", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        "ACADBELL works fully offline. Nothing leaves this device unless you export it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // ── Reliability ──
            item { SectionHeader(title = "Alarm reliability") }

            item {
                InfoBanner(
                    title = if (perms.notificationsGranted && perms.exactAlarmsGranted) "Alarms are ready" else "Some permissions are missing",
                    message = if (perms.notificationsGranted && perms.exactAlarmsGranted)
                        "Notifications and exact alarms are both enabled. ${perms.armedSummary}"
                    else
                        "Without these, reading alarms may arrive late or silently. Fix the items marked below.",
                    icon = if (perms.notificationsGranted && perms.exactAlarmsGranted) AcadIcons.CheckCircle else AcadIcons.Warning,
                    tint = if (perms.notificationsGranted && perms.exactAlarmsGranted) Color(0xFF2E7D32) else Color(0xFFF9A825)
                )
            }

            item {
                PermissionRow(
                    icon = AcadIcons.Bell,
                    title = "Notifications",
                    description = "Required for start and end alarms to appear.",
                    granted = perms.notificationsGranted,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openAppNotificationSettings(context)
                        }
                    }
                )
            }

            item {
                PermissionRow(
                    icon = AcadIcons.Alarm,
                    title = "Exact alarms",
                    description = "Lets ACADBELL ring at the exact minute your session starts and ends.",
                    granted = perms.exactAlarmsGranted,
                    onClick = { openExactAlarmSettings(context) }
                )
            }

            item {
                PermissionRow(
                    icon = AcadIcons.Battery,
                    title = "Battery optimisation",
                    description = if (perms.batteryOptimised)
                        "ACADBELL is exempt from battery optimisation — best reliability."
                    else
                        "Some phone brands (Xiaomi, Infinix, Tecno, Huawei) delay alarms unless ACADBELL is exempt.",
                    granted = perms.batteryOptimised,
                    onClick = { openBatterySettings(context) }
                )
            }

            item {
                AcadCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AcadIcons.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Next armed alarm", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(perms.nextAlarmLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionChip("Test in 60s") { viewModel.testAlarmNow() }
                        ActionChip("Re-arm all") { viewModel.rescheduleAllAlarms() }
                    }
                }
            }

            // ── Appearance ──
            item { SectionHeader(title = "Appearance") }
            item {
                AcadCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AcadIcons.DarkMode, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Theme", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Light, dark, or follow the system", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (value, label) ->
                            val selected = theme == value
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(Shape.pill)
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .clickable { viewModel.setTheme(value) }
                                    .padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Keep screen on while reading", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Stops the timer view from sleeping", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = keepAwake, onCheckedChange = viewModel::setKeepScreenOn)
                    }
                }
            }

            // ── Alarm defaults ──
            item { SectionHeader(title = "Alarm defaults") }
            item {
                AcadCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AcadIcons.Volume, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Default sound", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Used for new reading sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ActionChip("Preview") {
                            AlarmSoundPlayer.play(context, sound, vibration, loop = false)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    AlarmSoundChoice.entries.forEach { choice ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(Shape.cardSmall)
                                .clickable { viewModel.setSound(choice.name) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (sound == choice.name) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outline
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (sound == choice.name) {
                                    Box(Modifier.size(7.dp).clip(CircleShape).background(Color.White))
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Text(choice.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Vibrate by default", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                        Switch(checked = vibration, onCheckedChange = viewModel::setVibration)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Snooze length", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 20).forEach { mins ->
                            val selected = snooze == mins
                            Box(
                                modifier = Modifier
                                    .clip(Shape.pill)
                                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.setSnooze(mins) }
                                    .padding(horizontal = 14.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    "${mins}m",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // ── Data ──
            item { SectionHeader(title = "Backup & data") }
            item {
                AcadCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AcadIcons.Backup, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Export backup", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Saves courses, topics, sessions and history to a JSON file you choose.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ActionChip("Export") {
                            exportLauncher.launch("acadbell-backup-${System.currentTimeMillis()}.json")
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AcadIcons.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Restore backup", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("Merges a previously exported ACADBELL file into this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        ActionChip("Restore") { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                    }
                }
            }

            item {
                AcadCard(padding = 14.dp, onClick = onOpenDiagnostics) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AcadIcons.Bug, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Diagnostics", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text("See scheduled alarms, session events and recovery logs.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        AcadPill("View")
                    }
                }
            }

            // ── About ──
            item { SectionHeader(title = "About") }
            item {
                AcadCard(padding = 14.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AcadIcons.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("ACADBELL 1.0.0", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                            Text("Plan your reading. Stay consistent. Track your progress.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Free and offline. No accounts, no ads, no internet permission. " +
                            "Your reading data stays in a local database on this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    AcadCard(padding = 14.dp, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (granted) Color(0xFF2E7D32).copy(alpha = 0.12f)
                        else Color(0xFFF9A825).copy(alpha = 0.15f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = if (granted) Color(0xFF2E7D32) else Color(0xFFF9A825), modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AcadPill(
                if (granted) "Ready" else "Fix",
                color = if (granted) Color(0xFF2E7D32) else Color(0xFFF9A825)
            )
        }
    }
}

@Composable
private fun ActionChip(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(Shape.pill)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
    }
}

private fun openExactAlarmSettings(context: android.content.Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

private fun openBatterySettings(context: android.content.Context) {
    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as PowerManager
    if (pm.isIgnoringBatteryOptimizations(context.packageName)) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return
    }
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private fun openAppNotificationSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
