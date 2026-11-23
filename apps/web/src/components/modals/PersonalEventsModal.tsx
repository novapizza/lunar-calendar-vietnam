import { CalendarHeart, ChevronRight, Plus } from 'lucide-react';
import { cn } from '../../lib/utils';
import { LunarEvent } from '../../types';
import { EVENT_COLORS } from '../../lib/eventColors';
import { colorOf, describeEvent, timeLabel } from '../../services/personalEvents';
import { Sheet } from '../Sheet';

interface Props {
  events: LunarEvent[];
  onOpen: (e: LunarEvent) => void;
  onAdd: () => void;
  onClose: () => void;
}

/** Sự kiện của bạn: every personal event (title, rule, time); tap opens its detail. */
export function PersonalEventsModal({ events, onOpen, onAdd, onClose }: Props) {
  return (
    <Sheet
      title="Sự kiện của bạn"
      subtitle="Giỗ, sinh nhật, lịch hẹn… lặp theo ngày âm hoặc dương"
      icon={<CalendarHeart size={20} />}
      onClose={onClose}
      size="lg"
      tall
      footer={<button onClick={onAdd} className="btn-primary w-full py-3"><Plus size={17} /> Thêm sự kiện</button>}
    >
      {events.length === 0 ? (
        <div className="rounded-2xl border border-dashed border-line px-4 py-6 text-center text-sm text-fg-2">
          Chưa có sự kiện. Thêm giỗ, sinh nhật, lịch hẹn… để thấy trong lịch, danh sách sự kiện và nhận nhắc.
        </div>
      ) : (
        <ul className="rounded-2xl bg-surface-2 divide-y divide-line overflow-hidden">
          {events.map(e => (
            <li key={e.id}>
              <button onClick={() => onOpen(e)} className="flex w-full items-center gap-3 px-4 py-3 text-left hover:bg-surface-3">
                <span className={cn('h-2.5 w-2.5 shrink-0 rounded-full', EVENT_COLORS[colorOf(e)].dot)} aria-hidden="true" />
                <span className="min-w-0 flex-1">
                  <span className="block text-sm font-semibold text-fg break-words">{e.title}</span>
                  <span className="block text-xs text-fg-2">{describeEvent(e)} · {timeLabel(e)}</span>
                </span>
                <ChevronRight size={16} className="shrink-0 text-fg-3" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </Sheet>
  );
}
