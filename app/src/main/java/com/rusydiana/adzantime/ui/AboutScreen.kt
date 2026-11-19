package com.rusydiana.adzantime.ui

import android.Manifest
import android.content.Context
import android.os.PowerManager
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.rusydiana.adzantime.R
import com.rusydiana.adzantime.alarm.AdzanScheduler
import com.rusydiana.adzantime.data.Settings
import com.rusydiana.adzantime.prayer.PrayerEvent
import com.rusydiana.adzantime.prayer.Schedule
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One permission the app can work without, but works better with. */
private data class PermissionRow(
    val title: String,
    val why: String,
    val icon: ImageVector,
    val granted: Boolean,
    val warning: String? = null,
    val onFix: (() -> Unit)? = null,
)

@Composable
fun AboutScreen(
    settings: Settings,
    onLanguageChanged: (String) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onVisitSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Permissions change outside the app, so the list is rebuilt whenever the user re-checks.
    var checkRevision by remember { mutableIntStateOf(0) }
    val permissions = key(checkRevision) {
        permissionRows(context, onOpenAppSettings, onOpenExactAlarmSettings, onOpenBatterySettings)
    }
    val nextAdzan = key(checkRevision) {
        runCatching { Schedule.nextNotifiable(context) }.getOrNull()
    }
    val version = remember { appVersion(context) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Hero(version = version)
        LanguageCard(settings = settings, onLanguageChanged = onLanguageChanged)
        PermissionsCard(
            settings = settings,
            permissions = permissions,
            allGranted = permissions.all { it.granted },
            nextAdzan = nextAdzan,
            onRecheck = { checkRevision++ },
            onOpenAppSettings = onOpenAppSettings,
        )
        ContactCard(onVisitSupport = onVisitSupport)
    }
}

/** The identity block: icon, name, version, and the promise the app is built on. */
@Composable
private fun Hero(version: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                RoundedCornerShape(24.dp),
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // The launcher art on the launcher's own dark background, so the two match.
            Image(
                painter = painterResource(R.mipmap.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(colorResource(R.color.ic_launcher_background)),
            )
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Pill(text = stringResource(R.string.about_version_format, version))
            Text(
                stringResource(R.string.about_tagline),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                stringResource(R.string.about_blurb),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
private fun PermissionsCard(
    settings: Settings,
    permissions: List<PermissionRow>,
    allGranted: Boolean,
    nextAdzan: PrayerEvent?,
    onRecheck: () -> Unit,
    onOpenAppSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                RoundedCornerShape(24.dp),
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.section_permissions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                // One glance tells the user whether anything needs attention.
                StatusBadge(granted = allGranted)
            }

            InfoLine(
                label = stringResource(R.string.notif_test_next),
                value = nextAdzan?.let {
                    "${stringResource(it.prayer.labelRes)} · " +
                        SimpleDateFormat(
                            if (settings.use24Hour) "EEE HH:mm" else "EEE h:mm a",
                            Locale.getDefault(),
                        ).format(Date(it.timeMillis))
                } ?: stringResource(R.string.notif_test_next_none),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

            permissions.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                }
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .background(
                                if (row.granted) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
                                },
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            row.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (row.granted) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        Text(row.title, fontWeight = FontWeight.Bold)
                        Text(
                            row.why,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                        )
                    }
                    StatusBadge(granted = row.granted)
                }
                if (!row.granted) {
                    row.warning?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 50.dp),
                        )
                    }
                    row.onFix?.let { fix ->
                        TextButton(
                            onClick = fix,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                            modifier = Modifier.padding(start = 42.dp),
                        ) {
                            Text(
                                stringResource(R.string.action_fix_this),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRecheck,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.action_recheck_permissions),
                        Modifier.padding(start = 8.dp),
                    )
                }
                TextButton(onClick = onOpenAppSettings, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.Settings, contentDescription = null, Modifier.size(18.dp))
                    Text(
                        stringResource(R.string.action_open_app_settings),
                        Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

/** The language picker, moved here from the Adzan Times tab. */
@Composable
private fun LanguageCard(settings: Settings, onLanguageChanged: (String) -> Unit) {
    var selected by remember { mutableStateOf(settings.language) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f),
                RoundedCornerShape(24.dp),
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.label_language),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            LANGUAGES.forEach { (tag, labelRes) ->
                val isSelected = tag == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else Color.Transparent
                        )
                        .clickable {
                            selected = tag
                            settings.language = tag
                            onLanguageChanged(tag)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(labelRes),
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    )
                    if (isSelected) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** The invitation to get in touch. The address itself stays behind the button. */
@Composable
private fun ContactCard(onVisitSupport: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                RoundedCornerShape(24.dp),
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f)
        ),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.about_contact_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.about_contact_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
            )
            Button(
                onClick = onVisitSupport,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
            ) {
                Text(
                    stringResource(R.string.action_visit_us),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private val LANGUAGES = listOf(
    "system" to R.string.language_system,
    "en" to R.string.language_en,
    "in" to R.string.language_id,
)

/** A green "Granted" or red "Not granted" chip. */
@Composable
private fun StatusBadge(granted: Boolean) {
    val color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            if (granted) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color,
        )
        Text(
            stringResource(if (granted) R.string.permission_granted else R.string.permission_denied),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(50),
            )
            .padding(horizontal = 12.dp, vertical = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    )
}

@Composable
private fun InfoLine(label: String, value: String, valueColor: Color? = null) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Doze exemption: without it an exact alarm can be deferred indefinitely on some ROMs. */
internal fun isBatteryUnrestricted(context: Context): Boolean =
    runCatching {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }.getOrDefault(false)

@Composable
private fun permissionRows(
    context: Context,
    onOpenAppSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
): List<PermissionRow> = listOf(
    PermissionRow(
        title = stringResource(R.string.permission_location),
        why = stringResource(R.string.permission_location_why),
        icon = Icons.Filled.Place,
        granted = isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION),
        onFix = onOpenAppSettings,
    ),
    PermissionRow(
        title = stringResource(R.string.permission_notifications),
        why = stringResource(R.string.permission_notifications_why),
        icon = Icons.Filled.Notifications,
        granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS),
        onFix = onOpenAppSettings,
    ),
    PermissionRow(
        title = stringResource(R.string.permission_exact_alarm),
        why = stringResource(R.string.permission_exact_alarm_why),
        icon = Icons.Filled.DateRange,
        granted = AdzanScheduler.canScheduleExact(context),
        warning = stringResource(R.string.notif_test_exact_why),
        onFix = onOpenExactAlarmSettings,
    ),
    PermissionRow(
        title = stringResource(R.string.notif_test_battery),
        why = stringResource(R.string.notif_test_battery_why),
        icon = Icons.Filled.Warning,
        granted = isBatteryUnrestricted(context),
        warning = stringResource(R.string.notif_test_battery_hint),
        onFix = onOpenBatterySettings,
    ),
)

private fun isGranted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun appVersion(context: Context): String =
    runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "—"
