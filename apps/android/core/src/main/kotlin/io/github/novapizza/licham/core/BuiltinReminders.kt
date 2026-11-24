package io.github.novapizza.licham.core

/**
 * Reminders for built-in days (shared/design/events.md, "Reminders for built-in days"): one
 * event per built-in day ref, stored under `builtinReminders`, with the personal events' repeat,
 * end and reminder fields. It is never listed; the day's row shows a bell instead.
 */
object BuiltinReminders {
    /** Refs of every month's mồng 1 / rằm, whose reminder may repeat monthly ([UpcomingEvent.ref]). */
    const val MONG_1 = "Mồng 1"
    const val RAM = "Ngày rằm"

    /** The repeats offered for a built-in day, the default first: every month's mồng 1 / rằm also monthly. */
    fun repeats(ref: String): List<String> =
        if (ref == MONG_1 || ref == RAM) listOf(LunarEvent.MONTHLY, LunarEvent.YEARLY, LunarEvent.NONE)
        else listOf(LunarEvent.YEARLY, LunarEvent.NONE)

    /** The reminder kept for a built-in day, if any. */
    fun of(reminders: List<LunarEvent>, ref: String): LunarEvent? = reminders.firstOrNull { it.ref == ref }

    /** Whether a built-in day's occurrence on [day] has a reminder. */
    fun reminds(reminders: List<LunarEvent>, ref: String?, day: SolarDate): Boolean {
        val r = ref?.let { of(reminders, it) } ?: return false
        return r.reminders.isNotEmpty() && PersonalEvents.occurrenceStarts(r, day, day).isNotEmpty()
    }
}
