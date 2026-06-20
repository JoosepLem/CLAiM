// Shared formatting helpers.

/** Format a number as euros with thin-space thousands separators, e.g. "€ 1 204.50". */
export const money = (n: number): string =>
  '€ ' + n.toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');

/** Format an integer with thin-space thousands separators, e.g. "1 204". */
export const num = (n: number): string =>
  String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ' ');

export const MONTHS = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
] as const;

const pad = (n: number) => String(n).padStart(2, '0');
const lastDay = (y: number, m: number) => new Date(y, m, 0).getDate();
const parseISO = (s: string) => {
  const [y, m, d] = s.split('-').map(Number);
  return { y, m, d };
};

/**
 * Human label for a date range. Collapses full calendar months/years to
 * "May 2026" / "Apr – Jun 2026", and shows the sentinel range as "All time".
 */
export function rangeLabel(from: string, to: string): string {
  if (from <= '1900-01-01' && to >= '2999-12-31') return 'All time';
  const f = parseISO(from);
  const t = parseISO(to);
  const fullStart = f.d === 1;
  const fullEnd = t.d === lastDay(t.y, t.m);
  if (fullStart && fullEnd) {
    if (f.y === t.y && f.m === t.m) return `${MONTHS[f.m - 1]} ${f.y}`;
    if (f.y === t.y) return `${MONTHS[f.m - 1]} – ${MONTHS[t.m - 1]} ${t.y}`;
    return `${MONTHS[f.m - 1]} ${f.y} – ${MONTHS[t.m - 1]} ${t.y}`;
  }
  const fy = f.y === t.y ? '' : ` ${f.y}`;
  return `${pad(f.d)} ${MONTHS[f.m - 1]}${fy} – ${pad(t.d)} ${MONTHS[t.m - 1]} ${t.y}`;
}

/** True if an invoice's period month overlaps the [from, to] range. */
export function periodInRange(period: string, from: string, to: string): boolean {
  const [y, m] = period.split('-').map(Number);
  const start = `${y}-${pad(m)}-01`;
  const end = `${y}-${pad(m)}-${pad(lastDay(y, m))}`;
  return start <= to && end >= from;
}
