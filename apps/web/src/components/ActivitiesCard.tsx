import { Heart, Hammer, Store, Sparkles } from 'lucide-react';
import { cn } from '../lib/utils';
import type { LunarInfo } from '../types';

/**
 * "Việc nên làm": the three activities, good or not suitable, and the Trừ day's note.
 * Shared by the Today page and the Calendar's selected-day panel.
 */
export function ActivitiesCard({ info }: { info: LunarInfo }) {
  const activities = [
    { key: 'wedding', label: 'Cưới hỏi',    Icon: Heart,  ok: info.activities.wedding },
    { key: 'moving',  label: 'Chuyển nhà',  Icon: Hammer, ok: info.activities.moving },
    { key: 'opening', label: 'Khai trương', Icon: Store,  ok: info.activities.opening },
  ];
  return (
    <section className="card p-4">
      <h2 className="section-label mb-3">Việc nên làm</h2>
      <ul className="flex flex-col gap-2">
        {activities.map(({ key, label, Icon, ok }) => (
          <li key={key} className="flex items-center gap-3">
            <div className={cn(
              'w-9 h-9 shrink-0 rounded-xl flex items-center justify-center',
              ok ? 'bg-good-soft text-good' : 'bg-surface-2 text-fg-3',
            )}>
              <Icon size={17} />
            </div>
            <span className={cn('flex-1 text-sm font-semibold', ok ? 'text-fg' : 'text-fg-2')}>{label}</span>
            <span className={cn(
              'rounded-full px-2.5 py-1 text-[11px] font-bold',
              ok ? 'bg-good-soft text-good' : 'bg-surface-2 text-fg-3',
            )}>
              {ok ? 'Tốt' : 'Không hợp'}
            </span>
          </li>
        ))}
      </ul>
      {info.truc === 'Trừ' && (
        <p className="mt-3 flex gap-2 rounded-xl bg-surface-2 p-3 text-xs text-fg-2 leading-relaxed">
          <Sparkles size={14} className="shrink-0 mt-0.5 text-gold" />
          Ngày Trừ: tốt để dọn dẹp, chữa bệnh, giải toả mâu thuẫn.
        </p>
      )}
    </section>
  );
}
