import { DateRange } from '../data/types.js';

export const DEFAULT_TIMEZONE = 'Asia/Ho_Chi_Minh';
export const MAX_DAYS_RANGE = 92;

/**
 * Format a Date object to yyyy-MM-dd in the given timezone.
 */
export function formatDateInTz(date: Date, tz: string = DEFAULT_TIMEZONE): string {
  const formatter = new Intl.DateTimeFormat('en-CA', {
    timeZone: tz,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  });
  return formatter.format(date);
}

/**
 * Get today's date string (yyyy-MM-dd) in the given timezone.
 */
export function getToday(tz: string = DEFAULT_TIMEZONE): string {
  return formatDateInTz(new Date(), tz);
}

/**
 * Add (or subtract) days to a yyyy-MM-dd date string.
 */
export function addDays(dateStr: string, days: number): string {
  const [y, m, d] = dateStr.split('-').map(Number);
  const utc = new Date(Date.UTC(y, m - 1, d + days));
  return utc.toISOString().slice(0, 10);
}

/**
 * Calculate the number of calendar days between two yyyy-MM-dd dates (to - from).
 */
export function diffInDays(from: string, to: string): number {
  const [y1, m1, d1] = from.split('-').map(Number);
  const [y2, m2, d2] = to.split('-').map(Number);
  const utc1 = Date.UTC(y1, m1 - 1, d1);
  const utc2 = Date.UTC(y2, m2 - 1, d2);
  return Math.round((utc2 - utc1) / (1000 * 60 * 60 * 24));
}

/**
 * Validate and resolve optional date_from and date_to into a concrete DateRange.
 * - Missing both: defaults to today.
 * - Missing one: takes the other.
 * - Checks from <= to.
 * - Checks range <= 92 days.
 */
export function resolveDateRange(
  date_from?: string,
  date_to?: string,
  tz: string = DEFAULT_TIMEZONE
): DateRange {
  let from: string;
  let to: string;

  if (!date_from && !date_to) {
    const today = getToday(tz);
    from = today;
    to = today;
  } else if (!date_from) {
    from = date_to!;
    to = date_to!;
  } else if (!date_to) {
    from = date_from;
    to = date_from;
  } else {
    from = date_from;
    to = date_to;
  }

  if (from > to) {
    throw new Error(`Khoảng ngày không hợp lệ: date_from (${from}) phải nhỏ hơn hoặc bằng date_to (${to})`);
  }

  const daysInclusive = diffInDays(from, to) + 1;
  if (daysInclusive > MAX_DAYS_RANGE) {
    throw new Error(
      `Khoảng ngày (${daysInclusive} ngày) vượt quá giới hạn tối đa ${MAX_DAYS_RANGE} ngày. Vui lòng thu hẹp khoảng thời gian.`
    );
  }

  return { from, to };
}

/**
 * Get the immediately preceding period with the exact same number of days.
 */
export function getPreviousPeriod(range: DateRange): DateRange {
  const days = diffInDays(range.from, range.to) + 1;
  const prevTo = addDays(range.from, -1);
  const prevFrom = addDays(prevTo, -(days - 1));
  return { from: prevFrom, to: prevTo };
}

/**
 * Get day of week (0 = Sunday, 1 = Monday, ..., 6 = Saturday) for a yyyy-MM-dd date.
 */
export function getWeekday(dateStr: string): number {
  const [y, m, d] = dateStr.split('-').map(Number);
  const utc = new Date(Date.UTC(y, m - 1, d));
  return utc.getUTCDay();
}

/**
 * Get Monday (start of week) for a given yyyy-MM-dd date.
 * If Sunday (0), Monday is 6 days ago.
 * If Monday (1), Monday is 0 days ago.
 */
export function getMondayOfWeek(dateStr: string): string {
  const day = getWeekday(dateStr);
  const diffToMonday = day === 0 ? -6 : 1 - day;
  return addDays(dateStr, diffToMonday);
}
