import { Order } from '../data/types.js';

export interface MetricSummary {
  avg: number;
  p90: number;
}

export interface SlotPrepStats {
  slotLabel: string;
  avgPrep: number;
  p90Prep: number;
  count: number;
}

export interface SlowDayEntry {
  date: string;
  avgPrep: number;
}

export interface PrepTimeStatsResult {
  prep: MetricSummary;
  waitToStart: MetricSummary;
  pickupDelay: MetricSummary;
  bySlot: SlotPrepStats[];
  slowDays: SlowDayEntry[];
  skipped: number;
}

function calculateP90(sortedValues: number[]): number {
  if (sortedValues.length === 0) return 0;
  const index = Math.ceil(0.9 * sortedValues.length) - 1;
  const val = sortedValues[Math.max(0, Math.min(index, sortedValues.length - 1))];
  return Math.round(val * 10) / 10;
}

function calculateAvg(values: number[]): number {
  if (values.length === 0) return 0;
  const sum = values.reduce((acc, v) => acc + v, 0);
  return Math.round((sum / values.length) * 10) / 10;
}

export function computePrepTimeStats(orders: Order[]): PrepTimeStatsResult {
  const prepDurations: number[] = [];
  const waitDurations: number[] = [];
  const pickupDurations: number[] = [];

  const slotMap = new Map<string, number[]>();
  const dayMap = new Map<string, number[]>();

  let skipped = 0;

  for (const o of orders) {
    if (o.status !== 'COMPLETED') continue;

    const hasAllTimestamps = o.createdAt && o.preparingAt && o.readyAt && o.completedAt;

    if (!hasAllTimestamps) {
      skipped++;
      continue;
    }

    const prepMin = (o.readyAt!.getTime() - o.preparingAt!.getTime()) / 60000;
    const waitMin = (o.preparingAt!.getTime() - o.createdAt.getTime()) / 60000;
    const pickupMin = (o.completedAt!.getTime() - o.readyAt!.getTime()) / 60000;

    // Chỉ nhận thời lượng hợp lệ >= 0
    if (prepMin < 0 || waitMin < 0 || pickupMin < 0) {
      skipped++;
      continue;
    }

    prepDurations.push(prepMin);
    waitDurations.push(waitMin);
    pickupDurations.push(pickupMin);

    // Tách theo khung giờ
    const slotKey = o.slotLabel || o.slotId || 'Không rõ';
    const slotList = slotMap.get(slotKey) || [];
    slotList.push(prepMin);
    slotMap.set(slotKey, slotList);

    // Tách theo ngày
    const dayList = dayMap.get(o.date) || [];
    dayList.push(prepMin);
    dayMap.set(o.date, dayList);
  }

  prepDurations.sort((a, b) => a - b);
  waitDurations.sort((a, b) => a - b);
  pickupDurations.sort((a, b) => a - b);

  const prepSummary: MetricSummary = {
    avg: calculateAvg(prepDurations),
    p90: calculateP90(prepDurations),
  };

  const waitSummary: MetricSummary = {
    avg: calculateAvg(waitDurations),
    p90: calculateP90(waitDurations),
  };

  const pickupSummary: MetricSummary = {
    avg: calculateAvg(pickupDurations),
    p90: calculateP90(pickupDurations),
  };

  const bySlot: SlotPrepStats[] = [];
  for (const [slotLabel, values] of slotMap.entries()) {
    values.sort((a, b) => a - b);
    bySlot.push({
      slotLabel,
      avgPrep: calculateAvg(values),
      p90Prep: calculateP90(values),
      count: values.length,
    });
  }

  // Tính ngày chậm bất thường
  // trung bình prep của ngày > (trung bình toàn kỳ + 1.5 * độ lệch chuẩn của các trung bình theo ngày)
  const dailyAvgs: Array<{ date: string; avg: number }> = [];
  for (const [date, values] of dayMap.entries()) {
    dailyAvgs.push({
      date,
      avg: values.reduce((a, b) => a + b, 0) / values.length,
    });
  }

  const slowDays: SlowDayEntry[] = [];
  if (dailyAvgs.length > 1) {
    const totalMean = dailyAvgs.reduce((a, b) => a + b.avg, 0) / dailyAvgs.length;
    const variance =
      dailyAvgs.reduce((a, b) => a + Math.pow(b.avg - totalMean, 2), 0) / dailyAvgs.length;
    const stdDev = Math.sqrt(variance);

    const threshold = totalMean + 1.5 * stdDev;

    for (const d of dailyAvgs) {
      if (d.avg > threshold) {
        slowDays.push({
          date: d.date,
          avgPrep: Math.round(d.avg * 10) / 10,
        });
      }
    }
  }

  slowDays.sort((a, b) => b.avgPrep - a.avgPrep);

  return {
    prep: prepSummary,
    waitToStart: waitSummary,
    pickupDelay: pickupSummary,
    bySlot,
    slowDays,
    skipped,
  };
}
