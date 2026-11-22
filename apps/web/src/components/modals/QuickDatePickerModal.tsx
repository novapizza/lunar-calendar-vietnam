import { useState, useMemo } from 'react';
import type { ReactNode } from 'react';
import { format, addDays, isSameDay, isToday, startOfDay } from 'date-fns';
import { vi } from 'date-fns/locale';
import { ChevronLeft, ChevronRight, ChevronDown, CalendarSearch, Moon, Sun } from 'lucide-react';
import { cn } from '../../lib/utils';
import { WEEK_DAYS, YEAR_RANGE } from '../../constants/calendar';
import { getLunarInfo, getLunarMonths } from '../../services/lunarService';
import { Sheet } from '../Sheet';

interface Props {
  initialDate: Date;
  onClose: () => void;
  onSelect: (date: Date) => void;
}

type Cal = 'solar' | 'lunar';
type Mode = 'year' | 'month' | 'day';

const MIN_YEAR = YEAR_RANGE.start;
const MAX_YEAR = YEAR_RANGE.start + YEAR_RANGE.count - 1;
const PAGE = 12;
const pageStartOf = (year: number) => MIN_YEAR + Math.floor((year - MIN_YEAR) / PAGE) * PAGE;

/**
 * Đổi ngày âm – dương: the day as two dates, solar and lunar, side by side; tapping one picks
 * in that calendar below, with the app's year → month → day grids (as in "Chọn tháng" and
 * "Chọn ngày sinh"). Picking a day updates both dates; the footer opens it in the calendar.
 * Lunar months come in order with the leap month after its regular month.
 */
export function QuickDatePickerModal({ initialDate, onClose, onSelect }: Props) {
  const [selected, setSelected] = useState(() => startOfDay(initialDate));
  const lunar = getLunarInfo(selected);

  const [cal, setCal] = useState<Cal>('solar');
  const [mode, setMode] = useState<Mode>('day');
  // The month on show: solar = month 0–11 of viewYear; lunar = index in getLunarMonths(viewYear).
  const [viewYear, setViewYear] = useState(selected.getFullYear());
  const [viewMonth, setViewMonth] = useState(selected.getMonth());
  const [pageStart, setPageStart] = useState(() => pageStartOf(selected.getFullYear()));
  const lunarMonths = useMemo(() => (cal === 'lunar' ? getLunarMonths(viewYear) : []), [cal, viewYear]);

  /** Shows the month of `date` in calendar `c`, day grid. */
  const showIn = (c: Cal, date: Date) => {
    const l = getLunarInfo(date);
    const year = c === 'solar' ? date.getFullYear() : l.lunarYear;
    setCal(c);
    setMode('day');
    setViewYear(year);
    setViewMonth(c === 'solar'
      ? date.getMonth()
      : Math.max(0, getLunarMonths(year).findIndex(m => m.month === Math.abs(l.lunarMonth) && m.leap === l.isLeap)));
  };

  const lastIndex = (year: number) => (cal === 'solar' ? 11 : getLunarMonths(year).length - 1);
  const canPrev = mode === 'year' ? pageStart > MIN_YEAR : mode === 'month' ? viewYear > MIN_YEAR : viewYear > MIN_YEAR || viewMonth > 0;
  const canNext = mode === 'year' ? pageStart + PAGE <= MAX_YEAR : mode === 'month' ? viewYear < MAX_YEAR : viewYear < MAX_YEAR || viewMonth < lastIndex(viewYear);
  const step = (dir: 1 | -1) => {
    if (mode === 'year') setPageStart(p => p + dir * PAGE);
    else if (mode === 'month') setViewYear(y => y + dir);
    else if (dir === -1 && viewMonth === 0) { setViewYear(viewYear - 1); setViewMonth(lastIndex(viewYear - 1)); }
    else if (dir === 1 && viewMonth === lastIndex(viewYear)) { setViewYear(viewYear + 1); setViewMonth(0); }
    else setViewMonth(viewMonth + dir);
  };
  const zoomOut = () => {
    if (mode === 'day') setMode('month');
    else if (mode === 'month') { setPageStart(pageStartOf(viewYear)); setMode('year'); }
  };

  const shownLunar = cal === 'lunar' ? lunarMonths[Math.min(viewMonth, lunarMonths.length - 1)] : undefined;
  const pageEnd = Math.min(pageStart + PAGE - 1, MAX_YEAR);
  const titleText =
    mode === 'year' ? `${pageStart} – ${pageEnd}`
    : mode === 'month' ? String(viewYear)
    : cal === 'solar' ? `Tháng ${viewMonth + 1}, ${viewYear}`
    : shownLunar ? `Tháng ${shownLunar.month}${shownLunar.leap ? ' nhuận' : ''}, ${viewYear}` : String(viewYear);
  const unit = mode === 'year' ? `${PAGE} năm` : mode === 'month' ? 'Năm' : 'Tháng';

  const cell = (active: boolean, current: boolean) =>
    cn(
      'h-14 rounded-2xl text-[15px] font-semibold transition-[background-color,transform] active:scale-[0.97]',
      'disabled:opacity-30 disabled:pointer-events-none',
      active ? 'bg-brand text-on-brand shadow-[var(--shadow-card)]' : 'bg-surface-2 text-fg hover:bg-surface-3',
      !active && current && 'ring-2 ring-inset ring-brand text-brand',
    );

  const today = new Date();
  const todayLunar = getLunarInfo(today);
  const selectedYear = cal === 'solar' ? selected.getFullYear() : lunar.lunarYear;
  const currentYear = cal === 'solar' ? today.getFullYear() : todayLunar.lunarYear;

  let body: ReactNode;
  if (mode === 'year') {
    body = (
      <div className="grid grid-cols-3 gap-2.5" role="group" aria-label={`Các năm ${pageStart} đến ${pageEnd}`}>
        {Array.from({ length: PAGE }, (_, i) => pageStart + i).map(y => (
          <button
            key={y}
            onClick={() => { setViewYear(y); setMode('month'); }}
            disabled={y > MAX_YEAR}
            className={cn(cell(y === selectedYear, y === currentYear), y > MAX_YEAR && 'invisible')}
          >
            {y}
          </button>
        ))}
      </div>
    );
  } else if (mode === 'month') {
    const months = cal === 'solar'
      ? Array.from({ length: 12 }, (_, m) => ({ key: `${m}`, label: `Tháng ${m + 1}`, leap: false,
          active: viewYear === selected.getFullYear() && m === selected.getMonth(),
          current: viewYear === today.getFullYear() && m === today.getMonth() }))
      : lunarMonths.map(m => ({ key: `${m.month}${m.leap ? 'n' : ''}`, label: `Tháng ${m.month}`, leap: m.leap,
          active: viewYear === lunar.lunarYear && m.month === Math.abs(lunar.lunarMonth) && m.leap === lunar.isLeap,
          current: viewYear === todayLunar.lunarYear && m.month === Math.abs(todayLunar.lunarMonth) && m.leap === todayLunar.isLeap }));
    body = (
      <div className="grid grid-cols-3 gap-2.5" role="group" aria-label={`Các tháng ${cal === 'solar' ? 'dương' : 'âm'} lịch năm ${viewYear}`}>
        {months.map((m, i) => (
          <button key={m.key} onClick={() => { setViewMonth(i); setMode('day'); }} className={cn(cell(m.active, m.current), 'flex flex-col items-center justify-center leading-tight')}>
            {m.label}
            {m.leap && <span className="text-[11px] font-medium opacity-75">nhuận</span>}
          </button>
        ))}
      </div>
    );
  } else {
    // One cell per day of the month on show: the big number in the calendar being picked, the
    // small one in the other (as in the month grid: "1/m" on the first of a month).
    let first: Date;
    let count: number;
    if (cal === 'solar') {
      first = new Date(viewYear, viewMonth, 1);
      count = new Date(viewYear, viewMonth + 1, 0).getDate();
    } else {
      first = shownLunar ? new Date(shownLunar.first.year, shownLunar.first.month - 1, shownLunar.first.day) : selected;
      count = shownLunar?.days ?? 0;
    }
    const lead = (first.getDay() + 6) % 7; // Monday first
    body = (
      <div>
        <div className="grid grid-cols-7 gap-1 mb-1" aria-hidden="true">
          {WEEK_DAYS.map(w => (
            <span key={w} className={cn('text-center text-[11px] font-semibold', w === 'CN' ? 'text-lunar' : 'text-fg-3')}>{w}</span>
          ))}
        </div>
        <div className="grid grid-cols-7 gap-1" role="group" aria-label={titleText}>
          {Array.from({ length: lead }, (_, i) => <span key={`x${i}`} />)}
          {Array.from({ length: count }, (_, i) => {
            const date = addDays(first, i);
            const l = getLunarInfo(date);
            const lm = Math.abs(l.lunarMonth);
            const big = cal === 'solar' ? date.getDate() : i + 1;
            const small = cal === 'solar'
              ? (l.lunarDay === 1 ? `1/${lm}` : String(l.lunarDay))
              : (date.getDate() === 1 ? `1/${date.getMonth() + 1}` : String(date.getDate()));
            const markSmall = cal === 'solar' ? l.lunarDay === 1 || l.lunarDay === 15 : date.getDate() === 1;
            const active = isSameDay(date, selected);
            return (
              <button
                key={i}
                onClick={() => setSelected(date)}
                aria-label={`Ngày ${date.getDate()} tháng ${date.getMonth() + 1} năm ${date.getFullYear()}, âm lịch ${l.lunarDay} tháng ${lm}${l.isLeap ? ' nhuận' : ''}`}
                aria-current={active ? 'date' : undefined}
                className={cn(
                  'h-12 rounded-xl flex flex-col items-center justify-center leading-none transition-[background-color,transform] active:scale-[0.97]',
                  active ? 'bg-brand text-on-brand' : 'hover:bg-surface-2',
                  !active && isToday(date) && 'ring-2 ring-inset ring-brand',
                )}
              >
                <span className={cn('text-[15px] font-semibold tabular-nums', !active && date.getDay() === 0 && 'text-lunar')}>{big}</span>
                <span className={cn('text-[10px] mt-1 tabular-nums', active ? 'text-on-brand/80' : markSmall ? 'text-lunar font-semibold' : 'text-fg-3')}>
                  {small}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    );
  }

  const hint =
    mode === 'year' ? `Chọn năm ${cal === 'solar' ? 'dương' : 'âm'} lịch`
    : mode === 'month' ? `Chọn tháng ${cal === 'solar' ? 'dương' : 'âm'} lịch`
    : cal === 'solar' ? 'Chọn ngày dương · số nhỏ là ngày âm' : 'Chọn ngày âm · số nhỏ là ngày dương';

  return (
    <Sheet
      title="Đổi ngày âm – dương"
      icon={<CalendarSearch size={20} />}
      onClose={onClose}
      footer={
        <button onClick={() => onSelect(selected)} className="btn-primary w-full py-3">
          Xem ngày {format(selected, 'd/M/yyyy')}
        </button>
      }
    >
      {/* The two dates; the one being picked has the brand ring. */}
      <div className="grid grid-cols-2 gap-2.5" role="group" aria-label="Lịch để chọn ngày">
        <DateCard
          active={cal === 'solar'}
          icon={<Sun size={13} className="text-gold" />}
          label="Dương lịch"
          value={format(selected, 'd/M/yyyy')}
          detail={format(selected, 'EEEE', { locale: vi })}
          onClick={() => showIn('solar', selected)}
        />
        <DateCard
          active={cal === 'lunar'}
          icon={<Moon size={13} className="text-gold fill-current" />}
          label="Âm lịch"
          value={`${lunar.lunarDay}/${Math.abs(lunar.lunarMonth)}/${lunar.lunarYear}`}
          detail={`${lunar.isLeap ? 'Tháng nhuận · ' : ''}${lunar.canChiYear}`}
          onClick={() => showIn('lunar', selected)}
        />
      </div>

      {/* The app's picker heading: ‹ title ▾ ›, the title zooms out to months, then years. */}
      <div className="mt-4 flex items-center gap-1">
        <button onClick={() => step(-1)} disabled={!canPrev} className="icon-btn disabled:opacity-30 disabled:pointer-events-none" aria-label={`${unit} trước`}>
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
        <button onClick={() => step(1)} disabled={!canNext} className="icon-btn disabled:opacity-30 disabled:pointer-events-none" aria-label={`${unit} sau`}>
          <ChevronRight size={20} />
        </button>
      </div>
      <p className="text-xs text-fg-2 mt-1 mb-3 text-center">{hint}</p>
      {body}
    </Sheet>
  );
}

function DateCard({ active, icon, label, value, detail, onClick }: {
  active: boolean; icon: ReactNode; label: string; value: string; detail: string; onClick: () => void;
}) {
  return (
    <button
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        'rounded-2xl px-3 py-2.5 text-left transition-colors',
        active ? 'bg-brand-soft ring-2 ring-inset ring-brand' : 'bg-surface-2 hover:bg-surface-3',
      )}
    >
      <span className="section-label flex items-center gap-1.5">{icon} {label}</span>
      <span className="mt-1 block text-xl font-extrabold text-fg tabular-nums">{value}</span>
      <span className="block text-xs text-fg-2 capitalize truncate">{detail}</span>
    </button>
  );
}
