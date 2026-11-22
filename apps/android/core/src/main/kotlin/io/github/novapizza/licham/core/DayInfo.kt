package io.github.novapizza.licham.core

/** Rating of a day from its trực. */
enum class Rating { GOOD, BAD, NEUTRAL }

/** One giờ hoàng đạo: chi index, chi name and hours ("23–1h"). */
data class Hour(val chiIndex: Int, val chi: String, val startHour: Int, val endHour: Int) {
    val label: String get() = "$startHour–${endHour}h"
}

/** Everything the app shows about one day; same rules as the web app's getLunarInfo. */
data class DayInfo(
    val solar: SolarDate,
    val lunar: LunarDate,
    val canChiDay: String,
    val canChiMonth: String,
    val canChiYear: String,
    val truc: String,
    val rating: Rating,
    val gioHoangDao: List<Hour>,
    /** Activity keys of shared/data/calendar.json (wedding, moving, opening) that suit the day. */
    val activities: Set<String>,
    val holiday: String?,
    /** The first commemorative day or festival: printed on the sheet when there is no holiday, not red. */
    val observance: String? = null,
) {
    val dayChiIndex: Int get() = CalendarData.calendar.chi.indexOf(canChiDay.substringAfter(' '))

    companion object {
        fun of(date: SolarDate): DayInfo = DayFacts.info(date)
    }
}

object DayFacts {
    private val cal get() = CalendarData.calendar

    /** Can chi of a day from its Julian day number. */
    fun canChiDay(date: SolarDate): String {
        val jd = LunarCalendar.jdFromDate(date.day, date.month, date.year)
        return "${cal.can[(jd + 9) % 10]} ${cal.chi[(jd + 1) % 12]}"
    }

    /** Can chi of a lunar month (a leap month shares its month's can chi). */
    fun canChiMonth(lunar: LunarDate): String =
        "${cal.can[(lunar.year * 12 + lunar.month + 3) % 10]} ${cal.chi[(lunar.month + 1) % 12]}"

    /** Can chi of a lunar year. */
    fun canChiYear(lunarYear: Int): String =
        "${cal.can[Math.floorMod(lunarYear + 6, 10)]} ${cal.chi[Math.floorMod(lunarYear + 8, 12)]}"

    /** Chi index of the traditional hour at a clock hour (Tý = 23h–1h). */
    fun chiOfHour(hour: Int): Int = ((hour + 1) / 2) % 12

    fun info(date: SolarDate): DayInfo {
        val lunar = LunarCalendar.solarToLunar(date)
        val dayCanChi = canChiDay(date)
        val dayChi = cal.chi.indexOf(dayCanChi.substringAfter(' '))
        val truc = cal.truc.names[(dayChi - cal.truc.monthChiStart[lunar.month - 1] + 12) % 12]
        val rating = when (truc) {
            in cal.truc.good -> Rating.GOOD
            in cal.truc.bad -> Rating.BAD
            else -> Rating.NEUTRAL
        }
        val hours = cal.gioHoangDao.byDayChi[dayChi].map { i ->
            Hour(i, cal.chi[i], (i * 2 - 1 + 24) % 24, (i * 2 + 1) % 24)
        }
        val activities = if (rating == Rating.GOOD) {
            cal.activities.filterValues { truc in it.truc }.keys
        } else {
            emptySet()
        }
        val h = CalendarData.holidays
        val holiday = h.lunar["${lunar.day}-${lunar.month}"] ?: h.solarHoliday(date)
        return DayInfo(
            solar = date,
            lunar = lunar,
            canChiDay = dayCanChi,
            canChiMonth = canChiMonth(lunar),
            canChiYear = canChiYear(lunar.year),
            truc = truc,
            rating = rating,
            gioHoangDao = hours,
            activities = activities,
            holiday = holiday,
            observance = h.observancesOf(date, lunar).firstOrNull()?.first,
        )
    }
}
