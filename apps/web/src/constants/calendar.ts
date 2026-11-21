import calendar from '@lunar/shared/data/calendar.json';

/** The 12 Earthly Branches (Địa Chi) used for zodiac and time calculations. Data: shared/data/calendar.json. */
export type ChiName =
  | 'Tý' | 'Sửu' | 'Dần' | 'Mão' | 'Thìn' | 'Tỵ'
  | 'Ngọ' | 'Mùi' | 'Thân' | 'Dậu' | 'Tuất' | 'Hợi';
export const CHI_NAMES = calendar.chi as readonly ChiName[];

/** The 10 Heavenly Stems (Thiên Can). */
export const CAN_NAMES: readonly string[] = calendar.can;

/** Short Vietnamese weekday labels, Monday first. */
export const WEEK_DAYS: readonly string[] = calendar.weekDays;

/** Medium Vietnamese weekday labels. */
export const WEEK_DAYS_SHORT: readonly string[] = calendar.weekDaysShort;

/** Year range shown in the year picker. */
export const YEAR_RANGE = { start: 1900, count: 200 } as const;

/** Emoji for each Địa Chi. Sửu is the buffalo (trâu) and Mão the cat in the Vietnamese zodiac. */
export const CHI_EMOJI: Readonly<Record<string, string>> = calendar.chiEmoji;
