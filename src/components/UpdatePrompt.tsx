import { RefreshCw, X } from 'lucide-react';
import { useRegisterSW } from 'virtual:pwa-register/react';

const CHECK_EVERY_MS = 60 * 60 * 1000;

/**
 * Registers the service worker and shows a small toast when a new version
 * has been downloaded. The page only reloads when the user taps "Cập nhật".
 */
export function UpdatePrompt() {
  const {
    needRefresh: [needRefresh, setNeedRefresh],
    updateServiceWorker,
  } = useRegisterSW({
    onRegisteredSW(_url, registration) {
      // An installed app can stay open for days — check for updates hourly.
      if (registration) setInterval(() => { registration.update().catch(() => {}); }, CHECK_EVERY_MS);
    },
  });

  if (!needRefresh) return null;

  return (
    <div
      role="status"
      className="fixed left-3 right-3 md:left-auto md:right-6 md:w-80 z-[60] bottom-[calc(4.75rem+env(safe-area-inset-bottom))] md:bottom-6 flex items-center gap-3 rounded-2xl bg-fg text-bg px-4 py-3 shadow-[var(--shadow-float)] animate-sheet-in"
    >
      <RefreshCw size={16} className="shrink-0 opacity-70" />
      <span className="flex-1 text-sm">Đã có phiên bản mới</span>
      <button
        onClick={() => updateServiceWorker(true)}
        className="btn-primary shrink-0 px-3 py-1.5 text-xs"
      >
        Cập nhật
      </button>
      <button onClick={() => setNeedRefresh(false)} aria-label="Để sau" className="shrink-0 p-1 opacity-60">
        <X size={16} />
      </button>
    </div>
  );
}
