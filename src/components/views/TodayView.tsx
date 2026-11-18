import { format, getWeek, addDays, subDays, isToday } from 'date-fns';
import { vi } from 'date-fns/locale';
import {
  Clock, Heart, Hammer, Store, Flag, Frown, StickyNote, Plus, Trash2,
  ChevronLeft, ChevronRight, Sparkles, ThumbsUp, ThumbsDown, Minus, Undo2,
  Sun, Moon, SunMoon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '../../lib/utils';
import { CHI_NAMES } from '../../constants/calendar';
import { LunarInfo, UserNote } from '../../types';
import { useSwipe } from '../../hooks/useSwipe';
import { AboutLink } from '../AboutLink';
import { InstallBanner } from '../InstallBanner';
import type { ThemePref } from '../../hooks/useTheme';

const THEME_BUTTON = {
  system: { Icon: SunMoon, label: 'Giao diện: theo hệ thống' },
  light:  { Icon: Sun,     label: 'Giao diện: sáng' },
  dark:   { Icon: Moon,    label: 'Giao diện: tối' },
} as const;

const CHI_EMOJI: Record<string, string> = {
  'Tý': '🐭', 'Sửu': '🐂', 'Dần': '🐯', 'Mão': '🐱',
  'Thìn': '🐲', 'Tỵ': '🐍', 'Ngọ': '🐴', 'Mùi': '🐐',
  'Thân': '🐒', 'Dậu': '🐓', 'Tuất': '🐕', 'Hợi': '🐷',
};

function getChiEmoji(canChi: string): string {
  return CHI_EMOJI[canChi.split(' ')[1] ?? ''] ?? '🗓️';
}

/** "Mồng 1" / "Ngày rằm" for lunar day 1/15, excluding 1/1 and 15/1 (named holidays). */
function getLunarDayLabel(lunarDay: number, lunarMonth: number): string | null {
  const m = Math.abs(lunarMonth);
  if (lunarDay === 1 && m !== 1) return 'Mồng 1';
  if (lunarDay === 15 && m !== 1) return 'Ngày rằm';
  return null;
}

/** "Tý (23h-1h)" → { name: "Tý", hours: "23h–1h" } */
function parseGio(gio: string) {
  const m = gio.match(/^(\S+)\s*\((.*)\)$/);
  return m ? { name: m[1], hours: m[2].replace('-', '–') } : { name: gio, hours: '' };
}

const RATING = {
  good:    { label: 'Ngày tốt',        Icon: ThumbsUp,   tone: 'bg-good-soft text-good' },
  bad:     { label: 'Ngày xấu',        Icon: ThumbsDown, tone: 'bg-bad-soft text-bad' },
  neutral: { label: 'Ngày bình thường', Icon: Minus,      tone: 'bg-surface-2 text-fg-2' },
} as const;

interface Props {
  today: Date;
  now: Date;
  currentTraditionalHour: string;
  lunarInfo: LunarInfo;
  userZodiac: string | null;
  zodiacClash: boolean | null;
  holiday: string | undefined;
  todayNotes: UserNote[];
  newNoteContent: string;
  onNewNoteChange: (v: string) => void;
  onAddNote: () => void;
  onDeleteNote: (id: string) => void;
  onZodiacChange: (zodiac: string) => void;
  onDayChange: (date: Date) => void;
  onOpenAbout: () => void;
  themePref: ThemePref;
  onCycleTheme: () => void;
}

export function TodayView({
  today,
  now,
  currentTraditionalHour,
  lunarInfo,
  userZodiac,
  zodiacClash,
  holiday,
  todayNotes,
  newNoteContent,
  onNewNoteChange,
  onAddNote,
  onDeleteNote,
  onZodiacChange,
  onDayChange,
  onOpenAbout,
  themePref,
  onCycleTheme,
}: Props) {
  const swipe = useSwipe(
    () => onDayChange(addDays(today, 1)),
    () => onDayChange(subDays(today, 1)),
  );
  const viewingToday = isToday(today);
  const lunarDayLabel = getLunarDayLabel(lunarInfo.lunarDay, lunarInfo.lunarMonth);
  const rating = RATING[lunarInfo.rating];
  const theme = THEME_BUTTON[themePref];

  const activities = [
    { key: 'wedding', label: 'Cưới hỏi',   Icon: Heart,  ok: lunarInfo.activities.wedding },
    { key: 'moving',  label: 'Chuyển nhà', Icon: Hammer, ok: lunarInfo.activities.moving },
    { key: 'opening', label: 'Khai trương', Icon: Store,  ok: lunarInfo.activities.opening },
  ];

  return (
    <div className="flex-1 min-h-0 overflow-y-auto overscroll-contain pt-[env(safe-area-inset-top)]">
      <div className="max-w-5xl mx-auto px-4 md:px-8 pt-4 md:pt-8 pb-10">

        {/* Page header */}
        <header className="flex items-end justify-between gap-3 mb-4 md:mb-6">
          <div className="min-w-0">
            <p className="section-label">{viewingToday ? 'Hôm nay' : 'Đang xem'}</p>
            <h1 className="text-2xl md:text-3xl font-extrabold text-fg capitalize truncate">
              {format(today, "EEEE, d 'tháng' M", { locale: vi })}
            </h1>
          </div>
          <div className="flex items-center gap-1 shrink-0">
            {!viewingToday && (
              <button onClick={() => onDayChange(new Date())} className="chip text-brand">
                <Undo2 size={15} /> Hôm nay
              </button>
            )}
            <button onClick={onCycleTheme} className="icon-btn" aria-label={theme.label} title={theme.label}>
              <theme.Icon size={20} />
            </button>
          </div>
        </header>

        <InstallBanner />

        <div className="grid gap-4 md:gap-6 md:grid-cols-[1.15fr_1fr] items-start">

          {/* ───────────── Left column ───────────── */}
          <div className="flex flex-col gap-4">

            {/* Hero: solar + lunar date */}
            <section
              {...swipe}
              className="relative overflow-hidden rounded-[1.75rem] hero"
            >
              {/* decorative moon */}
              <div className="pointer-events-none absolute -right-14 -top-16 w-56 h-56 rounded-full bg-hero-accent/8" aria-hidden="true" />
              <div className="pointer-events-none absolute -right-2 -top-6 w-28 h-28 rounded-full bg-hero-accent/8" aria-hidden="true" />

              <div className="relative flex items-center justify-between px-3 pt-3">
                <button
                  onClick={() => onDayChange(subDays(today, 1))}
                  aria-label="Ngày trước"
                  className="w-10 h-10 rounded-full flex items-center justify-center bg-white/10 hover:bg-white/20 active:scale-95 transition"
                >
                  <ChevronLeft size={20} />
                </button>
                <span className="text-xs font-medium text-white/75">
                  Tuần {getWeek(today, { weekStartsOn: 1, locale: vi })} · {format(today, 'yyyy')}
                </span>
                <button
                  onClick={() => onDayChange(addDays(today, 1))}
                  aria-label="Ngày sau"
                  className="w-10 h-10 rounded-full flex items-center justify-center bg-white/10 hover:bg-white/20 active:scale-95 transition"
                >
                  <ChevronRight size={20} />
                </button>
              </div>

              <div className="relative grid grid-cols-2 px-5 pt-2 pb-5">
                <div className="pr-4 border-r border-white/15">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-white/60">Dương lịch</p>
                  <p className="text-[64px] md:text-[80px] leading-none font-extrabold tracking-tight mt-1 tabular-nums">
                    {format(today, 'd')}
                  </p>
                  <p className="mt-1 text-sm font-semibold">Tháng {format(today, 'M, yyyy')}</p>
                </div>
                <div className="pl-5">
                  <p className="text-[11px] font-semibold uppercase tracking-[0.12em] text-hero-accent/80">Âm lịch</p>
                  <p className="text-[64px] md:text-[80px] leading-none font-extrabold tracking-tight mt-1 text-hero-accent tabular-nums">
                    {lunarInfo.lunarDay}
                  </p>
                  <p className="mt-1 text-sm font-semibold">
                    Tháng {Math.abs(lunarInfo.lunarMonth)}{lunarInfo.isLeap && ' (nhuận)'}
                  </p>
                </div>
              </div>

              <div className="relative flex flex-wrap items-center gap-2 px-5 pb-5">
                <span className="inline-flex items-center gap-1.5 rounded-full bg-white/12 px-3 py-1.5 text-xs font-semibold tabular-nums">
                  <Clock size={13} /> {format(now, 'HH:mm')} · Giờ {currentTraditionalHour}
                </span>
                {lunarDayLabel && (
                  <span className="rounded-full bg-hero-accent text-hero-accent-ink px-3 py-1.5 text-xs font-bold">
                    {lunarDayLabel}
                  </span>
                )}
                {holiday && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-white text-lunar px-3 py-1.5 text-xs font-bold">
                    <Flag size={12} className="fill-current" /> {holiday}
                  </span>
                )}
              </div>
            </section>

            {/* Can Chi */}
            <section className="card grid grid-cols-3 divide-x divide-line py-4">
              {[
                { label: 'Năm', value: lunarInfo.canChiYear },
                { label: 'Tháng', value: lunarInfo.canChiMonth },
                { label: 'Ngày', value: lunarInfo.canChiDay },
              ].map(({ label, value }) => (
                <div key={label} className="flex flex-col items-center text-center gap-1 px-2">
                  <span className="text-2xl leading-none" aria-hidden="true">{getChiEmoji(value)}</span>
                  <span className="section-label mt-1">{label}</span>
                  <span className="text-sm font-bold text-fg">{value}</span>
                </div>
              ))}
            </section>

            {/* Day assessment */}
            <section className="card divide-y divide-line">
              <Row
                icon={<rating.Icon size={18} />}
                tone={rating.tone}
                title={rating.label}
                detail={`Trực ${lunarInfo.truc}`}
              />
              <div className="flex items-center gap-3 px-4 py-3">
                <div className="w-10 h-10 shrink-0 rounded-2xl bg-surface-2 text-fg-2 flex items-center justify-center text-lg">
                  {userZodiac ? CHI_EMOJI[userZodiac] : '🧧'}
                </div>
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-fg">Tuổi của bạn</p>
                  <p className="text-xs text-fg-2">Báo ngày xung tuổi</p>
                </div>
                <select
                  value={userZodiac ?? ''}
                  onChange={e => onZodiacChange(e.target.value)}
                  aria-label="Chọn tuổi"
                  className="field w-24 shrink-0 py-2 font-semibold cursor-pointer"
                >
                  <option value="">Chọn</option>
                  {CHI_NAMES.map(chi => <option key={chi} value={chi}>{chi}</option>)}
                </select>
              </div>
              {zodiacClash && (
                <Row
                  icon={<Frown size={18} />}
                  tone="bg-clash-soft text-clash"
                  title={`Xung với tuổi ${userZodiac}`}
                  detail="Nên cân nhắc việc quan trọng"
                />
              )}
            </section>
          </div>

          {/* ───────────── Right column ───────────── */}
          <div className="flex flex-col gap-4">

            {/* Activities */}
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
              {lunarInfo.truc === 'Trừ' && (
                <p className="mt-3 flex gap-2 rounded-xl bg-surface-2 p-3 text-xs text-fg-2 leading-relaxed">
                  <Sparkles size={14} className="shrink-0 mt-0.5 text-gold" />
                  Ngày Trừ: tốt để dọn dẹp, chữa bệnh, giải toả mâu thuẫn.
                </p>
              )}
            </section>

            {/* Giờ Hoàng Đạo */}
            <section className="card p-4">
              <h2 className="section-label mb-3">Giờ hoàng đạo</h2>
              <div className="grid grid-cols-3 gap-2">
                {lunarInfo.gioHoangDao.map(gio => {
                  const { name, hours } = parseGio(gio);
                  const isNow = viewingToday && name === currentTraditionalHour;
                  return (
                    <div
                      key={gio}
                      className={cn(
                        'rounded-xl px-2 py-2.5 text-center',
                        isNow ? 'bg-brand text-on-brand' : 'bg-brand-soft',
                      )}
                    >
                      <p className={cn('text-sm font-bold', isNow ? 'text-on-brand' : 'text-brand')}>{name}</p>
                      <p className={cn('text-[11px] tabular-nums', isNow ? 'text-on-brand/80' : 'text-fg-2')}>
                        {isNow ? 'Đang diễn ra' : hours}
                      </p>
                    </div>
                  );
                })}
              </div>
            </section>

            {/* Notes */}
            <section className="card p-4">
              <h2 className="section-label mb-3 flex items-center gap-1.5">
                <StickyNote size={13} /> Ghi chú
              </h2>
              {todayNotes.length > 0 && (
                <ul className="flex flex-col gap-2 mb-3">
                  {todayNotes.map(note => (
                    <li key={note.id} className="group flex items-start gap-2 rounded-xl bg-gold-soft px-3 py-2.5">
                      <p className="flex-1 text-sm text-fg whitespace-pre-wrap break-words">{note.content}</p>
                      <button
                        onClick={() => onDeleteNote(note.id)}
                        aria-label="Xoá ghi chú"
                        className="-m-1.5 p-1.5 rounded-lg text-fg-3 hover:text-bad md:opacity-0 md:group-hover:opacity-100 transition"
                      >
                        <Trash2 size={15} />
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              <textarea
                value={newNoteContent}
                onChange={e => onNewNoteChange(e.target.value)}
                placeholder="Thêm ghi chú cho ngày này…"
                rows={2}
                className="field resize-none"
              />
              <button onClick={onAddNote} disabled={!newNoteContent.trim()} className="btn-primary w-full mt-2">
                <Plus size={16} /> Lưu ghi chú
              </button>
            </section>
          </div>
        </div>

        <AboutLink onClick={onOpenAbout} />
      </div>
    </div>
  );
}

function Row({ icon, tone, title, detail }: { icon: ReactNode; tone: string; title: string; detail?: string }) {
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <div className={cn('w-10 h-10 shrink-0 rounded-2xl flex items-center justify-center', tone)}>{icon}</div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-fg">{title}</p>
        {detail && <p className="text-xs text-fg-2">{detail}</p>}
      </div>
    </div>
  );
}
