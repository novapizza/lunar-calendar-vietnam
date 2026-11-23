import { CalendarHeart, Plus, Repeat } from 'lucide-react';
import { cn } from '../lib/utils';
import { EVENT_COLORS } from '../lib/eventColors';
import { DayEvent, colorOf, repeatOf, timeLabel } from '../services/personalEvents';

interface Props {
  items: DayEvent[];
  onOpen: (item: DayEvent) => void;
  onAdd: () => void;
  className?: string;
}

/** "Sự kiện" card of a day (Calendar day panel, Today page): its events and "+ Thêm". */
export function DayEventsCard({ items, onOpen, onAdd, className }: Props) {
  return (
    <section className={cn('card p-4', className)}>
      <div className="mb-2 flex items-center justify-between">
        <p className="section-label flex items-center gap-1.5"><CalendarHeart size={13} /> Sự kiện</p>
        <button onClick={onAdd} className="btn-soft px-3 py-1.5 text-xs"><Plus size={14} /> Thêm</button>
      </div>
      {items.length === 0 ? (
        <p className="text-sm text-fg-3">Chưa có sự kiện.</p>
      ) : (
        <ul className="flex flex-col gap-1.5">
          {items.map(item => {
            const e = item.event;
            return (
              <li key={`${e.id}-${item.start}`}>
                <button onClick={() => onOpen(item)} className="flex w-full items-center gap-3 rounded-xl bg-surface-2 px-3 py-2 text-left hover:bg-surface-3">
                  <span className={cn('h-2.5 w-2.5 shrink-0 rounded-full', EVENT_COLORS[colorOf(e)].dot)} aria-hidden="true" />
                  <span className="min-w-0 flex-1">
                    <span className="block truncate text-sm font-semibold text-fg">{e.title}</span>
                    <span className="flex items-center gap-1 text-xs text-fg-2">
                      {timeLabel(e)}
                      {repeatOf(e) !== 'none' && <Repeat size={11} className="text-fg-3" aria-label="Lặp lại" />}
                      {e.location && <span className="truncate">· {e.location}</span>}
                    </span>
                  </span>
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
