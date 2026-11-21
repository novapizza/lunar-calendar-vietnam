import { Clock } from 'lucide-react';
import { cn } from '../lib/utils';
import { CHI_EMOJI } from '../constants/calendar';

/** "Tý (23h-1h)" → { name: "Tý", hours: "23–1h" } */
function parseGio(gio: string) {
  const m = gio.match(/^(\S+)\s*\((\d+)h-(\d+)h\)$/);
  return m ? { name: m[1], hours: `${m[2]}–${m[3]}h` } : { name: gio, hours: '' };
}

interface Props {
  /** The day's six auspicious hours, as in LunarInfo.gioHoangDao. */
  hours: string[];
  /** Chi of the hour now, when the day shown is today; that hour is highlighted. */
  currentHour?: string | null;
  className?: string;
}

/**
 * Giờ hoàng đạo: one row of six, each an animal tile, the chi and its hours.
 * Tinted with the palette's soft colour; the hour now gets the brand colour.
 */
export function GioHoangDao({ hours, currentHour, className }: Props) {
  return (
    <section className={cn('card p-4 bg-brand-soft', className)}>
      <h2 className="flex items-center gap-2 text-sm font-semibold text-fg mb-3">
        <Clock size={16} className="text-brand" /> Giờ hoàng đạo
      </h2>
      <ul className="grid grid-cols-6 gap-1.5">
        {hours.map(gio => {
          const { name, hours: range } = parseGio(gio);
          const isNow = name === currentHour;
          return (
            <li key={gio} className="flex flex-col items-center min-w-0 text-center">
              <span
                className={cn(
                  'w-full max-w-14 aspect-square rounded-xl flex items-center justify-center',
                  'text-[clamp(1.125rem,6vw,1.75rem)] leading-none shadow-[var(--shadow-card)]',
                  isNow ? 'bg-brand ring-2 ring-brand ring-offset-2 ring-offset-brand-soft' : 'bg-surface',
                )}
                aria-hidden="true"
              >
                {CHI_EMOJI[name] ?? '🕒'}
              </span>
              <span className={cn('mt-1.5 text-sm font-semibold', isNow ? 'text-brand' : 'text-fg')}>{name}</span>
              <span className={cn('text-[11px] tabular-nums whitespace-nowrap', isNow ? 'text-brand font-semibold' : 'text-fg-2')}>
                {range}
              </span>
              {isNow && <span className="sr-only">, đang diễn ra</span>}
            </li>
          );
        })}
      </ul>
    </section>
  );
}
