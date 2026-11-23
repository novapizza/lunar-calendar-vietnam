import { useLayoutEffect, useRef, useState, type CSSProperties, type TouchEvent } from 'react';
import { format, getWeek, addDays, subDays, isToday } from 'date-fns';
import { vi } from 'date-fns/locale';
import { ChevronLeft, ChevronRight, Undo2, Sun, Moon, SunMoon } from 'lucide-react';
import { cn } from '../lib/utils';
import { SheetPeel } from './SheetPeel';
import { useSwipe } from '../hooks/useSwipe';
import type { LunarInfo } from '../types';
import { LUNAR_MONTH_NAMES, getLunarDayLabel, getLunarInfo, getHoliday, getObservance } from '../services/lunarService';
import type { ThemePref } from '../hooks/useTheme';

const THEME_BUTTON = {
  system: { Icon: SunMoon, label: 'Giao diện: theo hệ thống' },
  light:  { Icon: Sun,     label: 'Giao diện: sáng' },
  dark:   { Icon: Moon,    label: 'Giao diện: tối' },
} as const;

/** What one sheet shows. */
interface Face {
  date: Date;
  lunarInfo: LunarInfo;
  holiday: string | undefined;
  /** Commemorative day or festival: printed on the sheet like a holiday, but not red. */
  observance: string | undefined;
}

interface Props {
  date: Date;
  now: Date;
  currentTraditionalHour: string;
  lunarInfo: LunarInfo;
  holiday: string | undefined;
  observance: string | undefined;
  onDayChange: (date: Date) => void;
  themePref: ThemePref;
  onCycleTheme: () => void;
}

/**
 * The Today page's date as one sheet of a Vietnamese tear-off wall calendar (lịch bóc):
 * a lacquer sheet hung on two brass rings, framed by a double gold hairline, the solar day in
 * large gold-foil serif (Sundays and holidays, red on a paper calendar, get a gold seal on the
 * weekday), and the lunar date in a gold "moon" next to the can chi. Two sheets peek out below,
 * so it reads as a pad. (A 3D pad that thinned through the year was tried and dropped,
 * 2026-11-22: hard to read.)
 *
 * The page turns like paper (SheetPeel, WebGL): a finger on the bottom edge curls it and the
 * paper follows it (a sideways swipe higher up steps a day, as the arrows); a flick or a pull past half the sheet tears it off, uncovering the next day.
 * The arrows turn it from the bottom-right corner; an earlier day brings a sheet back down.
 */
/** yyyymmdd of a local date, to compare days whatever their time of day. */
const dayKey = (d: Date) => d.getFullYear() * 10000 + (d.getMonth() + 1) * 100 + d.getDate();

export function TearOffSheet({ date, now, currentTraditionalHour, lunarInfo, holiday, observance, onDayChange, themePref, onCycleTheme }: Props) {
  // The sheet on show, and the turn in progress (state adjusted during render, React's pattern
  // for "previous prop"; safe under StrictMode).
  const face: Face = { date, lunarInfo, holiday, observance };
  const [shown, setShown] = useState<Face>(face);
  const [turn, setTurn] = useState<{ from: Face; forward: boolean; id: number } | null>(null);
  // Compared by calendar day: the same day as a new Date (another time of day, e.g. tapping
  // "Hôm nay" again) is not a turn.
  // A sheet torn off by hand has already turned: its day change plays no turn.
  const [torn, setTorn] = useState<number | null>(null);
  if (dayKey(shown.date) !== dayKey(date)) {
    if (torn === dayKey(date)) setTorn(null);
    else setTurn({ from: shown, forward: dayKey(date) > dayKey(shown.date), id: (turn?.id ?? 0) + 1 });
    setShown(face);
  }
  // While a finger holds the sheet, the next day lies underneath.
  const [peek, setPeek] = useState<Face | null>(null);
  const paperRef = useRef<HTMLDivElement>(null);
  const sourceRef = useRef<HTMLDivElement>(null);
  const nextFace = (): Face => {
    const d = addDays(date, 1);
    const info = getLunarInfo(d);
    return {
      date: d,
      lunarInfo: info,
      holiday: getHoliday(info.lunarDay, info.lunarMonth, d.getDate(), d.getMonth() + 1, d.getFullYear()),
      observance: getObservance(info.lunarDay, info.lunarMonth, d),
    };
  };
  // What the texture of the current sheet shows: day, clock and palette (the hero colours).
  const rootStyle = typeof document !== 'undefined' ? getComputedStyle(document.documentElement) : null;
  const faceKey = [dayKey(date), currentTraditionalHour, format(now, 'HH:mm'), holiday, observance,
    rootStyle?.getPropertyValue('--hero-via'), rootStyle?.getPropertyValue('--hero-accent')].join('|');
  const numberMax = useFitToPane();

  const lunarMonth = Math.abs(lunarInfo.lunarMonth);
  const theme = THEME_BUTTON[themePref];
  const strip = 'w-10 h-10 rounded-full flex items-center justify-center text-white/85 hover:bg-white/10 active:scale-95 transition';
  // The arrows always step from the selected day, also while a turn shows an older sheet
  // underneath, and work during a turn (pressing again starts the next turn at once).
  const nav = { prev: () => onDayChange(subDays(date, 1)), next: () => onDayChange(addDays(date, 1)) };
  // Above the bottom zone (SheetPeel's) the sheet does not curl: a sideways swipe steps a day
  // there, left to the next one, right to the previous one, with the arrows' turn.
  const swipe = useSwipe(() => nav.next(), () => nav.prev());
  const swipeAbove = {
    onTouchStart: (e: TouchEvent) => { if (!(e.target as Element).closest('[data-peel-zone]')) swipe.onTouchStart(e); },
    onTouchEnd: swipe.onTouchEnd,
  };
  const sheet = (f: Face, interactive: boolean) => (
    <SheetFace face={f} now={now} currentTraditionalHour={currentTraditionalHour} nav={interactive ? nav : undefined} />
  );

  return (
    <div
      ref={numberMax.ref}
      className="relative pb-3"
      style={numberMax.px ? ({ '--tear-number-max': `${numberMax.px}px` } as CSSProperties) : undefined}
    >
      {/* The pad: two sheets under the current one. */}
      <div className="absolute inset-x-4 bottom-0 top-8 rounded-b-[1.25rem] border border-hero-accent/15 bg-hero-to opacity-70" aria-hidden="true" />
      <div className="absolute inset-x-2 bottom-1.5 top-8 rounded-b-[1.25rem] border border-hero-accent/20 bg-hero-via shadow-[var(--shadow-card)]" aria-hidden="true" />

      {/* No overflow clip here: the turning page leaves the sheet's box. The binding and the
          paper round their own corners. */}
      <section
        aria-label={`Ngày ${format(date, 'd/M/yyyy')}, âm lịch ${lunarInfo.lunarDay}/${lunarMonth}`}
        className="@container relative rounded-[1.25rem] text-white shadow-[var(--shadow-float)]"
      >
        {/* Binding: a darker lacquer strip edged in gold. */}
        <div className="relative flex h-12 items-center justify-between overflow-hidden rounded-t-[1.25rem] bg-gradient-to-br from-hero-from via-hero-via to-hero-to px-1.5">
          <div className="absolute inset-0 bg-black/25" aria-hidden="true" />
          <button onClick={onCycleTheme} className={cn(strip, 'relative')} aria-label={theme.label} title={theme.label}>
            <theme.Icon size={18} />
          </button>
          {/* The year on the binding, in larger print; the week moved to the sheet's top line. */}
          <span className="relative pl-[0.3em] text-[15px] font-bold tracking-[0.3em] text-hero-accent tabular-nums">
            {format(date, 'yyyy')}
          </span>
          {isToday(date) ? (
            <span className="w-10" aria-hidden="true" />
          ) : (
            <button onClick={() => onDayChange(new Date())} className={cn(strip, 'relative')} aria-label="Về hôm nay" title="Về hôm nay">
              <Undo2 size={18} />
            </button>
          )}
          <div className="absolute inset-x-0 bottom-0 h-px bg-hero-accent/70" aria-hidden="true" />
        </div>

        {/* The sheet: underneath, the live sheet (the next day while a finger holds the sheet or
            a turn goes forward, the old one going back) with the rings over it; a hidden copy of
            the current day that SheetPeel paints into its texture; SheetPeel's WebGL overlay. */}
        <div ref={paperRef} className="relative" {...swipeAbove}>
          {sheet(peek ?? (turn && !turn.forward ? turn.from : face), true)}
          <span className="tear-ring left-[26%]" style={{ top: 'calc(30px - 3rem)' }} aria-hidden="true" />
          <span className="tear-ring right-[26%]" style={{ top: 'calc(30px - 3rem)' }} aria-hidden="true" />
          <div ref={sourceRef} className="pointer-events-none absolute inset-x-0 top-0 -z-10 opacity-0" aria-hidden="true" inert>
            <SheetFace face={face} now={now} currentTraditionalHour={currentTraditionalHour} nav={nav} />
          </div>
          <SheetPeel
            paperRef={paperRef}
            sourceRef={sourceRef}
            faceKey={faceKey}
            turn={turn}
            onTurnDone={() => setTurn(null)}
            onPeekStart={() => setPeek(nextFace())}
            onPeekEnd={tornOff => {
              if (tornOff) {
                const next = addDays(date, 1);
                setTorn(dayKey(next));
                onDayChange(next);
              }
              setPeek(null);
            }}
          />
        </div>

      </section>
    </div>
  );
}

/** The two-pane layouts of TodayView (book, and lg = 840px up), where the sheet has a column of its own. */
const TWO_PANES = '(min-width: 52.5rem), (min-width: 37.5rem) and (max-width: 52.4375rem) and (min-aspect-ratio: 3/4) and (max-aspect-ratio: 4/3)';

/**
 * In the two-pane layouts the whole sheet should be on screen without scrolling. A wide, short
 * pane (Galaxy Z Fold 8 unfolded: 4:3 landscape, each half like a phone on its side) made the
 * width-sized number push the sheet's bottom under the tab bar (816x616: 18px, measured
 * 2026-11-22). Only the number's line changes the sheet's height, so: rest = sheet height -
 * 1.05 x number size, and the number may be at most (room - rest) / 1.05, room being the
 * scroller's height below the sheet's top less 16px. The sheet's column is sticky there
 * (TodayView), so its top on screen is the same scrolled or not. Returns the cap in px (null:
 * no cap; at least 48) and the ref for the pad wrapper.
 */
function useFitToPane() {
  const ref = useRef<HTMLDivElement>(null);
  const [px, setPx] = useState<number | null>(null);
  useLayoutEffect(() => {
    const el = ref.current;
    const scroller = el?.closest<HTMLElement>('.overflow-y-auto');
    if (!el || !scroller) return;
    const twoPanes = window.matchMedia(TWO_PANES);
    const fit = () => {
      const number = el.querySelector<HTMLElement>('[data-tear-number]');
      if (!twoPanes.matches || !number) return setPx(null);
      const size = parseFloat(getComputedStyle(number).fontSize);
      const rest = el.offsetHeight - 1.05 * size;
      const top = el.getBoundingClientRect().top - scroller.getBoundingClientRect().top;
      const room = scroller.clientHeight - top - 16;
      const max = Math.max(48, Math.floor((room - rest) / 1.05));
      setPx(prev => (prev !== null && Math.abs(prev - max) < 1 ? prev : max));
    };
    fit();
    const ro = new ResizeObserver(fit);
    ro.observe(el);
    ro.observe(scroller);
    twoPanes.addEventListener('change', fit);
    return () => { ro.disconnect(); twoPanes.removeEventListener('change', fit); };
  }, []);
  return { ref, px };
}

/** One sheet of paper: lacquer with its light and moons, the frame and the dates. */
function SheetFace({ face, now, currentTraditionalHour, nav }: {
  face: Face;
  now: Date;
  currentTraditionalHour: string;
  /** Previous / next day; undefined: no arrows. */
  nav?: { prev: () => void; next: () => void };
}) {
  const { date, lunarInfo, holiday, observance } = face;
  const red = date.getDay() === 0 || !!holiday;
  const lunarDayLabel = getLunarDayLabel(lunarInfo.lunarDay, lunarInfo.lunarMonth);
  const lunarMonth = Math.abs(lunarInfo.lunarMonth);
  const arrow = 'w-10 h-10 rounded-full flex items-center justify-center text-white/60 hover:bg-white/10 hover:text-white active:scale-95 transition';

  return (
    <div className="relative p-3">
      {/* Paper: the lacquer gradient, soft light at 50% 25%, and the old hero card's two moons. */}
      <div className="absolute inset-0 overflow-hidden rounded-b-[1.25rem] bg-gradient-to-br from-hero-from via-hero-via to-hero-to" aria-hidden="true">
        <div className="absolute inset-0 bg-[radial-gradient(ellipse_at_50%_25%,rgb(255_255_255/0.10),transparent_60%)]" />
        <div className="absolute -right-16 -top-2 h-56 w-56 rounded-full bg-hero-accent/6" />
        <div className="absolute -left-10 bottom-24 h-32 w-32 rounded-full bg-hero-accent/5" />
      </div>

      {/* Double gold hairline frame, as on printed calendar sheets. */}
      <div className="relative rounded-[0.9rem] border border-hero-accent/45 p-[3px]">
        <div className="rounded-[0.7rem] border border-hero-accent/25 px-4 pt-6 pb-4">

          <p className="text-center text-[11px] font-semibold uppercase tracking-[0.34em] pl-[0.34em] text-hero-accent">
            Tháng {format(date, 'M')} · Tuần {getWeek(date, { weekStartsOn: 1, locale: vi })}
          </p>

          <div className="grid grid-cols-[2.5rem_1fr_2.5rem] items-center">
            <button onClick={nav?.prev} aria-label="Ngày trước" className={arrow} tabIndex={nav ? 0 : -1}>
              <ChevronLeft size={22} strokeWidth={1.5} />
            </button>
            <p className={cn(
              // Sized by the sheet's own width (cqw), so it fits a narrow desktop column too, and
              // capped by --tear-number-max where the sheet must fit the pane's height (useFitToPane).
              // Line height after the font size: tailwind-merge drops a leading-* that comes before text-*.
              'foil text-center font-serif font-bold tracking-[-0.02em] lining-nums',
              'text-[min(clamp(5rem,44cqw,11.5rem),var(--tear-number-max,11.5rem))] leading-[1.05]',
            )} data-tear-number="">
              {format(date, 'd')}
            </p>
            <button onClick={nav?.next} aria-label="Ngày sau" className={arrow} tabIndex={nav ? 0 : -1}>
              <ChevronRight size={22} strokeWidth={1.5} />
            </button>
          </div>

          {/* Sundays and holidays (red on a paper calendar) get a gold seal here. */}
          <div className="flex items-center justify-center gap-3 text-hero-accent">
            <span className="h-px w-8 bg-current opacity-40" aria-hidden="true" />
            <span className={cn(
              'text-[13px] font-semibold uppercase tracking-[0.4em] pl-[0.4em]',
              // Same padding every day, the seal only adds the fill: padded on Sundays alone, the
              // line was 8px taller and the whole sheet with it (2026-11-22).
              'rounded-full px-3 py-1',
              red ? 'bg-hero-accent text-hero-accent-ink' : 'text-white',
            )}>
              {format(date, 'EEEE', { locale: vi })}
            </span>
            <span className="h-px w-8 bg-current opacity-40" aria-hidden="true" />
          </div>

          <p className="mt-2 min-h-6 text-center font-serif text-base italic text-hero-accent">
            {holiday ?? observance ?? lunarDayLabel}
          </p>

          {/* Ornament: hairlines and a small gold diamond. */}
          <div className="mt-3 flex items-center gap-3" aria-hidden="true">
            <span className="h-px flex-1 bg-gradient-to-r from-transparent to-hero-accent/55" />
            <span className="h-1.5 w-1.5 rotate-45 bg-hero-accent/80" />
            <span className="h-px flex-1 bg-gradient-to-l from-transparent to-hero-accent/55" />
          </div>

          {/* Lunar date in a gold moon, a gold rule, the can chi beside it: always two columns
              (as the widget). Where the can chi column is under 146px (label 44 + gap 12 + the
              longest value "Nhâm Thân" 89.3, measured 2026-11-22; the phone's 343px sheet gives
              140) each label goes above its value, smaller. */}
          <div className="mt-4 grid grid-cols-[auto_1px_minmax(0,1fr)] items-center gap-4 @min-[22rem]:gap-5">
            <div className="tear-moon flex h-[6.5rem] w-[6.5rem] flex-col items-center justify-center rounded-full border border-hero-accent/55">
              {/* A leap month says so on the top line, so the bottom line stays inside the ring
                  ("tháng Mười Một nhuận" was 119px wide). */}
              <span className="text-[9px] font-semibold uppercase tracking-[0.28em] pl-[0.28em] text-hero-accent/85">
                {lunarInfo.isLeap ? 'Nhuận' : 'Âm lịch'}
              </span>
              <span className="font-serif text-[2.6rem] font-bold leading-none foil lining-nums">{lunarInfo.lunarDay}</span>
              {/* "Mười Một" in 10.5px: at 12px "tháng Mười Một" touched the ring. */}
              <span className={cn('mt-0.5 font-serif italic text-white/70', LUNAR_MONTH_NAMES[lunarMonth - 1].length > 5 ? 'text-[10.5px]' : 'text-xs')}>
                tháng {LUNAR_MONTH_NAMES[lunarMonth - 1]}
              </span>
            </div>
            <span className="h-20 w-px bg-hero-accent/35" aria-hidden="true" />
            <dl className="@container/canchi flex min-w-0 flex-col gap-1 @min-[146px]/canchi:gap-1.5">
              {[
                { label: 'Năm', value: lunarInfo.canChiYear },
                { label: 'Tháng', value: lunarInfo.canChiMonth },
                { label: 'Ngày', value: lunarInfo.canChiDay },
              ].map(({ label, value }) => (
                <div key={label} className="flex min-w-0 flex-col @min-[146px]/canchi:flex-row @min-[146px]/canchi:items-baseline @min-[146px]/canchi:gap-3">
                  <dt className="shrink-0 text-[7.5px] leading-[11px] @min-[146px]/canchi:w-11 @min-[146px]/canchi:text-[9px] @min-[146px]/canchi:leading-normal font-semibold uppercase tracking-[0.24em] text-white/55">{label}</dt>
                  <dd className="truncate font-serif text-[1.05rem] leading-snug font-semibold text-white">{value}</dd>
                </div>
              ))}
            </dl>
          </div>

          <p className="mt-5 text-center text-[10px] font-semibold uppercase tracking-[0.3em] pl-[0.3em] text-white/55 tabular-nums">
            Giờ {currentTraditionalHour} · {format(now, 'HH:mm')}
          </p>
        </div>
      </div>
    </div>
  );
}
