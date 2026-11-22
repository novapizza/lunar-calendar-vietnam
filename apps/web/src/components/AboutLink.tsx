import { cn } from '../lib/utils';

interface Props {
  onClick: () => void;
  /** Already inside the right pane (Today's cards column): no need to move to the right half in book. */
  inPane?: boolean;
}

/** Inline "app info" link placed at the end of a view's content (right pane in book mode). */
export function AboutLink({ onClick, inPane }: Props) {
  return (
    <div className={cn('mt-8 text-center', !inPane && 'book:w-1/2 book:ml-auto')}>
      <button
        onClick={onClick}
        className="px-3 py-2 text-xs font-medium text-fg-3 hover:text-fg-2 transition-colors"
      >
        Thông tin ứng dụng
      </button>
    </div>
  );
}
