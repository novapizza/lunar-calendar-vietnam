package io.github.novapizza.licham.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.outlined.DesktopWindows
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PrivacyTip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.SheetKind
import io.github.novapizza.licham.ThemePref
import io.github.novapizza.licham.core.AccentPref
import io.github.novapizza.licham.core.Element
import io.github.novapizza.licham.core.Menh
import io.github.novapizza.licham.core.MenhCalc

/** The privacy policy, on the web app's site (apps/web/public/privacy.html). */
const val PRIVACY_URL = "https://lunar.coding4pizza.com/privacy"

/** Colour choices for a mệnh; a choice repeating an earlier palette is left out (as on the web). */
private val R8 = RoundedCornerShape(8.dp)

private fun accentOptions(menh: Menh?): List<Triple<AccentPref, String, Element>> {
    val all = listOf(AccentPref.BAN_MENH to "Bản mệnh", AccentPref.TUONG_SINH to "Tương sinh", AccentPref.MAC_DINH to "Mặc định")
    val seen = HashSet<Element>()
    return all.map { (p, l) -> Triple(p, l, MenhCalc.paletteFor(menh, p)) }
        .filter { (p, _, e) -> (menh != null || p == AccentPref.MAC_DINH) && seen.add(e) }
}

/** Tiện ích: tools (converter, finder, export) and settings (birth date, colour, theme). */
@Composable
fun ToolsScreen(state: AppState, wide: Boolean) {
    val tools: @Composable () -> Unit = {
        Column {
            SectionLabel("Công cụ")
            PageTitle("Tiện ích", if (wide) 30f else 24f)
        }
        Card(padding = 0.dp) {
            ToolRow(Icons.Filled.SwapHoriz, "Đổi ngày âm – dương", "Tra ngày dương ra ngày âm và ngược lại") { state.sheet = SheetKind.QUICK_DATE }
            Divider()
            ToolRow(Icons.Filled.Search, "Tìm ngày tốt", "Cưới hỏi, khởi công, khai trương") { state.sheet = SheetKind.AUSPICIOUS }
            Divider()
            ToolRow(Icons.Filled.Download, "Xuất sự kiện âm lịch", "Giỗ, sinh nhật âm lịch ra file .ics cho Google Calendar") { state.sheet = SheetKind.LUNAR_EVENTS }
        }
    }
    val settings: @Composable () -> Unit = {
        SectionLabel("Cài đặt", Modifier.padding(start = 4.dp).offset(y = 8.dp))
        Card(padding = 0.dp) {
            BirthDateRow(state)
            Divider()
            AccentRow(state)
            Divider()
            ThemeRow(state)
        }
        Card(padding = 0.dp) {
            ToolRow(Icons.Outlined.Info, "Thông tin ứng dụng", "Cách tính âm lịch, ngày tốt xấu, xung tuổi") { state.sheet = SheetKind.ABOUT }
            Divider()
            // Google Play asks for the privacy policy in the app too; it opens in the browser (the app itself
            // has no network access).
            val uri = LocalUriHandler.current
            ToolRow(Icons.Outlined.PrivacyTip, "Chính sách quyền riêng tư", "Ứng dụng không thu thập dữ liệu của bạn") {
                runCatching { uri.openUri(PRIVACY_URL) }
            }
        }
    }
    if (!wide && LocalHinge.current == null) {
        Page { tools(); settings() }
    } else {
        TwoPanes(
            1f, 1f, top = if (wide) 32.dp else 16.dp, rightTop = if (wide) 40.dp else 16.dp, bottom = 40.dp, gap = if (wide) 24.dp else 16.dp,
            left = { tools() }, right = { settings() },
        )
    }
}

@Composable
private fun ToolRow(icon: ImageVector, title: String, detail: String, onClick: () -> Unit) {
    val c = LocalLichAmColors.current
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, c.brandSoft, c.brand)
        HSpace(12.dp)
        Column(Modifier.weight(1f)) {
            Text(title, color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = c.fg2, fontSize = 12.sp, lineHeight = 16.5.sp)
        }
        Icon(Icons.Filled.ChevronRight, null, Modifier.size(18.dp), tint = c.fg3)
    }
}

@Composable
private fun BirthDateRow(state: AppState) {
    val c = LocalLichAmColors.current
    val b = state.birthDate
    val menh = state.menh
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ngày sinh", color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("Dương lịch. Tính mệnh, tuổi xung", color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp)
            }
            Row(
                Modifier.clip(R12).background(c.surface2)
                    .clickable(onClickLabel = if (b != null) "Đổi ngày sinh" else "Chọn ngày sinh") { state.sheet = SheetKind.BIRTH_DATE }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(16.dp), tint = c.fg3)
                HSpace(8.dp)
                Text(b?.let { "%02d/%02d/%04d".format(it.day, it.month, it.year) } ?: "Chọn ngày", color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        if (menh != null) {
            VSpace(8.dp)
            Text(
                "Tuổi ${menh.canChi} (${menh.lunarYear} âm lịch) · mệnh ${menh.element.info.name} – ${menh.napAm}",
                color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun AccentRow(state: AppState) {
    val c = LocalLichAmColors.current
    val menh = state.menh
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Màu chủ đạo", color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        if (menh == null) Text("Nhập ngày sinh để dùng màu hợp mệnh", color = c.fg2, fontSize = 12.sp, lineHeight = 16.sp)
        VSpace(8.dp)
        Row(Modifier.fillMaxWidth().clip(R12).background(c.surface2).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            accentOptions(menh).forEach { (pref, label, element) ->
                val active = element == state.palette
                val swatch = LichAmColors.of(element, dark = false).brand
                Column(
                    Modifier.weight(1f).then(if (active) Modifier.webShadow(R8) else Modifier).clip(R8).background(if (active) c.surface else c.surface2)
                        .clickable { state.updateAccent(pref) }.padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    // ring-2 ring-brand ring-offset-2 ring-offset-surface on the active swatch
                    Box(
                        Modifier.size(if (active) 32.dp else 24.dp)
                            .then(if (active) Modifier.border(2.dp, swatch, Pill).padding(4.dp) else Modifier)
                            .clip(Pill).background(swatch),
                    )
                    Text(label, color = if (active) c.fg else c.fg2, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(element.info.color, color = c.fg2, fontSize = 11.sp, lineHeight = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ThemeRow(state: AppState) {
    val c = LocalLichAmColors.current
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Giao diện", color = c.fg, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
        VSpace(8.dp)
        Row(Modifier.fillMaxWidth().clip(R12).background(c.surface2).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf(
                Triple(ThemePref.SYSTEM, "Theo máy", Icons.Outlined.DesktopWindows),
                Triple(ThemePref.LIGHT, "Sáng", Icons.Filled.LightMode),
                Triple(ThemePref.DARK, "Tối", Icons.Filled.DarkMode),
            ).forEach { (pref, label, icon) ->
                val active = state.themePref == pref
                Column(
                    Modifier.weight(1f).then(if (active) Modifier.webShadow(R8) else Modifier).clip(R8).background(if (active) c.surface else c.surface2)
                        .clickable { state.updateTheme(pref) }.padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(icon, null, Modifier.size(16.dp), tint = if (active) c.brand else c.fg2)
                    Text(label, color = if (active) c.brand else c.fg2, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
