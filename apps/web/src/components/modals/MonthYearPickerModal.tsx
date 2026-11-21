import { useState } from 'react';
import { ChevronLeft, ChevronRight, ChevronDown } from 'lucide-react';
import { cn } from '../../lib/utils';
import { YEAR_RANGE } from '../../constants/calendar';
import { Sheet } from '../Sheet';

interface Props {
  /** Month currently shown in the calendar. */
  value: Date;
  onClose: () => void;
  /** Called with the first day of the chosen month. */
  onSelect: (date: Date) => void;
}

const MIN_YEAR = YEAR_RANGE.start;
const MAX_YEAR = YEAR_RANGE.start + YEAR_RANGE.count - 1;
const PAGE = 12;

/** First year of the 12-year page that contains `year` (pages aligned to MIN_YEAR). */
const pageStartOf = (year: number) => MIN_YEAR + Math.floor((year - MIN_YEAR) / PAGE) * PAGE;

/**
 * Month / year picker for the month calendar.
 * Month mode: "‹ 2026 ▾ ›" + 12 months. Tapping the year switches to year mode:
 * "‹ 2020 – 2031 ›" + 12 years; picking a year returns to its months.
 */
export function MonthYearPickerModal({ value, onClose, onSelect }: Props) {
  const today = new Date();
  const [mode, setMode] = useState<'month' | 'year'>('month');
  const [viewYear, setViewYear] = useState(value.getFullYear());
  const [pageStart, setPageStart] = useState(() => pageStartOf(value.getFullYear()));

  const openYears = () => { setPageStart(pageStartOf(viewYear)); setMode('year'); };
  const pickYear = (y: number) => { setViewYear(y); setMode('month'); };

  const isMonth = mode === 'month';
  const canPrev = isMonth ? viewYear > MIN_YEAR : pageStart > MIN_YEAR;
  const canNext = isMonth ? viewYear < MAX_YEAR : pageStart + PAGE <= MAX_YEAR;
  const prev = () => (isMonth ? setViewYear(y => y - 1) : setPageStart(p => p - PAGE));
  const next = () => (isMonth ? setViewYear(y => y + 1) : setPageStart(p => p + PAGE));

  const pageEnd = Math.min(pageStart + PAGE - 1, MAX_YEAR);

  const heading = (
    <div className="flex items-center gap-1">
      <button
        onClick={prev}
        disabled={!canPrev}
        className="icon-btn disabled:opacity-30 disabled:pointer-events-none"
        aria-label={isMonth ? 'Năm trước' : `${PAGE} năm trước`}
      >
        <ChevronLeft size={20} />
      </button>
      {isMonth ? (
        <button
          onClick={openYears}
          className="flex-1 min-w-0 inline-flex items-center justify-center gap-1 rounded-xl px-2 py-1.5
            text-lg font-extrabold text-fg hover:bg-surface-2 transition-colors"
          aria-label={`Năm ${viewYear}, bấm để chọn năm khác`}
        >
          {viewYear}
          <ChevronDown size={18} className="text-fg-3" />
        </button>
      ) : (
        <button
          onClick={() => setMode('month')}
          className="flex-1 min-w-0 rounded-xl px-2 py-1.5 text-lg font-extrabold text-fg
            hover:bg-surface-2 transition-colors whitespace-nowrap"
          aria-label={`Các năm ${pageStart} đến ${pageEnd}, bấm để quay lại chọn tháng`}
        >
          {pageStart} – {pageEnd}
        </button>
      )}
      <button
        onClick={next}
        disabled={!canNext}
        className="icon-btn disabled:opacity-30 disabled:pointer-events-none"
        aria-label={isMonth ? 'Năm sau' : `${PAGE} năm sau`}
      >
        <ChevronRight size={20} />
      </button>
    </div>
  );

  const cell = (active: boolean, current: boolean) =>
    cn(
      'h-14 rounded-2xl text-[15px] font-semibold transition-[background-color,transform] active:scale-[0.97]',
      active
        ? 'bg-brand text-on-brand shadow-[var(--shadow-card)]'
        : 'bg-surface-2 text-fg hover:bg-surface-3',
      !active && current && 'ring-2 ring-inset ring-brand text-brand',
    );

  return (
    <Sheet title={isMonth ? 'Chọn tháng' : 'Chọn năm'} heading={heading} onClose={onClose}>
      {isMonth ? (
        <div className="grid grid-cols-3 gap-2.5" role="group" aria-label={`Các tháng năm ${viewYear}`}>
          {Array.from({ length: 12 }, (_, m) => {
            const active = viewYear === value.getFullYear() && m === value.getMonth();
            const current = viewYear === today.getFullYear() && m === today.getMonth();
            return (
              <button
                key={m}
                onClick={() => onSelect(new Date(viewYear, m, 1))}
                className={cell(active, current)}
                aria-current={active ? 'date' : undefined}
              >
                Tháng {m + 1}
              </button>
            );
          })}
        </div>
      ) : (
        <div className="grid grid-cols-3 gap-2.5" role="group" aria-label={`Các năm ${pageStart} đến ${pageEnd}`}>
          {Array.from({ length: PAGE }, (_, i) => pageStart + i).map(y => {
            const outOfRange = y > MAX_YEAR;
            return (
              <button
                key={y}
                onClick={() => pickYear(y)}
                disabled={outOfRange}
                className={cn(cell(y === viewYear, y === today.getFullYear()), outOfRange && 'invisible')}
                aria-current={y === viewYear ? 'date' : undefined}
              >
                {y}
              </button>
            );
          })}
        </div>
      )}
    </Sheet>
  );
}
