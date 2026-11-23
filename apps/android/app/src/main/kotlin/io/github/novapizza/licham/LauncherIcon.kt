package io.github.novapizza.licham

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import io.github.novapizza.licham.core.Element

/**
 * The launcher icon follows the app's main colour (Màu chủ đạo): one activity-alias per palette in
 * the manifest, each with its icon (the crescent on the palette's dark lacquer), exactly one enabled.
 *
 * Switched when the app leaves the screen (MainActivity.onStop), not while it is in use: the
 * launcher redraws its icon then, which some launchers take a few seconds to do. The new entry is
 * enabled before the old one is disabled, so there is always one.
 */
object LauncherIcon {
    private val aliases = mapOf(
        Element.HOA to "IconHoa",
        Element.KIM to "IconKim",
        Element.THUY to "IconThuy",
        Element.MOC to "IconMoc",
        Element.THO to "IconTho",
    )
    /** The manifest's default: the one enabled at install. */
    private val atInstall = Element.HOA

    fun apply(context: Context, palette: Element) {
        val pm = context.packageManager
        // Aliases are named in the manifest's namespace, whatever the application id; taken from
        // MainActivity, whose name R8 keeps (a manifest entry), unlike this object's package.
        val base = MainActivity::class.java.name.substringBeforeLast('.')
        fun component(e: Element) = ComponentName(context, "$base.${aliases.getValue(e)}")
        fun enabled(e: Element) = when (pm.getComponentEnabledSetting(component(e))) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> e == atInstall
            else -> false
        }
        if (!enabled(palette)) {
            pm.setComponentEnabledSetting(component(palette), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        }
        aliases.keys.filter { it != palette && enabled(it) }.forEach {
            pm.setComponentEnabledSetting(component(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
