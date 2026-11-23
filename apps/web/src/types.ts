export interface LunarInfo {
  solarDate: Date;
  lunarDay: number;
  lunarMonth: number;
  lunarYear: number;
  isLeap: boolean;
  canChiDay: string;
  canChiMonth: string;
  canChiYear: string;
  tietKhi: string;
  gioHoangDao: string[];
  isHoliday?: string;
  stars: string[];
  truc: string;
  rating: 'good' | 'neutral' | 'bad';
  activities: {
    wedding: boolean;
    moving: boolean;
    opening: boolean;
  };
}

export interface AuspiciousDate {
  date: Date;
  lunarDate: string;
  score: number;
  stars: string[];
  truc: string;
  rating: 'good' | 'neutral' | 'bad';
}

export interface DayRating {
  name: string;
  description: string;
  isGood: boolean;
}

/** The calendar of a personal event's monthly / yearly rule. */
export type EventCalendar = 'lunar' | 'solar';
export type EventRepeat = 'none' | 'daily' | 'weekly' | 'monthly' | 'yearly';
export type EventColor = 'gold' | 'brand' | 'lunar' | 'good' | 'clash';

/**
 * A personal event (giỗ, sinh nhật, hẹn…), also the built-in presets. Spec and defaults:
 * shared/design/events.md. Fields are named after the lunar calendar because events were
 * lunar-only at first; stored data keeps those names.
 */
export interface LunarEvent {
  id: string;
  title: string;
  /** Day of the monthly / yearly rule in `calendar`: 1-30 lunar, 1-31 solar. */
  lunarDay: number;
  /** Month 1-12 of the yearly rule in `calendar`. */
  lunarMonth: number;
  /** Lunar yearly only: the leap copy of `lunarMonth`. */
  isLeapMonth: boolean;
  /** Absent: 'lunar'. */
  calendar?: EventCalendar;
  /** Absent: 'yearly'. */
  repeat?: EventRepeat;
  /** First day, YYYY-MM-DD (solar). Required for none / daily / weekly; a lower bound otherwise. */
  start?: string;
  /** Weekly: 0 = Monday … 6 = Sunday. Absent: the start's weekday. */
  weekdays?: number[];
  /** Last day an occurrence may start, YYYY-MM-DD. */
  until?: string;
  /** At most this many occurrences counted from `start`. */
  count?: number;
  /** Occurrence start days removed, YYYY-MM-DD. */
  exceptions?: string[];
  /** Absent: true. */
  allDay?: boolean;
  /** HH:mm; absent: 09:00 / 10:00. */
  startTime?: string;
  endTime?: string;
  /** Days an occurrence covers; absent: 1. */
  days?: number;
  /** Minutes before the start time (all day: before 08:00 of the start day). */
  reminders?: number[];
  notes?: string;
  location?: string;
  /** Absent: 'gold'. */
  color?: EventColor;
  createdAt: number;
}

export interface UpcomingEvent {
  solarDate: Date;
  title: string;
  lunarDay?: number;
  lunarMonth?: number;
  source: 'lunar' | 'solar';
  /** Absent for holidays, mồng 1 and rằm. 'personal': one of the user's own events. */
  kind?: 'observance' | 'festival' | 'personal';
  /** Personal events: the event, its colour and its time ("09:00 – 10:00"; absent when all day). */
  eventId?: string;
  color?: EventColor;
  time?: string;
  daysUntil: number;
}
