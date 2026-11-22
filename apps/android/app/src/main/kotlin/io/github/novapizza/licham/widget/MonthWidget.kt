package io.github.novapizza.licham.widget

import android.appwidget.AppWidgetManager
import android.content.Context
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
import androidx.glance.layout.fillMaxSize
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

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.of(context)
        provideContent { Month(look, AppState.today()) }
    }
}

/** The same widget under the 2×2 picker entry. */
class MonthSmallWidget : MonthWidget()

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
private fun Month(look: WidgetLook, today: SolarDate) {
    val context = LocalContext.current
    val size = LocalSize.current
    val w = size.width.value
    val h = size.height.value
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

    val open = actionStartActivity(WidgetLinks.open(context, Tab.CALENDAR, today))
    Column(GlanceModifier.fillMaxSize().cornerRadius(20.dp).background(look.color { it.surface }).padding(pad.dp)) {
        Column(GlanceModifier.height(headerH.dp).clickable(open), verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tháng ${today.month}", style = TextStyle(color = look.color { it.fg }, fontSize = fixedSp(titleDp), fontWeight = FontWeight.Bold), maxLines = 1)
                Text(" ${today.year}", style = TextStyle(color = look.color { it.fg3 }, fontSize = fixedSp(titleDp), fontWeight = FontWeight.Bold), maxLines = 1)
            }
            if (showSub) {
                Text(lunarMonthSubtitle(today.year, today.month), style = TextStyle(color = look.color { it.fg2 }, fontSize = fixedSp(subDp)), maxLines = 1)
            }
        }
        Row {
            CalendarData.calendar.weekDays.forEach { wd ->
                Box(GlanceModifier.width(cellW.dp).height(weekH.dp), contentAlignment = Alignment.Center) {
                    Text(wd, style = TextStyle(color = look.color { if (wd == "CN") it.lunar else it.fg3 }, fontSize = fixedSp(weekDp), fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        }
        grid.chunked(7).forEach { week ->
            Row {
                week.forEach { d -> DayCell(look, d, today, cellW, cellH, solarDp, if (showLunar) lunarDp else null) }
            }
        }
    }
}

@Composable
private fun DayCell(look: WidgetLook, d: SolarDate, today: SolarDate, cellW: Float, cellH: Float, solarDp: Float, lunarDp: Float?) {
    val context = LocalContext.current
    val info = DayInfo.orNull(d)
    val isToday = d == today
    val inMonth = d.month == today.month
    val red = d.isSunday || info?.holiday != null
    // Other months' days fade, as on the Calendar page.
    val fade = if (inMonth) 1f else 0.35f
    val solarColor = look.color { (if (isToday) it.brand else if (red) it.lunar else it.fg).copy(alpha = fade) }
    Box(
        GlanceModifier.width(cellW.dp).height(cellH.dp).padding(1.dp)
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
