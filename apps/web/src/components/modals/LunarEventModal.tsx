import { useMemo, useState } from 'react';
import { CalendarHeart, Download, TriangleExclamationPoint, CircleCheck, Check } from 'lucide-react';
import { cn } from '../../lib/utils';
import { LunarEvent } from '../../types';
import { buildICS, generateAndDownloadICS, ExportSummary } from '../../services/icsService';
import { PresetId, PRESET_DEFINITIONS } from '../../constants/presets';
import { Sheet } from '../Sheet';
import { EVENT_COLORS } from '../../lib/eventColors';
import { colorOf, describeEvent } from '../../services/personalEvents';

interface Props {
  events: LunarEvent[];
  enabledPresets: Set<PresetId>;
  expandedPresets: LunarEvent[];
  /** Opens Sự kiện của bạn (add, edit, delete the user's events). */
  onManageEvents: () => void;
  onTogglePreset: (id: PresetId) => void;
  onClose: () => void;
}

export function LunarEventModal({
  events,
  enabledPresets,
  expandedPresets,
  onManageEvents,
  onTogglePreset,
  onClose,
}: Props) {
  const [yearCount, setYearCount] = useState(5);
  const [exportResult, setExportResult] = useState<ExportSummary | null>(null);

  const startYear = new Date().getFullYear();
  const endYear   = startYear + yearCount - 1;

  const allExportEvents = [...expandedPresets, ...events];
  // VEVENTs the file will hold: one per solar series, one per occurrence of the rest.
  const totalItems = useMemo(
    () => buildICS(allExportEvents, startYear, yearCount, '').totalEvents,
    [expandedPresets, events, startYear, yearCount],
  );

  function handleTogglePreset(id: PresetId) {
    onTogglePreset(id);
    setExportResult(null);
  }

  function handleExport() {
    setExportResult(generateAndDownloadICS(allExportEvents, startYear, yearCount));
  }

  return (
    <Sheet
      title="Xuất lịch"
      subtitle="Tạo file .ics cho Google Calendar, Apple Calendar, Outlook"
      icon={<CalendarHeart size={20} />}
      onClose={onClose}
      size="lg"
      tall
      footer={
        <button onClick={handleExport} disabled={allExportEvents.length === 0} className="btn-primary w-full py-3">
          <Download size={17} />
          {allExportEvents.length === 0 ? 'Chọn sự kiện để xuất' : `Tải file .ics · ${totalItems} mục`}
        </button>
      }
    >
      <div className="flex flex-col gap-6">

        {/* Presets */}
        <section>
          <h3 className="section-label mb-2">Ngày lễ có sẵn</h3>
          <div className="grid grid-cols-2 gap-2">
            {PRESET_DEFINITIONS.map(preset => {
              const active = enabledPresets.has(preset.id);
              return (
                <button
                  key={preset.id}
                  onClick={() => handleTogglePreset(preset.id)}
                  aria-pressed={active}
                  className={cn(
                    'relative flex flex-col items-start gap-1 rounded-2xl p-3 text-left transition-colors ring-inset',
                    active ? 'bg-brand-soft ring-2 ring-brand' : 'bg-surface-2 hover:bg-surface-3',
                  )}
                >
                  <span className="text-2xl leading-none" aria-hidden="true">{preset.emoji}</span>
                  <span className={cn('text-sm font-bold leading-tight', active ? 'text-brand' : 'text-fg')}>{preset.label}</span>
                  <span className="text-[11px] text-fg-2 leading-snug">{preset.description}</span>
                  <span className={cn(
                    'absolute top-2.5 right-2.5 w-5 h-5 rounded-full flex items-center justify-center',
                    active ? 'bg-brand text-on-brand' : 'border-2 border-line',
                  )}>
                    {active && <Check size={12} strokeWidth={3} />}
                  </span>
                </button>
              );
            })}
          </div>
        </section>

        {/* Custom events */}
        <section>
          <div className="flex items-center justify-between mb-2">
            <h3 className="section-label">Sự kiện của bạn ({events.length})</h3>
            <button onClick={onManageEvents} className="btn-soft px-3 py-1.5 text-xs">{events.length ? 'Sửa' : 'Thêm'}</button>
          </div>
          {events.length === 0 ? (
            <div className="rounded-2xl border border-dashed border-line px-4 py-6 text-center text-sm text-fg-2">
              Thêm giỗ, sinh nhật… để xuất cùng lúc.
            </div>
          ) : (
            <ul className="rounded-2xl bg-surface-2 divide-y divide-line">
              {events.map(e => (
                <li key={e.id} className="flex items-center gap-3 px-4 py-2.5">
                  <span className={cn('h-2.5 w-2.5 shrink-0 rounded-full', EVENT_COLORS[colorOf(e)].dot)} aria-hidden="true" />
                  <span className="min-w-0 flex-1">
                    <span className="block text-sm font-semibold text-fg break-words">{e.title}</span>
                    <span className="block text-xs text-fg-2">{describeEvent(e)}</span>
                  </span>
                </li>
              ))}
            </ul>
          )}
        </section>

        {/* Export range */}
        {allExportEvents.length > 0 && (
          <section>
            <div className="flex items-baseline justify-between mb-2">
              <h3 className="section-label">Khoảng thời gian</h3>
              <span className="text-sm font-bold text-fg tabular-nums">{startYear} – {endYear}</span>
            </div>
            <div className="rounded-2xl bg-surface-2 p-4">
              <div className="flex items-center gap-3">
                <input
                  type="range"
                  min={1}
                  max={30}
                  value={yearCount}
                  onChange={e => { setYearCount(+e.target.value); setExportResult(null); }}
                  className="flex-1 accent-[var(--brand)] cursor-pointer"
                  aria-label="Số năm"
                />
                <span className="w-16 text-right text-sm font-bold text-brand tabular-nums">{yearCount} năm</span>
              </div>
              <p className="mt-2 text-xs text-fg-2">
                Sự kiện lặp theo âm lịch được ghi từng lần trong {yearCount} năm này; sự kiện theo dương lịch ghi một lần kèm quy tắc lặp.
              </p>
            </div>

            {exportResult && (
              <div className="mt-3 flex flex-col gap-2">
                <p className="flex items-center gap-2 rounded-2xl bg-good-soft px-4 py-3 text-sm font-semibold text-good">
                  <CircleCheck size={17} /> Đã tạo {exportResult.totalEvents} sự kiện
                </p>
                {exportResult.skippedCount > 0 && (
                  <div className="rounded-2xl bg-gold-soft p-4">
                    <p className="flex items-center gap-2 text-xs font-bold text-fg mb-1.5">
                      <TriangleExclamationPoint size={14} className="text-gold" /> Bỏ qua {exportResult.skippedCount} mục
                    </p>
                    <ul className="flex flex-col gap-0.5 text-xs text-fg-2">
                      {exportResult.skippedReasons.map((s, i) => (
                        <li key={i}>
                          • <span className="font-semibold text-fg">{s.title}</span> năm âm {s.lunarYear}
                          {s.reason === 'leap_month_not_found' ? ' — năm này không có tháng nhuận đó' : ' — lỗi chuyển đổi ngày'}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                <p className="text-xs text-fg-2 text-center">
                  Google Calendar → Cài đặt → Nhập và xuất → chọn file .ics vừa tải
                </p>
              </div>
            )}
          </section>
        )}
      </div>
    </Sheet>
  );
}
