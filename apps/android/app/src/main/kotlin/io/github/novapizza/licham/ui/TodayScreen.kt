package io.github.novapizza.licham.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Handyman
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.Tab
import io.github.novapizza.licham.core.DayFacts
import io.github.novapizza.licham.core.DayInfo
import io.github.novapizza.licham.core.Rating
import io.github.novapizza.licham.core.Zodiac
import java.time.LocalDateTime

/**
 * Hôm nay (views/TodayView.tsx): the selected day as a tear-off calendar sheet (TearOffSheet).
 * Phones: one column. Wide windows (≥ 600 dp): date on the left, the day's details on the right.
 */
@Composable
fun TodayScreen(state: AppState, now: LocalDateTime, wide: Boolean) {
    val info = state.selectedInfo
    val viewingToday = state.selectedDate == AppState.today()
    val hourChi = if (viewingToday) DayFacts.chiOfHour(now.hour) else null

    // The date as a tear-off calendar sheet; it carries the theme and "back to today" buttons,
    // so the page has no title row (as on the web).
    val dateGroup: @Composable () -> Unit = { TearOffSheet(state, info, now) }
    val dayGroup: @Composable () -> Unit = {
        Assessment(state, info)
        Activities(info)
        GioHoangDaoCard(info, hourChi)
        NotesCard(state.notesOn(state.selectedDate), { state.addNote(state.selectedDate, it) }, state::deleteNote)
    }

    if (!wide && LocalHinge.current == null) {
        Page { dateGroup(); dayGroup() }
    } else {
        // The date stays put (the sheet fits the pane, 16 dp to spare below); only the day's cards scroll.
        TwoPanes(
            1.15f, 1f, top = if (wide) 32.dp else 16.dp, bottom = 40.dp, leftBottom = 16.dp, gap = if (wide) 24.dp else 16.dp,
            left = { dateGroup() }, right = { dayGroup() },
        )
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

/** "Việc nên làm": the three activities and the Trừ note; also the Calendar's day panel. */
@Composable
internal fun Activities(info: DayInfo) {
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
