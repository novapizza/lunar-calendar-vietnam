package io.github.novapizza.licham.core

/**
 * The small date under a day in a month grid (shared/design/month-grid.md): the day, with its
 * month on the first of a month and on the selected day ("16/10"); [leap] for a leap lunar month,
 * which the grids draw as a small raised "n" after the month.
 */
data class SmallDate(val day: Int, val month: Int? = null, val leap: Boolean = false) {
    /** Plain text, the leap mark as a plain "n": for estimating widths and for screen readers. */
    val text: String get() = if (month == null) "$day" else "$day/$month${if (leap) "n" else ""}"

    companion object {
        /** A lunar day: with its month on mồng 1 and when [selected]. */
        fun lunar(l: LunarDate, selected: Boolean): SmallDate =
            if (l.day == 1 || selected) SmallDate(l.day, l.month, l.leap) else SmallDate(l.day)

        /** A solar day (the small date of a grid picking lunar days): with its month on the 1st and when [selected]. */
        fun solar(d: SolarDate, selected: Boolean): SmallDate =
            if (d.day == 1 || selected) SmallDate(d.day, d.month) else SmallDate(d.day)
    }
}
