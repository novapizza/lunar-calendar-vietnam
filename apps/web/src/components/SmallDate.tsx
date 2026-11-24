/**
 * A month grid's small date (shared/design/month-grid.md): the day, with its month on the first of
 * a month and on the selected day ("16/10"); after a leap lunar month a small raised "n" (user,
 * 2026-11-24: "n nhỏ ở góc trên bên phải"). Same rule as Android's core SmallDate.
 */
export function SmallDate({ day, month, leap = false, withMonth }: {
  day: number;
  /** The month of `day` (1-12, never negative). */
  month: number;
  leap?: boolean;
  withMonth: boolean;
}) {
  if (!withMonth) return <>{day}</>;
  return (
    <>
      {day}/{month}
      {leap && <span className="relative -top-[0.5em] ml-px text-[0.7em] leading-none">n</span>}
    </>
  );
}
