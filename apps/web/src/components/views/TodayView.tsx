import { isToday } from 'date-fns';
import {
  Heart, Hammer, Store, FaceSlightlyFrowning, StickyNote, Plus, Trash,
  Sparkles, ThumbsUp, ThumbsDown, Minus,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '../../lib/utils';
import { ELEMENTS, type Menh } from '../../lib/menh';
import { CHI_EMOJI } from '../../constants/calendar';
import { GioHoangDao } from '../GioHoangDao';
import { LunarInfo, UserNote } from '../../types';
import { TearOffSheet } from '../TearOffSheet';
import { AboutLink } from '../AboutLink';
import { InstallBanner } from '../InstallBanner';
import type { ThemePref } from '../../hooks/useTheme';

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
  userMenh: Menh | null;
  zodiacClash: boolean | null;
  holiday: string | undefined;
  todayNotes: UserNote[];
  newNoteContent: string;
  onNewNoteChange: (v: string) => void;
  onAddNote: () => void;
  onDeleteNote: (id: string) => void;
  /** Opens "Tiện ích", where the birth date is entered. */
  onOpenSettings: () => void;
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
  userMenh,
  zodiacClash,
  holiday,
  todayNotes,
  newNoteContent,
  onNewNoteChange,
  onAddNote,
  onDeleteNote,
  onOpenSettings,
  onDayChange,
  onOpenAbout,
  themePref,
  onCycleTheme,
}: Props) {
  const viewingToday = isToday(today);
  const rating = RATING[lunarInfo.rating];

  const activities = [
    { key: 'wedding', label: 'Cưới hỏi',   Icon: Heart,  ok: lunarInfo.activities.wedding },
    { key: 'moving',  label: 'Chuyển nhà', Icon: Hammer, ok: lunarInfo.activities.moving },
    { key: 'opening', label: 'Khai trương', Icon: Store,  ok: lunarInfo.activities.opening },
  ];

  // overflow-x-clip: the page-turn strips (TearOffSheet) are cut at 25° and reach past the right
  // edge (scroll width 612px on a 427px screen, 2026-11-22); they must not scroll the page.
  return (
    <div className="flex-1 min-h-0 overflow-y-auto overflow-x-clip overscroll-contain pt-[env(safe-area-inset-top)]">
      <div className="max-w-5xl xl:max-w-7xl mx-auto px-4 rail:px-8 pt-4 rail:pt-8 pb-10">

        {/* Three groups: A date (hero, can chi), B the day (assessment, activities),
            C hours + notes.
            < 840px: one column, A B C stacked.
            lg 840px: A | B side by side, C below across both.
            xl 1200px: A | B | C, three equal columns.
            B and C lay their two cards side by side whenever their own width allows
            (container queries: C from 560px, B from 620px), which covers C at lg and
            tablets in portrait.
            book (unfolded foldable): two equal panes split at the fold; left = header +
            A, right = B + C. The two pane wrappers are display:contents everywhere else.
            grid-cols-1 (minmax(0, 1fr)) matters: an implicit column grows to the widest
            item's min-content, and the header's one-line title made the whole page wider
            than the screen. */}
        <div className="grid grid-cols-1 gap-4 rail:gap-6 lg:grid-cols-[1.15fr_1fr] xl:grid-cols-3 items-start book:grid-cols-2">
        <div className="contents book:flex book:flex-col book:gap-4 book:min-w-0">

        <InstallBanner />

          {/* ───────────── A: the date, as a tear-off calendar sheet ───────────── */}
          <TearOffSheet
            date={today}
            now={now}
            currentTraditionalHour={currentTraditionalHour}
            lunarInfo={lunarInfo}
            holiday={holiday}
            onDayChange={onDayChange}
            themePref={themePref}
            onCycleTheme={onCycleTheme}
          />
        </div>

        <div className="contents book:flex book:flex-col book:gap-4 book:min-w-0">
          {/* ───────────── B: the day ───────────── */}
          <div className="@container">
          <div className="flex flex-col gap-4 @min-[620px]:grid @min-[620px]:grid-cols-2 @min-[620px]:gap-6 @min-[620px]:items-start">

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
                  <p className="text-xs text-fg-2 truncate">
                    {userMenh ? `Mệnh ${ELEMENTS[userMenh.element].name} · ${userMenh.napAm}` : 'Báo ngày xung tuổi'}
                  </p>
                </div>
                <button
                  onClick={onOpenSettings}
                  className="field w-auto shrink-0 py-2 font-semibold cursor-pointer whitespace-nowrap"
                  aria-label={userZodiac ? `Tuổi ${userMenh?.canChi ?? userZodiac}, đổi ngày sinh` : 'Nhập ngày sinh'}
                >
                  {userMenh?.canChi ?? userZodiac ?? 'Nhập ngày sinh'}
                </button>
              </div>
              {zodiacClash && (
                <Row
                  icon={<FaceSlightlyFrowning size={18} />}
                  tone="bg-clash-soft text-clash"
                  title={`Xung với tuổi ${userZodiac}`}
                  detail="Nên cân nhắc việc quan trọng"
                />
              )}
            </section>

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
          </div>
          </div>

          {/* ───────────── C: hours + notes ───────────── */}
          <div className="@container lg:col-span-2 xl:col-span-1">
          <div className="flex flex-col gap-4 @min-[560px]:grid @min-[560px]:grid-cols-[1.15fr_1fr] @min-[560px]:gap-6 @min-[560px]:items-start">

            <GioHoangDao hours={lunarInfo.gioHoangDao} currentHour={viewingToday ? currentTraditionalHour : null} />

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
                        className="-m-1.5 p-1.5 rounded-lg text-fg-3 hover:text-bad pointer-fine:opacity-0 pointer-fine:group-hover:opacity-100 focus-visible:opacity-100 transition"
                      >
                        <Trash size={15} />
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
