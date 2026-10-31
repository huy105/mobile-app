import { Order } from '../data/types.js';
import { getIsoWeekday } from '../util/dates.js';

export interface SlowDayResultItem {
  date: string;
  weekday: number;
  avgPrep: number;
  orders: number;
}

export interface FindSlowDaysResult {
  range: { from: string; to: string };
  avgPrepOverall: number;
  stdDev: number;
  slowDays: SlowDayResultItem[];
  daysWithData: number;
  skipped: number;
  note?: string;
}

export function computeSlowDays(
  orders: Order[],
  range: { from: string; to: string }
): FindSlowDaysResult {
  const dayMap = new Map<string, number[]>();
  let skipped = 0;

  for (const o of orders) {
    if (o.status !== 'COMPLETED') continue;
    if (o.readyAt && o.preparingAt) {
      const diff = (o.readyAt.getTime() - o.preparingAt.getTime()) / 60000;
      if (diff >= 0) {
        const list = dayMap.get(o.date) || [];
        list.push(diff);
        dayMap.set(o.date, list);
      } else {
        skipped++;
      }
    } else {
      skipped++;
    }
  }

  const daysWithData = dayMap.size;
  const dailyStats: Array<{ date: string; avg: number; count: number }> = [];

  for (const [date, values] of dayMap.entries()) {
    const avg = values.reduce((a, b) => a + b, 0) / values.length;
    dailyStats.push({ date, avg, count: values.length });
  }

  if (daysWithData < 5) {
    const overallAvg =
      dailyStats.length > 0
        ? Math.round(
            (dailyStats.reduce((a, b) => a + b.avg, 0) / dailyStats.length) * 10
          ) / 10
        : 0;

    return {
      range,
      avgPrepOverall: overallAvg,
      stdDev: 0,
      slowDays: [],
      daysWithData,
      skipped,
      note: 'Cần ít nhất 5 ngày có dữ liệu để phát hiện ngày chậm bất thường một cách tin cậy.',
    };
  }

  // Tính trung bình của các trung bình ngày và độ lệch chuẩn
  const mean = dailyStats.reduce((a, b) => a + b.avg, 0) / dailyStats.length;
  const variance =
    dailyStats.reduce((a, b) => a + Math.pow(b.avg - mean, 2), 0) / dailyStats.length;
  const stdDev = Math.sqrt(variance);
  const threshold = mean + 1.5 * stdDev;

  const slowDays: SlowDayResultItem[] = [];
  for (const d of dailyStats) {
    if (d.avg > threshold) {
      slowDays.push({
        date: d.date,
        weekday: getIsoWeekday(d.date),
        avgPrep: Math.round(d.avg * 10) / 10,
        orders: d.count,
      });
    }
  }

  slowDays.sort((a, b) => b.avgPrep - a.avgPrep);

  return {
    range,
    avgPrepOverall: Math.round(mean * 10) / 10,
    stdDev: Math.round(stdDev * 10) / 10,
    slowDays,
    daysWithData,
    skipped,
  };
}
