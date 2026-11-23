package io.github.novapizza.licham.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.ColorFilter
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.R
import io.github.novapizza.licham.Tab
import io.github.novapizza.licham.core.PersonalEvents
import io.github.novapizza.licham.ui.eventColor
import io.github.novapizza.licham.core.LunarEvent
import io.github.novapizza.licham.core.CalendarData
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.Rating
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.Zodiac
import io.github.novapizza.licham.core.isSunday
import io.github.novapizza.licham.core.lunarDayLabel
import io.github.novapizza.licham.core.monthGrid
import io.github.novapizza.licham.core.orNull
import kotlin.math.roundToInt

/**
 * Lịch tháng: this month as the Calendar page's grid (solar day, small lunar day, today filled,
 * Sundays and holidays red). The type shrinks with the cells, the lunar day included. 4×4 by
 * default, resizable down to 2×2 (the separate 2×2 entry was removed, user 2026-11-23).
 * Tapping a day opens the Calendar on that day. Dots as on the Calendar page (user, 2026-11-23):
 * after the lunar day, green for a good day, red for a bad one, and one for a clash with the
 * birth year set in the app; after the solar day, gold when the day has a note.
 *
 * Drawn as the launcher's own widgets are. Launchers give 2×2 cells a little taller than wide and
 * only say how big the cell is, not how their own widgets fill it (measured 2026-11-23 on
 * screenshots, px at 920 px screen width):
 * - HyperOS 4 (Xiaomi 17 Ultra): cell 377 × 431; its clock and weather draw a 377 × 377 square
 *   45 px below the cell's top and 9-10 px above its bottom, so this widget, filling the cell, stood
 *   taller, and a centred square sat 18 px higher than its neighbours.
 * - One UI 9 (Samsung): cell 374 × 418 px; the 2×2 widget beside it fills the cell, as this one does.
 * The cells' shapes are too alike to tell them apart, so the square follows the launcher: Xiaomi's.
 */
class MonthWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.of(context)
        val square = defaultLauncher(context) in SQUARE_2X2_LAUNCHERS
        val app = AppState(context.applicationContext)
        val marks = Marks(app.userChi, app.lunarEvents)
        provideContent { Month(look, AppState.today(), square, marks) }
    }
}

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

/**
 * What the dots and today's events need from the app: the days with a note, the user's year branch
 * (null: no birth date) and the user's own events (a dot in the first event's colour; named in the header).
 */
private class Marks(val userChi: String?, val personal: List<LunarEvent>)

@Composable
private fun Month(look: WidgetLook, today: SolarDate, squareInTallCell: Boolean, marks: Marks) {
    val context = LocalContext.current
    val size = LocalSize.current
    val w = size.width.value
    // A cell up to 1.3× taller than wide gets a square card, low in the cell as HyperOS's own widgets
    // sit; a widget stretched further keeps the whole height.
    val square = squareInTallCell && size.height.value > w && size.height.value <= w * 1.3f
    val h = if (square) w else size.height.value
    val pad = if (minOf(w, h) < 200f) 8f else 12f
    val month = monthGrid(today.year, today.month)
    val cellW = (w - 2 * pad) / 7f

    // Header: "Tháng 11 2026" and today's events at the right (user, 2026-11-23), as the Calendar's
    // day panel lists them: the holiday, the commemorative days and festivals, else mồng 1 / rằm.
    // The lunar months line under it ("Tháng 9 – 10 âm lịch · Bính Ngọ") was dropped for them.
    // A long event first hides the year, then is cut short with "…". Widths are estimated from the
    // type size (Roboto bold, about 0.6 of the size a letter for the title; 0.56 for the event line,
    // measured about 0.54 on the emulator with "Ngày thành lập Đoàn TNCS Hồ Chí Minh"): Glance
    // cannot measure text.
    val titleDp = (w * 0.065f).clampTo(11f, 18f)
    val eventDp = (titleDp * 0.72f).clampTo(9f, 13f)
    val events = todayEvents(today, marks.personal)
    val titleW = "Tháng ${today.month}".length * 0.6f * titleDp
    val yearW = " ${today.year}".length * 0.6f * titleDp
    val eventGap = 6f
    val eventW = events?.let { it.first.length * 0.56f * eventDp } ?: 0f
    val headRoom = w - 2 * pad
    val showYear = events == null || titleW + yearW + eventGap + eventW <= headRoom
    val eventText = events?.first?.let { ellipsize(it, (headRoom - titleW - (if (showYear) yearW else 0f) - eventGap) / (0.56f * eventDp)) }
    val weekDp = (cellW * 0.32f).clampTo(7f, 11f)
    val headerH = titleDp * 1.4f + 4f
    val weekH = weekDp * 1.6f
    val gridH = (h - 2 * pad - headerH - weekH).coerceAtLeast(12f)

    // Day numbers from the cell box, the lunar day under each (two lines must fit the cell's height).
    // Small cells (2×2, about 21 dp wide) get type up to 28 % bigger: at the base sizes the digits
    // looked lost in their cells (user, 2026-11-23: +20 %, then "a little bigger" on a Xiaomi 2×2).
    // From 36 dp cells (about 4×4) the base sizes stand.
    val boost = (1.28f - (cellW - 22f) / (36f - 22f) * 0.28f).coerceIn(1f, 1.28f)
    // The two lines' box (DayCell's pairH, 1.25 + 0.98 × 0.62 = 1.86 of the solar size) starts and
    // ends in font padding nothing is drawn in (about 0.35 at the top, 0.17 at the bottom): up to
    // 0.3 of it may spill into the gap between rows, so the cell must hold 1.56 of the solar size.
    val fitH = 1.56f

    // A widget wider than tall whose week rows would come out much lower than the cells' width asks
    // (under 3/4 of the height the width's type needs) shows only the weeks that fit, today's week
    // first, then the weeks after it, moved back near the month's end so the rows stay full (user,
    // 2026-11-23). The rest of the month is a tap away.
    val allRows = month.size / 7
    val needH = cellW * 0.42f * boost / minOf(0.44f * boost, 1f / fitH)
    val rows = if (w > h && gridH / allRows < 0.75f * needH) (gridH / needH).roundToInt().coerceIn(1, allRows) else allRows
    val first = minOf(month.indexOf(today).coerceAtLeast(0) / 7, allRows - rows)
    val grid = month.subList(first * 7, (first + rows) * 7)
    val cellH = gridH / rows
    val solarDp = minOf(cellW * 0.42f * boost, cellH * 0.44f * boost, cellH / fitH)
    val lunarDp = solarDp * 0.62f
    val showLunar = true

    // The card and the grid fill the box the launcher actually gives, whatever size it reported:
    // columns and week rows share it by weight. HyperOS reported about 80 % of a resized widget's
    // box, and a grid laid out in dp from that left empty space on the right and at the bottom
    // (Xiaomi 17 Ultra screenshots, 2026-11-23). Only the type sizes come from the reported size.
    // The square card is the cell less the spare height (in reported dp, so close to square), split
    // 5/6 above and 1/6 below: HyperOS's clock and weather sit 45 px below the cell's top and 9-10 px
    // above its bottom (54 px spare; Xiaomi 17 Ultra screenshot, 2026-11-23).
    val open = actionStartActivity(WidgetLinks.open(context, Tab.CALENDAR, today))
    val spare = if (square) size.height.value - w else 0f
    Box(GlanceModifier.fillMaxSize().padding(top = (spare * 5f / 6f).dp, bottom = (spare / 6f).dp)) {
    Column(GlanceModifier.fillMaxSize().cornerRadius(20.dp).background(look.color { it.surface }).padding(pad.dp)) {
        Column(GlanceModifier.fillMaxWidth().height(headerH.dp).clickable(open), verticalAlignment = Alignment.CenterVertically) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Tháng ${today.month}", style = TextStyle(color = look.color { it.fg }, fontSize = fixedSp(titleDp), fontWeight = FontWeight.Bold), maxLines = 1)
                if (showYear) {
                    Text(" ${today.year}", style = TextStyle(color = look.color { it.fg3 }, fontSize = fixedSp(titleDp), fontWeight = FontWeight.Bold), maxLines = 1)
                }
                if (events != null && eventText != null) {
                    val festive = events.second
                    Box(GlanceModifier.defaultWeight().padding(start = eventGap.dp), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            eventText,
                            style = TextStyle(
                                color = look.color { if (festive) it.gold else it.lunar },
                                fontSize = fixedSp(eventDp), fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.End,
                            ),
                            maxLines = 1,
                        )
                    }
                }
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
                week.forEach { d -> DayCell(look, d, today, marks, GlanceModifier.defaultWeight().fillMaxHeight(), cellW, solarDp, if (showLunar) lunarDp else null) }
            }
        }
    }
    }
}

/** [text] cut to about [chars] letters with "…" (whole when it fits); null when not even 3 fit. */
private fun ellipsize(text: String, chars: Float): String? {
    val n = chars.toInt()
    return when {
        text.length <= n -> text
        n < 3 -> null
        else -> text.take(n - 1).trimEnd(' ', '·') + "…"
    }
}

/**
 * Today's events in one line ("Tết Hạ nguyên", several joined by " · "): a holiday, the user's own
 * events, commemorative days and festivals, else mồng 1 / rằm. The flag: the first is the user's own
 * or a commemorative day / festival (gold) rather than a holiday or mồng 1 / rằm (red); null: none.
 */
private fun todayEvents(today: SolarDate, personal: List<LunarEvent>): Pair<String, Boolean>? {
    val info = DayInfo.orNull(today) ?: return null
    val own = PersonalEvents.on(personal, today).map { it.title }
    val names = listOfNotNull(info.holiday) + own + info.observances.map { it.first } +
        listOfNotNull(if (info.holiday == null && own.isEmpty() && info.observances.isEmpty()) lunarDayLabel(info.lunar) else null)
    if (names.isEmpty()) return null
    return names.joinToString(" · ") to (info.holiday == null && (own.isNotEmpty() || info.observances.isNotEmpty()))
}

@Composable
private fun DayCell(look: WidgetLook, d: SolarDate, today: SolarDate, marks: Marks, modifier: GlanceModifier, cellW: Float, solarDp: Float, lunarDp: Float?) {
    val context = LocalContext.current
    val info = DayInfo.orNull(d)
    val isToday = d == today
    val inMonth = d.month == today.month
    val red = d.isSunday || info?.holiday != null
    // Other months' days fade, as on the Calendar page.
    val fade = if (inMonth) 1f else 0.35f
    val solarColor = look.color { (if (isToday) it.brand else if (red) it.lunar else it.fg).copy(alpha = fade) }
    // Dots: the app's 5 dp beside a 17 dp number, so about 0.3 of the number; 1 dp apart when stacked.
    val dotDp = (solarDp * 0.3f).clampTo(2.5f, 5f)
    // The first event's colour, gold for a note alone (as the Calendar page).
    val dotToken = PersonalEvents.on(marks.personal, d).firstOrNull()?.color
    val noteDot = if (dotToken != null) listOf(look.color { it.eventColor(dotToken).copy(alpha = fade) }) else emptyList()
    val dayDots = buildList {
        if (info?.rating == Rating.GOOD) add(look.color { it.good.copy(alpha = fade) })
        if (info?.rating == Rating.BAD) add(look.color { it.bad.copy(alpha = 0.7f * fade) })
        if (info != null && Zodiac.clashes(marks.userChi, info)) add(look.color { it.clash.copy(alpha = fade) })
    }
    Box(
        modifier.padding(1.dp)
            .clickable(actionStartActivity(WidgetLinks.open(context, Tab.CALENDAR, d))),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            GlanceModifier.fillMaxSize().let { if (isToday) it.cornerRadius(8.dp).background(look.color { c -> c.brandSoft }) else it },
            horizontalAlignment = Alignment.CenterHorizontally, verticalAlignment = Alignment.CenterVertically,
        ) {
            val solar = "${d.day}"
            val solarLine = @Composable {
                WithDots(noteDot, dotDp, solar.length * 0.58f * solarDp, cellW - 2f) {
                    Text(solar, style = TextStyle(color = solarColor, fontSize = fixedSp(solarDp), fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium), maxLines = 1)
                }
            }
            if (lunarDp != null && info != null) {
                val l = info.lunar
                val mark = l.day == 1 || l.day == 15
                val lunar = if (l.day == 1) "${l.day}/${l.month}" else "${l.day}"
                // Two Texts stacked keep each one's font padding, and Glance has no line height or
                // negative margin: the lines sat about 9 dp apart in a 4×2 widget (user, 2026-11-23).
                // Pinned to the top and the bottom of a box that is just tall enough, they overlap
                // that padding and keep a gap of about 0.1 of the solar size, near the Calendar page's
                // 2 dp. Set on the emulator's screenshots (4×2, solar digits 49 px tall): 1.03 / 0.95
                // overlapped the lines by 8 px, 1.16 / 0.98 left 2 px; each 0.01 of the solar size
                // moves the gap by about 0.7 px, so 1.25 leaves about 8 px.
                val pairH = 1.25f * solarDp + 0.98f * lunarDp
                Box(GlanceModifier.fillMaxWidth().height(pairH.dp)) {
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) { solarLine() }
                    Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                        WithDots(dayDots, dotDp, lunar.length * 0.58f * lunarDp, cellW - 2f) {
                            Text(
                                lunar,
                                style = TextStyle(
                                    color = look.color { (if (mark) it.lunar else it.fg3).copy(alpha = fade) },
                                    fontSize = fixedSp(lunarDp), fontWeight = if (mark) FontWeight.Bold else FontWeight.Normal,
                                ),
                                maxLines = 1,
                            )
                        }
                    }
                }
            } else {
                solarLine()
            }
        }
    }
}

/**
 * [text] with [dots] stacked just after it, as on the Calendar page. The text stays centred: a gap
 * as wide as the dots goes before it, unless the cell ([room] dp, the text about [textW] dp) is too
 * narrow for both; then the dots get the room and the text sits a little left of centre.
 */
@Composable
private fun WithDots(dots: List<androidx.glance.unit.ColorProvider>, dotDp: Float, textW: Float, room: Float, text: @Composable () -> Unit) {
    if (dots.isEmpty()) { text(); return }
    val gap = (dotDp * 0.5f).clampTo(1f, 2f)
    val side = dotDp + gap
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (textW + 2 * side <= room) Box(GlanceModifier.width(side.dp)) {}
        text()
        Column(GlanceModifier.padding(start = gap.dp)) {
            dots.forEachIndexed { i, color ->
                if (i > 0) Box(GlanceModifier.height(1.dp)) {}
                Image(
                    ImageProvider(R.drawable.widget_dot), contentDescription = null,
                    modifier = GlanceModifier.size(dotDp.dp),
                    colorFilter = ColorFilter.tint(color),
                )
            }
        }
    }
}
