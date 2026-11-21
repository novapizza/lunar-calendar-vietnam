import presetData from '@lunar/shared/data/presets.json';
import { LunarEvent } from '../types';

export type PresetId = 'mong1' | 'ngay-ram' | 'tet' | 'gio-to';

export interface PresetDefinition {
  id: PresetId;
  label: string;
  description: string;
  emoji: string;
  events: LunarEvent[];
}

/** Built-in lunar event groups. Data: shared/data/presets.json. */
export const PRESET_DEFINITIONS: PresetDefinition[] = presetData.presets.map(p => ({
  id: p.id as PresetId,
  label: p.label,
  description: p.description,
  emoji: p.emoji,
  events: p.events.map(e => ({
    id: e.id,
    title: e.title,
    lunarDay: e.lunarDay,
    lunarMonth: e.lunarMonth,
    isLeapMonth: false,
    createdAt: 0,
  })),
}));
