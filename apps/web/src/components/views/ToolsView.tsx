import type { ReactNode } from 'react';
import {
  ArrowLeftRight, Search, Download, ChevronRight, Info, Monitor, Sun, Moon, CalendarDays,
} from 'lucide-react';
import { cn } from '../../lib/utils';
import type { ThemePref } from '../../hooks/useTheme';
import { ELEMENTS, paletteFor, type AccentPref, type Element, type Menh } from '../../lib/menh';

interface Props {
  birthDate: string | null;
  userMenh: Menh | null;
  /** Zodiac picked before the birth date existed (still used for clash days). */
  legacyZodiac: string | null;
  accentPref: AccentPref;
  palette: Element;
  themePref: ThemePref;
  onOpenBirthPicker: () => void;
  onAccentChange: (pref: AccentPref) => void;
  onThemeChange: (pref: ThemePref) => void;
  onOpenQuickPicker: () => void;
  onOpenAuspiciousPicker: () => void;
  onOpenLunarEventModal: () => void;
  onOpenAbout: () => void;
}

const THEMES: ReadonlyArray<{ pref: ThemePref; label: string; Icon: typeof Sun }> = [
  { pref: 'system', label: 'Theo máy', Icon: Monitor },
  { pref: 'light',  label: 'Sáng',     Icon: Sun },
  { pref: 'dark',   label: 'Tối',      Icon: Moon },
];

/** "YYYY-MM-DD" → "15/06/1990". */
const formatDate = (iso: string) => iso.split('-').reverse().join('/');

/**
 * Colour choices for a mệnh: own element, generating element, default red.
 * A choice that shows the same palette as an earlier one is left out
 * (Hỏa's own colour and Thổ's generating colour are the default red).
 */
function accentOptions(menh: Menh | null) {
  const all: { pref: AccentPref; label: string }[] = [
    { pref: 'ban-menh', label: 'Bản mệnh' },
    { pref: 'tuong-sinh', label: 'Tương sinh' },
    { pref: 'mac-dinh', label: 'Mặc định' },
  ];
  const seen = new Set<Element>();
  return all
    .map(o => ({ ...o, palette: paletteFor(menh, o.pref) }))
    .filter(o => (menh || o.pref === 'mac-dinh') && !seen.has(o.palette) && seen.add(o.palette));
}

/**
 * "Tiện ích": the tools that otherwise live behind chips in the calendar header
 * (date converter, auspicious-date finder, lunar event export) plus the settings
 * (birth date → mệnh and main colour, theme) and the app info.
 *
 * Layout: one column; two columns (tools | settings) from lg, and in book mode
 * (unfolded foldable) the same two groups are the left and right panes.
 */
export function ToolsView({
  birthDate,
  userMenh,
  legacyZodiac,
  accentPref,
  palette,
  themePref,
  onOpenBirthPicker,
  onAccentChange,
  onThemeChange,
  onOpenQuickPicker,
  onOpenAuspiciousPicker,
  onOpenLunarEventModal,
  onOpenAbout,
}: Props) {
  return (
    <div className="flex-1 min-h-0 overflow-y-auto overscroll-contain pt-[env(safe-area-inset-top)]">
      <div className="max-w-2xl lg:max-w-5xl mx-auto px-4 rail:px-8 pt-4 rail:pt-8 pb-10">
        <div className="grid grid-cols-1 gap-4 rail:gap-6 items-start lg:grid-cols-2 book:grid-cols-2">

          {/* ───────────── Tools ───────────── */}
          <div className="flex flex-col gap-4 min-w-0">
            <header>
              <p className="section-label">Công cụ</p>
              <h1 className="text-2xl book:text-xl rail:text-3xl font-extrabold text-fg">Tiện ích</h1>
            </header>

            <ul className="card divide-y divide-line overflow-hidden">
              <ToolRow
                icon={<ArrowLeftRight size={18} />}
                title="Đổi ngày âm – dương"
                detail="Tra ngày dương ra ngày âm và ngược lại"
                onClick={onOpenQuickPicker}
              />
              <ToolRow
                icon={<Search size={18} />}
                title="Tìm ngày tốt"
                detail="Cưới hỏi, khởi công, khai trương"
                onClick={onOpenAuspiciousPicker}
              />
              <ToolRow
                icon={<Download size={18} />}
                title="Xuất sự kiện âm lịch"
                detail="Giỗ, sinh nhật âm lịch ra file .ics cho Google Calendar"
                onClick={onOpenLunarEventModal}
              />
            </ul>
          </div>

          {/* ───────────── Settings ───────────── */}
          <div className="flex flex-col gap-4 min-w-0">
            <p className="section-label px-1 -mb-2 lg:mt-2 book:mt-1">Cài đặt</p>

            <section className="card divide-y divide-line">
              <div className="px-4 py-3">
                <div className="flex items-center gap-3">
                  <div className="flex-1 min-w-0">
                    <p className="text-sm font-semibold text-fg">Ngày sinh</p>
                    <p className="text-xs text-fg-2">Dương lịch. Tính mệnh, tuổi xung</p>
                  </div>
                  <button
                    onClick={onOpenBirthPicker}
                    aria-label={birthDate ? `Ngày sinh ${formatDate(birthDate)}, bấm để đổi` : 'Chọn ngày sinh'}
                    className="field w-auto shrink-0 py-2 inline-flex items-center gap-2 font-semibold cursor-pointer whitespace-nowrap tabular-nums"
                  >
                    <CalendarDays size={16} className="text-fg-3" />
                    {birthDate ? formatDate(birthDate) : 'Chọn ngày'}
                  </button>
                </div>
                {userMenh ? (
                  <p className="mt-2 text-xs text-fg-2">
                    Tuổi <span className="font-semibold text-fg">{userMenh.canChi}</span> ({userMenh.lunarYear} âm lịch) ·
                    mệnh <span className="font-semibold text-fg">{ELEMENTS[userMenh.element].name}</span> – {userMenh.napAm}
                  </p>
                ) : legacyZodiac ? (
                  <p className="mt-2 text-xs text-fg-2">Đang dùng tuổi {legacyZodiac} đã chọn trước đây. Nhập ngày sinh để biết mệnh.</p>
                ) : null}
              </div>

              <div className="px-4 py-3">
                <p className="text-sm font-semibold text-fg">Màu chủ đạo</p>
                {!userMenh && <p className="text-xs text-fg-2">Nhập ngày sinh để dùng màu hợp mệnh</p>}
                <div
                  className="mt-2 grid grid-flow-col auto-cols-fr gap-1 rounded-xl bg-surface-2 p-1"
                  role="radiogroup"
                  aria-label="Màu chủ đạo"
                >
                  {accentOptions(userMenh).map(o => {
                    const active = o.palette === palette;
                    return (
                      <button
                        key={o.pref}
                        role="radio"
                        aria-checked={active}
                        onClick={() => onAccentChange(o.pref)}
                        className={cn(
                          'flex flex-col items-center justify-center gap-1 rounded-lg px-1 py-2 text-xs whitespace-nowrap transition-colors',
                          active ? 'bg-surface text-fg shadow-[var(--shadow-card)]' : 'text-fg-2 hover:text-fg',
                        )}
                      >
                        <span
                          data-palette={o.palette}
                          className={cn('w-6 h-6 rounded-full bg-brand', active && 'ring-2 ring-brand ring-offset-2 ring-offset-surface')}
                          aria-hidden="true"
                        />
                        <span className="font-semibold">{o.label}</span>
                        <span className="text-[11px] leading-none">{ELEMENTS[o.palette].color}</span>
                      </button>
                    );
                  })}
                </div>
              </div>

              <div className="px-4 py-3">
                <p className="text-sm font-semibold text-fg">Giao diện</p>
                <div className="mt-2 grid grid-cols-3 gap-1 rounded-xl bg-surface-2 p-1" role="radiogroup" aria-label="Giao diện">
                  {THEMES.map(({ pref, label, Icon }) => {
                    const active = themePref === pref;
                    return (
                      <button
                        key={pref}
                        role="radio"
                        aria-checked={active}
                        onClick={() => onThemeChange(pref)}
                        className={cn(
                          'flex flex-col items-center justify-center gap-1 rounded-lg px-1 py-2 text-xs font-semibold whitespace-nowrap transition-colors',
                          active ? 'bg-surface text-brand shadow-[var(--shadow-card)]' : 'text-fg-2 hover:text-fg',
                        )}
                      >
                        <Icon size={16} className="shrink-0" /> {label}
                      </button>
                    );
                  })}
                </div>
              </div>
            </section>

            <ul className="card divide-y divide-line overflow-hidden">
              <ToolRow
                icon={<Info size={18} />}
                title="Thông tin ứng dụng"
                detail="Cách tính âm lịch, ngày tốt xấu, xung tuổi"
                onClick={onOpenAbout}
              />
            </ul>
          </div>
        </div>
      </div>
    </div>
  );
}

function ToolRow({ icon, title, detail, onClick }: { icon: ReactNode; title: string; detail: string; onClick: () => void }) {
  return (
    <li>
      <button
        onClick={onClick}
        className="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-surface-2 active:bg-surface-3 transition-colors"
      >
        <span className="w-10 h-10 shrink-0 rounded-2xl bg-brand-soft text-brand flex items-center justify-center">{icon}</span>
        <span className="flex-1 min-w-0">
          <span className="block text-sm font-semibold text-fg">{title}</span>
          <span className="block text-xs text-fg-2 leading-snug">{detail}</span>
        </span>
        <ChevronRight size={18} className="shrink-0 text-fg-3" />
      </button>
    </li>
  );
}
