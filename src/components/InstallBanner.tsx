import { useEffect, useState } from 'react';
import { Download, Share, SquarePlus, X } from 'lucide-react';
import {
  canPromptInstall,
  isIosSafari,
  isStandalone,
  onInstallAvailabilityChange,
  promptInstall,
} from '../lib/pwa';

const DISMISS_KEY = 'installBannerDismissedAt';
const DISMISS_DAYS = 30;

function recentlyDismissed(): boolean {
  try {
    const at = Number(localStorage.getItem(DISMISS_KEY));
    return at > 0 && Date.now() - at < DISMISS_DAYS * 86_400_000;
  } catch {
    return false;
  }
}

/**
 * Invites the user to install the app.
 * - Chrome / Edge / Android: one-tap install button.
 * - iOS Safari: short "Share → Add to Home Screen" instructions.
 * Hidden when already installed, or for 30 days after the user closes it.
 */
export function InstallBanner() {
  const [canInstall, setCanInstall] = useState(canPromptInstall);
  const [dismissed, setDismissed] = useState(recentlyDismissed);

  useEffect(() => onInstallAvailabilityChange(() => setCanInstall(canPromptInstall())), []);

  if (dismissed || isStandalone()) return null;
  const ios = isIosSafari();
  if (!canInstall && !ios) return null;

  const dismiss = () => {
    setDismissed(true);
    try { localStorage.setItem(DISMISS_KEY, String(Date.now())); } catch { /* storage blocked */ }
  };

  return (
    <div className="card mb-4 flex items-center gap-3 p-3 pl-3.5">
      <img src="/pwa-64x64.png" alt="" className="w-10 h-10 rounded-xl shrink-0" />
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
