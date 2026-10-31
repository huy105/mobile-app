import { MenuItem } from '../data/types.js';
import { getWeekday } from '../util/dates.js';

export interface ForecastItemResult {
  dishKey: string;
  name: string;
  forecast: number | null;
  weeksOfData: number;
  currentQuota: number;
  warning?: string;
  note?: string;
}

export interface ForecastResult {
  forDate: string;
  weekday: number;
  buffer_pct: number;
  items: ForecastItemResult[];
}

export function computeForecast(
  menuItems: MenuItem[],
  forDate: string,
  bufferPct: number = 10
): ForecastResult {
  const targetWeekday = getWeekday(forDate);

  // Lọc các bản ghi trước ngày dự báo và có cùng thứ (weekday)
  const historicalItems = menuItems.filter(
    (m) => m.date < forDate && getWeekday(m.date) === targetWeekday
  );

  // Gom theo dishKey
  const dishMap = new Map<
    string,
    {
      dishKey: string;
      name: string;
      historyByDate: Map<string, number>;
      latestQuota: number;
    }
  >();

  // Sắp xếp theo ngày tăng dần để lấy latestQuota chuẩn xác
  const sorted = [...menuItems].sort((a, b) => a.date.localeCompare(b.date));
  for (const m of sorted) {
    const key = m.dishKey || m.name;
    const entry = dishMap.get(key) || {
      dishKey: key,
      name: m.name,
      historyByDate: new Map<string, number>(),
      latestQuota: m.dailyQuota || 0,
    };
    entry.name = m.name;
    if (m.dailyQuota !== undefined) {
      entry.latestQuota = m.dailyQuota;
    }
    dishMap.set(key, entry);
  }

  for (const m of historicalItems) {
    const key = m.dishKey || m.name;
    const entry = dishMap.get(key);
    if (entry) {
      // Lưu soldQty cho ngày m.date (lấy bản ghi sau nếu có)
      entry.historyByDate.set(m.date, m.soldQty || 0);
    }
  }

  const items: ForecastItemResult[] = [];

  for (const entry of dishMap.values()) {
    // Lấy tối đa 4 ngày gần nhất cùng thứ
    const dates = Array.from(entry.historyByDate.keys()).sort();
    const recentDates = dates.slice(-4);
    const weeksOfData = recentDates.length;

    if (weeksOfData < 2) {
      items.push({
        dishKey: entry.dishKey,
        name: entry.name,
        forecast: null,
        weeksOfData,
        currentQuota: entry.latestQuota,
        note: 'chưa đủ dữ liệu',
      });
      continue;
    }

    const soldValues = recentDates.map((d) => entry.historyByDate.get(d)!);
    const avgSold = soldValues.reduce((a, b) => a + b, 0) / soldValues.length;
    const forecast = Math.ceil(avgSold * (1 + bufferPct / 100));

    let warning: string | undefined;
    if (entry.latestQuota > 0 && forecast > entry.latestQuota) {
      warning = 'vượt hạn mức hiện tại';
    }

    items.push({
      dishKey: entry.dishKey,
      name: entry.name,
      forecast,
      weeksOfData,
      currentQuota: entry.latestQuota,
      warning,
    });
  }

  items.sort((a, b) => (b.forecast ?? -1) - (a.forecast ?? -1));

  return {
    forDate,
    weekday: targetWeekday,
    buffer_pct: bufferPct,
    items,
  };
}
