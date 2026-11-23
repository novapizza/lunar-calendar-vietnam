package io.github.novapizza.licham

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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
        // Event reminders: the channel and the next alarm, at every start.
        Reminders.ensureChannel(applicationContext)
        Reminders.reschedule(applicationContext)
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
            // Clock for "Giờ …" and the current giờ hoàng đạo: on the minute, from the system's
            // TIME_TICK, sent as each minute starts while the screen is on. Polling every 15 s left it
            // up to 15 s behind (seen 5 s or more behind on 2026-11-23, user). Also on a clock or
            // time zone change, and on coming back to the app (no ticks while the screen was off).
            var now by remember { mutableStateOf(LocalDateTime.now()) }
            DisposableEffect(Unit) {
                val tick = object : BroadcastReceiver() {
                    override fun onReceive(context: Context, intent: Intent) { now = LocalDateTime.now() }
                }
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_TIME_TICK)
                    addAction(Intent.ACTION_TIME_CHANGED)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                }
                ContextCompat.registerReceiver(this@MainActivity, tick, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
                val resumed = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) now = LocalDateTime.now() }
                lifecycle.addObserver(resumed)
                onDispose {
                    unregisterReceiver(tick)
                    lifecycle.removeObserver(resumed)
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
        state.closeAllSheets()
        state.selectTab(tab)
        intent.getStringExtra(WidgetLinks.EXTRA_DATE)?.let(SolarDate::parse)?.let(state::selectDate)
    }

    /**
     * Widgets and the launcher icon follow the app's palette (and widgets its theme): redraw them
     * when the app leaves the screen.
     */
    override fun onStop() {
        super.onStop()
        WidgetClock.refresh(applicationContext)
        LauncherIcon.apply(applicationContext, state.palette)
    }
}
