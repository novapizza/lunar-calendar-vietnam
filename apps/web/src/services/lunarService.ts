import { LunarCalendar, CHI, getSolarDate } from '@dqcai/vn-lunar';
import calendar from '@lunar/shared/data/calendar.json';
import holidays from '@lunar/shared/data/holidays.json';
import { LunarInfo, AuspiciousDate, UpcomingEvent } from '../types';

export { LunarCalendar, CHI };

// ---------------------------------------------------------------------------
// Verified lunar → solar conversion
// ---------------------------------------------------------------------------

export interface SolarYMD { year: number; month: number; day: number }

export type LunarToSolarResult =
  | { ok: true; solar: SolarYMD; adjustedDay: boolean }
  | { ok: false; reason: 'leap_month_not_found' | 'conversion_error' };

/** Converts once and verifies the result by converting back to lunar. */
function convertExact(day: number, month: number, lunarYear: number, isLeap: boolean): SolarYMD | null {
  try {
    const s = getSolarDate(day, month, lunarYear, isLeap);
    if (!s || !s.year || !s.month || !s.day) return null;
    const back = LunarCalendar.fromSolar(s.day, s.month, s.year).lunarDate;
    const matches =
      back.day === day &&
      Math.abs(back.month) === month &&
      Boolean(back.leap) === isLeap &&
      back.year === lunarYear;
    return matches ? { year: s.year, month: s.month, day: s.day } : null;
  } catch {
    return null;
  }
}

/**
 * Converts a lunar date to solar and checks the answer.
 *
 * `getSolarDate` never fails on bad input: day 30 of a 29-day month rolls over
 * into the next month, and a leap month that does not exist that year still
 * returns a date. This helper catches both by converting the result back.
 *
 * Day 30 in a 29-day month (tháng thiếu) falls back to day 29, the usual
 * practice for anniversaries.
 */
export function lunarToSolar(
  day: number,
  month: number,
  lunarYear: number,
  isLeap: boolean,
): LunarToSolarResult {
  const exact = convertExact(day, month, lunarYear, isLeap);
  if (exact) return { ok: true, solar: exact, adjustedDay: false };

  if (day === 30) {
    const shorter = convertExact(29, month, lunarYear, isLeap);
    if (shorter) return { ok: true, solar: shorter, adjustedDay: true };
  }

  if (isLeap && !convertExact(1, month, lunarYear, true)) {
    return { ok: false, reason: 'leap_month_not_found' };
  }
  return { ok: false, reason: 'conversion_error' };
}

// ---------------------------------------------------------------------------
// Module-level constants (hoisted out of hot paths for clarity & performance)
// ---------------------------------------------------------------------------

/** Six auspicious hour indexes (Giờ Hoàng Đạo) for each day Chi index. */
const GIO_HD_MAP: readonly (readonly number[])[] = calendar.gioHoangDao.byDayChi;

/** The 12 Directors (Thập Nhị Trực), in cycle order starting from Kiến. */
const TRUC_NAMES: readonly string[] = calendar.truc.names;

/** Chi index at which each lunar month starts (index 0 = month 1, which starts at Dần). */
const MONTH_CHI_START: readonly number[] = calendar.truc.monthChiStart;

const GOOD_TRUC = new Set(calendar.truc.good);
const BAD_TRUC  = new Set(calendar.truc.bad);

// ---------------------------------------------------------------------------
// Holiday data (shared/data/holidays.json)
// ---------------------------------------------------------------------------

/** Traditional lunar holidays keyed as "day-month". */
export const VIETNAMESE_HOLIDAYS: Readonly<Record<string, string>> = holidays.lunar;

/** Public solar holidays keyed as "day-month". */
export const SOLAR_HOLIDAYS: Readonly<Record<string, string>> = holidays.solar;

// ---------------------------------------------------------------------------
// Activity rules (used by the auspicious-date picker)
// ---------------------------------------------------------------------------

export const ACTIVITY_RULES: Readonly<Record<string, {
  label: string;
  requiredStars: string[];
  forbiddenStars: string[];
  preferredTruc: string[];
}>> = {
  WEDDING:       { label: 'Cưới Hỏi',   requiredStars: [], forbiddenStars: [], preferredTruc: [] },
  GROUNDBREAKING:{ label: 'Khởi Công',  requiredStars: [], forbiddenStars: [], preferredTruc: [] },
  OPENING:       { label: 'Khai Trương', requiredStars: [], forbiddenStars: [], preferredTruc: [] },
};

// ---------------------------------------------------------------------------
// Core service functions
// ---------------------------------------------------------------------------

/** Can chi of a lunar month; a leap month shares its month's can chi. */
export function monthCanChi(lunarMonth: number, lunarYear: number): string {
  const m = Math.abs(lunarMonth);
  return `${calendar.can[(lunarYear * 12 + m + 3) % 10]} ${calendar.chi[(m + 1) % 12]}`;
}

/** Converts a solar Date to a full LunarInfo object. */
export function getLunarInfo(date: Date): LunarInfo {
  const d = date.getDate();
  const m = date.getMonth() + 1;
  const y = date.getFullYear();

  const lunar = LunarCalendar.fromSolar(d, m, y);
  const dayChi = lunar.dayCanChi.split(' ')[1];
  const chiIndex = CHI.indexOf(dayChi);

  // --- Giờ Hoàng Đạo ---
  const hoursIndices = GIO_HD_MAP[chiIndex] ?? [];
  const gioHoangDao = hoursIndices.map(i => {
    const start = (i * 2 - 1 + 24) % 24;
    const end   = (i * 2 + 1) % 24;
    return `${CHI[i]} (${start}h-${end}h)`;
  });

  // --- Thập Nhị Trực ---
  const monthChiIdx = MONTH_CHI_START[Math.abs(lunar.lunarDate.month) - 1] ?? 0;
  const dayChiIdx   = CHI.indexOf(dayChi);
  const truc        = TRUC_NAMES[(dayChiIdx - monthChiIdx + 12) % 12];

  // --- Day rating ---
  const rating: LunarInfo['rating'] = GOOD_TRUC.has(truc)
    ? 'good'
    : BAD_TRUC.has(truc)
    ? 'bad'
    : 'neutral';

  // --- Activity suitability ---
  const activities: LunarInfo['activities'] = {
    wedding: rating === 'good' && calendar.activities.wedding.truc.includes(truc),
    moving:  rating === 'good' && calendar.activities.moving.truc.includes(truc),
    opening: rating === 'good' && calendar.activities.opening.truc.includes(truc),
  };

  return {
    solarDate:    date,
    lunarDay:     lunar.lunarDate.day,
    lunarMonth:   lunar.lunarDate.month,
    lunarYear:    lunar.lunarDate.year,
    isLeap:       lunar.lunarDate.leap,
    // @dqcai/vn-lunar has a typo: "Bình" instead of "Bính" (丙) in its CAN array
    canChiDay:    lunar.dayCanChi.replace(/^Bình /, 'Bính '),
    // Ho Ngoc Duc's rule (shared/data/calendar.json canChiRules). @dqcai/vn-lunar's monthCanChi
    // is one stem off (e.g. month 10 of 2026 is Kỷ Hợi, it says Mậu Hợi).
    canChiMonth:  monthCanChi(lunar.lunarDate.month, lunar.lunarDate.year),
    canChiYear:   lunar.yearCanChi.replace(/^Bình /, 'Bính '),
    tietKhi:      'Đang cập nhật',
    gioHoangDao,
    stars:        [],
    truc,
    rating,
    activities,
  };
}

/** Returns the holiday name for a given lunar/solar date pair, or undefined. */
export function getHoliday(
  lunarDay: number,
  lunarMonth: number,
  solarDay: number,
  solarMonth: number,
): string | undefined {
  const lunarKey = `${lunarDay}-${Math.abs(lunarMonth)}`;
  const solarKey = `${solarDay}-${solarMonth}`;
  return VIETNAMESE_HOLIDAYS[lunarKey] ?? SOLAR_HOLIDAYS[solarKey];
}

/** Activity key → LunarInfo activities field mapping. */
const ACTIVITY_FIELD_MAP: Readonly<Record<string, keyof LunarInfo['activities']>> = {
  WEDDING:       'wedding',
  GROUNDBREAKING:'moving',
  OPENING:       'opening',
};

/**
 * Scans forward from today and returns up to 10 auspicious dates for the
 * given activity within `monthsToScan * 31` days.
 *
 * Dates where the Truc matches the activity get score 10; generically good
 * days get score 7.
 */
export function findBestDates(activityKey: string, monthsToScan = 3): AuspiciousDate[] {
  const activityField = ACTIVITY_FIELD_MAP[activityKey];
  const results: AuspiciousDate[] = [];
  const cursor = new Date();

  for (let i = 0; i < monthsToScan * 31; i++) {
    const info = getLunarInfo(cursor);

    const include = activityField
      ? info.activities[activityField]          // activity selected: only exact matches (score 10)
      : info.rating === 'good';                 // no activity: all good-rated days

    if (include) {
      results.push({
        date:      new Date(cursor),
        lunarDate: `${info.lunarDay}/${info.lunarMonth}`,
        score:     activityField ? 10 : 7,
        stars:     [],
        truc:      info.truc,
        rating:    info.rating,
      });
    }

    cursor.setDate(cursor.getDate() + 1);
    if (results.length >= 10) break;
  }

  return results;
}

/**
 * Scans forward from today and returns up to `maxCount` upcoming lunar/solar
 * holiday events within `daysToScan` days (inclusive of today).
 */
export function getUpcomingEvents(maxCount = 10, daysToScan = 180): UpcomingEvent[] {
  const results: UpcomingEvent[] = [];
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const cursor = new Date(today);

  for (let i = 0; i < daysToScan && results.length < maxCount; i++) {
    const d = cursor.getDate();
    const m = cursor.getMonth() + 1;
    const daysUntil = i;

    const solarKey = `${d}-${m}`;
    if (SOLAR_HOLIDAYS[solarKey]) {
      results.push({
        solarDate: new Date(cursor),
        title: SOLAR_HOLIDAYS[solarKey],
        source: 'solar',
        daysUntil,
      });
      if (results.length >= maxCount) break;
    }

    const lunar = LunarCalendar.fromSolar(d, m, cursor.getFullYear());
    const ld = lunar.lunarDate.day;
    const lm = Math.abs(lunar.lunarDate.month);
    const lunarKey = `${ld}-${lm}`;
    if (VIETNAMESE_HOLIDAYS[lunarKey]) {
      results.push({
        solarDate: new Date(cursor),
        title: VIETNAMESE_HOLIDAYS[lunarKey],
        lunarDay: ld,
        lunarMonth: lm,
        source: 'lunar',
        daysUntil,
      });
    } else if (ld === 1 && lm !== 1 && results.length < maxCount) {
      // Mồng 1 (new moon) — skip if already a named holiday or lunar 1/1
      results.push({
        solarDate: new Date(cursor),
        title: `Mồng 1 tháng ${lm}`,
        lunarDay: ld,
        lunarMonth: lm,
        source: 'lunar',
        daysUntil,
      });
    } else if (ld === 15 && lm !== 1 && results.length < maxCount) {
      // Ngày rằm (full moon) — skip if already a named holiday or lunar 15/1
      results.push({
        solarDate: new Date(cursor),
        title: `Ngày rằm tháng ${lm}`,
        lunarDay: ld,
        lunarMonth: lm,
        source: 'lunar',
        daysUntil,
      });
    }

    cursor.setDate(cursor.getDate() + 1);
  }

  return results.slice(0, maxCount);
}
