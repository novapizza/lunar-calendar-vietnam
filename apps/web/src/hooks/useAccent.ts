import { useEffect, useState } from 'react';
import { paletteFor, type AccentPref, type Element, type Menh } from '../lib/menh';

const PREF_KEY = 'accent';
/** Read by the inline script in index.html to paint the right colour before React starts. */
const PALETTE_KEY = 'palette';

function loadPref(): AccentPref {
  try {
    const v = localStorage.getItem(PREF_KEY);
    return v === 'tuong-sinh' || v === 'mac-dinh' ? v : 'ban-menh';
  } catch {
    return 'ban-menh';
  }
}

/**
 * Main colour of the app, from the user's mệnh. Sets html[data-palette]; no
 * attribute means the default red (which is also the Hỏa palette).
 */
export function useAccent(menh: Menh | null) {
  const [pref, setPref] = useState<AccentPref>(loadPref);
  const palette: Element = paletteFor(menh, pref);

  useEffect(() => {
    const root = document.documentElement;
    if (palette === 'hoa') root.removeAttribute('data-palette');
    else root.setAttribute('data-palette', palette);
    try {
      if (palette === 'hoa') localStorage.removeItem(PALETTE_KEY);
      else localStorage.setItem(PALETTE_KEY, palette);
    } catch { /* storage blocked */ }
  }, [palette]);

  useEffect(() => {
    try {
      if (pref === 'ban-menh') localStorage.removeItem(PREF_KEY);
      else localStorage.setItem(PREF_KEY, pref);
    } catch { /* storage blocked */ }
  }, [pref]);

  return { pref, setPref, palette };
}
