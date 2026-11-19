package com.rusydiana.adzantime.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.rusydiana.adzantime.R
import com.rusydiana.adzantime.alarm.AdzanScheduler
import com.rusydiana.adzantime.data.Settings
import com.rusydiana.adzantime.data.WidgetHealth
import com.rusydiana.adzantime.data.WidgetSettings
import com.rusydiana.adzantime.data.WidgetTheme
import com.rusydiana.adzantime.prayer.HijriDate
import com.rusydiana.adzantime.prayer.Prayer
import com.rusydiana.adzantime.prayer.Schedule
import com.rusydiana.adzantime.ui.MainActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * The wide home-screen widget: one row of six prayer columns under the Hijri/location header.
 * Redrawn at each prayer, at midnight, and whenever settings change.
 *
 * The square variant lives in [AdzanSquareWidgetProvider] and shares everything but the
 * layouts it picks.
 */
open class AdzanWidgetProvider : AppWidgetProvider() {

    /** Which layout to render at a given cell size. Overridden by the square variant. */
    open fun viewsFor(context: Context, manager: AppWidgetManager, appWidgetId: Int): RemoteViews =
        buildViews(context, manager, appWidgetId)

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Driven by updatePeriodMillis, whose alarm the system owns rather than this app —
        // so it survives the app's own alarms being wiped, and re-arms the chain below.
        WidgetHealth.markRefreshed(context)
        ids.forEach { manager.updateAppWidget(it, viewsFor(context, manager, it)) }
        AdzanScheduler.rescheduleAll(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        manager.updateAppWidget(appWidgetId, viewsFor(context, manager, appWidgetId))
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetSettings.forget(context, it) }
    }

    override fun onEnabled(context: Context) {
        AdzanScheduler.rescheduleAll(context)
    }

    companion object {
        private val COLUMN_ROOTS = intArrayOf(
            R.id.col_0, R.id.col_1, R.id.col_2, R.id.col_3, R.id.col_4, R.id.col_5,
        )
        private val COLUMN_LABELS = intArrayOf(
            R.id.label_0, R.id.label_1, R.id.label_2, R.id.label_3, R.id.label_4, R.id.label_5,
        )
        private val COLUMN_TIMES = intArrayOf(
            R.id.time_0, R.id.time_1, R.id.time_2, R.id.time_3, R.id.time_4, R.id.time_5,
        )

        // Palettes. Transparent keeps the dark palette: it sits on the wallpaper.
        private const val DARK_STRONG = 0xFFFFFFFF.toInt()
        private const val DARK_MUTED = 0xFFB9C4D0.toInt()
        private const val LIGHT_STRONG = 0xFF101828.toInt()
        private const val LIGHT_MUTED = 0xFF667085.toInt()
        private const val ACTIVE_ON_DARK = 0xFF04231A.toInt()
        private const val ACTIVE_ON_LIGHT = 0xFF065F46.toInt()
        private const val ALERT_ON_DARK = 0xFFFF7A7A.toInt()
        private const val ALERT_ON_LIGHT = 0xFFC62828.toInt()

        /** Push a fresh render to every placed instance of either widget. */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            // Stamped before the render so every instance in this pass shares one heartbeat.
            WidgetHealth.markRefreshed(context)
            manager.getAppWidgetIds(ComponentName(context, AdzanWidgetProvider::class.java))
                .forEach { manager.updateAppWidget(it, buildViews(context, manager, it)) }
            manager.getAppWidgetIds(ComponentName(context, AdzanSquareWidgetProvider::class.java))
                .forEach { manager.updateAppWidget(it, buildSquareViews(context, manager, it)) }
        }

        /**
         * How big the type is drawn at a given cell size. The layouts ship one set of sizes;
         * these override them per size bucket, so a widget the user stretched across the
         * home screen fills the space it was given instead of leaving the times marooned in
         * the middle of it.
         */
        private data class TypeScale(
            val hijri: Float,
            val city: Float,
            val label: Float,
            val time: Float,
            /** Below the wider buckets the Hijri year is dropped so the date still fits. */
            val shortDate: Boolean = false,
        ) {
            fun scaledBy(factor: Float): TypeScale =
                if (factor == 1f) this
                else copy(
                    hijri = hijri * factor,
                    city = city * factor,
                    label = label * factor,
                    time = time * factor,
                )
        }

        private val XS = TypeScale(hijri = 10f, city = 10f, label = 9f, time = 13f, shortDate = true)
        private val S = TypeScale(hijri = 12f, city = 11f, label = 11f, time = 16f, shortDate = true)
        private val M = TypeScale(hijri = 15f, city = 13f, label = 14f, time = 21f)
        private val L = TypeScale(hijri = 18f, city = 15f, label = 17f, time = 26f)
        private val XL = TypeScale(hijri = 21f, city = 17f, label = 20f, time = 32f)

        // The square layouts stack their rows, so they take their own pair of scales.
        private val SQ_S = TypeScale(hijri = 13f, city = 11f, label = 12f, time = 15f)
        private val SQ_M = TypeScale(hijri = 17f, city = 14f, label = 16f, time = 19f)
        private val SQ_L = TypeScale(hijri = 21f, city = 17f, label = 20f, time = 24f)

        fun buildViews(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
        ): RemoteViews {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                // Pre-S there is no size map, so the current cell size picks both the layout
                // and how large the type is drawn in it.
                val options = manager.getAppWidgetOptions(appWidgetId)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
                val (layout, scale) = when {
                    height in 1 until HEADER_MIN_HEIGHT_DP -> R.layout.widget_adzan_row to
                        widthScale(width, XS, S, M, L)
                    width in 1 until WIDE_MIN_WIDTH_DP -> R.layout.widget_adzan_compact to S
                    else -> R.layout.widget_adzan to widthScale(width, M, M, L, XL)
                }
                return render(context, appWidgetId, layout, scale)
            }
            // The launcher picks the largest entry that fits the cell the user resized to, so
            // the ladder runs from a single narrow row up to a full-width block. A one-cell-high
            // widget lands on the row layout, which is the densest of the three.
            return RemoteViews(
                mapOf(
                    // Each step up needs the height to draw it in as well as the width, so a
                    // wide but one-cell-high widget stays on the smallest type rather than
                    // picking a size that would clip.
                    SizeF(120f, 40f) to row(context, appWidgetId, XS),
                    SizeF(200f, 52f) to row(context, appWidgetId, S),
                    SizeF(290f, 68f) to row(context, appWidgetId, M),
                    SizeF(380f, 84f) to row(context, appWidgetId, L),
                    SizeF(120f, HEADER_MIN_HEIGHT_DP.toFloat()) to
                        render(context, appWidgetId, R.layout.widget_adzan_compact, S),
                    SizeF(WIDE_MIN_WIDTH_DP.toFloat(), HEADER_MIN_HEIGHT_DP.toFloat()) to
                        render(context, appWidgetId, R.layout.widget_adzan, M),
                    SizeF(340f, 125f) to render(context, appWidgetId, R.layout.widget_adzan, L),
                    SizeF(420f, 160f) to render(context, appWidgetId, R.layout.widget_adzan, XL),
                )
            )
        }

        private fun row(context: Context, appWidgetId: Int, scale: TypeScale) =
            render(context, appWidgetId, R.layout.widget_adzan_row, scale)

        /** Picks a scale from how much width the cell actually has, for the pre-S path. */
        private fun widthScale(
            width: Int,
            xs: TypeScale,
            s: TypeScale,
            m: TypeScale,
            l: TypeScale,
        ): TypeScale = when {
            width < 200 -> xs
            width < 290 -> s
            width < 380 -> m
            else -> l
        }

        /**
         * The square widget: one entry per size so the type steps up once there is room.
         * Both entries keep the header — it is no longer optional.
         */
        fun buildSquareViews(
            context: Context,
            manager: AppWidgetManager,
            appWidgetId: Int,
        ): RemoteViews {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                val options = manager.getAppWidgetOptions(appWidgetId)
                val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
                val layout =
                    if (height in 1 until SQUARE_ROOMY_HEIGHT_DP) R.layout.widget_adzan_square_compact
                    else R.layout.widget_adzan_square
                return render(
                    context, appWidgetId, layout,
                    if (layout == R.layout.widget_adzan_square) SQ_M else SQ_S,
                )
            }
            return RemoteViews(
                mapOf(
                    SizeF(100f, 100f) to
                        render(context, appWidgetId, R.layout.widget_adzan_square_compact, SQ_S),
                    SizeF(100f, SQUARE_ROOMY_HEIGHT_DP.toFloat()) to
                        render(context, appWidgetId, R.layout.widget_adzan_square, SQ_M),
                    SizeF(180f, 300f) to
                        render(context, appWidgetId, R.layout.widget_adzan_square, SQ_L),
                )
            )
        }

        /** Above this a square cell has room for the full-size rows. */
        private const val SQUARE_ROOMY_HEIGHT_DP = 180

        /** Below this the wide layout's spacing would squeeze the times out of the cell. */
        private const val HEADER_MIN_HEIGHT_DP = 92
        private const val WIDE_MIN_WIDTH_DP = 250

        /** [autoScale] null means keep whatever type sizes the layout itself declares. */
        private fun render(
            context: Context,
            appWidgetId: Int,
            layoutRes: Int,
            autoScale: TypeScale?,
        ): RemoteViews {
            val settings = Settings.get(context)
            val theme = WidgetSettings.themeOf(context, appWidgetId)
            // The cell size earns a scale; the user's choice then nudges it either way.
            val scale = autoScale?.scaledBy(
                WidgetSettings.fontSizeOf(context, appWidgetId).multiplier
            )
            val showSyuruq = WidgetSettings.showSyuruq(context, appWidgetId)
            val views = RemoteViews(context.packageName, layoutRes)
            val locale = Locale.getDefault()

            val strong = if (theme.isLight) LIGHT_STRONG else DARK_STRONG
            val muted = if (theme.isLight) LIGHT_MUTED else DARK_MUTED
            val active = if (theme.isLight) ACTIVE_ON_LIGHT else ACTIVE_ON_DARK

            views.setInt(
                R.id.widget_root, "setBackgroundResource",
                when (theme) {
                    WidgetTheme.DARK -> R.drawable.widget_background
                    WidgetTheme.LIGHT -> R.drawable.widget_background_light
                    WidgetTheme.TRANSPARENT -> R.drawable.widget_background_transparent
                },
            )
            // The header is always on: it is the only place the Hijri date and the location
            // appear. Every layout, down to the one-cell-high one, makes room for it.
            views.setViewVisibility(R.id.row_header, View.VISIBLE)

            val today = Schedule.today(context)
            val (current, _) = Schedule.currentAndNext(context)
            val timeFormat = SimpleDateFormat(
                if (settings.use24Hour) "HH:mm" else "h:mm", locale
            )

            // At the smallest sizes the Hijri year is dropped — it is the one thing there is
            // no room for, and the day and month are what the row is read for.
            views.setTextViewText(
                R.id.hijri,
                if (scale?.shortDate == true) HijriDate.todayShort(context)
                else HijriDate.today(context),
            )
            views.setTextColor(R.id.hijri, strong)
            if (scale != null) {
                views.setTextViewTextSize(R.id.hijri, TypedValue.COMPLEX_UNIT_SP, scale.hijri)
                views.setTextViewTextSize(R.id.city, TypedValue.COMPLEX_UNIT_SP, scale.city)
            }
            views.setTextViewText(
                R.id.city,
                settings.cityName.ifBlank { context.getString(R.string.city_unknown) },
            )
            views.setTextColor(R.id.city, muted)

            // Columns keep the fixed reading order Fajr → Isha, not the chronological order.
            Prayer.entries.forEachIndexed { index, prayer ->
                if (prayer == Prayer.SYURUQ && !showSyuruq) {
                    views.setViewVisibility(COLUMN_ROOTS[index], View.GONE)
                    return@forEachIndexed
                }
                views.setViewVisibility(COLUMN_ROOTS[index], View.VISIBLE)
                val event = today.events.first { it.prayer == prayer }
                views.setTextViewText(
                    COLUMN_LABELS[index],
                    if (event.isJumuah) context.getString(R.string.prayer_jumuah)
                    else context.getString(prayer.labelRes),
                )
                views.setTextViewText(COLUMN_TIMES[index], timeFormat.format(Date(event.timeMillis)))
                if (scale != null) {
                    views.setTextViewTextSize(
                        COLUMN_LABELS[index], TypedValue.COMPLEX_UNIT_SP, scale.label
                    )
                    views.setTextViewTextSize(
                        COLUMN_TIMES[index], TypedValue.COMPLEX_UNIT_SP, scale.time
                    )
                }
                val isCurrent = current?.prayer == prayer &&
                    isSameDay(current.timeMillis, event.timeMillis)
                views.setInt(
                    COLUMN_ROOTS[index], "setBackgroundResource",
                    when {
                        !isCurrent -> R.drawable.pill_inactive
                        theme.isLight -> R.drawable.pill_active_light
                        else -> R.drawable.pill_active
                    },
                )
                views.setTextColor(COLUMN_LABELS[index], if (isCurrent) active else muted)
                views.setTextColor(COLUMN_TIMES[index], if (isCurrent) active else strong)
            }

            renderHeartbeat(context, views, scale, theme.isLight, muted)

            views.setOnClickPendingIntent(
                R.id.widget_root,
                PendingIntent.getActivity(
                    context, 0, Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return views
        }

        /**
         * The countdown to the next adzan, next to the Hijri date — and the liveness signal.
         *
         * The Chronometer is ticked by the launcher, not by this app, and counts down to the
         * instant the next alarm is armed for. That makes it useful every ordinary day, but it
         * is also what reports a dead chain: when a force-stop or a battery manager kills the
         * alarm, nothing here runs to redraw the widget, so the count runs straight past zero
         * and keeps going negative — "Maghrib -0:14:32" says the adzan was due a quarter of an
         * hour ago and never came. A healthy app rebases it at every prayer, so it is never
         * negative for more than a few seconds.
         *
         * The red state is necessarily retrospective: painting it takes a redraw, and a redraw
         * only happens once the app is alive again. The negative count is what carries the
         * signal in the meantime.
         */
        private fun renderHeartbeat(
            context: Context,
            views: RemoteViews,
            scale: TypeScale?,
            isLight: Boolean,
            muted: Int,
        ) {
            val next = Schedule.nextNotifiable(context)
            if (next == null) {
                // Every prayer silenced: a countdown to nothing would be a lie.
                views.setViewVisibility(R.id.heartbeat, View.GONE)
                return
            }
            views.setViewVisibility(R.id.heartbeat, View.VISIBLE)

            val missed = Schedule.missedNotifiable(
                context,
                // Not lastFiredAt: an adzan the user has already seen was missed stops
                // being news, and the countdown owes them the next prayer.
                lastFiredAt = WidgetHealth.accountedThrough(context),
                since = WidgetHealth.firstSeen(context),
            )
            // Whichever prayer the label names, the trailing "%s" is left in place for the
            // Chronometer to fill in itself, so the count stays live inside the label.
            //
            // The name and the count must come from the *same* event, which is the whole reason
            // this is one variable. Naming `missed` while counting to `next` put a passed
            // prayer's name on the next one's countdown — "Maghrib 36:35" at 18:22 with Maghrib
            // gone at 17:49 and Isha due at 18:59 — which reads as a countdown to a prayer that
            // already happened, and hid the negative count that is supposed to be the signal
            // that the chain is dead.
            val label = missed ?: next
            val name =
                if (label.isJumuah) context.getString(R.string.prayer_jumuah)
                else context.getString(label.prayer.labelRes)
            views.setChronometer(
                R.id.heartbeat,
                // The Chronometer runs on the monotonic clock, so the wall-clock instant the
                // alarm is set for has to be carried across as a delta from now. Past its base
                // a counting-down Chronometer goes negative on its own, which is exactly what
                // the missed case wants to show.
                SystemClock.elapsedRealtime() + (label.timeMillis - System.currentTimeMillis()),
                context.getString(R.string.widget_countdown_format, name, "%s"),
                true,
            )
            views.setChronometerCountDown(R.id.heartbeat, true)
            views.setTextColor(
                R.id.heartbeat,
                when {
                    missed == null -> muted
                    isLight -> ALERT_ON_LIGHT
                    else -> ALERT_ON_DARK
                },
            )
            if (scale != null) {
                views.setTextViewTextSize(R.id.heartbeat, TypedValue.COMPLEX_UNIT_SP, scale.city)
            }
        }

        private fun isSameDay(a: Long, b: Long): Boolean {
            val ca = Calendar.getInstance().apply { timeInMillis = a }
            val cb = Calendar.getInstance().apply { timeInMillis = b }
            return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
        }
    }
}
