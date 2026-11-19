package com.rusydiana.adzantime.ui

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.pm.PackageManager
import android.content.ComponentName
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.delay
import com.rusydiana.adzantime.R
import com.rusydiana.adzantime.alarm.AdzanNotifier
import com.rusydiana.adzantime.alarm.AdzanScheduler
import com.rusydiana.adzantime.alarm.AdzanService
import com.rusydiana.adzantime.alarm.PreviewPlayer
import androidx.core.content.ContextCompat
import com.rusydiana.adzantime.data.AdzanSound
import com.rusydiana.adzantime.data.CustomSound
import com.rusydiana.adzantime.data.Settings
import com.rusydiana.adzantime.data.WidgetHealth
import com.rusydiana.adzantime.location.LocationFinder
import com.rusydiana.adzantime.prayer.Madhab
import com.rusydiana.adzantime.prayer.Method
import com.rusydiana.adzantime.prayer.Prayer
import com.rusydiana.adzantime.prayer.Schedule
import com.rusydiana.adzantime.widget.AdzanSquareWidgetProvider
import com.rusydiana.adzantime.widget.AdzanWidgetProvider
import android.icu.util.IslamicCalendar
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** The four destinations in the bottom bar, in display order. */
private enum class Tab(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    TIMES(R.string.tab_times, Icons.Filled.Notifications, Icons.Outlined.Notifications),
    QIBLA(R.string.tab_qibla, Icons.Filled.Place, Icons.Outlined.Place),
    CALENDAR(R.string.tab_calendar, Icons.Filled.DateRange, Icons.Outlined.DateRange),
    ABOUT(R.string.tab_about, Icons.Filled.Info, Icons.Outlined.Info),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdzanApp(
    onRequestLocationPermission: ((() -> Unit)) -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onVisitSupport: () -> Unit,
    onLanguageChanged: (String) -> Unit,
) {
    val context = LocalContext.current
    val settings = remember { Settings.get(context) }
    var tab by remember { mutableStateOf(Tab.TIMES) }
    // Set by the calendar screen so the date in the app bar can page its grid back to today.
    var calendarGoToToday by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showLocation by remember { mutableStateOf(false) }
    var locationBusy by remember { mutableStateOf(false) }
    // Held here, not read from Settings on each recomposition: the store is not reactive, so
    // an edit in the dialog would otherwise not reach the label in the card behind it.
    var city by remember { mutableStateOf(settings.cityName) }

    // Bumping this recomputes everything derived from settings; the store itself is not reactive.
    var revision by remember { mutableIntStateOf(0) }
    // When what is on screen stops being true: the next prayer's instant. Past it the cards
    // are showing the wrong row as current, which is what the foreground check below tests.
    var staleAfter by remember {
        mutableLongStateOf(Schedule.currentAndNext(context).second?.timeMillis ?: Long.MAX_VALUE)
    }
    fun invalidate() {
        revision++
        staleAfter = Schedule.currentAndNext(context).second?.timeMillis ?: Long.MAX_VALUE
        AdzanScheduler.rescheduleAll(context)
        AdzanWidgetProvider.refreshAll(context)
    }

    fun updateLocation() {
        locationBusy = true
        LocationFinder.requestOnce(context) { result ->
            locationBusy = false
            when (result) {
                is LocationFinder.Result.Success -> {
                    settings.latitude = result.latitude
                    settings.longitude = result.longitude
                    settings.elevation = result.elevation
                    settings.hasLocation = true
                    result.city?.let {
                        settings.cityName = it
                        city = it
                    }
                    invalidate()
                }
                LocationFinder.Result.PermissionMissing ->
                    onRequestLocationPermission { updateLocation() }
                LocationFinder.Result.Unavailable ->
                    Toast.makeText(context, R.string.location_unavailable, Toast.LENGTH_LONG).show()
            }
        }
    }

    // The alarm chain can die while the app is away — a force-stop, an OEM battery manager —
    // and nothing in the app runs to notice, so the widget's countdown keeps running past a
    // prayer that was never called. Coming to the foreground is the one moment we can put it
    // right: re-arm from the current clock, then stamp the miss as seen so the countdown
    // rebases onto the next prayer instead of staying negative all day. Rescheduling goes
    // first — the stamp would otherwise suppress a catch-up that is still inside its window.
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        AdzanScheduler.rescheduleAll(context)
        WidgetHealth.markSeen(context)
        // Re-key the cards only once a prayer has actually passed while the app was away.
        // An unconditional bump would also close the dialog a user left open to go and grant
        // a permission in Settings, which is the one place this screen sends them.
        if (System.currentTimeMillis() >= staleAfter) invalidate()
        AdzanWidgetProvider.refreshAll(context)
    }

    // One refresh per launch. A first run has no coordinates at all, so it looks regardless of
    // the toggle — the times would otherwise be for the default city.
    LaunchedEffect(Unit) {
        if (settings.autoUpdateLocation || !settings.hasLocation) updateLocation()
    }

    if (showLocation) {
        LocationDialog(
            settings = settings,
            city = city,
            busy = locationBusy,
            onCityChange = { city = it },
            onUpdate = ::updateLocation,
            onDismiss = { showLocation = false },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (tab == Tab.TIMES) stringResource(R.string.app_name)
                            else stringResource(tab.labelRes),
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    actions = {
                        // Today's date belongs in the calendar's header, not in a card that
                        // pushes the grid down the screen.
                        if (tab == Tab.CALENDAR) {
                            TodayHeaderDate(onClick = { calendarGoToToday?.invoke() })
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                    ),
                )
            },
        ) { inner ->
            // The nav pill is drawn over the content rather than in the Scaffold's bottomBar slot,
            // so nothing paints a solid strip behind it and it reads as floating. The content pays
            // for the space it covers with its own bottom padding instead.
            val screenModifier = Modifier
                .fillMaxSize()
                .padding(
                    top = inner.calculateTopPadding(),
                    bottom = inner.calculateBottomPadding() + NAV_BAR_SPACE,
                )
            when (tab) {
                Tab.TIMES -> PrayerTimesScreen(
                    settings = settings,
                    revision = revision,
                    locationCity = city,
                    locationBusy = locationBusy,
                    onOpenLocation = { showLocation = true },
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                    onOpenFullScreenIntentSettings = onOpenFullScreenIntentSettings,
                    onOpenBatterySettings = onOpenBatterySettings,
                    invalidate = ::invalidate,
                    modifier = screenModifier,
                )
                // Keyed on revision so a new location redraws the compass bearing.
                Tab.QIBLA -> key(revision) { QiblaScreen(settings, screenModifier) }
                Tab.CALENDAR -> HijriCalendarScreen(
                    settings = settings,
                    onGoToTodayReady = { calendarGoToToday = it },
                    modifier = screenModifier,
                )
                Tab.ABOUT -> AboutScreen(
                    settings = settings,
                    onLanguageChanged = onLanguageChanged,
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                    onOpenAppSettings = onOpenAppSettings,
                    onOpenNotificationSettings = onOpenNotificationSettings,
                    onOpenBatterySettings = onOpenBatterySettings,
                    onVisitSupport = onVisitSupport,
                    modifier = screenModifier,
                )
            }
        }
        FloatingNavBar(
            current = tab,
            onSelect = { tab = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * The Hijri and Gregorian date for today, sized to sit in the app bar's action slot. Tapping it
 * brings the calendar grid back to the current month.
 */
@Composable
private fun TodayHeaderDate(onClick: () -> Unit) {
    val hijri = remember {
        IslamicCalendar().apply {
            calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
        }
    }
    Column(
        Modifier
            .padding(end = 12.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Text(
            "${hijri.get(Calendar.DAY_OF_MONTH)} " +
                "${stringResource(hijriMonthNameRes(hijri.get(Calendar.MONTH)))} " +
                "${hijri.get(Calendar.YEAR)} H",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            remember { SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault()).format(Date()) },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/** Height of the floating pill plus its margins; the content reserves this much at the bottom. */
private val NAV_BAR_SPACE = 86.dp

/**
 * The bottom navigation, as a pill that floats clear of the screen edges rather than a bar
 * welded to the bottom. All four destinations share the width evenly and all four keep their
 * label — the selection is carried by a soft tinted well behind the icon and label, not by
 * hiding the other three labels.
 *
 * It is still the Scaffold's bottomBar, so its full height — margins and gesture inset
 * included — is handed to the content as padding and nothing ends up underneath it.
 */
@Composable
private fun FloatingNavBar(current: Tab, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
            shadowElevation = 12.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            modifier = Modifier.fillMaxWidth().height(68.dp),
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Tab.entries.forEach { entry ->
                    NavPill(entry = entry, selected = entry == current) { onSelect(entry) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.NavPill(entry: Tab, selected: Boolean, onClick: () -> Unit) {
    val content = if (selected) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

    Column(
        modifier = Modifier
            // Equal weights: the four cells stay a grid whichever one is selected.
            .weight(1f)
            .padding(vertical = 6.dp, horizontal = 3.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            if (selected) entry.selectedIcon else entry.icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        Text(
            stringResource(entry.labelRes),
            modifier = Modifier.padding(top = 3.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content,
            maxLines = 1,
        )
    }
}

@Composable
private fun PrayerTimesScreen(
    settings: Settings,
    revision: Int,
    locationCity: String,
    locationBusy: Boolean,
    onOpenLocation: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    invalidate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            key(revision) {
                TodayCard(
                    settings = settings,
                    invalidate = invalidate,
                    locationCity = locationCity,
                    locationBusy = locationBusy,
                    onOpenLocation = onOpenLocation,
                    onOpenExactAlarmSettings = onOpenExactAlarmSettings,
                    onOpenFullScreenIntentSettings = onOpenFullScreenIntentSettings,
                    onOpenBatterySettings = onOpenBatterySettings,
                )
            }
        }
        item { key(revision) { CalculationCard(settings, invalidate) } }
        item { key(revision) { WidgetCard() } }
    }
}

/** Re-opens the per-widget style screen for anything the user has already placed. */
@Composable
private fun WidgetCard() {
    val context = LocalContext.current
    // Both shapes are listed together: the configuration screen is the same for either.
    val ids = remember {
        val manager = AppWidgetManager.getInstance(context)
        val all = listOf(AdzanWidgetProvider::class.java, AdzanSquareWidgetProvider::class.java)
            .flatMap { manager?.getAppWidgetIds(ComponentName(context, it))?.toList().orEmpty() }
            .distinct()
        // A launcher also allocates an id while the widget is being dragged or previewed, and
        // an abandoned one keeps showing up here — it just never gets laid out, so it has no
        // size in its options. Those are the phantom entries; drop them, but never end up with
        // an empty list because a real widget has not been measured yet.
        val placed = all.filter { id ->
            val options = manager?.getAppWidgetOptions(id)
            (options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0) > 0
        }
        placed.ifEmpty { all }
    }
    SectionCard(stringResource(R.string.section_widget), Icons.Filled.Home) {
        Text(
            stringResource(R.string.widget_configure_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (ids.isEmpty()) {
            Text(
                stringResource(R.string.widget_none_placed),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            return@SectionCard
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ids.forEachIndexed { index, id ->
                OutlinedButton(
                    onClick = {
                        context.startActivity(
                            Intent(context, WidgetConfigActivity::class.java)
                                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    Text(
                        if (ids.size == 1) stringResource(R.string.action_configure_widget)
                        else "${stringResource(R.string.action_configure_widget)} ${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * One settings group. Deliberately tight: an icon-led caption instead of a title row, 14dp
 * padding and 6dp between children, so a form of eight groups stays scannable on one screen
 * instead of stretching over three.
 */
@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                RoundedCornerShape(20.dp),
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    title.uppercase(Locale.getDefault()),
                    modifier = Modifier.weight(1f).padding(start = 7.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

/** A label on the left, its control on the right, on one line. The form's basic unit. */
@Composable
private fun SettingRow(
    label: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        }
        control()
    }
}

/**
 * The screen's anchor. A hero states the one thing the app exists to answer — which prayer is
 * next and how long there is — and the day's list sits under it with the current window
 * marked, so the whole schedule is still one glance away.
 */
@Composable
private fun TodayCard(
    settings: Settings,
    invalidate: () -> Unit,
    locationCity: String,
    locationBusy: Boolean,
    onOpenLocation: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val context = LocalContext.current
    val schedule = remember { Schedule.today(context) }
    val format = remember(settings.use24Hour) {
        SimpleDateFormat(if (settings.use24Hour) "HH:mm" else "h:mm a", Locale.getDefault())
    }
    // Half-minute ticks: enough for a countdown shown to the minute, cheap enough to ignore.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(30_000L)
        }
    }
    val (current, next) = remember(now) { Schedule.currentAndNext(context, now) }
    // The prayer whose notification settings are open, or null when the list is just a list.
    var configuring by remember { mutableStateOf<Prayer?>(null) }

    configuring?.let { prayer ->
        PrayerNotificationDialog(
            prayer = prayer,
            settings = settings,
            onDismiss = {
                PreviewPlayer.stop()
                configuring = null
                // Rescheduling is deferred to here: it recomposes the whole card, which would
                // otherwise tear the dialog down the moment a sound was picked.
                invalidate()
            },
        )
    }

    SectionCard(
        title = stringResource(R.string.section_today),
        icon = Icons.Filled.DateRange,
        // The place these times are for, opposite the caption they belong to.
        trailing = {
            LocationLabel(
                city = locationCity,
                busy = locationBusy,
                onClick = onOpenLocation,
            )
        },
    ) {
        if (next != null) {
            NextPrayerHero(
                title = if (next.isJumuah) stringResource(R.string.prayer_jumuah)
                else stringResource(next.prayer.labelRes),
                time = format.format(Date(next.timeMillis)),
                remaining = remainingLabel(next.timeMillis - now),
            )
        }

        Text(
            SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date(now)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        Prayer.entries.forEach { prayer ->
            val event = schedule.events.first { it.prayer == prayer }
            val isCurrent = current?.prayer == prayer
            val isNext = next?.prayer == prayer
            val accent = MaterialTheme.colorScheme.primary
            val notifyOn = settings.isNotificationEnabled(prayer)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isCurrent) accent.copy(alpha = 0.12f) else Color.Transparent
                    )
                    // Each time carries its own notification setting, opened from the time itself.
                    .clickable { configuring = prayer }
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A filled dot marks the window you are in; a hollow one marks what is coming.
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isCurrent -> accent
                                isNext -> accent.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                            }
                        )
                )
                Column(Modifier.weight(1f).padding(start = 10.dp, end = 8.dp)) {
                    Text(
                        if (event.isJumuah) stringResource(R.string.prayer_jumuah)
                        else stringResource(prayer.labelRes),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                    )
                    // What this time will actually do, in one quiet line.
                    Text(
                        if (notifyOn) stringResource(settings.soundOf(prayer).labelRes)
                        else stringResource(R.string.label_sound_off),
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (notifyOn) 0.6f else 0.4f
                        ),
                    )
                }
                Text(
                    format.format(Date(event.timeMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
                )
                Icon(
                    if (notifyOn) Icons.Filled.Notifications else Icons.Outlined.Notifications,
                    contentDescription = null,
                    modifier = Modifier.padding(start = 8.dp).size(17.dp),
                    tint = if (notifyOn) accent
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                )
            }
        }
        Text(
            stringResource(R.string.prayer_config_hint),
            modifier = Modifier.padding(start = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )

        // The clock format belongs with the times it formats, not in a settings group at the
        // bottom of the screen.
        HorizontalDivider(
            modifier = Modifier.padding(vertical = 2.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
        )
        SettingRow(
            label = stringResource(R.string.label_auto_location),
            subtitle = stringResource(R.string.label_auto_location_hint),
        ) {
            var auto by remember { mutableStateOf(settings.autoUpdateLocation) }
            Switch(
                checked = auto,
                onCheckedChange = {
                    auto = it
                    settings.autoUpdateLocation = it
                },
            )
        }
        SettingRow(label = stringResource(R.string.label_24h)) {
            Switch(
                checked = settings.use24Hour,
                onCheckedChange = {
                    settings.use24Hour = it
                    invalidate()
                },
            )
        }

        // Two switches that colour the whole list, sitting with it rather than in a group of
        // their own; per-prayer choices live in each row's dialog.
        AlarmStyleSetting(
            settings, invalidate, onOpenFullScreenIntentSettings, onOpenBatterySettings,
        )
        ExactAlarmWarning(onOpenExactAlarmSettings)
        CustomSoundSection(settings, invalidate)
    }
}

/**
 * One prayer's notification settings. A dialog rather than an inline expansion: the switch and
 * the full list of sounds would not fit a row that already carries a name, a time and a state
 * dot, and picking a sound is a short detour, not somewhere you stay.
 */
@Composable
private fun PrayerNotificationDialog(
    prayer: Prayer,
    settings: Settings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(settings.isNotificationEnabled(prayer)) }
    var sound by remember { mutableStateOf(settings.soundOf(prayer)) }
    val choices = when (prayer) {
        Prayer.FAJR -> AdzanSound.fajrChoices
        Prayer.SYURUQ -> AdzanSound.syuruqChoices
        else -> AdzanSound.standardChoices
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
        dismissButton = {
            TextButton(onClick = { PreviewPlayer.stop() }) {
                Text(stringResource(R.string.action_stop_preview))
            }
        },
        title = {
            Text(stringResource(prayer.labelRes), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                SettingRow(label = stringResource(R.string.label_notify_this_prayer)) {
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            settings.setNotificationEnabled(prayer, it)
                        },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                )
                Text(
                    stringResource(R.string.label_sound),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                )
                // The list scrolls on its own so the dialog never outgrows a short screen.
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    choices.forEach { option ->
                        val selected = option == sound
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    } else {
                                        Color.Transparent
                                    }
                                )
                                .clickable(enabled = enabled) {
                                    if (option.isCustom &&
                                        Settings.get(context).customSoundUri == null
                                    ) {
                                        Toast.makeText(
                                            context,
                                            R.string.custom_sound_not_set,
                                            Toast.LENGTH_LONG,
                                        ).show()
                                        return@clickable
                                    }
                                    sound = option
                                    settings.setSound(prayer, option)
                                    // Auditioning the choice is the point of picking one.
                                    AdzanService.preview(context, option)
                                }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                stringResource(option.labelRes),
                                modifier = Modifier.padding(start = 10.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                color = if (enabled) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
                // Friday's Dhuhr is still Dhuhr, so its exception belongs in Dhuhr's own sheet.
                if (prayer == Prayer.DHUHR) {
                    JumuahSection(settings)
                }
            }
        },
    )
}

/**
 * A group inside a card: a divider and a small caption, so the Today card can carry several
 * subjects without each one needing a card of its own.
 */
@Composable
private fun SubSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
    )
    Text(
        title.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.8.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
        modifier = Modifier.padding(bottom = 2.dp),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

@Composable
private fun NextPrayerHero(title: String, time: String, remaining: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.label_next_prayer).uppercase(Locale.getDefault()),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f),
            )
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                remaining,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
            )
        }
        Text(
            time,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimary,
            textAlign = TextAlign.End,
        )
    }
}

/** "in 2h 14m", or "in 3m" once the hour is gone. */
@Composable
private fun remainingLabel(millis: Long): String {
    val total = (millis.coerceAtLeast(0L) / 60_000L).toInt()
    val hours = total / 60
    val minutes = total % 60
    return if (hours > 0) stringResource(R.string.remaining_hours_format, hours, minutes)
    else stringResource(R.string.remaining_minutes_format, minutes)
}

/** Just the place name; the coordinates live in the dialog this opens. */
@Composable
private fun LocationLabel(city: String, busy: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Place,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            when {
                busy -> stringResource(R.string.action_updating_location)
                city.isBlank() -> stringResource(R.string.location_header_unset)
                else -> city
            },
            modifier = Modifier.padding(start = 5.dp).widthIn(max = 150.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The city name, the coordinates and the manual refresh. */
@Composable
private fun LocationDialog(
    settings: Settings,
    city: String,
    busy: Boolean,
    onCityChange: (String) -> Unit,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_done)) }
        },
        title = { Text(stringResource(R.string.section_location), fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = city,
                    onValueChange = {
                        onCityChange(it)
                        settings.cityName = it
                        AdzanWidgetProvider.refreshAll(context)
                    },
                    label = { Text(stringResource(R.string.label_city)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            R.string.coordinates_format,
                            String.format(Locale.US, "%.4f", settings.latitude),
                            String.format(Locale.US, "%.4f", settings.longitude),
                        ),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    OutlinedButton(
                        onClick = onUpdate,
                        enabled = !busy,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    ) {
                        Text(
                            stringResource(
                                if (busy) R.string.action_updating_location
                                else R.string.action_update_location
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
                Text(
                    stringResource(R.string.location_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
        },
    )
}

/** A quiet caption inside a section, for a group of rows that needs naming. */
@Composable
private fun SubHeading(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 4.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
    )
}

@Composable
private fun CalculationCard(settings: Settings, invalidate: () -> Unit) {
    var method by remember { mutableStateOf(settings.method) }
    var fajrAngle by remember { mutableStateOf(settings.customFajrAngle) }
    var ishaAngle by remember { mutableStateOf(settings.customIshaAngle) }

    SectionCard(stringResource(R.string.section_calculation), Icons.Filled.Build) {
        EnumDropdown(
            label = stringResource(R.string.label_method),
            options = Method.entries,
            selected = method,
            labelOf = { stringResource(it.labelRes) },
        ) {
            method = it
            settings.method = it
            invalidate()
        }
        // What the method actually changes, spelled out: every method here differs only in
        // the sun angles it puts Fajr and Isha at.
        Text(
            methodSummary(method, fajrAngle, ishaAngle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        if (method == Method.CUSTOM) {
            SettingRow(label = stringResource(R.string.label_fajr_angle)) {
                Stepper(
                    value = angleText(fajrAngle),
                    emphasised = true,
                    onDecrement = {
                        fajrAngle = (fajrAngle - 0.5).coerceAtLeast(10.0)
                        settings.customFajrAngle = fajrAngle
                        invalidate()
                    },
                    onIncrement = {
                        fajrAngle = (fajrAngle + 0.5).coerceAtMost(25.0)
                        settings.customFajrAngle = fajrAngle
                        invalidate()
                    },
                )
            }
            SettingRow(label = stringResource(R.string.label_isha_angle)) {
                Stepper(
                    value = angleText(ishaAngle),
                    emphasised = true,
                    onDecrement = {
                        ishaAngle = (ishaAngle - 0.5).coerceAtLeast(10.0)
                        settings.customIshaAngle = ishaAngle
                        invalidate()
                    },
                    onIncrement = {
                        ishaAngle = (ishaAngle + 0.5).coerceAtMost(25.0)
                        settings.customIshaAngle = ishaAngle
                        invalidate()
                    },
                )
            }
        }
        EnumDropdown(
            label = stringResource(R.string.label_madhab),
            options = Madhab.entries,
            selected = settings.madhab,
            labelOf = { stringResource(it.labelRes) },
        ) {
            settings.madhab = it
            invalidate()
        }
        Text(
            stringResource(R.string.madhab_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )

        SubHeading(stringResource(R.string.label_offsets))
        Text(
            stringResource(R.string.offsets_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Prayer.entries.forEach { prayer ->
            OffsetRow(stringResource(prayer.labelRes), settings.offsetOf(prayer)) {
                settings.setOffset(prayer, it)
                invalidate()
            }
        }
    }
}

/** "Fajr at 20.0 below the horizon, Isha at 18.0" — or the fixed interval Umm al-Qura uses. */
@Composable
private fun methodSummary(method: Method, customFajr: Double, customIsha: Double): String {
    val fajr = angleText(if (method == Method.CUSTOM) customFajr else method.fajrAngle)
    return if (method.ishaInterval > 0) {
        stringResource(R.string.method_summary_interval, fajr, method.ishaInterval)
    } else {
        stringResource(
            R.string.method_summary_angles,
            fajr,
            angleText(if (method == Method.CUSTOM) customIsha else method.ishaAngle),
        )
    }
}

private fun angleText(angle: Double): String = String.format(Locale.US, "%.1f\u00B0", angle)

@Composable
private fun OffsetRow(label: String, minutes: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
        )
        Stepper(
            value = stringResource(R.string.minutes_format, minutes),
            emphasised = minutes != 0,
            onDecrement = { onChange((minutes - 1).coerceAtLeast(-30)) },
            onIncrement = { onChange((minutes + 1).coerceAtMost(30)) },
        )
    }
}

/**
 * A minus / value / plus group in one rounded well. Half the height of the three TextButtons
 * it replaces, which matters when six of them sit in a row.
 */
@Composable
private fun Stepper(
    value: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    emphasised: Boolean = false,
    valueWidth: Dp = 56.dp,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton("\u2212", onDecrement)
        Text(
            value,
            modifier = Modifier.width(valueWidth),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasised) FontWeight.Bold else FontWeight.Normal,
            color = if (emphasised) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
        StepperButton("+", onIncrement)
    }
}

@Composable
private fun StepperButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AlarmStyleSetting(
    settings: Settings,
    invalidate: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenBatterySettings: () -> Unit,
) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        var alarmStyle by remember { mutableStateOf(settings.alarmStyleNotifications) }
        SettingRow(
            label = stringResource(R.string.label_alarm_style),
            subtitle = stringResource(R.string.label_alarm_style_hint),
        ) {
            Switch(
                checked = alarmStyle,
                onCheckedChange = { on ->
                    alarmStyle = on
                    settings.alarmStyleNotifications = on
                    // Android 14 hands out the full-screen-intent grant only to alarm and
                    // calling apps, so turning this on may need one trip to system settings.
                    if (on && !AdzanNotifier.canUseFullScreenIntent(context)) {
                        Toast.makeText(
                            context, R.string.alarm_style_needs_permission, Toast.LENGTH_LONG
                        ).show()
                        onOpenFullScreenIntentSettings()
                    }
                    invalidate()
                },
            )
        }
        if (alarmStyle && !isBatteryUnrestricted(context)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp).padding(top = 2.dp),
                )
                Column(
                    Modifier.weight(1f).padding(start = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.alarm_style_battery_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                    TextButton(
                        onClick = onOpenBatterySettings,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Text(
                            stringResource(R.string.action_fix_battery_settings),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Picking or recording the one custom sound. Once set it shows up as "My own sound" in every
 * prayer's dropdown, so the choice is made once and reused.
 */
@Composable
private fun CustomSoundSection(settings: Settings, invalidate: () -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(settings.customSoundName) }
    var recording by remember { mutableStateOf(false) }
    val recorder = remember { CustomSound.Recorder(context) }

    val pickAudio = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            name = CustomSound.adopt(context, uri)
            invalidate()
        }
    }

    fun startRecording() {
        runCatching { recorder.start() }
            .onSuccess { recording = true }
            .onFailure {
                Toast.makeText(context, R.string.recording_needs_permission, Toast.LENGTH_LONG).show()
            }
    }

    val requestMic = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startRecording()
        else Toast.makeText(context, R.string.recording_needs_permission, Toast.LENGTH_LONG).show()
    }

    SubSection(stringResource(R.string.section_custom_sound)) {
        when {
            name == null -> Text(
                stringResource(R.string.custom_sound_none),
                style = MaterialTheme.typography.bodySmall,
            )
            !CustomSound.isUsable(context) -> Text(
                stringResource(R.string.custom_sound_missing),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            else -> Text(
                stringResource(R.string.custom_sound_current, name.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (recording) {
            Text(
                stringResource(R.string.recording_in_progress),
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
            OutlinedButton(onClick = {
                recording = false
                val saved = recorder.stop()
                if (saved == null) {
                    Toast.makeText(context, R.string.recording_too_short, Toast.LENGTH_LONG).show()
                } else {
                    name = saved
                    invalidate()
                }
            }) { Text(stringResource(R.string.action_stop_recording)) }
            return@SubSection
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pickAudio.launch(arrayOf("audio/*")) }) {
                Text(stringResource(R.string.action_pick_audio))
            }
            OutlinedButton(onClick = {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    startRecording()
                } else {
                    requestMic.launch(Manifest.permission.RECORD_AUDIO)
                }
            }) { Text(stringResource(R.string.action_record)) }
        }
        if (name != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    AdzanService.preview(context, AdzanSound.CUSTOM)
                }) { Text("▶") }
                TextButton(onClick = { PreviewPlayer.stop() }) {
                    Text(stringResource(R.string.action_stop_preview))
                }
                TextButton(onClick = {
                    CustomSound.clear(context)
                    name = null
                    invalidate()
                }) { Text(stringResource(R.string.action_remove_custom_sound)) }
            }
        }
    }
}

@Composable
private fun JumuahSection(settings: Settings) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(settings.jumuahEnabled) }
    var fixedMinutes by remember { mutableIntStateOf(settings.jumuahFixedMinutes) }

    SubSection(stringResource(R.string.section_jumuah)) {
        SettingRow(label = stringResource(R.string.label_jumuah_enabled)) {
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    settings.jumuahEnabled = it
                },
            )
        }
        // The rest only means anything once Friday is being treated specially.
        if (enabled) {
            EnumDropdown(
                label = stringResource(R.string.label_jumuah_sound),
                options = AdzanSound.standardChoices,
                selected = settings.jumuahSound,
                labelOf = { stringResource(it.labelRes) },
            ) {
                settings.jumuahSound = it
                AdzanService.preview(context, it)
            }
            SettingRow(label = stringResource(R.string.label_jumuah_fixed_time)) {
                Switch(
                    checked = fixedMinutes >= 0,
                    onCheckedChange = { on ->
                        fixedMinutes = if (on) 12 * 60 else -1
                        settings.jumuahFixedMinutes = fixedMinutes
                    },
                )
            }
            if (fixedMinutes >= 0) {
                SettingRow(label = stringResource(R.string.label_jumuah_khutbah_time)) {
                    Stepper(
                        value = formatMinutes(fixedMinutes),
                        emphasised = true,
                        valueWidth = 62.dp,
                        onDecrement = {
                            fixedMinutes = (fixedMinutes - 5).coerceAtLeast(0)
                            settings.jumuahFixedMinutes = fixedMinutes
                        },
                        onIncrement = {
                            fixedMinutes = (fixedMinutes + 5).coerceAtMost(23 * 60 + 55)
                            settings.jumuahFixedMinutes = fixedMinutes
                        },
                    )
                }
            }
        }
        Text(
            stringResource(R.string.jumuah_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
    }
}

/**
 * The exact-alarm problem, shown inside the Today card because that is the schedule it breaks.
 * Nothing is drawn at all once the permission is there.
 */
@Composable
private fun ExactAlarmWarning(onOpenExactAlarmSettings: () -> Unit) {
    val context = LocalContext.current
    if (AdzanScheduler.canScheduleExact(context)) return
    run {
        // A real problem with the schedule, so it is coloured like one rather than sitting in
        // the page as another line of grey hint text.
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.exact_alarm_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(
                onClick = onOpenExactAlarmSettings,
                shape = RoundedCornerShape(50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
            ) {
                Text(
                    stringResource(R.string.action_grant_exact_alarm),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    SettingRow(label = label) {
        ValueChip(
            options = options,
            selected = selected,
            labelOf = labelOf,
            onSelected = onSelected,
            modifier = Modifier.width(150.dp),
        )
    }
}

/**
 * The form's one-line value control: the current choice in a tappable well with a caret,
 * opening a menu in place. Used wherever a full-width dropdown row would cost a line.
 */
@Composable
private fun <T> ValueChip(
    options: List<T>,
    selected: T,
    labelOf: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val content =
        if (enabled) MaterialTheme.colorScheme.onSurface
        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                .clickable { expanded = true }
                .padding(start = 12.dp, end = 8.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                labelOf(selected),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = content,
                maxLines = 1,
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = content,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

private fun formatMinutes(minutes: Int): String =
    String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60)
