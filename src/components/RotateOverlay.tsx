import { Smartphone } from 'lucide-react';

/**
 * Shown only when a phone is held sideways (`phone-landscape` variant in index.css):
 * the app is portrait-only on phones, and the orientation lock in lib/orientation.ts
 * does not work in an ordinary browser tab.
 */
export function RotateOverlay() {
  return (
    <div
      role="alert"
      className="hidden phone-landscape:flex fixed inset-0 z-[100] flex-col items-center justify-center gap-3 bg-bg px-6 text-center"
    >
      <Smartphone size={40} className="text-brand" aria-hidden="true" />
      <p className="text-lg font-bold text-fg">Xoay dọc điện thoại</p>
      <p className="text-sm text-fg-2">Lịch Âm dùng ở chế độ màn hình dọc trên điện thoại.</p>
    </div>
  );
}
