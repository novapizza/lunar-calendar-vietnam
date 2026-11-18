import { useCallback, useEffect, useState } from 'react';

export type ThemePref = 'system' | 'light' | 'dark';

const STORAGE_KEY = 'theme';
const THEME_COLORS = { light: '#F7F6F3', dark: '#0E1116' } as const;
const darkQuery = () => window.matchMedia('(prefers-color-scheme: dark)');

function loadPref(): ThemePref {
  try {
    const v = localStorage.getItem(STORAGE_KEY);
    return v === 'light' || v === 'dark' ? v : 'system';
  } catch {
    return 'system';
  }
}

/** Sets html[data-theme] and the browser/status-bar colour for the given preference. */
function applyTheme(pref: ThemePref) {
  const root = document.documentElement;
  if (pref === 'system') root.removeAttribute('data-theme');
  else root.setAttribute('data-theme', pref);

  const resolved = pref === 'system' ? (darkQuery().matches ? 'dark' : 'light') : pref;
  document.querySelectorAll('meta[name="theme-color"]').forEach(meta => {
    // With a fixed theme both tags must carry the same colour, or the system one wins.
    const media = meta.getAttribute('data-media') ?? meta.getAttribute('media');
    if (!meta.hasAttribute('data-media') && media) meta.setAttribute('data-media', media);
    if (pref === 'system') {
      meta.setAttribute('media', media ?? '');
      meta.setAttribute('content', media?.includes('dark') ? THEME_COLORS.dark : THEME_COLORS.light);
    } else {
      meta.removeAttribute('media');
      meta.setAttribute('content', THEME_COLORS[resolved]);
    }
  });
}

/** Light / dark / follow-system theme, saved on this device. */
export function useTheme() {
  const [pref, setPref] = useState<ThemePref>(loadPref);

  useEffect(() => {
    applyTheme(pref);
    try {
      if (pref === 'system') localStorage.removeItem(STORAGE_KEY);
      else localStorage.setItem(STORAGE_KEY, pref);
    } catch { /* storage blocked */ }
  }, [pref]);

  const cycle = useCallback(() => {
    setPref(p => (p === 'system' ? 'light' : p === 'light' ? 'dark' : 'system'));
  }, []);

  return { pref, setPref, cycle };
}
