import { Order, TimeSlot } from '../data/types.js';
import { AppliedFilters, parseStartMinute } from '../util/filters.js';
import { getIsoWeekday, getMondayOfWeek } from '../util/dates.js';
import { calculateAvg, calculateP90 } from './stats.js';

export const VALID_DIMENSIONS = [
  'day',
  'week',
  'weekday',
  'daypart',
  'slot',
  'dish',
  'category',
  'cancel_reason',
  'status',
] as const;

export type Dimension = (typeof VALID_DIMENSIONS)[number];

export const VALID_METRICS = [
  'revenue',
  'orders',
  'portions',
  'avg_order_value',
  'cancelled_orders',
  'in_progress_orders',
  'cancel_rate',
  'avg_prep_minutes',
  'p90_prep_minutes',
  'avg_wait_minutes',
  'avg_pickup_delay_minutes',
] as const;

export type Metric = (typeof VALID_METRICS)[number];

export interface EngineQueryOptions {
  groupBy?: Dimension[];
  metrics?: Metric[];
  sortBy?: Metric;
  sortDir?: 'desc' | 'asc';
  limit?: number;
}

export interface EngineRow {
  key: Record<string, string | number>;
  [metric: string]: any;
}

export interface EngineResult {
  appliedFilters: AppliedFilters;
  groupBy: Dimension[];
  metrics: Metric[];
  rows: EngineRow[];
  totals: Record<string, number | null>;
  rowCount: number;
  truncated: boolean;
  skipped: {
    missingTimestamps: number;
  };
  note?: string;
}

interface BucketData {
  orderIds: Set<string>;
  completedOrderIds: Set<string>;
  cancelledOrderIds: Set<string>;
  inProgressOrderIds: Set<string>;
  revenue: number;
  portions: number;
  cancelledRevenue: number;
  prepMinutes: number[];
  waitMinutes: number[];
  pickupDelayMinutes: number[];
}

function createEmptyBucket(): BucketData {
  return {
    orderIds: new Set(),
    completedOrderIds: new Set(),
    cancelledOrderIds: new Set(),
    inProgressOrderIds: new Set(),
    revenue: 0,
    portions: 0,
    cancelledRevenue: 0,
    prepMinutes: [],
    waitMinutes: [],
    pickupDelayMinutes: [],
  };
}

export function computeOrderMetrics(
  orders: Order[],
  appliedFilters: AppliedFilters,
  hasLineItemFilter: boolean,
  timeSlots: TimeSlot[] = [],
  options: EngineQueryOptions = {}
): EngineResult {
  const groupBy = options.groupBy || [];
  if (groupBy.length > 2) {
    throw new Error(
      `group_by quá 2 phần tử (nhận được ${groupBy.length}). Chỉ hỗ trợ tối đa 2 chiều phân tích.`
    );
  }

  for (const dim of groupBy) {
    if (!VALID_DIMENSIONS.includes(dim)) {
      throw new Error(
        `Chiều group_by không hợp lệ: "${dim}". Các giá trị hợp lệ: ${VALID_DIMENSIONS.join(', ')}.`
      );
    }
  }

  const metrics: Metric[] =
    options.metrics && options.metrics.length > 0
      ? options.metrics
      : ['revenue', 'orders', 'portions'];

  for (const m of metrics) {
    if (!VALID_METRICS.includes(m)) {
      throw new Error(
        `Chỉ số metric không hợp lệ: "${m}". Các giá trị hợp lệ: ${VALID_METRICS.join(', ')}.`
      );
    }
  }

  const sortBy: Metric = options.sortBy || metrics[0] || 'revenue';
  const sortDir = options.sortDir || 'desc';
  const limit = Math.max(1, Math.min(options.limit ?? 20, 100));

  let missingTimestampsCount = 0;
  const isLineItemGrouping = groupBy.includes('dish') || groupBy.includes('category');

  // Map key string -> { keyObj, bucket }
  const groupsMap = new Map<string, { keyObj: Record<string, string | number>; bucket: BucketData }>();
  const totalsBucket = createEmptyBucket();

  // Helper hàm trích xuất giá trị chiều của một đơn/món
  function getDimValue(dim: Dimension, o: Order, item?: Order['items'][0]): string | number {
    switch (dim) {
      case 'day':
        return o.date;
      case 'week':
        return getMondayOfWeek(o.date);
      case 'weekday':
        return getIsoWeekday(o.date);
      case 'daypart': {
        const startMin = parseStartMinute(o.slotId, o.slotLabel, timeSlots);
        return startMin < 840 ? 'lunch' : 'evening';
      }
      case 'slot':
        return o.slotLabel || o.slotId;
      case 'dish':
        return item ? item.dishKey || item.name : 'Tất cả';
      case 'category':
        return item ? item.category || 'Không rõ' : 'Tất cả';
      case 'cancel_reason':
        return o.cancelReason || 'UNKNOWN';
      case 'status':
        return o.status;
      default:
        return '';
    }
  }

  function addTimeToBucket(bucket: BucketData, o: Order) {
    if (o.status !== 'COMPLETED') return;
    const hasTimestamps = o.createdAt && o.preparingAt && o.readyAt && o.completedAt;
    if (!hasTimestamps) return;

    const prep = (o.readyAt!.getTime() - o.preparingAt!.getTime()) / 60000;
    const wait = (o.preparingAt!.getTime() - o.createdAt.getTime()) / 60000;
    const pickup = (o.completedAt!.getTime() - o.readyAt!.getTime()) / 60000;

    if (prep >= 0) bucket.prepMinutes.push(prep);
    if (wait >= 0) bucket.waitMinutes.push(wait);
    if (pickup >= 0) bucket.pickupDelayMinutes.push(pickup);
  }

  // 1. Tính toán totals chung
  for (const o of orders) {
    totalsBucket.orderIds.add(o.id);

    if (o.status === 'COMPLETED') {
      totalsBucket.completedOrderIds.add(o.id);
      if (hasLineItemFilter) {
        let lineRev = 0;
        let linePortions = 0;
        for (const item of o.items) {
          lineRev += item.price * item.quantity;
          linePortions += item.quantity;
        }
        totalsBucket.revenue += lineRev;
        totalsBucket.portions += linePortions;
      } else {
        totalsBucket.revenue += o.total;
        for (const item of o.items) {
          totalsBucket.portions += item.quantity;
        }
      }

      const hasTimestamps = o.createdAt && o.preparingAt && o.readyAt && o.completedAt;
      if (hasTimestamps) {
        addTimeToBucket(totalsBucket, o);
      } else {
        missingTimestampsCount++;
      }
    } else if (o.status === 'CANCELLED') {
      totalsBucket.cancelledOrderIds.add(o.id);
      totalsBucket.cancelledRevenue += o.total;
    } else {
      totalsBucket.inProgressOrderIds.add(o.id);
    }
  }

  // 2. Gom nhóm theo groupBy
  if (groupBy.length > 0) {
    for (const o of orders) {
      if (isLineItemGrouping) {
        // Mỗi món ăn trong đơn là một điểm dữ liệu
        for (const item of o.items) {
          const keyObj: Record<string, string | number> = {};
          for (const dim of groupBy) {
            keyObj[dim] = getDimValue(dim, o, item);
          }
          const groupKey = JSON.stringify(keyObj);
          let entry = groupsMap.get(groupKey);
          if (!entry) {
            entry = { keyObj, bucket: createEmptyBucket() };
            groupsMap.set(groupKey, entry);
          }
          const b = entry.bucket;
          b.orderIds.add(o.id);

          if (o.status === 'COMPLETED') {
            b.completedOrderIds.add(o.id);
            b.revenue += item.price * item.quantity;
            b.portions += item.quantity;
            addTimeToBucket(b, o);
          } else if (o.status === 'CANCELLED') {
            b.cancelledOrderIds.add(o.id);
            b.cancelledRevenue += item.price * item.quantity;
            b.portions += item.quantity;
          } else {
            b.inProgressOrderIds.add(o.id);
          }
        }
      } else {
        // Gom theo đơn hàng thông thường
        const keyObj: Record<string, string | number> = {};
        for (const dim of groupBy) {
          keyObj[dim] = getDimValue(dim, o);
        }
        const groupKey = JSON.stringify(keyObj);
        let entry = groupsMap.get(groupKey);
        if (!entry) {
          entry = { keyObj, bucket: createEmptyBucket() };
          groupsMap.set(groupKey, entry);
        }
        const b = entry.bucket;
        b.orderIds.add(o.id);

        if (o.status === 'COMPLETED') {
          b.completedOrderIds.add(o.id);
          if (hasLineItemFilter) {
            for (const item of o.items) {
              b.revenue += item.price * item.quantity;
              b.portions += item.quantity;
            }
          } else {
            b.revenue += o.total;
            for (const item of o.items) {
              b.portions += item.quantity;
            }
          }
          addTimeToBucket(b, o);
        } else if (o.status === 'CANCELLED') {
          b.cancelledOrderIds.add(o.id);
          b.cancelledRevenue += o.total;
        } else {
          b.inProgressOrderIds.add(o.id);
        }
      }
    }
  }

  // 3. Helper tính giá trị metric từ bucket
  function calculateMetricValue(b: BucketData, metric: Metric, isCancelGroup: boolean = false): number | null {
    switch (metric) {
      case 'revenue':
        return isCancelGroup ? b.cancelledRevenue : b.revenue;
      case 'orders':
        return isCancelGroup ? b.cancelledOrderIds.size : b.completedOrderIds.size;
      case 'portions':
        return b.portions;
      case 'avg_order_value': {
        const count = isCancelGroup ? b.cancelledOrderIds.size : b.completedOrderIds.size;
        const rev = isCancelGroup ? b.cancelledRevenue : b.revenue;
        return count > 0 ? Math.round(rev / count) : 0;
      }
      case 'cancelled_orders':
        return b.cancelledOrderIds.size;
      case 'in_progress_orders':
        return b.inProgressOrderIds.size;
      case 'cancel_rate': {
        const denom = b.cancelledOrderIds.size + b.completedOrderIds.size;
        return denom > 0 ? Math.round((b.cancelledOrderIds.size / denom) * 1000) / 1000 : null;
      }
      case 'avg_prep_minutes':
        return calculateAvg(b.prepMinutes);
      case 'p90_prep_minutes': {
        const sorted = [...b.prepMinutes].sort((x, y) => x - y);
        return calculateP90(sorted);
      }
      case 'avg_wait_minutes':
        return calculateAvg(b.waitMinutes);
      case 'avg_pickup_delay_minutes':
        return calculateAvg(b.pickupDelayMinutes);
      default:
        return 0;
    }
  }

  // 4. Tính toán totals map
  const totals: Record<string, number | null> = {};
  for (const m of metrics) {
    totals[m] = calculateMetricValue(totalsBucket, m);
  }

  // 5. Tạo danh sách rows
  const rows: EngineRow[] = [];
  const isCancelReasonGrouping = groupBy.includes('cancel_reason');

  for (const { keyObj, bucket } of groupsMap.values()) {
    const row: EngineRow = { key: keyObj };
    for (const m of metrics) {
      row[m] = calculateMetricValue(bucket, m, isCancelReasonGrouping);
    }
    rows.push(row);
  }

  // 6. Sắp xếp rows
  rows.sort((a, b) => {
    const valA = a[sortBy] ?? 0;
    const valB = b[sortBy] ?? 0;
    if (typeof valA === 'number' && typeof valB === 'number') {
      return sortDir === 'asc' ? valA - valB : valB - valA;
    }
    return 0;
  });

  const rowCount = rows.length;
  const truncated = rowCount > limit;
  const displayRows = rows.slice(0, limit);

  let note: string | undefined;
  if (isLineItemGrouping) {
    note =
      'Khi nhóm theo dish hoặc category, một đơn chứa nhiều món sẽ được đếm vào từng nhóm tương ứng, nên tổng số đơn của các nhóm có thể lớn hơn số đơn thực tế.';
  }

  return {
    appliedFilters,
    groupBy,
    metrics,
    rows: displayRows,
    totals,
    rowCount,
    truncated,
    skipped: {
      missingTimestamps: missingTimestampsCount,
    },
    note,
  };
}
