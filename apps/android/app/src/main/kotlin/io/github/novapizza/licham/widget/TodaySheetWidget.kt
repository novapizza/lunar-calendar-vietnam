package io.github.novapizza.licham.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.R
import io.github.novapizza.licham.Tab
import io.github.novapizza.licham.core.PersonalEvents
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.core.isSunday
import io.github.novapizza.licham.core.lunarDayLabel
import io.github.novapizza.licham.core.weekOfYear
import io.github.novapizza.licham.ui.LUNAR_MONTH_NAMES
import io.github.novapizza.licham.ui.weekdayName

/**
 * Tờ lịch hôm nay: today as the Today page's tear-off sheet (3×4 by default, resizable). Upright, the app's sheet is drawn on a bitmap (SheetPainter: Playfair, gold foil, rings);
 * wider than tall, a Glance layout puts the solar day on the left and the lunar date on the right.
 */
class TodaySheetWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val look = WidgetLook.of(context)
        val today = AppState.today()
        val own = PersonalEvents.on(AppState(context.applicationContext).lunarEvents, today).firstOrNull()?.title
        WidgetFontScale.sync(context)
        provideContent { WidgetFontScale.Provide { Sheet(look, today, own) } }
    }
}

class TodaySheetWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodaySheetWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        WidgetClock.schedule(context)
    }
}

private val GOLD = ColorProvider(R.color.widget_gold)
private val GOLD_INK = ColorProvider(R.color.widget_gold_ink)
private val WHITE = ColorProvider(Color.White)
private val WHITE_70 = ColorProvider(Color.White.copy(alpha = 0.7f))
private val WHITE_55 = ColorProvider(Color.White.copy(alpha = 0.55f))

@Composable
private fun Sheet(look: WidgetLook, d: SolarDate, own: String?) {
    val context = LocalContext.current
    val size = LocalSize.current
    val info = DayInfo.of(d)
    // Inside the 6dp margin and the double gold frame (6dp each side).
    val w = size.width.value - 24f
    val h = size.height.value - 24f
    // Upright: the app's sheet itself, drawn on a bitmap (SheetPainter) at the screen's density.
    if (w <= h * 1.05f) {
        val density = context.resources.displayMetrics.density
        val bitmap = SheetPainter(context, look.now(context))
            .paint(d, info, (size.width.value * density).toInt(), (size.height.value * density).toInt(), nameOf(info, own))
        Image(
            ImageProvider(bitmap),
            contentDescription = "${d.weekdayName()}, ${d.day}/${d.month}/${d.year}, âm lịch ${info.lunar.day}/${info.lunar.month}",
            modifier = GlanceModifier.fillMaxSize().clickable(actionStartActivity(WidgetLinks.open(context, Tab.TODAY))),
            contentScale = ContentScale.Fit,
        )
        return
    }
    Box(
        GlanceModifier.fillMaxSize().cornerRadius(20.dp).background(look.color { it.heroVia })
            .clickable(actionStartActivity(WidgetLinks.open(context, Tab.TODAY))),
    ) {
        Image(ImageProvider(Shade.bitmap), contentDescription = null, modifier = GlanceModifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
        Box(GlanceModifier.fillMaxSize().padding(6.dp)) {
            Box(
                GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_frame)).padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Wider than tall: the solar day on the left, the lunar date on the right.
                Landscape(d, info, w, h, nameOf(info, own))
            }
        }
    }
}

/** The sheet's name line: holiday, else the user's own event, else commemorative day / festival, else "Mồng 1" / "Ngày rằm". */
private fun nameOf(info: DayInfo, own: String?) = info.holiday ?: own ?: info.observance ?: lunarDayLabel(info.lunar)

@Composable
private fun Landscape(d: SolarDate, info: DayInfo, w: Float, h: Float, name: String?) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "THÁNG ${d.month} · TUẦN ${d.weekOfYear}",
                style = TextStyle(color = GOLD, fontSize = fixedSp((h * 0.065f).clampTo(7f, 11f)), fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(
                "${d.day}",
                style = TextStyle(color = GOLD, fontSize = fixedSp(minOf(h * 0.48f, w * 0.26f)), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
                maxLines = 1,
            )
            Weekday(d, info, (h * 0.075f).clampTo(8f, 13f))
        }
        Column(GlanceModifier.defaultWeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            if (h > 120f) Moon(info, minOf(h * 0.52f, w * 0.3f)) else LunarLine(info, (h * 0.09f).clampTo(8f, 13f))
            if (name != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    name,
                    style = TextStyle(color = GOLD, fontSize = fixedSp((h * 0.075f).clampTo(8f, 13f)), fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif, textAlign = TextAlign.Center),
                    maxLines = 2,
                )
            }
        }
    }
}

/** Weekday in capitals; Sundays and holidays (red on a paper calendar) get the gold seal. */
@Composable
private fun Weekday(d: SolarDate, info: DayInfo, fontDp: Float) {
    val red = d.isSunday || info.holiday != null
    val text = d.weekdayName().uppercase()
    val style = TextStyle(color = if (red) GOLD_INK else WHITE, fontSize = fixedSp(fontDp), fontWeight = FontWeight.Bold)
    Box(
        GlanceModifier.let { if (red) it.background(ImageProvider(R.drawable.widget_seal)) else it }
            .padding(horizontal = (fontDp * 0.9f).dp, vertical = (fontDp * 0.25f).dp),
    ) {
        Text(text, style = style, maxLines = 1)
    }
}

/** The lunar date in a gold ring: "ÂM LỊCH" (or "NHUẬN"), the day, "tháng …". */
@Composable
private fun Moon(info: DayInfo, ring: Float) {
    val month = LUNAR_MONTH_NAMES[info.lunar.month - 1]
    Box(
        GlanceModifier.size(ring.dp).background(ImageProvider(R.drawable.widget_moon)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (info.lunar.leap) "NHUẬN" else "ÂM LỊCH",
                style = TextStyle(color = GOLD, fontSize = fixedSp(ring * 0.085f), fontWeight = FontWeight.Medium),
                maxLines = 1,
            )
            Text(
                "${info.lunar.day}",
                style = TextStyle(color = GOLD, fontSize = fixedSp(ring * 0.36f), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
                maxLines = 1,
            )
            Text(
                "tháng $month",
                style = TextStyle(color = WHITE_70, fontSize = fixedSp(ring * if (month.length > 5) 0.09f else 0.105f), fontStyle = FontStyle.Italic, fontFamily = FontFamily.Serif),
                maxLines = 1,
            )
        }
    }
}

/** One line when there is no room for the ring: "Âm lịch 14 tháng Mười". */
@Composable
private fun LunarLine(info: DayInfo, fontDp: Float) {
    val leap = if (info.lunar.leap) " nhuận" else ""
    Text(
        "Âm lịch ${info.lunar.day} tháng ${LUNAR_MONTH_NAMES[info.lunar.month - 1]}$leap",
        style = TextStyle(color = WHITE, fontSize = fixedSp(fontDp), fontFamily = FontFamily.Serif, textAlign = TextAlign.Center),
        maxLines = 1,
    )
}
