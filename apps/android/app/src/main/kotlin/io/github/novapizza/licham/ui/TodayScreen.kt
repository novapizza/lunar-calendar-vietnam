package io.github.novapizza.licham.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Brightness6
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.SheetKind
import io.github.novapizza.licham.Tab
import io.github.novapizza.licham.ThemePref
import io.github.novapizza.licham.core.CalendarData
import io.github.novapizza.licham.core.DayFacts
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.Rating
import io.github.novapizza.licham.core.Zodiac
import io.github.novapizza.licham.core.lunarDayLabel
import io.github.novapizza.licham.core.plusDays
import io.github.novapizza.licham.core.weekOfYear
import java.time.LocalDateTime

/**
 * Hôm nay (views/TodayView.tsx): the selected day with day arrows and swipe.
 * Phones: one column. Wide windows (≥ 600 dp): date on the left, the day's details on the right.
 */
@Composable
fun TodayScreen(state: AppState, now: LocalDateTime, wide: Boolean) {
    val info = state.selectedInfo
    val viewingToday = state.selectedDate == AppState.today()
    val hourChi = if (viewingToday) DayFacts.chiOfHour(now.hour) else null

    val header: @Composable () -> Unit = { TodayHeader(state, viewingToday, wide) }
    val dateGroup: @Composable () -> Unit = {
        Hero(state, info, now, wide)
        CanChiCard(info)
    }
    val dayGroup: @Composable () -> Unit = {
        Assessment(state, info)
        Activities(info)
        GioHoangDaoCard(info, hourChi)
        NotesCard(state.notesOn(state.selectedDate), { state.addNote(state.selectedDate, it) }, state::deleteNote)
        AboutLink { state.sheet = SheetKind.ABOUT }
    }

    if (!wide) {
        Page { header(); dateGroup(); dayGroup() }
    } else {
        Row(Modifier.fillMaxHeight()) {
            Page(Modifier.weight(1.15f), padding = androidx.compose.foundation.layout.PaddingValues(start = 32.dp, end = 12.dp, top = 32.dp, bottom = 40.dp), gap = 24.dp) { header(); dateGroup() }
            Page(Modifier.weight(1f), padding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 32.dp, top = 32.dp, bottom = 40.dp), gap = 24.dp) { dayGroup() }
        }
    }
}

@Composable
private fun TodayHeader(state: AppState, viewingToday: Boolean, wide: Boolean) {
    val c = LocalLichAmColors.current
    val d = state.selectedDate
    // text-[clamp(1rem,5.2vw,1.5rem)] on phones, text-3xl with the rail.
    val vw = LocalConfiguration.current.screenWidthDp
    val titleSize = if (wide) 30f else (vw * 0.052f).coerceIn(16f, 24f)
    Row(verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            SectionLabel(if (viewingToday) "Hôm nay" else "Đang xem")
            Text(
                "${d.weekdayName()}, ${d.day} Tháng ${d.month}", color = c.fg, fontSize = titleSize.sp, lineHeight = (titleSize * 1.5f).sp,
                fontWeight = FontWeight.ExtraBold, letterSpacing = HeadingTracking, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (!viewingToday) {
            if (wide) Chip("Hôm nay", Icons.AutoMirrored.Filled.Undo, { state.selectDate(AppState.today()) }, textColor = c.brand)
            else IconBtn(Icons.AutoMirrored.Filled.Undo, "Về hôm nay", { state.selectDate(AppState.today()) }, tint = c.brand, bg = c.brandSoft, iconSize = 18.dp)
            HSpace(4.dp)
        }
        val (icon, label) = when (state.themePref) {
            ThemePref.SYSTEM -> Icons.Outlined.Brightness6 to "Giao diện: theo hệ thống"
            ThemePref.LIGHT -> Icons.Outlined.LightMode to "Giao diện: sáng"
            ThemePref.DARK -> Icons.Outlined.DarkMode to "Giao diện: tối"
        }
        IconBtn(icon, label, state::cycleTheme)
    }
}

/** Hero pill: rounded-full px-3 py-1.5 text-xs. */
@Composable
private fun HeroPill(text: String, fg: Color, bg: Color, icon: ImageVector? = null, bold: Boolean = false) {
    Row(Modifier.clip(Pill).background(bg).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(13.dp), tint = fg)
            HSpace(6.dp)
        }
        Text(text, color = fg, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.SemiBold)
    }
}

@Composable
private fun Hero(state: AppState, info: DayInfo, now: LocalDateTime, wide: Boolean) {
    val c = LocalLichAmColors.current
    val d = state.selectedDate
    val big = if (wide) 80f else 64f
    Box(
        Modifier
            .fillMaxWidth()
            .webShadow(HeroShape, float = true)
            .clip(HeroShape)
            .background(Brush.linearGradient(listOf(c.heroFrom, c.heroVia, c.heroTo)))
            // Decorative moon: -right-14 -top-16 w-56 and -right-2 -top-6 w-28, hero-accent/8.
            .heroMoons(c.heroAccent.copy(alpha = 0.08f), Triple(-56f, -64f, 224f), Triple(-8f, -24f, 112f))
            .swipe(onNext = { state.selectDate(d.plusDays(1)) }, onPrev = { state.selectDate(d.plusDays(-1)) }),
    ) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Filled.ChevronLeft, "Ngày trước", { state.selectDate(d.plusDays(-1)) }, tint = Color.White, bg = Color.White.copy(alpha = 0.1f))
                Text(
                    "Tuần ${d.weekOfYear} · ${d.year}", color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp, lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                )
                IconBtn(Icons.Filled.ChevronRight, "Ngày sau", { state.selectDate(d.plusDays(1)) }, tint = Color.White, bg = Color.White.copy(alpha = 0.1f))
            }
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp)) {
                Column(Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("DƯƠNG LỊCH", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, lineHeight = 16.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.em)
                    Text("${d.day}", color = Color.White, fontSize = big.sp, lineHeight = big.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.025).em, modifier = Modifier.padding(top = 4.dp).leadingNone(big.sp))
                    Text("Tháng ${d.month}, ${d.year}", color = Color.White, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp))
                }
                Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.15f)))
                Column(Modifier.weight(1f).padding(start = 20.dp)) {
                    Text("ÂM LỊCH", color = c.heroAccent.copy(alpha = 0.8f), fontSize = 11.sp, lineHeight = 16.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.12.em)
                    Text("${info.lunar.day}", color = c.heroAccent, fontSize = big.sp, lineHeight = big.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.025).em, modifier = Modifier.padding(top = 4.dp).leadingNone(big.sp))
                    Text(
                        "Tháng ${info.lunar.month}${if (info.lunar.leap) " (nhuận)" else ""}", color = Color.White, fontSize = 14.sp, lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                val hourName = CalendarData.calendar.chi[DayFacts.chiOfHour(now.hour)]
                HeroPill("%02d:%02d · Giờ %s".format(now.hour, now.minute, hourName), Color.White, Color.White.copy(alpha = 0.12f), Icons.Outlined.Schedule)
                lunarDayLabel(info.lunar)?.let { HeroPill(it, c.heroAccentInk, c.heroAccent, bold = true) }
                info.holiday?.let { HeroPill(it, c.lunar, Color.White, Icons.Filled.Flag, bold = true) }
            }
        }
    }
}

private data class RatingLook(val icon: ImageVector, val label: String, val bg: Color, val fg: Color)

@Composable
private fun Assessment(state: AppState, info: DayInfo) {
    val c = LocalLichAmColors.current
    val r = when (info.rating) {
        Rating.GOOD -> RatingLook(Icons.Outlined.ThumbUp, "Ngày tốt", c.goodSoft, c.good)
        Rating.BAD -> RatingLook(Icons.Outlined.ThumbDown, "Ngày xấu", c.badSoft, c.bad)
        Rating.NEUTRAL -> RatingLook(Icons.Filled.Remove, "Ngày bình thường", c.surface2, c.fg2)
    }
    val menh = state.menh
    Card(padding = 0.dp) {
        InfoRow(r.icon, r.bg, r.fg, r.label, "Trực ${info.truc}")
        Divider()
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(R16).background(c.surface2), contentAlignment = Alignment.Center) {
                Text(state.userChi?.let { AppState.chiEmoji[it] } ?: "🧧", fontSize = 18.sp, lineHeight = 18.sp, modifier = Modifier.leadingNone(18.sp))
            }
            HSpace(12.dp)
            Column(Modifier.weight(1f)) {
                Text("Tuổi của bạn", color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    menh?.let { "Mệnh ${it.element.info.name} · ${it.napAm}" } ?: "Báo ngày xung tuổi",
                    color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            // `field w-auto py-2 font-semibold`
            Text(
                menh?.canChi ?: "Nhập ngày sinh", color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
                modifier = Modifier.clip(R12).background(c.surface2).clickable { state.selectTab(Tab.TOOLS) }.padding(horizontal = 14.dp, vertical = 9.dp),
            )
        }
        if (Zodiac.clashes(state.userChi, info)) {
            Divider()
            InfoRow(Icons.Outlined.SentimentDissatisfied, c.clashSoft, c.clash, "Xung với tuổi ${state.userChi}", "Nên cân nhắc việc quan trọng")
        }
    }
}

@Composable
private fun Activities(info: DayInfo) {
    val c = LocalLichAmColors.current
    Card {
        SectionLabel("Việc nên làm")
        VSpace(12.dp)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                Triple("wedding", "Cưới hỏi", Icons.Outlined.FavoriteBorder),
                Triple("moving", "Chuyển nhà", Icons.Outlined.Handyman),
                Triple("opening", "Khai trương", Icons.Outlined.Storefront),
            ).forEach { (key, label, icon) ->
                val ok = key in info.activities
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(icon, if (ok) c.goodSoft else c.surface2, if (ok) c.good else c.fg3, size = 36.dp, iconSize = 17.dp, shape = R12)
                    HSpace(12.dp)
                    Text(label, color = if (ok) c.fg else c.fg2, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Badge(if (ok) "Tốt" else "Không hợp", if (ok) c.good else c.fg3, if (ok) c.goodSoft else c.surface2)
                }
            }
        }
        if (info.truc == "Trừ") {
            VSpace(12.dp)
            Row(Modifier.fillMaxWidth().clip(R12).background(c.surface2).padding(12.dp)) {
                Icon(Icons.Filled.AutoAwesome, null, Modifier.size(14.dp).padding(top = 2.dp), tint = c.gold)
                HSpace(8.dp)
                Text("Ngày Trừ: tốt để dọn dẹp, chữa bệnh, giải toả mâu thuẫn.", color = c.fg2, fontSize = 12.sp, lineHeight = 19.5.sp)
            }
        }
    }
}
