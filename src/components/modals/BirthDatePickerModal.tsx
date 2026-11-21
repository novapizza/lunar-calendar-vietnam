import { useState } from 'react';
import { ChevronLeft, ChevronRight, ChevronDown } from 'lucide-react';
import { cn } from '../../lib/utils';
import { WEEK_DAYS } from '../../constants/calendar';
import { LunarCalendar } from '../../services/lunarService';
import { BIRTH_DATE_MIN } from '../../lib/menh';
import { Sheet } from '../Sheet';

interface Props {
  /** Current birth date "YYYY-MM-DD", or null. */
  value: string | null;
  onClose: () => void;
  onSelect: (value: string | null) => void;
}

type Mode = 'year' | 'month' | 'day';

const MIN_YEAR = Number(BIRTH_DATE_MIN.slice(0, 4));
const PAGE = 12;
/** Year page shown first when no birth date is set yet. */
const DEFAULT_YEAR = 1990;

const pad = (n: number) => String(n).padStart(2, '0');
const iso = (y: number, m: number, d: number) => `${y}-${pad(m + 1)}-${pad(d)}`;
const pageStartOf = (year: number) => MIN_YEAR + Math.floor((year - MIN_YEAR) / PAGE) * PAGE;

/**
 * Birth date picker in the style of the month/year picker: year → month → day.
 * Starts on the saved date's month, or on a page of years when nothing is set.
 * The day grid shows the lunar day too, since Tết decides the year of birth.
 */
export function BirthDatePickerModal({ value, onClose, onSelect }: Props) {
  const now = new Date();
  const todayIso = iso(now.getFullYear(), now.getMonth(), now.getDate());
  const maxYear = now.getFullYear();
  const initial = value ? { y: Number(value.slice(0, 4)), m: Number(value.slice(5, 7)) - 1 } : null;

  const [mode, setMode] = useState<Mode>(initial ? 'day' : 'year');
  const [viewYear, setViewYear] = useState(initial?.y ?? DEFAULT_YEAR);
  const [viewMonth, setViewMonth] = useState(initial?.m ?? 0);
  const [pageStart, setPageStart] = useState(() => pageStartOf(initial?.y ?? DEFAULT_YEAR));

  const monthAllowed = (y: number, m: number) =>
    iso(y, m, new Date(y, m + 1, 0).getDate()) >= BIRTH_DATE_MIN && iso(y, m, 1) <= todayIso;

  // Arrows: a year page, a year, or a month, depending on the mode.
  const canPrev =
    mode === 'year' ? pageStart > MIN_YEAR
    : mode === 'month' ? viewYear > MIN_YEAR
    : monthAllowed(viewMonth === 0 ? viewYear - 1 : viewYear, (viewMonth + 11) % 12);
  const canNext =
    mode === 'year' ? pageStart + PAGE <= maxYear
    : mode === 'month' ? viewYear < maxYear
    : monthAllowed(viewMonth === 11 ? viewYear + 1 : viewYear, (viewMonth + 1) % 12);

  const step = (dir: 1 | -1) => {
    if (mode === 'year') setPageStart(p => p + dir * PAGE);
    else if (mode === 'month') setViewYear(y => y + dir);
    else {
      const d = new Date(viewYear, viewMonth + dir, 1);
      setViewYear(d.getFullYear());
      setViewMonth(d.getMonth());
    }
  };

  const pageEnd = Math.min(pageStart + PAGE - 1, maxYear);
  const titleText =
    mode === 'year' ? `${pageStart} – ${pageEnd}`
    : mode === 'month' ? String(viewYear)
    : `Tháng ${viewMonth + 1}, ${viewYear}`;
  const zoomOut = () => {
    if (mode === 'day') setMode('month');
    else if (mode === 'month') { setPageStart(pageStartOf(viewYear)); setMode('year'); }
  };

  const heading = (
    <div className="flex items-center gap-1">
      <button
        onClick={() => step(-1)}
        disabled={!canPrev}
        className="icon-btn disabled:opacity-30 disabled:pointer-events-none"
        aria-label={mode === 'year' ? `${PAGE} năm trước` : mode === 'month' ? 'Năm trước' : 'Tháng trước'}
      >
        <ChevronLeft size={20} />
      </button>
      <button
        onClick={zoomOut}
        disabled={mode === 'year'}
        className="flex-1 min-w-0 inline-flex items-center justify-center gap-1 rounded-xl px-2 py-1.5
          text-lg font-extrabold text-fg whitespace-nowrap hover:bg-surface-2 transition-colors disabled:hover:bg-transparent"
        aria-label={mode === 'year' ? titleText : `${titleText}, bấm để chọn ${mode === 'day' ? 'tháng' : 'năm'} khác`}
      >
        {titleText}
        {mode !== 'year' && <ChevronDown size={18} className="text-fg-3" />}
      </button>
      <button
        onClick={() => step(1)}
        disabled={!canNext}
        className="icon-btn disabled:opacity-30 disabled:pointer-events-none"
        aria-label={mode === 'year' ? `${PAGE} năm sau` : mode === 'month' ? 'Năm sau' : 'Tháng sau'}
      >
        <ChevronRight size={20} />
      </button>
    </div>
  );

  const cell = (active: boolean) =>
    cn(
      'h-14 rounded-2xl text-[15px] font-semibold transition-[background-color,transform] active:scale-[0.97]',
      'disabled:opacity-30 disabled:pointer-events-none',
      active ? 'bg-brand text-on-brand shadow-[var(--shadow-card)]' : 'bg-surface-2 text-fg hover:bg-surface-3',
    );

  let body;
  if (mode === 'year') {
    body = (
      <div className="grid grid-cols-3 gap-2.5" role="group" aria-label={`Các năm ${pageStart} đến ${pageEnd}`}>
        {Array.from({ length: PAGE }, (_, i) => pageStart + i).map(y => (
          <button
            key={y}
            onClick={() => { setViewYear(y); setMode('month'); }}
            disabled={y > maxYear}
            className={cn(cell(y === initial?.y), y > maxYear && 'invisible')}
          >
            {y}
          </button>
        ))}
      </div>
    );
  } else if (mode === 'month') {
    body = (
      <div className="grid grid-cols-3 gap-2.5" role="group" aria-label={`Các tháng năm ${viewYear}`}>
        {Array.from({ length: 12 }, (_, m) => (
          <button
            key={m}
            onClick={() => { setViewMonth(m); setMode('day'); }}
            disabled={!monthAllowed(viewYear, m)}
            className={cell(viewYear === initial?.y && m === initial?.m)}
          >
            Tháng {m + 1}
          </button>
        ))}
      </div>
    );
  } else {
    const days = new Date(viewYear, viewMonth + 1, 0).getDate();
    const lead = (new Date(viewYear, viewMonth, 1).getDay() + 6) % 7; // Monday first
    body = (
      <div>
        <div className="grid grid-cols-7 gap-1 mb-1" aria-hidden="true">
          {WEEK_DAYS.map(w => (
            <span key={w} className={cn('text-center text-[11px] font-semibold', w === 'CN' ? 'text-lunar' : 'text-fg-3')}>{w}</span>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-1" role="group" aria-label={`Các ngày tháng ${viewMonth + 1} năm ${viewYear}`}>
          {Array.from({ length: lead }, (_, i) => <span key={`x${i}`} />)}
          {Array.from({ length: days }, (_, i) => {
            const d = i + 1;
            const date = iso(viewYear, viewMonth, d);
            const active = date === value;
            const lunar = LunarCalendar.fromSolar(d, viewMonth + 1, viewYear).lunarDate;
            const lunarLabel = lunar.day === 1 ? `${lunar.day}/${Math.abs(lunar.month)}` : String(lunar.day);
            return (
              <button
                key={d}
                onClick={() => { onSelect(date); onClose(); }}
                disabled={date < BIRTH_DATE_MIN || date > todayIso}
                aria-label={`Ngày ${d} tháng ${viewMonth + 1} năm ${viewYear}, âm lịch ${lunar.day} tháng ${Math.abs(lunar.month)}`}
                aria-current={active ? 'date' : undefined}
                className={cn(
                  'h-12 rounded-xl flex flex-col items-center justify-center leading-none transition-[background-color,transform] active:scale-[0.97]',
                  'disabled:opacity-30 disabled:pointer-events-none',
                  active ? 'bg-brand text-on-brand' : 'hover:bg-surface-2',
                )}
              >
                <span className="text-[15px] font-semibold">{d}</span>
                <span className={cn('text-[10px] mt-1', active ? 'text-on-brand/80' : lunar.day === 1 ? 'text-lunar font-semibold' : 'text-fg-3')}>
                  {lunarLabel}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  return (
    <Sheet
      title="Chọn ngày sinh"
      heading={heading}
      onClose={onClose}
      footer={value ? (
        <button onClick={() => { onSelect(null); onClose(); }} className="btn w-full text-fg-2 hover:bg-surface-2">
          Xoá ngày sinh
        </button>
      ) : undefined}
    >
      <p className="text-xs text-fg-2 mb-3">
        {mode === 'year' ? 'Chọn năm sinh (dương lịch)' : mode === 'month' ? 'Chọn tháng sinh' : 'Chọn ngày sinh · số nhỏ là ngày âm'}
      </p>
      {body}
    </Sheet>
  );
}
