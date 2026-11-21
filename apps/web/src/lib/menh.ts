import { LunarCalendar } from '../services/lunarService';
import menhData from '@lunar/shared/data/menh.json';
import { CAN_NAMES, CHI_NAMES, type ChiName } from '../constants/calendar';

/** The five elements (Ngũ Hành). Also the keys of the colour palettes in index.css. */
export type Element = 'kim' | 'moc' | 'thuy' | 'hoa' | 'tho';

/** Element names, palette colour and tương sinh (Thổ → Kim → Thủy → Mộc → Hỏa → Thổ). Data: shared/data/menh.json. */
export const ELEMENTS = menhData.elements as Readonly<Record<Element, {
  name: string;
  /** Colour of the element's palette, as shown in the settings. */
  color: string;
  /** The element that generates this one (tương sinh). */
  generatedBy: Element;
}>>;

/** Nạp âm of the 60-year cycle, one entry per pair of years starting from Giáp Tý. */
const NAP_AM = menhData.napAm as ReadonlyArray<{ name: string; element: Element }>;

export interface Menh {
  lunarYear: number;
  /** e.g. "Canh Ngọ" */
  canChi: string;
  chi: ChiName;
  /** e.g. "Lộ Bàng Thổ" */
  napAm: string;
  element: Element;
}

/** Mệnh of a lunar year. 1984 is Giáp Tý, the start of the cycle. */
export function menhOfLunarYear(lunarYear: number): Menh {
  const i = (((lunarYear - menhData.cycleStartYear) % 60) + 60) % 60;
  const chi = CHI_NAMES[i % 12];
  const { name: napAm, element } = NAP_AM[i >> 1];
  return { lunarYear, canChi: `${CAN_NAMES[i % 10]} ${chi}`, chi, napAm, element };
}

/** Earliest birth date accepted: Tết 1900 (the lunar library starts there). */
export const BIRTH_DATE_MIN: string = menhData.birthDateMin;

/**
 * Mệnh from a solar birth date "YYYY-MM-DD". The lunar year decides, so someone
 * born in January before Tết belongs to the previous year.
 */
export function menhOfBirthDate(iso: string | null): Menh | null {
  const m = iso && /^(\d{4})-(\d{2})-(\d{2})$/.exec(iso);
  if (!m || iso! < BIRTH_DATE_MIN) return null;
  const [y, mo, d] = [Number(m[1]), Number(m[2]), Number(m[3])];
  try {
    return menhOfLunarYear(LunarCalendar.fromSolar(d, mo, y).lunarDate.year);
  } catch {
    return null;
  }
}

/** Which colour the app uses: the element's own, the generating element's, or the default red. */
export type AccentPref = 'ban-menh' | 'tuong-sinh' | 'mac-dinh';

/** The palette shown for a preference. Without a mệnh the app stays red (Hỏa palette = default). */
export function paletteFor(menh: Menh | null, pref: AccentPref): Element {
  if (!menh || pref === 'mac-dinh') return 'hoa';
  return pref === 'tuong-sinh' ? ELEMENTS[menh.element].generatedBy : menh.element;
}
