/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState, useMemo, useEffect } from 'react';
import { isSameDay } from 'date-fns';
import { CHI_NAMES } from './constants/calendar';
import { useClock } from './hooks/useClock';
import { useLunarEvents, useBuiltinReminders } from './hooks/useLunarEvents';
import { builtinReminderOf } from './services/builtinReminders';
import { useLunarPresets } from './hooks/useLunarPresets';
import { useTheme } from './hooks/useTheme';
import { useAccent } from './hooks/useAccent';
import { menhOfBirthDate } from './lib/menh';
import { getLunarInfo, getHoliday, getObservance, findBestDates } from './services/lunarService';
import { NavSidebar, NavTab } from './components/NavSidebar';
import { TodayView } from './components/views/TodayView';
import { CalendarView } from './components/views/CalendarView';
import { EventsView } from './components/views/EventsView';
import { ToolsView } from './components/views/ToolsView';
import { QuickDatePickerModal } from './components/modals/QuickDatePickerModal';
import { MonthYearPickerModal } from './components/modals/MonthYearPickerModal';
import { BirthDatePickerModal } from './components/modals/BirthDatePickerModal';
import { AuspiciousDateModal } from './components/modals/AuspiciousDateModal';
import { AboutModal } from './components/modals/AboutModal';
import { LunarEventModal } from './components/modals/LunarEventModal';
import { PersonalEventsModal } from './components/modals/PersonalEventsModal';
import { EventEditorModal, EventDraft, BuiltinDay } from './components/modals/EventEditorModal';
import { EventDetailModal, ChangeScope } from './components/modals/EventDetailModal';
import { DayEvent, isoOf, nextOccurrence, personalEventsOn } from './services/personalEvents';
import { useEventReminders } from './hooks/useEventReminders';
import { AuspiciousDate } from './types';

function loadZodiac(): string | null {
  try { return localStorage.getItem('userZodiac'); } catch { return null; }
}
/** Solar birth date "YYYY-MM-DD"; gives the mệnh (main colour) and the zodiac. */
function loadBirthDate(): string | null {
  try { return localStorage.getItem('userBirthDate'); } catch { return null; }
}
function saveBirthDate(value: string | null): void {
  try {
    if (value) localStorage.setItem('userBirthDate', value);
    else localStorage.removeItem('userBirthDate');
  } catch { /* quota */ }
}

export default function App() {
  const [activeTab,    setActiveTab]    = useState<NavTab>('today');
  const [currentDate,  setCurrentDate]  = useState(() => new Date());
  const [selectedDate, setSelectedDate] = useState(() => new Date());

  const [isQuickPickerOpen,      setIsQuickPickerOpen]      = useState(false);
  const [isMonthPickerOpen,      setIsMonthPickerOpen]      = useState(false);
  const [isAuspiciousPickerOpen, setIsAuspiciousPickerOpen] = useState(false);
  const [isAboutOpen,            setIsAboutOpen]            = useState(false);
  const [isLunarEventModalOpen,  setIsLunarEventModalOpen]  = useState(false);
  const [isPersonalEventsOpen,   setIsPersonalEventsOpen]   = useState(false);
  /** The event editor: a new event (eventId null) on `day`, or an edit of an occurrence (single: that one only). */
  const [editor, setEditor] = useState<{ eventId: string | null; day: string; single?: boolean } | null>(null);
  /** The event detail: event and occurrence start day. */
  const [detail, setDetail] = useState<{ eventId: string; start: string } | null>(null);
  const [isBirthPickerOpen,      setIsBirthPickerOpen]      = useState(false);

  const [activeActivity,  setActiveActivity]  = useState<string | null>(null);
  const [auspiciousDates, setAuspiciousDates] = useState<AuspiciousDate[]>([]);
  const [userBirthDate,   setUserBirthDate]   = useState<string | null>(loadBirthDate);
  /** Zodiac picked before the app asked for the birth date; used until one is entered. */
  const [legacyZodiac] = useState<string | null>(loadZodiac);

  const { pref: themePref, setPref: setThemePref, cycle: cycleTheme } = useTheme();
  const userMenh = useMemo(() => menhOfBirthDate(userBirthDate), [userBirthDate]);
  const userZodiac: string | null = userMenh?.chi ?? legacyZodiac;
  const { pref: accentPref, setPref: setAccentPref, palette } = useAccent(userMenh);
  const { now, currentTraditionalHour } = useClock();
  const { events: lunarEvents, addEvent, deleteEvent, updateEvent } = useLunarEvents();
  const { reminders: builtinReminders, saveReminder, removeReminder } = useBuiltinReminders();
  const { enabled: enabledPresets, toggle: togglePreset, expandedPresets } = useLunarPresets();
  const remindable = useMemo(() => [...lunarEvents, ...builtinReminders], [lunarEvents, builtinReminders]);
  useEventReminders(remindable);
  /** The reminder form of a built-in day (Sự kiện tab), opened on its occurrence `day`. */
  const [builtinEditor, setBuiltinEditor] = useState<{ day: string } & BuiltinDay | null>(null);

  // -------------------------------------------------------------------------
  // Derived data
  // -------------------------------------------------------------------------

  const selectedLunarInfo = useMemo(() => getLunarInfo(selectedDate), [selectedDate]);

  const zodiacClash = useMemo<boolean | null>(() => {
    if (!userZodiac) return null;
    const dayChi  = selectedLunarInfo.canChiDay.split(' ')[1];
    const userIdx = CHI_NAMES.indexOf(userZodiac as typeof CHI_NAMES[number]);
    const dayIdx  = CHI_NAMES.indexOf(dayChi as typeof CHI_NAMES[number]);
    if (userIdx === -1 || dayIdx === -1) return null;
    return Math.abs(userIdx - dayIdx) === 6;
  }, [userZodiac, selectedLunarInfo.canChiDay]);

  const holiday = useMemo(
    () => getHoliday(
      selectedLunarInfo.lunarDay,
      selectedLunarInfo.lunarMonth,
      selectedDate.getDate(),
      selectedDate.getMonth() + 1,
      selectedDate.getFullYear(),
    ),
    [selectedLunarInfo, selectedDate],
  );
  // The sheet names a day without a holiday after the user's own event first, then an observance.
  const observance = useMemo(
    () => personalEventsOn(lunarEvents, selectedDate)[0]?.title
      ?? getObservance(selectedLunarInfo.lunarDay, selectedLunarInfo.lunarMonth, selectedDate),
    [selectedLunarInfo, selectedDate, lunarEvents],
  );

  const anyModalOpen =
    isQuickPickerOpen || isMonthPickerOpen || isAuspiciousPickerOpen || isAboutOpen || isLunarEventModalOpen || isPersonalEventsOpen || isBirthPickerOpen || !!editor || !!detail || !!builtinEditor;

  // While a modal is open, Escape closes it.
  useEffect(() => {
    if (!anyModalOpen) return;
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      setIsQuickPickerOpen(false);
      setIsMonthPickerOpen(false);
      setIsAuspiciousPickerOpen(false);
      setIsAboutOpen(false);
      setIsLunarEventModalOpen(false);
      setBuiltinEditor(null);
      // The topmost sheet only: an event editor or detail sits on the list it came from.
      if (editor || detail) { setEditor(null); setDetail(null); return; }
      setIsPersonalEventsOpen(false);
      setIsBirthPickerOpen(false);
      setActiveActivity(null);
      setAuspiciousDates([]);
    };
    window.addEventListener('keydown', onKey);
    return () => {
      window.removeEventListener('keydown', onKey);
    };
  }, [anyModalOpen, editor, detail]);

  // -------------------------------------------------------------------------
  // Handlers
  // -------------------------------------------------------------------------

  const openEvent = (item: DayEvent) => setDetail({ eventId: item.event.id, start: item.start });
  const addEventOn = (day: string) => setEditor({ eventId: null, day });
  const detailEvent = detail ? lunarEvents.find(e => e.id === detail.eventId) ?? null : null;
  const editorEvent = editor?.eventId ? lunarEvents.find(e => e.id === editor.eventId) ?? null : null;

  function saveEvent(draft: EventDraft) {
    if (!editor) return;
    if (editor.eventId && editor.single && editorEvent) {
      // This occurrence only: drop it from the series, keep the edited copy as a one-off event.
      updateEvent(editorEvent.id, { exceptions: [...(editorEvent.exceptions ?? []), editor.day] });
      addEvent({ ...draft, repeat: 'none' });
    } else if (editor.eventId) {
      updateEvent(editor.eventId, draft);
    } else {
      addEvent(draft);
    }
    setEditor(null);
    setDetail(null);
  }

  function deleteOccurrence(scope: ChangeScope) {
    if (!detail || !detailEvent) return;
    if (scope === 'one') updateEvent(detailEvent.id, { exceptions: [...(detailEvent.exceptions ?? []), detail.start] });
    else deleteEvent(detailEvent.id);
    setDetail(null);
  }

  const handleTabChange = (tab: NavTab) => {
    setActiveTab(tab);
    // Switching to "Hôm nay" always resets to today
    if (tab === 'today') {
      // Keep the same Date when it is already today, so nothing re-renders or turns.
      const today = new Date();
      setSelectedDate(d => (isSameDay(d, today) ? d : today));
      setCurrentDate(d => (isSameDay(d, today) ? d : today));
    }
  };

  /**
   * Show the month of `date` in the month calendar and move the selected day
   * into it: same day number, or the 1st when that month is shorter
   * (e.g. 31 -> April, 29 Feb -> 2027). Used by the month arrows, swipe and
   * the month/year picker.
   */
  const showMonth = (date: Date) => {
    const y = date.getFullYear();
    const m = date.getMonth();
    const day = selectedDate.getDate();
    const daysInMonth = new Date(y, m + 1, 0).getDate();
    const next = new Date(y, m, day <= daysInMonth ? day : 1);
    setCurrentDate(next);
    setSelectedDate(next);
  };

  const handleBirthDateChange = (value: string | null) => {
    setUserBirthDate(value);
    saveBirthDate(value);
  };

  const handleFindDates = (key: string) => {
    setActiveActivity(key);
    setAuspiciousDates(findBestDates(key, 2));
  };

  const handleSelectAuspiciousDate = (date: Date) => {
    setCurrentDate(date);
    setSelectedDate(date);
    setIsAuspiciousPickerOpen(false);
    setActiveTab('calendar');
  };

  const handleAuspiciousPickerClose = () => {
    setIsAuspiciousPickerOpen(false);
    setActiveActivity(null);
    setAuspiciousDates([]);
  };

  // -------------------------------------------------------------------------
  // Render
  // -------------------------------------------------------------------------

  return (
    <div className="h-dvh flex flex-col rail:flex-row overflow-hidden bg-bg">

      <NavSidebar activeTab={activeTab} onTabChange={handleTabChange} />

      <main className="flex-1 min-h-0 overflow-hidden flex flex-col">
        {activeTab === 'today' && (
          <TodayView
            today={selectedDate}
            now={now}
            currentTraditionalHour={currentTraditionalHour}
            lunarInfo={selectedLunarInfo}
            userZodiac={userZodiac}
            userMenh={userMenh}
            zodiacClash={zodiacClash}
            holiday={holiday}
            observance={observance}
            personalEvents={lunarEvents}
            onOpenEvent={openEvent}
            onAddEvent={addEventOn}
            onOpenSettings={() => handleTabChange('tools')}
            onDayChange={date => {
              setSelectedDate(date);
              setCurrentDate(date);
            }}
            themePref={themePref}
            onCycleTheme={cycleTheme}
          />
        )}

        {activeTab === 'calendar' && (
          <CalendarView
            currentDate={currentDate}
            currentTraditionalHour={currentTraditionalHour}
            selectedDate={selectedDate}
            personalEvents={lunarEvents}
            onOpenEvent={openEvent}
            onAddEvent={addEventOn}
            userZodiac={userZodiac}
            selectedLunarInfo={selectedLunarInfo}
            holiday={holiday}
            onShowMonth={showMonth}
            onSelectedDateChange={date => {
              setSelectedDate(date);
              setCurrentDate(date);
            }}
            onOpenQuickPicker={() => setIsQuickPickerOpen(true)}
            onOpenMonthPicker={() => setIsMonthPickerOpen(true)}
            onOpenAuspiciousPicker={() => setIsAuspiciousPickerOpen(true)}
            onOpenLunarEventModal={() => setIsLunarEventModalOpen(true)}
          />
        )}

        {activeTab === 'events' && (
          <EventsView
            personalEvents={lunarEvents}
            onOpenPersonalEvents={() => setIsPersonalEventsOpen(true)}
            onOpenEvent={(eventId, day) => setDetail({ eventId, start: day })}
            builtinReminders={builtinReminders}
            onOpenBuiltin={e => e.ref && setBuiltinEditor({ ref: e.ref, title: e.title, calendar: e.source, day: isoOf(e.solarDate) })}
          />
        )}

        {activeTab === 'tools' && (
          <ToolsView
            birthDate={userBirthDate}
            userMenh={userMenh}
            legacyZodiac={legacyZodiac}
            accentPref={accentPref}
            palette={palette}
            themePref={themePref}
            onOpenBirthPicker={() => setIsBirthPickerOpen(true)}
            onAccentChange={setAccentPref}
            onThemeChange={setThemePref}
            onOpenQuickPicker={() => setIsQuickPickerOpen(true)}
            onOpenAuspiciousPicker={() => setIsAuspiciousPickerOpen(true)}
            onOpenLunarEventModal={() => setIsLunarEventModalOpen(true)}
            onOpenAbout={() => setIsAboutOpen(true)}
          />
        )}
      </main>

      {/* Modals */}
      {isAuspiciousPickerOpen && (
        <AuspiciousDateModal
          activeActivity={activeActivity}
          auspiciousDates={auspiciousDates}
          onFindDates={handleFindDates}
          onSelectDate={handleSelectAuspiciousDate}
          onClose={handleAuspiciousPickerClose}
        />
      )}

      {isQuickPickerOpen && (
        <QuickDatePickerModal
          initialDate={selectedDate}
          onClose={() => setIsQuickPickerOpen(false)}
          onSelect={date => {
            setCurrentDate(date);
            setSelectedDate(date);
            setIsQuickPickerOpen(false);
            setActiveTab('calendar');
          }}
        />
      )}

      {isMonthPickerOpen && (
        <MonthYearPickerModal
          value={currentDate}
          onClose={() => setIsMonthPickerOpen(false)}
          onSelect={date => {
            showMonth(date);
            setIsMonthPickerOpen(false);
          }}
        />
      )}

      {isBirthPickerOpen && (
        <BirthDatePickerModal
          value={userBirthDate}
          onClose={() => setIsBirthPickerOpen(false)}
          onSelect={handleBirthDateChange}
        />
      )}

      {isAboutOpen && (
        <AboutModal onClose={() => setIsAboutOpen(false)} />
      )}

      {isPersonalEventsOpen && (
        <PersonalEventsModal
          events={lunarEvents}
          onOpen={e => {
            const today = isoOf(new Date());
            setDetail({ eventId: e.id, start: nextOccurrence(e, today) ?? e.start ?? today });
          }}
          onAdd={() => addEventOn(isoOf(selectedDate))}
          onClose={() => setIsPersonalEventsOpen(false)}
        />
      )}

      {isLunarEventModalOpen && (
        <LunarEventModal
          events={lunarEvents}
          enabledPresets={enabledPresets}
          expandedPresets={expandedPresets}
          onManageEvents={() => { setIsLunarEventModalOpen(false); setIsPersonalEventsOpen(true); }}
          onTogglePreset={togglePreset}
          onClose={() => setIsLunarEventModalOpen(false)}
        />
      )}

      {detail && detailEvent && (
        <EventDetailModal
          event={detailEvent}
          start={detail.start}
          onEdit={scope => setEditor({ eventId: detailEvent.id, day: detail.start, single: scope === 'one' })}
          onDelete={deleteOccurrence}
          onClose={() => setDetail(null)}
        />
      )}

      {editor && (
        <EventEditorModal
          key={`${editor.eventId}-${editor.day}-${editor.single}`}
          event={editorEvent}
          day={editor.day}
          single={editor.single}
          onSave={saveEvent}
          onClose={() => setEditor(null)}
        />
      )}

      {builtinEditor && (
        <EventEditorModal
          key={`${builtinEditor.ref}-${builtinEditor.day}`}
          event={builtinReminderOf(builtinReminders, builtinEditor.ref) ?? null}
          day={builtinEditor.day}
          builtin={builtinEditor}
          onSave={draft => { saveReminder(draft); setBuiltinEditor(null); }}
          onDelete={() => { removeReminder(builtinEditor.ref); setBuiltinEditor(null); }}
          onClose={() => setBuiltinEditor(null)}
        />
      )}

    </div>
  );
}
