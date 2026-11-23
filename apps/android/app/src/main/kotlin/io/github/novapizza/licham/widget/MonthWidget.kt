package io.github.novapizza.licham.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.Tab
import io.github.novapizza.licham.core.CalendarData
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.isSunday
import io.github.novapizza.licham.core.lunarMonthSubtitle
import io.github.novapizza.licham.core.monthGrid
import io.github.novapizza.licham.core.orNull

/**
 * Lịch tháng: this month as the Calendar page's grid (solar day, small lunar day, today filled,
 * Sundays and holidays red). The type shrinks with the cells, the lunar day included. Two picker
 * entries share it: 4×4 and 2×2.
 * Tapping a day opens the Calendar on that day.
 */
open class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    /** Square in a cell a little taller than wide, on Xiaomi's launcher (see MonthSmallWidget). */
    protected open fun squareInTallCell(context: Context) = defaultLauncher(context) in SQUARE_2X2_LAUNCHERS

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.of(context)
        val square = squareInTallCell(context)
        provideContent { Month(look, AppState.today(), square) }
    }
}

/**
 * The same widget under the 2×2 picker entry, now hidden from the picker (Lịch tháng resizes down to
 * 2×2); kept for the widgets already placed. Both are drawn as the launcher's own widgets are. Launchers
 * give 2×2 cells a little taller than wide and only say how big the cell is, not how their own widgets
 * fill it (measured 2026-11-23 on screenshots):
 * - HyperOS 4 (Xiaomi 17 Ultra): cell 375 × 430 px; its clock and weather draw a 375 × 375 square,
 *   centred, so this widget, filling the cell, stood taller.
 * - One UI 9 (Samsung): cell 374 × 418 px; the 2×2 widget beside it fills the cell, as this one does.
 * The cells' shapes are too alike to tell them apart, so the square follows the launcher: Xiaomi's.
 */
class MonthSmallWidget : MonthWidget()

/** Launchers whose own 2×2 widgets are square in a taller cell: MIUI / HyperOS, POCO Launcher. */
private val SQUARE_2X2_LAUNCHERS = setOf("com.miui.home", "com.mi.android.globallauncher")

/** The default home app's package (needs the HOME <queries> entry in the manifest); null when none is set. */
private fun defaultLauncher(context: Context): String? =
    context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        ?.activityInfo?.packageName

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetClock.schedule(context)
    }
}

class MonthSmallWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthSmallWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetClock.schedule(context)
    }
}

@Composable
private fun Month(look: WidgetLook, today: SolarDate, squareInTallCell: Boolean) {
    val context = LocalContext.current
    val size = LocalSize.current
    val w = size.width.value
    // A cell up to 1.3× taller than wide gets a square card, centred; a widget stretched further keeps
    // the whole height.
    val square = squareInTallCell && size.height.value > w && size.height.value <= w * 1.3f
    val h = if (square) w else size.height.value
    val pad = if (minOf(w, h) < 200f) 8f else 12f
    val grid = monthGrid(today.year, today.month)
    val rows = grid.size / 7
    val cellW = (w - 2 * pad) / 7f

    // Header: "Tháng 11 2026", and the lunar months under it when there is room.
    val titleDp = (w * 0.065f).clampTo(11f, 18f)
    val showSub = h > 220f
    val subDp = (w * 0.036f).clampTo(9f, 12f)
    val weekDp = (cellW * 0.32f).clampTo(7f, 11f)
    val headerH = titleDp * 1.4f + (if (showSub) subDp * 1.45f else 0f) + 4f
    val weekH = weekDp * 1.6f
    val cellH = (h - 2 * pad - headerH - weekH) / rows

    // Day numbers from the cell box, the lunar day under each (two lines must fit the cell's height).
    val solarDp = minOf(cellW * 0.42f, cellH * 0.44f, cellH / (1.3f * 1.62f))
    val lunarDp = solarDp * 0.62f
    val showLunar = true

    // The card and the grid fill the box the launcher actually gives, whatever size it reported:
    // columns and week rows share it by weight. HyperOS reported about 80 % of a resized widget's
    // box, and a grid laid out in dp from that left empty space on the right and at the bottom
    // (Xiaomi 17 Ultra screenshots, 2026-11-23). Only the type sizes come from the reported size.
    // The square card is the cell less a margin above and below (in reported dp, so close to square).
    val open = actionStartActivity(WidgetLinks.open(context, Tab.CALENDAR, today))
    val squareMargin = if (square) ((size.height.value - w) / 2f).dp else 0.dp
    Box(GlanceModifier.fillMaxSize().padding(vertical = squareMargin)) {
    Column(GlanceModifier.fillMaxSize().cornerRadius(20.dp).background(look.color { it.surface }).padding(pad.dp)) {
        Column(GlanceModifier.fillMaxWidth().height(headerH.dp).clickable(open), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tháng ${today.month}", style = TextStyle(color = look.color { it.fg }, fontSize = fixedSp(titleDp), fontWeight = FontWeight.Bold), maxLines = 1)
                Text(" ${today.year}", style = TextStyle(color = look.color { it.fg3 }, fontSize = fixedSp(titleDp), fontWeight = FontWeight.Bold), maxLines = 1)
            }
            if (showSub) {
                Text(lunarMonthSubtitle(today.year, today.month), style = TextStyle(color = look.color { it.fg2 }, fontSize = fixedSp(subDp)), maxLines = 1)
            }
        }
        Row(GlanceModifier.fillMaxWidth().height(weekH.dp)) {
            CalendarData.calendar.weekDays.forEach { wd ->
                Box(GlanceModifier.defaultWeight().fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text(wd, style = TextStyle(color = look.color { if (wd == "CN") it.lunar else it.fg3 }, fontSize = fixedSp(weekDp), fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        }
        grid.chunked(7).forEach { week ->
            Row(GlanceModifier.fillMaxWidth().defaultWeight()) {
                week.forEach { d -> DayCell(look, d, today, GlanceModifier.defaultWeight().fillMaxHeight(), solarDp, if (showLunar) lunarDp else null) }
            }
        }
    }
    }
}

@Composable
private fun DayCell(look: WidgetLook, d: SolarDate, today: SolarDate, modifier: GlanceModifier, solarDp: Float, lunarDp: Float?) {
    val context = LocalContext.current
    val info = DayInfo.orNull(d)
    val isToday = d == today
    val inMonth = d.month == today.month
    val red = d.isSunday || info?.holiday != null
    // Other months' days fade, as on the Calendar page.
    val fade = if (inMonth) 1f else 0.35f
    val solarColor = look.color { (if (isToday) it.brand else if (red) it.lunar else it.fg).copy(alpha = fade) }
    Box(
        modifier.padding(1.dp)
            .clickable(actionStartActivity(WidgetLinks.open(context, Tab.CALENDAR, d))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            GlanceModifier.fillMaxSize().let { if (isToday) it.cornerRadius(8.dp).background(look.color { c -> c.brandSoft }) else it },
            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("${d.day}", style = TextStyle(color = solarColor, fontSize = fixedSp(solarDp), fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium), maxLines = 1)
            if (lunarDp != null && info != null) {
                val l = info.lunar
                val mark = l.day == 1 || l.day == 15
                Text(
                    if (l.day == 1) "${l.day}/${l.month}" else "${l.day}",
                    style = TextStyle(
                        color = look.color { (if (mark) it.lunar else it.fg3).copy(alpha = fade) },
                        fontSize = fixedSp(lunarDp), fontWeight = if (mark) FontWeight.Bold else FontWeight.Normal,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
