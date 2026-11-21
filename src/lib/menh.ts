import { LunarCalendar } from '../services/lunarService';
import { CHI_NAMES, type ChiName } from '../constants/calendar';

/** The five elements (Ngũ Hành). Also the keys of the colour palettes in index.css. */
export type Element = 'kim' | 'moc' | 'thuy' | 'hoa' | 'tho';

export const ELEMENTS: Readonly<Record<Element, {
  name: string;
  /** Colour of the element's palette, as shown in the settings. */
  color: string;
  /** The element that generates this one (tương sinh): Thổ → Kim → Thủy → Mộc → Hỏa → Thổ. */
  generatedBy: Element;
}>> = {
  kim:  { name: 'Kim',  color: 'Bạc',        generatedBy: 'tho' },
  thuy: { name: 'Thủy', color: 'Xanh dương', generatedBy: 'kim' },
  moc:  { name: 'Mộc',  color: 'Xanh lá',    generatedBy: 'thuy' },
  hoa:  { name: 'Hỏa',  color: 'Đỏ',         generatedBy: 'moc' },
  tho:  { name: 'Thổ',  color: 'Nâu đất',    generatedBy: 'hoa' },
};

const CAN = ['Giáp', 'Ất', 'Bính', 'Đinh', 'Mậu', 'Kỷ', 'Canh', 'Tân', 'Nhâm', 'Quý'] as const;

/**
 * Nạp âm of the 60-year cycle, one entry per pair of years starting from Giáp Tý.
 * Order and elements checked against the 納音 table on ja.wikipedia.org (2026-11-21).
 */
const NAP_AM: ReadonlyArray<readonly [string, Element]> = [
  ['Hải Trung Kim', 'kim'],   ['Lư Trung Hỏa', 'hoa'],   ['Đại Lâm Mộc', 'moc'],
  ['Lộ Bàng Thổ', 'tho'],     ['Kiếm Phong Kim', 'kim'], ['Sơn Đầu Hỏa', 'hoa'],
  ['Giản Hạ Thủy', 'thuy'],   ['Thành Đầu Thổ', 'tho'],  ['Bạch Lạp Kim', 'kim'],
  ['Dương Liễu Mộc', 'moc'],  ['Tuyền Trung Thủy', 'thuy'], ['Ốc Thượng Thổ', 'tho'],
  ['Tích Lịch Hỏa', 'hoa'],   ['Tùng Bách Mộc', 'moc'],  ['Trường Lưu Thủy', 'thuy'],
  ['Sa Trung Kim', 'kim'],    ['Sơn Hạ Hỏa', 'hoa'],     ['Bình Địa Mộc', 'moc'],
  ['Bích Thượng Thổ', 'tho'], ['Kim Bạch Kim', 'kim'],   ['Phú Đăng Hỏa', 'hoa'],
  ['Thiên Hà Thủy', 'thuy'],  ['Đại Trạch Thổ', 'tho'],  ['Thoa Xuyến Kim', 'kim'],
  ['Tang Đố Mộc', 'moc'],     ['Đại Khê Thủy', 'thuy'],  ['Sa Trung Thổ', 'tho'],
  ['Thiên Thượng Hỏa', 'hoa'], ['Thạch Lựu Mộc', 'moc'], ['Đại Hải Thủy', 'thuy'],
];

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
  const i = (((lunarYear - 1984) % 60) + 60) % 60;
  const chi = CHI_NAMES[i % 12];
  const [napAm, element] = NAP_AM[i >> 1];
  return { lunarYear, canChi: `${CAN[i % 10]} ${chi}`, chi, napAm, element };
}

/** Earliest birth date accepted: Tết 1900 (the lunar library starts there). */
export const BIRTH_DATE_MIN = '1900-01-31';

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
