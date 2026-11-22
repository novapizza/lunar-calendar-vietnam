package io.github.novapizza.licham.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.platform.LocalDensity
import io.github.novapizza.licham.core.LunarCalendar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.core.EventKind
import io.github.novapizza.licham.core.Upcoming
import io.github.novapizza.licham.core.UpcomingEvent
import io.github.novapizza.licham.core.weekdayIndex

private fun countdown(days: Int) = when {
    days == 0 -> "Hôm nay"
    days == 1 -> "Ngày mai"
    days == -1 -> "Hôm qua"
    days < 0 -> "${-days} ngày trước"
    else -> "$days ngày nữa"
}

/** Months loaded at least: this month and the next three, so three months from today or more. */
private const val MIN_MONTHS = 4

/**
 * Sự kiện: the next holiday highlighted, then the following ones grouped by solar month, without
 * end, as the web: months are loaded one at a time, at first through the third month from now and
 * until the screen is full, then whenever less than 600 dp of list is left below the screen, up
 * to the end of the lunar tables (2099).
 * Two-pane windows show a grid filled row by row (FlowPanes), the title and the next event across
 * both columns (in book the event's text stays in the left pane, clear of the hinge).
 */
@Composable
fun EventsScreen(state: AppState, wide: Boolean) {
    val today = AppState.today()
    var count by rememberSaveable(today) { mutableIntStateOf(1) }
    val cache = remember(today) { HashMap<Int, List<UpcomingEvent>>() }
    val events = remember(today, count) { (0 until count).flatMap { i -> cache.getOrPut(i) { Upcoming.month(today, i) } } }
    fun ended(c: Int) = Upcoming.monthStart(today, c) > LunarCalendar.LAST_DAY
    // This month's past days are listed too (dimmed); the highlight is the next event from today.
    val next = events.firstOrNull { it.daysUntil >= 0 }
    val groups = remember(events) { events.filter { it !== next }.groupBy { "Tháng ${it.solarDate.month}, ${it.solarDate.year}" }.toList() }

    // Load the next month while fewer than MIN_MONTHS are loaded or less than 600 dp is left
    // below the screen; after each load wait two frames (composition, then layout) so the scroll
    // range is up to date before deciding again.
    val scroll = rememberScrollState()
    val ahead = with(LocalDensity.current) { 600.dp.roundToPx() }
    LaunchedEffect(scroll, today) {
        snapshotFlow { scroll.maxValue - scroll.value }.collect {
            while (!ended(count) && (count < MIN_MONTHS || scroll.maxValue - scroll.value < ahead)) {
                count++
                withFrameNanos {}
                withFrameNanos {}
            }
        }
    }

    val title: @Composable () -> Unit = {
        Column {
            SectionLabel("Sắp diễn ra")
            PageTitle("Ngày lễ & sự kiện", if (wide) 30f else 24f)
        }
    }
    val highlight: @Composable () -> Unit = {
        if (next == null) EmptyEvents() else NextEvent(next)
    }
    // The about link moved out of this screen (Tiện ích has it): an endless list has no bottom.
    if (!wide && LocalHinge.current == null) {
        Page(scroll = scroll) {
            title(); highlight()
            // mt-6 / mb-6: 24 between the hero and each month group.
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                groups.forEach { (month, items) -> MonthGroup(month, items) }
            }
            if (ended(count)) EndNote()
        }
    } else {
        // Items: title and next event (one item across both columns), then each month.
        FlowPanes(top = if (wide) 32.dp else 16.dp, bottom = 40.dp, gap = 24.dp, spanning = 1, scroll = scroll) {
            Column(verticalArrangement = Arrangement.spacedBy(if (wide) 24.dp else 16.dp)) { title(); highlight() }
            groups.forEach { (month, items) -> MonthGroup(month, items) }
        }
    }
}

@Composable
private fun EndNote() {
    Text(
        "Lịch tính đến hết năm 2099.", color = LocalLichAmColors.current.fg3, fontSize = 12.sp, lineHeight = 16.sp,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
    )
}

@Composable
private fun EmptyEvents() {
    val c = LocalLichAmColors.current
    Card(padding = 40.dp) {
        Icon(Icons.Filled.EventBusy, null, Modifier.size(40.dp).align(Alignment.CenterHorizontally), tint = c.fg3)
        VSpace(12.dp)
        Text("Không có sự kiện sắp tới", color = c.fg2, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun NextEvent(e: UpcomingEvent) {
    val c = LocalLichAmColors.current
    Box(
        Modifier.fillMaxWidth().webShadow(HeroShape, float = true).clip(HeroShape)
            .background(Brush.linearGradient(listOf(c.heroFrom, c.heroVia, c.heroTo)))
            // -right-12 -top-12 w-44, hero-accent/8
            .heroMoons(c.heroAccent.copy(alpha = 0.08f), Triple(-48f, -48f, 176f)),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("SỰ KIỆN TIẾP THEO", color = Color.White.copy(alpha = 0.65f), fontSize = 11.sp, lineHeight = 16.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.em)
            VSpace(8.dp)
            Row(verticalAlignment = Alignment.Bottom) {
                // book: the card spans both panes; the text ends 16 dp short of the left pane's
                // column (20 dp is the card's padding), clear of the hinge.
                val textMax = LocalLeftColumn.current?.takeIf { LocalHinge.current != null }?.let { it - 20.dp - 16.dp } ?: Dp.Infinity
                Column(Modifier.weight(1f)) { Column(Modifier.widthIn(max = textMax)) {
                    Text(e.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp, letterSpacing = HeadingTracking)
                    VSpace(4.dp)
                    Text(
                        // `capitalize` on the web: "… · 15/10 Âm"
                        e.solarDate.longLabel() + if (e.lunar) " · ${e.lunarDay}/${e.lunarMonth} Âm" else "",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 20.sp,
                    )
                } }
                HSpace(16.dp)
                Column(horizontalAlignment = Alignment.End) {
                    Text(if (e.daysUntil == 0) "🎉" else "${e.daysUntil}", color = c.heroAccent, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 36.sp, modifier = Modifier.leadingNone(36.sp))
                    Text(if (e.daysUntil == 0) "Hôm nay" else "ngày nữa", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun MonthGroup(month: String, items: List<UpcomingEvent>) {
    val c = LocalLichAmColors.current
    Column {
        SectionLabel(month, Modifier.padding(start = 4.dp, bottom = 8.dp))
        Card(padding = 0.dp) {
            items.forEachIndexed { i, e ->
                if (i > 0) Divider()
                Row(Modifier.fillMaxWidth().alpha(if (e.daysUntil < 0) 0.55f else 1f).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        Modifier.size(48.dp).clip(R16).background(if (e.lunar) c.lunarSoft else c.brandSoft),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        val fg = if (e.lunar) c.lunar else c.brand
                        // Fixed 48dp tile: text grows with the font setting only up to 1.3x.
                        val dayFs = cappedSp(18f)
                        Text("${e.solarDate.day}", color = fg, fontSize = dayFs, fontWeight = FontWeight.ExtraBold, lineHeight = dayFs, modifier = Modifier.leadingNone(dayFs))
                        Text(WEEKDAY_SHORT[e.solarDate.weekdayIndex], color = fg, fontSize = cappedSp(10f), lineHeight = cappedSp(14f), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp))
                    }
                    HSpace(12.dp)
                    Column(Modifier.weight(1f)) {
                        Text(e.title, color = c.fg, fontSize = 14.sp, lineHeight = 19.25.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (e.lunar) Icons.Filled.NightsStay else Icons.Filled.WbSunny, null, Modifier.size(11.dp), tint = c.gold)
                            HSpace(4.dp)
                            Text(if (e.lunar) "${e.lunarDay}/${e.lunarMonth} âm lịch" else "Dương lịch", color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp)
                            val kind = when (e.kind) {
                                EventKind.OBSERVANCE, EventKind.OBSERVANCE_LUNAR -> "Kỷ niệm"
                                EventKind.FESTIVAL -> "Lễ hội"
                                EventKind.HOLIDAY -> null
                            }
                            if (kind != null) Text(" · $kind", color = c.fg3, fontSize = 12.sp, lineHeight = 16.sp)
                        }
                    }
                    val soon = e.daysUntil in 0..7
                    Badge(countdown(e.daysUntil), if (soon) c.gold else if (e.daysUntil < 0) c.fg3 else c.fg2, if (soon) c.goldSoft else c.surface2)
                }
            }
        }
    }
}
