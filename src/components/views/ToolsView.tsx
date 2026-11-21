import type { ReactNode } from 'react';
import {
  ArrowLeftRight, Search, Download, ChevronRight, Info, Monitor, Sun, Moon,
} from 'lucide-react';
import { cn } from '../../lib/utils';
import { CHI_NAMES } from '../../constants/calendar';
import type { ThemePref } from '../../hooks/useTheme';

interface Props {
  userZodiac: string | null;
  themePref: ThemePref;
  onZodiacChange: (zodiac: string) => void;
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

/**
 * "Tiện ích": the tools that otherwise live behind chips in the calendar header
 * (date converter, auspicious-date finder, lunar event export) plus the settings
 * (zodiac, theme) and the app info.
 *
 * Layout: one column; two columns (tools | settings) from lg, and in book mode
 * (unfolded foldable) the same two groups are the left and right panes.
 */
export function ToolsView({
  userZodiac,
  themePref,
  onZodiacChange,
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
              <div className="flex items-center gap-3 px-4 py-3">
                <div className="flex-1 min-w-0">
                  <p className="text-sm font-semibold text-fg">Tuổi của bạn</p>
                  <p className="text-xs text-fg-2">Báo ngày xung tuổi trên lịch</p>
                </div>
                <select
                  value={userZodiac ?? ''}
                  onChange={e => onZodiacChange(e.target.value)}
                  aria-label="Chọn tuổi"
                  className="field w-24 shrink-0 py-2 font-semibold cursor-pointer"
                >
                  <option value="">Chọn</option>
                  {CHI_NAMES.map(chi => <option key={chi} value={chi}>{chi}</option>)}
                </select>
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
