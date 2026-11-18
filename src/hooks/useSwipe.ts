import { useRef } from 'react';
import type { TouchEvent } from 'react';

/**
 * Horizontal swipe detection for touch screens.
 * Fires only for a clearly horizontal gesture, so vertical scrolling is untouched.
 * Spread the returned handlers on the element that should react.
 */
export function useSwipe(onSwipeLeft: () => void, onSwipeRight: () => void, minDistance = 60) {
  const start = useRef<{ x: number; y: number } | null>(null);

  return {
    onTouchStart(e: TouchEvent) {
      const t = e.touches[0];
      start.current = { x: t.clientX, y: t.clientY };
    },
    onTouchEnd(e: TouchEvent) {
      if (!start.current) return;
      const t = e.changedTouches[0];
      const dx = t.clientX - start.current.x;
      const dy = t.clientY - start.current.y;
      start.current = null;
      if (Math.abs(dx) < minDistance || Math.abs(dx) < Math.abs(dy) * 1.5) return;
      if (dx < 0) onSwipeLeft();
      else onSwipeRight();
    },
  };
}
