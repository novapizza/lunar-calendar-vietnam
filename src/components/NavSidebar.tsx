import { CalendarDays, LayoutGrid, Sparkles } from 'lucide-react';
import { cn } from '../lib/utils';

export type NavTab = 'today' | 'calendar' | 'events';

interface Props {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
}

const NAV_ITEMS = [
  { id: 'today' as NavTab, label: 'Hôm nay', Icon: CalendarDays },
  { id: 'calendar' as NavTab, label: 'Lịch', Icon: LayoutGrid },
  { id: 'events' as NavTab, label: 'Sự kiện', Icon: Sparkles },
];

/** Bottom tab bar on compact windows (< 600px), slim left rail from md (600px). */
export function NavSidebar({ activeTab, onTabChange }: Props) {
  return (
    <aside
      className="
        order-last shrink-0 z-30 bg-surface border-t border-line
        pb-[env(safe-area-inset-bottom)] pl-[env(safe-area-inset-left)] pr-[env(safe-area-inset-right)]
        md:order-first md:w-[88px] md:border-t-0 md:border-r md:pb-0 md:pr-0 md:pt-5 md:flex md:flex-col md:items-center
      "
    >
      <img src="/pwa-64x64.png" alt="Lịch Âm" className="hidden md:block w-10 h-10 rounded-xl mb-6" />
      <nav className="flex md:flex-col w-full md:gap-2 md:px-2" aria-label="Điều hướng chính">
        {NAV_ITEMS.map(({ id, label, Icon }) => {
          const active = activeTab === id;
          return (
            <button
              key={id}
              onClick={() => onTabChange(id)}
              aria-current={active ? 'page' : undefined}
              className="group flex-1 flex flex-col items-center gap-1 pt-2 pb-1.5 md:py-2"
            >
              <span
                className={cn(
                  'flex items-center justify-center h-8 w-14 rounded-full transition-colors',
                  active ? 'bg-brand-soft text-brand' : 'text-fg-2 group-hover:bg-surface-2',
                )}
              >
                <Icon size={20} strokeWidth={active ? 2.4 : 2} />
              </span>
              <span className={cn('text-[11px] leading-none', active ? 'font-bold text-fg' : 'font-medium text-fg-2')}>
                {label}
              </span>
            </button>
          );
        })}
      </nav>
    </aside>
  );
}
