/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import { useState, useMemo, useEffect } from 'react';
import { CHI_NAMES } from './constants/calendar';
import { useClock } from './hooks/useClock';
import { useNotes } from './hooks/useNotes';
import { useLunarEvents } from './hooks/useLunarEvents';
import { useLunarPresets } from './hooks/useLunarPresets';
import { getLunarInfo, getHoliday, findBestDates } from './services/lunarService';
import { NavSidebar, NavTab } from './components/NavSidebar';
import { TodayView } from './components/views/TodayView';
import { CalendarView } from './components/views/CalendarView';
import { EventsView } from './components/views/EventsView';
import { QuickDatePickerModal } from './components/modals/QuickDatePickerModal';
import { AuspiciousDateModal } from './components/modals/AuspiciousDateModal';
import { AboutModal } from './components/modals/AboutModal';
import { LunarEventModal } from './components/modals/LunarEventModal';
import { AuspiciousDate } from './types';

function loadZodiac(): string | null {
  try { return localStorage.getItem('userZodiac'); } catch { return null; }
}
function saveZodiac(value: string): void {
  try { localStorage.setItem('userZodiac', value); } catch { /* quota */ }
}

export default function App() {
  const [activeTab,    setActiveTab]    = useState<NavTab>('today');
  const [currentDate,  setCurrentDate]  = useState(() => new Date());
  const [selectedDate, setSelectedDate] = useState(() => new Date());

  const [isQuickPickerOpen,      setIsQuickPickerOpen]      = useState(false);
  const [isAuspiciousPickerOpen, setIsAuspiciousPickerOpen] = useState(false);
  const [isAboutOpen,            setIsAboutOpen]            = useState(false);
  const [isLunarEventModalOpen,  setIsLunarEventModalOpen]  = useState(false);

  const [activeActivity,  setActiveActivity]  = useState<string | null>(null);
  const [auspiciousDates, setAuspiciousDates] = useState<AuspiciousDate[]>([]);
  const [userZodiac,      setUserZodiac]      = useState<string | null>(loadZodiac);

  const { now, currentTraditionalHour } = useClock();
  const { notes, selectedDateNotes, newNoteContent, setNewNoteContent, addNote, deleteNote } =
    useNotes(selectedDate);
  const { events: lunarEvents, addEvent, deleteEvent, updateEvent } = useLunarEvents();
  const { enabled: enabledPresets, toggle: togglePreset, expandedPresets } = useLunarPresets();

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
    ),
    [selectedLunarInfo, selectedDate],
  );

  const anyModalOpen =
    isQuickPickerOpen || isAuspiciousPickerOpen || isAboutOpen || isLunarEventModalOpen;

  // While a modal is open: hide the chat bubble (body class) and let Escape close it.
  useEffect(() => {
    if (!anyModalOpen) return;
    document.body.classList.add('modal-open');
    const onKey = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      setIsQuickPickerOpen(false);
      setIsAuspiciousPickerOpen(false);
      setIsAboutOpen(false);
      setIsLunarEventModalOpen(false);
      setActiveActivity(null);
      setAuspiciousDates([]);
    };
    window.addEventListener('keydown', onKey);
    return () => {
      document.body.classList.remove('modal-open');
      window.removeEventListener('keydown', onKey);
    };
  }, [anyModalOpen]);

  // -------------------------------------------------------------------------
  // Handlers
  // -------------------------------------------------------------------------

  const handleTabChange = (tab: NavTab) => {
    setActiveTab(tab);
    // Switching to "Hôm nay" always resets to today
    if (tab === 'today') {
      const today = new Date();
      setSelectedDate(today);
      setCurrentDate(today);
    }
  };

  const handleZodiacChange = (zodiac: string) => {
    setUserZodiac(zodiac);
    saveZodiac(zodiac);
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
    <div className="h-dvh flex flex-col md:flex-row overflow-hidden bg-bg">

      <NavSidebar activeTab={activeTab} onTabChange={handleTabChange} />

      <main className="flex-1 min-h-0 overflow-hidden flex flex-col">
        {activeTab === 'today' && (
          <TodayView
            today={selectedDate}
            now={now}
            currentTraditionalHour={currentTraditionalHour}
            lunarInfo={selectedLunarInfo}
            userZodiac={userZodiac}
            zodiacClash={zodiacClash}
            holiday={holiday}
            todayNotes={selectedDateNotes}
            newNoteContent={newNoteContent}
            onNewNoteChange={setNewNoteContent}
            onAddNote={addNote}
            onDeleteNote={deleteNote}
            onZodiacChange={handleZodiacChange}
            onDayChange={date => {
              setSelectedDate(date);
              setCurrentDate(date);
            }}
            onOpenAbout={() => setIsAboutOpen(true)}
          />
        )}

        {activeTab === 'calendar' && (
          <CalendarView
            currentDate={currentDate}
            selectedDate={selectedDate}
            notes={notes}
            userZodiac={userZodiac}
            selectedLunarInfo={selectedLunarInfo}
            holiday={holiday}
            selectedDateNotes={selectedDateNotes}
            newNoteContent={newNoteContent}
            onNewNoteChange={setNewNoteContent}
            onAddNote={addNote}
            onDeleteNote={deleteNote}
            onCurrentDateChange={setCurrentDate}
            onSelectedDateChange={date => {
              setSelectedDate(date);
              setCurrentDate(date);
            }}
            onOpenQuickPicker={() => setIsQuickPickerOpen(true)}
            onOpenAuspiciousPicker={() => setIsAuspiciousPickerOpen(true)}
            onOpenLunarEventModal={() => setIsLunarEventModalOpen(true)}
          />
        )}

        {activeTab === 'events' && <EventsView onOpenAbout={() => setIsAboutOpen(true)} />}
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

      {isAboutOpen && (
        <AboutModal onClose={() => setIsAboutOpen(false)} />
      )}

      {isLunarEventModalOpen && (
        <LunarEventModal
          events={lunarEvents}
          enabledPresets={enabledPresets}
          expandedPresets={expandedPresets}
          onAddEvent={addEvent}
          onDeleteEvent={deleteEvent}
          onUpdateEvent={updateEvent}
          onTogglePreset={togglePreset}
          onClose={() => setIsLunarEventModalOpen(false)}
        />
      )}

    </div>
  );
}
