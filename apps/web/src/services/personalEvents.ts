import { LunarCalendar, getLunarMonths, lunarToSolar } from './lunarService';
import { EventCalendar, EventColor, EventRepeat, LunarEvent, UpcomingEvent } from '../types';

// Personal events: the rules of shared/design/events.md. Days are ISO strings (YYYY-MM-DD, solar,
// local); string order is date order. shared/test-vectors/events.json holds dates both apps must
// reproduce.

// ---------------------------------------------------------------------------
// Days
// ---------------------------------------------------------------------------

export const FIRST_DAY = '1900-01-31';
export const LAST_DAY = '2099-12-31';

const pad = (n: number) => String(n).padStart(2, '0');
export const iso = (y: number, m: number, d: number) => `${y}-${pad(m)}-${pad(d)}`;
export const isoOf = (date: Date) => iso(date.getFullYear(), date.getMonth() + 1, date.getDate());
export function parts(day: string): [number, number, number] {
  return [+day.slice(0, 4), +day.slice(5, 7), +day.slice(8, 10)];
}
/** Local midnight of `day`. */
export function dateOf(day: string): Date {
  const [y, m, d] = parts(day);
  return new Date(y, m - 1, d);
}
export function addDays(day: string, n: number): string {
  const [y, m, d] = parts(day);
  const t = new Date(Date.UTC(y, m - 1, d + n));
  return iso(t.getUTCFullYear(), t.getUTCMonth() + 1, t.getUTCDate());
}
export function daysBetween(a: string, b: string): number {
  const [ya, ma, da] = parts(a);
  const [yb, mb, db] = parts(b);
  return Math.round((Date.UTC(yb, mb - 1, db) - Date.UTC(ya, ma - 1, da)) / 86_400_000);
}
/** 0 = Monday … 6 = Sunday. */
export function weekdayOf(day: string): number {
  const [y, m, d] = parts(day);
  return (new Date(Date.UTC(y, m - 1, d)).getUTCDay() + 6) % 7;
}
const daysInMonth = (y: number, m: number) => new Date(Date.UTC(y, m, 0)).getUTCDate();
const maxDay = (a: string, b: string) => (a > b ? a : b);
const minDay = (a: string, b: string) => (a < b ? a : b);

export interface LunarYMD { day: number; month: number; year: number; leap: boolean }
export function lunarOf(day: string): LunarYMD {
  const [y, m, d] = parts(day);
  const l = LunarCalendar.fromSolar(d, m, y).lunarDate;
  return { day: l.day, month: Math.abs(l.month), year: l.year, leap: Boolean(l.leap) };
}

// ---------------------------------------------------------------------------
// Fields with their defaults
// ---------------------------------------------------------------------------

export const calendarOf = (e: LunarEvent): EventCalendar => e.calendar ?? 'lunar';
export const repeatOf = (e: LunarEvent): EventRepeat => e.repeat ?? 'yearly';
export const isAllDay = (e: LunarEvent) => e.allDay ?? true;
export const startTimeOf = (e: LunarEvent) => e.startTime ?? '09:00';
export const endTimeOf = (e: LunarEvent) => e.endTime ?? '10:00';
export const spanOf = (e: LunarEvent) => Math.max(1, e.days ?? 1);
export const colorOf = (e: LunarEvent): EventColor => e.color ?? 'gold';
export function weekdaysOf(e: LunarEvent): number[] {
  if (e.weekdays?.length) return e.weekdays;
  return e.start ? [weekdayOf(e.start)] : [];
}

// ---------------------------------------------------------------------------
// Occurrences
// ---------------------------------------------------------------------------

/** The rule's candidate start days in [lo, hi], in order (start, until, count, exceptions not applied). */
function candidates(e: LunarEvent, lo: string, hi: string): string[] {
  lo = maxDay(lo, FIRST_DAY);
  hi = minDay(hi, LAST_DAY);
  if (lo > hi) return [];
  const out: string[] = [];
  const repeat = repeatOf(e);
  const [y0] = parts(lo);
  const [y1] = parts(hi);
  const push = (day: string) => { if (day >= lo && day <= hi) out.push(day); };

  if (repeat === 'none') {
    if (e.start) push(e.start);
    return out;
  }
  if (repeat === 'daily' || repeat === 'weekly') {
    const wd = new Set(weekdaysOf(e));
    for (let d = lo; d <= hi; d = addDays(d, 1)) if (repeat === 'daily' || wd.has(weekdayOf(d))) out.push(d);
    return out;
  }
  if (calendarOf(e) === 'solar') {
    for (let y = y0; y <= y1; y++) {
      for (const m of repeat === 'monthly' ? [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12] : [e.lunarMonth]) {
        push(iso(y, m, Math.min(e.lunarDay, daysInMonth(y, m))));
      }
    }
    return out;
  }
  // Lunar: lunar year y0 - 1 may still have months in solar year y0.
  for (let ly = y0 - 1; ly <= y1; ly++) {
    if (repeat === 'monthly') {
      for (const m of getLunarMonths(ly)) {
        const f = m.first;
        push(addDays(iso(f.year, f.month, f.day), Math.min(e.lunarDay, m.days) - 1));
      }
    } else {
      const r = lunarToSolar(e.lunarDay, e.lunarMonth, ly, e.isLeapMonth);
      if (r.ok) push(iso(r.solar.year, r.solar.month, r.solar.day));
    }
  }
  return out;
}

/**
 * Start days of the occurrences of `e` that start in [from, to], in order: the candidates
 * bounded by start and until, the first `count` from start, without exceptions.
 */
export function occurrenceStarts(e: LunarEvent, from: string, to: string): string[] {
  let lo = from;
  let hi = to;
  if (e.start) lo = maxDay(lo, e.start);
  if (e.until) hi = minDay(hi, e.until);
  if (lo > hi) return [];
  let list: string[];
  if (e.start && e.count) {
    const all = candidates(e, e.start, e.until ? minDay(e.until, LAST_DAY) : LAST_DAY);
    list = all.slice(0, e.count).filter(d => d >= lo && d <= hi);
  } else {
    list = candidates(e, lo, hi);
  }
  const skip = new Set(e.exceptions ?? []);
  return list.filter(d => !skip.has(d));
}

/** Start days of the occurrences covering some day of [from, to] (an occurrence covers `days` days). */
export function occurrencesCovering(e: LunarEvent, from: string, to: string): string[] {
  return occurrenceStarts(e, addDays(from, -(spanOf(e) - 1)), to);
}

export interface DayEvent {
  event: LunarEvent;
  /** The occurrence's start day. */
  start: string;
}

/** The events covering `day`: all-day ones first (list order), then timed ones by start time. */
export function eventsOnDay(events: LunarEvent[], day: string): DayEvent[] {
  const out: DayEvent[] = [];
  for (const e of events) for (const s of occurrencesCovering(e, day, day)) out.push({ event: e, start: s });
  return out.sort((a, b) => {
    const ka = isAllDay(a.event) ? '' : startTimeOf(a.event);
    const kb = isAllDay(b.event) ? '' : startTimeOf(b.event);
    return ka < kb ? -1 : ka > kb ? 1 : 0;
  });
}

/** Old name kept for callers that pass a Date: the events covering that day. */
export function personalEventsOn(events: LunarEvent[], date: Date): LunarEvent[] {
  return eventsOnDay(events, isoOf(date)).map(x => x.event);
}

/** The next occurrence starting on or after `day`, or null (scans up to 2099). */
export function nextOccurrence(e: LunarEvent, day: string): string | null {
  for (let y = parts(day)[0]; y <= 2099; y += 5) {
    const list = occurrenceStarts(e, maxDay(day, iso(y, 1, 1)), iso(y + 4, 12, 31));
    if (list.length) return list[0];
  }
  return null;
}

/** The events starting on `date` as Sự kiện list entries (getEventsOfMonth's `personal`). */
export function personalUpcoming(events: LunarEvent[], date: Date, daysUntil: number): UpcomingEvent[] {
  const day = isoOf(date);
  return eventsOnDay(events, day).filter(x => x.start === day).map(({ event: e }) => {
    const base = { solarDate: new Date(date), title: e.title, kind: 'personal' as const, daysUntil, eventId: e.id, color: colorOf(e), time: isAllDay(e) ? undefined : timeLabel(e) };
    if (calendarOf(e) === 'solar' || repeatOf(e) === 'none' || repeatOf(e) === 'daily' || repeatOf(e) === 'weekly') return { ...base, source: 'solar' as const };
    const l = lunarOf(day);
    return { ...base, source: 'lunar' as const, lunarDay: l.day, lunarMonth: l.month };
  });
}

// ---------------------------------------------------------------------------
// Old day notes → events (shared/design/events.md, "Old notes")
// ---------------------------------------------------------------------------

/** A day note of the old "Ghi chú" card (localStorage `userNotes`). */
export interface OldNote { id: string; date: string; content: string; createdAt: number }

const TITLE_MAX = 60;

/** One old note as a one-off all-day event on its day: title = first line (≤ 60 chars), notes = the whole text when longer. */
export function noteToEvent(n: OldNote): LunarEvent {
  const text = n.content.trim();
  const first = text.split('\n')[0].trim();
  const title = first.length > TITLE_MAX ? `${first.slice(0, TITLE_MAX - 1).trimEnd()}…` : first || 'Ghi chú';
  const [, m, d] = parts(n.date);
  return {
    id: n.id, title, lunarDay: d, lunarMonth: m, isLeapMonth: false, calendar: 'solar', repeat: 'none',
    start: n.date, allDay: true, color: 'gold', createdAt: n.createdAt,
    ...(title !== text ? { notes: text } : {}),
  };
}

// ---------------------------------------------------------------------------
// Words
// ---------------------------------------------------------------------------

export const WEEKDAY_SHORT = ['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'];
const fmt = (day: string) => { const [y, m, d] = parts(day); return `${d}/${m}/${y}`; };

/** "Cả ngày", "09:00 – 10:00". */
export function timeLabel(e: LunarEvent): string {
  return isAllDay(e) ? 'Cả ngày' : `${startTimeOf(e)} – ${endTimeOf(e)}`;
}

/** The repeat in words: "Hằng năm vào 10/3 âm · đến 31/12/2030", "Hằng tuần vào T2, T4 · 10 lần", "Không lặp · 25/11/2026". */
export function describeEvent(e: LunarEvent): string {
  const repeat = repeatOf(e);
  const lunar = calendarOf(e) === 'lunar';
  let rule: string;
  switch (repeat) {
    case 'none': return e.start ? `Không lặp · ${fmt(e.start)}` : 'Không lặp';
    case 'daily': rule = 'Hằng ngày'; break;
    case 'weekly': rule = `Hằng tuần vào ${weekdaysOf(e).slice().sort().map(w => WEEKDAY_SHORT[w]).join(', ')}`; break;
    case 'monthly': rule = `Hằng tháng vào ngày ${e.lunarDay}${lunar ? ' âm' : ''}`; break;
    default: rule = `Hằng năm vào ${e.lunarDay}/${e.lunarMonth}${lunar ? (e.isLeapMonth ? ' nhuận âm' : ' âm') : ''}`;
  }
  if (e.until) rule += ` · đến ${fmt(e.until)}`;
  else if (e.count && e.start) rule += ` · ${e.count} lần`;
  return rule;
}

/** Reminder option labels; all-day ones are counted back from 08:00 of the day. */
export const REMINDERS_TIMED: [number, string][] = [
  [0, 'Đúng giờ'], [5, '5 phút trước'], [10, '10 phút trước'], [15, '15 phút trước'], [30, '30 phút trước'],
  [60, '1 giờ trước'], [120, '2 giờ trước'], [1440, '1 ngày trước'], [2880, '2 ngày trước'], [10080, '1 tuần trước'],
];
export const REMINDERS_ALL_DAY: [number, string][] = [
  [0, 'Vào ngày, lúc 8:00'], [1440, '1 ngày trước, lúc 8:00'], [2880, '2 ngày trước, lúc 8:00'], [10080, '1 tuần trước, lúc 8:00'],
];
export function reminderLabel(e: LunarEvent, minutes: number): string {
  const list = isAllDay(e) ? REMINDERS_ALL_DAY : REMINDERS_TIMED;
  return list.find(([m]) => m === minutes)?.[1] ?? `${minutes} phút trước`;
}

// ---------------------------------------------------------------------------
// Reminders
// ---------------------------------------------------------------------------

export interface ReminderFire {
  event: LunarEvent;
  /** The occurrence's start day. */
  start: string;
  minutes: number;
  /** When it fires, ms since epoch (local time). */
  at: number;
}

/** The moment `minutes` before the reminder base of the occurrence starting on `start`. */
export function fireTime(e: LunarEvent, start: string, minutes: number): number {
  const [y, m, d] = parts(start);
  const [hh, mm] = (isAllDay(e) ? '08:00' : startTimeOf(e)).split(':').map(Number);
  return new Date(y, m - 1, d, hh, mm - minutes).getTime();
}

/** Reminders of `events` firing in [fromMs, toMs), in time order. */
export function remindersBetween(events: LunarEvent[], fromMs: number, toMs: number): ReminderFire[] {
  const out: ReminderFire[] = [];
  for (const e of events) {
    const rs = e.reminders ?? [];
    if (!rs.length) continue;
    const longest = Math.max(...rs);
    // Occurrences whose reminders can fall in the window: starts up to `longest` minutes after it.
    const from = isoOf(new Date(fromMs - 86_400_000));
    const to = isoOf(new Date(toMs + longest * 60_000 + 86_400_000));
    for (const s of occurrenceStarts(e, from, to)) {
      for (const minutes of rs) {
        const at = fireTime(e, s, minutes);
        if (at >= fromMs && at < toMs) out.push({ event: e, start: s, minutes, at });
      }
    }
  }
  return out.sort((a, b) => a.at - b.at);
}

// ---------------------------------------------------------------------------
// Export: occurrences per year range (expanded VEVENTs)
// ---------------------------------------------------------------------------

/** Whether the export writes `e` as one VEVENT with an RRULE (solar rules RFC 5545 can say exactly). */
export function exportsAsRule(e: LunarEvent): boolean {
  const repeat = repeatOf(e);
  if (repeat === 'none' || repeat === 'daily' || repeat === 'weekly') return Boolean(e.start);
  if (calendarOf(e) !== 'solar') return false;
  if (repeat === 'monthly') return e.lunarDay <= 28;
  return !(e.lunarMonth === 2 && e.lunarDay === 29);
}

export interface SkippedOccurrence {
  title: string;
  lunarYear: number;
  reason: 'leap_month_not_found' | 'conversion_error';
}

/** Lunar yearly leap events: the lunar years in the range whose year lacks that leap month. */
export function skippedYears(e: LunarEvent, startYear: number, yearCount: number): SkippedOccurrence[] {
  if (calendarOf(e) !== 'lunar' || repeatOf(e) !== 'yearly' || !e.isLeapMonth) return [];
  const out: SkippedOccurrence[] = [];
  for (let ly = startYear; ly < startYear + yearCount; ly++) {
    const r = lunarToSolar(e.lunarDay, e.lunarMonth, ly, true);
    if (r.ok === false) out.push({ title: e.title, lunarYear: ly, reason: r.reason });
  }
  return out;
}
