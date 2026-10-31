import { Order } from '../data/types.js';
import { getMondayOfWeek } from '../util/dates.js';

export interface SalesGroup {
  key: string;
  revenue: number;
  orders: number;
  portions: number;
}

export interface SalesSummaryResult {
  range: { from: string; to: string };
  revenue: number;
  orders: number;
  portions: number;
  avgOrderValue: number;
  changePctVsPrevious: number | null;
  inProgressOrders: number;
  cancelledOrders: number;
  groups?: SalesGroup[];
}

export function computeSalesSummary(
  orders: Order[],
  range: { from: string; to: string },
  previousOrders: Order[] = [],
  groupBy: 'none' | 'day' | 'week' | 'category' = 'none'
): SalesSummaryResult {
  let revenue = 0;
  let completedOrdersCount = 0;
  let portions = 0;
  let inProgressOrders = 0;
  let cancelledOrders = 0;

  for (const o of orders) {
    if (o.status === 'COMPLETED') {
      completedOrdersCount++;
      revenue += o.total;
      for (const item of o.items) {
        portions += item.quantity;
      }
    } else if (o.status === 'CANCELLED') {
      cancelledOrders++;
    } else {
      // RECEIVED, PREPARING, READY
      inProgressOrders++;
    }
  }

  const avgOrderValue = completedOrdersCount > 0 ? Math.round(revenue / completedOrdersCount) : 0;

  let changePctVsPrevious: number | null = null;
  if (previousOrders.length > 0) {
    let prevRevenue = 0;
    for (const po of previousOrders) {
      if (po.status === 'COMPLETED') {
        prevRevenue += po.total;
      }
    }
    if (prevRevenue > 0) {
      changePctVsPrevious = Math.round(((revenue - prevRevenue) / prevRevenue) * 1000) / 10;
    }
  }

  let groups: SalesGroup[] | undefined;

  if (groupBy !== 'none') {
    const groupMap = new Map<string, { revenue: number; orderIds: Set<string>; portions: number }>();

    for (const o of orders) {
      if (o.status !== 'COMPLETED') continue;

      if (groupBy === 'day') {
        const key = o.date;
        const entry = groupMap.get(key) || { revenue: 0, orderIds: new Set<string>(), portions: 0 };
        entry.revenue += o.total;
        entry.orderIds.add(o.id);
        for (const item of o.items) {
          entry.portions += item.quantity;
        }
        groupMap.set(key, entry);
      } else if (groupBy === 'week') {
        const key = getMondayOfWeek(o.date);
        const entry = groupMap.get(key) || { revenue: 0, orderIds: new Set<string>(), portions: 0 };
        entry.revenue += o.total;
        entry.orderIds.add(o.id);
        for (const item of o.items) {
          entry.portions += item.quantity;
        }
        groupMap.set(key, entry);
      } else if (groupBy === 'category') {
        for (const item of o.items) {
          const key = item.category || 'Không rõ';
          const entry = groupMap.get(key) || { revenue: 0, orderIds: new Set<string>(), portions: 0 };
          entry.revenue += item.price * item.quantity;
          entry.orderIds.add(o.id);
          entry.portions += item.quantity;
          groupMap.set(key, entry);
        }
      }
    }

    groups = Array.from(groupMap.entries()).map(([key, data]) => ({
      key,
      revenue: data.revenue,
      orders: data.orderIds.size,
      portions: data.portions,
    }));

    if (groupBy === 'category') {
      groups.sort((a, b) => b.revenue - a.revenue);
    } else {
      groups.sort((a, b) => a.key.localeCompare(b.key));
    }
  }

  return {
    range,
    revenue,
    orders: completedOrdersCount,
    portions,
    avgOrderValue,
    changePctVsPrevious,
    inProgressOrders,
    cancelledOrders,
    groups,
  };
}
