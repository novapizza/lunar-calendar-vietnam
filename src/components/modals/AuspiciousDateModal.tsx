import type { ReactNode } from 'react';
import { format } from 'date-fns';
import { vi } from 'date-fns/locale';
import { Search, Heart, Hammer, Store, ChevronRight, Sparkles } from 'lucide-react';
import { cn } from '../../lib/utils';
import { ACTIVITY_RULES } from '../../services/lunarService';
import { AuspiciousDate } from '../../types';
import { Sheet } from '../Sheet';

interface Props {
  activeActivity: string | null;
  auspiciousDates: AuspiciousDate[];
  onFindDates: (key: string) => void;
  onSelectDate: (date: Date) => void;
  onClose: () => void;
}

const ACTIVITY_ICONS: Record<string, ReactNode> = {
  WEDDING:        <Heart size={20} />,
  GROUNDBREAKING: <Hammer size={20} />,
  OPENING:        <Store size={20} />,
};

export function AuspiciousDateModal({
  activeActivity,
  auspiciousDates,
  onFindDates,
  onSelectDate,
  onClose,
}: Props) {
  return (
    <Sheet
      title="Tìm ngày tốt"
      subtitle="Gợi ý trong 2 tháng tới"
      icon={<Search size={20} />}
      onClose={onClose}
      size="lg"
      tall
    >
      {/* Activity picker */}
      <div className="grid grid-cols-3 gap-2" role="radiogroup" aria-label="Loại việc">
        {Object.entries(ACTIVITY_RULES).map(([key, rule]) => {
          const active = activeActivity === key;
          return (
            <button
              key={key}
              role="radio"
              aria-checked={active}
              onClick={() => onFindDates(key)}
              className={cn(
                'flex flex-col items-center gap-2 rounded-2xl px-2 py-3.5 transition-colors',
                active ? 'bg-brand text-on-brand' : 'bg-surface-2 text-fg hover:bg-surface-3',
              )}
            >
              {ACTIVITY_ICONS[key]}
              <span className="text-sm font-semibold">{rule.label}</span>
            </button>
          );
        })}
      </div>

      {/* Results */}
      <div className="mt-5">
        {auspiciousDates.length > 0 ? (
          <>
            <p className="section-label mb-2">{auspiciousDates.length} ngày phù hợp</p>
            <ul className="flex flex-col gap-2">
              {auspiciousDates.map(item => (
                <li key={item.date.getTime()}>
                  <button
                    onClick={() => onSelectDate(item.date)}
                    className="w-full flex items-center gap-3 rounded-2xl bg-surface-2 hover:bg-surface-3 p-3 text-left transition-colors"
                  >
                    <div className="w-12 h-12 shrink-0 rounded-xl bg-surface flex flex-col items-center justify-center">
                      <span className="text-lg font-extrabold leading-none text-brand tabular-nums">{format(item.date, 'd')}</span>
                      <span className="text-[10px] font-semibold text-fg-2 mt-0.5">Th.{format(item.date, 'M')}</span>
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="text-sm font-bold text-fg capitalize">{format(item.date, 'EEEE, d/M/yyyy', { locale: vi })}</p>
                      <p className="text-xs text-fg-2">Âm {item.lunarDate} · Trực {item.truc}</p>
                    </div>
                    {item.score === 10 && (
                      <span className="shrink-0 inline-flex items-center gap-1 rounded-full bg-good-soft px-2 py-1 text-[11px] font-bold text-good">
                        <Sparkles size={11} /> Rất hợp
                      </span>
                    )}
                    <ChevronRight size={16} className="shrink-0 text-fg-3" />
                  </button>
                </li>
              ))}
            </ul>
          </>
        ) : (
          <div className="rounded-2xl border border-dashed border-line px-6 py-10 text-center text-sm text-fg-2">
            {activeActivity
              ? 'Không tìm thấy ngày phù hợp trong khoảng thời gian này.'
              : 'Chọn loại việc ở trên để xem các ngày tốt.'}
          </div>
        )}
      </div>

      <p className="mt-5 text-center text-[11px] text-fg-3">
        Dựa trên hệ thống Trực truyền thống. Chỉ mang tính chất tham khảo.
      </p>
    </Sheet>
  );
}
