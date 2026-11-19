package com.rusydiana.adzantime.ui

import android.icu.util.IslamicCalendar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rusydiana.adzantime.R
import com.rusydiana.adzantime.alarm.AdzanScheduler
import com.rusydiana.adzantime.data.CalendarEvent
import com.rusydiana.adzantime.data.CalendarEvents
import com.rusydiana.adzantime.data.Settings
import com.rusydiana.adzantime.prayer.HijriHolidays
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import kotlinx.coroutines.launch
import java.util.Locale

/** One square in the grid: the Hijri day, the Gregorian day under it, and any holiday. */
private data class DayCell(
    val hijriDay: Int,
    val gregorianDay: Int,
    val gregorianMonthLabel: String?,
    /** The same day spelled out, for the holiday list under the grid. */
    val gregorianFull: String,
    /** Weekday and all, for the detail dialog. */
    val gregorianLong: String,
    val holidayRes: Int?,
)

private data class HijriMonth(
    val year: Int,
    val month: Int,
    /** Leading nulls pad the first row so day 1 lands on its weekday. */
    val cells: List<DayCell?>,
    val gregorianRange: String,
) {
    /** Deduplicated so the three days of Tashriq read as one entry. */
    val holidays: List<DayCell>
        get() = cells.filterNotNull().filter { it.holidayRes != null }
            .distinctBy { it.holidayRes }
}

@Composable
fun HijriCalendarScreen(
    settings: Settings,
    onGoToTodayReady: (() -> Unit) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val today = remember { IslamicCalendar().apply { umalqura() } }
    val todayYear = remember { today.get(Calendar.YEAR) }
    val todayMonth = remember { today.get(Calendar.MONTH) }
    val todayDay = remember { today.get(Calendar.DAY_OF_MONTH) }

    // The shown month is absolute, not an offset from today: ICU's add(MONTH, n) on a calendar
    // whose calculationType was changed after construction walks off, and the grid would stick
    // on Muharram once the step crossed a year boundary.
    var shownYear by remember { mutableIntStateOf(todayYear) }
    var shownMonth by remember { mutableIntStateOf(todayMonth) }
    var pickerOpen by remember { mutableStateOf(false) }
    var showHolidays by remember { mutableStateOf(settings.showHijriHolidays) }
    var notifyHolidays by remember { mutableStateOf(settings.notifyHijriHolidays) }
    val context = LocalContext.current
    val shown = remember(shownYear, shownMonth) { buildMonth(shownYear, shownMonth) }

    // Hijri months are twelve to the year, so paging is plain arithmetic on that count.
    fun step(delta: Int) {
        val total = (shownYear * 12 + shownMonth + delta)
            .coerceIn(MIN_HIJRI_YEAR * 12, MAX_HIJRI_YEAR * 12 + 11)
        shownYear = total / 12
        shownMonth = total % 12
    }
    // The tapped square, shown as a detail dialog until dismissed.
    var selected by remember { mutableStateOf<DayCell?>(null) }
    // The store is not reactive, so the list is held here and re-read after every edit.
    var events by remember { mutableStateOf(CalendarEvents.all(context)) }
    // Null means the editor is closed; an event with a blank title means a new one.
    var editing by remember { mutableStateOf<CalendarEvent?>(null) }
    // The app bar's date owns the jump back to this month.
    LaunchedEffect(Unit) {
        onGoToTodayReady {
            shownYear = todayYear
            shownMonth = todayMonth
            pickerOpen = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PaddingValues(16.dp)),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedSurface {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArrowButton(
                        icon = Icons.Filled.KeyboardArrowLeft,
                        description = stringResource(R.string.calendar_previous),
                    ) { step(-1) }
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { pickerOpen = !pickerOpen }
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "${stringResource(hijriMonthNameRes(shown.month))} ${shown.year} H",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            shown.gregorianRange,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                    }
                    ArrowButton(
                        icon = Icons.Filled.KeyboardArrowRight,
                        description = stringResource(R.string.calendar_next),
                    ) { step(1) }
                }

                // A horizontal fling pages the month, the way a paper calendar is flipped.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            var dragged = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { dragged = 0f },
                                onDragEnd = {
                                    if (dragged <= -SWIPE_THRESHOLD_PX) step(1)
                                    else if (dragged >= SWIPE_THRESHOLD_PX) step(-1)
                                },
                                onHorizontalDrag = { change, amount ->
                                    dragged += amount
                                    change.consume()
                                },
                            )
                        },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                            .padding(vertical = 6.dp)
                    ) {
                        weekdayInitials().forEachIndexed { index, initial ->
                            Text(
                                initial,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                // Friday, the day of the congregation, reads first.
                                color = if (index == 5) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            )
                        }
                    }

                    shown.cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            week.forEach { cell ->
                                DayCell(
                                    cell = cell,
                                    isToday = cell?.hijriDay == todayDay &&
                                        shown.month == todayMonth && shown.year == todayYear,
                                    hasEvent = cell != null && events.any {
                                        it.falls(shown.year, shown.month, cell.hijriDay)
                                    },
                                    markHoliday = showHolidays,
                                    onClick = { selected = cell },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(7 - week.size) { Box(Modifier.weight(1f)) }
                        }
                    }
                }

                Text(
                    stringResource(R.string.calendar_swipe_hint),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                )
            }
        }

        OutlinedSurface {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.calendar_show_holidays), Modifier.weight(1f))
                    Switch(
                        checked = showHolidays,
                        onCheckedChange = {
                            showHolidays = it
                            settings.showHijriHolidays = it
                        },
                    )
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.calendar_notify_holidays))
                        Text(
                            stringResource(R.string.calendar_notify_holidays_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                    Switch(
                        checked = notifyHolidays,
                        onCheckedChange = {
                            notifyHolidays = it
                            settings.notifyHijriHolidays = it
                            // Arms or cancels the eve-of-holiday alarm right away.
                            AdzanScheduler.rescheduleAll(context)
                        },
                    )
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )
                if (showHolidays) {
                    Text(
                        stringResource(R.string.calendar_holidays_this_month),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    val holidays = shown.holidays
                    if (holidays.isEmpty()) {
                        Text(
                            stringResource(R.string.calendar_no_holidays),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    holidays.forEach { cell ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(cell.holidayRes!!))
                                Text(
                                    "${cell.hijriDay} ${stringResource(hijriMonthNameRes(shown.month))}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                cell.gregorianFull,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        Text(stringResource(R.string.calendar_hint), style = MaterialTheme.typography.bodySmall)
    }

    if (pickerOpen) {
        MonthYearPickerDialog(
            year = shownYear,
            month = shownMonth,
            onPick = { year, month ->
                shownYear = year.coerceIn(MIN_HIJRI_YEAR, MAX_HIJRI_YEAR)
                shownMonth = month
                pickerOpen = false
            },
            onDismiss = { pickerOpen = false },
        )
    }

    selected?.let { cell ->
        DayDetailDialog(
            cell = cell,
            hijriMonth = shown.month,
            hijriYear = shown.year,
            events = events.filter { it.falls(shown.year, shown.month, cell.hijriDay) },
            onAddEvent = {
                editing = CalendarEvent(
                    id = System.currentTimeMillis(),
                    title = "",
                    year = shown.year,
                    month = shown.month,
                    day = cell.hijriDay,
                    repeatsYearly = false,
                )
            },
            onEditEvent = { editing = it },
            onDeleteEvent = {
                CalendarEvents.delete(context, it.id)
                events = CalendarEvents.all(context)
            },
            onDismiss = { selected = null },
        )
    }

    editing?.let { event ->
        EventEditorDialog(
            event = event,
            dateLabel = "${event.day} ${stringResource(hijriMonthNameRes(event.month))} " +
                "${event.year} H",
            onSave = {
                CalendarEvents.save(context, it)
                events = CalendarEvents.all(context)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

/** The tapped date spelled out: Hijri, Gregorian, and the holiday if there is one. */
@Composable
private fun DayDetailDialog(
    cell: DayCell,
    hijriMonth: Int,
    hijriYear: Int,
    events: List<CalendarEvent>,
    onAddEvent: () -> Unit,
    onEditEvent: (CalendarEvent) -> Unit,
    onDeleteEvent: (CalendarEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.calendar_detail_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onAddEvent) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = EventColor,
                )
                Text(
                    stringResource(R.string.calendar_event_add),
                    modifier = Modifier.padding(start = 4.dp),
                    color = EventColor,
                )
            }
        },
        title = {
            Text(
                "${cell.hijriDay} ${stringResource(hijriMonthNameRes(hijriMonth))} $hijriYear H",
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                DetailRow(
                    label = stringResource(R.string.calendar_detail_hijri),
                    value = "${cell.hijriDay} ${stringResource(hijriMonthNameRes(hijriMonth))} $hijriYear H",
                )
                DetailRow(
                    label = stringResource(R.string.calendar_detail_gregorian),
                    value = cell.gregorianLong,
                )
                DetailRow(
                    label = stringResource(R.string.calendar_detail_event),
                    value = cell.holidayRes?.let { stringResource(it) }
                        ?: stringResource(R.string.calendar_detail_no_event),
                    highlight = cell.holidayRes != null,
                )
                if (events.isNotEmpty()) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    Text(
                        stringResource(R.string.calendar_event_yours),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                    events.forEach { event ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onEditEvent(event) }
                                .padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .background(EventColor, CircleShape)
                            )
                            Column(
                                Modifier
                                    .weight(1f)
                                    .padding(start = 10.dp, top = 8.dp, bottom = 8.dp)
                            ) {
                                Text(event.title, style = MaterialTheme.typography.bodyMedium)
                                if (event.repeatsYearly) {
                                    Text(
                                        stringResource(R.string.calendar_event_repeats),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                            .copy(alpha = 0.6f),
                                    )
                                }
                            }
                            IconButton(onClick = { onDeleteEvent(event) }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(
                                        R.string.calendar_event_delete
                                    ),
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

/** New and existing events share this: a blank title is simply an event not worth keeping. */
@Composable
private fun EventEditorDialog(
    event: CalendarEvent,
    dateLabel: String,
    onSave: (CalendarEvent) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember(event.id) { mutableStateOf(event.title) }
    var repeats by remember(event.id) { mutableStateOf(event.repeatsYearly) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { onSave(event.copy(title = title.trim(), repeatsYearly = repeats)) },
                enabled = title.isNotBlank(),
            ) { Text(stringResource(R.string.calendar_event_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.calendar_pick_cancel))
            }
        },
        title = {
            Text(
                stringResource(
                    if (event.title.isBlank()) R.string.calendar_event_add
                    else R.string.calendar_event_edit
                ),
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.calendar_event_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.calendar_event_repeat))
                        Text(
                            stringResource(R.string.calendar_event_repeat_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        )
                    }
                    Switch(checked = repeats, onCheckedChange = { repeats = it })
                }
            }
        },
    )
}

@Composable
private fun DetailRow(label: String, value: String, highlight: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal,
            color = if (highlight) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** A card with a hairline edge: in the light scheme `surface` alone is invisible. */
@Composable
private fun OutlinedSurface(content: @Composable () -> Unit) {
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
    ) { content() }
}

@Composable
private fun ArrowButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

/** How far a drag has to travel before it counts as paging the month. */
private const val SWIPE_THRESHOLD_PX = 120f

@Composable
private fun DayCell(
    cell: DayCell?,
    isToday: Boolean,
    hasEvent: Boolean,
    markHoliday: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(0.92f)
            .padding(2.dp)
            .then(if (cell == null) Modifier else Modifier.clip(CircleShape).clickable(onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        if (cell == null) return@Box
        val isHoliday = markHoliday && cell.holidayRes != null
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape,
                )
                .then(
                    when {
                        isToday -> Modifier
                        isHoliday -> Modifier
                            .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        hasEvent -> Modifier.border(1.dp, EventColor, CircleShape)
                        else -> Modifier
                    }
                ),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                cell.hijriDay.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isHoliday || hasEvent) FontWeight.Bold
                else FontWeight.Normal,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface,
            )
            // The Gregorian day the user actually lives by, kept deliberately quiet.
            Text(
                cell.gregorianMonthLabel?.let { "${cell.gregorianDay} $it" }
                    ?: cell.gregorianDay.toString(),
                fontSize = 9.sp,
                lineHeight = 10.sp,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            // A holiday and an own event can land on the same square, so both dots show.
            if (isHoliday || hasEvent) {
                Row(
                    Modifier.padding(top = 1.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (isHoliday) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .background(
                                    if (isToday) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.primary,
                                    CircleShape,
                                )
                        )
                    }
                    if (hasEvent) {
                        Box(Modifier.size(4.dp).background(EventColor, CircleShape))
                    }
                }
            }
        }
    }
}

/**
 * The jump-to popup behind the title. Two spinning wheels rather than a grid, because the span
 * is three centuries of years, and a Masehi mode beside the Hijri one: people know when their
 * appointment is in September long before they know which Hijri month that lands in.
 */
@Composable
private fun MonthYearPickerDialog(
    year: Int,
    month: Int,
    onPick: (Int, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var masehi by remember { mutableStateOf(false) }

    var hijriYear by remember { mutableIntStateOf(year) }
    var hijriMonth by remember { mutableIntStateOf(month) }
    // Seeded from the middle of the shown Hijri month, so flipping mode lands where the user is.
    val seed = remember(year, month) { gregorianOf(year, month) }
    var gregorianYear by remember { mutableIntStateOf(seed.first) }
    var gregorianMonth by remember { mutableIntStateOf(seed.second) }

    val hijriMonths = (0 until 12).map { stringResource(hijriMonthNameRes(it)) }
    val gregorianMonths = remember { gregorianMonthNames() }
    val hijriYears = remember { (MIN_HIJRI_YEAR..MAX_HIJRI_YEAR).map { "$it H" } }
    val gregorianYears = remember { (MIN_GREGORIAN_YEAR..MAX_GREGORIAN_YEAR).map { it.toString() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                if (masehi) {
                    val (y, m) = hijriOf(gregorianYear, gregorianMonth)
                    onPick(y, m)
                } else {
                    onPick(hijriYear, hijriMonth)
                }
            }) { Text(stringResource(R.string.calendar_pick_show)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.calendar_pick_cancel))
            }
        },
        title = {
            Text(stringResource(R.string.calendar_pick_title), fontWeight = FontWeight.Bold)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    ModeTab(
                        label = stringResource(R.string.calendar_detail_hijri),
                        selected = !masehi,
                        modifier = Modifier.weight(1f),
                    ) { masehi = false }
                    ModeTab(
                        label = stringResource(R.string.calendar_detail_gregorian),
                        selected = masehi,
                        modifier = Modifier.weight(1f),
                    ) { masehi = true }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (masehi) {
                        Wheel(
                            items = gregorianMonths,
                            selectedIndex = gregorianMonth,
                            onSelect = { gregorianMonth = it },
                            modifier = Modifier.weight(1.4f),
                        )
                        Wheel(
                            items = gregorianYears,
                            selectedIndex = gregorianYear - MIN_GREGORIAN_YEAR,
                            onSelect = { gregorianYear = MIN_GREGORIAN_YEAR + it },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Wheel(
                            items = hijriMonths,
                            selectedIndex = hijriMonth,
                            onSelect = { hijriMonth = it },
                            modifier = Modifier.weight(1.4f),
                        )
                        Wheel(
                            items = hijriYears,
                            selectedIndex = hijriYear - MIN_HIJRI_YEAR,
                            onSelect = { hijriYear = MIN_HIJRI_YEAR + it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // The other calendar's reading of the same choice, so the jump holds no surprise.
                val preview = if (masehi) {
                    val (y, m) = hijriOf(gregorianYear, gregorianMonth)
                    "${stringResource(hijriMonthNameRes(m))} $y H"
                } else {
                    val (y, m) = gregorianOf(hijriYear, hijriMonth)
                    "${gregorianMonths[m]} $y"
                }
                Text(
                    preview,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                )
            }
        },
    )
}

@Composable
private fun ModeTab(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Text(
        label,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
        color = if (selected) MaterialTheme.colorScheme.onPrimary
        else MaterialTheme.colorScheme.onSurface,
    )
}

/**
 * A snapping column that reports whatever sits on the centre line. The two rows of padding top
 * and bottom are what put item 0 in the middle, which is why the centred item is simply the
 * first visible one.
 */
@Composable
private fun Wheel(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    val select by rememberUpdatedState(onSelect)
    val centre by remember { derivedStateOf { state.firstVisibleItemIndex } }

    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .collect { scrolling -> if (!scrolling) select(state.firstVisibleItemIndex) }
    }

    Box(
        modifier.height(WHEEL_ITEM_HEIGHT * WHEEL_VISIBLE),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(WHEEL_ITEM_HEIGHT)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
        )
        LazyColumn(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(state),
            contentPadding = PaddingValues(vertical = WHEEL_ITEM_HEIGHT * (WHEEL_VISIBLE / 2)),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(items) { index, label ->
                val isCentre = index == centre
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(WHEEL_ITEM_HEIGHT)
                        .clickable { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        textAlign = TextAlign.Center,
                        style = if (isCentre) MaterialTheme.typography.titleMedium
                        else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCentre) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (isCentre) 1f else 0.45f
                        ),
                    )
                }
            }
        }
    }
}

/** The Gregorian month the middle of a Hijri month falls in — the two never line up on the 1st. */
private fun gregorianOf(hijriYear: Int, hijriMonth: Int): Pair<Int, Int> {
    val hijri = IslamicCalendar().apply {
        umalqura()
        set(Calendar.YEAR, hijriYear)
        set(Calendar.MONTH, hijriMonth)
        set(Calendar.DAY_OF_MONTH, 15)
    }
    val gregorian = Calendar.getInstance().apply { timeInMillis = hijri.timeInMillis }
    return gregorian.get(Calendar.YEAR) to gregorian.get(Calendar.MONTH)
}

private fun hijriOf(gregorianYear: Int, gregorianMonth: Int): Pair<Int, Int> {
    val gregorian = Calendar.getInstance().apply {
        set(gregorianYear, gregorianMonth, 15, 12, 0)
    }
    val hijri = IslamicCalendar().apply {
        umalqura()
        timeInMillis = gregorian.timeInMillis
    }
    return hijri.get(Calendar.YEAR) to hijri.get(Calendar.MONTH)
}

private fun gregorianMonthNames(): List<String> {
    val format = SimpleDateFormat("LLLL", Locale.getDefault())
    val cursor = Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }
    return (0 until 12).map { month ->
        cursor.set(Calendar.MONTH, month)
        format.format(cursor.time)
    }
}

/** Amber, fixed: the scheme's primary is the emerald that already marks holidays and today. */
private val EventColor = Color(0xFFD9822B)

private val WHEEL_ITEM_HEIGHT = 44.dp
private const val WHEEL_VISIBLE = 5

// ICU's Umm al-Qura tables only cover this span; outside it the dates are a fallback guess.
private const val MIN_HIJRI_YEAR = 1300
private const val MAX_HIJRI_YEAR = 1600

// The Gregorian window those Hijri years fall inside, trimmed to years anyone would pick.
private const val MIN_GREGORIAN_YEAR = 1900
private const val MAX_GREGORIAN_YEAR = 2100

private fun buildMonth(year: Int, month: Int): HijriMonth {
    // Anchored on the real instant: clear()+set() would drop the era. All three fields are set
    // before anything is read back, so the calendar resolves them in one go.
    val calendar = IslamicCalendar().apply {
        umalqura()
        set(Calendar.YEAR, year)
        set(Calendar.MONTH, month)
        set(Calendar.DAY_OF_MONTH, 1)
    }
    val length = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)

    // Sunday is column 0, matching the weekday header.
    val leading = calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    val gregorian = Calendar.getInstance()
    val monthAbbrev = SimpleDateFormat("MMM", Locale.getDefault())

    val fullDate = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    val longDate = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())

    val days = (1..length).map { day ->
        val cursor = (calendar.clone() as IslamicCalendar).apply { set(Calendar.DAY_OF_MONTH, day) }
        gregorian.timeInMillis = cursor.timeInMillis
        val gregorianDay = gregorian.get(Calendar.DAY_OF_MONTH)
        DayCell(
            hijriDay = day,
            gregorianDay = gregorianDay,
            gregorianFull = fullDate.format(gregorian.time),
            gregorianLong = longDate.format(gregorian.time),
            // Name the Gregorian month only where it changes, so the grid stays readable.
            gregorianMonthLabel = if (day == 1 || gregorianDay == 1) {
                monthAbbrev.format(gregorian.time)
            } else {
                null
            },
            holidayRes = HijriHolidays.of(month, day)?.labelRes,
        )
    }

    val firstDay = Date(calendar.timeInMillis)
    val lastDay = Date(
        (calendar.clone() as IslamicCalendar)
            .apply { set(Calendar.DAY_OF_MONTH, length) }.timeInMillis
    )
    val format = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
    return HijriMonth(
        year = year,
        month = month,
        cells = List(leading) { null } + days,
        gregorianRange = "${format.format(firstDay)} – ${format.format(lastDay)}",
    )
}

/** Umm al-Qura is the civil calendar used across most printed Hijri calendars. */
private fun IslamicCalendar.umalqura() {
    calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
}

fun hijriMonthNameRes(month: Int): Int = when (month) {
    0 -> R.string.hijri_month_1
    1 -> R.string.hijri_month_2
    2 -> R.string.hijri_month_3
    3 -> R.string.hijri_month_4
    4 -> R.string.hijri_month_5
    5 -> R.string.hijri_month_6
    6 -> R.string.hijri_month_7
    7 -> R.string.hijri_month_8
    8 -> R.string.hijri_month_9
    9 -> R.string.hijri_month_10
    10 -> R.string.hijri_month_11
    else -> R.string.hijri_month_12
}

/** Sunday-first initials in the user's locale. */
private fun weekdayInitials(): List<String> {
    val format = SimpleDateFormat("EEEEE", Locale.getDefault())
    val cursor = Calendar.getInstance().apply { set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY) }
    return (0 until 7).map {
        format.format(cursor.time).also { _ -> cursor.add(Calendar.DAY_OF_YEAR, 1) }
    }
}
