import { useMemo } from 'react';
import {
  format, addMonths, subMonths,
  startOfMonth, endOfMonth,
  startOfWeek, endOfWeek,
  eachDayOfInterval,
  isSameMonth, isSameDay, isToday, getWeek,
} from 'date-fns';
import { vi } from 'date-fns/locale';
import {
  ChevronLeft, ChevronRight, ChevronDown,
  Heart, Hammer, Store, StickyNote, Flag, FaceSlightlyFrowning,
  Search, CalendarSearch, Download, Plus, Trash, ThumbsUp, ThumbsDown, Minus, Undo2,
  Sparkles, Landmark, Moon,
} from 'lucide-react';
import { cn } from '../../lib/utils';
import { WEEK_DAYS, CHI_NAMES } from '../../constants/calendar';
import { getLunarInfo, getHoliday, getObservances, getLunarDayLabel, LUNAR_MONTH_NAMES } from '../../services/lunarService';
import { LunarInfo, UserNote } from '../../types';
import { useSwipe } from '../../hooks/useSwipe';
import { GioHoangDao } from '../GioHoangDao';

interface Props {
  currentDate: Date;
  /** Chi of the hour now (highlighted in Giờ hoàng đạo when today is selected). */
  currentTraditionalHour: string;
  selectedDate: Date;
  notes: UserNote[];
  userZodiac: string | null;
  selectedLunarInfo: LunarInfo;
  holiday: string | undefined;
  selectedDateNotes: UserNote[];
  newNoteContent: string;
  onNewNoteChange: (v: string) => void;
  onAddNote: () => void;
  onDeleteNote: (id: string) => void;
  /** Show the month of `date`; the selected day moves into that month too. */
  onShowMonth: (date: Date) => void;
  onSelectedDateChange: (date: Date) => void;
  onOpenQuickPicker: () => void;
  /** Month/year grid picker, opened from the month title. */
  onOpenMonthPicker: () => void;
  onOpenAuspiciousPicker: () => void;
  onOpenLunarEventModal: () => void;
}

const RATING = {
  good:    { label: 'Ngày tốt',        Icon: ThumbsUp,   tone: 'bg-good-soft text-good' },
  bad:     { label: 'Ngày xấu',        Icon: ThumbsDown, tone: 'bg-bad-soft text-bad' },
  neutral: { label: 'Bình thường',     Icon: Minus,      tone: 'bg-surface-2 text-fg-2' },
} as const;

/**
 * Day-cell marker dot. One size everywhere: cells are only ~40px wide at 320px
 * and ~43px in the md two-column layout, so the dots must stay this small.
 */
const DOT = 'block w-[5px] h-[5px] rounded-full';
/** Places dots right after a number without moving the number off-centre. */
const DOT_SLOT = 'absolute left-full top-1/2 -translate-y-1/2 ml-[2px]';

function clashes(userZodiac: string | null, info: LunarInfo): boolean {
  if (!userZodiac) return false;
  const userIdx = CHI_NAMES.indexOf(userZodiac as typeof CHI_NAMES[number]);
  const dayIdx  = CHI_NAMES.indexOf(info.canChiDay.split(' ')[1] as typeof CHI_NAMES[number]);
  return userIdx !== -1 && dayIdx !== -1 && Math.abs(userIdx - dayIdx) === 6;
}

export function CalendarView({
  currentTraditionalHour,
  currentDate,
  selectedDate,
  notes,
  userZodiac,
  selectedLunarInfo,
  holiday,
  selectedDateNotes,
  newNoteContent,
  onNewNoteChange,
  onAddNote,
  onDeleteNote,
  onShowMonth,
  onSelectedDateChange,
  onOpenQuickPicker,
  onOpenMonthPicker,
  onOpenAuspiciousPicker,
  onOpenLunarEventModal,
}: Props) {
  const monthStart = startOfMonth(currentDate);

  const weeks = useMemo(() => {
    const days = eachDayOfInterval({
      start: startOfWeek(monthStart, { weekStartsOn: 1 }),
      end:   endOfWeek(endOfMonth(monthStart), { weekStartsOn: 1 }),
    });
    const noteDates = new Set(notes.map(n => n.date));
    const cells = days.map(day => {
      const lunar = getLunarInfo(day);
      return {
        day,
        lunar,
        holiday: getHoliday(lunar.lunarDay, lunar.lunarMonth, day.getDate(), day.getMonth() + 1, day.getFullYear()),
        hasNote: noteDates.has(format(day, 'yyyy-MM-dd')),
        clash: clashes(userZodiac, lunar),
      };
    });
    return Array.from({ length: cells.length / 7 }, (_, i) => cells.slice(i * 7, i * 7 + 7));
  }, [monthStart.getTime(), notes, userZodiac]);

  // "Tháng 9 – 10 âm lịch · Bính Ngọ" for the header subtitle
  const lunarSubtitle = useMemo(() => {
    const first = getLunarInfo(monthStart);
    const last  = getLunarInfo(endOfMonth(monthStart));
    const a = Math.abs(first.lunarMonth), b = Math.abs(last.lunarMonth);
    const months = a === b ? `Tháng ${a}` : `Tháng ${a} – ${b}`;
    return `${months} âm lịch · ${last.canChiYear}`;
  }, [monthStart.getTime()]);

  const swipe = useSwipe(
    () => onShowMonth(addMonths(currentDate, 1)),
    () => onShowMonth(subMonths(currentDate, 1)),
  );

  const rating = RATING[selectedLunarInfo.rating];
  // Everything the Today page shows for a day, so the panel is the same day in short: its
  // commemorative days and festivals, and "Mồng 1" / "Ngày rằm" when the day has no name.
  const observances = getObservances(selectedLunarInfo.lunarDay, selectedLunarInfo.lunarMonth, selectedDate);
  const lunarDayLabel = holiday || observances.length ? null : getLunarDayLabel(selectedLunarInfo.lunarDay, selectedLunarInfo.lunarMonth);
  const lunarMonthName = LUNAR_MONTH_NAMES[Math.abs(selectedLunarInfo.lunarMonth) - 1];
  const selectedClash = clashes(userZodiac, selectedLunarInfo);
  const showingThisMonth = isSameMonth(currentDate, new Date());
  const goToToday = () => onSelectedDateChange(new Date());

  return (
    // book (unfolded foldable): two equal panes split at the fold. Left = header + month
    // grid, right = the selected-day panel, each scrolling on its own.
    <div className="flex-1 min-h-0 overflow-y-auto overscroll-contain wide:overflow-hidden wide:flex wide:flex-col book:overflow-hidden book:grid book:grid-cols-2 book:grid-rows-[auto_minmax(0,1fr)]">

      {/* ───────── Header ───────── */}
      <header className="sticky top-0 z-10 bg-bg/90 backdrop-blur-md pt-[env(safe-area-inset-top)] wide:static wide:shrink-0 book:static book:col-start-1 book:row-start-1">
        <div className="max-w-6xl xl:max-w-7xl mx-auto px-4 rail:px-8 pt-3 rail:pt-6 pb-3 book:pr-2">
          <div className="flex items-center gap-2">
            <button
              onClick={onOpenMonthPicker}
              className="flex-1 min-w-0 text-left rounded-xl -mx-1 px-1 py-0.5 hover:bg-surface-2 transition-colors"
              aria-label="Chọn tháng, năm"
            >
              {/* One line always: 20px at 320px wide, 24px from 375px, 30px on md+. */}
              <span className="flex items-center gap-1 whitespace-nowrap text-[clamp(1.25rem,6.4vw,1.5rem)] book:text-xl rail:text-3xl font-extrabold text-fg">
                Tháng {format(currentDate, 'M')}
                <span className="text-fg-3 font-bold">{format(currentDate, 'yyyy')}</span>
                <ChevronDown size={20} className="text-fg-3 mt-1 shrink-0" />
              </span>
              <span className="block text-xs rail:text-sm text-fg-2 truncate">{lunarSubtitle}</span>
            </button>
            {/* Header "Hôm nay" only from md (600px) up; on phones it sits first in the chip row
                below, so the month title keeps the full width. Wrapper divs carry the
                visibility because btn-soft/chip set their own display. */}
            {!showingThisMonth && (
              <div className="hidden rail:block shrink-0">
                <button onClick={goToToday} className="btn-soft px-3 py-2">
                  Hôm nay
                </button>
              </div>
            )}
            <button onClick={() => onShowMonth(subMonths(currentDate, 1))} className="icon-btn" aria-label="Tháng trước">
              <ChevronLeft size={20} />
            </button>
            <button onClick={() => onShowMonth(addMonths(currentDate, 1))} className="icon-btn" aria-label="Tháng sau">
              <ChevronRight size={20} />
            </button>
          </div>

          <div className="flex gap-2 mt-3 overflow-x-auto no-scrollbar -mx-4 px-4 book:mr-0 book:pr-0 rail:mx-0 rail:px-0">
            {!showingThisMonth && (
              <div className="rail:hidden shrink-0">
                <button onClick={goToToday} className="chip text-brand">
                  <Undo2 size={15} /> Hôm nay
                </button>
              </div>
            )}
            <button onClick={onOpenAuspiciousPicker} className="chip">
              <Search size={15} className="text-brand" /> Tìm ngày tốt
            </button>
            <button onClick={onOpenQuickPicker} className="chip">
              <CalendarSearch size={15} className="text-brand" /> Đổi ngày
            </button>
            <button onClick={onOpenLunarEventModal} className="chip">
              <Download size={15} className="text-brand" /> Xuất lịch
            </button>
          </div>
        </div>
      </header>

      {/* ───────── Body ───────── */}
      <div className="max-w-6xl xl:max-w-7xl w-full mx-auto px-4 rail:px-8 pb-10 book:contents wide:pb-6 wide:flex-1 wide:min-h-0 wide:flex wide:gap-6">

        {/* Month grid */}
        {/* Phones: 8px page gutter and 4px padding (instead of 16 + 8) so day cells get
            ~3.4px wider. From 600px `cal-fit` caps the width by the window height so the
            whole month is visible. stack: centred. wide (two panes): takes the free width
            up to that cap, then the day panel grows; the card hugs the grid. */}
        <section className="card cal-fit -mx-2 rail:mx-0 p-1 rail:p-3 book:mx-2 book:mb-2 book:col-start-1 book:row-start-2 book:self-start book:max-h-[calc(100%-0.5rem)] book:overflow-y-auto stack:mx-auto wide:flex-[999_1_0%] wide:min-w-0 wide:min-h-0 wide:self-start wide:max-h-full wide:overflow-y-auto no-scrollbar" {...swipe}>
          {/* Week-number column only from xl: narrower two-pane windows leave the grid ~400px. */}
          <div className="grid grid-cols-7 xl:grid-cols-[28px_repeat(7,minmax(0,1fr))] gap-0.5 rail:max-xl:gap-1 xl:gap-1.5">
            <div className="hidden xl:block" />
            {WEEK_DAYS.map(d => (
              <div key={d} className={cn('py-2 text-center text-xs font-semibold', d === 'CN' ? 'text-lunar' : 'text-fg-3')}>
                {d}
              </div>
            ))}

            {weeks.map(week => [
              <div
                key={`w${week[0].day.getTime()}`}
                className="hidden xl:flex items-center justify-center text-[10px] font-semibold text-fg-3 tabular-nums"
                title="Tuần"
              >
                {getWeek(week[0].day, { weekStartsOn: 1, locale: vi })}
              </div>,
              ...week.map(({ day, lunar, holiday: dayHoliday, hasNote, clash }) => {
                const selected = isSameDay(day, selectedDate);
                const today    = isToday(day);
                const inMonth  = isSameMonth(day, monthStart);
                const sunday   = day.getDay() === 0;
                const firstDay = lunar.lunarDay === 1;
                const special  = firstDay || lunar.lunarDay === 15 || !!dayHoliday;
                return (
                  <button
                    key={day.getTime()}
                    onClick={() => onSelectedDateChange(day)}
                    aria-label={format(day, "d 'tháng' M", { locale: vi })}
                    aria-pressed={selected}
                    className={cn(
                      // Square cell; text sizes follow the cell's own width (container query),
                      // since the width depends on the layout, not only the screen.
                      // The whole cell carries the state: today = soft fill,
                      // selected = brand outline (both when they coincide).
                      '@container group relative flex flex-col items-center justify-center gap-0.5 aspect-square rounded-xl transition-colors',
                      today && 'bg-brand-soft',
                      selected && 'ring-2 ring-inset ring-brand',
                      !selected && !today && 'hover:bg-surface-2',
                      !inMonth && 'opacity-35',
                    )}
                  >
                    {/* Solar day; note dot sits to its right without shifting the number. */}
                    <span
                      className={cn(
                        'relative text-[17px] @min-[64px]:text-xl @min-[88px]:text-2xl leading-none font-semibold tabular-nums',
                        today ? 'text-brand font-bold'
                          : sunday ? 'text-lunar' : 'text-fg',
                        selected && 'font-bold',
                      )}
                    >
                      {format(day, 'd')}
                      {hasNote && (
                        <span className={cn(DOT_SLOT, DOT, 'bg-gold')} aria-hidden="true" />
                      )}
                    </span>
                    {/* Lunar day; day-quality and zodiac-clash dots (both from the lunar day) to its right. */}
                    <span
                      className={cn(
                        'relative text-[11px] @min-[64px]:text-xs @min-[88px]:text-sm leading-none tabular-nums',
                        special ? 'text-lunar font-bold' : 'text-fg-3 font-medium',
                        // "1/11" + dot is too wide to hang the dot off a centred number:
                        // centre the whole group instead so both sides keep equal room.
                        firstDay && 'inline-flex items-center',
                      )}
                    >
                      {firstDay ? `${lunar.lunarDay}/${Math.abs(lunar.lunarMonth)}` : lunar.lunarDay}
                      {(lunar.rating !== 'neutral' || clash) && (
                        // Two dots stack vertically so they stay inside a ~40px cell.
                        <span
                          className={cn(firstDay ? 'ml-[2px]' : DOT_SLOT, 'flex flex-col gap-px')}
                          aria-hidden="true"
                        >
                          {lunar.rating === 'good' && <span className={cn(DOT, 'bg-good')} />}
                          {lunar.rating === 'bad'  && <span className={cn(DOT, 'bg-bad/70')} />}
                          {clash && <span className={cn(DOT, 'bg-clash')} />}
                        </span>
                      )}
                    </span>
                    {dayHoliday && (
                      <span className="hidden @min-[88px]:block w-full px-1.5 mt-0.5 text-[10px] font-semibold text-lunar leading-tight truncate text-center">
                        {dayHoliday}
                      </span>
                    )}
                  </button>
                );
              }),
            ])}
          </div>

          {/* Legend */}
          <div className="flex flex-wrap justify-center gap-x-4 gap-y-1 pt-3 pb-1 text-[11px] text-fg-2">
            <Legend className="bg-good" label="Ngày tốt" />
            <Legend className="bg-bad/70" label="Ngày xấu" />
            <Legend className="bg-gold" label="Ghi chú" />
            {userZodiac && <Legend className="bg-clash" label="Xung tuổi" />}
          </div>
        </section>

        {/* Selected-day panel. Phones: cards stacked. book: the whole right pane. stack: two columns (day card on the
            left, hours + notes on the right). wide: a column beside the calendar, 18rem
            wide and growing to 32rem once the calendar has reached its cap. */}
        <aside className="mt-4 flex flex-col gap-3 book:mt-0 book:col-start-2 book:row-start-1 book:row-span-2 book:overflow-y-auto book:px-2 book:pb-4 book:pt-[calc(env(safe-area-inset-top)+0.75rem)] stack:grid stack:grid-cols-2 stack:items-start stack:gap-4 wide:mt-0 wide:flex-[1_0_18rem] wide:max-w-[32rem] wide:min-w-0 wide:overflow-y-auto no-scrollbar">
          <section className="card p-4 stack:row-span-2">
            <p className="section-label">Ngày đã chọn · Tuần {getWeek(selectedDate, { weekStartsOn: 1, locale: vi })}</p>
            <div className="flex items-end justify-between gap-3 mt-1">
              <div>
                <p className="text-xl font-extrabold text-fg capitalize">
                  {format(selectedDate, "EEEE, d/M", { locale: vi })}
                </p>
                <p className="text-sm text-fg-2">
                  Âm lịch <span className="font-bold text-lunar">{selectedLunarInfo.lunarDay}/{Math.abs(selectedLunarInfo.lunarMonth)}</span>
                  {' · tháng '}{lunarMonthName}{selectedLunarInfo.isLeap && ' nhuận'}
                </p>
              </div>
              <span className={cn('shrink-0 inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-bold', rating.tone)}>
                <rating.Icon size={13} /> {rating.label}
              </span>
            </div>
            <dl className="mt-3 grid grid-cols-3 gap-2 text-center">
              {[
                { label: 'Năm', value: selectedLunarInfo.canChiYear },
                { label: 'Tháng', value: selectedLunarInfo.canChiMonth },
                { label: 'Ngày', value: selectedLunarInfo.canChiDay },
              ].map(({ label, value }) => (
                <div key={label} className="rounded-xl bg-surface-2 px-1 py-2">
                  <dt className="text-[10px] font-semibold uppercase tracking-wider text-fg-3">{label}</dt>
                  <dd className="text-xs font-bold text-fg mt-0.5">{value}</dd>
                </div>
              ))}
            </dl>
            <p className="mt-2 text-xs text-fg-2">Trực <span className="font-semibold text-fg">{selectedLunarInfo.truc}</span></p>

            {(holiday || observances.length > 0 || lunarDayLabel || selectedClash) && (
              <div className="mt-3 flex flex-col gap-2">
                {holiday && (
                  <p className="flex items-center gap-2 rounded-xl bg-lunar-soft px-3 py-2 text-sm font-semibold text-lunar">
                    <Flag size={14} className="shrink-0 fill-current" /> {holiday}
                  </p>
                )}
                {observances.map(o => {
                  const Icon = o.kind === 'festival' ? Landmark : Sparkles;
                  return (
                    <p key={o.title} className="flex items-center gap-2 rounded-xl bg-gold-soft px-3 py-2 text-sm font-semibold text-gold">
                      <Icon size={14} className="shrink-0" />
                      <span className="min-w-0">
                        {o.title}
                        <span className="font-medium opacity-75"> · {o.kind === 'festival' ? 'Lễ hội' : 'Kỷ niệm'}</span>
                      </span>
                    </p>
                  );
                })}
                {lunarDayLabel && (
                  <p className="flex items-center gap-2 rounded-xl bg-surface-2 px-3 py-2 text-sm font-semibold text-lunar">
                    <Moon size={14} className="shrink-0" /> {lunarDayLabel}
                  </p>
                )}
                {selectedClash && (
                  <p className="flex items-center gap-2 rounded-xl bg-clash-soft px-3 py-2 text-sm font-semibold text-clash">
                    <FaceSlightlyFrowning size={14} className="shrink-0" />
                    <span className="min-w-0">Xung tuổi {userZodiac}<span className="font-medium opacity-75"> · nên cân nhắc việc quan trọng</span></span>
                  </p>
                )}
              </div>
            )}

            {/* The three activities, good or not (Today's "Việc nên làm"), one tile each. */}
            <ul className="mt-3 grid grid-cols-3 gap-2" aria-label="Việc nên làm">
              {([
                { key: 'wedding', label: 'Cưới hỏi', Icon: Heart },
                { key: 'moving', label: 'Chuyển nhà', Icon: Hammer },
                { key: 'opening', label: 'Khai trương', Icon: Store },
              ] as const).map(({ key, label, Icon }) => {
                const ok = selectedLunarInfo.activities[key];
                return (
                  <li key={key} className={cn('flex flex-col items-center gap-1 rounded-xl px-1 py-2 text-center', ok ? 'bg-good-soft text-good' : 'bg-surface-2 text-fg-3')}>
                    <Icon size={16} />
                    <span className={cn('text-xs font-semibold', ok ? 'text-fg' : 'text-fg-2')}>{label}</span>
                    <span className="text-[10px] font-bold">{ok ? 'Tốt' : 'Không hợp'}</span>
                  </li>
                );
              })}
            </ul>
            {selectedLunarInfo.truc === 'Trừ' && (
              <p className="mt-2 flex gap-2 rounded-xl bg-surface-2 p-3 text-xs text-fg-2 leading-relaxed">
                <Sparkles size={14} className="shrink-0 mt-0.5 text-gold" />
                Ngày Trừ: tốt để dọn dẹp, chữa bệnh, giải toả mâu thuẫn.
              </p>
            )}
          </section>

          <GioHoangDao
            hours={selectedLunarInfo.gioHoangDao}
            currentHour={isToday(selectedDate) ? currentTraditionalHour : null}
          />

          <section className="card p-4">
            <p className="section-label mb-2 flex items-center gap-1.5"><StickyNote size={13} /> Ghi chú</p>
            {selectedDateNotes.length > 0 && (
              <ul className="flex flex-col gap-2 mb-2">
                {selectedDateNotes.map(note => (
                  <li key={note.id} className="group flex items-start gap-2 rounded-xl bg-gold-soft px-3 py-2">
                    <p className="flex-1 text-sm text-fg whitespace-pre-wrap break-words">{note.content}</p>
                    <button
                      onClick={() => onDeleteNote(note.id)}
                      aria-label="Xoá ghi chú"
                      className="-m-1.5 p-1.5 rounded-lg text-fg-3 hover:text-bad pointer-fine:opacity-0 pointer-fine:group-hover:opacity-100 focus-visible:opacity-100 transition"
                    >
                      <Trash size={14} />
                    </button>
                  </li>
                ))}
              </ul>
            )}
            <textarea
              value={newNoteContent}
              onChange={e => onNewNoteChange(e.target.value)}
              placeholder="Thêm ghi chú…"
              rows={2}
              className="field resize-none"
            />
            <button onClick={onAddNote} disabled={!newNoteContent.trim()} className="btn-primary w-full mt-2">
              <Plus size={16} /> Lưu
            </button>
          </section>
        </aside>
      </div>
    </div>
  );
}

function Legend({ className, label }: { className: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className={cn('w-1.5 h-1.5 rounded-full', className)} /> {label}
    </span>
  );
}
