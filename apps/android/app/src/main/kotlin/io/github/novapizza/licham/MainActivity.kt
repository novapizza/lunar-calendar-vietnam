package io.github.novapizza.licham

import android.content.Context
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.novapizza.licham.ui.AppScaffold
import io.github.novapizza.licham.ui.LichAmTheme
import io.github.novapizza.licham.ui.MAX_FONT_SCALE
import kotlinx.coroutines.delay
import java.time.LocalDateTime

class MainActivity : ComponentActivity() {
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
        val state = AppState(applicationContext)
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
            BackHandler(enabled = state.tab != Tab.TODAY) { state.selectTab(Tab.TODAY) }
            LichAmTheme(palette = state.palette, dark = dark) {
                AppScaffold(state, now)
            }
        }
    }
}
