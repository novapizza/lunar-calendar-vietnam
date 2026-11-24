import { EventRepeat, LunarEvent } from '../types';
import { occurrenceStarts } from './personalEvents';
import { REF_MONG_1, REF_RAM } from './lunarService';

/**
 * Reminders for built-in days (shared/design/events.md, "Reminders for built-in days"): one
 * event per built-in day ref, stored under `builtinReminders`, with the personal events' repeat,
 * end and reminder fields. It is never listed; the day's row shows a bell instead.
 */

/** The repeats offered for a built-in day, the default first: every month's mồng 1 / rằm also monthly. */
export function builtinRepeats(ref: string): EventRepeat[] {
  return ref === REF_MONG_1 || ref === REF_RAM ? ['monthly', 'yearly', 'none'] : ['yearly', 'none'];
}

/** The reminder kept for a built-in day, if any. */
export function builtinReminderOf(reminders: LunarEvent[], ref: string): LunarEvent | undefined {
  return reminders.find(r => r.ref === ref);
}

/** Whether a built-in day's occurrence on `day` (YYYY-MM-DD) has a reminder. */
export function builtinReminds(reminders: LunarEvent[], ref: string | undefined, day: string): boolean {
  if (!ref) return false;
  const r = builtinReminderOf(reminders, ref);
  return !!r?.reminders?.length && occurrenceStarts(r, day, day).length > 0;
}
