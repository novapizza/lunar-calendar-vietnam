package io.github.novapizza.licham.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import androidx.glance.LocalContext
import androidx.glance.appwidget.updateAll
import androidx.glance.unit.ColorProvider
import io.github.novapizza.licham.AppState
import io.github.novapizza.licham.MainActivity
import io.github.novapizza.licham.Tab
import io.github.novapizza.licham.ThemePref
import io.github.novapizza.licham.core.SolarDate
import io.github.novapizza.licham.ui.LichAmColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import androidx.glance.color.ColorProvider as DayNightColor

/**
 * The app's colours for a widget: the palette chosen in the app (element / accent), light and
 * dark from shared/design/tokens.json. "Theo hệ thống" follows the launcher's day/night; a theme
 * forced in the app is kept.
 */
internal class WidgetLook(private val light: LichAmColors, private val dark: LichAmColors, private val theme: ThemePref) {
    /** The colours in use now, for what is drawn on a bitmap (no day/night switch there). */
    fun now(context: Context): LichAmColors = when (theme) {
        ThemePref.LIGHT -> light
        ThemePref.DARK -> dark
        ThemePref.SYSTEM -> {
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            if (night) dark else light
        }
    }

    fun color(pick: (LichAmColors) -> Color): ColorProvider = when (theme) {
        ThemePref.SYSTEM -> DayNightColor(day = pick(light), night = pick(dark))
        ThemePref.LIGHT -> ColorProvider(pick(light))
        ThemePref.DARK -> ColorProvider(pick(dark))
    }

    companion object {
        fun of(context: Context): WidgetLook {
            val app = AppState(context.applicationContext)
            return WidgetLook(LichAmColors.of(app.palette, false), LichAmColors.of(app.palette, true), app.themePref)
        }
    }
}

/** Font size in sp that stays [dp] dp whatever the phone's font scale: widget text is sized to its box. */
@Composable
internal fun fixedSp(dp: Float): TextUnit = (dp / LocalWidgetFontScale.current).sp

/** The font scale [fixedSp] divides by; set at each widget's root by [WidgetFontScale.Provide]. */
internal val LocalWidgetFontScale = compositionLocalOf { 1f }

/**
 * The phone's font scale for the widgets, read from the setting (not the process's configuration,
 * which a process woken by [FontScaleJob] might not have updated yet) and held as state, so a new
 * value also recomposes Glance sessions still running. Not shown to be needed: font size changes
 * a few seconds apart were redrawn late on the emulator (2026-11-24), but JobScheduler had stopped
 * the app's jobs then (WITHIN_QUOTA: at most 20 jobs a minute, and every widget update is a
 * WorkManager job per widget), which explains the delay on its own.
 */
internal object WidgetFontScale {
    private val scale = MutableStateFlow<Float?>(null)

    /** Reads the setting again (before the widgets are redrawn). */
    fun sync(context: Context) {
        scale.value = runCatching { Settings.System.getFloat(context.contentResolver, Settings.System.FONT_SCALE) }
            .getOrElse { context.resources.configuration.fontScale }
    }

    /** [content] with [LocalWidgetFontScale] following the setting. */
    @Composable
    fun Provide(content: @Composable () -> Unit) {
        val context = LocalContext.current
        if (scale.value == null) sync(context)
        val value by scale.collectAsState()
        CompositionLocalProvider(LocalWidgetFontScale provides (value ?: 1f), content = content)
    }
}

internal fun Float.clampTo(min: Float, max: Float) = coerceIn(min, max)

/** Light from the top left, shade to the bottom right, over a solid colour: the sheet's lacquer gradient. */
internal object Shade {
    val bitmap: Bitmap by lazy {
        val w = 48
        val h = 72
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { b ->
            val paint = Paint().apply {
                shader = LinearGradient(
                    0f, 0f, w.toFloat(), h.toFloat(),
                    intArrayOf(0x24FFFFFF, 0x00000000, 0x55000000), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP,
                )
            }
            Canvas(b).drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
    }
}

/** Opening the app from a widget: the tab, and for the Calendar the day to select. */
object WidgetLinks {
    const val EXTRA_TAB = "io.github.novapizza.licham.TAB"
    const val EXTRA_DATE = "io.github.novapizza.licham.DATE"

    fun open(context: Context, tab: Tab, date: SolarDate? = null): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_TAB, tab.name)
            if (date != null) putExtra(EXTRA_DATE, date.toString())
            // A distinct data URI per target, so each day cell gets its own PendingIntent.
            data = Uri.parse("licham://${tab.name.lowercase()}/${date ?: ""}")
        }
}

/**
 * Widgets show "today": they are redrawn within ten minutes after midnight (a windowed alarm,
 * which waits for the phone to wake while it dozes), when the clock or time zone changes, when
 * the app goes to the background (a new palette or theme) and when the font size changes
 * ([FontScaleJob]).
 */
object WidgetClock {
    private const val ACTION_TICK = "io.github.novapizza.licham.WIDGET_TICK"

    fun schedule(context: Context) {
        FontScaleJob.schedule(context)
        val next = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 5_000
        // A plain inexact alarm got a one-hour window (dumpsys alarm, 2026-11-22: "window=+1h"); a set
        // window keeps the redraw within minutes of midnight without the exact-alarm permission.
        context.getSystemService(AlarmManager::class.java).setWindow(AlarmManager.RTC, next, 10 * 60_000L, tick(context))
    }

    private fun tick(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 0, Intent(context, WidgetTickReceiver::class.java).setAction(ACTION_TICK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    suspend fun updateAll(context: Context) {
        WidgetFontScale.sync(context)
        TodaySheetWidget().updateAll(context)
        MonthWidget().updateAll(context)
    }

    /** [updateAll] from a broadcast receiver or an activity, off the main thread. */
    fun refresh(context: Context, done: () -> Unit = {}) {
        CoroutineScope(Dispatchers.Default).launch {
            try { updateAll(context.applicationContext) } finally { done() }
        }
    }
}

/** Midnight alarm, clock and time-zone changes: redraw the widgets and set the next alarm. */
class WidgetTickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        WidgetClock.schedule(context)
        WidgetClock.refresh(context) { pending.finish() }
    }
}
