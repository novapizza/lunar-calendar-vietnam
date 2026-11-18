import { useState } from 'react';
import { CalendarHeart, Plus, Pencil, Trash2, Download, AlertTriangle, CheckCircle2, Check } from 'lucide-react';
import { cn } from '../../lib/utils';
import { LunarEvent } from '../../types';
import { generateAndDownloadICS, ExportSummary } from '../../services/icsService';
import { PresetId, PRESET_DEFINITIONS } from '../../constants/presets';
import { Sheet } from '../Sheet';

interface Props {
  events: LunarEvent[];
  enabledPresets: Set<PresetId>;
  expandedPresets: LunarEvent[];
  onAddEvent:    (draft: Omit<LunarEvent, 'id' | 'createdAt'>) => void;
  onDeleteEvent: (id: string) => void;
  onUpdateEvent: (id: string, patch: Partial<Omit<LunarEvent, 'id' | 'createdAt'>>) => void;
  onTogglePreset: (id: PresetId) => void;
  onClose: () => void;
}

const EMPTY_FORM = { title: '', lunarDay: 1, lunarMonth: 1, isLeapMonth: false };

export function LunarEventModal({
  events,
  enabledPresets,
  expandedPresets,
  onAddEvent,
  onDeleteEvent,
  onUpdateEvent,
  onTogglePreset,
  onClose,
}: Props) {
  const [form, setForm]           = useState(EMPTY_FORM);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [showForm, setShowForm]   = useState(false);
  const [yearCount, setYearCount] = useState(5);
  const [exportResult, setExportResult] = useState<ExportSummary | null>(null);

  const startYear = new Date().getFullYear();
  const endYear   = startYear + yearCount - 1;

  const allExportEvents = [...expandedPresets, ...events];
  const totalItems      = yearCount * allExportEvents.length;

  function handleEdit(event: LunarEvent) {
    setEditingId(event.id);
    setForm({
      title:       event.title,
      lunarDay:    event.lunarDay,
      lunarMonth:  event.lunarMonth,
      isLeapMonth: event.isLeapMonth,
    });
    setShowForm(true);
    setExportResult(null);
  }

  function handleSave() {
    const title = form.title.trim();
    if (!title) return;
    if (editingId) {
      onUpdateEvent(editingId, { ...form, title });
      setEditingId(null);
    } else {
      onAddEvent({ ...form, title });
    }
    setForm(EMPTY_FORM);
    setShowForm(false);
    setExportResult(null);
  }

  function handleCancel() {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setShowForm(false);
  }

  function handleDelete(id: string) {
    onDeleteEvent(id);
    if (editingId === id) handleCancel();
    setExportResult(null);
  }

  function handleTogglePreset(id: PresetId) {
    onTogglePreset(id);
    setExportResult(null);
  }

  function handleExport() {
    setExportResult(generateAndDownloadICS(allExportEvents, startYear, yearCount));
  }

  return (
    <Sheet
      title="Xuất lịch âm"
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
            <h3 className="section-label">Sự kiện riêng ({events.length})</h3>
            {!showForm && (
              <button
                onClick={() => { setShowForm(true); setEditingId(null); setForm(EMPTY_FORM); }}
                className="btn-soft px-3 py-1.5 text-xs"
              >
                <Plus size={14} /> Thêm
              </button>
            )}
          </div>

          {events.length === 0 && !showForm && (
            <div className="rounded-2xl border border-dashed border-line px-4 py-6 text-center text-sm text-fg-2">
              Thêm giỗ, sinh nhật âm lịch… để xuất cùng lúc.
            </div>
          )}

          {events.length > 0 && (
            <ul className="rounded-2xl bg-surface-2 divide-y divide-line">
              {events.map(event => (
                <li key={event.id} className={cn('flex items-center gap-2 pl-4 pr-1.5 py-2', editingId === event.id && 'bg-brand-soft')}>
                  <div className="flex-1 min-w-0 py-1">
                    <p className="text-sm font-semibold text-fg break-words">{event.title}</p>
                    <p className="text-xs text-fg-2">
                      Âm {event.lunarDay}/{event.lunarMonth}{event.isLeapMonth && ' · nhuận'}
                    </p>
                  </div>
                  <button onClick={() => handleEdit(event)} className="icon-btn" aria-label="Chỉnh sửa">
                    <Pencil size={16} />
                  </button>
                  <button onClick={() => handleDelete(event.id)} className="icon-btn hover:text-bad" aria-label="Xoá">
                    <Trash2 size={16} />
                  </button>
                </li>
              ))}
            </ul>
          )}

          {showForm && (
            <div className="mt-2 flex flex-col gap-3 rounded-2xl bg-surface-2 p-4">
              <p className="text-sm font-bold text-fg">{editingId ? 'Sửa sự kiện' : 'Sự kiện mới'}</p>
              <input
                type="text"
                value={form.title}
                onChange={e => setForm(f => ({ ...f, title: e.target.value }))}
                onKeyDown={e => e.key === 'Enter' && handleSave()}
                placeholder="Tên sự kiện, vd: Giỗ ông nội"
                enterKeyHint="done"
                className="field bg-surface"
                aria-label="Tên sự kiện"
              />
              <div className="grid grid-cols-2 gap-2">
                <select
                  value={form.lunarDay}
                  onChange={e => setForm(f => ({ ...f, lunarDay: +e.target.value }))}
                  className="field bg-surface font-semibold"
                  aria-label="Ngày âm"
                >
                  {Array.from({ length: 30 }, (_, i) => i + 1).map(d => <option key={d} value={d}>Ngày {d}</option>)}
                </select>
                <select
                  value={form.lunarMonth}
                  onChange={e => setForm(f => ({ ...f, lunarMonth: +e.target.value }))}
                  className="field bg-surface font-semibold"
                  aria-label="Tháng âm"
                >
                  {Array.from({ length: 12 }, (_, i) => i + 1).map(m => <option key={m} value={m}>Tháng {m}</option>)}
                </select>
              </div>
              <label className="flex items-center gap-2.5 cursor-pointer">
                <input
                  type="checkbox"
                  checked={form.isLeapMonth}
                  onChange={e => setForm(f => ({ ...f, isLeapMonth: e.target.checked }))}
                  className="w-5 h-5 rounded accent-[var(--brand)]"
                />
                <span className="text-sm text-fg">
                  Tháng nhuận <span className="text-xs text-fg-2">(chỉ chọn nếu đúng là tháng nhuận)</span>
                </span>
              </label>
              <div className="flex gap-2 justify-end">
                <button onClick={handleCancel} className="btn-ghost bg-surface">Huỷ</button>
                <button onClick={handleSave} disabled={!form.title.trim()} className="btn-primary px-6">Lưu</button>
              </div>
            </div>
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
                {allExportEvents.length} sự kiện × {yearCount} năm. Mỗi năm có ngày dương lịch riêng.
              </p>
            </div>

            {exportResult && (
              <div className="mt-3 flex flex-col gap-2">
                <p className="flex items-center gap-2 rounded-2xl bg-good-soft px-4 py-3 text-sm font-semibold text-good">
                  <CheckCircle2 size={17} /> Đã tạo {exportResult.totalEvents} sự kiện
                </p>
                {exportResult.skippedCount > 0 && (
                  <div className="rounded-2xl bg-gold-soft p-4">
                    <p className="flex items-center gap-2 text-xs font-bold text-fg mb-1.5">
                      <AlertTriangle size={14} className="text-gold" /> Bỏ qua {exportResult.skippedCount} mục
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
