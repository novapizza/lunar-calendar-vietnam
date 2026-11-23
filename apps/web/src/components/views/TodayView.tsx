import { isToday } from 'date-fns';
import {
  FaceSlightlyFrowning, ThumbsUp, ThumbsDown, Minus,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { cn } from '../../lib/utils';
import { ELEMENTS, type Menh } from '../../lib/menh';
import { CHI_EMOJI } from '../../constants/calendar';
import { GioHoangDao } from '../GioHoangDao';
import { ActivitiesCard } from '../ActivitiesCard';
import { LunarEvent, LunarInfo } from '../../types';
import { DayEvent, eventsOnDay, isoOf } from '../../services/personalEvents';
import { DayEventsCard } from '../DayEventsCard';
import { TearOffSheet } from '../TearOffSheet';
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
  /** A commemorative day or festival, shown on the sheet when there is no holiday. */
  observance: string | undefined;
  /** The user's own events (TearOffSheet names the next sheet's day with them). */
  personalEvents: LunarEvent[];
  onOpenEvent: (item: DayEvent) => void;
  onAddEvent: (day: string) => void;
  /** Opens "Tiện ích", where the birth date is entered. */
  onOpenSettings: () => void;
  onDayChange: (date: Date) => void;
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
  observance,
  personalEvents,
  onOpenEvent,
  onAddEvent,
  onOpenSettings,
  onDayChange,
  themePref,
  onCycleTheme,
}: Props) {
  const viewingToday = isToday(today);
  const rating = RATING[lunarInfo.rating];

  // overflow-x-clip: the page-turn strips (TearOffSheet) are cut at 25° and reach past the right
  // edge (scroll width 612px on a 427px screen, 2026-11-22); they must not scroll the page.
  return (
    <div className="flex-1 min-h-0 overflow-y-auto overflow-x-clip overscroll-contain pt-[env(safe-area-inset-top)]">
      <div className="max-w-5xl xl:max-w-7xl mx-auto px-4 rail:px-8 pt-4 rail:pt-8 pb-10 lg:pb-0 book:pb-0">

        {/* Two parts: A the date (the tear-off sheet) and the day's cards (assessment,
            events, activities, giờ hoàng đạo) in one flow.
            < 840px: one column. lg 840px: A | cards. xl 1200px: A | cards over two thirds.
            The cards are CSS columns at least 20rem wide: one column where only that fits,
            two where there is room, balanced by the browser, so no card waits in a row of its
            own under the sheet (giờ hoàng đạo and notes did at lg, leaving a gap beside them).
            book (unfolded foldable): two equal panes split at the fold; left = A, right = the
            cards.
            In both two-pane layouts (book, lg) the date stays put and only the cards scroll: the
            pane wrapper is sticky at the page's top padding (the tear-off sheet shrinks its
            number to fit the window's height, TearOffSheet useFitToPane). In one column the
            wrapper is display:contents.
            grid-cols-1 (minmax(0, 1fr)) matters: an implicit column grows to the widest
            item's min-content, and a one-line title once made the whole page wider than the
            screen. */}
        <div className="grid grid-cols-1 gap-4 rail:gap-6 lg:grid-cols-[1.15fr_1fr] xl:grid-cols-[1fr_2fr] items-start book:grid-cols-2">
        <div className="contents lg:sticky lg:top-[calc(env(safe-area-inset-top)+2rem)] lg:flex lg:flex-col lg:gap-6 lg:min-w-0 book:sticky book:top-[calc(env(safe-area-inset-top)+1rem)] book:flex book:flex-col book:gap-4 book:min-w-0">

        <InstallBanner />

          {/* ───────────── A: the date, as a tear-off calendar sheet ───────────── */}
          <TearOffSheet
            date={today}
            now={now}
            currentTraditionalHour={currentTraditionalHour}
            lunarInfo={lunarInfo}
            holiday={holiday}
            observance={observance}
            personalEvents={personalEvents}
            onDayChange={onDayChange}
            themePref={themePref}
            onCycleTheme={onCycleTheme}
          />
        </div>

        {/* ───────────── The day's cards, one flow in balanced columns ───────────── */}
        {/* The page's bottom padding sits in this column in two-pane layouts: under the grid it made
            the page scroll past the sticky date column's end and carried the sheet up with it (a
            sticky box stays inside its grid). App info is in Tools only. */}
        <div className="min-w-0 lg:pb-10 book:pb-10">
        <div className="columns-[20rem] gap-4 rail:gap-6 [&>*]:mb-4 rail:[&>*]:mb-6 [&>*]:break-inside-avoid">

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

            <DayEventsCard items={eventsOnDay(personalEvents, isoOf(today))} onOpen={onOpenEvent} onAdd={() => onAddEvent(isoOf(today))} />

            <ActivitiesCard info={lunarInfo} />

            <GioHoangDao hours={lunarInfo.gioHoangDao} currentHour={viewingToday ? currentTraditionalHour : null} />
        </div>
        </div>
        </div>
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
