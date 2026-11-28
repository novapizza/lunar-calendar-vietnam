package io.github.novapizza.licham.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.Tab
import io.github.novapizza.licham.core.PersonalEvents
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.lunarDayLabel
import io.github.novapizza.licham.ui.weekdayName

/**
 * Tờ lịch hôm nay: today as the Today page's tear-off sheet (3×4 by default, resizable), drawn on a
 * bitmap (SheetPainter: Playfair, gold foil, rings) as the iOS widget's three sizes: upright and tall,
 * the app's sheet; small (2×2), the day, weekday and lunar date; wider than tall (4×2), the solar day
 * on the left and the lunar date in its ring on the right.
 */
class TodaySheetWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.of(context)
        val today = AppState.today()
        val own = PersonalEvents.on(AppState(context.applicationContext).lunarEvents, today).firstOrNull()?.title
        val square = squareCardsInTallCells(context)
        WidgetFontScale.sync(context)
        provideContent { WidgetFontScale.Provide { Sheet(look, today, own, square) } }
    }
}

class TodaySheetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodaySheetWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetClock.schedule(context)
    }
}

@Composable
private fun Sheet(look: WidgetLook, d: SolarDate, own: String?, squareInTallCell: Boolean) {
    val context = LocalContext.current
    val size = LocalSize.current
    val info = DayInfo.of(d)
    val w = size.width.value
    val cellH = size.height.value
    // On Xiaomi's launcher (only there, as MonthWidget), a cell up to 1.3× taller than wide gets a
    // square card low in the cell, as MonthWidget's and HyperOS's own widgets: filling a 2×2 cell the
    // sheet stood 54 px taller than the month widget beside it (user's Xiaomi screenshot, 2026-11-28:
    // cells 377 × 431 px, the month a 377 px square 44 px below the cell's top). Same rule and split
    // as MonthWidget: any size within 1.3×, 5/6 of the spare height above.
    val square = squareInTallCell && cellH > w && cellH <= w * 1.3f
    val h = if (square) w else cellH
    val spare = cellH - h
    val density = context.resources.displayMetrics.density
    val wPx = (w * density).toInt()
    val hPx = (h * density).toInt()
    val painter = SheetPainter(context, look.now(context))
    val bitmap = when {
        // Wider than tall (4×2 and the like): iOS's medium widget, the solar day left, the lunar ring right.
        w > h * 1.05f -> painter.paintMedium(d, info, wPx, hPx, density, nameOf(info, own))
        // Small (2×2): iOS's small widget; the whole sheet would be drawn at a third of its size.
        h < FULL_SHEET_MIN_H -> painter.paintSmall(d, info, wPx, hPx, density)
        // Upright and tall enough: the app's sheet itself.
        else -> painter.paint(d, info, wPx, hPx, nameOf(info, own))
    }
    Box(GlanceModifier.fillMaxSize().padding(top = (spare * 5f / 6f).dp, bottom = (spare / 6f).dp)) {
        Image(
            ImageProvider(bitmap),
            contentDescription = "${d.weekdayName()}, ${d.day}/${d.month}/${d.year}, âm lịch ${info.lunar.day}/${info.lunar.month}",
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(WidgetLinks.open(context, Tab.TODAY))),
            contentScale = ContentScale.Fit,
        )
    }
}

/** Below this height (dp) an upright widget draws the small sheet: 2×2 on a Pixel is about 186×213dp. */
private const val FULL_SHEET_MIN_H = 240f

/** The sheet's name line: holiday, else the user's own event, else commemorative day / festival, else "Mồng 1" / "Ngày rằm". */
private fun nameOf(info: DayInfo, own: String?) = info.holiday ?: own ?: info.observance ?: lunarDayLabel(info.lunar)
