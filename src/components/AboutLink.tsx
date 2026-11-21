interface Props {
  onClick: () => void;
}

/** Inline "app info" link placed at the end of a view's content (right pane in book mode). */
export function AboutLink({ onClick }: Props) {
  return (
    <div className="mt-8 text-center book:w-1/2 book:ml-auto">
      <button
        onClick={onClick}
        className="px-3 py-2 text-xs font-medium text-fg-3 hover:text-fg-2 transition-colors"
      >
        Thông tin ứng dụng
      </button>
    </div>
  );
}
