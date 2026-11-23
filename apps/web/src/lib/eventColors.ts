import { EventColor } from '../types';

/** A personal event's colour as Tailwind classes of the theme tokens. */
export const EVENT_COLORS: Record<EventColor, { dot: string; soft: string; text: string; label: string }> = {
  gold:  { dot: 'bg-gold',  soft: 'bg-gold-soft',  text: 'text-gold',  label: 'Vàng' },
  brand: { dot: 'bg-brand', soft: 'bg-brand-soft', text: 'text-brand', label: 'Màu chính' },
  lunar: { dot: 'bg-lunar', soft: 'bg-lunar-soft', text: 'text-lunar', label: 'Đỏ' },
  good:  { dot: 'bg-good',  soft: 'bg-good-soft',  text: 'text-good',  label: 'Xanh lá' },
  clash: { dot: 'bg-clash', soft: 'bg-clash-soft', text: 'text-clash', label: 'Tím' },
};
export const EVENT_COLOR_ORDER: EventColor[] = ['gold', 'brand', 'lunar', 'good', 'clash'];
