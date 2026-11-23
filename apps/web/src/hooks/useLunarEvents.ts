import { useState, useEffect } from 'react';
import { LunarEvent } from '../types';
import { OldNote, noteToEvent } from '../services/personalEvents';

const STORAGE_KEY = 'lunarEvents';
/** The old day notes ("Ghi chú" card, removed 2026-11-23): moved into events once, then deleted. */
const OLD_NOTES_KEY = 'userNotes';

function load(): LunarEvent[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const events = raw ? (JSON.parse(raw) as LunarEvent[]) : [];
    const oldRaw = localStorage.getItem(OLD_NOTES_KEY);
    if (!oldRaw) return events;
    const have = new Set(events.map(e => e.id));
    const moved = (JSON.parse(oldRaw) as OldNote[])
      .filter(n => n && n.id && n.date && typeof n.content === 'string' && !have.has(n.id))
      .map(noteToEvent);
    const all = [...events, ...moved];
    // Saved before the old key goes: a failed write keeps the notes for the next start.
    localStorage.setItem(STORAGE_KEY, JSON.stringify(all));
    localStorage.removeItem(OLD_NOTES_KEY);
    return all;
  } catch {
    return [];
  }
}

/**
 * Manages user-defined lunar events (anniversaries, giỗ, etc.)
 * persisted to localStorage.
 */
export function useLunarEvents() {
  const [events, setEvents] = useState<LunarEvent[]>(load);

  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(events));
    } catch {
      // Storage quota exceeded — silently ignore
    }
  }, [events]);

  function addEvent(draft: Omit<LunarEvent, 'id' | 'createdAt'>): void {
    setEvents(prev => [
      ...prev,
      { ...draft, id: crypto.randomUUID(), createdAt: Date.now() },
    ]);
  }

  function deleteEvent(id: string): void {
    setEvents(prev => prev.filter(e => e.id !== id));
  }

  function updateEvent(id: string, patch: Partial<Omit<LunarEvent, 'id' | 'createdAt'>>): void {
    setEvents(prev => prev.map(e => (e.id === id ? { ...e, ...patch } : e)));
  }

  return { events, addEvent, deleteEvent, updateEvent };
}
