package io.github.novapizza.licham

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import io.github.novapizza.licham.ui.AppScaffold
import io.github.novapizza.licham.ui.Hinge
import io.github.novapizza.licham.ui.LocalHinge
import io.github.novapizza.licham.ui.LichAmTheme
import io.github.novapizza.licham.ui.MAX_FONT_SCALE
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.widget.WidgetClock
import io.github.novapizza.licham.widget.WidgetLinks
import kotlinx.coroutines.delay
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
    private lateinit var state: AppState

    /** Follow the phone's font size only up to MAX_FONT_SCALE (every window of the app reads this configuration). */
    override fun attachBaseContext(newBase: Context) {
        val config = newBase.resources.configuration
        if (config.fontScale <= MAX_FONT_SCALE) return super.attachBaseContext(newBase)
        val capped = Configuration(config).apply { fontScale = MAX_FONT_SCALE }
        super.attachBaseContext(newBase.createConfigurationContext(capped))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Phones stay portrait; tablets and unfolded foldables rotate (the web app's policy).
        if (resources.configuration.smallestScreenWidthDp < 600) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        enableEdgeToEdge()
        state = AppState(applicationContext)
        // Opened from a widget: its tab and day (not again when the activity is recreated).
        if (savedInstanceState == null) openFromWidget(intent)
        setContent {
            val dark = when (state.themePref) {
                ThemePref.SYSTEM -> isSystemInDarkTheme()
                ThemePref.LIGHT -> false
                ThemePref.DARK -> true
            }
            // Status/navigation bar icons follow the app's theme, not only the system's.
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            // Clock for "Giờ …" and the current giờ hoàng đạo.
            var now by remember { mutableStateOf(LocalDateTime.now()) }
            LaunchedEffect(Unit) {
                while (true) {
                    delay(15_000)
                    now = LocalDateTime.now()
                }
            }
            // The fold, from Jetpack WindowManager: reported only while it crosses this window
            // (unfolded or half open). A fold running top to bottom puts the app in book mode.
            val windowLayout by remember { WindowInfoTracker.getOrCreate(this@MainActivity).windowLayoutInfo(this@MainActivity) }
                .collectAsState(initial = null)
            val hinge = windowLayout?.displayFeatures
                ?.filterIsInstance<FoldingFeature>()
                ?.firstOrNull { it.orientation == FoldingFeature.Orientation.VERTICAL }
                ?.bounds?.let { Hinge(it.left.toFloat(), it.right.toFloat()) }
            BackHandler(enabled = state.tab != Tab.TODAY) { state.selectTab(Tab.TODAY) }
            LichAmTheme(palette = state.palette, dark = dark) {
                CompositionLocalProvider(LocalHinge provides hinge) {
                    AppScaffold(state, now)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openFromWidget(intent)
    }

    /** A widget tap: Hôm nay, or the Calendar on the tapped day (widget/WidgetLinks). */
    private fun openFromWidget(intent: Intent?) {
        val tab = intent?.getStringExtra(WidgetLinks.EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name } } ?: return
        state.sheet = null
        state.selectTab(tab)
        intent.getStringExtra(WidgetLinks.EXTRA_DATE)?.let(SolarDate::parse)?.let(state::selectDate)
    }

    /** Widgets follow the app's palette and theme: redraw them when the app leaves the screen. */
    override fun onStop() {
        super.onStop()
        WidgetClock.refresh(applicationContext)
    }
}
