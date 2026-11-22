package io.github.novapizza.licham.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.R
import io.github.novapizza.licham.Tab
import java.time.LocalDateTime

private data class NavItem(val tab: Tab, val label: String, val icon: ImageVector, val iconOn: ImageVector)

private val NAV = listOf(
    NavItem(Tab.TODAY, "Hôm nay", Icons.Outlined.CalendarToday, Icons.Filled.CalendarToday),
    NavItem(Tab.CALENDAR, "Lịch", Icons.Outlined.GridView, Icons.Filled.GridView),
    NavItem(Tab.EVENTS, "Sự kiện", Icons.Outlined.AutoAwesome, Icons.Filled.AutoAwesome),
)

/**
 * Bottom tab bar on phones (< 600 dp wide) and on an unfolded foldable (book: two phone-like
 * panes split at the hinge, see Panes.kt), a slim left rail on other windows from 600 dp —
 * as the web app.
 * Three labelled tabs plus the logo, which opens Tiện ích.
 */
@Composable
fun AppScaffold(state: AppState, now: LocalDateTime) {
    val c = LocalLichAmColors.current
    BoxWithConstraints(Modifier.fillMaxSize().background(c.bg)) {
        // wide = rail and the tablet type scale; a book window keeps the phone styles in each pane.
        val wide = LocalHinge.current == null && maxWidth >= 600.dp
        val content: @Composable (Modifier) -> Unit = { mod ->
            Box(mod) {
                when (state.tab) {
                    Tab.TODAY -> TodayScreen(state, now, wide)
                    Tab.CALENDAR -> CalendarScreen(state, now, wide)
                    Tab.EVENTS -> EventsScreen(state, wide)
                    Tab.TOOLS -> ToolsScreen(state, wide)
                }
            }
        }
        if (!wide) {
            Column(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxWidth().statusBarsPadding())
                BottomBar(state)
            }
        } else {
            Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                Rail(state)
                content(Modifier.weight(1f).fillMaxHeight().statusBarsPadding().navigationBarsPadding())
            }
        }
        AppSheets(state)
    }
}

/** The logo (rounded-xl); active: ring-2 ring-brand ring-offset-2 ring-offset-surface. */
@Composable
private fun Logo(active: Boolean, size: Int) {
    val c = LocalLichAmColors.current
    Box(
        Modifier.size((size + 8).dp).then(if (active) Modifier.border(2.dp, c.brand, RoundedCornerShape(16.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(c.logo), contentAlignment = Alignment.Center) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size((size * 1.5f).dp))
        }
    }
}

@Composable
private fun TabButton(item: NavItem, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val c = LocalLichAmColors.current
    Column(modifier.clickable(onClick = onClick).padding(top = 8.dp, bottom = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.width(56.dp).height(32.dp).clip(Pill).background(if (active) c.brandSoft else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(item.icon, null, Modifier.size(20.dp), tint = if (active) c.brand else c.fg2)
        }
        Spacer(Modifier.height(4.dp))
        Text(item.label, color = if (active) c.fg else c.fg2, fontSize = 11.sp, lineHeight = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
    }
}

@Composable
private fun BottomBar(state: AppState) {
    val c = LocalLichAmColors.current
    Column(Modifier.fillMaxWidth().background(c.surface)) {
        Divider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            NAV.forEach { item -> TabButton(item, state.tab == item.tab, Modifier.weight(1f)) { state.selectTab(item.tab) } }
            Box(
                Modifier.weight(1f).height(60.dp).clickable { state.selectTab(Tab.TOOLS) }.semantics { contentDescription = "Tiện ích và cài đặt" },
                contentAlignment = Alignment.Center,
            ) { Logo(state.tab == Tab.TOOLS, 36) }
        }
    }
}

@Composable
private fun Rail(state: AppState) {
    val c = LocalLichAmColors.current
    Row(Modifier.fillMaxHeight()) {
        Column(
            Modifier.width(88.dp).fillMaxHeight().background(c.surface).statusBarsPadding().padding(top = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).clickable { state.selectTab(Tab.TOOLS) }.semantics { contentDescription = "Tiện ích và cài đặt" }) { Logo(state.tab == Tab.TOOLS, 40) }
            Spacer(Modifier.height(16.dp))
            NAV.forEach { item -> TabButton(item, state.tab == item.tab, Modifier.fillMaxWidth().padding(horizontal = 8.dp)) { state.selectTab(item.tab) } }
        }
        Box(Modifier.width(1.dp).fillMaxHeight().background(c.line))
    }
}
