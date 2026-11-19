package com.rusydiana.adzantime.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.widget.RemoteViews

/**
 * The square widget. Separate from [AdzanWidgetProvider] on purpose: the launcher lists it as
 * its own choice with its own preview and default cell size, so the user picks the shape they
 * want instead of dropping the wide one and resizing it into something it was not drawn for.
 *
 * Everything else — theme, Shuruq, the schedule, the per-instance configuration — is shared.
 */
class AdzanSquareWidgetProvider : AdzanWidgetProvider() {

    override fun viewsFor(
        context: Context,
        manager: AppWidgetManager,
        appWidgetId: Int,
    ): RemoteViews = buildSquareViews(context, manager, appWidgetId)
}
