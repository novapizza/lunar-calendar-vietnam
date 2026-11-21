import { CalendarDays, LayoutGrid, Sparkles } from 'lucide-react';
import { cn } from '../lib/utils';
import { AppLogo } from './AppLogo';

export type NavTab = 'today' | 'calendar' | 'events' | 'tools';

interface Props {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
}

const NAV_ITEMS = [
  { id: 'today' as NavTab, label: 'Hôm nay', Icon: CalendarDays },
  { id: 'calendar' as NavTab, label: 'Lịch', Icon: LayoutGrid },
  { id: 'events' as NavTab, label: 'Sự kiện', Icon: Sparkles },
];

const TOOLS_LABEL = 'Tiện ích và cài đặt';

/** The app logo doubles as the entry to the tools & settings view. */
function Logo({ active, className }: { active: boolean; className?: string }) {
  return (
    <AppLogo
      className={cn(
        'rounded-xl transition-shadow',
        active && 'ring-2 ring-brand ring-offset-2 ring-offset-surface',
        className,
      )}
    />
  );
}

/**
 * Bottom tab bar on phones and unfolded foldables (book), slim left rail elsewhere.
 * Three labelled tabs plus the logo, which opens "Tiện ích" (tools & settings):
 * last in the tab bar, on top of the rail. Four slots, so in book mode two sit on
 * each side of the fold.
 */
export function NavSidebar({ activeTab, onTabChange }: Props) {
  const toolsActive = activeTab === 'tools';
  return (
    <aside
      className="
        order-last shrink-0 z-30 bg-surface border-t border-line
        pb-[env(safe-area-inset-bottom)] pl-[env(safe-area-inset-left)] pr-[env(safe-area-inset-right)]
        rail:order-first rail:w-[88px] rail:border-t-0 rail:border-r rail:pb-0 rail:pr-0 rail:pt-5 rail:flex rail:flex-col rail:items-center
      "
    >
      {/* Rail: logo on top. The wrapper carries the visibility, the button its own display. */}
      <div className="hidden rail:block mb-6">
        <button
          onClick={() => onTabChange('tools')}
          aria-label={TOOLS_LABEL}
          title={TOOLS_LABEL}
          aria-current={toolsActive ? 'page' : undefined}
          className="block rounded-xl transition-transform hover:scale-105 active:scale-95"
        >
          <Logo active={toolsActive} className="w-10 h-10" />
        </button>
      </div>

      <nav className="flex rail:flex-col w-full rail:gap-2 rail:px-2" aria-label="Điều hướng chính">
        {NAV_ITEMS.map(({ id, label, Icon }) => {
          const active = activeTab === id;
          return (
            <button
              key={id}
              onClick={() => onTabChange(id)}
              aria-current={active ? 'page' : undefined}
              className="group flex-1 flex flex-col items-center gap-1 pt-2 pb-1.5 rail:py-2"
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

        {/* Tab bar: the logo is the fourth slot (no text label). */}
        <div className="flex-1 flex rail:hidden">
          <button
            onClick={() => onTabChange('tools')}
            aria-label={TOOLS_LABEL}
            aria-current={toolsActive ? 'page' : undefined}
            className="flex-1 flex items-center justify-center active:scale-95 transition-transform"
          >
            <Logo active={toolsActive} className="w-9 h-9" />
          </button>
        </div>
      </nav>
    </aside>
  );
}
