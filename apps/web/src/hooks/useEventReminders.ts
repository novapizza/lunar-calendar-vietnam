import { useEffect } from 'react';
import { LunarEvent } from '../types';
import { dateOf, isoOf, lunarOf, remindersBetween, ReminderFire, repeatOf, calendarOf, timeLabel, addDays } from '../services/personalEvents';
import { format } from 'date-fns';
import { vi } from 'date-fns/locale';

const SHOWN_KEY = 'eventRemindersShown';
/** Reminders due up to this long ago are still shown when the page opens. */
const LATE_MS = 15 * 60_000;
/** Look this far ahead for the next reminder (a timer is set again when the page stays open). */
const AHEAD_MS = 24 * 3_600_000;

function readShown(): number {
  try { return Number(localStorage.getItem(SHOWN_KEY)) || 0; } catch { return 0; }
}
function writeShown(t: number) {
  try { localStorage.setItem(SHOWN_KEY, String(t)); } catch { /* storage off */ }
}

/** "Hôm nay · 09:00 – 10:00 · 10/10 âm", "Ngày mai · Cả ngày", "Thứ năm 26/11 · Cả ngày". */
export function reminderBody(f: ReminderFire, now = new Date()): string {
  const today = isoOf(now);
  const when = f.start === today ? 'Hôm nay' : f.start === addDays(today, 1) ? 'Ngày mai' : format(dateOf(f.start), 'EEEE d/M', { locale: vi });
  const e = f.event;
  const byLunar = calendarOf(e) === 'lunar' && (repeatOf(e) === 'monthly' || repeatOf(e) === 'yearly');
  const l = lunarOf(f.start);
  return `${when.charAt(0).toUpperCase()}${when.slice(1)} · ${timeLabel(e)}${byLunar ? ` · ${l.day}/${l.month} âm` : ''}`;
}

async function show(f: ReminderFire) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return;
  const options = { body: reminderBody(f), tag: `${f.event.id}-${f.start}-${f.minutes}`, icon: '/pwa-192x192.png' };
  try {
    const reg = await navigator.serviceWorker?.getRegistration();
    if (reg) { await reg.showNotification(f.event.title, options); return; }
  } catch { /* fall back below */ }
  try { new Notification(f.event.title, options); } catch { /* not allowed here */ }
}

/**
 * Shows personal event reminders while the page is open (shared/design/events.md: the web has
 * no push server). Due reminders not yet shown (up to 15 minutes late) are shown at once, then a
 * timer waits for the next one within a day.
 */
export function useEventReminders(events: LunarEvent[]) {
  useEffect(() => {
    if (!events.some(e => e.reminders?.length)) return;
    let timer: number | undefined;
    const run = () => {
      const now = Date.now();
      const shown = readShown();
      const due = remindersBetween(events, Math.max(now - LATE_MS, shown + 1), now + 1);
      due.forEach(f => void show(f));
      if (due.length) writeShown(due[due.length - 1].at);
      const next = remindersBetween(events, now + 1, now + AHEAD_MS)[0];
      timer = window.setTimeout(run, next ? Math.max(1000, next.at - now) : AHEAD_MS / 2);
    };
    run();
    return () => window.clearTimeout(timer);
  }, [events]);
}
