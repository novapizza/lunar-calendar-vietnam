package io.github.novapizza.licham.core

/** The five elements; [key] matches shared/data/menh.json and the palettes in tokens.json. */
enum class Element(val key: String) {
    KIM("kim"), THUY("thuy"), MOC("moc"), HOA("hoa"), THO("tho");

    val info: ElementJson get() = CalendarData.menh.elements.getValue(key)
    /** The element that generates this one (tương sinh). */
    val generatedBy: Element get() = of(info.generatedBy)

    companion object {
        fun of(key: String): Element = entries.first { it.key == key }
    }
}

/** Mệnh of a lunar year, e.g. 1990 → Canh Ngọ, Lộ Bàng Thổ, Thổ. */
data class Menh(val lunarYear: Int, val canChi: String, val chi: String, val napAm: String, val element: Element)

/** Which colour the app uses: own element, generating element, or the default red. */
enum class AccentPref { BAN_MENH, TUONG_SINH, MAC_DINH }

object MenhCalc {
    private val data get() = CalendarData.menh
    private val cal get() = CalendarData.calendar

    fun ofLunarYear(lunarYear: Int): Menh {
        val i = Math.floorMod(lunarYear - data.cycleStartYear, 60)
        val chi = cal.chi[i % 12]
        val nap = data.napAm[i shr 1]
        return Menh(lunarYear, "${cal.can[i % 10]} $chi", chi, nap.name, Element.of(nap.element))
    }

    /** Mệnh from a solar birth date; someone born before Tết belongs to the previous lunar year. */
    fun ofBirthDate(date: SolarDate): Menh? {
        val min = SolarDate.parse(data.birthDateMin) ?: return null
        if (date < min) return null
        return ofLunarYear(LunarCalendar.solarToLunar(date).year)
    }

    /** Palette for a preference; without a mệnh the app stays red (Hỏa palette = default). */
    fun paletteFor(menh: Menh?, pref: AccentPref): Element = when {
        menh == null || pref == AccentPref.MAC_DINH -> Element.HOA
        pref == AccentPref.TUONG_SINH -> menh.element.generatedBy
        else -> menh.element
    }
}
