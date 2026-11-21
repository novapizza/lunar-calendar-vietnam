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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.SheetKind
import io.github.novapizza.licham.core.Upcoming
import io.github.novapizza.licham.core.UpcomingEvent
import io.github.novapizza.licham.core.weekdayIndex

private fun countdown(days: Int) = when (days) {
    0 -> "Hôm nay"
    1 -> "Ngày mai"
    else -> "$days ngày nữa"
}

/** Sự kiện: the next holiday highlighted, then the following ones grouped by solar month. */
@Composable
fun EventsScreen(state: AppState, wide: Boolean) {
    val today = AppState.today()
    val events = remember(today) { Upcoming.events(today, 12) }
    val next = events.firstOrNull()
    val groups = remember(events) { events.drop(1).groupBy { "Tháng ${it.solarDate.month}, ${it.solarDate.year}" }.toList() }

    val title: @Composable () -> Unit = {
        Column {
            SectionLabel("Sắp diễn ra")
            PageTitle("Ngày lễ & sự kiện", if (wide) 30f else 24f)
        }
    }
    val highlight: @Composable () -> Unit = {
        if (next == null) EmptyEvents() else NextEvent(next)
    }
    val list: @Composable () -> Unit = {
        // mt-6 / mb-6: 24 between the hero and each month group.
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) { groups.forEach { (month, items) -> MonthGroup(month, items) } }
        AboutLink { state.sheet = SheetKind.ABOUT }
    }
    if (!wide) {
        Page {
            title(); highlight()
            Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) { list() }
        }
    } else {
        Row(Modifier.fillMaxHeight()) {
            Page(Modifier.weight(1f), padding = PaddingValues(start = 32.dp, end = 12.dp, top = 32.dp, bottom = 40.dp), gap = 24.dp) { title(); highlight() }
            Page(Modifier.weight(1f), padding = PaddingValues(start = 12.dp, end = 32.dp, top = 32.dp, bottom = 40.dp), gap = 0.dp) { list() }
        }
    }
}

@Composable
private fun EmptyEvents() {
    val c = LocalLichAmColors.current
    Card(padding = 40.dp) {
        Icon(Icons.Filled.EventBusy, null, Modifier.size(40.dp).align(Alignment.CenterHorizontally), tint = c.fg3)
        VSpace(12.dp)
        Text("Không có sự kiện trong 6 tháng tới", color = c.fg2, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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
                Column(Modifier.weight(1f)) {
                    Text(e.title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp, letterSpacing = HeadingTracking)
                    VSpace(4.dp)
                    Text(
                        // `capitalize` on the web: "… · 15/10 Âm"
                        e.solarDate.longLabel() + if (e.lunar) " · ${e.lunarDay}/${e.lunarMonth} Âm" else "",
                        color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, lineHeight = 20.sp,
                    )
                }
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
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                        }
                    }
                    Badge(countdown(e.daysUntil), if (e.daysUntil <= 7) c.gold else c.fg2, if (e.daysUntil <= 7) c.goldSoft else c.surface2)
                }
            }
        }
    }
}
