import type { ReactNode } from 'react';
import { X } from 'lucide-react';
import { cn } from '../lib/utils';

interface Props {
  title: string;
  subtitle?: string;
  icon?: ReactNode;
  onClose: () => void;
  children: ReactNode;
  footer?: ReactNode;
  /** Desktop width of the dialog. */
  size?: 'md' | 'lg';
  /** Phones: take the full height (long forms / lists) instead of fitting content. */
  tall?: boolean;
}

/**
 * Modal shell used by every popup.
 * Phones: bottom sheet that slides up, with a grab handle and safe-area padding.
 * Desktop: centred dialog. Backdrop tap closes it; Escape is handled in App.
 */
export function Sheet({ title, subtitle, icon, onClose, children, footer, size = 'md', tall }: Props) {
  return (
    <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center sm:p-6">
      <div
        className="absolute inset-0 bg-black/40 backdrop-blur-[2px] animate-fade-in"
        onClick={onClose}
        aria-hidden="true"
      />
      <div
        role="dialog"
        aria-modal="true"
        aria-label={title}
        className={cn(
          'relative w-full flex flex-col bg-surface shadow-[var(--shadow-float)] animate-sheet-in',
          'rounded-t-[1.75rem] sm:rounded-[1.75rem] overflow-hidden',
          'max-h-[calc(100dvh-env(safe-area-inset-top)-0.75rem)] sm:max-h-[min(88dvh,820px)]',
          tall && 'h-[calc(100dvh-env(safe-area-inset-top)-0.75rem)] sm:h-auto',
          size === 'md' ? 'sm:max-w-md' : 'sm:max-w-xl',
        )}
      >
        {/* Grab handle (phones) */}
        <div className="sm:hidden pt-2.5 pb-1 flex justify-center" aria-hidden="true">
          <div className="h-1.5 w-10 rounded-full bg-surface-3" />
        </div>

        <header className="flex items-center gap-3 px-5 pt-2 pb-3 sm:pt-5">
          {icon && (
            <div className="w-10 h-10 shrink-0 rounded-2xl bg-brand-soft text-brand flex items-center justify-center">
              {icon}
            </div>
          )}
          <div className="flex-1 min-w-0">
            <h2 className="text-lg font-bold text-fg leading-tight">{title}</h2>
            {subtitle && <p className="text-xs text-fg-2 mt-0.5">{subtitle}</p>}
          </div>
          <button onClick={onClose} aria-label="Đóng" className="icon-btn -mr-2 bg-surface-2">
            <X size={18} />
          </button>
        </header>

        <div className="flex-1 min-h-0 overflow-y-auto overscroll-contain px-5 pb-5">
          {children}
        </div>

        {footer && (
          <footer className="px-5 pt-3 pb-[max(1rem,env(safe-area-inset-bottom))] sm:pb-5 border-t border-line bg-surface">
            {footer}
          </footer>
        )}
        {!footer && <div className="pb-[env(safe-area-inset-bottom)]" />}
      </div>
    </div>
  );
}
