import { Order } from '../data/types.js';

export interface TopCancelledDish {
  dishKey: string;
  name: string;
  quantity: number;
}

export interface CancellationsResult {
  total: number;
  ratePct: number;
  byReason: Record<string, number>;
  byCancelledBy: Record<string, number>;
  topDishesInCancelled: TopCancelledDish[];
  noShow: number;
}

export function computeCancellations(orders: Order[]): CancellationsResult {
  const cancelledOrders = orders.filter((o) => o.status === 'CANCELLED');
  const completedOrders = orders.filter((o) => o.status === 'COMPLETED');

  const totalCancelled = cancelledOrders.length;
  const totalCompleted = completedOrders.length;
  const totalRelevant = totalCancelled + totalCompleted;

  const ratePct = totalRelevant > 0 ? Math.round((totalCancelled / totalRelevant) * 1000) / 10 : 0;

  const byReason: Record<string, number> = {};
  const byCancelledBy: Record<string, number> = {};
  const dishMap = new Map<string, { dishKey: string; name: string; quantity: number }>();
  let noShow = 0;

  for (const o of cancelledOrders) {
    const reason = o.cancelReason || 'UNKNOWN';
    byReason[reason] = (byReason[reason] || 0) + 1;
    if (reason === 'NO_SHOW') {
      noShow++;
    }

    const by = o.cancelledBy || 'UNKNOWN';
    byCancelledBy[by] = (byCancelledBy[by] || 0) + 1;

    for (const item of o.items) {
      const key = item.dishKey || item.name;
      const entry = dishMap.get(key) || {
        dishKey: key,
        name: item.name,
        quantity: 0,
      };
      entry.quantity += item.quantity;
      dishMap.set(key, entry);
    }
  }

  const topDishesInCancelled = Array.from(dishMap.values())
    .sort((a, b) => b.quantity - a.quantity)
    .slice(0, 5);

  return {
    total: totalCancelled,
    ratePct,
    byReason,
    byCancelledBy,
    topDishesInCancelled,
    noShow,
  };
}

