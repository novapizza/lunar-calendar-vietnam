import { useEffect, useState } from 'react';
import { Bell, CalendarHeart, Plus, X } from 'lucide-react';
import { builtinRepeats } from '../../services/builtinReminders';
import { cn } from '../../lib/utils';
import { EventCalendar, EventColor, EventRepeat, LunarEvent } from '../../types';
import { EVENT_COLORS, EVENT_COLOR_ORDER } from '../../lib/eventColors';
import {
  REMINDERS_ALL_DAY, REMINDERS_TIMED, WEEKDAY_SHORT, calendarOf, isAllDay, lunarOf, parts, repeatOf,
  spanOf, startTimeOf, endTimeOf, weekdayOf, weekdaysOf, addDays, colorOf,
} from '../../services/personalEvents';
import { Sheet } from '../Sheet';
import { DatePickerField } from '../DatePickerField';

export type EventDraft = Omit<LunarEvent, 'id' | 'createdAt'>;

/** A built-in day (holiday, observance, mồng 1, rằm) whose reminder the form sets. */
export interface BuiltinDay {
  ref: string;
  /** The day's own title ("Mồng 1 tháng 10"); a monthly reminder takes the ref ("Mồng 1"). */
  title: string;
  calendar: EventCalendar;
}

interface Props {
  /** The event being edited, or null for a new one. */
  event: LunarEvent | null;
  /** The day the form starts on: the chosen day for a new event, the occurrence for an edit. */
  day: string;
  /** Editing one occurrence of a repeating event: the repeat fields are hidden (it becomes a one-off). */
  single?: boolean;
  /**
   * Setting a built-in day's reminder (shared/design/events.md): title, day, time, place, notes and
   * colour are the day's; only the repeat, its end and the reminders are shown.
   */
  builtin?: BuiltinDay;
  onSave: (draft: EventDraft) => void;
  /** Built-in day with a reminder: turns it off. */
  onDelete?: () => void;
  onClose: () => void;
}

type EndMode = 'never' | 'until' | 'count';

interface Form {
  title: string;
  allDay: boolean;
  start: string;
  startTime: string;
  endTime: string;
  days: number;
  repeat: EventRepeat;
  calendar: EventCalendar;
  weekdays: number[];
  endMode: EndMode;
  until: string;
  count: number;
  reminders: number[];
  location: string;
  notes: string;
  color: EventColor;
  /** Lunar yearly from a day in a leap month: only that leap month. */
  leap: boolean;
}

const REPEATS: [EventRepeat, string][] = [
  ['none', 'Không lặp'], ['daily', 'Hằng ngày'], ['weekly', 'Hằng tuần'], ['monthly', 'Hằng tháng'], ['yearly', 'Hằng năm'],
];

function formOf(e: LunarEvent | null, day: string, single: boolean, builtin?: BuiltinDay): Form {
  const l = lunarOf(day);
  if (!e) {
    return {
      title: builtin?.title ?? '', allDay: true, start: day, startTime: '09:00', endTime: '10:00', days: 1,
      repeat: builtin ? builtinRepeats(builtin.ref)[0] : 'none', calendar: builtin?.calendar ?? 'lunar',
      weekdays: [weekdayOf(day)], endMode: 'never', until: addDays(day, 30), count: 10, reminders: builtin ? [0] : [], location: '', notes: '',
      color: 'gold', leap: builtin ? false : l.leap,
    };
  }
  const repeat = single ? 'none' : repeatOf(e);
  return {
    title: e.title, allDay: isAllDay(e), start: single ? day : (e.start && repeat !== 'monthly' && repeat !== 'yearly' ? e.start : day),
    startTime: startTimeOf(e), endTime: endTimeOf(e), days: spanOf(e), repeat, calendar: calendarOf(e),
    weekdays: weekdaysOf(e).length ? weekdaysOf(e) : [weekdayOf(day)],
    endMode: single ? 'never' : e.until ? 'until' : e.count ? 'count' : 'never', until: e.until ?? addDays(day, 30), count: e.count ?? 10,
    reminders: e.reminders ?? [], location: e.location ?? '', notes: e.notes ?? '', color: colorOf(e), leap: e.isLeapMonth || l.leap,
  };
}

function Toggle({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button type="button" role="switch" aria-checked={on} aria-label={label} onClick={() => onChange(!on)} className="flex items-center justify-between gap-3 py-1">
      <span className="text-sm font-semibold text-fg">{label}</span>
      <span className={cn('relative h-6 w-11 rounded-full transition-colors', on ? 'bg-brand' : 'bg-surface-3')}>
        <span className={cn('absolute top-0.5 h-5 w-5 rounded-full bg-white shadow transition-all', on ? 'left-[22px]' : 'left-0.5')} />
      </span>
    </button>
  );
}

function Segmented<T extends string>({ value, options, onChange, label }: { value: T; options: [T, string][]; onChange: (v: T) => void; label: string }) {
  return (
    <div role="radiogroup" aria-label={label} className="grid grid-cols-2 gap-1 rounded-xl bg-surface p-1">
      {options.map(([v, text]) => (
        <button key={v} type="button" role="radio" aria-checked={value === v} onClick={() => onChange(v)}
          className={cn('rounded-lg py-1.5 text-sm font-semibold transition-colors', value === v ? 'bg-brand text-on-brand' : 'text-fg-2 hover:bg-surface-2')}>
          {text}
        </button>
      ))}
    </div>
  );
}

/** Asks for notification permission once (the browser keeps the answer). */
function askNotifications() {
  try {
    if ('Notification' in window && Notification.permission === 'default') void Notification.requestPermission();
  } catch { /* not supported */ }
}

/** The page may not show notifications: blocked by the user or not supported. */
function notificationsBlocked(): boolean {
  try {
    return !('Notification' in window) || Notification.permission === 'denied';
  } catch {
    return true;
  }
}

const Group = ({ children }: { children: React.ReactNode }) => (
  <div className="flex flex-col gap-3 rounded-2xl bg-surface-2 p-4">{children}</div>
);

/** Sự kiện mới / Sửa sự kiện: every field of shared/design/events.md. */
export function EventEditorModal({ event, day, single = false, builtin, onSave, onDelete, onClose }: Props) {
  const [f, setF] = useState<Form>(() => formOf(event, day, single, builtin));
  const set = (patch: Partial<Form>) => setF(prev => ({ ...prev, ...patch }));
  const l = lunarOf(f.start);
  const [sy, sm, sd] = parts(f.start);
  const repeating = f.repeat !== 'none';
  const byDate = f.repeat === 'monthly' || f.repeat === 'yearly';
  const lunar = f.calendar === 'lunar';
  const reminderOptions = f.allDay ? REMINDERS_ALL_DAY : REMINDERS_TIMED;
  const timeError = !f.allDay && f.days === 1 && f.endTime <= f.startTime;
  // Opening a form with reminders asks at once (user, 2026-11-24: a popup, not only a line of
  // text). The tap that opened the form still counts as the user's gesture. A browser never asks
  // again once blocked: then only the line under the reminders remains.
  useEffect(() => { if (f.reminders.length) askNotifications(); }, []);

  const ruleWords = f.repeat === 'monthly'
    ? lunar ? `Ngày ${l.day} âm lịch hằng tháng (cả tháng nhuận)` : `Ngày ${sd} hằng tháng${sd > 28 ? ' (tháng thiếu: ngày cuối tháng)' : ''}`
    : lunar ? `Ngày ${l.day}/${l.month}${l.leap && f.leap ? ' nhuận' : ''} âm lịch hằng năm` : `Ngày ${sd}/${sm} hằng năm${sd === 29 && sm === 2 ? ' (năm thường: 28/2)' : ''}`;

  function save() {
    // Also on save: a built-in day's form starts with a reminder, which no "Thêm nhắc" asked for.
    if (f.reminders.length) askNotifications();
    if (builtin) {
      if (!f.reminders.length) return;
      onSave({
        title: f.repeat === 'monthly' ? builtin.ref : builtin.title,
        ref: builtin.ref,
        calendar: byDate ? builtin.calendar : 'solar',
        repeat: f.repeat,
        lunarDay: byDate && lunar ? l.day : sd,
        lunarMonth: byDate && lunar ? l.month : sm,
        isLeapMonth: false,
        start: event?.start && event.start < f.start ? event.start : f.start,
        until: repeating && f.endMode === 'until' ? f.until : undefined,
        count: repeating && f.endMode === 'count' ? Math.max(1, f.count) : undefined,
        exceptions: event?.exceptions,
        allDay: true,
        reminders: [...f.reminders].sort((a, b) => a - b),
        color: 'gold',
      });
      return;
    }
    const title = f.title.trim();
    if (!title || timeError) return;
    const byLunar = byDate && lunar;
    // The form's day sets a monthly / yearly rule; editing such a series opens on the occurrence
    // tapped, so the series keeps its own first day (none: every year shows, as old giỗ events),
    // moved earlier only when the form's day is earlier. A count needs a first day.
    let start: string | undefined = f.start;
    if (byDate && event && !single) {
      start = event.start ? (f.start < event.start ? f.start : event.start) : f.endMode === 'count' ? f.start : undefined;
    }
    const draft: EventDraft = {
      title,
      calendar: byDate ? f.calendar : 'solar',
      repeat: f.repeat,
      lunarDay: byLunar ? l.day : sd,
      lunarMonth: byLunar ? l.month : sm,
      isLeapMonth: byLunar && f.repeat === 'yearly' && l.leap && f.leap,
      start,
      weekdays: f.repeat === 'weekly' ? [...f.weekdays].sort() : undefined,
      until: repeating && f.endMode === 'until' ? f.until : undefined,
      count: repeating && f.endMode === 'count' ? Math.max(1, f.count) : undefined,
      exceptions: event && !single ? event.exceptions : undefined,
      allDay: f.allDay,
      startTime: f.allDay ? undefined : f.startTime,
      endTime: f.allDay ? undefined : f.endTime,
      days: f.days > 1 ? f.days : undefined,
      reminders: f.reminders.length ? [...f.reminders].sort((a, b) => a - b) : undefined,
      location: f.location.trim() || undefined,
      notes: f.notes.trim() || undefined,
      color: f.color,
    };
    onSave(draft);
  }

  function addReminder(minutes: number) {
    if (f.reminders.includes(minutes)) return;
    set({ reminders: [...f.reminders, minutes] });
    askNotifications();
  }

  return (
    <Sheet
      title={builtin ? 'Nhắc ngày này' : event ? (single ? 'Sửa lần này' : 'Sửa sự kiện') : 'Sự kiện mới'}
      icon={builtin ? <Bell size={20} /> : <CalendarHeart size={20} />}
      onClose={onClose}
      size="lg"
      tall={!builtin}
      footer={
        <div className="flex gap-2">
          {builtin && event && onDelete
            ? <button onClick={onDelete} className="btn-ghost flex-1 py-3 text-bad">Tắt nhắc</button>
            : <button onClick={onClose} className="btn-ghost flex-1 py-3">Huỷ</button>}
          <button onClick={save} disabled={builtin ? !f.reminders.length : !f.title.trim() || timeError} className="btn-primary flex-[2] py-3">Lưu</button>
        </div>
      }
    >
      <div className="flex flex-col gap-3">
        {builtin ? (
          <div className="px-1">
            <p className="text-base font-bold text-fg">{builtin.title}</p>
            <p className="text-xs text-fg-2">{sd}/{sm}/{sy} · {l.day}/{l.month}{l.leap ? ' nhuận' : ''} âm lịch</p>
          </div>
        ) : (
        <input
          type="text" value={f.title} onChange={e => set({ title: e.target.value })} placeholder="Tên sự kiện, vd: Giỗ ông nội"
          className="field bg-surface-2 text-base font-semibold" aria-label="Tên sự kiện" autoFocus={!event}
        />
        )}

        {!builtin && <Group>
          <Toggle on={f.allDay} onChange={allDay => set({ allDay, reminders: [] })} label="Cả ngày" />
          <DatePickerField value={f.start} onChange={start => set({ start, leap: lunarOf(start).leap, weekdays: f.repeat === 'weekly' ? f.weekdays : [weekdayOf(start)], until: f.until < start ? start : f.until })} label="Ngày" />
          {!f.allDay && (
            <div className="grid grid-cols-2 gap-2">
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-semibold text-fg-2">Bắt đầu</span>
                <input type="time" step={300} value={f.startTime} onChange={e => e.target.value && set({ startTime: e.target.value })} className="field bg-surface font-semibold" />
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-semibold text-fg-2">Kết thúc</span>
                <input type="time" step={300} value={f.endTime} onChange={e => e.target.value && set({ endTime: e.target.value })} className="field bg-surface font-semibold" />
              </label>
            </div>
          )}
          {timeError && <p className="text-xs font-semibold text-bad">Giờ kết thúc phải sau giờ bắt đầu.</p>}
          <label className="flex items-center justify-between gap-3">
            <span className="text-sm font-semibold text-fg">Kéo dài</span>
            <select value={f.days} onChange={e => set({ days: +e.target.value })} className="field bg-surface font-semibold w-36" aria-label="Kéo dài">
              {Array.from({ length: 30 }, (_, i) => i + 1).map(n => <option key={n} value={n}>{n} ngày</option>)}
            </select>
          </label>
          {f.days > 1 && <p className="text-xs text-fg-2">Đến {(() => { const [y, m, d] = parts(addDays(f.start, f.days - 1)); return `${d}/${m}/${y}`; })()}</p>}
        </Group>}

        {!single && (
          <Group>
            <label className="flex items-center justify-between gap-3">
              <span className="text-sm font-semibold text-fg">Lặp lại</span>
              <select value={f.repeat} onChange={e => set({ repeat: e.target.value as EventRepeat })} className="field bg-surface font-semibold w-36" aria-label="Lặp lại">
                {REPEATS.filter(([v]) => !builtin || builtinRepeats(builtin.ref).includes(v)).map(([v, t]) => <option key={v} value={v}>{t}</option>)}
              </select>
            </label>
            {f.repeat === 'weekly' && (
              <div className="grid grid-cols-7 gap-1" role="group" aria-label="Các thứ trong tuần">
                {WEEKDAY_SHORT.map((w, i) => {
                  const on = f.weekdays.includes(i);
                  return (
                    <button key={w} type="button" aria-pressed={on}
                      onClick={() => set({ weekdays: on ? (f.weekdays.length > 1 ? f.weekdays.filter(x => x !== i) : f.weekdays) : [...f.weekdays, i] })}
                      className={cn('rounded-lg py-1.5 text-xs font-bold', on ? 'bg-brand text-on-brand' : 'bg-surface text-fg-2')}>
                      {w}
                    </button>
                  );
                })}
              </div>
            )}
            {byDate && (
              <>
                {!builtin && <Segmented label="Theo lịch" value={f.calendar} options={[['lunar', 'Âm lịch'], ['solar', 'Dương lịch']]} onChange={calendar => set({ calendar })} />}
                <p className="text-xs text-fg-2">{ruleWords}</p>
                {!builtin && lunar && f.repeat === 'yearly' && l.leap && (
                  <label className="flex items-center gap-2.5 cursor-pointer">
                    <input type="checkbox" checked={f.leap} onChange={e => set({ leap: e.target.checked })} className="w-5 h-5 rounded accent-[var(--brand)]" />
                    <span className="text-sm text-fg">Chỉ tháng {l.month} nhuận <span className="text-xs text-fg-2">(bỏ chọn: tháng {l.month} thường)</span></span>
                  </label>
                )}
              </>
            )}
            {repeating && (
              <>
                <label className="flex items-center justify-between gap-3">
                  <span className="text-sm font-semibold text-fg">Kết thúc</span>
                  <select value={f.endMode} onChange={e => set({ endMode: e.target.value as EndMode })} className="field bg-surface font-semibold w-36" aria-label="Kết thúc">
                    <option value="never">Không bao giờ</option>
                    <option value="until">Đến ngày</option>
                    <option value="count">Sau số lần</option>
                  </select>
                </label>
                {f.endMode === 'until' && (
                  <DatePickerField value={f.until} min={f.start} onChange={until => set({ until })} label="Đến ngày" />
                )}
                {f.endMode === 'count' && (
                  <label className="flex items-center justify-between gap-3">
                    <span className="text-xs text-fg-2">Số lần, tính từ {sd}/{sm}/{sy}</span>
                    <input type="number" min={1} max={999} value={f.count} onChange={e => set({ count: Math.max(1, Math.min(999, +e.target.value || 1)) })} className="field bg-surface font-semibold w-24 text-right" aria-label="Số lần" />
                  </label>
                )}
              </>
            )}
          </Group>
        )}

        <Group>
          <span className="text-sm font-semibold text-fg">Nhắc</span>
          {f.reminders.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {[...f.reminders].sort((a, b) => a - b).map(m => (
                <span key={m} className="inline-flex items-center gap-1 rounded-full bg-surface pl-3 pr-1 py-1 text-xs font-semibold text-fg">
                  {reminderOptions.find(([x]) => x === m)?.[1] ?? `${m} phút trước`}
                  <button type="button" onClick={() => set({ reminders: f.reminders.filter(x => x !== m) })} className="rounded-full p-0.5 text-fg-3 hover:text-bad" aria-label="Bỏ nhắc">
                    <X size={13} />
                  </button>
                </span>
              ))}
            </div>
          )}
          <label className="relative flex items-center gap-2 text-sm font-semibold text-brand">
            <Plus size={15} /> Thêm nhắc
            <select value="" onChange={e => e.target.value !== '' && addReminder(+e.target.value)} className="absolute inset-0 opacity-0 cursor-pointer" aria-label="Thêm nhắc">
              <option value="">Chọn</option>
              {reminderOptions.filter(([m]) => !f.reminders.includes(m)).map(([m, t]) => <option key={m} value={m}>{t}</option>)}
            </select>
          </label>
          <p className="text-xs text-fg-3">Trên web, nhắc chỉ hiện khi trang hoặc app đã cài đang mở.</p>
          {f.reminders.length > 0 && notificationsBlocked() && (
            <p className="text-xs font-semibold text-bad">Trình duyệt đang chặn thông báo của trang này: bật lại trong phần quyền của trang để nhận nhắc.</p>
          )}
        </Group>

        {!builtin && <Group>
          <input type="text" value={f.location} onChange={e => set({ location: e.target.value })} placeholder="Địa điểm" className="field bg-surface" aria-label="Địa điểm" />
          <textarea value={f.notes} onChange={e => set({ notes: e.target.value })} placeholder="Ghi chú" rows={3} className="field bg-surface resize-none" aria-label="Ghi chú" />
          <div className="flex items-center justify-between">
            <span className="text-sm font-semibold text-fg">Màu</span>
            <div className="flex gap-2" role="radiogroup" aria-label="Màu">
              {EVENT_COLOR_ORDER.map(c => (
                <button key={c} type="button" role="radio" aria-checked={f.color === c} aria-label={EVENT_COLORS[c].label} onClick={() => set({ color: c })}
                  className={cn('h-7 w-7 rounded-full', EVENT_COLORS[c].dot, f.color === c && 'ring-2 ring-offset-2 ring-offset-surface-2 ring-fg')} />
              ))}
            </div>
          </div>
        </Group>}
      </div>
    </Sheet>
  );
}
