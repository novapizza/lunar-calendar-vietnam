import {
  LAST_DAY, SkippedOccurrence, addDays, calendarOf, describeEvent, endTimeOf, exportsAsRule, isAllDay, isoOf, lunarOf,
  nextOccurrence, occurrenceStarts, parts, repeatOf, skippedYears, spanOf, startTimeOf, weekdaysOf,
} from './personalEvents';
import { LunarEvent } from '../types';

// ---------------------------------------------------------------------------
// Result types
// ---------------------------------------------------------------------------

export type SkippedEventYear = SkippedOccurrence;

export interface ExportSummary {
  totalEvents: number;
  skippedCount: number;
  skippedReasons: SkippedEventYear[];
}

// ---------------------------------------------------------------------------
// ICS text helpers
// ---------------------------------------------------------------------------

/** Escape special characters in RFC 5545 TEXT values. */
function escapeText(s: string): string {
  return s
    .replace(/\\/g, '\\\\')
    .replace(/;/g, '\\;')
    .replace(/,/g, '\\,')
    .replace(/\n/g, '\\n');
}

/**
 * Fold an ICS property line at 75 UTF-8 bytes per RFC 5545.
 * Continuation lines start with a single space.
 */
function foldLine(line: string): string {
  const encoder = new TextEncoder();
  const parts: string[] = [];
  let remaining = line;
  let maxBytes = 75;

  while (true) {
    const bytes = encoder.encode(remaining);
    if (bytes.length <= maxBytes) {
      parts.push(remaining);
      break;
    }
    // Find the split point at maxBytes bytes without cutting a multi-byte char
    let byteCount = 0;
    let splitAt = 0;
    for (const char of remaining) {
      const charLen = encoder.encode(char).length;
      if (byteCount + charLen > maxBytes) break;
      byteCount += charLen;
      splitAt += char.length;
    }
    parts.push(remaining.slice(0, splitAt));
    remaining = remaining.slice(splitAt);
    maxBytes = 74; // continuation lines: space prefix takes 1 byte
  }

  return parts.join('\r\n ');
}

function pad2(n: number): string {
  return String(n).padStart(2, '0');
}



function utcStamp(): string {
  const now = new Date();
  return (
    `${now.getUTCFullYear()}${pad2(now.getUTCMonth() + 1)}${pad2(now.getUTCDate())}` +
    `T${pad2(now.getUTCHours())}${pad2(now.getUTCMinutes())}${pad2(now.getUTCSeconds())}Z`
  );
}

// ---------------------------------------------------------------------------
// ICS formatting (shared/design/events.md, "Export")
// ---------------------------------------------------------------------------

const compact = (day: string) => day.replaceAll('-', '');
const hhmmss = (t: string) => `${t.replace(':', '')}00`;

/** DTSTART / DTEND / EXDATE value of a day: a DATE when all day, a floating local time otherwise. */
function when(e: LunarEvent, day: string, time: string): string {
  return isAllDay(e) ? `;VALUE=DATE:${compact(day)}` : `:${compact(day)}T${hhmmss(time)}`;
}

/** Start, end, alarms, location: the lines every VEVENT of `e` shares, for the occurrence on `start`. */
function bodyLines(e: LunarEvent, start: string): string[] {
  const last = addDays(start, spanOf(e) - 1);
  const lines = [
    `DTSTART${when(e, start, startTimeOf(e))}`,
    isAllDay(e) ? `DTEND;VALUE=DATE:${compact(addDays(last, 1))}` : `DTEND:${compact(last)}T${hhmmss(endTimeOf(e))}`,
  ];
  if (e.location) lines.push(foldLine(`LOCATION:${escapeText(e.location)}`));
  return lines;
}

function alarmLines(e: LunarEvent): string[] {
  const out: string[] = [];
  for (const minutes of e.reminders ?? []) {
    // All day: the reminder base is 08:00, so the trigger counts from midnight (DTSTART).
    const offset = isAllDay(e) ? 480 - minutes : -minutes;
    out.push('BEGIN:VALARM', 'ACTION:DISPLAY', foldLine(`DESCRIPTION:${escapeText(e.title)}`), `TRIGGER:${offset < 0 ? '-' : ''}PT${Math.abs(offset)}M`, 'END:VALARM');
  }
  return out;
}

function vevent(uid: string, dtstamp: string, summary: string, description: string, body: string[], extra: string[], e: LunarEvent): string {
  return [
    'BEGIN:VEVENT',
    foldLine(`UID:${uid}@lunar-vn`),
    `DTSTAMP:${dtstamp}`,
    ...body,
    ...extra,
    foldLine(`SUMMARY:${escapeText(summary)}`),
    foldLine(`DESCRIPTION:${escapeText(description)}`),
    ...alarmLines(e),
    'END:VEVENT',
  ].join('\r\n');
}

const BYDAY = ['MO', 'TU', 'WE', 'TH', 'FR', 'SA', 'SU'];
const notesLine = (e: LunarEvent) => (e.notes ? `\n${e.notes}` : '');

/** One VEVENT with an RRULE (exportsAsRule), or null when the series has no occurrence. */
function ruleEvent(e: LunarEvent, dtstamp: string): string | null {
  // DTSTART is the first candidate from the start (exceptions go to EXDATE, and COUNT counts them).
  const first = e.start
    ? occurrenceStarts({ ...e, exceptions: [] }, e.start, LAST_DAY)[0]
    : nextOccurrence({ ...e, exceptions: [] }, isoOf(new Date()).slice(0, 4) + '-01-01');
  if (!first) return null;
  const [, m, d] = parts(first);
  const repeat = repeatOf(e);
  const extra: string[] = [];
  if (repeat !== 'none') {
    let rule = { daily: 'FREQ=DAILY', weekly: `FREQ=WEEKLY;BYDAY=${weekdaysOf(e).slice().sort().map(w => BYDAY[w]).join(',')}`,
      monthly: `FREQ=MONTHLY;BYMONTHDAY=${d}`, yearly: `FREQ=YEARLY;BYMONTH=${m};BYMONTHDAY=${d}` }[repeat];
    if (e.until) rule += `;UNTIL=${compact(e.until)}${isAllDay(e) ? '' : 'T235959'}`;
    else if (e.count && e.start) rule += `;COUNT=${e.count}`;
    extra.push(`RRULE:${rule}`);
    const ex = (e.exceptions ?? []).filter(x => x >= first);
    if (ex.length) extra.push(foldLine(`EXDATE${isAllDay(e) ? ';VALUE=DATE:' : ':'}${ex.map(x => isAllDay(e) ? compact(x) : `${compact(x)}T${hhmmss(startTimeOf(e))}`).join(',')}`));
  }
  return vevent(e.id, dtstamp, e.title, `${describeEvent(e)}${notesLine(e)}\nTạo bởi Lịch Âm Việt Nam`, bodyLines(e, first), extra, e);
}

/** One VEVENT for the occurrence of `e` on `start` (lunar rules, solar days past 28). */
function occurrenceEvent(e: LunarEvent, start: string, dtstamp: string): string {
  const monthly = repeatOf(e) === 'monthly';
  const [y, m, d] = parts(start);
  if (calendarOf(e) === 'lunar') {
    const l = lunarOf(start);
    const leapTag = l.leap ? ' Nhuận' : '';
    const uid = monthly ? `${e.id}-${l.year}-${l.month}${l.leap ? 'n' : ''}` : `${e.id}-${l.year}`;
    const about = monthly
      ? `Sự kiện âm lịch ngày ${e.lunarDay} hằng tháng, tháng ${l.month}${leapTag} (năm âm ${l.year})`
      : `Sự kiện âm lịch ngày ${e.lunarDay} tháng ${e.lunarMonth}${leapTag} (năm âm ${l.year})`;
    const adjusted = l.day !== e.lunarDay ? `\nTháng ${l.month} năm nay chỉ có 29 ngày nên sự kiện được đặt vào ngày 29.` : '';
    return vevent(uid, dtstamp, `${e.title} (Âm ${e.lunarDay}/${l.month}${leapTag})`, `${about}${adjusted}${notesLine(e)}\nTạo bởi Lịch Âm Việt Nam`, bodyLines(e, start), [], e);
  }
  const about = monthly ? `Sự kiện dương lịch ngày ${e.lunarDay} hằng tháng` : `Sự kiện dương lịch ngày ${e.lunarDay} tháng ${e.lunarMonth} hằng năm`;
  const adjusted = d !== e.lunarDay ? `\nTháng ${m} chỉ có ${d} ngày nên sự kiện được đặt vào ngày ${d}.` : '';
  return vevent(monthly ? `${e.id}-${y}-${m}` : `${e.id}-${y}`, dtstamp, e.title, `${about}${adjusted}${notesLine(e)}\nTạo bởi Lịch Âm Việt Nam`, bodyLines(e, start), [], e);
}

function wrapVCalendar(vevents: string[]): string {
  const header = [
    'BEGIN:VCALENDAR',
    'VERSION:2.0',
    'PRODID:-//Lịch Âm Việt Nam//VI',
    'CALSCALE:GREGORIAN',
    'METHOD:PUBLISH',
    foldLine('X-WR-CALNAME:Sự kiện âm lịch Việt Nam'),
  ].join('\r\n');

  return `${header}\r\n${vevents.join('\r\n')}\r\nEND:VCALENDAR`;
}

// ---------------------------------------------------------------------------
// Public entry points
// ---------------------------------------------------------------------------

export interface IcsBuild extends ExportSummary { text: string }

/**
 * The .ics text for `events`: solar series RFC 5545 can express as one VEVENT with an RRULE
 * (whole series), the rest one VEVENT per occurrence in solar years startYear … +yearCount-1.
 */
export function buildICS(events: LunarEvent[], startYear: number, yearCount: number, dtstamp = utcStamp()): IcsBuild {
  const from = `${startYear}-01-01`;
  const to = `${startYear + yearCount - 1}-12-31`;
  const vevents: string[] = [];
  const skippedReasons: SkippedEventYear[] = [];
  for (const e of events) {
    if (exportsAsRule(e)) {
      const v = ruleEvent(e, dtstamp);
      if (v) vevents.push(v);
      continue;
    }
    skippedReasons.push(...skippedYears(e, startYear, yearCount));
    for (const start of occurrenceStarts(e, from, to)) vevents.push(occurrenceEvent(e, start, dtstamp));
  }
  return { text: wrapVCalendar(vevents), totalEvents: vevents.length, skippedCount: skippedReasons.length, skippedReasons };
}

/** Builds the .ics file (buildICS) and triggers a browser download. */
export function generateAndDownloadICS(events: LunarEvent[], startYear: number, yearCount: number): ExportSummary {
  const { text, ...summary } = buildICS(events, startYear, yearCount);
  const blob = new Blob([text], { type: 'text/calendar;charset=utf-8' });
  const url  = URL.createObjectURL(blob);
  const a    = document.createElement('a');
  a.href     = url;
  a.download = `lich-am-su-kien-${startYear}-${startYear + yearCount - 1}.ics`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
  return summary;
}
