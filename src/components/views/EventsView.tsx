import { useMemo } from 'react';
import { format } from 'date-fns';
import { vi } from 'date-fns/locale';
import { Moon, Sun, CalendarX2 } from 'lucide-react';
import { cn } from '../../lib/utils';
import { getUpcomingEvents } from '../../services/lunarService';
import { UpcomingEvent } from '../../types';
import { AboutLink } from '../AboutLink';

function countdown(days: number): string {
  if (days === 0) return 'Hôm nay';
  if (days === 1) return 'Ngày mai';
  return `${days} ngày nữa`;
}

export function EventsView({ onOpenAbout }: { onOpenAbout: () => void }) {
  const events = useMemo(() => getUpcomingEvents(12), []);
  const [next, ...rest] = events;

  // Group the remaining events by solar month
  const groups = useMemo(() => {
    const map = new Map<string, UpcomingEvent[]>();
    for (const e of rest) {
      const key = format(e.solarDate, "'Tháng' M, yyyy");
      map.set(key, [...(map.get(key) ?? []), e]);
    }
    return [...map.entries()];
  }, [events]);

  return (
    <div className="flex-1 min-h-0 overflow-y-auto overscroll-contain pt-[env(safe-area-inset-top)]">
      <div className="max-w-2xl mx-auto px-4 md:px-8 pt-4 md:pt-8 pb-10">
        <header className="mb-4 md:mb-6">
          <p className="section-label">Sắp diễn ra</p>
          <h1 className="text-2xl md:text-3xl font-extrabold text-fg">Ngày lễ & sự kiện</h1>
        </header>

        {!next ? (
          <div className="card p-10 text-center">
            <CalendarX2 size={40} className="mx-auto mb-3 text-fg-3" />
            <p className="font-semibold text-fg-2">Không có sự kiện trong 6 tháng tới</p>
          </div>
        ) : (
          <>
            {/* Next event highlight */}
            <section className="relative overflow-hidden rounded-[1.75rem] p-5 hero">
              <div className="pointer-events-none absolute -right-12 -top-12 w-44 h-44 rounded-full bg-hero-accent/8" aria-hidden="true" />
              <p className="relative text-[11px] font-semibold uppercase tracking-[0.12em] text-white/65">Sự kiện tiếp theo</p>
              <div className="relative mt-2 flex items-end justify-between gap-4">
                <div className="min-w-0">
                  <h2 className="text-xl md:text-2xl font-extrabold leading-tight">{next.title}</h2>
                  <p className="mt-1 text-sm text-white/80 capitalize">
                    {format(next.solarDate, 'EEEE, d/M/yyyy', { locale: vi })}
                    {next.source === 'lunar' && ` · ${next.lunarDay}/${next.lunarMonth} âm`}
                  </p>
                </div>
                <div className="shrink-0 text-right">
                  <p className="text-4xl md:text-5xl font-extrabold leading-none text-hero-accent tabular-nums">
                    {next.daysUntil === 0 ? '🎉' : next.daysUntil}
                  </p>
                  <p className="text-xs font-semibold text-white/75 mt-1">
                    {next.daysUntil === 0 ? 'Hôm nay' : 'ngày nữa'}
                  </p>
                </div>
              </div>
            </section>

            {groups.map(([month, list]) => (
              <section key={month} className="mt-6">
                <h2 className="section-label mb-2 px-1">{month}</h2>
                <ul className="card divide-y divide-line overflow-hidden">
                  {list.map(event => (
                    <li key={`${event.title}-${event.solarDate.getTime()}`} className="flex items-center gap-3 px-4 py-3">
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
                        </p>
                      </div>
                      <span className={cn(
                        'shrink-0 rounded-full px-2.5 py-1 text-[11px] font-bold whitespace-nowrap',
                        event.daysUntil <= 7 ? 'bg-gold-soft text-gold' : 'bg-surface-2 text-fg-2',
                      )}>
                        {countdown(event.daysUntil)}
                      </span>
                    </li>
                  ))}
                </ul>
              </section>
            ))}
          </>
        )}

        <AboutLink onClick={onOpenAbout} />
      </div>
    </div>
  );
}
