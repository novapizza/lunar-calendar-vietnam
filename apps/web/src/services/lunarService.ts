import { LunarCalendar, CHI, getSolarDate } from '@dqcai/vn-lunar';
import calendar from '@lunar/shared/data/calendar.json';
import holidays from '@lunar/shared/data/holidays.json';
import { LunarInfo, AuspiciousDate, UpcomingEvent } from '../types';
import { YEAR_RANGE } from '../constants/calendar';

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

/** First year a solar holiday counts (newer days off: 24/11 from 2026, 27/7 from 2027). */
const SOLAR_SINCE: Readonly<Record<string, number>> = holidays.solarSince;

/** Commemorative and lesser traditional days (not red), and temple festivals (main day). */
const OBSERVANCES_SOLAR: Readonly<Record<string, string>> = holidays.observances.solar;
const OBSERVANCES_LUNAR: Readonly<Record<string, string>> = holidays.observances.lunar;
const FESTIVALS_LUNAR: Readonly<Record<string, string>> = holidays.festivals.lunar;

/** The solar holiday on day-month of `year`, honouring SOLAR_SINCE. */
function solarHoliday(day: number, month: number, year: number): string | undefined {
  const key = `${day}-${month}`;
  return year >= (SOLAR_SINCE[key] ?? 0) ? SOLAR_HOLIDAYS[key] : undefined;
}

/**
 * The commemorative days and festivals of a day, in that order (observances first). A leap
 * month (lunarMonth < 0) has none of the lunar ones; a solar observance gives way to the same
 * day's holiday (27/7 from 2027).
 */
export function getObservances(lunarDay: number, lunarMonth: number, date: Date): { title: string; kind: 'observance' | 'festival'; source: 'lunar' | 'solar' }[] {
  const out: { title: string; kind: 'observance' | 'festival'; source: 'lunar' | 'solar' }[] = [];
  const d = date.getDate();
  const m = date.getMonth() + 1;
  const solarKey = `${d}-${m}`;
  if (OBSERVANCES_SOLAR[solarKey] && !solarHoliday(d, m, date.getFullYear())) out.push({ title: OBSERVANCES_SOLAR[solarKey], kind: 'observance', source: 'solar' });
  if (lunarMonth > 0) {
    const lunarKey = `${lunarDay}-${lunarMonth}`;
    if (OBSERVANCES_LUNAR[lunarKey]) out.push({ title: OBSERVANCES_LUNAR[lunarKey], kind: 'observance', source: 'lunar' });
    if (FESTIVALS_LUNAR[lunarKey]) out.push({ title: FESTIVALS_LUNAR[lunarKey], kind: 'festival', source: 'lunar' });
  }
  return out;
}

/** The first commemorative day or festival of a day (shown on the sheet when it has no holiday). */
export function getObservance(lunarDay: number, lunarMonth: number, date: Date): string | undefined {
  return getObservances(lunarDay, lunarMonth, date)[0]?.title;
}

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

/** Returns the holiday name for a given lunar/solar date pair (solar `year` for SOLAR_SINCE), or undefined. */
export function getHoliday(
  lunarDay: number,
  lunarMonth: number,
  solarDay: number,
  solarMonth: number,
  year: number,
): string | undefined {
  const lunarKey = `${lunarDay}-${Math.abs(lunarMonth)}`;
  return VIETNAMESE_HOLIDAYS[lunarKey] ?? solarHoliday(solarDay, solarMonth, year);
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
 * The events on one solar day: a solar holiday, then a named lunar holiday or, failing that,
 * mồng 1 / rằm (not of tháng Giêng, which has its own holidays).
 */
function eventsOn(date: Date, daysUntil: number): UpcomingEvent[] {
  const out: UpcomingEvent[] = [];
  const d = date.getDate();
  const m = date.getMonth() + 1;
  const solar = solarHoliday(d, m, date.getFullYear());
  if (solar) out.push({ solarDate: new Date(date), title: solar, source: 'solar', daysUntil });

  const lunar = LunarCalendar.fromSolar(d, m, date.getFullYear());
  const ld = lunar.lunarDate.day;
  const lm = Math.abs(lunar.lunarDate.month);
  const named = VIETNAMESE_HOLIDAYS[`${ld}-${lm}`];
  const observances = getObservances(ld, lunar.lunarDate.month, date);
  // A named lunar day (Tết Hạ nguyên on 15/10) stands for that month's mồng 1 / rằm.
  const lunarNamed = observances.some(o => o.source === 'lunar' && o.kind === 'observance');
  const title = named ?? (lm === 1 || lunarNamed ? undefined : ld === 1 ? `Mồng 1 tháng ${lm}` : ld === 15 ? `Ngày rằm tháng ${lm}` : undefined);
  if (title) out.push({ solarDate: new Date(date), title, lunarDay: ld, lunarMonth: lm, source: 'lunar', daysUntil });
  for (const o of observances) {
    out.push({ solarDate: new Date(date), title: o.title, kind: o.kind, source: o.source, daysUntil, ...(o.source === 'lunar' ? { lunarDay: ld, lunarMonth: lm } : {}) });
  }
  return out;
}

/** The last day the lunar tables cover (YEAR_RANGE: 1900-2099). */
export const LAST_EVENT_DAY = new Date(YEAR_RANGE.start + YEAR_RANGE.count - 1, 11, 31);

/**
 * Scans forward from today and returns up to `maxCount` upcoming lunar/solar
 * holiday events within `daysToScan` days (inclusive of today).
 */
export function getUpcomingEvents(maxCount = 10, daysToScan = 180): UpcomingEvent[] {
  const results: UpcomingEvent[] = [];
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const cursor = new Date(today);
  for (let i = 0; i < daysToScan && results.length < maxCount && cursor <= LAST_EVENT_DAY; i++) {
    results.push(...eventsOn(cursor, i));
    cursor.setDate(cursor.getDate() + 1);
  }
  return results.slice(0, maxCount);
}

/**
 * The events of the whole solar month `offset` months after today's (0 = this month, its past
 * days included), with daysUntil counted from `today` (local midnight; negative for past days).
 * Empty past LAST_EVENT_DAY.
 */
export function getEventsOfMonth(today: Date, offset: number): UpcomingEvent[] {
  const first = new Date(today.getFullYear(), today.getMonth() + offset, 1);
  const results: UpcomingEvent[] = [];
  const cursor = new Date(first);
  while (cursor.getMonth() === first.getMonth() && cursor <= LAST_EVENT_DAY) {
    const daysUntil = Math.round((cursor.getTime() - today.getTime()) / 86_400_000);
    results.push(...eventsOn(cursor, daysUntil));
    cursor.setDate(cursor.getDate() + 1);
  }
  return results;
}
