import { useState, useMemo } from 'react';
import {
  format, addMonths, subMonths, startOfMonth, endOfMonth,
  startOfWeek, endOfWeek, eachDayOfInterval, isSameMonth, isSameDay, isToday,
} from 'date-fns';
import { ChevronLeft, ChevronRight, CalendarSearch, Moon, Sun } from 'lucide-react';
import { cn } from '../../lib/utils';
import { WEEK_DAYS, YEAR_RANGE } from '../../constants/calendar';
import { getLunarInfo, lunarToSolar } from '../../services/lunarService';
import { Sheet } from '../Sheet';

interface Props {
  initialDate: Date;
  onClose: () => void;
  onSelect: (date: Date) => void;
}

export function QuickDatePickerModal({ initialDate, onClose, onSelect }: Props) {
  const [viewDate, setViewDate] = useState(() => new Date(initialDate));
  const [selected, setSelected] = useState(() => new Date(initialDate));

  const calendarDays = useMemo(() => {
    const ms = startOfMonth(viewDate);
    return eachDayOfInterval({
      start: startOfWeek(ms, { weekStartsOn: 1 }),
      end:   endOfWeek(endOfMonth(ms), { weekStartsOn: 1 }),
    }).map(day => ({ day, lunar: getLunarInfo(day) }));
  }, [viewDate]);

  const currentLunar = getLunarInfo(selected);

  const pick = (d: Date) => { setSelected(d); setViewDate(new Date(d)); };

  const handleSolarChange = (field: 'day' | 'month' | 'year', value: number) => {
    const next = new Date(selected);
    if (field === 'day')   next.setDate(value);
    if (field === 'month') next.setMonth(value - 1);
    if (field === 'year')  next.setFullYear(value);
    pick(next);
  };

  const handleLunarChange = (field: 'day' | 'month' | 'year', value: number) => {
    let { lunarDay: d, lunarMonth: m, lunarYear: y } = currentLunar;
    const { isLeap } = currentLunar;
    if (field === 'day')   d = value;
    if (field === 'month') m = value;
    if (field === 'year')  y = value;
    m = Math.abs(m);
    // Keep the leap flag only while it still exists in the target month/year.
    let r = lunarToSolar(d, m, y, isLeap);
    if (!r.ok && isLeap) r = lunarToSolar(d, m, y, false);
    if (!r.ok) return;
    pick(new Date(r.solar.year, r.solar.month - 1, r.solar.day));
  };

  const years = Array.from({ length: YEAR_RANGE.count }, (_, i) => YEAR_RANGE.start + i);

  return (
    <Sheet
      title="Đổi ngày âm – dương"
      subtitle="Chọn một ngày để xem lịch"
      icon={<CalendarSearch size={20} />}
      onClose={onClose}
      footer={
        <button onClick={() => onSelect(selected)} className="btn-primary w-full py-3">
          Xem ngày {format(selected, 'd/M/yyyy')}
        </button>
      }
    >
      {/* Month navigation */}
      <div className="flex items-center justify-between mb-1">
        <h3 className="text-base font-bold text-fg">Tháng {format(viewDate, 'M, yyyy')}</h3>
        <div className="flex">
          <button onClick={() => setViewDate(subMonths(viewDate, 1))} className="icon-btn" aria-label="Tháng trước">
            <ChevronLeft size={18} />
          </button>
          <button onClick={() => setViewDate(addMonths(viewDate, 1))} className="icon-btn" aria-label="Tháng sau">
            <ChevronRight size={18} />
          </button>
        </div>
      </div>

      {/* Mini calendar */}
      <div className="grid grid-cols-7 gap-y-0.5">
        {WEEK_DAYS.map(d => (
          <div key={d} className={cn('pb-1 text-center text-[11px] font-semibold', d === 'CN' ? 'text-lunar' : 'text-fg-3')}>
            {d}
          </div>
        ))}
        {calendarDays.map(({ day, lunar }) => {
          const isSel = isSameDay(day, selected);
          return (
            <button
              key={day.getTime()}
              onClick={() => pick(day)}
              className={cn(
                // Same spacing as the month grid's day cells: no line leading, 2px between the numbers.
                'mx-auto w-10 h-11 flex flex-col items-center justify-center gap-0.5 rounded-xl transition-colors',
                !isSameMonth(day, viewDate) && 'opacity-30',
                isSel ? 'bg-brand text-on-brand' : 'hover:bg-surface-2',
                !isSel && isToday(day) && 'ring-2 ring-brand ring-inset',
              )}
            >
              <span className={cn('text-sm leading-none font-semibold tabular-nums', !isSel && day.getDay() === 0 && 'text-lunar')}>
                {format(day, 'd')}
              </span>
              <span className={cn(
                'text-[9px] leading-none tabular-nums',
                isSel ? 'text-on-brand/80' : lunar.lunarDay === 1 || lunar.lunarDay === 15 ? 'text-lunar font-bold' : 'text-fg-3',
              )}>
                {lunar.lunarDay === 1 ? `1/${Math.abs(lunar.lunarMonth)}` : lunar.lunarDay}
              </span>
            </button>
          );
        })}
      </div>

      {/* Converters */}
      <div className="mt-4 flex flex-col gap-3">
        <fieldset>
          <legend className="section-label mb-1.5 flex items-center gap-1.5"><Sun size={12} className="text-gold" /> Dương lịch</legend>
          <div className="grid grid-cols-[1fr_1.2fr_1.4fr] gap-2">
            <select value={selected.getDate()} onChange={e => handleSolarChange('day', +e.target.value)} className="field font-semibold" aria-label="Ngày dương">
              {Array.from({ length: 31 }, (_, i) => <option key={i} value={i + 1}>{i + 1}</option>)}
            </select>
            <select value={selected.getMonth() + 1} onChange={e => handleSolarChange('month', +e.target.value)} className="field font-semibold" aria-label="Tháng dương">
              {Array.from({ length: 12 }, (_, i) => <option key={i} value={i + 1}>Th {i + 1}</option>)}
            </select>
            <select value={selected.getFullYear()} onChange={e => handleSolarChange('year', +e.target.value)} className="field font-semibold" aria-label="Năm dương">
              {years.map(y => <option key={y} value={y}>{y}</option>)}
            </select>
          </div>
        </fieldset>
        <fieldset>
          <legend className="section-label mb-1.5 flex items-center gap-1.5">
            <Moon size={12} className="text-gold fill-current" /> Âm lịch{currentLunar.isLeap && ' (tháng nhuận)'}
          </legend>
          <div className="grid grid-cols-[1fr_1.2fr_1.4fr] gap-2">
            <select value={currentLunar.lunarDay} onChange={e => handleLunarChange('day', +e.target.value)} className="field font-semibold" aria-label="Ngày âm">
              {Array.from({ length: 30 }, (_, i) => <option key={i} value={i + 1}>{i + 1}</option>)}
            </select>
            <select value={Math.abs(currentLunar.lunarMonth)} onChange={e => handleLunarChange('month', +e.target.value)} className="field font-semibold" aria-label="Tháng âm">
              {Array.from({ length: 12 }, (_, i) => <option key={i} value={i + 1}>Th {i + 1}</option>)}
            </select>
            <select value={currentLunar.lunarYear} onChange={e => handleLunarChange('year', +e.target.value)} className="field font-semibold" aria-label="Năm âm">
              {years.map(y => <option key={y} value={y}>{y}</option>)}
            </select>
          </div>
        </fieldset>
      </div>
    </Sheet>
  );
}
