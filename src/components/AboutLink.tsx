interface Props {
  onClick: () => void;
}

/** Inline "app info" link placed at the end of a view's content. */
export function AboutLink({ onClick }: Props) {
  return (
    <div className="mt-8 text-center">
      <button
        onClick={onClick}
        className="px-3 py-2 text-xs font-medium text-fg-3 hover:text-fg-2 transition-colors"
      >
        Thông tin ứng dụng
      </button>
    </div>
  );
}
