import { lunarToSolar, SolarYMD } from './lunarService';
import { LunarEvent } from '../types';

// ---------------------------------------------------------------------------
// Result types
// ---------------------------------------------------------------------------

export interface ResolvedEventYear {
  skipped: false;
  /** Lunar year of this occurrence — stable identity, used in the event UID. */
  lunarYear: number;
  /** Solar year the occurrence actually falls in (differs for months 11–12). */
  solarYear: number;
  solarDate: SolarYMD;
  /** True when day 30 was moved to day 29 because the month has only 29 days. */
  adjustedDay: boolean;
}

export interface SkippedEventYear {
  skipped: true;
  lunarYear: number;
  title: string;
  reason: 'leap_month_not_found' | 'conversion_error';
}

export type EventYearResult = ResolvedEventYear | SkippedEventYear;

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

function toDateString(y: number, m: number, d: number): string {
  return `${y}${pad2(m)}${pad2(d)}`;
}

function nextDay(y: number, m: number, d: number): string {
  const dt = new Date(y, m - 1, d + 1);
  return toDateString(dt.getFullYear(), dt.getMonth() + 1, dt.getDate());
}

function utcStamp(): string {
  const now = new Date();
  return (
    `${now.getUTCFullYear()}${pad2(now.getUTCMonth() + 1)}${pad2(now.getUTCDate())}` +
    `T${pad2(now.getUTCHours())}${pad2(now.getUTCMinutes())}${pad2(now.getUTCSeconds())}Z`
  );
}

// ---------------------------------------------------------------------------
// Core resolution logic
// ---------------------------------------------------------------------------

/**
 * Returns every occurrence of a lunar event that falls in the solar years
 * `startYear` … `startYear + yearCount - 1`.
 *
 * Works per lunar year, not per solar year: months 11–12 of lunar year N often
 * land in January/February of solar year N+1. So lunar year `startYear - 1` is
 * checked too, and each occurrence is kept only if its real solar date is
 * inside the range. Results are labelled with both years.
 */
export function resolveEventYears(
  event: LunarEvent,
  startYear: number,
  yearCount: number,
): EventYearResult[] {
  const endYear = startYear + yearCount - 1;
  const results: EventYearResult[] = [];

  for (let lunarYear = startYear - 1; lunarYear <= endYear; lunarYear++) {
    const r = lunarToSolar(event.lunarDay, event.lunarMonth, lunarYear, event.isLeapMonth);

    if (r.ok === false) {
      // Lunar year startYear-1 is only probed for spill-over into startYear;
      // a miss there is not something the user asked about.
      if (lunarYear >= startYear) {
        results.push({ skipped: true, lunarYear, title: event.title, reason: r.reason });
      }
      continue;
    }

    if (r.solar.year < startYear || r.solar.year > endYear) continue;

    results.push({
      skipped: false,
      lunarYear,
      solarYear: r.solar.year,
      solarDate: r.solar,
      adjustedDay: r.adjustedDay,
    });
  }

  return results;
}

// ---------------------------------------------------------------------------
// ICS formatting
// ---------------------------------------------------------------------------

function formatVEvent(event: LunarEvent, resolved: ResolvedEventYear, dtstamp: string): string {
  const { year, month, day } = resolved.solarDate;
  const dtstart = toDateString(year, month, day);
  const dtend   = nextDay(year, month, day);

  const leapTag = event.isLeapMonth ? ' Nhuận' : '';
  const summary = escapeText(
    `${event.title} (Âm ${event.lunarDay}/${event.lunarMonth}${leapTag})`,
  );
  const adjustedNote = resolved.adjustedDay
    ? `\nTháng ${event.lunarMonth} năm nay chỉ có 29 ngày nên sự kiện được đặt vào ngày 29.`
    : '';
  const description = escapeText(
    `Sự kiện âm lịch ngày ${event.lunarDay} tháng ${event.lunarMonth}${leapTag} (năm âm ${resolved.lunarYear})${adjustedNote}\nTạo bởi Lịch Âm Việt Nam`,
  );

  const lines = [
    'BEGIN:VEVENT',
    foldLine(`UID:${event.id}-${resolved.lunarYear}@lunar-vn`),
    `DTSTAMP:${dtstamp}`,
    `DTSTART;VALUE=DATE:${dtstart}`,
    `DTEND;VALUE=DATE:${dtend}`,
    foldLine(`SUMMARY:${summary}`),
    foldLine(`DESCRIPTION:${description}`),
    'END:VEVENT',
  ];

  return lines.join('\r\n');
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
// Public entry point
// ---------------------------------------------------------------------------

/**
 * Generates an ICS file for all saved lunar events across `yearCount` years
 * starting from `startYear`, then triggers a browser download.
 */
export function generateAndDownloadICS(
  events: LunarEvent[],
  startYear: number,
  yearCount: number,
): ExportSummary {
  const dtstamp = utcStamp();
  const vevents: string[] = [];
  const skippedReasons: SkippedEventYear[] = [];
  let totalEvents = 0;

  for (const event of events) {
    const results = resolveEventYears(event, startYear, yearCount);
    for (const result of results) {
      if (result.skipped === true) {
        skippedReasons.push(result);
      } else {
        vevents.push(formatVEvent(event, result, dtstamp));
        totalEvents++;
      }
    }
  }

  const icsContent = wrapVCalendar(vevents);
  const blob = new Blob([icsContent], { type: 'text/calendar;charset=utf-8' });
  const url  = URL.createObjectURL(blob);
  const a    = document.createElement('a');
  a.href     = url;
  a.download = `lich-am-su-kien-${startYear}-${startYear + yearCount - 1}.ics`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);

  return { totalEvents, skippedCount: skippedReasons.length, skippedReasons };
}
