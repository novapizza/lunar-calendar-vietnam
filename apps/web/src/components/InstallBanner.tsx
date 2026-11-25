import { useEffect, useState } from 'react';
import { Download, ExternalLink, Share, SquarePlus, X } from 'lucide-react';
import { isAndroid, OPEN_APP_URL } from '../lib/androidApp';
import {
  canPromptInstall,
  isIosSafari,
  isStandalone,
  onInstallAvailabilityChange,
  promptInstall,
} from '../lib/pwa';
import { AppLogo } from './AppLogo';

const DISMISS_KEY = 'installBannerDismissedAt';
/** The Android app's banner has its own key: closing the old PWA banner does not hide it. */
const APP_DISMISS_KEY = 'appBannerDismissedAt';
const DISMISS_DAYS = 30;

function recentlyDismissed(key: string): boolean {
  try {
    const at = Number(localStorage.getItem(key));
    return at > 0 && Date.now() - at < DISMISS_DAYS * 86_400_000;
  } catch {
    return false;
  }
}

/**
 * Invites the user to install the app.
 * - Android: the Android app instead of the PWA (user, 2026-11-25): "Mở app" opens it, or its
 *   Google Play page when it is not installed (lib/androidApp). Shown in the installed PWA too.
 * - Chrome / Edge: one-tap install button.
 * - iOS Safari: short "Share → Add to Home Screen" instructions.
 * Hidden when already installed, or for 30 days after the user closes it.
 */
export function InstallBanner() {
  const [canInstall, setCanInstall] = useState(canPromptInstall);
  const [android] = useState(isAndroid);
  const key = android ? APP_DISMISS_KEY : DISMISS_KEY;
  const [dismissed, setDismissed] = useState(() => recentlyDismissed(key));

  useEffect(() => onInstallAvailabilityChange(() => setCanInstall(canPromptInstall())), []);

  const dismiss = () => {
    setDismissed(true);
    try { localStorage.setItem(key, String(Date.now())); } catch { /* storage blocked */ }
  };
  if (dismissed) return null;

  if (android) {
    return (
      <div className="card col-span-full flex items-center gap-3 p-3 pl-3.5">
        <AppLogo className="w-10 h-10 rounded-xl shrink-0" />
        <div className="flex-1 min-w-0">
          <div className="text-sm font-bold text-fg">Lịch Âm đã có app Android</div>
          <div className="text-xs text-fg-2 leading-snug mt-0.5">Có widget, nhắc sự kiện. Chưa cài sẽ mở CH Play.</div>
        </div>
        <a href={OPEN_APP_URL} className="btn-primary shrink-0 px-3 py-2 text-xs">
          <ExternalLink size={14} /> Mở app
        </a>
        <button
          onClick={dismiss}
          aria-label="Đóng"
          className="icon-btn shrink-0 w-8 h-8 -mr-1 text-fg-3"
        >
          <X size={16} />
        </button>
      </div>
    );
  }

  if (isStandalone()) return null;
  const ios = isIosSafari();
  if (!canInstall && !ios) return null;

  return (
    <div className="card col-span-full flex items-center gap-3 p-3 pl-3.5">
      <AppLogo className="w-10 h-10 rounded-xl shrink-0" />
      <div className="flex-1 min-w-0">
        <div className="text-sm font-bold text-fg">Cài Lịch Âm lên màn hình chính</div>
        {ios ? (
          <div className="text-xs text-fg-2 leading-snug mt-0.5">
            Nhấn <Share size={12} className="inline -mt-0.5 text-brand" /> Chia sẻ, rồi chọn{' '}
            <span className="whitespace-nowrap">
              <SquarePlus size={12} className="inline -mt-0.5 text-brand" /> Thêm vào MH chính
            </span>
          </div>
        ) : (
          <div className="text-xs text-fg-2 mt-0.5">Mở nhanh, dùng được cả khi không có mạng</div>
        )}
      </div>
      {!ios && (
        <button
          onClick={async () => { if (await promptInstall()) dismiss(); }}
          className="btn-primary shrink-0 px-3 py-2 text-xs"
        >
          <Download size={14} /> Cài đặt
        </button>
      )}
      <button
        onClick={dismiss}
        aria-label="Đóng"
        className="icon-btn shrink-0 w-8 h-8 -mr-1 text-fg-3"
      >
        <X size={16} />
      </button>
    </div>
  );
}
