import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { format } from 'date-fns';
import { vi } from 'date-fns/locale';
import { Moon, Sun, CalendarX2 } from 'lucide-react';
import { cn } from '../../lib/utils';
import { getEventsOfMonth, LAST_EVENT_DAY } from '../../services/lunarService';
import { UpcomingEvent } from '../../types';

function countdown(days: number): string {
  if (days === 0) return 'Hôm nay';
  if (days === 1) return 'Ngày mai';
  if (days === -1) return 'Hôm qua';
  if (days < 0) return `${-days} ngày trước`;
  return `${days} ngày nữa`;
}

const KIND_LABEL = { observance: 'Kỷ niệm', festival: 'Lễ hội' } as const;

/** Months loaded at least: this month and the next three, so three months from today or more. */
const MIN_MONTHS = 4;
/** Keep this much list loaded below the bottom of the screen (px), so scrolling never waits. */
const LOAD_AHEAD = 600;

/**
 * Sự kiện: the next event highlighted, then the following ones grouped by solar month, without
 * end: months are loaded one at a time, at first through the third month from now and until the
 * screen is full, then as the list nears the bottom of the screen, up to the end of the lunar
 * tables (2099).
 */
export function EventsView() {
  const today = useMemo(() => {
    const t = new Date();
    t.setHours(0, 0, 0, 0);
    return t;
  }, []);
  const [count, setCount] = useState(1);
  const cache = useRef(new Map<number, UpcomingEvent[]>());
  const events = useMemo(() => {
    const out: UpcomingEvent[] = [];
    for (let i = 0; i < count; i++) {
      let month = cache.current.get(i);
      if (!month) cache.current.set(i, (month = getEventsOfMonth(today, i)));
      out.push(...month);
    }
    return out;
  }, [count, today]);
  const ended = new Date(today.getFullYear(), today.getMonth() + count, 1) > LAST_EVENT_DAY;
  // This month's past days are listed too (dimmed); the highlight is the next event from today.
  const next = events.find(e => e.daysUntil >= 0);
  const rest = events.filter(e => e !== next);

  // Group the remaining events by solar month
  const groups = useMemo(() => {
    const map = new Map<string, UpcomingEvent[]>();
    for (const e of rest) {
      const key = format(e.solarDate, "'Tháng' M, yyyy");
      map.set(key, [...(map.get(key) ?? []), e]);
    }
    return [...map.entries()];
  }, [events]);

  // Load the next month while fewer than MIN_MONTHS are loaded or the list's end is less than
  // LOAD_AHEAD below the screen. Runs after every load (so it fills the screen month by month),
  // on scroll and when the window changes size.
  const scroller = useRef<HTMLDivElement>(null);
  const end = useRef<HTMLDivElement>(null);
  const check = useCallback(() => {
    const sc = scroller.current;
    const e = end.current;
    if (!sc || !e || ended) return;
    const below = e.getBoundingClientRect().top - sc.getBoundingClientRect().bottom;
    if (count < MIN_MONTHS || below < LOAD_AHEAD) setCount(count + 1);
  }, [count, ended]);
  useEffect(() => {
    check();
    const sc = scroller.current;
    if (!sc) return;
    const ro = new ResizeObserver(check);
    ro.observe(sc);
    return () => ro.disconnect();
  }, [check]);

  return (
    <div ref={scroller} onScroll={check} className="flex-1 min-h-0 overflow-y-auto overscroll-contain pt-[env(safe-area-inset-top)]">
      <div className="max-w-2xl lg:max-w-5xl mx-auto px-4 rail:px-8 pt-4 rail:pt-8 pb-10">
        {/* One grid, filled row by row (left, then right), as a year calendar's month cards: the
            list grows only at the bottom, so loading more never moves what is on screen.
            One column on phones; two from lg (840px) and in book (unfolded foldable), the title
            and next event across both (in book the event's text stays in the left half, clear of
            the fold). */}
        <div className="grid grid-cols-1 gap-y-6 items-start lg:grid-cols-2 lg:gap-x-6 book:grid-cols-2 book:gap-x-4">
          <div className="min-w-0 lg:col-span-2 book:col-span-2">
            <header className="mb-4 rail:mb-6">
              <p className="section-label">Sắp diễn ra</p>
              <h1 className="text-2xl rail:text-3xl font-extrabold text-fg">Ngày lễ & sự kiện</h1>
            </header>

            {!next ? (
              <div className="card p-10 text-center">
                <CalendarX2 size={40} className="mx-auto mb-3 text-fg-3" />
                <p className="font-semibold text-fg-2">Không có sự kiện sắp tới</p>
              </div>
            ) : (
              /* Next event highlight */
              <section className="relative overflow-hidden rounded-[1.75rem] p-5 hero">
                <div className="pointer-events-none absolute -right-12 -top-12 w-44 h-44 rounded-full bg-hero-accent/8" aria-hidden="true" />
                <p className="relative text-[11px] font-semibold uppercase tracking-[0.12em] text-white/65">Sự kiện tiếp theo</p>
                <div className="relative mt-2 flex items-end justify-between gap-4">
                  {/* book: the card spans both halves; the text ends 1.5rem short of the left half
                      (card padding 1.25rem, half the 1rem column gap: the left half ends at 50% - 0.5rem). */}
                  <div className="min-w-0 book:max-w-[calc(50%-1.5rem)]">
                    <h2 className="text-xl rail:text-2xl font-extrabold leading-tight">{next.title}</h2>
                    <p className="mt-1 text-sm text-white/80 capitalize">
                      {format(next.solarDate, 'EEEE, d/M/yyyy', { locale: vi })}
                      {next.source === 'lunar' && ` · ${next.lunarDay}/${next.lunarMonth} âm`}
                    </p>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="text-4xl rail:text-5xl font-extrabold leading-none text-hero-accent tabular-nums">
                      {next.daysUntil === 0 ? '🎉' : next.daysUntil}
                    </p>
                    <p className="text-xs font-semibold text-white/75 mt-1">
                      {next.daysUntil === 0 ? 'Hôm nay' : 'ngày nữa'}
                    </p>
                  </div>
                </div>
              </section>
            )}
          </div>

          {groups.map(([month, list]) => (
            <section key={month} className="min-w-0">
              <h2 className="section-label mb-2 px-1">{month}</h2>
              <ul className="card divide-y divide-line overflow-hidden">
                {list.map(event => (
                  <li key={`${event.title}-${event.solarDate.getTime()}`} className={cn('flex items-center gap-3 px-4 py-3', event.daysUntil < 0 && 'opacity-55')}>
                    <div className={cn(
                      'w-12 h-12 shrink-0 rounded-2xl flex flex-col items-center justify-center',
                      event.source === 'lunar' ? 'bg-lunar-soft text-lunar' : 'bg-brand-soft text-brand',
                    )}>
                      <span className="text-lg font-extrabold leading-none tabular-nums">{format(event.solarDate, 'd')}</span>
                      <span className="text-[10px] font-semibold mt-0.5 capitalize">{format(event.solarDate, 'EEEEEE', { locale: vi })}</span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-bold text-fg leading-snug">{event.title}</p>
                      <p className="mt-0.5 flex items-center gap-1 text-xs text-fg-2">
                        {event.source === 'lunar'
                          ? <><Moon size={11} className="text-gold fill-current" /> {event.lunarDay}/{event.lunarMonth} âm lịch</>
                          : <><Sun size={11} className="text-gold" /> Dương lịch</>}
                        {event.kind && <span className="text-fg-3">· {KIND_LABEL[event.kind]}</span>}
                      </p>
                    </div>
                    <span className={cn(
                      'shrink-0 rounded-full px-2.5 py-1 text-[11px] font-bold whitespace-nowrap',
                      event.daysUntil >= 0 && event.daysUntil <= 7 ? 'bg-gold-soft text-gold' : event.daysUntil < 0 ? 'bg-surface-2 text-fg-3' : 'bg-surface-2 text-fg-2',
                    )}>
                      {countdown(event.daysUntil)}
                    </span>
                  </li>
                ))}
              </ul>
            </section>
          ))}
        </div>

        {/* The list's end: loading is measured from here. The about link moved out of this view
            (Tiện ích has it): an endless list has no bottom to put it at. */}
        <div ref={end} aria-hidden="true" />
        {ended && <p className="mt-8 text-center text-xs text-fg-3">Lịch tính đến hết năm 2099.</p>}
      </div>
    </div>
  );
}
