import { useId } from 'react';

/**
 * The app logo (same drawing as shared/design/icon.svg), drawn inline so its
 * background follows the mệnh palette through --logo. The installed app icon
 * (PNG files in public/) stays red: the system fixes it at install time.
 */
export function AppLogo({ className }: { className?: string }) {
  const id = useId();
  return (
    <svg viewBox="0 0 512 512" className={className} aria-hidden="true">
      <defs>
        <linearGradient id={`${id}moon`} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#FDE68A" />
          <stop offset="1" stopColor="#F59E0B" />
        </linearGradient>
        <mask id={`${id}crescent`}>
          <rect width="512" height="512" fill="#fff" />
          <circle cx="306" cy="214" r="128" fill="#000" />
        </mask>
      </defs>
      <rect width="512" height="512" style={{ fill: 'var(--logo)' }} />
      <circle cx="246" cy="262" r="140" fill={`url(#${id}moon)`} mask={`url(#${id}crescent)`} />
      <path d="M370 128 l12 27 27 12 -27 12 -12 27 -12 -27 -27 -12 27 -12z" fill="#FDE68A" />
      <circle cx="404" cy="236" r="9" fill="#FDE68A" opacity=".8" />
    </svg>
  );
}
