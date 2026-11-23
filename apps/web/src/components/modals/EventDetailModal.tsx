import { useState } from 'react';
import { format } from 'date-fns';
import { vi } from 'date-fns/locale';
import { Bell, CalendarHeart, Clock, MapPin, Pencil, Repeat, StickyNote, Trash } from 'lucide-react';
import { cn } from '../../lib/utils';
import { LunarEvent } from '../../types';
import { EVENT_COLORS } from '../../lib/eventColors';
import {
  addDays, colorOf, dateOf, describeEvent, lunarOf, reminderLabel, repeatOf, spanOf, timeLabel,
} from '../../services/personalEvents';
import { Sheet } from '../Sheet';

export type ChangeScope = 'one' | 'all';

interface Props {
  event: LunarEvent;
  /** The occurrence shown (its start day). */
  start: string;
  onEdit: (scope: ChangeScope) => void;
  onDelete: (scope: ChangeScope) => void;
  onClose: () => void;
}

const dayLabel = (day: string) => format(dateOf(day), 'EEEE, d/M/yyyy', { locale: vi });

function Row({ icon, children }: { icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <div className="flex gap-3 py-2">
      <span className="mt-0.5 shrink-0 text-fg-3">{icon}</span>
      <div className="min-w-0 flex-1 text-sm text-fg">{children}</div>
    </div>
  );
}

/** One event as the user sees it on a day: when, how it repeats, reminders, place, notes; edit and delete. */
export function EventDetailModal({ event: e, start, onEdit, onDelete, onClose }: Props) {
  const repeating = repeatOf(e) !== 'none';
  // Repeating events ask which occurrences a change is for.
  const [asking, setAsking] = useState<'edit' | 'delete' | null>(null);
  const l = lunarOf(start);
  const color = EVENT_COLORS[colorOf(e)];
  const last = addDays(start, spanOf(e) - 1);

  function act(kind: 'edit' | 'delete') {
    if (repeating) setAsking(kind);
    else if (kind === 'edit') onEdit('all');
    else onDelete('all');
  }

  return (
    <Sheet
      title="Sự kiện"
      icon={<CalendarHeart size={20} />}
      onClose={onClose}
      footer={asking ? (
        <div className="flex flex-col gap-2">
          <p className="text-center text-sm font-semibold text-fg">{asking === 'edit' ? 'Sửa' : 'Xoá'} lần nào?</p>
          <div className="grid grid-cols-2 gap-2">
            <button onClick={() => (asking === 'edit' ? onEdit('one') : onDelete('one'))} className="btn-ghost py-3">Chỉ lần này</button>
            <button onClick={() => (asking === 'edit' ? onEdit('all') : onDelete('all'))} className={cn('py-3', asking === 'delete' ? 'btn bg-bad text-white' : 'btn-primary')}>Tất cả các lần</button>
          </div>
          <button onClick={() => setAsking(null)} className="text-sm font-semibold text-fg-2 py-1">Thôi</button>
        </div>
      ) : (
        <div className="flex gap-2">
          <button onClick={() => act('delete')} className="btn-ghost flex-1 py-3 text-bad"><Trash size={16} /> Xoá</button>
          <button onClick={() => act('edit')} className="btn-primary flex-[2] py-3"><Pencil size={16} /> Sửa</button>
        </div>
      )}
    >
      <div className="flex gap-3">
        <span className={cn('w-1.5 shrink-0 rounded-full', color.dot)} aria-hidden="true" />
        <div className="min-w-0 flex-1">
          <h2 className="text-xl font-extrabold text-fg break-words">{e.title}</h2>
          <p className="mt-1 text-sm text-fg-2 capitalize">{dayLabel(start)}</p>
          <p className="text-sm text-fg-2">Âm lịch {l.day}/{l.month}{l.leap ? ' nhuận' : ''}</p>
        </div>
      </div>
      <div className="mt-3 divide-y divide-line">
        <Row icon={<Clock size={16} />}>
          {timeLabel(e)}
          {spanOf(e) > 1 && <span className="text-fg-2">, đến <span className="capitalize">{dayLabel(last)}</span></span>}
        </Row>
        {repeating && <Row icon={<Repeat size={16} />}>{describeEvent(e)}</Row>}
        {(e.reminders?.length ?? 0) > 0 && (
          <Row icon={<Bell size={16} />}>{[...e.reminders!].sort((a, b) => a - b).map(m => reminderLabel(e, m)).join(', ')}</Row>
        )}
        {e.location && <Row icon={<MapPin size={16} />}>{e.location}</Row>}
        {e.notes && <Row icon={<StickyNote size={16} />}><span className="whitespace-pre-wrap break-words">{e.notes}</span></Row>}
      </div>
    </Sheet>
  );
}
