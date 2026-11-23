package site.brutt.betterred.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Home-screen widget: cycle ring, phase and next period. Android refreshes it hourly (so the day rolls over). */
class CycleWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        Widgets.run({ Widgets.renderCycle(context.applicationContext, ids) }, pending::finish)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, newOptions: Bundle) {
        val pending = goAsync()
        Widgets.run({ Widgets.renderCycle(context.applicationContext, intArrayOf(id)) }, pending::finish)
    }
}
