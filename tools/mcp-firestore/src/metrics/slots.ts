import { Order, TimeSlot } from '../data/types.js';

export interface SlotStats {
  slotLabel: string;
  orders: number;
  fillRate: number; // 0..1
  fullDays: number;
  days: number;
}

export interface OrdersBySlotResult {
  slots: SlotStats[];
  busiest: string | null;
  oftenFull: string[];
}

export function computeOrdersBySlot(
  orders: Order[],
  timeSlots: TimeSlot[]
): OrdersBySlotResult {
  // Chỉ lấy đơn không CANCELLED
  const validOrders = orders.filter((o) => o.status !== 'CANCELLED');

  // Tập hợp các ngày thực sự có đơn trong khoảng
  const distinctDaysWithOrders = new Set<string>();
  for (const o of orders) {
    distinctDaysWithOrders.add(o.date);
  }
  const daysCount = distinctDaysWithOrders.size || 1;

  // Gom theo slotId/slotLabel
  const slotMap = new Map<
    string,
    {
      label: string;
      slotId?: string;
      maxOrders: number;
      orders: number;
      dailyOrderCounts: Map<string, number>;
    }
  >();

  // Khởi tạo trước từ danh sách timeSlots để đảm bảo đủ khung giờ
  for (const ts of timeSlots) {
    slotMap.set(ts.id, {
      label: ts.label,
      slotId: ts.id,
      maxOrders: ts.maxOrders,
      orders: 0,
      dailyOrderCounts: new Map<string, number>(),
    });
  }

  // Cộng dồn đơn
  for (const o of validOrders) {
    let entry = slotMap.get(o.slotId);
    if (!entry) {
      // Thử tìm theo label
      for (const val of slotMap.values()) {
        if (val.label === o.slotLabel) {
          entry = val;
          break;
        }
      }
    }
    if (!entry) {
      entry = {
        label: o.slotLabel || o.slotId,
        slotId: o.slotId,
        maxOrders: 10,
        orders: 0,
        dailyOrderCounts: new Map<string, number>(),
      };
      slotMap.set(o.slotId || o.slotLabel, entry);
    }

    entry.orders++;
    const currentDayCount = entry.dailyOrderCounts.get(o.date) || 0;
    entry.dailyOrderCounts.set(o.date, currentDayCount + 1);
  }

  const slots: SlotStats[] = [];
  let highestFillRate = -1;
  let busiestSlot: string | null = null;
  const oftenFullSlots: string[] = [];

  for (const item of slotMap.values()) {
    let fullDays = 0;
    for (const count of item.dailyOrderCounts.values()) {
      if (count >= item.maxOrders) {
        fullDays++;
      }
    }

    const capacity = item.maxOrders * daysCount;
    const rawFillRate = capacity > 0 ? item.orders / capacity : 0;
    const fillRate = Math.round(rawFillRate * 1000) / 1000;

    slots.push({
      slotLabel: item.label,
      orders: item.orders,
      fillRate,
      fullDays,
      days: daysCount,
    });

    if (fillRate > highestFillRate) {
      highestFillRate = fillRate;
      busiestSlot = item.label;
    }

    if (fullDays > 0) {
      oftenFullSlots.push(item.label);
    }
  }

  return {
    slots,
    busiest: busiestSlot,
    oftenFull: oftenFullSlots,
  };
}

