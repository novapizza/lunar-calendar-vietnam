package io.github.novapizza.licham.core

/** A lunar date. [month] is 1..12; [leap] marks a leap month (tháng nhuận). */
data class LunarDate(val day: Int, val month: Int, val year: Int, val leap: Boolean)

/** A solar (Gregorian) date. [month] is 1..12. */
data class SolarDate(val year: Int, val month: Int, val day: Int) : Comparable<SolarDate> {
    override fun compareTo(other: SolarDate) = compareValuesBy(this, other, { it.year }, { it.month }, { it.day })
    override fun toString() = "%04d-%02d-%02d".format(year, month, day)

    companion object {
        /** Parses "YYYY-MM-DD"; null when the text has another shape. */
        fun parse(iso: String): SolarDate? {
            val m = Regex("""(\d{4})-(\d{2})-(\d{2})""").matchEntire(iso) ?: return null
            val (y, mo, d) = m.destructured
            return SolarDate(y.toInt(), mo.toInt(), d.toInt())
        }
    }
}

/** Start of one lunar month: its number, leap flag and Julian day number of day 1. */
data class LunarMonthStart(val month: Int, val leap: Boolean, val jd: Int)

/**
 * Vietnamese lunar calendar from Hồ Ngọc Đức's precomputed year tables
 * (shared/data/lunar-years.json, UTC+7), the same tables the web app uses.
 * Range: solar 1900-01-31 (Tết 1900) to 2099-12-31.
 */
object LunarCalendar {
    val FIRST_DAY = SolarDate(1900, 1, 31)
    val LAST_DAY = SolarDate(2099, 12, 31)

    /** Julian day number of a solar date. */
    fun jdFromDate(dd: Int, mm: Int, yy: Int): Int {
        val a = (14 - mm) / 12
        val y = yy + 4800 - a
        val m = mm + 12 * a - 3
        var jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) jd = dd + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return jd
    }

    /** Solar date of a Julian day number. */
    fun jdToDate(jd: Int): SolarDate {
        val b: Int
        val c: Int
        if (jd > 2299160) {
            val a = jd + 32044
            b = (4 * a + 3) / 146097
            c = a - b * 146097 / 4
        } else {
            b = 0
            c = jd + 32082
        }
        val d = (4 * c + 3) / 1461
        val e = c - 1461 * d / 4
        val m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = b * 100 + d - 4800 + m / 10
        return SolarDate(year, month, day)
    }

    private val monthCache = HashMap<Int, List<LunarMonthStart>>()

    /** The 12 or 13 months of lunar year [year], in order (decoding rules in lunar-years.json). */
    fun monthsOf(year: Int): List<LunarMonthStart> = synchronized(monthCache) {
        monthCache.getOrPut(year) {
            val table = CalendarData.lunarYears
            val v = table.years.getOrNull(year - table.firstYear)
                ?: throw IllegalArgumentException("lunar year $year is outside ${table.firstYear}..${table.firstYear + table.years.size - 1}")
            val tetOffset = v shr 17
            val leapLength = 29 + ((v shr 16) and 1)
            val leapMonth = v and 0xF
            var bits = v shr 4
            val lengths = IntArray(12)
            for (i in 11 downTo 0) {
                lengths[i] = 29 + (bits and 1)
                bits = bits shr 1
            }
            var jd = jdFromDate(1, 1, year) + tetOffset
            buildList {
                for (m in 1..12) {
                    add(LunarMonthStart(m, false, jd))
                    jd += lengths[m - 1]
                    if (m == leapMonth) {
                        add(LunarMonthStart(m, true, jd))
                        jd += leapLength
                    }
                }
            }
        }
    }

    /** Solar → lunar. Throws for dates outside [FIRST_DAY]..[LAST_DAY]. */
    fun solarToLunar(yy: Int, mm: Int, dd: Int): LunarDate {
        val date = SolarDate(yy, mm, dd)
        require(date in FIRST_DAY..LAST_DAY) { "$date is outside $FIRST_DAY..$LAST_DAY" }
        val jd = jdFromDate(dd, mm, yy)
        var lunarYear = yy
        var months = monthsOf(yy)
        if (jd < months.first().jd) {
            lunarYear = yy - 1
            months = monthsOf(lunarYear)
        }
        val start = months.last { it.jd <= jd }
        return LunarDate(jd - start.jd + 1, start.month, lunarYear, start.leap)
    }

    fun solarToLunar(date: SolarDate) = solarToLunar(date.year, date.month, date.day)

    /**
     * Lunar → solar, or null when that (leap) month does not exist that year.
     * Like the web app's library, day 30 of a 29-day month rolls into the next month;
     * callers that need an exact date convert the result back.
     */
    fun lunarToSolar(lunarDay: Int, lunarMonth: Int, lunarYear: Int, leap: Boolean): SolarDate? {
        val start = monthsOf(lunarYear).firstOrNull { it.month == lunarMonth && it.leap == leap } ?: return null
        return jdToDate(start.jd + lunarDay - 1)
    }
}
