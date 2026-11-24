import { SmallDate } from './SmallDate';
import { useState } from 'react';
import { format } from 'date-fns';
import { vi } from 'date-fns/locale';
import { CalendarDays, ChevronDown, ChevronLeft, ChevronRight } from 'lucide-react';
import { cn } from '../lib/utils';
import { WEEK_DAYS } from '../constants/calendar';
import { dateOf, iso, isoOf, lunarOf, parts } from '../services/personalEvents';

interface Props {
  /** YYYY-MM-DD */
  value: string;
  onChange: (day: string) => void;
  label: string;
  /** Earliest day that can be picked (YYYY-MM-DD). */
  min?: string;
}

type Mode = 'day' | 'month' | 'year';
const MIN_YEAR = 1901;
const MAX_YEAR = 2099;
const PAGE = 12;

/**
 * A date as one line ("Thứ Hai, 23/11/2026 · 15/10 âm"); tapping it opens a month grid under it
 * with the lunar day under each day (as Chọn ngày sinh), the title zooms out to months and years,
 * "Hôm nay" jumps back. Picking a day closes it.
 */
export function DatePickerField({ value, onChange, label, min }: Props) {
  const [open, setOpen] = useState(false);
  const [mode, setMode] = useState<Mode>('day');
  const [vy, vm] = parts(value);
  const [viewYear, setViewYear] = useState(vy);
  const [viewMonth, setViewMonth] = useState(vm - 1);
  const [pageStart, setPageStart] = useState(vy - (vy % PAGE));
  const today = isoOf(new Date());
  const l = lunarOf(value);

  function toggle() {
    if (!open) { const [y, m] = parts(value); setViewYear(y); setViewMonth(m - 1); setMode('day'); }
    setOpen(o => !o);
  }
  function pick(day: string) { onChange(day); setOpen(false); }
  function step(dir: 1 | -1) {
    if (mode === 'year') setPageStart(p => Math.max(MIN_YEAR - (MIN_YEAR % PAGE), Math.min(MAX_YEAR, p + dir * PAGE)));
    else if (mode === 'month') setViewYear(y => Math.max(MIN_YEAR, Math.min(MAX_YEAR, y + dir)));
    else {
      const d = new Date(viewYear, viewMonth + dir, 1);
      if (d.getFullYear() < MIN_YEAR || d.getFullYear() > MAX_YEAR) return;
      setViewYear(d.getFullYear());
      setViewMonth(d.getMonth());
    }
  }
  const title = mode === 'year' ? `${pageStart} – ${pageStart + PAGE - 1}` : mode === 'month' ? String(viewYear) : `Tháng ${viewMonth + 1}, ${viewYear}`;
  const cell = (active: boolean) => cn('h-11 rounded-xl text-sm font-semibold transition-colors', active ? 'bg-brand text-on-brand' : 'bg-surface hover:bg-surface-3 text-fg');

  let body;
  if (mode === 'year') {
    body = (
      <div className="grid grid-cols-4 gap-1.5">
        {Array.from({ length: PAGE }, (_, i) => pageStart + i).map(y => (
          <button key={y} type="button" disabled={y < MIN_YEAR || y > MAX_YEAR} onClick={() => { setViewYear(y); setMode('month'); }}
            className={cn(cell(y === vy), 'disabled:invisible')}>{y}</button>
        ))}
      </div>
    );
  } else if (mode === 'month') {
    body = (
      <div className="grid grid-cols-4 gap-1.5">
        {Array.from({ length: 12 }, (_, m) => (
          <button key={m} type="button" onClick={() => { setViewMonth(m); setMode('day'); }} className={cell(viewYear === vy && m === vm - 1)}>Th {m + 1}</button>
        ))}
      </div>
    );
  } else {
    const days = new Date(viewYear, viewMonth + 1, 0).getDate();
    const lead = (new Date(viewYear, viewMonth, 1).getDay() + 6) % 7;
    body = (
      <div>
        <div className="grid grid-cols-7 gap-0.5 mb-0.5" aria-hidden="true">
          {WEEK_DAYS.map(w => <span key={w} className={cn('text-center text-[11px] font-semibold py-1', w === 'CN' ? 'text-lunar' : 'text-fg-3')}>{w}</span>)}
        </div>
        <div className="grid grid-cols-7 gap-0.5" role="group" aria-label={`Các ngày tháng ${viewMonth + 1} năm ${viewYear}`}>
          {Array.from({ length: lead }, (_, i) => <span key={`x${i}`} />)}
          {Array.from({ length: days }, (_, i) => {
            const d = i + 1;
            const day = iso(viewYear, viewMonth + 1, d);
            const active = day === value;
            const isToday = day === today;
            const ld = lunarOf(day);
            return (
              <button
                key={d}
                type="button"
                onClick={() => pick(day)}
                disabled={!!min && day < min}
                aria-label={`Ngày ${d} tháng ${viewMonth + 1} năm ${viewYear}, âm lịch ${ld.day} tháng ${ld.month}`}
                aria-current={active ? 'date' : undefined}
                className={cn(
                  'h-11 rounded-xl flex flex-col items-center justify-center leading-none transition-colors disabled:opacity-30 disabled:pointer-events-none',
                  active ? 'bg-brand text-on-brand' : isToday ? 'bg-brand-soft text-brand' : 'hover:bg-surface-3 text-fg',
                )}
              >
                <span className="text-sm font-semibold">{d}</span>
                <span className={cn('text-[10px] mt-0.5', active ? 'text-on-brand/80' : ld.day === 1 || ld.day === 15 ? 'text-lunar font-semibold' : 'text-fg-3')}>
                  <SmallDate day={ld.day} month={ld.month} leap={ld.leap} withMonth={ld.day === 1 || active} />
                </span>
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-xs font-semibold text-fg-2">{label}</span>
      <button type="button" onClick={toggle} aria-expanded={open} className="field bg-surface flex items-center gap-2 text-left">
        <CalendarDays size={16} className="shrink-0 text-brand" />
        <span className="min-w-0 flex-1 truncate">
          <span className="font-semibold text-fg capitalize">{format(dateOf(value), 'EEEE, d/M/yyyy', { locale: vi })}</span>
          <span className="text-fg-2"> · {l.day}/{l.month}{l.leap ? ' nhuận' : ''} âm</span>
        </span>
        <ChevronDown size={16} className={cn('shrink-0 text-fg-3 transition-transform', open && 'rotate-180')} />
      </button>
      {open && (
        <div className="rounded-2xl bg-surface p-2">
          <div className="flex items-center gap-1 mb-1">
            <button type="button" onClick={() => step(-1)} className="icon-btn" aria-label="Trước"><ChevronLeft size={18} /></button>
            <button
              type="button"
              onClick={() => (mode === 'day' ? setMode('month') : mode === 'month' ? (setPageStart(viewYear - (viewYear % PAGE)), setMode('year')) : undefined)}
              className="flex-1 inline-flex items-center justify-center gap-1 rounded-xl py-1.5 text-sm font-bold text-fg hover:bg-surface-2"
            >
              {title}{mode !== 'year' && <ChevronDown size={15} className="text-fg-3" />}
            </button>
            <button type="button" onClick={() => step(1)} className="icon-btn" aria-label="Sau"><ChevronRight size={18} /></button>
          </div>
          {body}
          <div className="mt-1.5 flex items-center justify-between px-1">
            <span className="text-[11px] text-fg-3">{mode === 'day' ? 'Số nhỏ là ngày âm' : mode === 'month' ? 'Chọn tháng' : 'Chọn năm'}</span>
            <button type="button" onClick={() => pick(today)} disabled={!!min && today < min} className="text-xs font-semibold text-brand py-1 disabled:opacity-30">Hôm nay</button>
          </div>
        </div>
      )}
    </div>
  );
}
