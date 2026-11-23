package io.github.novapizza.licham.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.StickyNote2
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.core.DayInfo

/**
 * GioHoangDao (components/GioHoangDao.tsx): brand-soft card, one row of six tiles
 * (rounded-xl, shadow-card, emoji clamp(18px, 6vw, 28px)), chi text-sm, hours 11px.
 */
@Composable
fun GioHoangDaoCard(info: DayInfo, currentHourChi: Int?) {
    val c = LocalLichAmColors.current
    val vw = LocalConfiguration.current.screenWidthDp
    val emojiSize = (vw * 0.06f).coerceIn(18f, 28f)
    Card(color = c.brandSoft) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Schedule, null, Modifier.size(16.dp), tint = c.brand)
            HSpace(8.dp)
            Text("Giờ hoàng đạo", color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, letterSpacing = HeadingTracking)
        }
        VSpace(12.dp)
        BoxWithConstraints {
            // Narrow cards (tablet side panel): smaller hour labels so "11–13h" fits.
            val narrow = maxWidth < 300.dp
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                info.gioHoangDao.forEach { h ->
                    val now = h.chiIndex == currentHourChi
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .widthIn(max = 56.dp)
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                // ring-2 ring-brand ring-offset-2 ring-offset-brand-soft on the hour now
                                .then(if (now) Modifier.border(2.dp, c.brand, R16).padding(4.dp) else Modifier)
                                .webShadow(R12)
                                .clip(R12)
                                .background(if (now) c.brand else c.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            val emojiFs = cappedSp(emojiSize)
                            Text(AppState.chiEmoji[h.chi] ?: "🕒", fontSize = emojiFs, lineHeight = emojiFs, modifier = Modifier.leadingNone(emojiFs))
                        }
                        Text(
                            h.chi, color = if (now) c.brand else c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                        Text(
                            h.label, color = if (now) c.brand else c.fg2, fontSize = if (narrow) 10.sp else 11.sp, lineHeight = 16.5.sp,
                            maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
                            fontWeight = if (now) FontWeight.SemiBold else FontWeight.Normal,
                        )
                    }
                }
            }
        }
    }
}

