import { useLayoutEffect, type RefObject } from 'react';

/**
 * The Calendar page's month card getting shorter under the sticky header as the page scrolls
 * (user, 2026-11-23): the same card, rounded corners, labels and legend, with only two week rows
 * left, one of them the selected day's week, so the panel below can be read with the days around
 * it in view.
 *
 * Only while the header is sticky (single-column layouts; two panes keep the whole month).
 *
 * The header's chip row (`data-head-hide`) goes first: the header is sticky with top = -C, C the
 * chip row with its margin, so it scrolls up by C before it sticks, while its title row
 * (`data-head-keep`) slides down by the same amount (--hd-keep) and covers the chips sliding
 * under it, which fade (--hd-fade).
 *
 * Then the card, sticky right under what is left of the header. Over the next K px of scrolling
 * (K = all rows but two, and the legend), by o = 0..K: the card's look (index.css, a ::before
 * with its background, corners and shadow) loses o at the bottom (--wk-o), the rows move up by
 * t·first·step (t = o/K, --wk-rows) so rows [first, first + 1] end under the labels, the other
 * rows and the legend fade (--wk-fade), and the content (`data-fold-body`) is clipped to the
 * shorter card. A strip of the page's colour under it (::after) keeps the usual gap to the
 * cards scrolling up under it.
 *
 * Elements: weekday labels `data-weekday`, day cells `data-week="<row>"`, legend `data-legend`.
 * `mark` is an empty element right before the card, in the flow: where the card would be
 * without sticking.
 */
export function useStickyWeeks(
  scroller: RefObject<HTMLElement | null>,
  header: RefObject<HTMLElement | null>,
  card: RefObject<HTMLElement | null>,
  mark: RefObject<HTMLElement | null>,
  first: number,
  rows: number,
) {
  useLayoutEffect(() => {
    const sc = scroller.current, hd = header.current, cd = card.current, mk = mark.current;
    if (!sc || !hd || !cd || !mk) return;
    let raf = 0;
    const clear = () => {
      for (const p of ['position', 'top', 'z-index', '--wk-o', '--wk-rows', '--wk-fade']) cd.style.removeProperty(p);
      delete cd.dataset.folding;
      for (const p of ['top', '--hd-keep', '--hd-fade']) hd.style.removeProperty(p);
      delete hd.dataset.headFolding;
    };
    const update = () => {
      raf = 0;
      if (getComputedStyle(hd).position !== 'sticky') { clear(); return; }
      cd.style.position = 'sticky';
      cd.dataset.folding = '';
      cd.style.zIndex = '5';
      // offsetTop (not getBoundingClientRect): untouched by the transforms set below.
      const cells = cd.querySelectorAll<HTMLElement>('[data-week]');
      const row = (j: number) => cells[j * 7];
      if (!row(0) || !row(1)) { clear(); return; }
      const step = row(1).offsetTop - row(0).offsetTop;
      // All rows but two, and the legend (it fades away with them).
      const legend = cd.querySelector<HTMLElement>('[data-legend]');
      const K = Math.max(0, (cells.length / 7 - 2) * step + (legend?.offsetHeight ?? 0));
      // The chip row hides first (C px of scrolling); the card meets what is left of the header.
      const keep = hd.querySelector<HTMLElement>('[data-head-keep]');
      const hide = hd.querySelector<HTMLElement>('[data-head-hide]');
      const C = keep && hide ? Math.max(0, hide.offsetTop + hide.offsetHeight - (keep.offsetTop + keep.offsetHeight)) : 0;
      const oh = Math.min(C, Math.max(0, sc.scrollTop));
      hd.dataset.headFolding = '';
      hd.style.top = `${-C}px`;
      hd.style.setProperty('--hd-keep', `${oh}px`);
      hd.style.setProperty('--hd-fade', `${C > 0 ? 1 - oh / C : 1}`);
      const H = hd.offsetHeight - C;
      const natural = mk.getBoundingClientRect().top - sc.getBoundingClientRect().top + sc.scrollTop;
      const o = Math.min(K, Math.max(0, sc.scrollTop - (natural - H)));
      const t = K > 0 ? o / K : 0;
      cd.style.top = `${H}px`;
      cd.style.setProperty('--wk-o', `${o}px`);
      cd.style.setProperty('--wk-rows', `${-t * first * step}px`);
      cd.style.setProperty('--wk-fade', `${1 - t}`);
    };
    const schedule = () => { if (!raf) raf = requestAnimationFrame(update); };
    update();
    sc.addEventListener('scroll', schedule, { passive: true });
    const ro = new ResizeObserver(schedule);
    ro.observe(sc);
    ro.observe(cd);
    ro.observe(hd);
    return () => {
      sc.removeEventListener('scroll', schedule);
      ro.disconnect();
      cancelAnimationFrame(raf);
      clear();
    };
  }, [scroller, header, card, mark, first, rows]);
}
