import { MenuItem, Order } from '../data/types.js';

export interface TopItemEntry {
  dishKey: string;
  name: string;
  category: string;
  portions: number;
  revenue: number;
  sharePct: number;
}

export interface UnsoldItemEntry {
  dishKey: string;
  name: string;
  avgSellThrough: number;
  daysBelowThreshold: number;
  soldOutDays: number;
  days: number;
  status: 'ế' | 'bán hết sớm' | 'ổn';
}

/**
 * 6.2 get_top_items
 */
export function computeTopItems(
  completedOrders: Order[],
  menuItems: MenuItem[],
  order: 'best' | 'worst' = 'best',
  limit: number = 5
): TopItemEntry[] {
  const itemMap = new Map<
    string,
    { dishKey: string; name: string; category: string; portions: number; revenue: number }
  >();

  let totalPortions = 0;

  for (const o of completedOrders) {
    if (o.status !== 'COMPLETED') continue;
    for (const item of o.items) {
      const key = item.dishKey || item.name;
      const entry = itemMap.get(key) || {
        dishKey: key,
        name: item.name,
        category: item.category || 'Không rõ',
        portions: 0,
        revenue: 0,
      };
      entry.portions += item.quantity;
      entry.revenue += item.price * item.quantity;
      totalPortions += item.quantity;
      itemMap.set(key, entry);
    }
  }

  // Nếu là 'worst', bao gồm cả các món trong menu mà không có đơn nào
  if (order === 'worst') {
    for (const m of menuItems) {
      const key = m.dishKey || m.name;
      if (!itemMap.has(key)) {
        itemMap.set(key, {
          dishKey: key,
          name: m.name,
          category: m.category || 'Không rõ',
          portions: 0,
          revenue: 0,
        });
      }
    }
  }

  const list = Array.from(itemMap.values()).map((entry) => ({
    dishKey: entry.dishKey,
    name: entry.name,
    category: entry.category,
    portions: entry.portions,
    revenue: entry.revenue,
    sharePct: totalPortions > 0 ? Math.round((entry.portions / totalPortions) * 1000) / 10 : 0,
  }));

  if (order === 'best') {
    list.sort((a, b) => b.portions - a.portions || b.revenue - a.revenue);
  } else {
    list.sort((a, b) => a.portions - b.portions || a.revenue - b.revenue);
  }

  const safeLimit = Math.max(1, Math.min(limit, 20));
  return list.slice(0, safeLimit);
}

/**
 * 6.6 get_unsold_items
 */
export function computeUnsoldItems(
  menuItems: MenuItem[],
  threshold: number = 0.5
): UnsoldItemEntry[] {
  // Chỉ xét món có dailyQuota > 0
  const quotaItems = menuItems.filter((m) => (m.dailyQuota || 0) > 0);

  // Gom theo dishKey
  const dishMap = new Map<
    string,
    {
      dishKey: string;
      name: string;
      dailyStats: Array<{ soldRatio: number; isSoldOut: boolean; isBelowThreshold: boolean }>;
    }
  >();

  for (const m of quotaItems) {
    const key = m.dishKey || m.name;
    const quota = m.dailyQuota!;
    const sold = m.soldQty || 0;
    const soldRatio = sold / quota;
    const isSoldOut = sold >= quota;
    const isBelowThreshold = soldRatio < threshold;

    const entry = dishMap.get(key) || {
      dishKey: key,
      name: m.name,
      dailyStats: [],
    };
    entry.dailyStats.push({ soldRatio, isSoldOut, isBelowThreshold });
    dishMap.set(key, entry);
  }

  const results: UnsoldItemEntry[] = [];

  for (const entry of dishMap.values()) {
    const days = entry.dailyStats.length;
    if (days === 0) continue;

    const sumRatio = entry.dailyStats.reduce((acc, cur) => acc + cur.soldRatio, 0);
    const avgSellThrough = Math.round((sumRatio / days) * 1000) / 1000;
    const daysBelowThreshold = entry.dailyStats.filter((s) => s.isBelowThreshold).length;
    const soldOutDays = entry.dailyStats.filter((s) => s.isSoldOut).length;

    let status: 'ế' | 'bán hết sớm' | 'ổn';
    if (avgSellThrough < threshold) {
      status = 'ế';
    } else if (soldOutDays / days >= 0.3) {
      status = 'bán hết sớm';
    } else {
      status = 'ổn';
    }

    results.push({
      dishKey: entry.dishKey,
      name: entry.name,
      avgSellThrough,
      daysBelowThreshold,
      soldOutDays,
      days,
      status,
    });
  }

  // Sắp xếp theo avgSellThrough tăng dần
  results.sort((a, b) => a.avgSellThrough - b.avgSellThrough);

  return results;
}

