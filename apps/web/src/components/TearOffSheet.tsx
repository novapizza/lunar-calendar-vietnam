import { memo, useEffect, useLayoutEffect, useRef, useState } from 'react';
import { format, getWeek, addDays, subDays, isToday } from 'date-fns';
import { vi } from 'date-fns/locale';
import { ChevronLeft, ChevronRight, Undo2, Sun, Moon, SunMoon } from 'lucide-react';
import { cn } from '../lib/utils';
import { useSwipe } from '../hooks/useSwipe';
import { curlFrame, foldFrame, PERSPECTIVE, STRIPS, TILT_DEG, TURN_MS } from '../lib/pageCurl';
import type { LunarInfo } from '../types';
import type { ThemePref } from '../hooks/useTheme';

const THEME_BUTTON = {
  system: { Icon: SunMoon, label: 'Giao diện: theo hệ thống' },
  light:  { Icon: Sun,     label: 'Giao diện: sáng' },
  dark:   { Icon: Moon,    label: 'Giao diện: tối' },
} as const;

/** Lunar month names as printed on tear-off calendars ("Tháng Giêng" … "Tháng Chạp"); month 11 is "Mười Một", not the old "Một" (read as month 1, 2026-11-22). */
const LUNAR_MONTH_NAMES = ['Giêng', 'Hai', 'Ba', 'Tư', 'Năm', 'Sáu', 'Bảy', 'Tám', 'Chín', 'Mười', 'Mười Một', 'Chạp'];

/** "Mồng 1" / "Ngày rằm" for lunar day 1/15, excluding 1/1 and 15/1 (named holidays). */
function getLunarDayLabel(lunarDay: number, lunarMonth: number): string | null {
  const m = Math.abs(lunarMonth);
  if (lunarDay === 1 && m !== 1) return 'Mồng 1';
  if (lunarDay === 15 && m !== 1) return 'Ngày rằm';
  return null;
}

/** What one sheet shows. */
interface Face {
  date: Date;
  lunarInfo: LunarInfo;
  holiday: string | undefined;
}

interface Props {
  date: Date;
  now: Date;
  currentTraditionalHour: string;
  lunarInfo: LunarInfo;
  holiday: string | undefined;
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
 * Swipe or the arrows turn the page like paper (PageTurn): a later day curls the sheet up from
 * the bottom and flips it over the binding, uncovering the next one; an earlier day brings a
 * sheet back down the same way.
 */
export function TearOffSheet({ date, now, currentTraditionalHour, lunarInfo, holiday, onDayChange, themePref, onCycleTheme }: Props) {
  const swipe = useSwipe(
    () => onDayChange(addDays(date, 1)),
    () => onDayChange(subDays(date, 1)),
  );

  // The sheet on show, and the turn in progress (state adjusted during render, React's pattern
  // for "previous prop"; safe under StrictMode).
  const face: Face = { date, lunarInfo, holiday };
  const [shown, setShown] = useState<Face>(face);
  const [turn, setTurn] = useState<{ from: Face; forward: boolean; id: number } | null>(null);
  if (shown.date.getTime() !== date.getTime()) {
    setTurn({ from: shown, forward: date.getTime() > shown.date.getTime(), id: (turn?.id ?? 0) + 1 });
    setShown(face);
  }
  // The sheet PageTurn's strips hold between turns. Rebuilding their 48 copies when a turn ends
  // cost a ~40ms frame (desktop, measured 2026-11-22), so it waits until the browser is idle.
  const [prepared, setPrepared] = useState<Face>(face);
  useEffect(() => {
    if (turn || prepared.date.getTime() === date.getTime()) return;
    const ric = window.requestIdleCallback ?? ((cb: () => void) => window.setTimeout(cb, 200));
    const cancel = window.cancelIdleCallback ?? window.clearTimeout;
    const id = ric(() => setPrepared({ date, lunarInfo, holiday }), { timeout: 1500 });
    return () => cancel(id);
  }, [turn, date, lunarInfo, holiday, prepared]);

  const lunarMonth = Math.abs(lunarInfo.lunarMonth);
  const theme = THEME_BUTTON[themePref];
  const strip = 'w-10 h-10 rounded-full flex items-center justify-center text-white/85 hover:bg-white/10 active:scale-95 transition';
  // The arrows always step from the selected day, also while a turn shows an older sheet
  // underneath, and work during a turn (pressing again starts the next turn at once).
  const nav = { prev: () => onDayChange(subDays(date, 1)), next: () => onDayChange(addDays(date, 1)) };
  const sheet = (f: Face, interactive: boolean) => (
    <SheetFace face={f} now={now} currentTraditionalHour={currentTraditionalHour} nav={interactive ? nav : undefined} />
  );

  return (
    <div className="relative pb-3">
      {/* The pad: two sheets under the current one. */}
      <div className="absolute inset-x-4 bottom-0 top-8 rounded-b-[1.25rem] border border-hero-accent/15 bg-hero-to opacity-70" aria-hidden="true" />
      <div className="absolute inset-x-2 bottom-1.5 top-8 rounded-b-[1.25rem] border border-hero-accent/20 bg-hero-via shadow-[var(--shadow-card)]" aria-hidden="true" />

      {/* No overflow clip here: the turning page leaves the sheet's box. The binding and the
          paper round their own corners. */}
      <section
        {...swipe}
        aria-label={`Ngày ${format(date, 'd/M/yyyy')}, âm lịch ${lunarInfo.lunarDay}/${lunarMonth}`}
        className="@container relative rounded-[1.25rem] text-white shadow-[var(--shadow-float)]"
      >
        {/* Binding: a darker lacquer strip edged in gold. */}
        <div className="relative flex h-12 items-center justify-between overflow-hidden rounded-t-[1.25rem] bg-gradient-to-br from-hero-from via-hero-via to-hero-to px-1.5">
          <div className="absolute inset-0 bg-black/25" aria-hidden="true" />
          <button onClick={onCycleTheme} className={cn(strip, 'relative')} aria-label={theme.label} title={theme.label}>
            <theme.Icon size={18} />
          </button>
          <span className="relative text-[11px] font-semibold uppercase tracking-[0.32em] text-hero-accent/90 tabular-nums">
            Tuần {getWeek(date, { weekStartsOn: 1, locale: vi })}
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

        {/* The sheet: underneath, the live sheet (the new day going forward, the old one going
            back) with the rings over it; above it, PageTurn's strips, always mounted, holding the
            sheet that turns next. */}
        <div className="relative">
          {sheet(turn && !turn.forward ? turn.from : face, true)}
          <span className="tear-ring left-[26%]" style={{ top: 'calc(30px - 3rem)' }} aria-hidden="true" />
          <span className="tear-ring right-[26%]" style={{ top: 'calc(30px - 3rem)' }} aria-hidden="true" />
          <PageTurn
            face={turn ? (turn.forward ? turn.from : face) : prepared}
            turn={turn}
            now={now}
            currentTraditionalHour={currentTraditionalHour}
            onDone={() => setTurn(null)}
          />
        </div>

      </section>
    </div>
  );
}

/** One sheet of paper: lacquer with its light and moons, the frame and the dates. */
function SheetFace({ face, now, currentTraditionalHour, nav }: {
  face: Face;
  now: Date;
  currentTraditionalHour: string;
  /** Previous / next day; undefined for the copies drawn by PageTurn (nothing to press there). */
  nav?: { prev: () => void; next: () => void };
}) {
  const { date, lunarInfo, holiday } = face;
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
            Tháng {format(date, 'M')} · {format(date, 'yyyy')}
          </p>

          <div className="grid grid-cols-[2.5rem_1fr_2.5rem] items-center">
            <button onClick={nav?.prev} aria-label="Ngày trước" className={arrow} tabIndex={nav ? 0 : -1}>
              <ChevronLeft size={22} strokeWidth={1.5} />
            </button>
            <p className={cn(
              // Sized by the sheet's own width (cqw), so it fits a narrow desktop column too.
              // Line height after the font size: tailwind-merge drops a leading-* that comes before text-*.
              'foil text-center font-serif font-bold tracking-[-0.02em] lining-nums',
              'text-[clamp(5rem,44cqw,11.5rem)] leading-[1.05]',
            )}>
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
              red ? 'rounded-full bg-hero-accent px-3 py-1 text-hero-accent-ink' : 'text-white',
            )}>
              {format(date, 'EEEE', { locale: vi })}
            </span>
            <span className="h-px w-8 bg-current opacity-40" aria-hidden="true" />
          </div>

          <p className="mt-2 min-h-6 text-center font-serif text-base italic text-hero-accent">
            {holiday ?? lunarDayLabel}
          </p>

          {/* Ornament: hairlines and a small gold diamond. */}
          <div className="mt-3 flex items-center gap-3" aria-hidden="true">
            <span className="h-px flex-1 bg-gradient-to-r from-transparent to-hero-accent/55" />
            <span className="h-1.5 w-1.5 rotate-45 bg-hero-accent/80" />
            <span className="h-px flex-1 bg-gradient-to-l from-transparent to-hero-accent/55" />
          </div>

          {/* Lunar date in a gold moon, can chi beside it; on a narrow sheet (under 22rem) the
              can chi go below the moon, in three columns. */}
          <div className="mt-4 flex flex-col items-center gap-4 @min-[22rem]:grid @min-[22rem]:grid-cols-[auto_1px_1fr] @min-[22rem]:gap-5">
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
            <span className="hidden h-20 w-px bg-hero-accent/35 @min-[22rem]:block" aria-hidden="true" />
            <dl className="grid w-full grid-cols-3 gap-2 text-center @min-[22rem]:flex @min-[22rem]:flex-col @min-[22rem]:gap-1.5 @min-[22rem]:text-left">
              {[
                { label: 'Năm', value: lunarInfo.canChiYear },
                { label: 'Tháng', value: lunarInfo.canChiMonth },
                { label: 'Ngày', value: lunarInfo.canChiDay },
              ].map(({ label, value }) => (
                <div key={label} className="flex flex-col gap-0.5 @min-[22rem]:flex-row @min-[22rem]:items-baseline @min-[22rem]:gap-3">
                  <dt className="shrink-0 text-[9px] @min-[22rem]:w-11 font-semibold uppercase tracking-[0.24em] text-white/55">{label}</dt>
                  <dd className="whitespace-nowrap font-serif text-[1.05rem] font-semibold text-white">{value}</dd>
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

/**
 * The turning sheet, a corner peel. The sheet is cut into STRIPS fixed strips parallel to the
 * fold (lib/pageCurl.ts): each strip is a copy of the sheet clipped to its strip of the fold
 * frame (the page rotated by -TILT_DEG), so it is no bigger than the strip. Each frame only sets
 * the strips' transforms and opacities, which the browser composites without painting again.
 * A strip turned past 90° shows the back of the paper (cream). Forward runs progress 0 → 1 (the
 * sheet peels off past the top-left corner), backward 1 → 0 (a sheet comes back and lies flat).
 *
 * The strips stay mounted between turns, holding the sheet on show, nearly transparent (0.001,
 * so the browser keeps them rasterised): a forward turn then starts on layers that are already
 * drawn. (Building 26 page-sized layers when a turn started, and dropping them when it ended,
 * blanked the sheet for a frame at both moments on a phone, 2026-11-22.)
 */
function PageTurn({ face, turn, now, currentTraditionalHour, onDone }: {
  /** The sheet the strips show: the one that turns next. */
  face: Face;
  turn: { forward: boolean; id: number } | null;
  now: Date;
  currentTraditionalHour: string;
  onDone: () => void;
}) {
  const root = useRef<HTMLDivElement>(null);
  const box = useRef<HTMLDivElement>(null);
  const shadow = useRef<HTMLDivElement>(null);
  const strips = useRef<(HTMLDivElement | null)[]>([]);
  const done = useRef(onDone);
  done.current = onDone;
  const [size, setSize] = useState<{ w: number; h: number } | null>(null);

  // The sheet's size (the strips are cut to it); follows resizes.
  useLayoutEffect(() => {
    const el = root.current;
    if (!el) return;
    const measure = () => setSize(s => (s && s.w === el.offsetWidth && s.h === el.offsetHeight ? s : { w: el.offsetWidth, h: el.offsetHeight }));
    measure();
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    return () => ro.disconnect();
  }, []);

  useLayoutEffect(() => {
    if (!size || !turn) return;
    const frame = foldFrame(size.w, size.h);
    const stripH = frame.sMax / STRIPS;
    const apply = (p: number) => {
      const f = curlFrame(p, size.w, size.h);
      if (box.current) box.current.style.opacity = String(f.opacity);
      if (shadow.current) {
        shadow.current.style.opacity = String(f.shadow.alpha);
        shadow.current.style.transform = `rotate(${-TILT_DEG}deg) translate3d(${frame.uMin}px, ${f.shadow.from}px, 0)`;
      }
      f.strips.forEach((st, k) => {
        const el = strips.current[k];
        if (!el) return;
        el.style.transform = `translate3d(0px, ${(st.s - k * stripH).toFixed(2)}px, ${st.z.toFixed(2)}px) rotateX(${st.angle.toFixed(2)}deg)`;
        const [front, backFace, shade] = (el.firstElementChild as HTMLElement).children as unknown as HTMLElement[];
        front.style.opacity = st.back ? '0' : '1';
        backFace.style.opacity = st.back ? '1' : '0';
        shade.style.opacity = String(st.shade);
      });
    };
    const reset = () => {
      if (box.current) box.current.style.opacity = '1';
      if (shadow.current) shadow.current.style.opacity = '0';
      strips.current.forEach(el => {
        if (!el) return;
        el.style.transform = '';
        const [front, backFace, shade] = (el.firstElementChild as HTMLElement).children as unknown as HTMLElement[];
        front.style.opacity = '1';
        backFace.style.opacity = '0';
        shade.style.opacity = '0';
      });
    };
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      done.current();
      return;
    }
    let raf = 0;
    const start = performance.now();
    const step = (t: number) => {
      // Development only: window.__tearFreeze = 0..1 holds the turn at that point (screenshots).
      const freeze = import.meta.env.DEV ? (window as unknown as { __tearFreeze?: number }).__tearFreeze : undefined;
      // A frame's timestamp can be earlier than `start` (it is the frame's start time): clamp at 0.
      const k = typeof freeze === 'number' ? freeze : Math.min(1, Math.max(0, (t - start) / TURN_MS));
      apply(turn.forward ? k : 1 - k);
      if (k < 1 || typeof freeze === 'number') raf = requestAnimationFrame(step);
      else {
        // Hide the strips before flattening them, in this same frame: React hides them only on
        // its next render, a frame later, and in between the flat old sheet covered the new day
        // (a blink after each turn on the phone, 2026-11-22).
        if (root.current) root.current.style.opacity = '0.001';
        reset();
        done.current();
      }
    };
    apply(turn.forward ? 0 : 1);
    raf = requestAnimationFrame(step);
    return () => {
      cancelAnimationFrame(raf);
      reset();
    };
  }, [size, turn]);

  const frame = size && foldFrame(size.w, size.h);
  return (
    <div
      ref={root}
      className="pointer-events-none absolute inset-0 z-10"
      // Between turns: drawn but invisible (0, the browser could drop the layers' pixels).
      style={{ opacity: turn ? 1 : 0.001 }}
      aria-hidden="true"
      inert
    >
      {/* Shadow the turning page casts on the sheet underneath: a gradient band in the fold frame
          that slides with the fold (moved, never repainted), clipped to the sheet. */}
      <div className="absolute inset-0 overflow-hidden rounded-b-[1.25rem]">
        {frame && (
          <div
            ref={shadow}
            className="absolute left-0 top-0 bg-gradient-to-b from-black to-transparent"
            style={{ width: frame.uMax - frame.uMin, height: 0.4 * frame.sMax, transformOrigin: '0 0', opacity: 0, willChange: 'transform, opacity' }}
          />
        )}
      </div>
      <div ref={box} className="absolute inset-0" style={{ perspective: PERSPECTIVE, perspectiveOrigin: '50% 0' }}>
        {size && frame && (
          <div className="absolute left-0 top-0" style={{ transform: `rotate(${-TILT_DEG}deg)`, transformOrigin: '0 0', transformStyle: 'preserve-3d' }}>
            <StripCopies
              face={face} w={size.w} h={size.h} uMin={frame.uMin} uMax={frame.uMax} sMax={frame.sMax}
              clock={`${currentTraditionalHour} ${format(now, 'HH:mm')}`} now={now} currentTraditionalHour={currentTraditionalHour}
              strips={strips}
            />
            {/* The rings again, just above the flat paper (z 0.5px) and so under the curl and the
                flap; page-aligned (the fold frame turned back). Same place as the sheet's rings. */}
            <div className="absolute left-0 top-0" style={{ width: size.w, height: size.h, transformOrigin: '0 0', transform: `rotate(${TILT_DEG}deg) translateZ(0.5px)` }}>
              <span className="tear-ring left-[26%]" style={{ top: 'calc(30px - 3rem)' }} />
              <span className="tear-ring right-[26%]" style={{ top: 'calc(30px - 3rem)' }} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * STRIPS copies of the sheet, each clipped to one strip of the fold frame. Memoised on what the
 * sheet shows and its size: they are rebuilt between turns, not during one.
 */
const StripCopies = memo(function StripCopies({ face, w, h, uMin, uMax, sMax, now, currentTraditionalHour, strips }: {
  face: Face; w: number; h: number; uMin: number; uMax: number; sMax: number;
  /** Only for the memo: the clock text the sheet shows. */
  clock: string;
  now: Date; currentTraditionalHour: string;
  strips: React.MutableRefObject<(HTMLDivElement | null)[]>;
}) {
  const stripH = sMax / STRIPS;
  return Array.from({ length: STRIPS }, (_, k) => (
    <div
      key={k}
      ref={el => { strips.current[k] = el; }}
      className="absolute overflow-hidden"
      // +1px so neighbouring strips overlap and no seam shows.
      style={{ left: uMin, top: k * stripH, width: uMax - uMin, height: stripH + 1, transformOrigin: '0 0', willChange: 'transform' }}
    >
      {/* The sheet, page-aligned again, placed so the page's corner sits where it belongs. */}
      <div className="absolute" style={{ left: -uMin, top: -k * stripH, width: w, height: h, transformOrigin: '0 0', transform: `rotate(${TILT_DEG}deg)` }}>
        <SheetFace face={face} now={now} currentTraditionalHour={currentTraditionalHour} />
        {/* Back of the sheet: cream paper, warmed by the palette's gold. */}
        <div className="tear-paper-back absolute inset-0 rounded-b-[1.25rem]" style={{ opacity: 0 }} />
        {/* Shading, rounded like the paper. */}
        <div className="absolute inset-0 rounded-b-[1.25rem] bg-black" style={{ opacity: 0 }} />
      </div>
    </div>
  ));
}, (a, b) => a.face.date.getTime() === b.face.date.getTime() && a.face.holiday === b.face.holiday && a.w === b.w && a.h === b.h && a.clock === b.clock);
